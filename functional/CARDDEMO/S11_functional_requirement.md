# S-11 Bill Payment (ONLINE) — Stream Functional Requirements (`!mf_stream_fr_generation`)

Source of truth: `app/cbl/COBIL00C.cbl` (cites below are `COBIL00C.cbl:<line>` unless prefixed), `app/bms/COBIL00.bms`, `app/cpy-bms/COBIL00.CPY`, `app/cpy/CVTRA05Y.cpy`, `app/cpy/CVACT01Y.cpy`, `app/cpy/CVACT03Y.cpy`, `app/cpy/CSMSG01Y.cpy`, `app/csd/CARDDEMO.CSD`. Analysis: `S11_bill_pay_analysis.md`.

## 1. Purpose and scope
Pay the **entire current balance** of one account in a single online step: the user keys an account id, sees the balance, confirms with `Y`, and the program writes a `BILL PAYMENT - ONLINE` transaction and zeroes the balance. In scope: transaction CB00 / program COBIL00C only. Out of scope: menu (S-01), other transaction programs, batch posting.

## 2. Actors and preconditions
- Actor: any signed-on CardDemo user (no admin restriction in COBIL00C; menu option 10 is a `U` option in COMEN02Y).
- Precondition: signed on (a call with `EIBCALEN = 0` is routed to the sign-on program, :107-109 → target: `authGuard` + `[Authorize]`).
- Data: `accounts`, `card_xref`, `transactions` populated by the shared legacy import.

## 3. Surface specification
### Bill Payment screen COBIL0A (`app/bms/COBIL00.bms:26-136`)
| Field | Len | I/O | Rule |
|---|---|---|---|
| Acct ID (ACTIDIN) | 11 | input | mandatory; used as the account key exactly as typed (no numeric edit) |
| Current balance (CURBAL) | 14 | output | `+9999999999.99` (explicit sign, 10 integer digits zero-filled, 2 decimals) |
| Confirm (CONFIRM) | 1 | input | `Y`/`y`/`N`/`n`/blank accepted; anything else rejected |
| Message (ERRMSG) | 78 | output | red by default; green for the success message |
| Footer | — | — | `ENTER=Continue  F3=Back  F4=Clear` |

