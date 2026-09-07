# Wave 1 parity results — S-01 AccountView (CSUTLDTC + data seams B-0001/B-0002/B-0006/B-0007)

Independent verification of PR #95 (`devin/1788757216-w1-data-seams-csutldtc`, head `1fe792b7d30be3a3c82d3a304c13d72fa62b44c2`).
Expectations were derived from `app/cbl/CSUTLDTC.cbl`, `app/cpy/*.cpy`, the raw files under `app/data` and
`functional/CardDemo/programs/CSUTLDTC_functional_requirement.md`; the Java sources were opened only to learn the
invocation surface (`DateValidationService#validate`, import profile, column names). No implementation or existing
test was modified. No customer-supplied cases were provided to this pass. Cross-check branches were not consulted.

Evidence engine: docker `carddemo-pg`, **PostgreSQL 16.15**, port 5433 (`.migration/07_runbook.md`). H2 was not used.

## 1. Verdict table

| Unit | Verdict | Confidence | Evidence |
|---|---|---|---|
| CSUTLDTC port (FR-23, FR-24, DTC-03 / §5.4, DV-04, B-0014) | **PASS** | HIGH for layout, severity, RETURN-CODE, `0000`/`2513`; codes 2507/2508/2517/2518/2520 **INFERRED** (D-0028) | `csutldtc/results_java.md`, `csutldtc/actual.csv`, `csutldtc/gnucobol/differential_results.md` |
| B-0001 ACCTDAT -> `accounts` | **PASS-WITH-RISK** | HIGH | 50/50 rows, 500/600 fields byte-equal to CVACT01Y decode; 100 differences are all the documented ZIP/GROUP-ID slot quirk (§4.2); negative overpunch UNTESTED (fixture has none) |
| B-0002 CUSTDAT -> `customers` | **PASS** | HIGH | 50/50 rows, 900/900 fields equal |
| B-0007 CXACAIX -> `card_xrefs` + `idx_card_xrefs_acct_id` | **PASS-WITH-RISK** | HIGH (rows/index); Q-04 ordering **UNTESTED on real data** | 50/50 rows, 150/150 fields equal; index present; 0 multi-card accounts in `app/data` (D-0022 open sign-off risk stands) |
| B-0006 USRSEC -> `users` (IBM037 decode, D-0029 state) | **PASS** | HIGH | 10/10 rows, 50/50 fields equal to cp037 decode; `sec_usr_pwd_hash` NULL on all 10, `sec_usr_pwd_legacy` set on all 10 |
| Flyway V1 on PostgreSQL 16 | **PASS** | HIGH | `flyway_schema_history` v1 success=t; `ddl-auto=validate` context start OK |
| Q-10 date gate (D-0032) | **PASS** — "0 offenders" **confirmed** | HIGH | 200/200 values re-measured from raw offsets (`q10/q10_output.txt`) |
| Session model B-0027 (D-0030) | **PASS** | HIGH (static review only; no HTTP surface in wave 1) | `SessionContext` holds only `userId`(<=8) + `userType` A/U, mirrors `COCOM01Y.cpy`; nothing else in session |
| Error model B-0031 (E-01..E-14 verbatim) | **PASS-WITH-RISK** | HIGH for literals; formatting of RESP/RESP2 in E-09..E-12 **UNTESTED** (no caller in wave 1) | §5 below |
| Conformance skill | **PASS** — 0 failing lines | HIGH | `conformance/conformance_output.txt` (all mechanical checks PASS; backend `mvn clean verify` exit 0, 37 tests; frontend build + 6 specs SUCCESS) |
| Delivering child's "money expectations corrected to extract widths" | **PASS** — derivable from raw | HIGH | §6 below |

Counts: CSUTLDTC cases derived 19 + 4 structural = **23**, run 23, passed 23, failed 0, untested 0.
Data-seam field comparisons: 1,700 fields across 160 rows; 1,600 equal, 100 documented-quirk differences, 0 unexplained.
Untested (legitimately): Q-04 multi-card ordering on real data; negative overpunch signs; E-09..E-12 dynamic formatting (wave 2+ callers).

## 2. CSUTLDTC

### 2.1 Case table
`csutldtc/cases.csv` — 19 rows (id, requirement, inputs, expected severity/msg/reason/80-byte output, source, confidence,
COBOL cite). All derived from `CSUTLDTC.cbl:42-57` (result layout), `:83-88` (linkage), `:97-100` (RETURN-CODE),
`:105-124` (CEEDAYS call / MOVEs), `:129-147` (EVALUATE catalogue) and `CSUTLDWY.cpy`. Runner: `csutldtc/CsutldtcParityRunner.java`
(standalone `java` source launcher against `backend/target/classes`; one normalization rule: exact 80-byte compare, no trimming).

