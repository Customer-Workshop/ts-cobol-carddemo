#!/usr/bin/env bash
# COMEN01C parity: HTTP cases against the running backend (default profile, PostgreSQL 16 on :5433).
# Usage: BASE=http://localhost:8080 ./run_http_cases.sh > http_cases.out
# Fixture: wave-1 `import` profile (10 USRSEC rows, legacy password PASSWORD). Nothing under backend/ is modified.
# Expected values come from cases.md (derived from the COBOL before this script was written).
set -u
BASE="${BASE:-http://localhost:8080}"
OUT_DIR="${OUT_DIR:-$(cd "$(dirname "$0")" && pwd)/http}"
mkdir -p "$OUT_DIR"
PSQL="docker exec carddemo-pg psql -U carddemo -d carddemo"
JAR_U="$OUT_DIR/jar_user.txt"; JAR_A="$OUT_DIR/jar_admin.txt"
E08='Please enter a valid option number...'
NA='Option not available in this release'

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
}
# sel <id> <raw-json-body> <jar> <expected-status> <expected-echo-or-> <expected-message-or->
sel() {
  local id="$1" body="$2" jar="$3" es="$4" ee="$5" em="$6"
  req "$id" POST /api/menu/select "$body" "$jar"
  local st echo msg
  st=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -b "$jar" --data-binary "$body" "$BASE/api/menu/select")
  echo=$(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d.get("option","-"))' "$OUT_DIR/$id.body" 2>/dev/null || echo '<nojson>')
  msg=$(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print((d.get("message") or "-").rstrip())' "$OUT_DIR/$id.body" 2>/dev/null || echo '<nojson>')
  local v=PASS
  [ "$st" = "$es" ] || v=FAIL
  [ "$ee" = "-" ] || [ "$echo" = "$ee" ] || v=FAIL
  [ "$em" = "-" ] || [ "$msg" = "$em" ] || v=FAIL
  echo "EXPECT   status=$es echo=$ee message=$em"
  echo "VERDICT  $v"
}

echo "run at $(date -u +%FT%TZ)  BASE=$BASE"
echo "postgres: $($PSQL -Atc 'select version();')"
rm -f "$JAR_U" "$JAR_A"

hdr "M-12 unauthenticated GET /api/menu and POST /api/menu/select (EIBCALEN=0 refusal, cbl:82-84)"
req M-12a GET /api/menu "" ""
req M-12b POST /api/menu/select '{"option":"1"}' ""
req M-12c GET /api/menu "" "" -H 'Accept: text/html'

hdr "sign on USER0001 (type U) and ADMIN001 (type A) - COSGN00C, outside this program's scope; only to get sessions"
req SGN-U POST /api/auth/signon '{"userId":"USER0001","password":"PASSWORD"}' "$JAR_U"
req SGN-A POST /api/auth/signon '{"userId":"ADMIN001","password":"PASSWORD"}' "$JAR_A"

hdr "M-01 GET /api/menu as U (initial SEND MAP, cbl:85-92, 208-303)"
req M-01 GET /api/menu "" "$JAR_U"
python3 - "$OUT_DIR/M-01.body" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
h=d["header"]; print("HEADER  ", json.dumps(h))
print("LABELS  ", [f"{o['number']:02d}. {o['name']}" for o in d["options"]])
print("ROWS    ", len(d["options"]))
PY

