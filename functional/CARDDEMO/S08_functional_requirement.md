# S-08 Transaction View — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Derived from `S08_tran_view_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; no transcoding needed; cites are raw line numbers (`COTRN01C.cbl:` = `app/cbl/COTRN01C.cbl`, `bms:` = `app/bms/COTRN01.bms`).

## 1. Purpose and scope
Display one transaction record from the TRANSACT file, looked up by its 16-character transaction ID, with the exact legacy validation messages and function-key behavior. Process type ONLINE. Trigger: CICS transaction CT01 (`app/csd/CARDDEMO.CSD:429-430`), reached from main-menu option 07 or from the transaction list (COTRN00C) with a selected row. Hard stop: every transfer out of COTRN01C (sign-on, menu, transaction list). Exclusions: COTRN00C (S-07), COTRN02C (S-09).

## 2. Actors and preconditions
- Actor: any signed-on CardDemo user (regular or admin); COTRN01C applies no user-type gate.
- Precondition: a session context exists (`EIBCALEN > 0`, `COTRN01C.cbl:94`); the TRANSACT KSDS (`TRANSACT`, key TRAN-ID X(16), `app/cpy/CVTRA05Y.cpy:4-5`) is open.

## 3. Surface specification
### Screen COTRN1A / mapset COTRN01 (`app/bms/COTRN01.bms`, fields `app/cpy-bms/COTRN01.CPY`)
| Field | Label (verbatim) | I/O | Len/PIC | Edits / source |
|---|---|---|---|---|
| TRNIDIN | `Enter Tran ID:` (bms:84) | INPUT | X(16), IC cursor (bms:85-90) | mandatory (`COTRN01C.cbl:147-152`); used verbatim as key (`:172`) |
| TRNID | `Transaction ID:` (bms:104) | OUTPUT | X(16) | TRAN-ID (`:178`) |
| CARDNUM | `Card Number:` (bms:117) | OUTPUT | X(16) | TRAN-CARD-NUM (`:179`) |
| TTYPCD | `Type CD:` (bms:131) | OUTPUT | X(2) | TRAN-TYPE-CD (`:180`) |
| TCATCD | `Category CD:` (bms:143) | OUTPUT | X(4) | TRAN-CAT-CD 9(04) (`:181`) |
| TRNSRC | `Source:` (bms:155) | OUTPUT | X(10) | TRAN-SOURCE (`:182`) |
| TDESC | `Description:` (bms:167) | OUTPUT | X(60) | TRAN-DESC X(100), first 60 (`:184`) |
| TRNAMT | `Amount:` (bms:179) | OUTPUT | X(12) | TRAN-AMT via `+99999999.99` (`:49`, `:177`, `:183`) |
| TORIGDT | `Orig Date:` (bms:191) | OUTPUT | X(10) | TRAN-ORIG-TS X(26), first 10 (`:185`) |
| TPROCDT | `Proc Date:` (bms:203) | OUTPUT | X(10) | TRAN-PROC-TS X(26), first 10 (`:186`) |
| MID | `Merchant ID:` (bms:215) | OUTPUT | X(9) | TRAN-MERCHANT-ID 9(09) (`:187`) |
| MNAME | `Merchant Name:` (bms:227) | OUTPUT | X(30) | TRAN-MERCHANT-NAME X(50), first 30 (`:188`) |
| MCITY | `Merchant City:` (bms:239) | OUTPUT | X(25) | TRAN-MERCHANT-CITY X(50), first 25 (`:189`) |
| MZIP | `Merchant Zip:` (bms:251) | OUTPUT | X(10) | TRAN-MERCHANT-ZIP (`:190`) |
| ERRMSG | — | OUTPUT | X(78) red (bms:259-262) | WS-MESSAGE (`:217`) |
| Header | `Tran:`/`Prog:`/`Date:`/`Time:` + titles (bms:29-74) | OUTPUT | — | `POPULATE-HEADER-INFO` (`:243-262`) |
Title: `View Transaction` (bms:79). Footer: `ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.` (bms:263-268).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S08-01 | Entry | Program entered without a session context (`EIBCALEN = 0`) | Sign-on screen shown; no transaction data exposed | COTRN01C | COTRN01C.cbl:94-96 | S01-B6 | `TransactionViewApiIntegrationTests.WithoutToken_IsUnauthorized`; `app.routes.spec` guard on `/transactions/view` |
| FR-S08-02 | Entry | First entry with session, no pre-selected transaction | Blank View Transaction screen, empty message, cursor on `Enter Tran ID:`; no lookup performed | COTRN01C | :98-109 | — | `transaction-view.component.spec` "FR-S08-02" |
| FR-S08-03 | Entry | First entry with `CDEMO-CT01-TRN-SELECTED` populated (arrival from transaction list) | Tran ID pre-filled with the selected id and fetched immediately as if ENTER were pressed | COTRN01C | :103-108; COTRN00C.cbl:186-195 | S08-B2 | `transaction-view.component.spec` "FR-S08-03" |
| FR-S08-04 | Fetch validation | ENTER with Tran ID blank (spaces/low-values) | Message `Tran ID can NOT be empty...`, cursor on Tran ID, previously displayed detail fields unchanged, no lookup | COTRN01C | :147-152, :158 | — | `TransactionViewServiceTests.BlankId_*`; API `BlankTranId_IsBadRequestWithLegacyMessage`; spec "FR-S08-04" |
| FR-S08-05 | Fetch | ENTER with non-blank Tran ID | All 13 detail fields cleared before the lookup, so a failed lookup shows an empty detail area | COTRN01C | :158-173 | — | spec "FR-S08-05" |
| FR-S08-06 | Fetch | Lookup finds no record (RESP NOTFND) | Message `Transaction ID NOT found...`, cursor on Tran ID, detail area empty, entered id retained | COTRN01C | :283-288 | S08-B1 | `TransactionViewServiceTests.UnknownId_*`; `TransactionViewIntegrationTests.UnknownId_*`; API `UnknownTranId_IsNotFoundWithLegacyMessage`; spec "FR-S08-06" |
| FR-S08-07 | Fetch | Lookup fails for any other reason (RESP other) | Message `Unable to lookup Transaction...`, cursor on Tran ID, detail area empty; RESP/RESP2 logged | COTRN01C | :289-295 | S08-B1 | `TransactionViewServiceTests.StoreFailure_*`; spec "FR-S08-07" |
| FR-S08-08 | Fetch | Lookup succeeds (RESP NORMAL) | All 13 detail fields populated from the record, message area blank, cursor on Tran ID | COTRN01C | :176-191 | S08-B1 | `TransactionViewIntegrationTests.SeededDebit_*`; API `SeededTranId_ReturnsScreenFields`; spec "FR-S08-08" |
| FR-S08-09 | Display derivation | Amount displayed | `+99999999.99` edit: fixed leading `+`/`-`, 8 zero-padded integer digits, point, 2 decimals; a 9th integer digit is dropped | COTRN01C | :49, :177, :183 | — | `TransactionViewMapperTests.Amount_*`; `TransactionViewIntegrationTests.SeededCredit_*` |
| FR-S08-10 | Display derivation | Orig/Proc timestamps displayed | First 10 characters of the X(26) timestamp (`yyyy-MM-dd`); blank when the timestamp is absent | COTRN01C | :185-186; bms:187-203 | — | `TransactionViewMapperTests.Timestamps_*`; integration `SeededCredit_*` |
| FR-S08-11 | Display derivation | Description / merchant name / city displayed | Truncated to the map lengths 60 / 30 / 25; type, category, source, merchant id/zip, card number shown in full | COTRN01C | :184, :188-189; bms:163-251 | — | `TransactionViewMapperTests.LongText_*` |
| FR-S08-12 | Key handling | Tran ID typed | Up to 16 characters; used verbatim as the key (no upper-casing, no numeric edit); trailing blanks are key padding | COTRN01C | :172; bms:85-90 | S08-B1 | `TransactionViewServiceTests.Id_*`; `TransactionViewIntegrationTests.CaseAndLeadingSpace_*`; spec "FR-S08-12" |
| FR-S08-13 | Navigation | PF3 / Back | Return to the calling program (`CDEMO-FROM-PROGRAM`); main menu when no caller recorded | COTRN01C | :115-122 | S08-B3 | spec "FR-S08-13" |
| FR-S08-14 | Navigation | PF4 / Clear | Tran ID, all 13 detail fields and the message cleared; cursor on Tran ID; no lookup | COTRN01C | :123-124, :301-326 | — | spec "FR-S08-14" |
| FR-S08-15 | Navigation | PF5 / Browse Tran. | Transfer to the transaction list program (COTRN00C); while that stream is not migrated the registry's coming-soon message is shown and the screen is retained | COTRN01C | :125-127 | S08-B4 | spec "FR-S08-15" |
| FR-S08-16 | AID handling | Any other function key | Message `Invalid key pressed. Please see below...`, screen contents retained | COTRN01C | :128-131; CSMSG01Y.cpy:20-21 | — | spec "FR-S08-16" |
| FR-S08-17 | Header/footer | Screen displayed | Title `View Transaction`, `Tran: CT01`, `Prog: COTRN01C`, footer `ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.` | COTRN01C | bms:75-79, 263-268; :243-262 | — | spec "FR-S08-17" |

