#!/usr/bin/env bash
# Wave 2 (CBTRN03C) fixture capture/verify.
#
# The tran-report parity tests replay the exact oracle inputs through the
# .NET port, so those inputs are committed as immutable fixtures under
# dotnet/parity/fixtures/tran-report-inputs/:
#   TRANFILE.dat / TRANFIL2.dat  350-byte record-sequential transactions
#   DATEPARM.txt                 80-byte date window parm record
#   CARDXREF.unload              keyed-order 50-byte card xref unload
#   TRANTYPE.unload              keyed-order 60-byte tran type unload
#   TRANCATG.unload              keyed-order 60-byte tran category unload
# All are produced by the same WRSEED seeding the goldens were captured
# from; the lookup unloads come from RDUNLD2 (keyed order, never raw
# ISAM bytes). Like the goldens, committed fixtures are immutable.
#
# Default mode verifies the committed fixtures still match a fresh
# seed+unload (falsifiability / drift check). --capture writes them
# (first capture only).
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

FIXTURE_DIR="$PARITY_DIR/fixtures/tran-report-inputs"
STAGE_DIR="$PARITY_DIR/work/fixture-stage/tran-report-inputs"
FILES=(TRANFILE.dat TRANFIL2.dat DATEPARM.txt CARDXREF.unload TRANTYPE.unload TRANCATG.unload)

build_estate
cobc -x "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/rdunld2" \
  "$PARITY_DIR/tools/RDUNLD2.cbl"

seed_fixtures

unload2() { # unload2 <CXRF|TTYP|TCTG> <output-path>
  export UNLOAD_TARGET="$1"
  export DD_UNLOADOUT="$2"
  "$BIN_DIR/rdunld2"
  unset UNLOAD_TARGET DD_UNLOADOUT
}

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
cp "$DATA_DIR/TRANFILE.dat" "$STAGE_DIR/TRANFILE.dat"
cp "$DATA_DIR/TRANFIL2.dat" "$STAGE_DIR/TRANFIL2.dat"
cp "$DATA_DIR/DATEPARM.txt" "$STAGE_DIR/DATEPARM.txt"
unload2 CXRF "$STAGE_DIR/CARDXREF.unload"
unload2 TTYP "$STAGE_DIR/TRANTYPE.unload"
unload2 TCTG "$STAGE_DIR/TRANCATG.unload"

if [[ "${1:-verify}" == "--capture" ]]; then
  if [[ -e "$FIXTURE_DIR/SHA256SUMS" ]]; then
    echo "FIXTURES ALREADY CAPTURED ($FIXTURE_DIR is immutable)" >&2
    exit 1
  fi
  mkdir -p "$FIXTURE_DIR"
  cp "${FILES[@]/#/$STAGE_DIR/}" "$FIXTURE_DIR/"
  (cd "$FIXTURE_DIR" && sha256sum "${FILES[@]}" > SHA256SUMS)
  echo "FIXTURES CAPTURED to $FIXTURE_DIR"
else
  for f in "${FILES[@]}"; do
    cmp "$FIXTURE_DIR/$f" "$STAGE_DIR/$f"
  done
  (cd "$FIXTURE_DIR" && sha256sum -c SHA256SUMS)
  echo "TRAN-REPORT INPUT FIXTURES OK"
fi
