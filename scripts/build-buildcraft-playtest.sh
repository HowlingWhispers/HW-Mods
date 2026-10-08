#!/usr/bin/env bash
# Build a SEPARATE, manual single-player playtest ZIP. Never used by player auto-updates.
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
cp mods/buildcraft-cml/PLAYTEST-README.txt "$OUTPUT_DIR/README-PLAYTEST.txt"
cp mods/buildcraft-cml/Start-BuildCraft-Playtest.bat "$OUTPUT_DIR/Start-BuildCraft-Playtest.bat"
# Inspect the actual entrypoint without constructing a second runtime mods
# directory inside the package. The launcher will place this JAR in game/mods.
unzip -l "$OUTPUT_DIR/payload/buildcraft-cml-0.1.0-dev.jar" \
  | grep -F 'dev/howlingwhispers/buildcraft/BuildCraftGlassPipeDemo.class'
java -jar "$OUTPUT_DIR/CodaLoader.jar" --version
mkdir -p dist
(cd "$OUTPUT_DIR" && zip -qr "$OLDPWD/dist/HOWL-BuildCraft-Singleplayer-Playtest.zip" .)
unzip -l dist/HOWL-BuildCraft-Singleplayer-Playtest.zip \
  | grep -F 'payload/buildcraft-cml-0.1.0-dev.jar'
sha256sum dist/HOWL-BuildCraft-Singleplayer-Playtest.zip \
  > dist/HOWL-BuildCraft-Singleplayer-Playtest.zip.sha256
echo 'Developer-only standalone single-player playtest built.'
