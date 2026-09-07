# 03 — Glossary: engagement vocabulary, shop vocabulary, business terms

## Engagement (migration-factory) vocabulary — use these words exactly

| Term | Meaning here |
|---|---|
| Module | The unit of the engagement: **CardDemo core** (`app/`). One target state per module. |
| Stream | A user-facing slice of the module migrated and signed off as a unit (e.g. **Account View**). Has a process type, an entry point, a hard stop, and owned programs. |
| Process type | ONLINE (CICS transaction), BATCH (JCL job chain), SUBTRANSACTION (called contract). Selects which target-state profile applies. |
| Entry point | The trancode + program where the stream starts (`CAVW` -> `COACTVWC`). |
| Hard stop | The boundary where the stream's analysis stops following control flow (`XCTL` to `COMEN01C`). |
| Transaction | A CICS transaction id (4 chars) and the task it starts — *not* a database transaction unless said so. |
| Program | An 8-char COBOL member in `app/cbl/`. The unit of FR generation, migration and parity testing. |
| Shared program | A program used by several streams; **owned** by the first stream that migrates it (Account View owns `COSGN00C`, `COMEN01C`, `CSUTLDTC`). |
| Sub-transaction | A program reached by `CALL`/`LINK` that returns to its caller (not `XCTL`). N/A for Account View. |
| pstep | Not used in this shop; JCL steps are called **steps** (`EXEC PGM=`), Control-M units are **jobs**. |
| Boundary | Any place where a stream touches something it does not own: another stream's program, a dataset written elsewhere, an external interface, a scheduler dependency. Registered in `04_boundary_register.md`. |
| Wave | A batch of program migrations executed in parallel by child sessions under one stream plan. |
| Phase 0 | The first wave of the first stream: creates `backend/`, `frontend/`, CI. |
| Parity | Evidence that the migrated program produces the same outputs/messages as the COBOL for the same inputs (source-derived; no mainframe). |
| STOP A–E | The human stop points; A, B, C, E block, D notifies (see `00_context.md` §6). |
| FACT / PROPOSED | Marking on every target-state field: cited from a source vs. a default awaiting confirmation. |
| Cross-check asset | Prior material that may be compared against but never trusted over `app/` source. |
| Lead-time request | A request to an external team (data extract, SSO); simulated via `.migration/requests/`. |

## Shop / mainframe vocabulary (as used in CardDemo)

| Term | Meaning |
|---|---|
| CICS | Online transaction monitor; programs use `EXEC CICS` commands. |
| CSD | CICS System Definition — `app/csd/CARDDEMO.CSD` lists transactions, programs, files, mapsets. |
| BMS map / mapset | 3270 screen layout (`app/bms/*.bms`) and its generated symbolic copybook (`app/cpy-bms/*.CPY`). |
| COMMAREA | Communication area passed between programs/tasks; CardDemo's layout is `CARDDEMO-COMMAREA` (`COCOM01Y`). |
| Pseudo-conversational | Each screen interaction is a separate task; state travels in the COMMAREA (`EIBCALEN = 0` means first entry). |
| XCTL / LINK / RETURN TRANSID | Transfer control (no return) / call and return / end task and name the next transaction. |
| AID / PF key | Attention identifier (Enter, PF3, PF7/8 …) read via `DFHAID`; PF3 = back, PF7/PF8 = page. |
| RESP / RESP2 | CICS command return codes (`0` normal, `13` NOTFND, `22` LENGERR …). |
| VSAM KSDS | Key-sequenced dataset (indexed file); maps to one table with the key as primary key. |
| AIX / PATH | Alternate index over a KSDS (`CXACAIX` = xref by account id); maps to a secondary index. |
| PS / QSAM | Physical sequential dataset (flat file). GDG = generation data group (versioned files). |
| DCLGEN | DB2 table declaration copybook — none in the core module. |
| Copybook | Included source (`COPY name.`) under `app/cpy/`. |
| COMP / COMP-3 / zoned | Binary / packed decimal / display numeric; overpunch sign in the last digit for signed zoned. |
| RECLN | Record length (e.g. ACCOUNT-RECORD is 300 bytes). |
| JCL / PROC / step | Batch job script / reusable procedure / one program execution inside a job. |
| Control-M / CA-7 | Job schedulers; `INCOND`/`OUTCOND` are prerequisite/completion conditions. |
| USRSEC | User security file: `SEC-USR-ID`, `SEC-USR-PWD` (8 chars, plaintext), `SEC-USR-TYPE` `A` admin / `U` user. |
| UniKix / AWS M2 | Rehosting runtimes referenced in `samples/`; not used in this engagement. |

## Business vocabulary

| Term | Meaning / source |
|---|---|
| Account | Credit-card account (`ACCT-ID` 11 digits); balance, credit limit, cash credit limit, open/expiry/reissue dates, cycle credit/debit, group id (`CVACT01Y`). |
| Customer | Card holder (`CUST-ID` 9 digits): names, address, SSN, DOB, FICO score, phones (`CVCUS01Y`). |
| Card | 16-digit card number linked to an account and customer; CVV, embossed name, expiry, active status (`CVACT02Y`). |
| Card cross-reference (xref) | Link card number <-> account <-> customer (`CVACT03Y`); Account View reads it via the account-id AIX. |
| Transaction | Posted card transaction with type, category, amount, merchant, timestamps (`CVTRA05Y`); daily transactions arrive in `DALYTRAN`. |
| Transaction type / category | Reference codes (`CVTRA03Y`, `CVTRA04Y`); category balances `TCATBAL`. |
| Disclosure group | Interest-rate group per account group / transaction category (`DISCGRP`). |
| Bill payment | Online payment of the account balance (`COBIL00C`). |
| Statement | Monthly statement (batch `CBSTM03A/B`). |
| Interest calculation | Monthly batch (`CBACT04C`, job `INTCALC`). |
| Posting | Daily batch that applies transactions to balances (`CBTRN02C`, job `POSTTRAN`). |
| Admin vs user | `A` users see the admin menu (`COADM01C`, user maintenance); `U` users see the main menu (`COMEN01C`). |

## Label / locale conventions
English only; screen titles from `COTTL01Y` and BMS map text; dates displayed `YYYY-MM-DD` (`PIC X(10)`); SSN rendered `###-##-####` in the target; amounts 2 decimals.