hdr "M-02 route table vs COMEN02Y.cpy:25-90 (number|name|program|userType|implemented)"
python3 - "$OUT_DIR/M-01.body" <<'PY'
import json,sys
exp=[(1,"Account View","COACTVWC","U"),(2,"Account Update","COACTUPC","U"),(3,"Credit Card List","COCRDLIC","U"),
(4,"Credit Card View","COCRDSLC","U"),(5,"Credit Card Update","COCRDUPC","U"),(6,"Transaction List","COTRN00C","U"),
(7,"Transaction View","COTRN01C","U"),(8,"Transaction Add","COTRN02C","U"),(9,"Transaction Reports","CORPT00C","U"),
(10,"Bill Payment","COBIL00C","U"),(11,"Pending Authorization View","COPAUS0C","U")]
d=json.load(open(sys.argv[1]))
got=[(o["number"],o["name"].rstrip(),o["program"],o["userType"]) for o in d["options"]]
impl=[o["number"] for o in d["options"] if o["implemented"]]
for e,g in zip(exp,got): print("ROW", "PASS" if e==g else "FAIL", e, g)
print("COUNT", "PASS" if len(got)==11 else "FAIL", len(got))
print("ORDER", "PASS" if [g[0] for g in got]==list(range(1,12)) else "FAIL")
print("IMPLEMENTED rows:", impl, "PASS" if impl==[1] else "FAIL")
print("ROW1 endpoint/route:", d["options"][0].get("endpoint"), d["options"][0].get("route"))
PY

hdr "M-03 blank option -> E-08, echo 00 (cbl:117-133)"
sel M-03a '{"option":""}'   "$JAR_U" 400 00 "$E08"
sel M-03b '{"option":"  "}' "$JAR_U" 400 00 "$E08"
sel M-03c '{}'              "$JAR_U" 400 00 "$E08"
sel M-03d '{"option":null}' "$JAR_U" 400 00 "$E08"

hdr "M-04 invalid options -> E-08 with normalised echo"
sel M-04a '{"option":"0"}'  "$JAR_U" 400 00 "$E08"
sel M-04b '{"option":"00"}' "$JAR_U" 400 00 "$E08"
sel M-04c '{"option":"A"}'  "$JAR_U" 400 0A "$E08"
sel M-04d '{"option":"1A"}' "$JAR_U" 400 1A "$E08"
sel M-04e '{"option":"A1"}' "$JAR_U" 400 A1 "$E08"
sel M-04f '{"option":"?"}'  "$JAR_U" 400 0? "$E08"
sel M-04g '{"option":" A"}' "$JAR_U" 400 0A "$E08"
sel M-04h '{"option":" 0"}' "$JAR_U" 400 00 "$E08"
sel M-04i '{"option":"a"}'  "$JAR_U" 400 0a "$E08"

hdr "M-05 out-of-range 12..99 -> E-08 echo as typed, never 500 (ruling item 3; cbl:127-137)"
sel M-05a '{"option":"12"}' "$JAR_U" 400 12 "$E08"
sel M-05b '{"option":"13"}' "$JAR_U" 400 13 "$E08"
sel M-05c '{"option":"99"}' "$JAR_U" 400 99 "$E08"

hdr "M-06 option 1 variants -> dispatch COACTVWC (cbl:117-125, 177-187; tbl:25-29)"
for v in '1' ' 1' '01' '1 '; do
  id="M-06$(printf '%s' "$v" | tr ' ' '_')"
  sel "$id" "{\"option\":\"$v\"}" "$JAR_U" 200 01 -
  python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print("DISPATCH", "PASS" if (d["program"],d["endpoint"],d["implemented"],d.get("message"))==("COACTVWC","/api/accounts/{acctId}",True,None) else "FAIL", d)' "$OUT_DIR/$id.body"
done

hdr "M-07 options 2..11 -> Q-12 facade (implemented=false, NEW TEXT), program = table row"
progs=(x COACTVWC COACTUPC COCRDLIC COCRDSLC COCRDUPC COTRN00C COTRN01C COTRN02C CORPT00C COBIL00C COPAUS0C)
for n in 2 3 4 5 6 7 8 9 10 11; do
  id="M-07-$n"; e=$(printf '%02d' $n)
  sel "$id" "{\"option\":\"$n\"}" "$JAR_U" 200 "$e" "$NA"
  python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print("FACADE", "PASS" if (d["program"],d["implemented"],d.get("endpoint"),d.get("route"))==(sys.argv[2],False,None,None) else "FAIL", d)' "$OUT_DIR/$id.body" "${progs[$n]}"