## 5. Validation and error catalogue
Order on ENTER (`:146-192`): (1) blank Tran ID → `Tran ID can NOT be empty...` (stop, details untouched); (2) details cleared; (3) keyed read: NOTFND → `Transaction ID NOT found...`; other → `Unable to lookup Transaction...`; NORMAL → populate. Every branch positions the cursor on Tran ID (`:151`, `:154`, `:287`, `:294`).

| Message (verbatim) | Condition | Cite | Severity |
|---|---|---|---|
| `Tran ID can NOT be empty...` | TRNIDIN spaces/low-values | :149 | error |
| `Transaction ID NOT found...` | RESP = NOTFND (13) | :285 | error |
| `Unable to lookup Transaction...` | RESP other than NORMAL/NOTFND | :292 | error |
| `Invalid key pressed. Please see below...` | AID not ENTER/PF3/PF4/PF5 | :129; CSMSG01Y.cpy:20-21 | error |
| `This option Transaction List is coming soon ...` | PF5 while COTRN00C route disabled (registry idiom, S-01 FR-S01-14) | S08-B4 | info |

## 6. Field and data derivations
- Amount: `WS-TRAN-AMT PIC +99999999.99` (`:49`) ← `TRAN-AMT S9(09)V99`; sign always shown; 9th integer digit truncated (MOVE numeric-edited).
- Dates: `TRAN-ORIG-TS`/`TRAN-PROC-TS` X(26) → X(10) map field keeps `yyyy-MM-dd`; absent timestamp → blank.
- Truncations: TDESC 100→60, MNAME 50→30, MCITY 50→25.
- Key: 16-byte space-padded compare; target stores keys trailing-space-trimmed and compares after `TrimEnd()`; leading spaces and letter case are preserved.

