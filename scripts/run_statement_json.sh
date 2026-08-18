#!/usr/bin/env bash
#
# Local (GnuCOBOL) run harness for the DJ-96 statement JSON extract.
#
# Mainframe equivalent : app/jcl/CREASTMJ.jcl
# Program under test   : app/cbl/CBSTM04C.cbl (calls app/cbl/CBSTM03B.CBL)
# Output               : app/data/json/statements.json
#
# What it does, mirroring CREASTMJ.jcl step by step:
#   STEP010 (SORT)   -> reformat app/data/ASCII/dailytran.txt into the
#                       card-number-keyed COSTM01 layout and sort it by
#                       card number then tran id
#   STEP020 (REPRO)  -> load the four inputs into GnuCOBOL indexed files
#                       (scripts/cobol/CBSTLOAD.cbl stands in for IDCAMS)
#   STEP030 (IEFBR14)-> delete the previous run's output
#   STEP040 (CBSTM04C) -> run the extract and write statements.json
#
# Requires: GnuCOBOL (cobc/cobcrun) on PATH.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${REPO_ROOT}/build/statement-json"
CPY_DIR="${BUILD_DIR}/cpy"
DATA_DIR="${BUILD_DIR}/data"
OUT_JSON="${REPO_ROOT}/app/data/json/statements.json"
ASCII_DIR="${REPO_ROOT}/app/data/ASCII"

command -v cobc >/dev/null || { echo "cobc (GnuCOBOL) not found on PATH" >&2; exit 1; }

rm -rf "${BUILD_DIR}"
mkdir -p "${CPY_DIR}" "${DATA_DIR}"

# Some committed copybooks (CUSTREC.cpy, CSLKPCDY.cpy) indent with TAB
# characters, which pushes their PICTURE clauses out of the fixed format
# code area once tabs are expanded. Re-indent those lines to column 12 in
# a scratch copybook dir instead of touching app/cpy.
for cpy in "${REPO_ROOT}"/app/cpy/*; do
  sed $'s/^[\t ]*\t[\t ]*/           /' "${cpy}" > "${CPY_DIR}/$(basename "${cpy}")"
done

echo "=== STEP010: build the card-num keyed transaction file (SORT stand-in)"
# dailytran.txt holds CVTRA05Y records (350 bytes) with TRAN-CARD-NUM at
# offset 263. CREASTMJ.jcl SORTs them into the COSTM01 layout:
#   1:263,16   card number
#   17:1,262   tran id .. merchant zip
#   279:279,50 original / processing timestamps
# then sorts on card number, tran id (both at the front of the key).
awk '{ printf("%-350s\n", substr($0,263,16) substr($0,1,262) substr($0,279,50)) }' \
  "${ASCII_DIR}/dailytran.txt" | LC_ALL=C sort > "${DATA_DIR}/trnxfile.txt"

# XREF records are 36 bytes on disk; CBSTM03B's FD expects 50.
awk '{ printf("%-50s\n", $0) }' "${ASCII_DIR}/cardxref.txt" > "${DATA_DIR}/xreffile.txt"
cp "${ASCII_DIR}/custdata.txt" "${DATA_DIR}/custfile.txt"
cp "${ASCII_DIR}/acctdata.txt" "${DATA_DIR}/acctfile.txt"

echo "=== compile CBSTLOAD, CBSTM04C and CBSTM03B"
# -fsign=EBCDIC: the ASCII data carries zoned decimal overpunch signs
# ({ = +0, A-I = +1..+9, } = -0, J-R = -1..-9).
COBC_FLAGS=(-I "${CPY_DIR}" -fsign=EBCDIC -Wno-others)
cobc -x "${COBC_FLAGS[@]}" -o "${BUILD_DIR}/cbstload" \
  "${REPO_ROOT}/scripts/cobol/CBSTLOAD.cbl"
cobc -m "${COBC_FLAGS[@]}" -o "${BUILD_DIR}/CBSTM03B.so" \
  "${REPO_ROOT}/app/cbl/CBSTM03B.CBL"
cobc -x "${COBC_FLAGS[@]}" -o "${BUILD_DIR}/cbstm04c" \
  "${REPO_ROOT}/app/cbl/CBSTM04C.cbl"

echo "=== STEP020: load the indexed input files (IDCAMS REPRO stand-in)"
export DD_LDTRNX="${DATA_DIR}/trnxfile.txt"
export DD_LDXREF="${DATA_DIR}/xreffile.txt"
export DD_LDCUST="${DATA_DIR}/custfile.txt"
export DD_LDACCT="${DATA_DIR}/acctfile.txt"
export DD_TRNXFILE="${DATA_DIR}/TRNXFILE"
export DD_XREFFILE="${DATA_DIR}/XREFFILE"
export DD_CUSTFILE="${DATA_DIR}/CUSTFILE"
export DD_ACCTFILE="${DATA_DIR}/ACCTFILE"
"${BUILD_DIR}/cbstload"

echo "=== STEP030: delete the previous run's output"
rm -f "${OUT_JSON}" "${DATA_DIR}/JSONFILE"

echo "=== STEP040: run CBSTM04C"
export DD_JSONFILE="${DATA_DIR}/JSONFILE"
COB_LIBRARY_PATH="${BUILD_DIR}" "${BUILD_DIR}/cbstm04c"

# JSONFILE is a fixed 400 byte sequential dataset (RECFM=FB,LRECL=400),
# so locally it lands as one unbroken byte stream. Split it back into its
# records and drop the record padding to get a readable statements.json.
mkdir -p "$(dirname "${OUT_JSON}")"
fold -w 400 "${DATA_DIR}/JSONFILE" | sed 's/[[:space:]]*$//' > "${OUT_JSON}"

echo "=== wrote ${OUT_JSON}"
python3 - "${OUT_JSON}" <<'PY'
import json, sys
with open(sys.argv[1]) as fh:
    doc = json.load(fh)
stmts = doc["statements"]
print(f"statements: {len(stmts)}")
print(f"transactions: {sum(len(s['transactions']) for s in stmts)}")
PY
