# S-03 Account Update — Stream Analysis (`!mf_stream_analysis`)

Source of truth: `app/cbl/COACTUPC.cbl` (4,2xx lines), `app/bms/COACTUP.bms` (map `CACTUPA`), copybooks
`app/cpy/CSUTLDPY.cpy` + `CSUTLDWY.cpy` (date edits), `CSLKPCDY.cpy` (area-code / state / state+zip lookups),
`CSSETATY.cpy` (attribute helper), `CVACT01Y.cpy` (ACCTDAT), `CVCUS01Y.cpy` (CUSTDAT), `CVACT03Y.cpy` (CXACAIX),
`COCOM01Y.cpy` (COMMAREA), `app/csd/CARDDEMO.CSD` (CAUP/COACTUPC). Line cites are `file:line`.

## 1. Pinned stream
| Item | Value |
|---|---|
| Stream | S-03 Account Update (ONLINE) — inventory row `functional/CARDDEMO/CardDemo_inventory.md` §5 |
| Entry transaction | `CAUP` (COACTUPC.cbl:536; CSD) |
| Program | `COACTUPC` (COACTUPC.cbl:534) |
| Caller | `COMEN01C` / `CM00` option 02 "Account Update" (COACTUPC.cbl:557-560; menu registry `MenuRoutes.Main[1]`, `Enabled=false`) |
| Map / mapset | `CACTUPA` / `COACTUP` (COACTUP.bms; COACTUPC.cbl:538-540) |
| Files | `ACCTDAT` (read/update/rewrite), `CUSTDAT` (read/update/rewrite), `CXACAIX` (read by account id) — COACTUPC.cbl:568-575, 3650-3797, 3888-4105 |
| Base branch | `devin/1787242078-carddemo-premigration` @ shared data layer `468e17ded0be830785246d6e3cf2d4ede915f609` |

## 2. Program inventory + leaf-first DAG
Single program. No CALLs except the LE date service `CSUTLDTC` inside `CSUTLDPY` (EDIT-DATE-LE, CSUTLDPY.cpy:284-323) which is
only reached after the copybook's own year/month/day/leap checks have already passed, so it cannot produce a new message
in practice (documented as demoted mechanics).

XCTL targets (COACTUPC.cbl:927-960): PF3 exits to `CDEMO-TO-PROGRAM` (defaults to `COMEN01C`/`CM00` when the from-program is blank).
Literals for `COCRDUPC`, `COCRDLIC`, `COCRDSLC` (COACTUPC.cbl:542-566) are declared but **never referenced by an XCTL** in this
program — they stay off-stream and behind the disabled route registry regardless.

DAG depth = 1 → one wave: COACTUPC.

## 3. Surfaces (ONLINE)

### COACTUPC — screen CACTUPA / mapset COACTUP (`app/bms/COACTUP.bms`)
24×80, `CTRL=(FREEKB)`, `MAPATTS/DSATTS=(COLOR,HILIGHT,PS,VALIDN)`. Header rows 1-3: TRNNAME (`CAUP`), TITLE01/02, CURDATE
`mm/dd/yy`, PGMNAME (`COACTUPC`), CURTIME `hh:mm:ss` (COACTUPC.cbl:2668-2694).

