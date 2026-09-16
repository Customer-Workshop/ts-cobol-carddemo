# COTRN02C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COTRN02C — `app/cbl/COTRN02C.cbl`. Stream S-09, **wave 2** (consumes wave-1 CSUTLDTC port and repository seams).
- Role: entry/validator/writer — the add-transaction screen: resolves the account/card key through the card cross-reference, applies the field edits in a fixed order, asks for confirmation, allocates the next sequential transaction id and writes the record; also offers "copy last transaction" pre-fill.

## 2. Trigger / caller contract
- CICS transaction `CT02` (`app/csd/CARDDEMO.CSD:439-440`), reached by `XCTL` from the main menu option 08 (`app/cpy/COMEN02Y.cpy:71`, `COMEN01C.cbl:185`) with `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy`) — `CDEMO-FROM-PROGRAM='COMEN01C'`.
- `EIBCALEN = 0` ⇒ XCTL to COSGN00C (`:115-118`). First entry (`CDEMO-PGM-REENTER` off) clears the screen (`:120-130`); if `CDEMO-CT02-TRN-SELECTED` (`:80`) is non-blank it is moved to the Card # field and ENTER processing runs at once (`:124-129`).
- Re-entered pseudo-conversationally with its own COMMAREA (`:156-159`); PF3 XCTLs back to `CDEMO-FROM-PROGRAM` (`COMEN01C` when blank) (`:136-143`, `:497-511`).
- Target: route `/transactions/add` behind `authGuard`; `POST /api/v1/transactions/add` (ENTER) and `POST /api/v1/transactions/add/copy-last` (PF5); Exit → `/menu`; optional `cardNumber` query parameter = pre-selected card.

## 3. Inputs and outputs
Inputs (map COTRN2A / mapset COTRN02, `app/cpy-bms/COTRN02.CPY:60-138`): ACTIDINI X(11), CARDNINI X(16), TTYPCDI X(2), TCATCDI X(4), TRNSRCI X(10), TDESCI X(60), TRNAMTI X(12), TORIGDTI X(10), TPROCDTI X(10), MIDI X(9), MNAMEI X(30), MCITYI X(25), MZIPI X(10), CONFIRMI X(1); AID key (EIBAID).
Reads: CXACAIX by XREF-ACCT-ID (`:578-586`), CCXREF by XREF-CARD-NUM (`:611-619`), TRANSACT highest key via STARTBR HIGH-VALUES + READPREV (`:644-697`).
Writes: TRANSACT record TRAN-RECORD (`app/cpy/CVTRA05Y.cpy`, `:713-721`).
Outputs: the same 14 fields echoed (with normalisations), ERRMSGO X(78) red/green (`:727`), cursor position, header fields (`:548-568`).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COTRN02C-01 | first entry | blank screen, cursor Acct # | :120-130, :762-779 | FR-S09-01 |
| COTRN02C-02 | both key fields blank | `Account or Card Number must be entered...` | :224-229 | FR-S09-02 |
| COTRN02C-03 | Acct # not all digits | `Account ID must be Numeric...` | :196-203 | FR-S09-03 |
| COTRN02C-04 | Acct # found in AIX | Card # ← XREF-CARD-NUM | :204-209, :589-590 | FR-S09-04 |
| COTRN02C-05 | Acct # NOTFND | `Account ID NOT found...` | :591-596 | FR-S09-05 |
| COTRN02C-06 | Acct # read error | `Unable to lookup Acct in XREF AIX file...` | :597-603 | FR-S09-06 |
| COTRN02C-07 | Card # not all digits | `Card Number must be Numeric...` | :210-217 | FR-S09-07 |
| COTRN02C-08 | Card # found | Acct # ← XREF-ACCT-ID | :218-223, :622-623 | FR-S09-08 |
| COTRN02C-09 | Card # NOTFND | `Card Number NOT found...` | :624-629 | FR-S09-09 |
| COTRN02C-10 | Card # read error | `Unable to lookup Card # in XREF file...` | :630-636 | FR-S09-10 |
| COTRN02C-11 | first blank data field (11, fixed order) | `<Field> can NOT be empty...` | :251-320 | FR-S09-11 |
| COTRN02C-12 | Type/Category CD not all digits | `Type CD must be Numeric...` / `Category CD must be Numeric...` | :322-337 | FR-S09-12 |
| COTRN02C-13 | amount layout | `Amount should be in format -99999999.99` | :339-351 | FR-S09-13 |
| COTRN02C-14 | date layout | `Orig Date should be in format YYYY-MM-DD` / `Proc Date ...` | :353-381 | FR-S09-14 |
| COTRN02C-15 | amount layout passed | amount echoed `+99999999.99` | :383-386 | FR-S09-15 |
| COTRN02C-16 | CSUTLDTC sev≠0000, msg≠2513 | `Orig Date - Not a valid date...` / `Proc Date - Not a valid date...` | :388-428 | FR-S09-16, 32 |
| COTRN02C-17 | Merchant ID not all digits | `Merchant ID must be Numeric...` | :430-437 | FR-S09-17 |
| COTRN02C-18 | Confirm blank/N/n | `Confirm to add this transaction...`, no write | :173-181 | FR-S09-18 |
| COTRN02C-19 | Confirm other | `Invalid value. Valid values are (Y/N)...` | :182-187 | FR-S09-19 |
| COTRN02C-20 | Confirm Y/y | id = highest + 1 (16 digits), record written | :444-466 | FR-S09-20 |
| COTRN02C-21 | WRITE NORMAL | fields cleared, green `Transaction added successfully.  Your Tran ID is <id>.` | :724-734 | FR-S09-21 |
| COTRN02C-22 | WRITE DUPKEY/DUPREC | `Tran ID already exist...` | :735-741 | FR-S09-22 |
| COTRN02C-23 | WRITE other | `Unable to Add Transaction...` | :742-748 | FR-S09-23 |
| COTRN02C-24 | browse NOTFND / other | `Transaction ID NOT found...` / `Unable to lookup Transaction...` | :652-668, :685-697 | FR-S09-24 |
| COTRN02C-25 | PF3 | back to caller | :136-143 | FR-S09-25 |
| COTRN02C-26 | PF4 | all fields + message cleared | :144-145, :751-757 | FR-S09-26 |
| COTRN02C-27 | PF5 | key edits → copy highest transaction's data fields → ENTER processing | :469-495 | FR-S09-27, 28 |
| COTRN02C-28 | unmapped AID | `Invalid key pressed. Please see below...` | :148-151 | FR-S09-29 |
| COTRN02C-29 | pre-selected card in COMMAREA | Card # filled, ENTER processing on entry | :124-129 | FR-S09-30 |

