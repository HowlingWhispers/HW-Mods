#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
MOD=mods/howl-frontier
API_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
mkdir -p "$MOD/build/api" "$MOD/build/classes" "$MOD/build/tests" "$MOD/dist"
find "$MOD/build" -name '*.class' -delete
mapfile -d '' API < <(find "$API_DIR/dev/howlingwhispers/codaloader/api" -name '*.java' -print0)
mapfile -d '' SOURCES < <(find "$MOD/src" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$MOD/build/api" "${API[@]}"
javac --release 21 -encoding UTF-8 -cp "$MOD/build/api" -d "$MOD/build/classes" "${SOURCES[@]}"
cp "$MOD/resources/coda.mod.json" "$MOD/build/classes/"
VERSION="$(python3 -c 'import json;print(json.load(open("mods/howl-frontier/resources/coda.mod.json"))["version"])')"
mapfile -d '' TESTS < <(find "$MOD/tests" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$MOD/build/api:$MOD/build/classes" -d "$MOD/build/tests" "${TESTS[@]}"
java -cp "$MOD/build/api:$MOD/build/classes:$MOD/build/tests" dev.howlingwhispers.frontier.FrontierTest
jar --create --file "$MOD/dist/howl-frontier-${VERSION}.jar" -C "$MOD/build/classes" .
(cd "$MOD/dist" && sha256sum "howl-frontier-${VERSION}.jar" > "howl-frontier-${VERSION}.jar.sha256")
python3 - "$MOD/dist/howl-frontier-${VERSION}.jar" <<'PY'
import json,sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
 m=json.loads(z.read('coda.mod.json'));assert m['id']=='howl_frontier' and m['minecraft']=='26.4-snapshot-3'
 assert m['entrypoint'].replace('.','/')+'.class' in z.namelist()
 assert not any(n.startswith('dev/howlingwhispers/codaloader/') for n in z.namelist())
print('PASS: Frontier identity, entrypoint and compile-only loader API')
PY