## 4. Functional requirements (KEEP)
| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S11-01 | Input validation | ENTER with blank Acct ID | `Acct ID can NOT be empty...`, cursor Acct ID; nothing read | COBIL00C | :158-166 | — | `BillPaymentServiceTests.BlankAccountId_*`; `bill-payment.component.spec` |
| FR-S11-02 | Input validation | Acct ID present; Confirm not one of `Y y N n` blank | `Invalid value. Valid values are (Y/N)...`, cursor Confirm; no account lookup; previously displayed balance retained | COBIL00C | :173-191 | — | `BillPaymentServiceTests.InvalidConfirm_*`; component spec |
| FR-S11-03 | Decline | Acct ID present; Confirm `N`/`n` | Screen cleared (Acct ID, balance, confirm, message blank), cursor Acct ID; no lookup, no payment | COBIL00C | :178-180, :552-566 | — | `BillPaymentServiceTests.DeclineN_*`; component spec |
| FR-S11-04 | Account lookup | Confirm blank or `Y`; no account with the typed key | `Account ID NOT found...`, cursor Acct ID | COBIL00C | :343-364 | S11-B2 | `BillPaymentServiceTests.AccountNotFound_*`; `BillPaymentIntegrationTests` |
| FR-S11-05 | Account lookup | Account store unavailable / other error | `Unable to lookup Account...`, cursor Acct ID | COBIL00C | :365-371 | S11-B2 | `BillPaymentServiceTests.AccountStoreError_*` |
| FR-S11-06 | Balance display | Account read succeeds | Current balance shown as `+9999999999.99` / `-9999999999.99` (14 chars) | COBIL00C | :56, :193-194 | — | `BillPaymentServiceTests.BalanceFormat_*`; component spec |
| FR-S11-07 | Nothing to pay | Account read; balance ≤ 0 | `You have nothing to pay...`, cursor Acct ID, balance displayed; no payment | COBIL00C | :197-206 | — | `BillPaymentServiceTests.NothingToPay_*`; integration |
| FR-S11-08 | Confirmation prompt | Confirm blank; account read; balance > 0 | `Confirm to make a bill payment...`, cursor Confirm, balance displayed; nothing written | COBIL00C | :235-242 | — | `BillPaymentServiceTests.ConfirmRequired_*`; integration; component spec |
| FR-S11-09 | Card resolution | Confirm `Y`; balance > 0; account has no card xref | `Account ID NOT found...`; other xref error → `Unable to lookup XREF AIX file...`; no payment | COBIL00C | :408-436 | reuse `ICardXrefRepository` | `BillPaymentServiceTests.XrefNotFound_*`, `XrefStoreError_*`; integration |
| FR-S11-10 | Transaction id allocation | Payment confirmed | New id = highest existing TRAN-ID + 1, 16 digits zero-filled; empty file → `0000000000000001`; browse error → `Unable to lookup Transaction...` | COBIL00C | :211-217, :441-505 | S11-B3 | `BillPaymentServiceTests.TransactionId_*`; integration |
| FR-S11-11 | Transaction content | Payment confirmed | Record: type `02`, category `0002`, source `POS TERM`, description `BILL PAYMENT - ONLINE`, amount = current balance (as `S9(09)V99`), card = first card of the account, merchant id `999999999`, name `BILL PAYMENT`, city `N/A`, zip `N/A`, orig/proc timestamp = now (`yyyy-MM-dd HH:mm:ss.000000`) | COBIL00C | :218-231, :249-267 | S11-B1, S11-B6 | `BillPaymentServiceTests.TransactionContent_*`; integration |
| FR-S11-12 | Success | Transaction written and account updated | Fields cleared, cursor Acct ID, **green** message `Payment successful.  Your Transaction ID is <id>.` | COBIL00C | :522-531, :242 | — | service, integration, component spec |
| FR-S11-13 | Duplicate key | Allocated TRAN-ID already exists | `Tran ID already exist...`, cursor Acct ID; nothing persisted | COBIL00C | :532-537 | S11-B1 | service; integration |
| FR-S11-14 | Write error | Transaction write fails for any other reason | `Unable to Add Bill pay Transaction...`, cursor Acct ID; nothing persisted | COBIL00C | :538-545 | S11-B1 | service |
| FR-S11-15 | Account update | Payment confirmed | `ACCT-CURR-BAL := ACCT-CURR-BAL − TRAN-AMT` rewritten; rewrite not-found → `Account ID NOT found...`; other → `Unable to Update Account...` | COBIL00C | :232-233, :375-403 | S11-B2 | service; integration |
| FR-S11-16 | Exit | PF3 | Return to the calling screen (main menu) | COBIL00C | :128-134, :273-284 | S11-B4 | component spec |
| FR-S11-17 | Clear | PF4 | Acct ID, balance, confirm and message cleared, cursor Acct ID; no server call | COBIL00C | :135-136, :552-566 | — | component spec |
| FR-S11-18 | Invalid key | Any AID other than ENTER/PF3/PF4 | `Invalid key pressed. Please see below...`, screen redisplayed unchanged | COBIL00C | :137-140; CSMSG01Y.cpy | — | component spec |
| FR-S11-19 | Sign-on required | Entry without session (`EIBCALEN = 0`) | Routed to sign-on | COBIL00C | :107-109 | S01-B6 | route guard spec (`authGuard`), API `[Authorize]` (integration 401) |
| FR-S11-20 | Pre-selected account | First entry with `CDEMO-CB00-TRN-SELECTED` populated | Acct ID pre-filled and ENTER processing performed immediately | COBIL00C | :112-122 | S11-B5 | component spec (`?accountId=`) |