## 7. Mechanics (demoted, cited)
CICS SEND/RECEIVE MAP with ERASE + CURSOR (`:219-225`, `:232-238`); pseudo-conversational RETURN TRANSID CT01 (`:136-139`); COMMAREA copy (`:97`); `READ ... UPDATE` with no subsequent REWRITE (`:269-278`) → plain read in target; `DISPLAY 'RESP:'` diagnostics (`:290`) → structured log; header date/time formatting (`:243-262`).

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S08-01: Given no valid session, When `/transactions/view` or the API is requested, Then sign-on is required (route → `/signin`, API → 401) and no transaction data is returned.
- FR-S08-02: Given a signed-on user opening the screen with no `tranId`, When it renders, Then the input and all detail fields are blank, the message is empty, focus is on Tran ID and no API call is made.
- FR-S08-03: Given `tranId=0000000000683580` on the route, When the screen initializes, Then the input shows that id and the lookup is issued immediately.
- FR-S08-04: Given details on screen from a previous fetch, When ENTER is pressed with a blank id, Then `Tran ID can NOT be empty...` shows, no API call is made and the details remain.
- FR-S08-05: Given details on screen, When ENTER is pressed with a different non-blank id, Then the details are blank before the response arrives.
- FR-S08-06: Given id `NOPE000000000001`, When fetched, Then 404 + `Transaction ID NOT found...`, details blank, input retains `NOPE000000000001`.
- FR-S08-07: Given the repository throws, When fetched, Then 500 + `Unable to lookup Transaction...`, details blank.
- FR-S08-08: Given seed row `0000000000683580`, When fetched, Then id `0000000000683580`, card `4859452612877065`, type `01`, category `0001`, source `POS TERM`, description `Purchase at Abshire-Lowe`, amount `+00000504.77`, orig date `2022-06-10`, proc date blank, merchant `800000000` / `Abshire-Lowe` / `North Enoshaven` / `72112` are all shown and the message is blank.
- FR-S08-09: Given amount `-919.00`, Then `-00000919.00`; given `123456789.12`, Then `+23456789.12`; given `0`, Then `+00000000.00`.
- FR-S08-10: Given orig `2022-06-10 19:27:53` and proc null, Then `2022-06-10` and ``.
- FR-S08-11: Given a 100-char description, Then exactly its first 60 characters are shown (30 / 25 for merchant name / city).
- FR-S08-12: Given a 16-char id containing lower-case letters or a leading space, When fetched, Then the id is sent unchanged and a not-found results when only a differently-cased key exists; the input accepts at most 16 characters.
- FR-S08-13: Given `returnUrl=/somewhere` (internal), When F3/Back is pressed, Then navigation goes there; without it, to `/menu`.
- FR-S08-14: Given an id, details and a message on screen, When F4/Clear is pressed, Then all are blank and focus returns to Tran ID.
- FR-S08-15: Given the Transaction List registry flag disabled, When F5/Browse Tran. is pressed, Then `This option Transaction List is coming soon ...` shows as info and no navigation occurs; when enabled, the route from the registry is navigated.
- FR-S08-16: Given the screen, When F7 (or any F-key other than F3/F4/F5) is pressed, Then `Invalid key pressed. Please see below...` shows and details are retained.
- FR-S08-17: Given the screen, Then the title `View Transaction`, `Tran: CT01`, `Prog: COTRN01C` and the footer legend are rendered verbatim.

