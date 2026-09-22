#!/usr/bin/env bash
set -euo pipefail
base=/opt/gongguhan-ci
for dir in workspace releases state secrets; do
  sudo install -d -o 1000 -g 1000 -m 700 "$base/$dir"
done
test -s "$base/secrets/gitlab-read.json"
python3 - <<'PY'
from pathlib import Path
import os, secrets, json, subprocess
base=Path('/opt/gongguhan-ci')
password=base/'secrets/admin-password'
if not password.exists():
    password.write_text(secrets.token_urlsafe(32))
    password.chmod(0o600)
agent=base/'secrets/agent-secret'
if not agent.exists():
    agent.touch(mode=0o600)
baseline=base/'state/baseline.json'
if not baseline.exists():
    def run(*args): return subprocess.check_output(args,text=True).strip()
    sha=run('git','-C','/home/ubuntu/S15P21B205','rev-parse','HEAD')
    images={s:run('docker','inspect','-f','{{.Image}}',f'gongguhan-prod-{s}-1') for s in ('backend','ai','nginx')}
    baseline.write_text(json.dumps({'sha':sha,'base':'/home/ubuntu/S15P21B205/deploy/docker-compose.prod.yml','images':images},indent=2))
    baseline.chmod(0o600)
print('Baseline and protected credentials prepared; secret values omitted.')
query='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "SELECT version,success FROM gongguhan.flyway_schema_history ORDER BY installed_rank DESC LIMIT 4"'
print(subprocess.check_output(['docker','exec','gongguhan-prod-mysql-1','sh','-c',query],text=True))
PY
printf 'DOCKER_GID=%s\n' "$(stat -c %g /var/run/docker.sock)" > "$base/config/.env"
cd "$base/config"
docker compose build controller agent
docker compose up -d controller
echo 'Controller started. Copy generated agent secret and start agent after initialization completes.'
