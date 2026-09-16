# S-09 Transaction Add — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Derived from `S09_tran_add_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; no transcoding needed; cites are raw line numbers. Unqualified `:n` cites refer to `app/cbl/COTRN02C.cbl`.

## 1. Purpose and scope
Let a signed-on CardDemo user key a new transaction against an account (or card), validate every field with the legacy edits in the legacy order, confirm, and write it to the transaction file under the next sequential transaction id. Process type ONLINE. Trigger: CICS transaction CT02 (`app/csd/CARDDEMO.CSD:439-440`), main-menu option 08 (`app/cpy/COMEN02Y.cpy:71`). Includes the shared date utility CSUTLDTC (ported here, consumed later by S-10). Hard stop: PF3 return to the menu shell (S-01) and the no-COMMAREA bounce to sign-on; downstream readers of the written record (S-07/S-08 online, batch) are other streams.

## 2. Actors and preconditions
- Actor: any authenticated user, type 'A' or 'U' — COTRN02C has **no** user-type gate (only `CDEMO-USER-ID`/type are carried in the COMMAREA, never tested).
- Preconditions: CCXREF (`app/cpy/CVACT03Y.cpy`) with its account AIX CXACAIX and TRANSACT (`app/cpy/CVTRA05Y.cpy`) open; the user entered from the menu shell with a COMMAREA (`:115-118`).

