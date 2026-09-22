"""Required infrastructure tests cannot silently turn into skipped tests in CI."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

required = {'GroupBuySlotGateIntegrationTest': 2, 'S3ProductImageStorageIntegrationTest': 1}
for name, expected in required.items():
    files = list(Path(sys.argv[1]).glob(f'TEST-*.{name}.xml'))
    if len(files) != 1:
        raise SystemExit(f'Missing required report: {name}')
    root = ET.parse(files[0]).getroot()
    if int(root.get('tests', 0)) != expected or any(int(root.get(k, 0)) for k in ('skipped','failures','errors')):
        raise SystemExit(f'Required integration tests did not all pass: {name}')
print('Required Redis concurrency and S3-compatible storage tests passed without skips')
