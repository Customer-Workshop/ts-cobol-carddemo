# S-11 Bill Payment (ONLINE) — Stream Analysis (`!mf_stream_analysis`)

Inputs: `CardDemo_inventory.md` §5 row S-11 (CB00 → COBIL00C), `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, CONFIRMED at STOP A), S-01 shell artifacts (auth/menu/route-registry conventions), shared data layer landed at `468e17d` (accounts / card_xref / transactions tables, EF entities, repositories, seed import).

## 1. Pinned stream
- Stream: **S-11 Bill Payment**, process type ONLINE, status active.
- Entry transaction: `CB00` → program `COBIL00C` (`app/csd/CARDDEMO.CSD`: `DEFINE TRANSACTION(CB00) ... PROGRAM(COBIL00C)`).
- Reached from the main menu option 10 "Bill Payment" (`app/cpy/COMEN02Y.cpy`, `CDEMO-MENU-OPT-PGMNAME(10) = 'COBIL00C'`); the S-01 route registry entry for option 10 stays `Enabled: false` until integration flips it.
- Single program, single screen, three VSAM datasets (ACCTDAT read/update, CXACAIX alternate-index read, TRANSACT browse + write). No batch, no called subprograms, no DB2/MQ.

## 2. Program inventory + leaf-first DAG
| Program | Type | Role | XCTL out | Files | Wave |
|---|---|---|---|---|---|
| COBIL00C | CICS/BMS online | Pay the full current balance of an account as a `02`/`2` "BILL PAYMENT - ONLINE" transaction | `COSGN00C` when `EIBCALEN = 0` (:107-109); `CDEMO-FROM-PROGRAM` or `COMEN01C` on PF3 (:128-134) | ACCTDAT (READ UPDATE :343-353, REWRITE :375-384), CXACAIX (READ :408-418), TRANSACT (STARTBR :441-448, READPREV :472-480, ENDBR :501-505, WRITE :510-518) | 1 |

DAG depth 1: COBIL00C is a leaf (its only outbound transfers are to shell programs owned by S-01, consumed as-is). One wave.

## 3. Surfaces (ONLINE)
### COBIL00C — screen COBIL0A / mapset COBIL00 (`app/bms/COBIL00.bms:26-136`, fields `app/cpy-bms/COBIL00.CPY`)
24x80 map. Header row 1-2: TRNNAME X(4) = `CB00`, TITLE01/TITLE02 (COTTL01Y), CURDATE X(8) `mm/dd/yy`, PGMNAME X(8) = `COBIL00C`, CURTIME X(8) `hh:mm:ss` (populated in SEND-BILLPAY-SCREEN via POPULATE-HEADER-INFO, :289-301, :321-338). Row 4 literal `Bill Payment` (BRT NEUTRAL).

| Field | Pos | Len | Attr | Meaning |
|---|---|---|---|---|
| ACTIDIN | (6,21) | 11 | UNPROT, UNDERLINE, FSET, GREEN | Account ID input (`Enter Acct ID:` label at (6,6)). No numeric edit in the program — the 11 characters are used as the ACCTDAT key as typed (:170-171). |
| CURBAL | (11,32) | 14 | ASKIP, FSET, BLUE | Current balance display, formatted from `WS-CURR-BAL PIC +9999999999.99` (:56, :193-194). Label `Your current balance is:` at (11,6). |
| CONFIRM | (15,60) | 1 | UNPROT, UNDERLINE, FSET, GREEN | Confirmation; label `Do you want to pay your balance now. Please confirm:` at (15,6), literal `(Y/N)` at (15,63). |
| ERRMSG | (23,1) | 78 | ASKIP, BRT, RED | Message area; colour switched to GREEN only for the success message (:525). |
| footer | (24,1) | — | | `ENTER=Continue  F3=Back  F4=Clear` |

AID keys (:126-141): ENTER → PROCESS-ENTER-KEY; PF3 → return to caller (`CDEMO-FROM-PROGRAM`, default `COMEN01C`); PF4 → CLEAR-CURRENT-SCREEN; any other AID → `CCDA-MSG-INVALID-KEY` (`Invalid key pressed. Please see below...`, `app/cpy/CSMSG01Y.cpy`).

### PROCESS-ENTER-KEY (:154-244) — validation order (first failure wins, unless noted)
1. `CONF-PAY-NO` reset (:156).
2. ACTIDINI blank/low-values → `Acct ID can NOT be empty...`, cursor ACTIDIN (:158-166).
3. Move ACTIDINI to ACCT-ID and XREF-ACCT-ID (:170-171).
4. EVALUATE CONFIRMI (:173-191):
   - `Y`/`y` → `CONF-PAY-YES`, READ-ACCTDAT-FILE (UPDATE).
   - `N`/`n` → CLEAR-CURRENT-SCREEN, ERR-FLG on (stops processing; screen cleared with no message).
   - blank/low-values → READ-ACCTDAT-FILE.
   - other → `Invalid value. Valid values are (Y/N)...`, cursor CONFIRM. No account read. The screen keeps the previously displayed balance (CURBAL is FSET and echoed back by RECEIVE).
5. `ACCT-CURR-BAL` → `WS-CURR-BAL` → CURBALI (:193-194), unconditional (only visible when a later SEND happens).
6. If no error and `ACCT-CURR-BAL <= 0` → `You have nothing to pay...`, cursor ACTIDIN, balance displayed (:197-206).
7. If no error and CONF-PAY-YES (:208-233): READ-CXACAIX-FILE (first card for the account), STARTBR TRANSACT from HIGH-VALUES, READPREV (last record; ENDFILE → zeros), ENDBR, `WS-TRAN-ID-NUM = TRAN-ID + 1`, build TRAN-RECORD, WRITE, `ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`, REWRITE ACCTDAT.
   Else (blank confirm) → `Confirm to make a bill payment...`, cursor CONFIRM, balance displayed (:235-242).

**Source defect noted (drives deviation D1 in the FR doc):** inside step 7 there is no ERR-FLG test between the PERFORMs. A NOTFND from CXACAIX, a browse error, or a failed WRITE each send an error screen but execution continues: the transaction would still be written (with whatever `XREF-CARD-NUM` held) and the account still rewritten with the reduced balance, and the last SEND wins on the terminal. The target treats every step in 7 as blocking and atomic (see FR doc §11 / plan §3).

### Message catalogue (exact text)
`Acct ID can NOT be empty...` (:161) · `Invalid value. Valid values are (Y/N)...` (:187) · `You have nothing to pay...` (:201) · `Confirm to make a bill payment...` (:236) · `Account ID NOT found...` (:360, :391, :425) · `Unable to lookup Account...` (:367) · `Unable to Update Account...` (:398) · `Unable to lookup XREF AIX file...` (:432) · `Transaction ID NOT found...` (:457) · `Unable to lookup Transaction...` (:463, :491) · `Tran ID already exist...` (:534) · `Unable to Add Bill pay Transaction...` (:540) · `Payment successful.  Your Transaction ID is <tran-id>.` (:526-530; note two spaces — `'Payment successful. '` followed by `' Your Transaction ID is '`) · `Invalid key pressed. Please see below...` (CSMSG01Y).

## 4. Data + field dictionary
| Legacy | Copybook | Target (shared layer at `468e17d`) | Notes |
|---|---|---|---|
| ACCTDAT `ACCT-ID 9(11)`, `ACCT-CURR-BAL S9(10)V99` | `app/cpy/CVACT01Y.cpy` | `accounts.acct_id` varchar(11) PK (C collation), `accounts.acct_curr_bal` numeric(12,2); `Account.AccountId`, `Account.CurrentBalance` | READ UPDATE + REWRITE → `SELECT ... FOR UPDATE` + `UPDATE` in one DB transaction |
| CXACAIX (AIX on `XREF-ACCT-ID`, NONUNIQUEKEY) → `XREF-CARD-NUM X(16)` | `app/cpy/CVACT03Y.cpy` | `card_xref.xref_acct_id` index `ix_card_xref_xref_acct_id`; `ICardXrefRepository.GetFirstByAccountIdAsync` (ordered by card number = first AIX record) | reuse, no extension |
| TRANSACT `TRAN-RECORD` (RECLN 350), key `TRAN-ID X(16)` | `app/cpy/CVTRA05Y.cpy` | `transactions` table, `Transaction` entity, `tran_id` C collation (byte order = VSAM key order) | new **write** path (first online writer of `transactions`) |
| `WS-CURR-BAL PIC +9999999999.99` | COBIL00C.cbl:56 | `BillPaymentResult.CurrentBalance` string (14 chars, explicit sign) | formatting rule FR-S11-06 |
| `WS-TRAN-ID-NUM 9(16)` | :57 | `long` → `D16` string | FR-S11-10 |
| `WS-TIMESTAMP` `yyyy-MM-dd HH:mm:ss.000000` | `app/cpy/CSDAT01Y.cpy:42-55`, COBIL00C.cbl:249-267 | `TimeProvider.GetLocalNow()` truncated to seconds, microseconds `000000` | FR-S11-11 |
| `CDEMO-CB00-TRN-SELECTED X(16)` | COBIL00C.cbl:72 | optional route query parameter `accountId` | FR-S11-20 |
| `CDEMO-FROM-PROGRAM` | `app/cpy/COCOM01Y.cpy` | Angular route `/menu` (S01-B3 contract) | FR-S11-16 |

Numeric mapping: `ACCT-CURR-BAL S9(10)V99` and `TRAN-AMT S9(09)V99` → `decimal`; the MOVE at :223 truncates the high-order digit when |balance| ≥ 1 000 000 000.00 (standard COBOL truncation) — replicated in FR-S11-11.

## 5. Boundary table (headline) — S11-B1..S11-B6 (register append is owned by the integration stage; not edited here)
| ID | Class | Description | Decision taken in this stream |
|---|---|---|---|
| S11-B1 | B4 leaf (write) | First online **write** to TRANSACT (`transactions`): WRITE keyed by TRAN-ID, DUPKEY/DUPREC → `Tran ID already exist...` | Stream-owned `IBillPaymentRepository.AddTransactionAsync` (EF insert); Postgres unique-violation (23505) → duplicate outcome; other → `Unable to Add Bill pay Transaction...` |
| S11-B2 | B4 leaf (update) | ACCTDAT READ UPDATE + REWRITE of `ACCT-CURR-BAL` under the CICS record lock | `GetAccountForUpdateAsync` = `SELECT ... FOR UPDATE` inside an explicit DB transaction; `UpdateAccountAsync` rewrites and commits together with the transaction insert; 0 rows → `Account ID NOT found...` |
| S11-B3 | B4 leaf (key allocation) | TRAN-ID allocation = last key (READPREV from HIGH-VALUES) + 1 | `MAX(tran_id)` under C collation + 1, `D16`; concurrent allocation collides on the PK → S11-B1 duplicate outcome (same as legacy DUPREC) |
| S11-B4 | B5 in | Return routing (`CDEMO-FROM-PROGRAM` → caller, default COMEN01C) | Angular navigates to `/menu` (S01-B3 route contract); no COMMAREA |
| S11-B5 | B5 in | Pre-selected account (`CDEMO-CB00-TRN-SELECTED`) on first entry | Optional `accountId` query param on `/bill-payment`: pre-fills Acct ID and processes ENTER immediately |
| S11-B6 | B10 | Timestamp source (CICS ASKTIME/FORMATTIME) | `TimeProvider` injected into `BillPaymentService`; seconds precision, `.000000` microseconds, local wall clock |
No stored procedures, no external systems, no lead-time requests.

## 6. Waves (leaf-first)
- **Wave 1 (only wave):** COBIL00C — backend `CardDemo.Application/BillPayment` (service, models, repository seam), `CardDemo.Infrastructure/Persistence/BillPaymentRepository`, `CardDemo.Api/Controllers/BillPaymentController` (`POST /api/v1/bill-payment`), Angular `BillPaymentComponent` at `/bill-payment` (authGuard), tests. Consumes S-01 seams (JWT, authGuard, `classifyAidKey`, menu route contract). Route registry flag for option 10 stays disabled.

## 7. Risks
- R1 Fall-through defect in the confirmed path (see §3) — resolved by deviation D1 (blocking + atomic); must be visible to sign-off.
- R2 Concurrency on TRAN-ID allocation — accepted: PK collision yields the legacy duplicate message; retry is the user's action, as on the mainframe.
- R3 Account ID is not numerically validated in this program (unlike COACTVWC) — kept as-is; typed value is the lookup key (trimmed; `"123"` does **not** match `"00000000123"`, exactly like the VSAM key compare of the padded field).
- R4 High-order truncation of TRAN-AMT for balances ≥ 1e9 — replicated faithfully and covered by a unit test; flagged for business review.

## 8. Validation
- Program FR doc: `programs/COBIL00C_functional_requirement.md`. Stream FR doc: `S11_functional_requirement.md`. Plan: `S11_bill_pay_migration_plan.md`.
- Every FR maps to at least one xUnit unit test, one Testcontainers integration test where persistence is involved, and one Angular spec where the UI owns the behaviour (traceability in FR doc §9).