## 3. Surface specification
### Add-transaction screen COTRN2A (`app/bms/COTRN02.bms`, fields `app/cpy-bms/COTRN02.CPY`)
| Field | Label (verbatim) | I/O | Len/PIC | Edits |
|---|---|---|---|---|
| ACTIDIN | `Enter Acct #:` (bms:83-84) | INPUT, IC cursor (bms:88) | X(11) | present ⇒ all 11 digits (`:196-203`); resolves card via CXACAIX (`:208-209`) |
| CARDNIN | `(or)` `Card #:` (bms:97-103) | INPUT | X(16) | used only when ACTIDIN blank; all 16 digits (`:210-217`); resolves account via CCXREF (`:222-223`) |
| TTYPCD | `Type CD:` (bms:120-121) | INPUT | X(2) | mandatory (`:252-257`); all digits (`:323-328`) |
| TCATCD | `Category CD:` (bms:133-134) | INPUT | X(4) | mandatory (`:258-263`); all digits (`:329-334`) |
| TRNSRC | `Source:` (bms:146-147) | INPUT | X(10) | mandatory (`:264-269`) |
| TDESC | `Description:` (bms:159-160) | INPUT | X(60) | mandatory (`:270-275`) |
| TRNAMT | `Amount:` hint `(-99999999.99)` (bms:172-173, 211-212) | INPUT | X(12) | mandatory (`:276-281`); sign,8 digits,`.`,2 digits (`:339-351`); echoed `+99999999.99` (`:383-386`) |
| TORIGDT | `Orig Date:` hint `(YYYY-MM-DD)` (bms:185-186, 216-217) | INPUT | X(10) | mandatory (`:282-287`); `dddd-dd-dd` (`:353-366`); CSUTLDTC (`:388-408`) |
| TPROCDT | `Proc Date:` hint `(YYYY-MM-DD)` (bms:198-199, 221-222) | INPUT | X(10) | mandatory (`:288-293`); `dddd-dd-dd` (`:368-381`); CSUTLDTC (`:410-428`) |
| MID | `Merchant ID:` (bms:226-227) | INPUT | X(9) | mandatory (`:294-299`); all digits (`:430-437`) |
| MNAME | `Merchant Name:` (bms:239-240) | INPUT | X(30) | mandatory (`:300-305`) |
| MCITY | `Merchant City:` (bms:252-253) | INPUT | X(25) | mandatory (`:306-311`) |
| MZIP | `Merchant Zip:` (bms:265-266) | INPUT | X(10) | mandatory (`:312-317`) |
| CONFIRM | `You are about to add this transaction. Please confirm` `(Y/N)` (bms:278-292) | INPUT | X(1) | Y/y, N/n, blank, other (`:169-188`) |
| ERRMSG | — | OUTPUT, RED BRT; GREEN on success (`:727`) | X(78) | catalogue §5 |
| Header | `Tran:` `Prog:` `Date:` `Time:` (bms:32-74) | OUTPUT | — | `POPULATE-HEADER-INFO` (`:548-568`) |
Title: `Add Transaction` (bms:78-79). Legend: `ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.` (bms:300-303).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S09-01 | Screen display | First entry from the menu | Empty add-transaction screen, all 14 input fields blank, cursor on Acct #, no message | COTRN02C | :120-130, :762-779; bms | S09-B1 | `TranAddComponent` spec "renders the 14 COTRN2A input fields" |
| FR-S09-02 | Key validation | ENTER with Acct # and Card # both blank | `Account or Card Number must be entered...`, cursor Acct # | COTRN02C | :224-229 | — | `TransactionAddServiceTests.KeyFields_BothBlank` |
| FR-S09-03 | Key validation | Acct # present but not 11 digits | `Account ID must be Numeric...`, cursor Acct # | COTRN02C | :196-203 | — | `...AccountNotNumeric` |
| FR-S09-04 | Key resolution | Acct # valid and found in xref AIX | Card # field replaced by the xref card number (account path takes precedence over any typed card); processing continues | COTRN02C | :204-209, :573-590 | S09-B2 | `...AccountFound_FillsCard`, integration `AccountLookup_*` |
| FR-S09-05 | Key resolution | Acct # valid, no xref row | `Account ID NOT found...`, cursor Acct # | COTRN02C | :591-596 | S09-B2 | `...AccountNotFound`, integration |
| FR-S09-06 | Key resolution | Acct # lookup fails (store error) | `Unable to lookup Acct in XREF AIX file...`, cursor Acct # | COTRN02C | :597-603 | S09-B2 | `...AccountStoreError` |
| FR-S09-07 | Key validation | Acct # blank, Card # present but not 16 digits | `Card Number must be Numeric...`, cursor Card # | COTRN02C | :210-217 | — | `...CardNotNumeric` |
| FR-S09-08 | Key resolution | Acct # blank, Card # valid and found | Acct # field filled from the xref account id; processing continues | COTRN02C | :218-223, :606-623 | S09-B2 | `...CardFound_FillsAccount`, integration `CardLookup_*` |
| FR-S09-09 | Key resolution | Card # valid, no xref row | `Card Number NOT found...`, cursor Card # | COTRN02C | :624-629 | S09-B2 | `...CardNotFound`, integration |
| FR-S09-10 | Key resolution | Card # lookup fails (store error) | `Unable to lookup Card # in XREF file...`, cursor Card # | COTRN02C | :630-636 | S09-B2 | `...CardStoreError` |
| FR-S09-11 | Data validation | Any data field blank, checked in this order: Type CD, Category CD, Source, Description, Amount, Orig Date, Proc Date, Merchant ID, Merchant Name, Merchant City, Merchant Zip | `<Field> can NOT be empty...` for the **first** blank field, cursor on it; no later edit runs | COTRN02C | :251-320 | — | `...MandatoryFields_InOrder` (theory, 11 rows) |
| FR-S09-12 | Data validation | All present; Type CD or Category CD contains a non-digit position | `Type CD must be Numeric...` / `Category CD must be Numeric...` (Type checked first), cursor on the field | COTRN02C | :322-337 | — | `...TypeAndCategoryNumeric` |
| FR-S09-13 | Data validation | Amount not exactly `[+-]dddddddd.dd` | `Amount should be in format -99999999.99`, cursor Amount | COTRN02C | :339-351 | — | `...AmountFormat` |
| FR-S09-14 | Data validation | Orig Date / Proc Date not `dddd-dd-dd` (Orig checked first) | `Orig Date should be in format YYYY-MM-DD` / `Proc Date should be in format YYYY-MM-DD`, cursor on the field | COTRN02C | :353-381 | — | `...DateFormat` |
| FR-S09-15 | Data normalisation | Amount passed the layout edit | Amount echoed back as `+99999999.99` (`NUMVAL-C` → `PIC +99999999.99`) before any later edit message | COTRN02C | :383-386 | — | `...AmountNormalised` |
| FR-S09-16 | Data validation | Orig Date / Proc Date well-formed but CSUTLDTC returns severity ≠ `0000` and message ≠ `2513` (Orig first) | `Orig Date - Not a valid date...` / `Proc Date - Not a valid date...`, cursor on the field | COTRN02C, CSUTLDTC | :388-428 | S09-B4 | `...DateNotValid`, `DateValidationServiceTests` |
| FR-S09-17 | Data validation | Merchant ID contains a non-digit position | `Merchant ID must be Numeric...`, cursor Merchant ID | COTRN02C | :430-437 | — | `...MerchantIdNumeric` |
| FR-S09-18 | Confirmation | All edits pass; Confirm blank, `N` or `n` | `Confirm to add this transaction...`, cursor Confirm, nothing written; screen keeps the (normalised) values | COTRN02C | :173-181 | — | `...ConfirmBlankOrN_Prompts` |
| FR-S09-19 | Confirmation | All edits pass; Confirm any other value than Y/y/N/n/blank | `Invalid value. Valid values are (Y/N)...`, cursor Confirm, nothing written | COTRN02C | :182-187 | — | `...ConfirmInvalid` |
| FR-S09-20 | Add | Confirm `Y`/`y` | New TRAN-ID = highest existing TRAN-ID + 1, 16 digits zero-padded (`0000000000000001` on an empty file); record built per §6 and written | COTRN02C | :444-466, :688-689 | S09-B3 | `...Add_UsesNextId`, integration `Add_WritesNextId*` |
| FR-S09-21 | Add | Write succeeds | All 14 fields cleared, cursor Acct #, **green** message `Transaction added successfully.  Your Tran ID is <16 digits>.` (two spaces before `Your`) | COTRN02C | :724-734 | S09-B3 | `...Add_Success_ClearsAndMessages`, integration |
| FR-S09-22 | Add | Write reports duplicate key | `Tran ID already exist...`, cursor Acct #, fields retained | COTRN02C | :735-741 | S09-B3 | `...Add_Duplicate`, integration `Add_ConcurrentDuplicate` |
| FR-S09-23 | Add | Write fails otherwise | `Unable to Add Transaction...`, cursor Acct # | COTRN02C | :742-748 | S09-B3 | `...Add_StoreError` |
| FR-S09-24 | Add / Copy | Highest-id browse fails: start NOTFND / other failure | `Transaction ID NOT found...` / `Unable to lookup Transaction...`, cursor Acct # | COTRN02C | :652-668, :685-697 | S09-B3 | `...BrowseLast_StoreError` |
| FR-S09-25 | Exit | PF3 | Return to the calling program (main menu shell); nothing written | COTRN02C | :136-143, :497-511 | S09-B1 | `TranAddComponent` spec "F3 / Back" |
| FR-S09-26 | Clear | PF4 | All 14 fields and the message cleared, cursor Acct # | COTRN02C | :144-145, :751-757, :762-779 | — | spec "F4 / Clear" |
| FR-S09-27 | Copy last | PF5 with a valid key (FR-S09-02..10 apply first) | Data fields replaced from the transaction with the highest id (type, category, source, amount as `+99999999.99`, description[60], orig/proc date = first 10 chars of the timestamps, merchant id/name[30]/city[25]/zip); Acct #/Card #/Confirm kept as typed; then ENTER processing runs on the result (so an empty Confirm yields FR-S09-18) | COTRN02C | :469-495 | S09-B3 | `...CopyLast_*`, integration `CopyLast_*`, spec "F5" |
| FR-S09-28 | Copy last | PF5 when the file is empty | Copied fields are blank (TRAN-ID zeros), so ENTER processing yields `Type CD can NOT be empty...` | COTRN02C | :688-689, :480-495 | S09-B3 | `...CopyLast_EmptyFile` |
| FR-S09-29 | Invalid key | Any AID other than ENTER/PF3/PF4/PF5 | `Invalid key pressed. Please see below...`; screen state preserved | COTRN02C | :148-151; CSMSG01Y.cpy:20-21 | — | spec "invalid key" |
| FR-S09-30 | Pre-selected entry | Entered with `CDEMO-CT02-TRN-SELECTED` non-blank | Value placed in Card # and ENTER processing performed immediately (typically ending in FR-S09-08 + `Type CD can NOT be empty...`) | COTRN02C | :124-129 | S09-B1 | spec "pre-selected card" |
| FR-S09-31 | Date utility | CSUTLDTC called with a date and mask | 80-byte result: severity `0000` + `Mesg Code:0000` + `Date is valid` for a valid date; severity `0003` + message number + 15-char verdict for the eight recognised CEEDAYS conditions; `Date is invalid` otherwise; return code = severity | CSUTLDTC | CSUTLDTC.cbl:42-57, 97-98, 116-149 | S09-B4 | `DateValidationServiceTests` |
| FR-S09-32 | Date utility | Date outside CEEDAYS supported range (before 1582-10-15) | Message number `2513` (`Unsupp. Range`) — which COTRN02C deliberately **accepts** (`:400,:420`) | CSUTLDTC, COTRN02C | CSUTLDTC.cbl:66,137-138; COTRN02C.cbl:400,420 | S09-B4, S09-B6 | `DateValidationServiceTests.Pre1582_*`, `...DateBefore1582_Accepted` |

