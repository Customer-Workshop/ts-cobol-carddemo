# COBIL00C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COBIL00C — `app/cbl/COBIL00C.cbl`. Stream S-11, **wave 1** (single-program stream).
- Role: online bill payment — pays the full current balance of an account by writing a `02`/`0002` `BILL PAYMENT - ONLINE` transaction and rewriting the account balance.

## 2. Trigger / caller contract
- CICS transaction `CB00` (`app/csd/CARDDEMO.CSD`); reached from the main menu option 10 (`COMEN02Y.cpy`, `COBIL00C`). `EIBCALEN = 0` → XCTL `COSGN00C` (:107-109).
- Re-entered pseudo-conversationally with `CARDDEMO-COMMAREA` + program extension `CDEMO-CB00-*` (:63-72); `CDEMO-PGM-REENTER` distinguishes first display from ENTER processing (:112-124).
- PF3 returns to `CDEMO-FROM-PROGRAM` (default `COMEN01C`) via XCTL (:128-134, :273-284).

## 3. Inputs and outputs
Inputs (map COBIL0A / mapset COBIL00, `app/cpy-bms/COBIL00.CPY`): ACTIDINI X(11) (dict: accounts.acct_id), CONFIRMI X(1), AID key; optional `CDEMO-CB00-TRN-SELECTED` X(16) pre-selection (:116-121).
Reads: ACCTDAT `ACCOUNT-RECORD` (`CVACT01Y.cpy`) key ACCT-ID (READ UPDATE, :343-353); CXACAIX `CARD-XREF-RECORD` (`CVACT03Y.cpy`) by XREF-ACCT-ID (:408-418); TRANSACT last record via STARTBR HIGH-VALUES + READPREV (:441-505).
Writes: TRANSACT `TRAN-RECORD` (`CVTRA05Y.cpy`, :510-518); ACCTDAT REWRITE with reduced `ACCT-CURR-BAL` (:375-384).
Outputs: CURBALO X(14), ERRMSGO X(78) (RED default, GREEN on success :525), cursor position via `-1` length fields, header fields (:321-338).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COBIL00C-01 | blank Acct ID | `Acct ID can NOT be empty...`, cursor ACTIDIN | :158-166 | FR-S11-01 |
| COBIL00C-02 | confirm not Y/y/N/n/blank | `Invalid value. Valid values are (Y/N)...`, cursor CONFIRM, no read | :185-190 | FR-S11-02 |
| COBIL00C-03 | confirm N/n | screen cleared, no message, no read | :178-180, :552-566 | FR-S11-03 |
| COBIL00C-04 | ACCTDAT NOTFND | `Account ID NOT found...` | :358-364 | FR-S11-04 |
| COBIL00C-05 | ACCTDAT other RESP | `Unable to lookup Account...` | :365-371 | FR-S11-05 |
| COBIL00C-06 | account read | CURBAL = `+9999999999.99` edit of ACCT-CURR-BAL | :56, :193-194 | FR-S11-06 |
| COBIL00C-07 | balance ≤ 0 | `You have nothing to pay...`, cursor ACTIDIN | :197-206 | FR-S11-07 |
| COBIL00C-08 | confirm blank, balance > 0 | `Confirm to make a bill payment...`, cursor CONFIRM | :235-239 | FR-S11-08 |
| COBIL00C-09 | CXACAIX NOTFND / other | `Account ID NOT found...` / `Unable to lookup XREF AIX file...` | :420-436 | FR-S11-09 |
| COBIL00C-10 | id allocation | TRAN-ID = last + 1 (9(16)); ENDFILE → zeros; errors `Transaction ID NOT found...` / `Unable to lookup Transaction...` | :211-217, :450-467, :483-496 | FR-S11-10 |
| COBIL00C-11 | build record | constants + amount + card + timestamps per :218-231 | :218-231, :249-267 | FR-S11-11 |
| COBIL00C-12 | WRITE NORMAL | INITIALIZE-ALL-FIELDS, green `Payment successful.  Your Transaction ID is <id>.` | :522-531 | FR-S11-12 |
| COBIL00C-13 | WRITE DUPKEY/DUPREC | `Tran ID already exist...` | :532-537 | FR-S11-13 |
| COBIL00C-14 | WRITE other | `Unable to Add Bill pay Transaction...` | :538-545 | FR-S11-14 |
| COBIL00C-15 | REWRITE | balance − TRAN-AMT; NOTFND `Account ID NOT found...`; other `Unable to Update Account...` | :232-233, :386-403 | FR-S11-15 |
| COBIL00C-16 | PF3 | return to caller | :128-134 | FR-S11-16 |
| COBIL00C-17 | PF4 | CLEAR-CURRENT-SCREEN | :135-136 | FR-S11-17 |
| COBIL00C-18 | other AID | `Invalid key pressed. Please see below...` | :137-140 | FR-S11-18 |
| COBIL00C-19 | EIBCALEN = 0 | routed to sign-on | :107-109 | FR-S11-19 |
| COBIL00C-20 | TRN-SELECTED populated on first entry | ACTIDIN pre-filled, PROCESS-ENTER-KEY executed before first SEND | :116-121 | FR-S11-20 |

