#!/usr/bin/env bash
# COACTVWC parity: HTTP cases against the running backend (default profile, PostgreSQL 16 on :5433).
# Usage: BASE=http://localhost:8080 ./run_http_cases.sh > http_cases.out
# Fixture: wave-1 `import` profile (50/50/50 + 10 USRSEC) + seed_synthetic.sql. Nothing under backend/ is modified.
# Expected values come from cases.md / expected_fixture.json (derived from the COBOL + raw bytes before this script was written).
set -u
BASE="${BASE:-http://localhost:8080}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${OUT_DIR:-$HERE/http}"
mkdir -p "$OUT_DIR"
PSQL="docker exec carddemo-pg psql -U carddemo -d carddemo"
JAR="$OUT_DIR/jar_user.txt"; JAR_A="$OUT_DIR/jar_admin.txt"
E04='No input received'
E05='Account Filter must  be a non-zero 11 digit number'

hdr() { echo; echo "=================== $1 ==================="; }
# req <id> <method> <path> [json] [cookiejar] [extra curl args...]
req() {
  local id="$1" m="$2" p="$3" body="${4:-}" jar="${5:-}"; shift 5 || shift $#
  local args=(-s -o "$OUT_DIR/$id.body" -w '%{http_code}' -X "$m" -H 'Content-Type: application/json')
  [ -n "$jar" ] && args+=(-b "$jar" -c "$jar")
  [ -n "$body" ] && args+=(--data-binary "$body")
  local code; code=$(curl "${args[@]}" "$@" "$BASE$p")
  echo "REQUEST  $m $p  body=${body:-<none>}  cookies=${jar:+yes}${jar:-none}"
  echo "STATUS   $code"
  echo "BODY     $(cat "$OUT_DIR/$id.body")"
  echo "$code" > "$OUT_DIR/$id.status"
}
jget() { python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
for k in sys.argv[2].split("."):
    d = d.get(k) if isinstance(d,dict) else None
print("" if d is None else (d.rstrip() if isinstance(d,str) else d))' "$1" "$2" 2>/dev/null || echo '<nojson>'; }
# view <id> <acct-path> <jar> <expected-status> <expected-message-prefix-or-> <contains-or->
view() {
  local id="$1" acct="$2" jar="$3" es="$4" ep="$5" ec="$6"
  req "$id" GET "/api/accounts/$acct" "" "$jar"
  local st msg v=PASS; st=$(cat "$OUT_DIR/$id.status"); msg=$(jget "$OUT_DIR/$id.body" message)
  [ "$st" = "$es" ] || v=FAIL
  [ "$ep" = "-" ] || [ "${msg#"$ep"}" != "$msg" ] || v=FAIL
  [ "$ec" = "-" ] || [[ "$msg" == *"$ec"* ]] || v=FAIL
  echo "EXPECT   status=$es message-prefix=[$ep] contains=[$ec]"
  echo "MSGLEN   ${#msg}"
  echo "VERDICT  $v"
}

echo "run at $(date -u +%FT%TZ)  BASE=$BASE"
echo "postgres: $($PSQL -Atc 'select version();')"
echo "rows: $($PSQL -Atc 'select (select count(*) from accounts),(select count(*) from customers),(select count(*) from card_xrefs),(select count(*) from users)')"
rm -f "$JAR" "$JAR_A"

hdr "A-17 unauthenticated GET /api/accounts/00000000027 and GET /api/accounts (CAVW without COMMAREA -> 401, FR §7)"
req A-17a GET /api/accounts/00000000027 "" ""
req A-17b GET /api/accounts "" ""
for i in a b; do echo "VERDICT  $([ "$(cat $OUT_DIR/A-17$i.status)" = 401 ] && echo PASS || echo FAIL)"; done

hdr "sign on USER0001 (U) and ADMIN001 (A) - COSGN00C, outside scope; only to get sessions (A-19 baseline)"
req SGN-U POST /api/auth/signon '{"userId":"USER0001","password":"PASSWORD"}' "$JAR"
req SGN-A POST /api/auth/signon '{"userId":"ADMIN001","password":"PASSWORD"}' "$JAR_A"
req A-19a GET /api/auth/session "" "$JAR"

hdr "A-01 initial SEND MAP state is rendered locally by the SPA (FR §7 'entry from menu: local render, no API call') -> verified in the UI run; here GET /api/accounts (empty id) must behave as blank input = E-04 (cbl:628-633)"
view A-01 "" "$JAR" 400 "$E04" -

hdr "A-02 blank / '*' / spaces -> 400 E-04 (cbl:628-633, 640-642)"
view A-02a "%20" "$JAR" 400 "$E04" -
view A-02b "*" "$JAR" 400 "$E04" -
view A-02c "%20%20%20%20%20%20%20%20%20%20%20" "$JAR" 400 "$E04" -
echo "-- A-02a field echo (cbl:632 MOVE '*' when blank) - 400 ErrorResponse carries no accountNumber; the '*' echo is a SPA rendering rule, asserted in the UI run:"; echo "ACCTNUM  $(jget $OUT_DIR/A-02a.body accountNumber)"

hdr "A-03 NOT NUMERIC / zero / short / long -> 400 E-05 (cbl:666-676; Q-02)"
view A-03a 00000000000 "$JAR" 400 "$E05" -
view A-03b 1234567890A "$JAR" 400 "$E05" -
view A-03c "12%2034567890" "$JAR" 400 "$E05" -
view A-03d 1234567890 "$JAR" 400 "$E05" -
view A-03e 123456789012 "$JAR" 400 "$E05" -
view A-03f -0000000001 "$JAR" 400 "$E05" -
echo "-- A-03b field echo (cbl:557-559 value echoed):"; echo "ACCTNUM  $(jget $OUT_DIR/A-03b.body accountNumber)"

hdr "A-04 no xref -> 404 E-09 (cbl:741-758)"
view A-04 00000000099 "$JAR" 404 "Account:00000000099 not found in Cross ref file.  Resp:" " Reas:"
echo "BLOCKS   account=$(jget $OUT_DIR/A-04.body account) customer=$(jget $OUT_DIR/A-04.body customer)"

hdr "A-05 xref ok, ACCTDAT missing -> 404 E-10, DV-01 no blocks (cbl:786-807)"
view A-05 00000000901 "$JAR" 404 "Account:00000000901 not found in Acct Master file.Resp:" " Reas:"
echo "BLOCKS   account=$(jget $OUT_DIR/A-05.body account) customer=$(jget $OUT_DIR/A-05.body customer)  DV-01 $([ "$(jget $OUT_DIR/A-05.body account)" = "" ] && [ "$(jget $OUT_DIR/A-05.body customer)" = "" ] && echo PASS || echo FAIL)"

hdr "A-06 xref+acct ok, CUSTDAT missing -> 404 E-11 WITH account block (cbl:836-857, :471-493)"
view A-06 00000000902 "$JAR" 404 "CustId:999999902 not found in customer master.Resp: " " REAS:"
echo "ACCOUNT-BLOCK $(jget $OUT_DIR/A-06.body account)"
echo "ACCT-BLOCK-PRESENT $([ -n "$(jget $OUT_DIR/A-06.body account.currentBalance)" ] && echo PASS || echo FAIL)"

hdr "A-07/A-08/A-09/A-10 happy path + field parity for 5 fixture accounts (expected_fixture.json from raw bytes)"
for a in 00000000001 00000000010 00000000027 00000000033 00000000050; do
  view A-08-$a "$a" "$JAR" 200 - -
done
python3 "$HERE/compare_fields.py" "$HERE/expected_fixture.json" "$OUT_DIR" 00000000001 00000000010 00000000027 00000000033 00000000050

hdr "A-11 Q-04 two xref rows for acct 903 (cards ...02 -> cust 2, ...01 -> cust 3): lowest card wins -> customer 000000003"
view A-11 00000000903 "$JAR" 200 - -
echo "CUSTID   $(jget $OUT_DIR/A-11.body customer.customerId)  $([ "$(jget $OUT_DIR/A-11.body customer.customerId)" = "000000003" ] && echo PASS || echo FAIL)"
echo "PG rows: $($PSQL -Atc "select xref_card_number, xref_cust_id from card_xrefs where xref_acct_id=903 order by xref_card_number")"

hdr "A-18 Q-14 payload validation: 30-char id, URL-encoded junk -> 400 E-05 class, never 500"
view A-18a 123456789012345678901234567890 "$JAR" 400 "$E05" -
view A-18b "%27%3Bdrop" "$JAR" 400 - -
echo "NOTE     A-18b: '%3B' (;) is rejected by the servlet container before the controller (generic 400 body, no program text) - status class asserted only"
view A-18c "abc%27def12" "$JAR" 400 "$E05" -
view A-18d "0000000002%25" "$JAR" 400 - -
echo "NOTE     A-18d: encoded '%' is likewise rejected by the container (generic 400) - status class asserted only"

hdr "A-19 DV-02 / B-0027: session unchanged after account view (U and A)"
req A-19b GET /api/auth/session "" "$JAR"
echo "SESSION-U before=$(cat $OUT_DIR/A-19a.body)"
echo "SESSION-U after =$(cat $OUT_DIR/A-19b.body)  $([ "$(jget $OUT_DIR/A-19a.body userId)$(jget $OUT_DIR/A-19a.body userType)" = "$(jget $OUT_DIR/A-19b.body userId)$(jget $OUT_DIR/A-19b.body userType)" ] && echo PASS || echo FAIL)"
req A-19c GET /api/auth/session "" "$JAR_A"
view A-19d 00000000027 "$JAR_A" 200 - -
req A-19e GET /api/auth/session "" "$JAR_A"
echo "SESSION-A $([ "$(cat $OUT_DIR/A-19c.body)" = "$(cat $OUT_DIR/A-19e.body)" ] && echo PASS || echo FAIL)"

hdr "A-12 E-12 (WHEN OTHER on CXACAIX read): PostgreSQL stopped -> 500 'File Error: READ ... CXACAIX ... returned RESP ...,RESP2 ...' (cbl:760-766, D-0040)"
docker stop carddemo-pg >/dev/null; sleep 2
view A-12 00000000027 "$JAR" 500 "File Error: READ" ",RESP2 "
echo "CONTAINS-CXACAIX $([[ "$(jget $OUT_DIR/A-12.body message)" == *CXACAIX* ]] && echo PASS || echo FAIL)"
echo "CONTAINS-'returned RESP ' $([[ "$(jget $OUT_DIR/A-12.body message)" == *"returned RESP "* ]] && echo PASS || echo FAIL)"
docker start carddemo-pg >/dev/null; sleep 6
view A-12r 00000000027 "$JAR" 200 - -
echo "RECOVERED $([ "$(cat $OUT_DIR/A-12r.status)" = 200 ] && echo PASS || echo FAIL)"

hdr "A-13 E-12 on the ACCTDAT read only: xref read succeeds, then accounts table renamed away -> 500 'File Error: READ ... ACCTDAT ...' (cbl:809-816)"
$PSQL -Atc 'ALTER TABLE accounts RENAME TO accounts_parity_hidden' >/dev/null
view A-13 00000000027 "$JAR" 500 "File Error: READ" ",RESP2 "
echo "CONTAINS-ACCTDAT $([[ "$(jget $OUT_DIR/A-13.body message)" == *"on ACCTDAT "* ]] && echo PASS || echo FAIL)"
$PSQL -Atc 'ALTER TABLE accounts_parity_hidden RENAME TO accounts' >/dev/null

hdr "A-14 E-12 on the CUSTDAT read only: xref + account succeed, customers table renamed away -> 500 'File Error: READ ... CUSTDAT ...' (cbl:858-865)"
$PSQL -Atc 'ALTER TABLE customers RENAME TO customers_parity_hidden' >/dev/null
view A-14 00000000027 "$JAR" 500 "File Error: READ" ",RESP2 "
echo "CONTAINS-CUSTDAT $([[ "$(jget $OUT_DIR/A-14.body message)" == *"on CUSTDAT "* ]] && echo PASS || echo FAIL)"
$PSQL -Atc 'ALTER TABLE customers_parity_hidden RENAME TO customers' >/dev/null
view A-14r 00000000027 "$JAR" 200 - -

hdr "A-24 message lengths observed (75-byte rule: E-09/E-10 71+4, E-11 68+7, E-12 exactly 75)"
for c in A-04 A-05 A-06 A-12; do m=$(jget $OUT_DIR/$c.body message); echo "$c len=${#m} [$m]"; done
echo; echo "done $(date -u +%FT%TZ)"
