#!/usr/bin/env bash
# COSGN00C parity: HTTP cases against the running backend (default profile, PostgreSQL 16 on :5433).
# Usage: BASE=http://localhost:8080 ./run_http_cases.sh > http_cases.out
# Fixture: wave-1 `import` profile (10 USRSEC rows, legacy password PASSWORD). No implementation is modified.
set -u
BASE="${BASE:-http://localhost:8080}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)/http"
mkdir -p "$OUT_DIR"
PSQL="docker exec carddemo-pg psql -U carddemo -d carddemo"

hdr() { echo; echo "=================== $1 ==================="; }
# post <case> <json> [cookiejar-in] [cookiejar-out]
post() {
  local id="$1" body="$2" cin="${3:-}" cout="${4:-}"
  local args=(-s -o "$OUT_DIR/$id.body" -w '%{http_code}' -H 'Content-Type: application/json')
  [ -n "$cin" ] && args+=(-b "$cin")
  [ -n "$cout" ] && args+=(-c "$cout")
  local code
  code=$(curl "${args[@]}" -X POST --data "$body" "$BASE/api/auth/signon")
  echo "REQUEST  POST /api/auth/signon  body=$body"
  echo "STATUS   $code"
  echo "BODY     $(cat "$OUT_DIR/$id.body")"
}
get() {
  local id="$1" path="$2" cin="${3:-}"
  local args=(-s -o "$OUT_DIR/$id.body" -w '%{http_code}')
  [ -n "$cin" ] && args+=(-b "$cin")
  local code; code=$(curl "${args[@]}" "$BASE$path")
  echo "REQUEST  GET $path  cookies=${cin:-none}"
  echo "STATUS   $code"
  echo "BODY     $(cat "$OUT_DIR/$id.body")"
}
row() { $PSQL -Atc "select sec_usr_id, sec_usr_type, coalesce(left(sec_usr_pwd_hash,12),'<null>'), coalesce(length(sec_usr_pwd_hash)::text,'<null>'), coalesce(length(sec_usr_pwd_legacy)::text,'<null>') from users where sec_usr_id='$1';"; }

echo "run at $(date -u +%FT%TZ)  BASE=$BASE"
echo "postgres: $($PSQL -Atc 'select version();')"

hdr "P-01 header (initial screen data) GET /api/auth/header"
get P-01 /api/auth/header

hdr "P-02 blank user id"
post P-02 '{"userId":"","password":"PASSWORD"}'
hdr "P-02b user id spaces only"
post P-02b '{"userId":"   ","password":"PASSWORD"}'
hdr "P-02c user id null"
post P-02c '{"password":"PASSWORD"}'

hdr "P-03 blank password"
post P-03 '{"userId":"USER0001","password":""}'
hdr "P-03b password null"
post P-03b '{"userId":"USER0001"}'

hdr "P-04 both blank (order: user id first)"
post P-04 '{"userId":"","password":""}'
hdr "P-04b empty object"
post P-04b '{}'

hdr "P-13b wrong password BEFORE upgrade (USER0002) - row must not change"
echo "ROW before: $(row USER0002)"
post P-13b '{"userId":"USER0002","password":"WRONGPWD"}'
echo "ROW after : $(row USER0002)"

hdr "P-08 unknown user (NOTFND)"
post P-08 '{"userId":"NOBODY01","password":"PASSWORD"}'

hdr "P-07 wrong password (USER0001, legacy row)"
post P-07 '{"userId":"USER0001","password":"BADPASS1"}'

hdr "P-05 lowercase credentials user0001/password -> upper-cased and authenticated"
rm -f "$OUT_DIR/jar_p05.txt"
echo "ROW before: $(row USER0001)"
post P-05 '{"userId":"user0001","password":"password"}' "" "$OUT_DIR/jar_p05.txt"
echo "ROW after : $(row USER0001)"
echo "COOKIES  : $(grep -v '^#' "$OUT_DIR/jar_p05.txt" | awk '{print $6"="substr($7,1,6)"... httponly_flag="$1}' )"
hdr "P-20 session payload after P-05"
get P-20 /api/auth/session "$OUT_DIR/jar_p05.txt"

hdr "P-06 valid U user USER0003 (legacy) -> session + /menu"
rm -f "$OUT_DIR/jar_p06.txt"
echo "ROW before: $(row USER0003)"
post P-06 '{"userId":"USER0003","password":"PASSWORD"}' "" "$OUT_DIR/jar_p06.txt"
echo "ROW after : $(row USER0003)"
get P-06s /api/auth/session "$OUT_DIR/jar_p06.txt"

