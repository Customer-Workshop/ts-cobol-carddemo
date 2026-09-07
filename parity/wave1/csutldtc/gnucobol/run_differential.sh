#!/usr/bin/env bash
# GnuCOBOL differential for CSUTLDTC (see .migration/07_runbook.md §1). Real CSUTLDTC.cbl, stub CEEDAYS.
# Output: differential_raw.txt (hexdump of the 80-byte LS-RESULT per case) -- the tool output never
# overwrites the derivation in cases.csv; it is compared against it in results.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../../.." && pwd)
BUILD=$(mktemp -d)
cobc -x -o "$BUILD/driver" "$HERE/DRIVER.cbl" "$ROOT/app/cbl/CSUTLDTC.cbl" "$HERE/CEEDAYS.cbl"
OUT="$HERE/differential_raw.txt"; : > "$OUT"
tail -n +2 "$HERE/../cases.csv" | while IFS=, read -r id req date mask sev msg rest; do
  printf '== %s date=[%s] mask=[%s] stub_code=%s\n' "$id" "$date" "$mask" "$msg" >> "$OUT"
  STUB_DATE="$date" STUB_MASK="$mask" STUB_CODE="$msg" "$BUILD/driver" > "$BUILD/o.bin" || true
  head -c 80 "$BUILD/o.bin" | xxd -c 40 >> "$OUT"
  tail -n 1 "$BUILD/o.bin" >> "$OUT"
done
rm -rf "$BUILD"; cat "$OUT"
