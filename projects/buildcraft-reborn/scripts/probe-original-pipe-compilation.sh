#!/usr/bin/env bash
# Compatibility entry point used by CI. A failed original build MUST fail CI.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BASE="$ROOT/dist/buildcraft-reborn"
mkdir -p "$BASE"
set +e
bash "$ROOT/projects/buildcraft-reborn/scripts/compile-original-pipes.sh" --holders-only
RESULT=$?
set -e
if [[ -f "$BASE/original-pipes-javac-errors.txt" ]]; then
  cp "$BASE/original-pipes-javac-errors.txt" "$BASE/pipe-holder-javac-errors.txt"
fi
if [[ $RESULT -eq 0 ]]; then
  printf 'BLOCK_PIPE_HOLDER=COMPILED\nTILE_PIPE_HOLDER=COMPILED\n' > "$BASE/pipe-holder-compiler-report.txt"
else
  printf 'BLOCK_PIPE_HOLDER=NOT_COMPILED\nTILE_PIPE_HOLDER=NOT_COMPILED\nBUILD_EXIT=%s\n' "$RESULT" > "$BASE/pipe-holder-compiler-report.txt"
fi
cat "$BASE/pipe-holder-compiler-report.txt"
exit "$RESULT"
