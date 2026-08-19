#!/usr/bin/env bash
# Parity gate. Three checks per scenario:
#   1. golden immutability: plain `sha256sum -c SHA256SUMS` inside each
#      golden directory,
#   2. oracle reproducibility: re-run the COBOL oracle and require the
#      regenerated outputs to be byte-identical to the goldens,
#   3. prints PARITY OK only if every surface matches.
# A parity failure against the goldens is authoritative: never adjust
# a golden to make a run pass.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

fail=0

for g in "$GOLDEN_DIR"/*/; do
  scenario="$(basename "$g")"
  echo "== golden immutability: $scenario"
  (cd "$g" && sha256sum -c SHA256SUMS) || fail=1
done

"$SCRIPT_DIR/run-scenarios.sh"

for g in "$GOLDEN_DIR"/*/; do
  scenario="$(basename "$g")"
  echo "== oracle re-run vs golden: $scenario"
  while read -r _hash file; do
    if ! cmp -s "$g/$file" "$OUT_DIR/$scenario/$file"; then
      echo "PARITY MISMATCH: $scenario/$file"
      fail=1
    else
      echo "match: $scenario/$file"
    fi
  done < "$g/SHA256SUMS"
done

if [ "$fail" -ne 0 ]; then
  echo "PARITY FAIL"
  exit 1
fi
echo "PARITY OK"