| Field (map name) | Row,Col | Len | Business meaning | Edit state |
|---|---|---|---|---|
| ACCTSID | 5,38 | 11 | Account id to update (search key), `IC` cursor | unprotected only while details not fetched (3300-SETUP-SCREEN-ATTRS:2994-2995) |
| ACSTTUS | 5,73 | 1 | Account active status Y/N | editable in S/E |
| OPNYEAR/OPNMON/OPNDAY | 6 | 4/2/2 | Open date | editable |
| ACRDLIM | 6,64 | 15 | Credit limit (`+ZZZ,ZZZ,ZZZ.99` on output, COACTUPC.cbl:371) | editable |
| EXPYEAR/EXPMON/EXPDAY | 7 | 4/2/2 | Expiry date | editable |
| ACSHLIM | 7,64 | 15 | Cash credit limit | editable |
| RISYEAR/RISMON/RISDAY | 8 | 4/2/2 | Reissue date | editable |
| ACURBAL | 8,64 | 15 | Current balance | editable |
| ACRCYCR | 9,64 | 15 | Current cycle credit | editable |
| AADDGRP | 9,23 | 10 | Account group id | editable |
| ACRCYDB | 10,64 | 15 | Current cycle debit | editable |
| ACSTNUM | 12,23 | 9 | Customer id | **always protected** (3320:3536) |
| ACTSSN1/2/3 | 12,49 | 3/2/4 | SSN parts | editable |
| DOBYEAR/DOBMON/DOBDAY | 13 | 4/2/2 | Date of birth | editable |
| ACSTFCO | 13,71 | 3 | FICO score | editable |
| ACSFNAM / ACSMNAM / ACSLNAM | 14 | 25/25/25 | First / middle / last name | editable |
| ACSADL1 / ACSADL2 | 15,16 | 50/50 | Address lines 1,2 | editable |
| ACSSTTE | 15,70 | 2 | State | editable |
| ACSZIPC | 16,70 | 5 | Zip (first 5 of the 10-char record field) | editable |
| ACSCITY | 17,16 | 50 | City (record `CUST-ADDR-LINE-3`) | editable |
| ACSCTRY | 17,70 | 3 | Country | **always protected** (3320:3548-3549, "edits are USA specific") |
| ACSPH1A/B/C, ACSPH2A/B/C | 18 | 3/3/4 | Phone 1 / phone 2 parts | editable |
| ACSGOVT | 19,25 | 20 | Government issued id | editable |
| ACSEFTC | 19,71 | 10 | EFT account id | editable |
| ACSPFLG | 20,71 | 1 | Primary card holder Y/N | editable |
| INFOMSG | 22,23 | 45 | Info line (neutral; dark when empty, 3390:3567-3571) | output |
| ERRMSG | 23,1 | 78 | Error line (red, bright) | output |
| FKEYS / FKEY05 / FKEY12 | 24 | 21/7/10 | `ENTER=Process F3=Exit` / `F5=Save` (dark unless confirming) / `F12=Cancel` (dark unless changes made) | output |

Screen states (from `ACUP-CHANGE-ACTION`, COACTUPC.cbl:869-880 area of `WS-THIS-PROGCOMMAREA`; behaviours in 2000/3200/3250/3300):

| State | 88 name | Info message (3250) | Values shown (3200) | Attributes (3300/3390) |
|---|---|---|---|---|
| Search | `ACUP-DETAILS-NOT-FETCHED` | `Enter or update id of account to update` | blank details (3201) | ACCTSID editable, all else protected |
| Details | `ACUP-SHOW-DETAILS` ('S') | `Update account details presented above.` | fetched originals (3202) | ACCTSID protected; detail fields editable except ACSTNUM, ACSCTRY |
| Edit error | `ACUP-CHANGES-NOT-OK` ('E') | `Update account details presented above.` | user's values (3203) | same as Details; invalid fields red, blank-invalid shown as `*` (CSSETATY); F12 lit |
| Confirm | `ACUP-CHANGES-OK-NOT-CONFIRMED` ('N') | `Changes validated.Press F5 to save` | user's values | everything protected; F5 + F12 lit |
| Done | `ACUP-CHANGES-OKAYED-AND-DONE` ('C') | `Changes committed to database` | user's values | everything protected |
| Lock error / Failed | 'L' / 'F' | `Changes unsuccessful. Please try again` | user's values | ACCTSID editable (WHEN OTHER), details protected; F12 lit |

## 4. Data + field dictionary
| Dataset | Copybook | Key | Access in COACTUPC | Target (shared layer @468e17d) |
|---|---|---|---|---|
| CXACAIX (xref by account) | CVACT03Y | acct id (11) | READ (9200:3650-3698) → gives `CDEMO-CUST-ID`, `CDEMO-CARD-NUM` | `card_xrefs`, `ICardXrefRepository.GetFirstByAccountIdAsync` |
| ACCTDAT | CVACT01Y | acct id (11) | READ (9300), READ UPDATE + REWRITE (9600) | `accounts` / `Account` entity |
| CUSTDAT | CVCUS01Y | cust id (9) | READ (9400), READ UPDATE + REWRITE (9600) | `customers` / `Customer` entity |