## 9. Traceability matrix
| FR | Owner layer | Target artifact | Test |
|---|---|---|---|
| 01 | API + UI | `[Authorize]` on `TransactionViewController`; `authGuard` on route | API integration; routes spec |
| 02, 03, 05, 13–17 | UI | `TransactionViewComponent` | component spec |
| 04, 06, 07, 12 | API + UI | `TransactionViewService`; component message area | service unit + Postgres integration + API integration; component spec |
| 08–11 | API | `TransactionViewMapper` (Domain.Transaction → screen fields) | mapper unit + Postgres integration |

## 10. Program index
| Program | Role | Program FR doc |
|---|---|---|
| COTRN01C | entry/validate/display | `programs/COTRN01C_functional_requirement.md` |

## 11. Open questions and assumptions
- A1: The `READ ... UPDATE` lock (`:275`) is a mechanic with no business effect (no REWRITE); target read is lock-free. Recorded, not a deviation of observable behavior.
- A2: `CDEMO-CT01-TRN-SELECTED` shares the COMMAREA offset with COTRN00C's `CDEMO-CT00-TRN-SELECTED` (same layout `:53-61` vs COTRN00C's copy); the target contract is the `tranId` query parameter, to be consumed by S-07 when it migrates.
- A3: Header date/time (`mm/dd/yy`, `hh:mm:ss`) is presentation mechanics; the target header shows title, transaction and program codes.
