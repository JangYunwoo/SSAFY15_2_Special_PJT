FROM docker:29-cli@sha256:018edbc908e08fcc9dbf029c812c34251e9b4719e6f71ca0e5eae2a987d014ca AS dockercli
FROM jenkins/inbound-agent:jdk21@sha256:6a31d728c22ad74adbb6b9a1a210eb9ec2599f644b95edd7f8a3b8a71d20772c
USER root
RUN apt-get update && apt-get install --no-install-recommends -y python3 python3-yaml && rm -rf /var/lib/apt/lists/*
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
USER jenkins
