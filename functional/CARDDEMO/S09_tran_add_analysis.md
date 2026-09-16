# S-09 Transaction Add — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Stream S-09 from the catalog (`CardDemo_inventory.md` §5, row S-09); process type **ONLINE**.
Target profiles applied (read-only): CORE + ONLINE + SUBTRANSACTION + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md` (C# 12 / .NET 8 + ASP.NET Core 8, Angular 18 standalone, PostgreSQL 16, single repo).
Base: shared data layer (accounts/customers/cards/xref/transactions schema, EF entities, repositories, seed import) already landed on `devin/1787242078-carddemo-premigration`; S-01 shell (JWT `SessionContext`, menu route registry, AID parity helper) reused, never forked.

## 1. Pinned stream

- **Entry point (proof)**: `CT02 -> COTRN02C` (`app/csd/CARDDEMO.CSD:439-440`); program defined at `:271`. Reached from the main menu as option 08 `Transaction Add` (`app/cpy/COMEN02Y.cpy:71`, dispatched by `COMEN01C.cbl:185` — S-01 registry row `ProgramKey=COTRN02C`, flag disabled until integration).
- **Hard stop**: every transfer out of COTRN02C is another stream's entry or the S-01 shell: `XCTL COSGN00C` when entered without COMMAREA (`COTRN02C.cbl:115-118`), `XCTL CDEMO-FROM-PROGRAM`/`COMEN01C` on PF3 (`:136-143`, `:497-511`). No other XCTL/LINK exists. Nothing past those transfers is implemented here.
- **Shared utility ported by this stream**: `CSUTLDTC` (`app/cbl/CSUTLDTC.cbl`), CALLed at `COTRN02C.cbl:393,413` and by S-10 `CORPT00C.cbl:392` (`CardDemo_inventory.md` §6 row CSUTLDTC: "first of S-09/S-10 migrated owns it").
- Pseudo-conversational shape: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` (`:156-159`); every screen send ends the task (`SEND-TRNADD-SCREEN` → `EXEC CICS RETURN`, `:518-533`), so **the first failing edit is the only message a user ever sees** — this is the ordering contract the FRs pin.

## 2. Program inventory + leaf-first DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COTRN02C | app/cbl/COTRN02C.cbl | entry/validate/write (add transaction screen) | CICS READ CXACAIX (:578-586), READ CCXREF (:611-619), STARTBR/READPREV/ENDBR TRANSACT (:644-707), WRITE TRANSACT (:713-721); CALL CSUTLDTC (:393,:413); XCTL COSGN00C (:115-118) / CDEMO-FROM-PROGRAM (:136-143) | no | yes |
| CSUTLDTC | app/cbl/CSUTLDTC.cbl | leaf utility — date validation wrapper over LE `CEEDAYS` (:116-120) | CALL CEEDAYS (LE callable service, no source) | yes: S-09 + S-10 | yes |

No absent programs inside the hard stop. `CEEDAYS` is an IBM Language Environment callable service (no COBOL source); its feedback codes are decoded by CSUTLDTC's 88-levels (`CSUTLDTC.cbl:62-70`) — that decoding table is the whole contract this stream must honour.

**Leaf-first DAG**: `CSUTLDTC` (leaf, depth 0) ← `COTRN02C` (depth 1). Source: [`diagrams/S09_tran_add_dag.mmd`](diagrams/S09_tran_add_dag.mmd).

## 3. Surfaces (ONLINE)

### COTRN02C — screen COTRN2A / mapset COTRN02 (`app/bms/COTRN02.bms`, fields `app/cpy-bms/COTRN02.CPY`)
Mapset `CTRL=(ALARM,FREEKB)` (`bms:19`). Title `Add Transaction` (`bms:78-79`). Legend line 24: `ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.` (`bms:300-303`). Header `Tran:`/`Prog:`/`Date:`/`Time:` (`bms:32-74`) populated by `POPULATE-HEADER-INFO` (`COTRN02C.cbl:548-568`, CT02 / COTRN02C / system clock).

