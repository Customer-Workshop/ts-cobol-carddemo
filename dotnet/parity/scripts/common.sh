#!/usr/bin/env bash
# Shared setup for the CardDemo recompile-to-run parity oracle.
# Requires: GnuCOBOL (cobc), libfaketime (faketime).
set -euo pipefail

PARITY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$PARITY_DIR/../.." && pwd)"
BIN_DIR="$PARITY_DIR/work/bin"
DATA_DIR="$PARITY_DIR/work/data"
OUT_DIR="$PARITY_DIR/work/out"
GOLDEN_DIR="$PARITY_DIR/golden"

# Frozen clock for all oracle runs: CBACT04C stamps output transactions
# via FUNCTION CURRENT-DATE in addition to the PARM date, so the PARM
# alone does NOT make the run deterministic.
export FROZEN_TS="2025-08-01 09:00:00"
export PARM_DATE="2025-07-31"

# cobc dialect flags for estate sources (mainframe batch recompiled).
# -fsign=ebcdic: seed/estate data carries EBCDIC sign overpunch
# ({ } A-R), visible in app/data/ASCII/*.txt.
ESTATE_COBC_FLAGS=(-std=ibm -fsign=ebcdic -I "$REPO_ROOT/app/cpy")
# Harness programs (writers/drivers/readers) use the same sign
# convention so seeded bytes match estate expectations.
HARNESS_COBC_FLAGS=(-fsign=ebcdic)

build_estate() {
  mkdir -p "$BIN_DIR"
  # Estate programs: compiled UNMODIFIED as dynamic modules with
  # mainframe dialect flags. Harness programs: default dialect
  # (they use GnuCOBOL extensions like ACCEPT FROM ENVIRONMENT).
  cobc -m "${ESTATE_COBC_FLAGS[@]}" -o "$BIN_DIR/CBACT04C.so" \
    "$REPO_ROOT/app/cbl/CBACT04C.cbl"
  cobc -m "${ESTATE_COBC_FLAGS[@]}" -o "$BIN_DIR/CBTRN03C.so" \
    "$REPO_ROOT/app/cbl/CBTRN03C.cbl"
  cobc -m "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/CEE3ABD.so" \
    "$PARITY_DIR/stubs/CEE3ABD.cbl"
  cobc -x "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/wrseed" \
    "$PARITY_DIR/tools/WRSEED.cbl"
  cobc -x "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/rdunload" \
    "$PARITY_DIR/tools/RDUNLOAD.cbl"
  cobc -x "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/drvact04" \
    "$PARITY_DIR/drivers/DRVACT04.cbl"
  export COB_LIBRARY_PATH="$BIN_DIR${COB_LIBRARY_PATH:+:$COB_LIBRARY_PATH}"
}

export_dds() {
  export DD_ACCTFILE="$DATA_DIR/ACCTFILE.isam"
  export DD_TCATBALF="$DATA_DIR/TCATBALF.isam"
  export DD_XREFFILE="$DATA_DIR/XREFFILE.isam"
  export DD_CARDXREF="$DATA_DIR/CARDXREF.isam"
  export DD_DISCGRP="$DATA_DIR/DISCGRP.isam"
  export DD_TRANTYPE="$DATA_DIR/TRANTYPE.isam"
  export DD_TRANCATG="$DATA_DIR/TRANCATG.isam"
  export DD_TRANFILE="$DATA_DIR/TRANFILE.dat"
  export DD_TRANFIL2="$DATA_DIR/TRANFIL2.dat"
  export DD_DATEPARM="$DATA_DIR/DATEPARM.txt"
}

seed_fixtures() {
  rm -rf "$DATA_DIR"
  mkdir -p "$DATA_DIR"
  export_dds
  "$BIN_DIR/wrseed"
}

unload() { # unload <ACCT|TCAT> <output-path>
  export UNLOAD_TARGET="$1"
  export DD_UNLOADOUT="$2"
  "$BIN_DIR/rdunload"
  unset UNLOAD_TARGET DD_UNLOADOUT
}
