# S-08 Transaction View — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Stream S-08 from the catalog (`CardDemo_inventory.md` §5, row S-08); process type **ONLINE**.
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md` (C#/.NET 8 + ASP.NET Core, Angular 18, PostgreSQL 16, single repo). S-01 conventions reused as-is: JWT `SessionContext`, `authGuard`, menu route registry, invalid-key parity helper, Controller/Service/Repository layering.

## 1. Pinned stream

- **Entry point (proof)**: `CT01 -> COTRN01C` (`app/csd/CARDDEMO.CSD:429-430`); program defined `:264`. Reached from main-menu option 07 "Transaction View" (`app/cpy/COMEN02Y.cpy`, registry row `ProgramKey=COTRN01C` in `backend/CardDemo.Api/appsettings.json`) and from the transaction list COTRN00C when a row is selected with `S` (`app/cbl/COTRN00C.cbl:186-195`).
- **Hard stop**: every `XCTL` out of COTRN01C is OUT of scope — COSGN00C/COMEN01C (S-01, already migrated: sign-on/menu routes), COTRN00C (S-07, not migrated: stays behind the disabled route registry), and the generic `CDEMO-FROM-PROGRAM` return.
- **Exclusions**: COTRN00C (list/browse, S-07), COTRN02C (add, S-09). No batch surface.
- Pseudo-conversational shape: `EXEC CICS RETURN TRANSID('CT01') COMMAREA(...)` (`app/cbl/COTRN01C.cbl:136-139`); re-entry detected via `CDEMO-PGM-REENTER` (`:99-100`, `app/cpy/COCOM01Y.cpy:29-31`).

## 2. Program inventory + leaf-first DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COTRN01C | app/cbl/COTRN01C.cbl | entry/validate/display (single-record view) | CICS READ TRANSACT (`:269-278`); XCTL COSGN00C on cold start (`:94-96`); XCTL COMEN01C or `CDEMO-FROM-PROGRAM` on PF3 (`:115-122`); XCTL COTRN00C on PF5 (`:125-127`) | no (stream-owned) | yes |

No subroutine `CALL`s, no DB2/IMS/MQ, no dates via CSUTLDTC. Single program, single wave; DAG is trivial (COTRN01C → data leaf TRANSACT).

Copybooks consumed: `COCOM01Y` (COMMAREA, `:52`) extended in-line with `CDEMO-CT01-INFO` (`:53-61`, TRNID-FIRST/LAST, PAGE-NUM, NEXT-PAGE-FLG, TRN-SEL-FLG, TRN-SELECTED); `COTRN01` (symbolic map, `app/cpy-bms/COTRN01.CPY`); `COTTL01Y`/`CSDAT01Y`/`CSMSG01Y` (titles, date/time, common messages); `CVTRA05Y` (TRAN-RECORD, `:69`); `DFHAID`/`DFHBMSCA`.

## 3. Surfaces (ONLINE)

### COTRN01C — screen COTRN1A / mapset COTRN01 (`app/bms/COTRN01.bms`, fields `app/cpy-bms/COTRN01.CPY`)

Screen title `View Transaction` (bms:75-79). Footer legend `ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.` (bms:263-268).

| Field | Label (verbatim, bms) | I/O | PIC (map) | Source field / edits (cite) |
|---|---|---|---|---|
| TRNIDIN | `Enter Tran ID:` (bms:80-84) | INPUT | X(16), IC cursor, green underline (bms:85-90) | mandatory — `Tran ID can NOT be empty...` (`COTRN01C.cbl:147-152`); otherwise used verbatim as the TRANSACT key (`:172`) — **no upper-casing, no numeric edit, no length edit** |
| TRNID | `Transaction ID:` (bms:100-104) | DISPLAY | X(16) | TRAN-ID X(16) (`:178`) |
| CARDNUM | `Card Number:` (bms:113-117) | DISPLAY | X(16) | TRAN-CARD-NUM X(16) (`:179`) |
| TTYPCD | `Type CD:` (bms:127-131) | DISPLAY | X(2) | TRAN-TYPE-CD X(02) (`:180`) |
| TCATCD | `Category CD:` (bms:139-143) | DISPLAY | X(4) | TRAN-CAT-CD 9(04) (`:181`) — zero-padded digits |
| TRNSRC | `Source:` (bms:151-155) | DISPLAY | X(10) | TRAN-SOURCE X(10) (`:182`) |
| TDESC | `Description:` (bms:163-167) | DISPLAY | X(60) | TRAN-DESC X(100) → **truncated to first 60** (`:184`) |
| TRNAMT | `Amount:` (bms:175-179) | DISPLAY | X(12) | TRAN-AMT S9(09)V99 through edit picture `+99999999.99` (`:49`, `:177`, `:183`): fixed leading sign (`+`/`-`), 8 zero-padded integer digits, `.`, 2 decimals; high-order 9th integer digit truncated by the MOVE |
| TORIGDT | `Orig Date:` (bms:187-191) | DISPLAY | X(10) | TRAN-ORIG-TS X(26) → **first 10 chars** = `yyyy-MM-dd` (`:185`) |
| TPROCDT | `Proc Date:` (bms:199-203) | DISPLAY | X(10) | TRAN-PROC-TS X(26) → first 10 chars (`:186`); blank timestamp → blank |
| MID | `Merchant ID:` (bms:211-215) | DISPLAY | X(9) | TRAN-MERCHANT-ID 9(09) (`:187`) |
| MNAME | `Merchant Name:` (bms:223-227) | DISPLAY | X(30) | TRAN-MERCHANT-NAME X(50) → **first 30** (`:188`) |
| MCITY | `Merchant City:` (bms:235-239) | DISPLAY | X(25) | TRAN-MERCHANT-CITY X(50) → **first 25** (`:189`) |
| MZIP | `Merchant Zip:` (bms:247-251) | DISPLAY | X(10) | TRAN-MERCHANT-ZIP X(10) (`:190`) |
| ERRMSG | — | DISPLAY | X(78) red bright (bms:259-262) | WS-MESSAGE X(80) (`:217`) |
| TRNNAME/PGMNAME/TITLE01/02/CURDATE/CURTIME | header (bms:29-74) | DISPLAY | — | `POPULATE-HEADER-INFO` (`:243-262`): `CT01`, `COTRN01C`, titles, `mm/dd/yy`, `hh:mm:ss` |

AID keys (`:112-132`): ENTER = fetch (`PROCESS-ENTER-KEY`); PF3 = back to `CDEMO-FROM-PROGRAM`, defaulting to COMEN01C when blank (`:115-122`); PF4 = clear all fields + message, cursor to TRNIDIN (`:123-124`, `:301-326`); PF5 = transfer to COTRN00C transaction list (`:125-127`); any other AID = `CCDA-MSG-INVALID-KEY` (`:128-131`, `app/cpy/CSMSG01Y.cpy:20-21`).

Entry behaviors (`:94-109`): cold start (`EIBCALEN = 0`) → XCTL COSGN00C (`:94-96`); first entry with COMMAREA → blank screen, cursor on TRNIDIN, **and if `CDEMO-CT01-TRN-SELECTED` is populated (set by COTRN00C, `COTRN00C.cbl:186-195` via `CDEMO-CT00-TRN-SELECTED`, same COMMAREA offset) the id is pre-filled and fetched immediately** (`:103-108`).

Fetch sequence (`PROCESS-ENTER-KEY`, `:144-192`): (1) empty check → message; (2) clear every detail field (`:159-171`) so a failed lookup shows an empty detail area; (3) keyed READ (`READ-TRANSACT-FILE`, `:267-296`): RESP NORMAL → populate; RESP NOTFND → `Transaction ID NOT found...` (`:283-288`); other RESP → `Unable to lookup Transaction...` (`:289-295`, RESP/RESP2 written to the log via `DISPLAY :290`). Cursor is always placed on TRNIDIN (`:151`, `:154`, `:287`, `:294`). **Return-code protocol: 0 / 13 / other.** The READ carries `UPDATE` (`:275`) but no REWRITE/UNLOCK ever follows — a record lock released at task end; demoted mechanics (read-only in the target).

## 4. Data + field dictionary

**Dataset**: TRANSACT VSAM KSDS, `KEYS(16 0)` = TRAN-ID, AIX on TRAN-PROC-TS (see `backend/CardDemo.Domain/Transactions/Transaction.cs` header). Read-only in this stream; writers are S-09 (COTRN02C) and the batch posting chain (S-14). Shared data layer already landed (`transactions` table, `ITransactionRepository.GetByIdAsync`, seed import from `app/data/ASCII/dailytran.txt`) — S-08 adds **no** schema change.

Field dictionary (FACT, `app/cpy/CVTRA05Y.cpy:4-17`; target mapping from `TransactionConfiguration.cs`):
| COBOL field | PIC | C# (Domain.Transaction) | PostgreSQL column |
|---|---|---|---|
| TRAN-ID | X(16) | `TransactionId` string | transactions.tran_id varchar(16) PK, collation C |
| TRAN-TYPE-CD | X(02) | `TypeCode` | tran_type_cd varchar(2) |
| TRAN-CAT-CD | 9(04) | `CategoryCode` string (zero-padded digits) | tran_cat_cd varchar(4) |
| TRAN-SOURCE | X(10) | `Source` | tran_source varchar(10) |
| TRAN-DESC | X(100) | `Description` | tran_desc varchar(100) |
| TRAN-AMT | S9(09)V99 | `Amount` decimal | tran_amt numeric(11,2) |
| TRAN-MERCHANT-ID | 9(09) | `MerchantId` string | tran_merchant_id varchar(9) |
| TRAN-MERCHANT-NAME | X(50) | `MerchantName` | tran_merchant_name varchar(50) |
| TRAN-MERCHANT-CITY | X(50) | `MerchantCity` | tran_merchant_city varchar(50) |
| TRAN-MERCHANT-ZIP | X(10) | `MerchantZip` | tran_merchant_zip varchar(10) |
| TRAN-CARD-NUM | X(16) | `CardNumber` | tran_card_num varchar(16) |
| TRAN-ORIG-TS | X(26) | `OriginalTimestamp` DateTime? | tran_orig_ts timestamp |
| TRAN-PROC-TS | X(26) | `ProcessedTimestamp` DateTime? | tran_proc_ts timestamp |
| FILLER | X(20) | — | not mapped |

Key semantics: the screen field is X(16) space-padded; VSAM compares the 16 bytes verbatim. Stored keys are `TrimEnd()`ed on import (`FixedWidthRecord.Text`), so the target compares the entered id with trailing spaces removed — identical outcome for every 16-byte key (leading spaces / case are **not** normalized, matching the source).

Session/COMMAREA: general info via S-01 `SessionContext`/JWT (S01-B6, no change). `CDEMO-CT01-TRN-SELECTED` (inbound pre-selection) becomes the route query parameter `tranId`; `CDEMO-FROM-PROGRAM` (PF3 return target) becomes the optional `returnUrl` query parameter defaulting to `/menu` (S01-B3 idiom). The list-paging members of `CDEMO-CT01-INFO` (TRNID-FIRST/LAST, PAGE-NUM, NEXT-PAGE-FLG) are declared but never referenced by COTRN01C — they belong to COTRN00C and are not carried.

## 5. Boundary table (headline) — S08-B1..S08-B4 (register append is owned by the integration stage; not edited here)

| ID | Class | Contract | Direction | Cite | Decision taken in this stream |
|---|---|---|---|---|---|
| S08-B1 | B4 data-access leaf | CICS READ TRANSACT keyed by TRAN-ID (UPDATE, never rewritten); RESP 0/13/other | outbound | COTRN01C.cbl:269-296 | Shared `ITransactionRepository.GetByIdAsync` (EF Core, read-only, no lock); repository exception → "other RESP" result |
| S08-B2 | B5 inbound pre-selection | COTRN00C XCTLs with `CDEMO-CT01-TRN-SELECTED` populated → auto-fetch | inbound | COTRN01C.cbl:103-108; COTRN00C.cbl:186-195 | Route contract `/transactions/view?tranId=<id>`: pre-fills and fetches on init; S-07 consumes when it migrates |
| S08-B3 | B5 outbound return | PF3 → `CDEMO-FROM-PROGRAM` (default COMEN01C) | outbound | COTRN01C.cbl:115-122 | `returnUrl` query param (internal path only), default `/menu` (S01-B3) |
| S08-B4 | B5 cross-program switch | PF5 → XCTL COTRN00C (S-07, not migrated) | outbound | COTRN01C.cbl:125-127 | Resolved through the S-01 route registry: main-menu option `06` (Transaction List = COTRN00C, `COMEN02Y.cpy`); registry disabled → coming-soon message on this screen, no navigation (integration stage flips the flag) |

No stored procedures, no external systems, no lead-time requests. All contracts resolved from source; **no unresolved-contract blockers**.

## 6. Waves (leaf-first)

| Wave | Content | Repos touched |
|---|---|---|
| 1 (only) | `TransactionViewService` + `GET /api/v1/transactions/view/{tranId}` + Angular `TransactionViewComponent` at `/transactions/view` (authGuard), specs/tests per FR | backend/, frontend/ |

Shared-port note: no new module-level seams; consumes S-01 session, guard, invalid-key and registry seams unchanged.

## 7. Risks

1. Amount edit picture `+99999999.99` drops the 9th integer digit of S9(09)V99 amounts (`:49`, `:177`) — reproduced exactly; visible only for |amount| ≥ 100,000,000.00 (none in seed data). LOW.
2. PF5 target (COTRN00C) not migrated: behavior is the registry's coming-soon message until S-07 lands. LOW (by design, hard-scope rule).
3. Display truncation of description/merchant name/city (60/30/25) is a screen-real-estate artifact of the 3270 map; reproduced for parity, flagged for the UX pass at STOP D. LOW.

## 8. Validation
(1) single program inventoried entry→hard stop (1/1, none absent); (2) trivial DAG, single wave; (3) claims cited `<file>:<line>`; (4) surfaces are ONLINE only (one screen, 4 AIDs); (5) all four crossings in the boundary table with decisions; (6) sole data-access leaf S08-B1 resolved onto the shared Postgres layer (no schema delta).