## 5. Validation and error catalogue
| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `Account or Card Number must be entered...` | both key fields blank | :226 | blocking | redisplay, cursor Acct # |
| `Account ID must be Numeric...` | Acct # has a non-digit position | :199 | blocking | redisplay, cursor Acct # |
| `Account ID NOT found...` | CXACAIX NOTFND | :593 | blocking | cursor Acct # |
| `Unable to lookup Acct in XREF AIX file...` | CXACAIX other RESP | :600 | blocking | cursor Acct # |
| `Card Number must be Numeric...` | Card # has a non-digit position | :213 | blocking | cursor Card # |
| `Card Number NOT found...` | CCXREF NOTFND | :626 | blocking | cursor Card # |
| `Unable to lookup Card # in XREF file...` | CCXREF other RESP | :633 | blocking | cursor Card # |
| `Type CD can NOT be empty...` | blank | :254 | blocking | cursor Type CD |
| `Category CD can NOT be empty...` | blank | :260 | blocking | cursor Category CD |
| `Source can NOT be empty...` | blank | :266 | blocking | cursor Source |
| `Description can NOT be empty...` | blank | :272 | blocking | cursor Description |
| `Amount can NOT be empty...` | blank | :278 | blocking | cursor Amount |
| `Orig Date can NOT be empty...` | blank | :284 | blocking | cursor Orig Date |
| `Proc Date can NOT be empty...` | blank | :290 | blocking | cursor Proc Date |
| `Merchant ID can NOT be empty...` | blank | :296 | blocking | cursor Merchant ID |
| `Merchant Name can NOT be empty...` | blank | :302 | blocking | cursor Merchant Name |
| `Merchant City can NOT be empty...` | blank | :308 | blocking | cursor Merchant City |
| `Merchant Zip can NOT be empty...` | blank | :314 | blocking | cursor Merchant Zip |
| `Type CD must be Numeric...` | non-digit | :325 | blocking | cursor Type CD |
| `Category CD must be Numeric...` | non-digit | :331 | blocking | cursor Category CD |
| `Amount should be in format -99999999.99` | layout | :345 | blocking | cursor Amount |
| `Orig Date should be in format YYYY-MM-DD` | layout | :360 | blocking | cursor Orig Date |
| `Proc Date should be in format YYYY-MM-DD` | layout | :375 | blocking | cursor Proc Date |
| `Orig Date - Not a valid date...` | CSUTLDTC sev≠0000 and msg≠2513 | :401 | blocking | cursor Orig Date; amount already normalised |
| `Proc Date - Not a valid date...` | same for Proc Date | :421 | blocking | cursor Proc Date |
| `Merchant ID must be Numeric...` | non-digit | :432 | blocking | cursor Merchant ID |
| `Confirm to add this transaction...` | Confirm blank/N/n | :178 | prompt (red) | cursor Confirm, values retained |
| `Invalid value. Valid values are (Y/N)...` | Confirm other | :184 | blocking | cursor Confirm |
| `Transaction ID NOT found...` | STARTBR NOTFND | :657 | blocking | cursor Acct # |
| `Unable to lookup Transaction...` | STARTBR/READPREV other RESP | :664, :693 | blocking | cursor Acct # |
| `Transaction added successfully.  Your Tran ID is <id>.` | WRITE NORMAL | :728-733 | success (green) | all fields cleared, cursor Acct # |
| `Tran ID already exist...` | WRITE DUPKEY/DUPREC | :738 | blocking | cursor Acct # |
| `Unable to Add Transaction...` | WRITE other RESP | :745 | blocking | cursor Acct # |
| `Invalid key pressed. Please see below...` | unmapped AID | CSMSG01Y.cpy:20-21 | blocking | redisplay |
All message text is source-proven (working-storage literals or copybooks); no external message table.

