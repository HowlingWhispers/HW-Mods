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
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUTPUT_DIR"' EXIT
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
Glass pipes: chest at (0,64,0), glass at x=1,2,3, chest at (4,64,0).
Place iron ingots in the source chest and run:
/buildcraft pulse 0 64 0 4 64 0
No native BuildCraft blocks/animations yet; game compatibility needs testing.
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
echo 'Developer-only standalone single-player playtest built.'