| Field | Label (verbatim, bms line) | I/O | Len (CPY line) | Screen edits (cbl lines) |
|---|---|---|---|---|
| ACTIDIN | `Enter Acct #:` (:83-84) | INPUT, IC cursor | X(11) (:60) | full-width NUMERIC when present (:196-203); normalised via NUMVAL (:204-207); filled from XREF on card path (:223) |
| CARDNIN | `Card #:` (:102-103), `(or)` (:97-98) | INPUT | X(16) (:66) | full-width NUMERIC when present and acct blank (:210-217); filled from XREF on acct path (:209) |
| TTYPCD | `Type CD:` (:120-121) | INPUT | X(2) (:72) | mandatory (:252-257); NUMERIC (:323-328) |
| TCATCD | `Category CD:` (:133-134) | INPUT | X(4) (:78) | mandatory (:258-263); NUMERIC (:329-334) |
| TRNSRC | `Source:` (:146-147) | INPUT | X(10) (:84) | mandatory (:264-269) |
| TDESC | `Description:` (:159-160) | INPUT | X(60) (:90) | mandatory (:270-275) |
| TRNAMT | `Amount:` (:172-173), hint `(-99999999.99)` (:211-212) | INPUT | X(12) (:96) | mandatory (:276-281); fixed layout sign+8+`.`+2 (:339-351); redisplayed as `+99999999.99` (:383-386) |
| TORIGDT | `Orig Date:` (:185-186), hint `(YYYY-MM-DD)` (:216-217) | INPUT | X(10) (:102) | mandatory (:282-287); layout (:353-366); CSUTLDTC (:388-408) |
| TPROCDT | `Proc Date:` (:198-199), hint `(YYYY-MM-DD)` (:221-222) | INPUT | X(10) (:108) | mandatory (:288-293); layout (:368-381); CSUTLDTC (:410-428) |
| MID | `Merchant ID:` (:226-227) | INPUT | X(9) (:114) | mandatory (:294-299); NUMERIC (:430-437) |
| MNAME | `Merchant Name:` (:239-240) | INPUT | X(30) (:120) | mandatory (:300-305) |
| MCITY | `Merchant City:` (:252-253) | INPUT | X(25) (:126) | mandatory (:306-311) |
| MZIP | `Merchant Zip:` (:265-266) | INPUT | X(10) (:132) | mandatory (:312-317) |
| CONFIRM | `You are about to add this transaction. Please confirm` (:278-280), `(Y/N)` (:288-292) | INPUT | X(1) (:138) | Y/y add, N/n/blank prompt, other invalid (:169-188) |
| ERRMSG | — | OUTPUT, BRT RED (:293-295) | X(78) (:144) | message catalogue §4 of FR doc; GREEN on success (:727) |

Input-field semantics carried by BMS: unmodified fields arrive as LOW-VALUES, modified fields left-justified and blank-padded to the field width. The COBOL `NUMERIC` class tests therefore require **every position** of the field to be a digit (e.g. Type CD `1 ` fails `Type CD must be Numeric...`), and the amount/date layout tests are positional over the padded field. The target keeps the same padded-field semantics (see FR doc §6).

AID keys (`:133-152`): ENTER → PROCESS-ENTER-KEY; PF3 → return to `CDEMO-FROM-PROGRAM` (or COMEN01C when blank); PF4 → CLEAR-CURRENT-SCREEN; PF5 → COPY-LAST-TRAN-DATA; any other → `CCDA-MSG-INVALID-KEY` (`app/cpy/CSMSG01Y.cpy:20-21`) with the screen state preserved.

First entry (`:120-130`): screen cleared (`INITIALIZE-ALL-FIELDS`, `:762-779`), cursor on ACTIDIN; if `CDEMO-CT02-TRN-SELECTED` (`:80`) is non-blank it is copied into CARDNIN and ENTER processing runs immediately (`:124-129`). No shipped caller sets that field (COMEN01C passes the base COMMAREA, so the extension is space-filled) — latent but live code, kept as a pre-fill entry contract.

### CSUTLDTC — no screen
Linkage `USING LS-DATE X(10), LS-DATE-FORMAT X(10), LS-RESULT X(80)` (`CSUTLDTC.cbl:83-88`). The 80-byte result is the `WS-MESSAGE` group (`:42-57`): severity `9(4)` | `Mesg Code:` | message number `9(4)` | 15-char verdict text (`:128-149`) | `TstDate:` date | `Mask used:` format. `RETURN-CODE` = severity (`:98`). The caller only inspects severity (`'0000'` = valid) and message number (`2513` exempted) — `COTRN02C.cbl:397-400`.