## 6. Field and data derivations
- **Padded-field semantics**: BMS delivers modified input left-justified and blank-padded to the field width (unmodified = LOW-VALUES). "Blank" = all spaces or all low-values (`:196,:252` etc.). `NUMERIC` class tests (`:197,:211,:323,:329,:430`) and the positional layout tests (`:339-381`) apply to the **whole padded field**: a 2-digit field with `1` typed is `1 ` and fails `Type CD must be Numeric...`; an amount `-100.00` is `-100.00     ` and fails the layout. Target: every value is right-padded with spaces to the BMS width before the edits run; values longer than the width cannot arise from the screen and are rejected at the API edge (HTTP 400 ProblemDetails, technical).
- **Account/card normalisation**: `NUMVAL` → `9(11)`/`9(16)` → back to the field (`:204-207,:218-221`), a no-op once the full-width digit test has passed; the xref lookup then fills the other key field (`:209,:223`).
- **Amount**: `NUMVAL-C(TRNAMTI)` → `S9(9)V99` → `PIC +99999999.99` (`:383-386`, `:456-458`) — target `decimal` with 2 scale, echoed as sign + 8 zero-padded integer digits + `.` + 2 digits.
- **Transaction id**: `WS-TRAN-ID-N 9(16)` = highest existing key + 1 (`:448-451`); empty file ⇒ `0000000000000001` (`:688-689`).
- **Record mapping** (`:450-465`): TRAN-DESC X(100) ← 60-char description; MERCHANT-NAME X(50) ← 30 chars; MERCHANT-CITY X(50) ← 25 chars; TRAN-ORIG-TS/PROC-TS X(26) ← the 10-char dates (no time). Target: `Transaction.OriginalTimestamp/ProcessedTimestamp` = date at `00:00:00` (S09-B6).
- **Copy-last mapping** (`:480-494`): description truncated to 60, merchant name to 30, city to 25, timestamps to their first 10 characters, amount rendered `+99999999.99`.
- **Header**: date/time from the system clock (`:548-568`) — cosmetic, not a requirement.

