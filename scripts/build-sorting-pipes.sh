#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf dist/sorting-pipes mods/sorting-pipes/dist
bash mods/sorting-pipes/test.sh
mkdir -p dist/sorting-pipes
cp mods/sorting-pipes/dist/* dist/sorting-pipes/
VERSION="$(python3 -c 'import json; print(json.load(open("mods/sorting-pipes/resources/coda.mod.json"))["version"])')"
python3 - "$VERSION" <<'PY'
from pathlib import Path
import sys,zipfile,hashlib
root=Path('mods/sorting-pipes'); version=sys.argv[1]
with zipfile.ZipFile(f'dist/sorting-pipes/sorting-pipes-{version}-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(root.rglob('*')):
        if path.is_file() and not any(p in {'build','dist','lib','__pycache__'} for p in path.relative_to(root).parts): z.write(path,path.relative_to(root.parent))
for path in Path('dist/sorting-pipes').iterdir():
    if path.is_file() and not path.name.endswith('.sha256'):
        path.with_name(path.name+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
PY
