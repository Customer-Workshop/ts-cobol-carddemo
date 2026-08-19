#!/usr/bin/env bash
# Runs the oracle end-to-end: build estate, seed fixtures, execute the
# two batch scenarios under a frozen clock, and drop the parity
# surfaces into work/out/<scenario>/.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

build_estate
seed_fixtures

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/data-roundtrip" "$OUT_DIR/interest-calc" \
         "$OUT_DIR/tran-report" "$OUT_DIR/tran-report-eof"

# --- data-roundtrip: pre-run keyed unloads (decoder fixtures) --------
unload ACCT "$OUT_DIR/data-roundtrip/ACCTFILE.seed.unload"
unload TCAT "$OUT_DIR/data-roundtrip/TCATBAL.seed.unload"

# --- interest-calc: CBACT04C (INTCALC.jcl equivalent) ----------------
# TRANSACT is CBACT04C's OUTPUT transaction file.
export DD_TRANSACT="$OUT_DIR/interest-calc/TRANSACT.dat"
faketime -f "$FROZEN_TS" "$BIN_DIR/drvact04" \
  > "$OUT_DIR/interest-calc/run.log"
unset DD_TRANSACT
# Post-run keyed unloads: byte-exact, INCLUDING accounts 9-12 which
# CBACT04C never touches (missed-REWRITE detector).
unload ACCT "$OUT_DIR/interest-calc/ACCTFILE.post.unload"
unload TCAT "$OUT_DIR/interest-calc/TCATBAL.post.unload"

# --- tran-report: CBTRN03C (TRANREPT.jcl equivalent) -----------------
# Input contains out-of-window txns from #125 on: the estate's report
# loop terminates at the first one (NEXT SENTENCE exits the PERFORM).
# Preserved bug-for-bug; see 03-contract.md SEM-33.
export DD_TRANREPT="$OUT_DIR/tran-report/TRANREPT.rpt"
faketime -f "$FROZEN_TS" cobcrun CBTRN03C \
  > "$OUT_DIR/tran-report/run.log"
unset DD_TRANREPT

# --- tran-report-eof: same report, all txns in-window ----------------
# Exercises the EOF page-total + grand-total path.
export DD_TRANFILE="$DATA_DIR/TRANFIL2.dat"
export DD_TRANREPT="$OUT_DIR/tran-report-eof/TRANREPT.rpt"
faketime -f "$FROZEN_TS" cobcrun CBTRN03C \
  > "$OUT_DIR/tran-report-eof/run.log"
unset DD_TRANREPT
export DD_TRANFILE="$DATA_DIR/TRANFILE.dat"

# run.log files are diagnostics, not parity surfaces (data outputs and
# reports are gated; DISPLAY logs are not).
rm -f "$OUT_DIR"/*/run.log
echo "run-scenarios: outputs in $OUT_DIR"