## 5. Validation and error catalogue
| Message | Trigger | Cite | Blocking? | Cursor | Resulting state |
|---|---|---|---|---|---|
| `Acct ID can NOT be empty...` | blank Acct ID | :161 | yes | Acct ID | redisplay |
| `Invalid value. Valid values are (Y/N)...` | confirm not Y/y/N/n/blank | :187 | yes | Confirm | redisplay, previous balance kept |
| `Account ID NOT found...` | ACCTDAT NOTFND / CXACAIX NOTFND / REWRITE NOTFND | :360, :425, :391 | yes | Acct ID | redisplay |
| `Unable to lookup Account...` | ACCTDAT other RESP | :367 | yes | Acct ID | redisplay |
| `You have nothing to pay...` | balance ≤ 0 | :201 | yes | Acct ID | redisplay with balance |
| `Confirm to make a bill payment...` | confirm blank, balance > 0 | :236 | prompt | Confirm | redisplay with balance |
| `Unable to lookup XREF AIX file...` | CXACAIX other RESP | :432 | yes | Acct ID | redisplay |
| `Transaction ID NOT found...` | STARTBR NOTFND (unreachable with HIGH-VALUES on a KSDS; kept in catalogue) | :457 | yes | Acct ID | redisplay |
| `Unable to lookup Transaction...` | STARTBR/READPREV other RESP | :463, :491 | yes | Acct ID | redisplay |
| `Tran ID already exist...` | WRITE DUPKEY/DUPREC | :534 | yes | Acct ID | redisplay |
| `Unable to Add Bill pay Transaction...` | WRITE other RESP | :540 | yes | Acct ID | redisplay |
| `Unable to Update Account...` | REWRITE other RESP | :398 | yes | Acct ID | redisplay |
| `Payment successful.  Your Transaction ID is <id>.` | WRITE NORMAL | :526-530 | — (green) | Acct ID | fields cleared |
| `Invalid key pressed. Please see below...` | other AID | :139 | yes | unchanged | redisplay |

## 6. Field and data derivations
- Account key = Acct ID as typed (X(11) → 9(11) byte move, :170); the target trims trailing/leading blanks and matches `acct_id` exactly — no zero-padding or numeric normalisation.
- `CURBAL` = `ACCT-CURR-BAL` edited by `PIC +9999999999.99` (:56): sign character `+`/`-`, 10 integer digits, `.`, 2 decimals (truncated, not rounded).
- `TRAN-ID` = `(max existing TRAN-ID as integer) + 1` formatted `9(16)` (:215-217); empty file → `1`.
- `TRAN-AMT` = `ACCT-CURR-BAL` moved into `S9(09)V99` (:223): high-order digit truncated for |balance| ≥ 1e9; new balance = old − TRAN-AMT (:232).
- `TRAN-CAT-CD` = `2` in `9(04)` → stored `0002`; `TRAN-MERCHANT-ID` = `999999999` in `9(09)`.
- Timestamps: `WS-TIMESTAMP` = `yyyy-MM-dd HH:mm:ss.000000` (CSDAT01Y, :249-267) — `MS6` is never populated, so microseconds are literally zero.

## 7. Mechanics (demoted, cited)
Pseudo-conversational RETURN TRANSID (:144-147); COMMAREA copy / `CDEMO-PGM-REENTER` (:111-113); header population (:321-338); RECEIVE/SEND MAP, ERASE, CURSOR (:289-314); RESP/RESP2 DISPLAY diagnostics (:366 etc.); browse START/END plumbing (:441-505); COMMAREA extension fields `CDEMO-CB00-*` other than `TRN-SELECTED` (:63-72, unused). Hard stop: XCTL to `COSGN00C`/`COMEN01C` (S-01 owned).