done
sel M-07-02b '{"option":"02"}' "$JAR_U" 200 02 "$NA"
sel M-07-02c '{"option":" 2"}' "$JAR_U" 200 02 "$NA"
sel M-07-11b '{"option":"11"}' "$JAR_U" 200 11 "$NA"

hdr "M-08 dead admin guard / excluded texts must never appear (tbl: all rows U; cbl:140, 163-166, 172-175)"
for t in 'No access' 'is not installed' 'coming soon' 'Invalid key'; do
  c=$(grep -l -- "$t" "$OUT_DIR"/M-0[3-7]*.body 2>/dev/null | wc -l); echo "TEXT '$t' in option responses: $c files -> $([ "$c" = 0 ] && echo PASS || echo FAIL)"
done

hdr "M-11 admin session (type A): same menu, option 1 works as for U (Q-01)"
req M-11a GET /api/menu "" "$JAR_A"
cmp <(python3 -c 'import json,sys;print(json.dumps(json.load(open(sys.argv[1]))["options"]))' "$OUT_DIR/M-01.body") \
    <(python3 -c 'import json,sys;print(json.dumps(json.load(open(sys.argv[1]))["options"]))' "$OUT_DIR/M-11a.body") && echo "OPTIONS identical U vs A: PASS" || echo "OPTIONS differ U vs A: FAIL"
sel M-11b '{"option":"1"}' "$JAR_A" 200 01 -
sel M-11c '{"option":"5"}' "$JAR_A" 200 05 "$NA"
req M-11d GET /api/auth/session "" "$JAR_A"

hdr "M-13 identity intact after menu -> option 1 -> account view -> back (B-0027 / DV-02)"
req M-13a GET /api/auth/session "" "$JAR_U"
sel M-13b '{"option":"1"}' "$JAR_U" 200 01 -
req M-13c GET /api/accounts/00000000001 "" "$JAR_U"
req M-13d GET /api/menu "" "$JAR_U"
req M-13e GET /api/auth/session "" "$JAR_U"

hdr "M-14 CM00 only in header text (D-0016)"
grep -l 'CM00' "$OUT_DIR"/*.body | sed "s#$OUT_DIR/##" | tr '\n' ' '; echo
python3 - "$OUT_DIR" <<'PY'
import json,sys,glob,os
bad=[]
for f in glob.glob(sys.argv[1]+"/*.body"):
    try: d=json.load(open(f))
    except Exception: continue
    def walk(x,path=""):
        if isinstance(x,dict):
            for k,v in x.items(): walk(v,path+"."+k)
        elif isinstance(x,list):
            for v in x: walk(v,path+"[]")
        elif isinstance(x,str) and "CM00" in x and not path.endswith(".tranId"): bad.append((os.path.basename(f),path,x))
    walk(d)
print("CM00 outside header.tranId:", bad or "none", "->", "PASS" if not bad else "FAIL")
PY

hdr "M-15 target-only payload edits (Q-14): length 3, non-string, malformed JSON, unicode digits"
req M-15a POST /api/menu/select '{"option":"123"}' "$JAR_U"
req M-15b POST /api/menu/select '{"option":1}' "$JAR_U"
req M-15c POST /api/menu/select '{"option":' "$JAR_U"
req M-15d POST /api/menu/select '{"option":"٠١"}' "$JAR_U"
req M-15e POST /api/menu/select '{"option":"1","extra":true}' "$JAR_U"
req M-15f POST /api/menu/select '' "$JAR_U"

hdr "M-09 Exit = POST /api/auth/signoff; afterwards GET /api/menu -> 401 (cbl:96-98, 196-203)"
req M-09a POST /api/auth/signoff "" "$JAR_U"
req M-09b GET /api/menu "" "$JAR_U"
req M-09c POST /api/menu/select '{"option":"1"}' "$JAR_U"

echo; echo "users table after run:"; $PSQL -c "select sec_usr_id, sec_usr_type, sec_usr_pwd_hash is not null as has_hash, sec_usr_pwd_legacy is not null as has_legacy from users order by 1"
