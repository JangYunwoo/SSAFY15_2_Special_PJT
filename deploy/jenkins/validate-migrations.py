"""One-time reviewed migration validation, never automatic approval inside Jenkins."""
from pathlib import Path
import hashlib
import json
import secrets
import subprocess
import sys
import time

root=Path('/opt/gongguhan-ci')
folder=Path(sys.argv[1]).resolve()
files=sorted(folder.glob('V*.sql'),key=lambda p:int(p.name.split('__')[0][1:]))
if {p.name for p in files} != {'V23__mark_payment_refund_required.sql','V24__refund_for_stranded_payment.sql'}:
    raise SystemExit('This reviewed validation is limited to V23 and V24')
name='gongguhan-ci-migration-234'
network=name+'-net'
def run(*args,**kwargs): return subprocess.check_output(args,timeout=kwargs.pop('timeout',90),**kwargs)
def mysql(container,sql):
    return run('docker','exec','-i',container,'sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot gongguhan',input=sql)

schema=run('docker','exec','gongguhan-prod-mysql-1','sh','-c',
 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --no-data --no-tablespaces --set-gtid-purged=OFF gongguhan')
run('docker','network','create','--internal',network)
try:
    run('docker','run','-d','--name',name,'--network',network,'--cpus','1','--memory','1g',
        '--tmpfs','/var/lib/mysql:rw,size=512m','-e','MYSQL_ROOT_PASSWORD='+secrets.token_urlsafe(24),
        '-e','MYSQL_DATABASE=gongguhan','mysql:8.0')
    try:
        for attempt in range(60):
            try:
                mysql(name,b'SELECT 1;')
                break
            except subprocess.CalledProcessError:
                time.sleep(2)
        else: raise RuntimeError('Isolated MySQL failed to start')
        mysql(name,schema)
        for file in files: mysql(name,file.read_bytes())
        mysql(name,b'SELECT reconcile_state FROM payments LIMIT 0; SELECT payment_id FROM refunds LIMIT 0;')
        approvals={f'backend/src/main/resources/db/migration/{p.name}':hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
        result={'status':'passed','engine':'mysql:8.0','scope':'production schema only, no customer rows',
                'migrations':approvals,'productionDatabaseMutated':False}
        (root/'state/migration-validation-234.json').write_text(json.dumps(result,indent=2))
        if '--approve' in sys.argv:
            approved=root/'state/approved-migrations.json'
            existing=json.loads(approved.read_text()) if approved.exists() else {}
            existing.update(approvals)
            approved.write_text(json.dumps(existing,indent=2))
            approved.chmod(0o600)
        print(json.dumps(result))
    finally:
        # Exact dedicated test container only. No production containers or volumes removed.
        run('docker','rm','-f',name)
finally:
    run('docker','network','rm',network)
