#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
mkdir -p ci-results
uid="$(id -u):$(id -g)"
run_label="io.gongguhan.ci-run=${BUILD_TAG:?Jenkins BUILD_TAG is required}"
case "${1:-}" in
 cleanup)
  # Remove only containers owned by this exact Jenkins run, never global Docker prune.
  while read -r container; do
   [ -z "$container" ] || docker rm -f "$container"
  done < <(docker ps -aq --filter "label=$run_label")
  ;;
 frontend)
  docker run --rm --label "$run_label" --cpus=2 --memory=3g --user "$uid" -e HOME=/tmp -e CI=true \
   -v "$PWD:$PWD" -w "$PWD/frontend" node:22-bookworm-slim \
   sh -ec 'npm ci --no-audit --no-fund; npm test; npm run lint; npm run build'
  ;;
 backend)
  # A disposable namespace gives legacy localhost-based tests Redis and S3 without
  # exposing ports or connecting to the production Compose network/volumes.
  redis="${BUILD_TAG}-redis"
  minio="${BUILD_TAG}-minio"
  trap 'bash deploy/jenkins/ci.sh cleanup' EXIT
  docker run -d --label "$run_label" --name "$redis" --cpus=0.5 --memory=256m --tmpfs /data:rw,size=64m \
   redis:7-alpine redis-server --save '' --appendonly no
  docker run -d --label "$run_label" --name "$minio" --network "container:$redis" \
   --cpus=0.5 --memory=512m --tmpfs /data:rw,size=128m \
   -e MINIO_ROOT_USER=gongguhan -e MINIO_ROOT_PASSWORD=gongguhan-local-s3 \
   quay.io/minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e \
   server /data --console-address :9001
  ready=false
  for attempt in {1..30}; do
   if docker exec "$minio" curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then ready=true; break; fi
   sleep 2
  done
  [ "$ready" = true ] || { echo 'CI MinIO did not become ready' >&2; exit 1; }
  [ "$(docker exec "$redis" redis-cli ping)" = PONG ]
  docker run --rm --label "$run_label" --network "container:$redis" --entrypoint /bin/sh \
   quay.io/minio/mc@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727 \
   -ec 'mc alias set local http://127.0.0.1:9000 gongguhan gongguhan-local-s3 >/dev/null; mc mb local/gongguhan-local; mc anonymous set download local/gongguhan-local'
  docker run --rm --label "$run_label" --cpus=2 --memory=6g --user "$uid" -e HOME=/tmp \
   --network "container:$redis" -e RUN_S3_INTEGRATION_TEST=true \
   -e AWS_ACCESS_KEY_ID=gongguhan -e AWS_SECRET_ACCESS_KEY=gongguhan-local-s3 \
   -e GRADLE_USER_HOME=/tmp/gradle \
   -e APP_ADMIN_SEED_ENABLED=false -e RECOMMENDATION_SEED_ENABLED=false \
   -e CRYPTO_CONFIRMATION_POLL_ENABLED=false \
   -v "$PWD:$PWD" -w "$PWD/backend" eclipse-temurin:21-jdk-jammy \
   bash -ec 'bash gradlew --no-daemon --max-workers=2 -I ../docs/testing/full-regression-runtime.gradle test bootJar'
  python3 deploy/jenkins/verify-results.py backend/build/test-results/test
  ;;
 ai)
  docker build -f deploy/jenkins/ai-test.Dockerfile -t "gongguhan-ci/ai-test:$RELEASE_SHA" ai
  docker run --rm --label "$run_label" --cpus=2 --memory=3g --user "$uid" -e HOME=/tmp \
   -e OMP_NUM_THREADS=2 -e OPENBLAS_NUM_THREADS=2 -e MKL_NUM_THREADS=2 \
   -v "$PWD/ci-results:/results" "gongguhan-ci/ai-test:$RELEASE_SHA"
  ;;
 images)
  for service in backend ai frontend; do
   name="$service"; [ "$service" != frontend ] || name=nginx
   docker build --label "org.opencontainers.image.revision=$RELEASE_SHA" \
    -f "deploy/$service.Dockerfile" -t "gongguhan-ci/$name:$RELEASE_SHA" "$service"
  done
  ;;
 *) echo 'Expected frontend, backend, ai or images' >&2; exit 2;;
esac
