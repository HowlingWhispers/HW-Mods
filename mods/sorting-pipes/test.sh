#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
API_JAR="${1:-lib/howl-api-sorting-pipes.jar}"
bash build.sh "$API_JAR"
VERSION="$(python3 -c 'import json;print(json.load(open("resources/coda.mod.json"))["version"])')"
MOD_JAR="dist/howl-sorting-pipes-${VERSION}.jar"

# Verify that the shipped JAR embeds the exact uploaded PNG and binds it only
# to the transport center. This checks the release asset, not just source files.
python3 - "$MOD_JAR" <<'PY'
import io,json,sys,zipfile
from pathlib import Path
with zipfile.ZipFile(sys.argv[1]) as jar:
    resource_zip=jar.read('sorting-pipes-resources.zip')
with zipfile.ZipFile(io.BytesIO(resource_zip)) as pack:
    path='assets/howl_sorting_pipes/textures/block/pipe_center.png'
    assert pack.read(path)==Path('textures/block/pipe_center.png').read_bytes()
    core=json.loads(pack.read('assets/howl_sorting_pipes/models/block/transport_pipe_core.json'))
    arm=json.loads(pack.read('assets/howl_sorting_pipes/models/block/transport_pipe_east.json'))
    assert core['textures']['body']=='howl_sorting_pipes:block/pipe_center'
    assert arm['textures']['body']=='minecraft:block/iron_block'
PY

mkdir -p build/tests
mapfile -d '' TESTS < <(find tests -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$API_JAR:$MOD_JAR" -d build/tests "${TESTS[@]}"
java -cp "build/tests:$API_JAR:$MOD_JAR" dev.howlingwhispers.sortingpipes.SortingTransportTest
java -cp "build/tests:$API_JAR:$MOD_JAR" dev.howlingwhispers.sortingpipes.SortingCommandTest
