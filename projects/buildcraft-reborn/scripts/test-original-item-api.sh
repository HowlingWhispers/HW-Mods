#!/usr/bin/env bash
# Actual original BCCE ItemPort API compiled against verified Mojang Snapshot 3.
# This is a compile/ABI check, NOT a fake inventory or end-to-end pipe test.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BASE="$ROOT/dist/buildcraft-reborn"
ORIGINAL="$BASE/effective-1.21.11-neoforge/src/main/java"
CACHE="$BASE/mojang-26.4-snapshot-3"
mkdir -p "$CACHE"
if [[ ! -s "$CACHE/client.jar" ]]; then
  curl --fail --location --silent --show-error --retry 3 \
    'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' \
    -o "$CACHE/manifest.json"
  VERSION_URL="$(jq -er '.versions[] | select(.id=="26.4-snapshot-3") | .url' "$CACHE/manifest.json")"
  curl --fail --location --silent --show-error --retry 3 "$VERSION_URL" \
    -o "$CACHE/version.json"
  URL="$(jq -er '.downloads.client.url' "$CACHE/version.json")"
  curl --fail --location --silent --show-error --retry 3 "$URL" \
    -o "$CACHE/client.jar.tmp"
  mv "$CACHE/client.jar.tmp" "$CACHE/client.jar"
fi
test -s "$CACHE/version.json" || { echo "Missing authenticated Mojang metadata" >&2; exit 1; }
[[ "$(jq -r '.id' "$CACHE/version.json")" == "26.4-snapshot-3" ]] || {
  echo "Wrong Mojang Snapshot downloaded" >&2; exit 1;
}
EXPECTED="$(jq -er '.downloads.client.sha1' "$CACHE/version.json")"
ACTUAL="$(sha1sum "$CACHE/client.jar" | awk '{print $1}')"
[[ "$EXPECTED" == "$ACTUAL" ]] || { echo "Mojang client SHA1 mismatch" >&2; exit 1; }
test -s "$ORIGINAL/buildcraft/api/v2/item/ItemPort.java"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
javac --release 25 -encoding UTF-8 -cp "$CACHE/client.jar" -d "$BUILD" \
  "$ORIGINAL/buildcraft/api/v2/OperationMode.java" \
  "$ORIGINAL/buildcraft/api/v2/item/ItemMatcher.java" \
  "$ORIGINAL/buildcraft/api/v2/item/ItemPort.java" \
  "$ORIGINAL/buildcraft/api/v2/item/ItemTransferPolicy.java" \
  "$ORIGINAL/buildcraft/api/v2/item/ItemTransferResult.java"
javap -classpath "$BUILD" buildcraft.api.v2.item.ItemPort |
  grep -F 'insert(net.minecraft.world.item.ItemStack, buildcraft.api.v2.OperationMode)'
javap -classpath "$BUILD" buildcraft.api.v2.item.ItemTransferResult |
  grep -F 'net.minecraft.world.item.ItemStack transferred()'
echo "PASS: unchanged original BCCE ItemPort API compiles with VERIFIED Minecraft 26.4 Snapshot 3 ItemStack."
echo "NOT YET playable; NeoForge tile/capability integration remains."