## 7. Mechanics (demoted, cited)
Pseudo-conversational RETURN TRANSID loop (`:156-159`); COMMAREA re-enter flag (`:120,:122`); `MOVE DFHCOMMAREA(1:EIBCALEN)` (`:119`); map SEND/RECEIVE plumbing and cursor `-1` length fields (`:518-546`, every `MOVE -1 TO ...L`); `ERR-FLG-ON` field-clearing branch at `:237-249` — unreachable because every error path ends the task inside `SEND-TRNADD-SCREEN` (`:530-533`); unused `WS-ACCTDAT-FILE` (`:40`), `WS-TRAN-DATE`, `WS-USR-MODIFIED` (`:47-57`); header population (`:548-568`); ENDBR (`:702-707`); CSUTLDTC Vstring plumbing and `DISPLAY` comment (`CSUTLDTC.cbl:25-40, 79`).

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S09-01: Given option 08 selected, When the screen opens, Then all fields are blank, the cursor is on Acct #, and no message shows.
- FR-S09-02: Given both key fields blank, When ENTER, Then `Account or Card Number must be entered...`.
- FR-S09-03: Given Acct # `123` (not 11 digits), When ENTER, Then `Account ID must be Numeric...`.
- FR-S09-04: Given Acct # `00000000050` (xref exists) and Card # blank or anything, When ENTER, Then Card # becomes `0500024453765740` and the data edits run.
- FR-S09-05: Given Acct # `99999999999`, When ENTER, Then `Account ID NOT found...`.
- FR-S09-06: Given the xref store unreachable, When ENTER with an Acct #, Then `Unable to lookup Acct in XREF AIX file...`.
- FR-S09-07: Given Acct # blank and Card # `12AB`, When ENTER, Then `Card Number must be Numeric...`.
- FR-S09-08: Given Acct # blank and Card # `0500024453765740`, When ENTER, Then Acct # becomes `00000000050`.
- FR-S09-09: Given Card # `9999999999999999`, When ENTER, Then `Card Number NOT found...`.
- FR-S09-10: Given the xref store unreachable, When ENTER with a Card #, Then `Unable to lookup Card # in XREF file...`.
- FR-S09-11: Given a valid key and every data field blank, When ENTER, Then `Type CD can NOT be empty...`; filling fields one by one surfaces the 11 messages in the listed order.
- FR-S09-12: Given Type CD `1 ` (or `AB`), When ENTER, Then `Type CD must be Numeric...`; given Type CD `01` and Category CD `1`, Then `Category CD must be Numeric...`.
- FR-S09-13: Given Amount `100.00`, `-100.00`, `+1234567890.00`, or `+00000100,00`, When ENTER, Then `Amount should be in format -99999999.99`.
- FR-S09-14: Given Orig Date `2024/01/15` or `20240115`, When ENTER, Then `Orig Date should be in format YYYY-MM-DD`; same for Proc Date.
- FR-S09-15: Given Amount `-00000100.50`, When the layout edit passes, Then the screen echoes `-00000100.50`; given `+00000100.5x` the layout fails instead.
- FR-S09-16: Given Orig Date `2024-02-30` or `2024-13-01`, When ENTER, Then `Orig Date - Not a valid date...`; given Proc Date `2024-00-10`, Then `Proc Date - Not a valid date...`.
- FR-S09-17: Given Merchant ID `12345678A`, When ENTER, Then `Merchant ID must be Numeric...`.
- FR-S09-18: Given everything valid and Confirm blank or `N`/`n`, When ENTER, Then `Confirm to add this transaction...` and no row is written.
- FR-S09-19: Given Confirm `X`, When ENTER, Then `Invalid value. Valid values are (Y/N)...` and no row is written.
- FR-S09-20: Given highest id `0000000001774260`, When Confirm `Y` (or `y`), Then a row `0000000001774261` is written with the §6 mapping; on an empty table the id is `0000000000000001`.
- FR-S09-21: Given the write succeeds, Then the message is green `Transaction added successfully.  Your Tran ID is 0000000001774261.` and all fields are blank.
- FR-S09-22: Given another adder wrote the same id first, When the write runs, Then `Tran ID already exist...`.
- FR-S09-23: Given the transaction store rejects the write, Then `Unable to Add Transaction...`.
- FR-S09-24: Given the transaction store unreachable during the highest-id read, Then `Unable to lookup Transaction...`.
- FR-S09-25: Given the screen, When PF3 / Exit, Then the main menu is shown.
- FR-S09-26: Given typed values and a message, When PF4 / Clear, Then all fields and the message are blank, cursor Acct #.
- FR-S09-27: Given Acct # `00000000050` and Confirm blank, When PF5, Then the data fields hold the highest-id transaction's values (amount `+99999999.99` form, dates `yyyy-MM-dd`) and the message is `Confirm to add this transaction...`; with Confirm `Y`, PF5 adds immediately.
- FR-S09-28: Given an empty transaction table, When PF5 with a valid key, Then `Type CD can NOT be empty...`.
- FR-S09-29: Given the screen, When F7 (any unmapped F-key), Then `Invalid key pressed. Please see below...` and the fields keep their values.
- FR-S09-30: Given the route opened with a pre-selected card number, When the screen initialises, Then the card is placed in Card # and ENTER processing runs at once.
- FR-S09-31: Given `2024-01-15` / `YYYY-MM-DD`, Then severity `0000`, message `0000`, `Date is valid`; given `2024-02-30`, Then `0003`/`2508`/`Datevalue error`; given `2024-13-01`, Then `0003`/`2517`/`Invalid month`.
- FR-S09-32: Given `1500-01-01`, Then `0003`/`2513`/`Unsupp. Range`, and COTRN02C accepts the date.

