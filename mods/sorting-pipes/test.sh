#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
API_JAR="${1:-lib/howl-api-sorting-pipes.jar}"
bash build.sh "$API_JAR"
VERSION="$(python3 -c 'import json;print(json.load(open("resources/coda.mod.json"))["version"])')"
MOD_JAR="dist/howl-sorting-pipes-${VERSION}.jar"

# Validate the released JAR: original cap, generated dark-metal sides, and
# every one of the 64 connected-center models. No Minecraft runtime required.
python3 - "$MOD_JAR" <<'PY'
import io, json, sys, zipfile
from pathlib import Path
from pipe_art import DIRECTIONS, unpack_rgba_png, joint_mask
with zipfile.ZipFile(sys.argv[1]) as jar:
    pack_bytes = jar.read('sorting-pipes-resources.zip')
with zipfile.ZipFile(io.BytesIO(pack_bytes)) as pack:
    prefix = 'assets/howl_sorting_pipes/'
    def image(name):
        return unpack_rgba_png(pack.read(prefix + 'textures/block/' + name + '.png'))
    def model(name):
        return json.loads(pack.read(prefix + 'models/block/' + name + '.json'))
    def states(name):
        return json.loads(pack.read(prefix + 'blockstates/' + name + '.json'))

    original = Path('textures/block/pipe_center.png').read_bytes()
    assert pack.read(prefix + 'textures/block/pipe_center.png') == original
    cap = image('pipe_center')
    arm_h, arm_v = image('pipe_arm_side_h'), image('pipe_arm_side_v')
    # Arm color and stripe width match the uploaded central 6x6 design.
    for i in range(5, 11):
        assert arm_h[i][0] == arm_v[0][i]
    assert arm_h[7][0] != cap[7][5]   # joined edge is no longer a dark flange
    assert arm_h[7][0] == cap[6][8]   # bright steel from the creator's art
    assert image('pipe_center_joint_01')[7][5] == arm_h[7][0]
    assert image('pipe_center_joint_01')[7][10] == cap[7][10]
    assert image('pipe_center_joint_0f')[7][5] == arm_h[7][0]
    for mask in range(1, 16):
        image(f'pipe_center_joint_{mask:02x}')

    parts = states('transport_pipe')['multipart']
    core_parts = [x for x in parts if '_core' in x['apply']['model']]
    assert len(core_parts) == 64
    assert len(parts) == 70   # 64 mutually exclusive cores and 6 optional arms
    seen = set()
    for part in core_parts:
        flags = part['when']
        assert set(flags) == set(DIRECTIONS)
        bits = tuple(k for k in DIRECTIONS if flags[k] == 'true')
        assert bits not in seen
        seen.add(bits)
        mask = sum(1 << i for i, direction in enumerate(DIRECTIONS)
                   if flags[direction] == 'true')
        suffix = '' if mask == 0 else f'_{mask:02x}'
        core = model('transport_pipe_core' + suffix)
        for face in DIRECTIONS:
            key = core['elements'][0]['faces'][face]['texture'][1:]
            expected_mask = joint_mask(face, set(bits))
            expected_tex = ('howl_sorting_pipes:block/pipe_center_joint_'
                            + f'{expected_mask:02x}' if expected_mask else
                            'howl_sorting_pipes:block/pipe_center')
            assert core['textures'][key] == expected_tex
    assert len(seen) == 64
    for arm in DIRECTIONS:
        m = model('transport_pipe_' + arm)
        t = m['textures']
        assert t['end'] == 'howl_sorting_pipes:block/pipe_center'
        assert t['horizontal'] == 'howl_sorting_pipes:block/pipe_arm_side_h'
        assert t['vertical'] == 'howl_sorting_pipes:block/pipe_arm_side_v'
        assert 'minecraft:block/iron_block' not in t.values()
    # Extractor and sorter must retain their previous models and textures.
    assert model('extractor_pipe_core')['textures']['body'] == 'minecraft:block/oak_planks'
    assert model('sorting_pipe_core')['textures']['body'] == 'minecraft:block/diamond_block'
    assert model('transport_pipe_core')['textures']['cap'] == 'howl_sorting_pipes:block/pipe_center'
print('PASS: original art, six matching arms, 16 edge variants, 64 center states')
PY

mkdir -p build/tests
mapfile -d '' TESTS < <(find tests -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$API_JAR:$MOD_JAR" -d build/tests "${TESTS[@]}"
java -cp "build/tests:$API_JAR:$MOD_JAR" dev.howlingwhispers.sortingpipes.SortingTransportTest
java -cp "build/tests:$API_JAR:$MOD_JAR" dev.howlingwhispers.sortingpipes.SortingCommandTest
