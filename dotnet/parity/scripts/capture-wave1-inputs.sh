#!/usr/bin/env bash
# Wave-1 input capture: keyed-order unloads of DISCGRP and XREFFILE
# (the two CBACT04C inputs wave 0 did not surface), produced by the
# same COBOL seeder the goldens were captured from. The committed
# fixtures under parity/fixtures/interest-calc-inputs/ are immutable
# once captured, exactly like goldens: a mismatch means the harness
# drifted, never that the fixtures should be regenerated.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

FIXTURE_DIR="$PARITY_DIR/fixtures/interest-calc-inputs"

build_estate
cobc -x "${HARNESS_COBC_FLAGS[@]}" -o "$BIN_DIR/rdunld41" \
  "$PARITY_DIR/tools/RDUNLD41.cbl"
seed_fixtures

mkdir -p "$FIXTURE_DIR"
unload41() { # unload41 <DISC|XREF> <output-path>
  export UNLOAD_TARGET="$1"
  export DD_UNLOADOUT="$2"
  "$BIN_DIR/rdunld41"
  unset UNLOAD_TARGET DD_UNLOADOUT
}
unload41 DISC "$FIXTURE_DIR/DISCGRP.seed.unload"
unload41 XREF "$FIXTURE_DIR/XREFFILE.seed.unload"

(cd "$FIXTURE_DIR" && sha256sum DISCGRP.seed.unload XREFFILE.seed.unload)
echo "capture-wave1-inputs: fixtures in $FIXTURE_DIR"