hdr "P-12 valid A user ADMIN001 -> /menu (Q-01)"
rm -f "$OUT_DIR/jar_p12.txt"
echo "ROW before: $(row ADMIN001)"
post P-12 '{"userId":"ADMIN001","password":"PASSWORD"}' "" "$OUT_DIR/jar_p12.txt"
echo "ROW after : $(row ADMIN001)"
get P-12s /api/auth/session "$OUT_DIR/jar_p12.txt"
hdr "P-12b lowercase admin001/password"
post P-12b '{"userId":"admin001","password":"password"}'

hdr "P-13 legacy -> BCrypt upgrade: USER0004 first login (legacy), second login (hash)"
echo "ROW before 1st: $(row USER0004)"
post P-13a '{"userId":"USER0004","password":"PASSWORD"}'
echo "ROW after 1st : $(row USER0004)"
post P-13second '{"userId":"USER0004","password":"PASSWORD"}'
echo "ROW after 2nd : $(row USER0004)"
hdr "P-13c wrong password AFTER upgrade (USER0004)"
post P-13c '{"userId":"USER0004","password":"BADPASS1"}'
echo "ROW after     : $(row USER0004)"

hdr "P-14 no lockout: 10 wrong attempts then success (USER0005)"
for i in $(seq 1 10); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -X POST --data '{"userId":"USER0005","password":"WRONG000"}' "$BASE/api/auth/signon")
  echo "attempt $i status=$code"
done
post P-14 '{"userId":"USER0005","password":"PASSWORD"}'

hdr "P-15 protected URL unauthenticated -> JSON 401"
get P-15a /api/accounts/00000000001
hdr "P-15 protected URL with session cookie (P-06 jar) -> not 401"
get P-15b /api/accounts/00000000001 "$OUT_DIR/jar_p06.txt"
hdr "P-15 session endpoint without cookie"
get P-15c /api/auth/session
hdr "P-09 PF3 / signoff with P-06 session"
code=$(curl -s -o "$OUT_DIR/P-09.body" -w '%{http_code}' -b "$OUT_DIR/jar_p06.txt" -X POST "$BASE/api/auth/signoff")
echo "REQUEST  POST /api/auth/signoff"; echo "STATUS   $code"; echo "BODY     $(cat "$OUT_DIR/P-09.body")"
hdr "P-15 session after signoff (same cookie) -> 401"
get P-15d /api/auth/session "$OUT_DIR/jar_p06.txt"
hdr "P-09b signoff without any session"
code=$(curl -s -o "$OUT_DIR/P-09b.body" -w '%{http_code}' -X POST "$BASE/api/auth/signoff")
echo "STATUS   $code"; echo "BODY     $(cat "$OUT_DIR/P-09b.body")"

hdr "P-15e second sign-on replaces existing session (P-12 jar, sign on as USER0001)"
post P-15e '{"userId":"USER0001","password":"PASSWORD"}' "$OUT_DIR/jar_p12.txt" "$OUT_DIR/jar_p15e.txt"
get P-15f /api/auth/session "$OUT_DIR/jar_p15e.txt"
get P-15g /api/auth/session "$OUT_DIR/jar_p12.txt"

hdr "P-17 over-length input (9 chars)"
post P-17a '{"userId":"USER00011","password":"PASSWORD"}'
post P-17b '{"userId":"USER0001","password":"PASSWORD9"}'

hdr "P-18 trailing/leading spaces"
post P-18a '{"userId":"USER0001 ","password":"PASSWORD"}'
post P-18b '{"userId":" USER0001","password":"PASSWORD"}'
post P-18c '{"userId":"USER0001","password":"PASSWORD "}'

hdr "P-16 CC00 never appears in any URL/response"
grep -l "CC00" "$OUT_DIR"/*.body | sed 's#.*/##' | tr '\n' ' '; echo "(files containing CC00; header response is the only legitimate place)"

hdr "P-10 datastore failure (OTHER RESP): simulate by revoking SELECT on users from the app role is impossible (single role); instead stop PostgreSQL"
docker stop carddemo-pg >/dev/null && sleep 2
post P-10 '{"userId":"USER0001","password":"PASSWORD"}'
docker start carddemo-pg >/dev/null; for i in 1 2 3 4 5 6 7 8 9 10; do docker exec carddemo-pg pg_isready -U carddemo >/dev/null 2>&1 && break; sleep 1; done
hdr "P-10b recovery sign-on after PostgreSQL back"
post P-10b '{"userId":"USER0001","password":"PASSWORD"}'

hdr "FINAL users table"
$PSQL -c "select sec_usr_id, sec_usr_type, left(sec_usr_pwd_hash,12) hash_prefix, length(sec_usr_pwd_hash) hash_len, sec_usr_pwd_legacy is not null as has_legacy from users order by 1;"
