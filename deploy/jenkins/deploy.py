"""Deploy tested immutable images. Never reset the team's server checkout or volumes."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path('/opt/gongguhan-ci')
LIVE = Path('/home/ubuntu/S15P21B205')
SERVICES = ('backend', 'ai', 'nginx')


def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, timeout=kwargs.pop('timeout', 90), **kwargs).strip()


def validate_sha(sha):
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Expected a full lowercase commit SHA')


def image_map(sha):
    validate_sha(sha)
    return {s: f'gongguhan-ci/{s}:{sha}' for s in SERVICES}


def migration_changes(old, new):
    return run('git', 'diff', '--name-only', old, new, '--',
               'backend/src/main/resources/db/migration').splitlines()


def verify_migrations(changed, approvals, directory):
    for relative in changed:
        file = directory / relative
        if not file.is_file() or approvals.get(relative) != hashlib.sha256(file.read_bytes()).hexdigest():
            raise RuntimeError('DB migration requires review and isolated MySQL validation: ' + relative)


def write_json(file, data):
    temporary = file.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2), encoding='utf-8')
    temporary.chmod(0o600)
    temporary.replace(file)


def compose(record, *args):
    return run('docker', 'compose', '-p', 'gongguhan-prod', '-f', record['base'],
               '-f', record['override'], *args, timeout=240)


def wait_healthy(service, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        state = json.loads(run('docker', 'inspect', '-f', '{{json .State}}', f'gongguhan-prod-{service}-1'))
        if state.get('Status') == 'running' and state.get('Health', {}).get('Status') == 'healthy':
            return
        if state.get('Status') in ('exited', 'dead'):
            raise RuntimeError(service + ' exited')
        time.sleep(3)
    raise RuntimeError(service + ' health check timed out')


def verify_site():
    for service in ('backend', 'ai'):
        wait_healthy(service)
    run('docker', 'exec', 'gongguhan-prod-nginx-1', 'nginx', '-t')
    for url in ('https://j15b205.p.ssafy.io/', 'https://j15b205.p.ssafy.io/api/v1/actuator/health'):
        response = run('curl', '--fail', '--silent', '--show-error', '--max-time', '30', url)
        if url.endswith('/health') and json.loads(response).get('status') != 'UP':
            raise RuntimeError('Public health check failed')


def activate(candidate, previous):
    """An unhealthy candidate cannot become the recorded current release."""
    try:
        compose(candidate, 'up', '-d', '--no-deps', '--no-build', 'backend', 'ai')
        wait_healthy('backend')
        wait_healthy('ai')
        compose(candidate, 'up', '-d', '--no-deps', '--no-build', '--force-recreate', 'nginx')
        verify_site()
    except Exception:
        # Application rollback only: restoring a DB could erase concurrent transactions.
        compose(previous, 'up', '-d', '--no-deps', '--no-build', 'backend', 'ai')
        wait_healthy('backend')
        wait_healthy('ai')
        compose(previous, 'up', '-d', '--no-deps', '--no-build', '--force-recreate', 'nginx')
        verify_site()
        raise


def main(sha):
    import fcntl  # Linux only; unit-testable pure validation functions remain portable.
    validate_sha(sha)
    if os.environ.get('JOB_NAME') != 'gongguhan-DevelopV2' or os.environ.get('GIT_BRANCH') != 'origin/DevelopV2':
        raise RuntimeError('Deployment is restricted to the DevelopV2 production job')
    if run('git', 'rev-parse', 'HEAD') != sha:
        raise RuntimeError('Tested commit does not match workspace')
    state = ROOT / 'state'
    with (state / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        current_file = state / 'current.json'
        previous = json.loads((current_file if current_file.exists() else state / 'baseline.json').read_text())
        if previous['sha'] == sha:
            verify_site()
            print('Already deployed and healthy: ' + sha)
            return
        run('git', 'merge-base', '--is-ancestor', previous['sha'], sha)
        approvals_file = state / 'approved-migrations.json'
        approvals = json.loads(approvals_file.read_text()) if approvals_file.exists() else {}
        verify_migrations(migration_changes(previous['sha'], sha), approvals, Path.cwd())
        images = image_map(sha)
        for service, image in images.items():
            revision = run('docker', 'image', 'inspect', '-f', '{{index .Config.Labels "org.opencontainers.image.revision"}}', image)
            if revision != sha:
                raise RuntimeError('Image revision mismatch: ' + service)
        # Capture the actual running image IDs, not mutable tag names.
        old_images = {s: run('docker', 'inspect', '-f', '{{.Image}}', f'gongguhan-prod-{s}-1') for s in SERVICES}
        release = ROOT / 'releases' / sha
        if release.exists():
            raise RuntimeError('Release directory already exists; inspect previous failed deployment before retry')
        release.mkdir(mode=0o700)
        archive = subprocess.Popen(['git', 'archive', sha], stdout=subprocess.PIPE)
        subprocess.run(['tar', '-x', '-C', str(release)], stdin=archive.stdout, check=True)
        archive.stdout.close()
        if archive.wait() != 0:
            raise RuntimeError('Git archive failed')
        (release / 'deploy/env').mkdir(exist_ok=True)
        for name in ('backend.env', 'mysql.env', 'ai.env'):
            source = LIVE / 'deploy/env' / name
            if not source.is_file():
                raise RuntimeError('Missing runtime environment file: ' + name)
            (release / 'deploy/env' / name).symlink_to(source)
        (release / 'deploy/secrets').symlink_to(LIVE / 'deploy/secrets', target_is_directory=True)
        override = {'services': {s: {'image': image} for s, image in images.items()}}
        # Preserve the existing untracked bank test console without committing its contents.
        console = LIVE / 'frontend/public/bank-console.html'
        if console.is_file():
            override['services']['nginx']['volumes'] = [f'{console}:/usr/share/nginx/html/bank-console.html:ro']
        override_path = release / 'images.json'
        write_json(override_path, override)
        candidate = {'sha': sha, 'base': str(release / 'deploy/docker-compose.prod.yml'),
                     'override': str(override_path), 'images': images}
        rollback_path = release / 'rollback-images.json'
        rollback_override = {'services': {s: {'image': image} for s, image in old_images.items()}}
        if console.is_file():
            rollback_override['services']['nginx']['volumes'] = override['services']['nginx']['volumes']
        write_json(rollback_path, rollback_override)
        previous = {**previous, 'override': str(rollback_path)}
        compose(candidate, 'config', '--quiet')
        # Backup stays server-local, private, outside Jenkins archived artifacts.
        backup = state / f'db-before-{sha}.sql'
        fd = os.open(backup, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            subprocess.run(['docker','exec','gongguhan-prod-mysql-1','sh','-c',
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF gongguhan'],
                stdout=stream, check=True, timeout=180)
        activate(candidate, previous)
        write_json(state / 'previous.json', previous)
        write_json(current_file, candidate)
        write_json(Path('ci-results/deployment.json'), {'sha': sha, 'status': 'healthy', 'images': images})
        print('Deployed and healthy: ' + sha)


if __name__ == '__main__':
    main(sys.argv[1])