## 5. Business rules and validations
Sequence (blocking, first failure wins — every error path ends the task inside `SEND-TRNADD-SCREEN`, `:530-533`):
1. Key: Acct # present? → all-digit test → AIX read (found/NOTFND/other) → Card # ← xref. Else Card # present? → all-digit test → base read → Acct # ← xref. Else "must be entered" (`:190-230`).
2. Mandatory, in order: Type CD, Category CD, Source, Description, Amount, Orig Date, Proc Date, Merchant ID, Merchant Name, Merchant City, Merchant Zip (`:251-320`).
3. Type CD digits, then Category CD digits (`:322-337`).
4. Amount layout `[+-]dddddddd.dd` over the full 12-byte field (`:339-351`).
5. Orig Date layout, then Proc Date layout `dddd-dd-dd` (`:353-381`).
6. Amount normalised to `+99999999.99` and echoed (`:383-386`).
7. Orig Date validity (CSUTLDTC), then Proc Date validity; message `2513` is accepted (`:388-428`).
8. Merchant ID digits (`:430-437`).
9. Confirm: Y/y add; N/n/blank prompt; other invalid (`:169-188`).
Numeric class tests operate on the blank-padded BMS field, so partial-width entries (`1 `, `123        `) fail the numeric edits.

## 6. Data access and boundaries
- CXACAIX / CCXREF keyed reads (S09-B2): shared `ICardXrefRepository`; RESP 0 / NOTFND / other → found / not-found message / store-error message.
- TRANSACT highest-key browse + WRITE (S09-B3): `ITransactionRepository.GetLastAsync` + `AddAsync` (additive, this stream); ENDFILE (empty) ⇒ id zeros ⇒ first id `0000000000000001`; DUPKEY/DUPREC ⇒ Postgres unique violation.
- Record mapping (`:450-465`): see stream FR §6; timestamps receive the 10-char dates only (S09-B6 → midnight `DateTime`).
- ACCTDAT is named (`:40`) but never read.
- Deviations: D-1 midnight timestamps; D-2 year 0000 rejected; D-3 over-length input → HTTP 400 (stream FR §11).

## 7. Error and edge behavior
- Blank = spaces or low-values throughout (`:196,:210,:252-317`).
- Account path wins when both keys are typed; the typed Card # is overwritten by the xref value (`:209`).
- After a successful add the whole screen (incl. Confirm) is cleared (`:725`, `:762-779`); after any error the typed values (with normalisations) are retained.
- PF5 with Confirm already `Y` adds immediately (copy → `PROCESS-ENTER-KEY`, `:495`); PF5 on an empty file copies blanks and yields `Type CD can NOT be empty...` (`:688-689`).
- Copy-last truncations: description 100→60, merchant name 50→30, city 50→25, timestamps 26→10 (`:480-494`). Amount ≥ 100,000,000 would lose its high-order digit in `PIC +99999999.99` (`:481`) — unreachable for records written by this program (8-digit limit at `:341`).
- `ERR-FLG-ON` clearing branch at `:237-249` is unreachable (see §9).

## 8. Hard-stop boundary
Everything past the PF3 XCTL (menu shell, S-01) and past the no-COMMAREA bounce (sign-on, S-01); downstream consumers of the written record (COTRN00C/COTRN01C, batch CBTRN*) are other streams. The menu registry flag for option 08 is not flipped by this stream.

## 9. Demoted mechanics
Pseudo-conversational RETURN TRANSID (`:156-159`); COMMAREA copy/re-enter flag (`:119-122`); SEND/RECEIVE map plumbing (`:518-546`); cursor `MOVE -1 TO ...L` mechanics (carried to the target as `cursorField`); header population (`:548-568`); ENDBR (`:702-707`); dead `ERR-FLG-ON` field clearing (`:237-249`); unused working storage `WS-ACCTDAT-FILE`, `WS-TRAN-DATE`, `WS-USR-MODIFIED`, `WS-TRAN-AMT` (`:40, :47-57`).

## 10. Traceability
COTRN02C-01..29 ↔ FR-S09-01..30, 32 (table §4) ↔ `backend/CardDemo.Tests/Transactions/TransactionAddServiceTests.cs`, `TransactionAddIntegrationTests.cs`, `TransactionAddApiIntegrationTests.cs`, `frontend/src/app/transactions/tran-add.component.spec.ts`.