## 4. Data + field dictionary

| Dataset / structure | Copybook | Access in stream | Target (shared data layer) |
|---|---|---|---|
| CXACAIX (xref AIX by account) | `CVACT03Y.cpy` CARD-XREF-RECORD, key XREF-ACCT-ID 9(11) | READ EQUAL (:578-586) | `card_xref` index `ix_card_xref_xref_acct_id` → `ICardXrefRepository.GetFirstByAccountIdAsync` |
| CCXREF (xref base) | same, key XREF-CARD-NUM X(16) | READ EQUAL (:611-619) | `card_xref` PK → `ICardXrefRepository.GetByCardNumberAsync` |
| TRANSACT (KSDS, key TRAN-ID X(16)) | `CVTRA05Y.cpy` TRAN-RECORD (350 bytes) | STARTBR HIGH-VALUES / READPREV / ENDBR (:644-707), WRITE (:713-721) | `transactions` PK → `ITransactionRepository.GetLastAsync` (**added by S-09**) / `AddAsync` (**added by S-09**) |
| ACCTDAT | declared `WS-ACCTDAT-FILE` (:40) | **never accessed** | — |

TRAN-RECORD population (`:450-465`), screen → record (COBOL MOVE widths):

| Record field (PIC) | Source | Note |
|---|---|---|
| TRAN-ID X(16) | `WS-TRAN-ID-N 9(16)` = last TRAN-ID + 1 (:448-451) | highest key via STARTBR HIGH-VALUES + READPREV; ENDFILE → zeros (:688-689) → first id `0000000000000001` |
| TRAN-TYPE-CD X(2) | TTYPCDI | |
| TRAN-CAT-CD 9(4) | TCATCDI | digits guaranteed by :329 |
| TRAN-SOURCE X(10) | TRNSRCI | |
| TRAN-DESC X(100) | TDESCI X(60) | space-padded |
| TRAN-AMT S9(9)V99 | `NUMVAL-C(TRNAMTI)` (:456-458) | `decimal` in target |
| TRAN-MERCHANT-ID 9(9) | MIDI | digits guaranteed by :430 |
| TRAN-MERCHANT-NAME X(50) | MNAMEI X(30) | |
| TRAN-MERCHANT-CITY X(50) | MCITYI X(25) | |
| TRAN-MERCHANT-ZIP X(10) | MZIPI | |
| TRAN-CARD-NUM X(16) | CARDNINI | as resolved by the xref step |
| TRAN-ORIG-TS X(26) | TORIGDTI X(10) | **date only**, space-padded — no time component is ever written by this program |
| TRAN-PROC-TS X(26) | TPROCDTI X(10) | same |

Copy-last (`:480-494`) record → screen truncations: TDESC ← first 60 of TRAN-DESC; TORIGDT/TPROCDT ← first 10 of the 26-byte timestamps (`yyyy-MM-dd`); MNAME ← first 30, MCITY ← first 25; amount rendered `+99999999.99` (`WS-TRAN-AMT-E`, :59).

## 5. Boundary table (headline) — S09-B1..S09-B6 (register append is the integration stage's job; ledgers untouched by this stream)