## 5. Business rules and validations
Sequence in PROCESS-ENTER-KEY (:154-244): Acct ID mandatory → confirm value edit (Y/y → confirmed; N/n → clear+stop; blank → lookup only; other → error) → account READ UPDATE → balance edit to CURBAL → balance ≤ 0 stop → confirmed ? (xref → last id + 1 → build → WRITE → subtract → REWRITE) : confirmation prompt. Blank/low-values are equivalent for both inputs (:159, :181-182). Acct ID has no numeric/length edit (:170).

## 6. Data access and boundaries
- ACCTDAT read-for-update + rewrite (S11-B2): `IBillPaymentRepository.GetAccountForUpdateAsync` (`SELECT … FOR UPDATE`) + `UpdateAccountAsync` in one DB transaction.
- CXACAIX first-record read (reuse shared `ICardXrefRepository.GetFirstByAccountIdAsync`, ordered by card number).
- TRANSACT last-key + write (S11-B1/B3): `GetLastTransactionIdAsync` (`MAX(tran_id)`), `AddTransactionAsync`.
- Clock (S11-B6): `TimeProvider`.
- Outbound XCTLs to `COSGN00C`/`COMEN01C` are S-01 seams (`authGuard`, `/menu`).

## 7. Error and edge behavior
- Invalid confirm value is rejected before any file access; the previously displayed balance stays on screen because CURBAL is FSET and echoed by RECEIVE.
- `N` clears the screen even when the account does not exist (no read happens).
- Balance ≤ 0 is reported with the balance visible (CURBAL set at :193-194 before the SEND at :204).
- TRAN-AMT `S9(09)V99` truncates the high-order digit of balances ≥ 1e9 (:223); the residual stays on the account (:232). Replicated.
- **Fall-through defect (deviation D1):** no ERR-FLG test between :209-233; failures in xref/browse/write do not stop the write/rewrite on the mainframe. Target: first failure is terminal, message per failing step, insert + update atomic (rolled back together).

## 8. Hard-stop boundary
Menu integration (route registry flag for option 10) and the S-01 shell programs. No downstream XCTL other than the shell.

## 9. Demoted mechanics
RETURN TRANSID loop (:144-147); COMMAREA copy (:111); header population (:321-338); SEND/RECEIVE MAP plumbing (:289-314); RESP DISPLAY diagnostics; STARTBR/ENDBR plumbing; unused `CDEMO-CB00-TRNID-FIRST/LAST/PAGE-NUM/NEXT-PAGE-FLG/TRN-SEL-FLG` (:63-71).

## 10. Traceability
COBIL00C-01..20 → FR-S11-01..20 → `BillPaymentService` / `BillPaymentController` / `BillPaymentComponent` → `BillPaymentServiceTests`, `BillPaymentIntegrationTests`, `bill-payment.component.spec.ts`.
