#!/usr/bin/env bash
# BuildCraft Reborn: materialize the COMPLETE original BCCE source baseline.
# This intentionally does not compile, publish, or load a Minecraft mod.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT="$ROOT/projects/buildcraft-reborn"
CACHE="$ROOT/dist/buildcraft-reborn"
UPSTREAM="$CACHE/bcce-8.0.23-original"
EFFECTIVE="$CACHE/effective-1.21.11-neoforge"
COMMIT="23c6af379676ce5262c5c0cb6f1f331edc9b12c6"
URL="https://github.com/BCCE-team/BuildCraft.git"

test ! -d "$ROOT/mods/buildcraft-cml" || {
  echo "ABORT: retired BuildCraft CML is present; separate port requires clean source." >&2
  exit 1
}
mkdir -p "$CACHE"
if [[ ! -d "$UPSTREAM/.git" ]]; then
  [[ ! -e "$UPSTREAM" ]] || { echo "Refusing unknown upstream cache" >&2; exit 1; }
  TEMP="$(mktemp -d "$CACHE/.original-fetch-XXXXXX")"
  trap 'rm -rf "$TEMP"' EXIT
  git -C "$TEMP" init -q
  git -C "$TEMP" fetch -q --depth 1 "$URL" "$COMMIT"
  git -C "$TEMP" checkout -q --detach FETCH_HEAD
  [[ "$(git -C "$TEMP" rev-parse HEAD)" == "$COMMIT" ]]
  mv "$TEMP" "$UPSTREAM"
  trap - EXIT
fi
if [[ "$(git -C "$UPSTREAM" rev-parse HEAD)" != "$COMMIT" ]] ||
   [[ -n "$(git -C "$UPSTREAM" status --porcelain --untracked-files=all)" ]]; then
  echo "Refusing modified or incorrectly pinned original BCCE checkout" >&2
  exit 1
fi
grep -Fxq 'common.mod.version=8.0.23' "$UPSTREAM/build-config/common.properties"
grep -Fxq 'target.1.21.11-neoforge.deps.minecraft=1.21.11' "$UPSTREAM/build-config/targets.properties"
grep -Fq 'Mozilla Public License Version 2.0' "$UPSTREAM/LICENSE.txt"

# Use BCCE's OWN authoritative layered-source materializer, preserving its
# implementations rather than copying source from the retired H.O.W.L. mod.
python3 "$UPSTREAM/scripts/source_layout.py" 1.21.11-neoforge --output "$EFFECTIVE"
python3 "$PROJECT/tests/verify_original.py" "$UPSTREAM" "$EFFECTIVE"
echo "PASS: BuildCraft Reborn original BCCE 8.0.23 source imported and verified."
echo "Source: $EFFECTIVE"
echo "NOT a playable H.O.W.L. mod. Original porting still required."