## 9. Traceability matrix
FR-S09-01..30 → COTRN02C → cites §4 → `backend/CardDemo.Tests/Transactions/TransactionAddServiceTests.cs`, `TransactionAddIntegrationTests.cs`, `TransactionAddApiIntegrationTests.cs`, `frontend/src/app/transactions/tran-add.component.spec.ts`.
FR-S09-16, 31, 32 → CSUTLDTC → `backend/CardDemo.Tests/Dates/DateValidationServiceTests.cs`.

**FR-S09-29 target disposition**: same AID contract as S-01 (FR-S01-20 disposition): F3 = Exit, F4 = Clear, F5 = Copy Last, every other F1–F12 shows the invalid-key message with the screen state preserved (`frontend/src/app/shared/invalid-key.ts` reused; F4/F5 are classified by the component before delegating to the shared helper).

**Cursor placement**: every message row above names the field the COBOL positions the cursor on (`MOVE -1 TO <field>L`); the API returns it as `cursorField` and the component focuses that input.

## 10. Program index
| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| COTRN02C | add-transaction screen: key resolution, edits, confirm, sequential id, write, copy-last | FR-S09-01..30, 32 | [programs/COTRN02C_functional_requirement.md](programs/COTRN02C_functional_requirement.md) |
| CSUTLDTC | shared date-validity utility (CEEDAYS wrapper) | FR-S09-16, 31, 32 | [programs/CSUTLDTC_functional_requirement.md](programs/CSUTLDTC_functional_requirement.md) |