All fields the program reads/writes already exist on `Account` and `Customer` — no additive migration is required for S-03.
Record→screen derivations (3202:2787-2867): money → `+ZZZ,ZZZ,ZZZ.99`; dates `YYYY-MM-DD` → (1:4),(6:2),(9:2); SSN 9 digits → 3/2/4;
phone `(aaa)bbb-cccc` → (2:3),(6:3),(10:4); zip → first 5. Screen→record (9600:3945-4050): dates re-stringed `YYYY-MM-DD`,
phones re-stringed `(aaa)bbb-cccc`, money via `NUMVAL-C`.

## 5. Boundary table (headline) — S03-B1..S03-B5 (not written to `.migration/` by this stream; recorded here for the ledger owner)
| ID | Class | Description | Cite | Decision taken in S-03 |
|---|---|---|---|---|
| S03-B1 | B5 outbound routing | PF3 XCTL to `CDEMO-TO-PROGRAM` (menu) | COACTUPC.cbl:927-960 | Angular `router.navigateByUrl('/menu')` via S01-B3 stable routes |
| S03-B2 | B4 data-access leaf | READ UPDATE / REWRITE ACCTDAT + CUSTDAT with SYNCPOINT ROLLBACK on second REWRITE failure | COACTUPC.cbl:3888-4105 | One PostgreSQL transaction; `SELECT … FOR UPDATE NOWAIT` = CICS record lock; lock-not-available → `Could not lock … record for update`; any write failure → rollback + `Update of record failed` |
| S03-B3 | B10 shared state | `WS-THIS-PROGCOMMAREA` (ACUP-OLD/NEW details, change action) carried in COMMAREA between pseudo-conversational turns | COACTUPC.cbl:656-849, 1007-1019 | Stateless API: the client (Angular) carries the fetched snapshot (`original`) and the edited snapshot (`updated`) back on validate/save, mirroring ACUP-OLD/ACUP-NEW; screen state machine lives in the component |
| S03-B4 | B5 declared-but-unused targets | `COCRDUPC`/`COCRDLIC`/`COCRDSLC` literals | COACTUPC.cbl:542-566 | Out of scope; remain disabled in the menu registry |
| S03-B5 | LE runtime | `CALL 'CSUTLDTC'` in `EDIT-DATE-LE` | CSUTLDPY.cpy:284-323 | Unreachable-error path (structural date checks precede it); not ported, documented |

## 6. Waves (leaf-first, from DAG depth)
| Wave | Programs | Deliverables |
|---|---|---|
| 1 | COACTUPC | `CardDemo.Application/AccountUpdate` (fields record, edit rules, service), `IAccountUpdateRepository` + EF implementation, `AccountUpdateController` under `/api/v1/account-update`, Angular `account-update` standalone component + route `/accounts/update` (authGuard), unit + Testcontainers + Karma specs |

## 7. Risks
1. Source quirks that would produce wrong results if ported literally (see FR doc §11 and program FR §7): customer-lock failure falls into the
   "done" branch (2000:2600-2612), account-master miss still shows details when the xref customer exists (9000:3624-3646 tests
   never-set 88s), after a commit the next ENTER redisplays the stale `ACUP-OLD` snapshot (2000:2626-2634 + 3202), and a no-change ENTER
   after a failed save reaches the `UNEXPECTED DATA SCENARIO` abend (2000:2635-2642). Each is handled as a recorded deviation.
2. Lookup tables (`CSLKPCDY`) are large literal lists (410 area codes, 56 state codes, 240 state+zip2 combos) — ported verbatim; any
   transcription slip changes validation results. Mitigated by unit tests on representative members/non-members.
3. Sample data itself fails the edits (e.g. customer 000000001 phone 2 area `373`, `NC`+`12` zip) — parity means the target rejects them too.

## 8. Validation
- `cobc -I app/cpy -fsign=EBCDIC -x app/cbl/COACTUPC.cbl` compile check (CICS statements are not compiled by GnuCOBOL; check is syntax-level only).
- `dotnet test backend/CardDemo.slnx` (unit + Testcontainers Postgres 16), `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build`.
