#!/usr/bin/env bash
#
# Build and run the local GnuCOBOL equivalent of CREASTMJ.JCL.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${REPO_ROOT}/build/statement-json"
CPY_DIR="${BUILD_DIR}/cpy"
DATA_DIR="${BUILD_DIR}/data"
ASCII_DIR="${REPO_ROOT}/app/data/ASCII"
OUT_JSON="${REPO_ROOT}/app/data/json/statements.json"

command -v cobc >/dev/null || {
  echo "cobc (GnuCOBOL) not found on PATH" >&2
  exit 1
}

rm -rf "${BUILD_DIR}"
mkdir -p "${CPY_DIR}" "${DATA_DIR}"

# Keep committed copybooks unchanged.  The tab-indented copybooks are
# re-indented in the scratch directory so level numbers start in column 12.
for cpy in "${REPO_ROOT}"/app/cpy/*; do
  sed $'s/^[\t ]*\t[\t ]*/           /' "${cpy}" \
    > "${CPY_DIR}/$(basename "${cpy}")"
done

echo "=== STEP010: sort/reformat transaction records"
awk '{ printf("%-350s\n", substr($0,263,16) substr($0,1,262) substr($0,279,50)) }' \
  "${ASCII_DIR}/dailytran.txt" | LC_ALL=C sort > "${DATA_DIR}/trnxfile.txt"
awk '{ printf("%-50s\n", $0) }' "${ASCII_DIR}/cardxref.txt" \
  > "${DATA_DIR}/xreffile.txt"
cp "${ASCII_DIR}/custdata.txt" "${DATA_DIR}/custfile.txt"
cp "${ASCII_DIR}/acctdata.txt" "${DATA_DIR}/acctfile.txt"

echo "=== compile local loader, CBSTM03B and CBSTM04C"
COBC_FLAGS=(-I "${CPY_DIR}" -fsign=EBCDIC)
cobc "${COBC_FLAGS[@]}" -x -o "${BUILD_DIR}/cbstload" \
  "${REPO_ROOT}/scripts/cobol/CBSTLOAD.cbl"
cobc "${COBC_FLAGS[@]}" -m -o "${BUILD_DIR}/CBSTM03B.so" \
  "${REPO_ROOT}/app/cbl/CBSTM03B.CBL"
cobc "${COBC_FLAGS[@]}" -x -o "${BUILD_DIR}/cbstm04c" \
  "${REPO_ROOT}/app/cbl/CBSTM04C.cbl"

echo "=== STEP020: load indexed files (IDCAMS REPRO stand-in)"
export DD_LDTRNX="${DATA_DIR}/trnxfile.txt"
export DD_LDXREF="${DATA_DIR}/xreffile.txt"
export DD_LDCUST="${DATA_DIR}/custfile.txt"
export DD_LDACCT="${DATA_DIR}/acctfile.txt"
export DD_TRNXFILE="${DATA_DIR}/TRNXFILE"
export DD_XREFFILE="${DATA_DIR}/XREFFILE"
export DD_CUSTFILE="${DATA_DIR}/CUSTFILE"
export DD_ACCTFILE="${DATA_DIR}/ACCTFILE"
"${BUILD_DIR}/cbstload"

echo "=== STEP030: delete previous JSON output"
rm -f "${OUT_JSON}" "${DATA_DIR}/JSONFILE"

echo "=== STEP040: run CBSTM04C"
export DD_JSONFILE="${DATA_DIR}/JSONFILE"
COB_LIBRARY_PATH="${BUILD_DIR}" "${BUILD_DIR}/cbstm04c"

# JSONFILE is fixed-record FB/LRECL=400.  Convert its records to a normal
# newline-delimited JSON document and remove record padding.
fold -w 400 "${DATA_DIR}/JSONFILE" | sed 's/[[:space:]]*$//' > "${OUT_JSON}"
echo "=== wrote ${OUT_JSON}"
python3 - "${OUT_JSON}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    document = json.load(fh)
print(f"statements: {len(document['statements'])}")
print(f"transactions: {sum(len(s['transactions']) for s in document['statements'])}")
PY