Outcome (`csutldtc/results_java.md`): 19/19 byte-exact PASS; structural S01-S04 PASS (null date / null mask ->
`IllegalArgumentException`; 11-char date -> `IllegalArgumentException` per FR A-DTC-4 INFERRED policy; 1,000-input fuzz
never throws, unmapped -> severity 3 / `Date is invalid` as `WHEN OTHER` `CSUTLDTC.cbl:146-147`).

Requirement coverage: FR-23 (C01-C05, C10, C11), FR-24 (C06-C09, C12-C19), DTC-03 §5.4 layout (asserted on every case:
length 80, bytes 1-4 == severity, `Mesg Code: ` @5, `TstDate: ` @37, `Mask used:` @57, 77-80 spaces), DV-04 (bytes 46-55 ==
intended 10-char date on every case), B-0014 (S01-S03), R-6 fallback (S04). Every FR requirement appears once.

### 2.2 GnuCOBOL differential (optional step, done)
`csutldtc/gnucobol/run_differential.sh` compiles the **real** `app/cbl/CSUTLDTC.cbl` with a stub `CEEDAYS` that only
returns the feedback token named in `cases.csv` (it does not judge dates). Result (`differential_results.md`): 19/19 —
bytes 1-45 and 56-80 identical to the Java `formatted80`, `RETURN-CODE` == Java severity on every case.
**DV-04 is confirmed as FACT by the compiled COBOL**: bytes 46-55 are `X'000A'` + first 8 date characters
(e.g. `000a3230323430323239` for `20240229`), i.e. the VSTRING group is copied (`CSUTLDTC.cbl:108`, `:122`); the target
writes the intended 10-char date. The stub proves the formatting path only; the CEEDAYS code mapping stays INFERRED (D-0028).

### 2.3 FR-doc vs COBOL discrepancy (reported, not softened)
- **FR §4 DTC-01 and AC-DTC-01 literal is 79 bytes, not 80.** Both write `...Date is valid   TstDate:20240229   Mask used:YYYYMMDD      `
  (no space between `TstDate:` and the date). The COBOL layout is `FILLER PIC X(09) VALUE 'TstDate:'` (`CSUTLDTC.cbl:52`;
  FR §3 row "37 | 9 | 'TstDate:' + 1 space" and AC-DTC-09 `37-45 == "TstDate: "` are correct). The correct 80-byte literal is
  `0000Mesg Code: 0000 Date is valid   TstDate: 20240229   Mask used:YYYYMMDD      `. The Java matches the COBOL, not the
  AC-DTC-01 literal. FR lines 101 and 201 should be corrected (documentation only; no code change). My first case-file draft
  reproduced the FR's mistake and was corrected from the COBOL before the run — the fix is visible in `cases.csv`.

## 3. Data seams on PostgreSQL 16 (real DB)

Import run: `import_run.log` — Flyway `Migrating schema "public" to version "1 - account view schema"` ... `Successfully applied 1 migration`,
`Import complete: accounts=50 customers=50 card_xrefs=50 users=10`, `Q-10 date gate: 200 values checked, 0 offending`, exit 0.

Independent raw counts (`data_seams/raw_vs_pg_output.txt`):
- `acctdata.txt` 15,050 bytes = 50 lines x 300 (+LF) — CVACT01Y RECLN 300. **50 confirmed.**
- `custdata.txt` 25,050 bytes = 50 x 500 — CVCUS01Y RECLN 500. **50 confirmed.**
- `cardxref.txt` 1,850 bytes = 50 x **36** (+LF): the ASCII extract omits the 14-byte FILLER of CVACT03Y (RECLN 50). The EBCDIC
  `AWS.M2.CARDDEMO.CARDXREF.PS` is 2,500 bytes = 50 x 50 and its first 36 bytes agree with the ASCII file on all 50 records.
  **50 confirmed**; the importer pads short lines to 50 (filler only) — no data effect.
- `AWS.M2.CARDDEMO.USRSEC.PS` 800 bytes = 10 x 80, decoded cp037. **10 confirmed.**