## 11. Open questions and assumptions
1. **A-1 CEEDAYS emulation**: the LE service has no source. The port classifies, in this order: non-digit where the mask expects digits → `2520`; month outside 1..12 → `2517`; day outside the month (incl. leap rules) → `2508`; Lillian range violation (before 1582-10-15, after 9999-12-31) → `2513`; unsupported mask tokens → `2518`; date shorter than the mask → `2507`. `2509`/`2521` (era conditions) are unreachable with `YYYY-MM-DD` masks and are kept only as recognised codes. Only well-formed `dddd-dd-dd` strings reach the utility from COTRN02C, so the reachable set is `0000`, `2508`, `2517`, `2513`.
2. **D-1 storage type** (S09-B6): the shared layer types `tran_orig_ts`/`tran_proc_ts` as `timestamp`; the 10-char dates are stored at midnight. Equivalent when rendered as `yyyy-MM-dd` (which is all the source ever writes here).
3. **D-2 year 0000**: source accepts `0000-mm-dd` (CEEDAYS `2513` exempted) and stores the text; `DateTime`/`timestamp` cannot hold year 0, so the target answers `Orig/Proc Date - Not a valid date...` for year 0000 only. Years 0001–1581 are accepted exactly like the source.
4. **D-3 over-length input**: impossible on the 3270; rejected with HTTP 400 at the API edge rather than silently truncated (target-state "no silent PIC truncation").
5. FR-S09-30 has no shipped caller (COMEN01C passes the base COMMAREA); implemented as an optional `cardNumber` route query parameter for parity with the live code path.
6. Id generation race (S09-B3) yields the source's own DUPREC message; no retry loop is added (none exists in the source).
