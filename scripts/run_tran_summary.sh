#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="${TMPDIR:-/tmp}/ts-cobol-carddemo-tran-summary-build"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ts-cobol-carddemo-tran-summary.XXXXXX")"
PROGRAM="$BUILD_DIR/CBTRN04C"

cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$BUILD_DIR" "$REPO_ROOT/reports"

echo "Compiling CBTRN04C"
echo "cobc -x --std=ibm-strict -fsign=EBCDIC -I app/cpy -o $PROGRAM app/cbl/CBTRN04C.cbl"
(
    cd "$REPO_ROOT"
    cobc -x --std=ibm-strict -fsign=EBCDIC -I app/cpy \
        -o "$PROGRAM" app/cbl/CBTRN04C.cbl
)

echo "Preparing fixed-length input files"
tr -d '\r\n' < "$REPO_ROOT/app/data/ASCII/dailytran.txt" \
    > "$WORK_DIR/dailytran.fb"
tr -d '\r\n' < "$REPO_ROOT/app/data/ASCII/trantype.txt" \
    > "$WORK_DIR/trantype.fb"
tr -d '\r\n' < "$REPO_ROOT/app/data/ASCII/trancatg.txt" \
    > "$WORK_DIR/trancatg.fb"

echo "Running CBTRN04C"
TRANFILE="$WORK_DIR/dailytran.fb" \
TRANTYPE="$WORK_DIR/trantype.fb" \
TRANCATG="$WORK_DIR/trancatg.fb" \
TRANSUMM="$REPO_ROOT/reports/tran_summary.csv" \
"$PROGRAM"