PostgreSQL excerpts (`data_seams/evidence_output.txt`):
```
version | PostgreSQL 16.15 (Debian 16.15-1.pgdg13+2) ...
flyway_schema_history: version 1 | account view schema | success t
accounts 50 | customers 50 | card_xrefs 50 | users 10
indexdef | CREATE INDEX idx_card_xrefs_acct_id ON public.card_xrefs USING btree (xref_acct_id)
multi-card accounts (group by xref_acct_id having count(*)>1): (0 rows)
neg_bal 0 | neg_cyc_cr 0 | neg_cyc_db 0 | min 2.00 | max 843.00
users: has_legacy t / has_hash f on all 10 rows (ADMIN001..005, USER0001..0005), sec_usr_pwd_legacy='PASSWORD'
```
Sample row (accounts, acct_id=1, raw `00000000001Y00000001940{00000020200{00000010200{2014-11-202025-05-202025-05-2000000000000{00000000000{A000000000`):
`acct_curr_bal 194.00, acct_credit_limit 2020.00, acct_cash_credit_limit 1020.00, open 2014-11-20, exp 2025-05-20, reissue 2025-05-20, cyc credit/debit 0.00, acct_group_id A000000000, acct_addr_zip NULL`.
Sample row (customers, cust_id=1): `Immanuel Madeline Kessler, 618 Deshaun Route / Apt. 802 / Altenwerthshire, NC USA 12546, (908)119-8310, (373)693-8684, ssn 20973888, govt 00000000000049368437, dob 1961-06-08, eft 0053581756, Y, fico 274` — all 18 fields equal.
Sample row (users, ADMIN001, IBM037): `MARGARET GOLD, PASSWORD, A` — equal.

Field-by-field comparison (`raw_vs_pg.py`, every row, copybook-derived offsets; first 5 rows per table printed):
customers 900/900, card_xrefs 150/150, users 50/50 equal; accounts 500/600 equal + 100 differences, all of one kind (§4.2).

Overpunch: the 250 money fields in `acctdata.txt` all end in `{` (+0); the `S9(10)V99` decode (`00000001940{` -> 194.00) matches
PostgreSQL on all 250. **Negative signs (`}`, `J`..`R`) and non-zero positive sign digits (`A`..`I`) are UNTESTED** — absent from the fixture.

## 4. Risks and deviations found

### 4.1 Q-04 — no multi-card account in the data (UNTESTED, open sign-off risk, already recorded in D-0022)
Raw `cardxref.txt`: 50 xrefs over 50 distinct accounts. The lowest-card-number contract can only be exercised on seeded data
(the delivering integration test does that with a synthetic account); it is **not** proven on the customer extract. Stands as a risk.

### 4.2 ACCT-ADDR-ZIP / ACCT-GROUP-ID slot (PASS-WITH-RISK)
Literal copybook decode of every `acctdata.txt` record gives `ACCT-ADDR-ZIP` (bytes 103-112) = `A000000000` and `ACCT-GROUP-ID`
(bytes 113-122) = spaces. The target stores `acct_group_id='A000000000'`, `acct_addr_zip=NULL` under
`carddemo.import.acctdata-group-id-in-zip-slot=true`. This is a **documented, STOP-A-confirmed data quirk**
(`CardDemo_target_state.md:146`, `application.properties:13-14`) and `A000000000` is indeed a DISCGRP key (`discgrp.txt`),
so the interpretation is plausible. Risk to record: `COACTVWC.cbl:485` moves `ACCT-GROUP-ID` (bytes 113-122, blank) to `AADDGRP`,
so the **legacy screen shows a blank Account Group for these records while the target will show `A000000000`** — an observable
difference at the wave-2/4 screen boundary unless the customer confirms the extract is mis-columned. Not softened; flagged for the orchestrator.

### 4.3 DV-04 — confirmed and correctly asserted
See §2.2. Deliberate deviation, asserted explicitly on all 19 cases.

## 5. Session model and error model (static verification; no HTTP surface in wave 1)

B-0027: `security/SessionContext` = record(`userId` X(8) enforced, `userType` A/U from `SEC-USR-TYPE`), stored under one session
attribute; matches D-0030 (COMMAREA -> `userId`+`userType` only). PASS (review; runtime assertion belongs to wave 2).

B-0031 literals, each compared byte-for-byte with the source:

| Code | Java constant/format | COBOL | Match |
|---|---|---|---|
| E-01 | `Please enter User ID ...` | `COSGN00C.cbl:120` | yes |
| E-02 | `Please enter Password ...` | `COSGN00C.cbl:125` | yes |
| E-03 | `Invalid key pressed. Please see below...` + 9 spaces (50) | `CSMSG01Y.cpy:21` | yes |
| E-04 | `No input received` | `COACTVWC.cbl:124` | yes |
| E-05 | `Account Filter must  be a non-zero 11 digit number` (2 spaces) | `COACTVWC.cbl:672` | yes |
| E-06 | `Wrong Password. Try again ...` | `COSGN00C.cbl:242` | yes |
| E-07 | `User not found. Try again ...` | `COSGN00C.cbl:249` | yes |
| E-08 | `Please enter a valid option number...` | `COMEN01C.cbl:131` | yes |
| E-09 | `Account:`+id11+` not found in Cross ref file.  Resp:`+X(10)+` Reas:`+X(10), truncated to 75 | `COACTVWC.cbl:747-757`, `:117` | yes (literals) |
| E-10 | `Account:`+id11+` not found in Acct Master file.Resp:`+X(10)+` Reas:`+X(10) | `COACTVWC.cbl:796-806` | yes (literals) |
| E-11 | `CustId:`+id9+` not found in customer master.Resp: `+X(10)+` REAS:`+X(10) | `COACTVWC.cbl:845-856` | yes (literals) |
| E-12 | `File Error: `+X(8)+` on `+X(9)+` returned RESP `+X(10)+`,RESP2 `+X(10)+5 spaces | `COACTVWC.cbl:86-105` | yes (layout) |
| E-13 | `Unable to verify the User ...` | `COSGN00C.cbl:254` | yes |
| E-14 | `Thank you for using CardDemo application...` + 6 spaces (50) | `CSMSG01Y.cpy:19` | yes |

Risk (UNTESTED, wave 2+): in E-09..E-12 the COBOL moves `WS-RESP-CD PIC S9(09) COMP` (`COACTVWC.cbl:40,42`) into `ERROR-RESP PIC X(10)`,
which renders as 9 zoned digits + 1 space (e.g. `000000013 `). The Java helpers take the code as a caller-supplied string and only pad
to 10; parity depends on the wave-2 caller passing the 9-digit zero-filled form. Nothing in wave 1 calls these helpers.

## 6. Delivering child's money-expectation flag
`AccountViewRepositoriesIntegrationTest` expects `acct_curr_bal 194.00`, `acct_credit_limit 2020.00` for acct 1. From the raw record,
bytes 13-24 = `00000001940{` and 25-36 = `00000020200{`; `S9(10)V99` with `{`=+0 gives 194.00 and 2020.00. The corrected
expectations are **derivable from the raw extract** (confirmed independently in `raw_vs_pg_output.txt`), not only from the Java output.

## 7. Conformance skill
`conformance/run_skill.sh` executes every mechanical line of `.agents/skills/carddemo-target-state-conformance/SKILL.md`.
Result: **0 failing lines** (`conformance_output.txt`). Backend `mvn clean verify` exit 0 — 37 tests (H2 context 3, CobolFieldReader 5,
Q10 gate 4, repositories on Testcontainers-PostgreSQL 6, DateValidationService 19). Frontend `npm ci`, `npm run build`, `ng test` 6/6 SUCCESS.
Informational: the ONLINE "controller mappings" list is empty and the README endpoint table has 4 rows because wave 1 ships no controller.

## 8. Reproduce
```
docker start carddemo-pg   # or the runbook docker run
docker exec carddemo-pg psql -U carddemo -d carddemo -c 'drop schema public cascade; create schema public;'
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo \
  mvn -B spring-boot:run -Dspring-boot.run.profiles=import -Dspring-boot.run.arguments="--spring.main.web-application-type=none")
PGPASSWORD=carddemo psql -h localhost -p 5433 -U carddemo -d carddemo -x -f parity/wave1/data_seams/evidence.sql
python3 parity/wave1/data_seams/raw_vs_pg.py        # exits 1 while the §4.2 quirk differences exist (by design: literal copybook)
python3 parity/wave1/q10/q10_remeasure.py
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q -o clean verify)
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -cp backend/target/classes parity/wave1/csutldtc/CsutldtcParityRunner.java \
  parity/wave1/csutldtc/cases.csv parity/wave1/csutldtc/actual.csv parity/wave1/csutldtc/results_java.md
parity/wave1/csutldtc/gnucobol/run_differential.sh && python3 parity/wave1/csutldtc/gnucobol/compare_differential.py
parity/wave1/conformance/run_skill.sh
```