## 8. Acceptance criteria (Given/When/Then)
- FR-S11-01: Given the screen, When ENTER with blank Acct ID, Then `Acct ID can NOT be empty...` and nothing is read.
- FR-S11-02: Given Acct ID `X` and Confirm `Q`, When ENTER, Then `Invalid value. Valid values are (Y/N)...` and no account lookup happens (even if `X` does not exist).
- FR-S11-03: Given any Acct ID and Confirm `n`, When ENTER, Then all fields and the message are blank and nothing was read or written.
- FR-S11-04: Given no account `99999999999`, When ENTER with blank confirm, Then `Account ID NOT found...`.
- FR-S11-05: Given the account store failing, When ENTER, Then `Unable to lookup Account...`.
- FR-S11-06: Given account balance `1234.56`, When read, Then balance shows `+0000001234.56`; given `-50.00` → `-0000000050.00`.
- FR-S11-07: Given balance `0.00` or negative and Confirm blank or `Y`, When ENTER, Then `You have nothing to pay...` with the balance shown and no write.
- FR-S11-08: Given balance `> 0` and Confirm blank, When ENTER, Then `Confirm to make a bill payment...` with the balance shown and no write.
- FR-S11-09: Given balance `> 0`, Confirm `Y`, and no card xref for the account, When ENTER, Then `Account ID NOT found...` and no write.
- FR-S11-10: Given highest transaction id `0000000000000123`, When a payment is posted, Then its id is `0000000000000124`; given no transactions → `0000000000000001`.
- FR-S11-11: Given a confirmed payment, Then the stored transaction has the FR-S11-11 constants, amount = balance, card = the account's first card, and both timestamps = now at second precision.
- FR-S11-12: Given a confirmed payment, Then the screen is cleared and shows green `Payment successful.  Your Transaction ID is 0000000000000124.`.
- FR-S11-13: Given the allocated id already exists at write time, Then `Tran ID already exist...` and the account balance is unchanged.
- FR-S11-14: Given the transaction write fails otherwise, Then `Unable to Add Bill pay Transaction...` and nothing persisted.
- FR-S11-15: Given a confirmed payment on balance `1234.56`, Then `acct_curr_bal` becomes `0.00` in the same unit of work as the insert.
- FR-S11-16: Given the screen, When F3 / Back, Then the main menu is shown.
- FR-S11-17: Given filled fields and a message, When F4 / Clear, Then all are blank and no request was sent.
- FR-S11-18: Given the screen, When F5, Then `Invalid key pressed. Please see below...` and fields are unchanged.
- FR-S11-19: Given no session, When `/bill-payment` is opened, Then the sign-on screen is shown; API calls without a token get 401.
- FR-S11-20: Given `/bill-payment?accountId=00000000001`, When the screen opens, Then Acct ID is pre-filled and ENTER processing has run (balance/prompt shown).

## 9. Traceability matrix
FR-S11-01..15 → COBIL00C → `CardDemo.Application.BillPayment.BillPaymentService` → `CardDemo.Tests/BillPayment/BillPaymentServiceTests.cs` (unit, all FRs) + `CardDemo.Tests/BillPayment/BillPaymentIntegrationTests.cs` (Testcontainers Postgres: FR-S11-04, 07, 08, 09, 10, 11, 12, 13, 15, 19).
FR-S11-01..03, 06..08, 12, 16..18, 20 (UI-owned rendering / keys) → `frontend/src/app/bill-payment/bill-payment.component.spec.ts`; FR-S11-19 → `frontend/src/app/app.routes.ts` (`authGuard`) + `MenuApiIntegrationTests`-style 401 check.

## 10. Program index
| Program | Transaction | Map/Mapset | Files | Program FR doc |
|---|---|---|---|---|
| COBIL00C | CB00 | COBIL0A / COBIL00 | ACCTDAT (R/U), CXACAIX (R), TRANSACT (browse, W) | `programs/COBIL00C_functional_requirement.md` |

## 11. Open questions, assumptions and deviations
- **D1 (behavioural, deliberate):** COBIL00C does not test ERR-FLG between READ-CXACAIX / STARTBR / READPREV / WRITE / REWRITE (:209-233). On the mainframe a failure in one step still executes the following steps (e.g. no xref → transaction written with a blank card number and balance still zeroed; failed WRITE → balance still zeroed). The target makes every step blocking at the first failure and persists the insert + update atomically; the *message* shown is the legacy one for the failing step. Reason: the fall-through is a defect, not a business rule, and it corrupts balances. Flagged for sign-off.
- A1: `Transaction ID NOT found...` (STARTBR NOTFND) is unreachable when browsing from HIGH-VALUES; the constant is retained but no target condition maps to it.
- A2: Return target on PF3 is the main menu (`/menu`); COMEN01C is the only caller in the shipped catalogue and the default when `CDEMO-FROM-PROGRAM` is blank.
- A3: The `CDEMO-CB00-TRN-SELECTED` pre-selection is exposed as the `accountId` query parameter (no caller in the shipped catalogue sets it; kept for parity).
- A4: Timestamps use the server's local clock (CICS ASKTIME/FORMATTIME semantics), second precision.
