#!/usr/bin/env bash
#
# Build and run the local GnuCOBOL equivalent of INTRPT.jcl.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${REPO_ROOT}/build/interest-report"
CPY_DIR="${BUILD_DIR}/cpy"
DATA_DIR="${BUILD_DIR}/data"
ASCII_DIR="${REPO_ROOT}/app/data/ASCII"
OUT_CSV="${REPO_ROOT}/app/data/reports/interest-summary.csv"

command -v cobc >/dev/null || {
  echo "cobc (GnuCOBOL) not found on PATH" >&2
  exit 1
}

rm -rf "${BUILD_DIR}"
mkdir -p "${CPY_DIR}" "${DATA_DIR}" "$(dirname "${OUT_CSV}")"

# Keep committed copybooks unchanged.  The tab-indented copybooks are
# re-indented in the scratch directory so level numbers start in column 12.
for cpy in "${REPO_ROOT}"/app/cpy/*; do
  sed $'s/^[\t ]*\t[\t ]*/           /' "${cpy}" \
    > "${CPY_DIR}/$(basename "${cpy}")"
done

echo "=== prepare fixed-length input records"
tr -d '\r' < "${ASCII_DIR}/tcatbal.txt" \
  | awk '{ printf("%-50s\n", substr($0,1,50)) }' \
  > "${DATA_DIR}/tcatbal.txt"
tr -d '\r' < "${ASCII_DIR}/acctdata.txt" \
  | awk '{ printf("%-300s\n", substr($0,1,300)) }' \
  > "${DATA_DIR}/acctdata.txt"
tr -d '\r' < "${ASCII_DIR}/discgrp.txt" \
  | awk '{ printf("%-50s\n", substr($0,1,50)) }' \
  > "${DATA_DIR}/discgrp.txt"

echo "=== compile local loader and CBACT05C"
COBC_FLAGS=(-I "${CPY_DIR}" -fsign=EBCDIC)
cobc "${COBC_FLAGS[@]}" -x -o "${BUILD_DIR}/cbinload" \
  "${REPO_ROOT}/scripts/cobol/CBINLOAD.cbl"
cobc "${COBC_FLAGS[@]}" -x -o "${BUILD_DIR}/cbact05c" \
  "${REPO_ROOT}/app/cbl/CBACT05C.cbl"

echo "=== load indexed files (IDCAMS REPRO stand-in)"
export DD_LDTCATB="${DATA_DIR}/tcatbal.txt"
export DD_LDACCT="${DATA_DIR}/acctdata.txt"
export DD_LDDISC="${DATA_DIR}/discgrp.txt"
export DD_TCATBALF="${DATA_DIR}/TCATBALF"
export DD_ACCTFILE="${DATA_DIR}/ACCTFILE"
export DD_DISCGRP="${DATA_DIR}/DISCGRP"
"${BUILD_DIR}/cbinload"

echo "=== run CBACT05C"
export DD_INTRPT="${DATA_DIR}/INTRPT"
COB_LIBRARY_PATH="${BUILD_DIR}" "${BUILD_DIR}/cbact05c"

# INTRPT is fixed-record FB/LRECL=120.  Convert its records to CSV and
# remove only the fixed-record padding.
fold -w 120 "${DATA_DIR}/INTRPT" | sed 's/[[:space:]]*$//' > "${OUT_CSV}"
printf '\n' >> "${OUT_CSV}"

DATA_ROWS=$(($(wc -l < "${OUT_CSV}") - 1))
echo "=== wrote ${OUT_CSV}"
echo "data rows: ${DATA_ROWS}"