| ID | Class | Contract | Direction | Cite | Decision taken in this stream |
|---|---|---|---|---|---|
| S09-B1 | B5 cross-program switch (inbound + return) | Entered by XCTL from COMEN01C with CARDDEMO-COMMAREA; PF3 returns to `CDEMO-FROM-PROGRAM`; no-COMMAREA entry bounces to COSGN00C | both | COTRN02C.cbl:115-118, 136-143, 497-511; COMEN02Y.cpy:71 | Angular route `/transactions/add` behind `authGuard` (COBOL has no admin gate); PF3 → `/menu`; unauthenticated → `/signin` via guard. Registry row 08 keeps `Enabled=false` (integration flips it) |
| S09-B2 | B4 data-access leaf (read) | Keyed READ of CCXREF / CXACAIX; RESP 0 / NOTFND / other | outbound | :578-604, :611-637 | shared `ICardXrefRepository` as landed; NOTFND → not-found message, exception → store-error message |
| S09-B3 | B4 data-access leaf (browse + write) | STARTBR HIGH-VALUES + READPREV for the highest TRAN-ID, then WRITE; RESP NORMAL / DUPKEY,DUPREC / other | outbound | :644-749 | additive `ITransactionRepository.GetLastAsync` + `AddAsync`; Postgres unique violation (23505) → `Tran ID already exist...`; other failures → `Unable to Add Transaction...`. Id generation is read-then-insert as in source; concurrent adders collide on the PK and receive the DUPREC message (same observable as a CICS DUPREC) |
| S09-B4 | B10 shared utility | CSUTLDTC CALL contract (date, format, 80-byte result); consumed by S-10 too | both | CSUTLDTC.cbl:83-88; COTRN02C.cbl:388-428; CORPT00C.cbl:392 | ported once here as `CardDemo.Domain.Dates.DateValidationService` (SUBTRANSACTION profile: in-process class library, typed result, no magic codes). CEEDAYS feedback codes emulated per the 88-level table |
| S09-B5 | B10 shared data contract | TRANSACT written here, read by S-07/S-08 online and by batch (CBTRN*) | both | CVTRA05Y.cpy | schema owned by the shared data layer; no schema change needed (all fields present) |
| S09-B6 | B10 storage-type contract | TRAN-ORIG-TS/PROC-TS are X(26) free text; this program writes a 10-char date | outbound | :464-465 | target columns are `timestamp` (shared layer): the date is stored at midnight (`00:00:00`). Consequence: years `0000` accepted by source via the 2513 exemption cannot be represented → rejected with the source's own `... - Not a valid date...` message (recorded deviation D-2) |

All contracts resolved from source; **no unresolved-contract blockers**. No external enablement lead times.

## 6. Waves (leaf-first, from DAG depth)

| Wave | Content | Repos touched |
|---|---|---|
| 1 | CSUTLDTC port (`DateValidationService` + unit tests); additive repository capabilities `GetLastAsync`/`AddAsync` with duplicate-key mapping | backend/ |
| 2 | COTRN02C: `TransactionAddService` (state machine, exact messages, cursor), `POST /api/v1/transactions/add` (ENTER) and `POST /api/v1/transactions/add/copy-last` (PF5), Testcontainers integration tests | backend/ |
| 3 | Angular `TranAddComponent` mirroring COTRN2A (field widths, message area, ENTER/F3/F4/F5, invalid-key parity), route in `app.routes.ts` (flag stays off in registry) | frontend/ |

Single branch `devin/batch-a-s09-tran-add`; no PR opened by this stream.

## 7. Risks

1. **CEEDAYS emulation** — no source for the LE service; the accepted/rejected set is reconstructed from the CSUTLDTC 88-level feedback table and documented CEEDAYS semantics (Lillian range 1582-10-15..9999-12-31). Only pre-1582 dates are affected by any residual ambiguity, and the source *accepts* those (message 2513 exempted at `:400,:420`). MEDIUM (documented as A-1).
2. **Id generation under concurrency** — CICS serialises the STARTBR/WRITE within a region; Postgres does not. Collisions surface as the source's DUPREC message; the user re-presses ENTER. LOW.
3. **Timestamp storage** (S09-B6) — date-only values stored as midnight; downstream S-07/S-08 render `yyyy-MM-dd` prefixes identically. Year 0000 unrepresentable (D-2). LOW.
4. **Padded-field numeric semantics** — web users must type the full width (11-digit account, 16-digit card, 2/4/9-digit codes) exactly as on the 3270; the UI carries the BMS `maxlength`s and hints so the message parity is exact. LOW.
5. `Tran ID already exist...` positions the cursor on ACTIDIN (`:740`) — kept as-is.

## 8. Validation
(1) all programs entry→hard stop inventoried (2/2, none absent; CEEDAYS is an LE service, contract decoded from CSUTLDTC); (2) wave order is a topological sort of the DAG (leaf CSUTLDTC first); (3) every claim cited `<file>:<line>`; (4) surfaces are ONLINE (one screen, AID set, edits) plus the SUBTRANSACTION utility; (5) all mechanical crossings are in the boundary table with full contracts; (6) data-access leaves S09-B2/B3 resolve to the shared Postgres data layer, additive extension only (`GetLastAsync`, `AddAsync`, no schema change, no migration).
