#!/usr/bin/env bash
# Build a Nightly-channel artifact. Only CodaLauncher installs its mod payload.
set -euo pipefail
cd "$(dirname "$0")/.."
LOADER_ROOT="${HW_CODALOADER_ROOT:-../HW-CodaLoader}"
[[ -f "$LOADER_ROOT/scripts/build-dist.sh" ]] || {
  echo "Missing sibling HW-CodaLoader checkout at $LOADER_ROOT" >&2; exit 1;
}
[[ -f dist/buildcraft-cml-0.1.0-dev.jar ]] || {
  echo "Build BuildCraft first: bash scripts/build-buildcraft-cml.sh" >&2; exit 1;
}
LOADER_ROOT="$(cd "$LOADER_ROOT" && pwd)"
(
  cd "$LOADER_ROOT"
  bash scripts/build-dist.sh
)
[[ -f "$LOADER_ROOT/dist/CodaLoader.jar" ]] || {
  echo "CodaLoader developer JAR missing" >&2; exit 1;
}
# Test the generated Snapshot 3 resource pack against the ACTUAL ORIGINAL
# BuildCraft 8.0.0 JAR's entire resource tree, not a hand-written mock ZIP.
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
javac --release 21 -cp "$LOADER_ROOT/dist/CodaLoader.jar" -d "$TEST_DIR" \
  "$LOADER_ROOT/tests/src/dev/howlingwhispers/codaloader/bootstrap/BuildCraftResourceInstallerTest.java"
java -cp "$TEST_DIR:$LOADER_ROOT/dist/CodaLoader.jar" \
  dev.howlingwhispers.codaloader.bootstrap.BuildCraftResourceInstallerTest \
  "$(pwd)/dist/buildcraft-cml-0.1.0-dev.jar"
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUTPUT_DIR" "$TEST_DIR"' EXIT
mkdir -p "$OUTPUT_DIR/payload"
cp "$LOADER_ROOT/dist/CodaLoader.jar" "$OUTPUT_DIR/CodaLoader.jar"
cp dist/buildcraft-cml-0.1.0-dev.jar "$OUTPUT_DIR/payload/"
# Quiet Underground is a *world datapack*, not a second CML mod. Bundle it
# with Nightly so the integrated Create World hook can apply it on new saves.
python3 scripts/build-hw-quiet-underground.py
(cd dist && sha256sum -c hw-quiet-underground-1.0.0.zip.sha256)
cp dist/hw-quiet-underground-1.0.0.zip "$OUTPUT_DIR/payload/"
cp dist/hw-quiet-underground-1.0.0.zip.sha256 "$OUTPUT_DIR/payload/"
cat > "$OUTPUT_DIR/README-NIGHTLY.txt" <<'EOF'
H.O.W.L. BuildCraft Nightly development payload
===============================================
This ZIP is consumed automatically by CodaLauncher Settings -> Nightly.
Do not extract or run its loader by hand. The launcher validates SHA-256,
keeps Stable untouched, and installs the JAR into nightly/minecraft/mods.
Only use a fresh disposable single-player world.
HW Quiet Underground also ships as a checked world datapack inside Nightly.
The H.O.W.L. Create World screen hook attempts to select it automatically
BEFORE the new world is generated. Existing world saves are not modified.
This is experimental and still requires a live Minecraft confirmation.
H.O.W.L. BuildCraft 0.0.1-dev.1; source BCCE 8.0.23.
Requires H.O.W.L. 0.0.33 (Nightly prerelease).
HUMAN SMOKE TEST: place native pipes, check the Creative inventory and
verify Minecraft native tile ticking via /buildcraft status. Leave and
reopen the disposable world; blocks and ticker functionality should remain.
Report the current log and screenshots if anything crashes.
BuildCraft 8.0.23 native MJ engine DEVELOPMENT BASELINE ONLY:
Engine BlockItem, original art and Creative tab still load in H.O.W.L.
The engine simulates BCCE's original 0.05 MJ/t, redstone heat/cooling,
MJ buffer and piston stroke on the authoritative server tick and persists
its state per-world.
IMPORTANT: genuine Minecraft pipe BlockEntity identities and native tick
callbacks are registered, but BCCE TilePipeHolder state/flows are not yet
attached. There are NO MJ pipe receivers, travelling ItemStacks, moving-item
graphics or functional chest-to-chest item transport.
The former instant chest-transfer prototype and /buildcraft pulse command
are no longer registered in game.
Do not ask players to test chest-to-chest movement from this package.
There is no player-facing functional transport test yet.
This developer package is NOT a playable BuildCraft 8.0.23 release.
A HUMAN SMOKE TEST of Creative placement, block entity ticking and save/reload
is permitted on a disposable Nightly world. Do not claim playable cargo
movement. See mods/buildcraft-cml/PLAYTEST-README.txt in source for steps.
EOF
# Inspect the actual entrypoint without constructing a second runtime mods
# directory inside the package. The launcher will place this JAR in game/mods.
unzip -l "$OUTPUT_DIR/payload/buildcraft-cml-0.1.0-dev.jar" \
  | grep -F 'dev/howlingwhispers/buildcraft/BuildCraftGlassPipeDemo.class'
java -jar "$OUTPUT_DIR/CodaLoader.jar" --version
mkdir -p dist
(cd "$OUTPUT_DIR" && zip -qr "$OLDPWD/dist/HOWL-BuildCraft-Singleplayer-Playtest.zip" .)
unzip -l dist/HOWL-BuildCraft-Singleplayer-Playtest.zip \
  | grep -F 'payload/buildcraft-cml-0.1.0-dev.jar'
unzip -l dist/HOWL-BuildCraft-Singleplayer-Playtest.zip \
  | grep -F 'payload/hw-quiet-underground-1.0.0.zip'
sha256sum dist/HOWL-BuildCraft-Singleplayer-Playtest.zip \
  > dist/HOWL-BuildCraft-Singleplayer-Playtest.zip.sha256
echo 'Verified opt-in Nightly BuildCraft native-block gameplay preview bundled.'
