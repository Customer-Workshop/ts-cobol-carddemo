# S-08 Transaction View — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Stream S-08 from the catalog (`CardDemo_inventory.md` §5, row S-08); process type **ONLINE**.
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md` (Java 21 / Spring Boot 3.4.5, Thymeleaf server-rendered UI, PostgreSQL 16 + Spring Data JPA, single `spring-boot/` module). S-01 conventions reused as-is: server session + `SecurityContext`, unsigned bounce to `/signon`, `MenuService` catalogue + `UI_ROUTES`, `aid` form-field idiom, Controller/Service/Repository layering.

> Java-engagement note (2026-09-16): source-side analysis unchanged; target references below
> were re-expressed for Java 21/Spring Boot (EF Core → Spring Data JPA, Angular → server-rendered
> web UI, /api/v1 → /api, JWT → server session) at this engagement's STOP C.

## 1. Pinned stream

- **Entry point (proof)**: `CT01 -> COTRN01C` (`app/csd/CARDDEMO.CSD:429-430`); program defined `:264`. Reached from main-menu option 07 "Transaction View" (`app/cpy/COMEN02Y.cpy`, `MenuService` catalogue row COTRN01C) and from the transaction list COTRN00C when a row is selected with `S` (`app/cbl/COTRN00C.cbl:186-195`).
- **Hard stop**: every `XCTL` out of COTRN01C is OUT of scope — COSGN00C/COMEN01C (S-01, already migrated: `/signon` and `/menu` routes), COTRN00C (S-07, not migrated: stays behind a non-browsable `UI_ROUTES` entry until it lands), and the generic `CDEMO-FROM-PROGRAM` return.
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

**Dataset**: TRANSACT VSAM KSDS, `KEYS(16 0)` = TRAN-ID, AIX on TRAN-PROC-TS (see `com.carddemo.model.Transaction`). Read-only in this stream; writers are S-09 (COTRN02C) and the batch posting chain (S-14). Shared data layer already landed (`transactions` table via Flyway V2, `TransactionRepository.findById`, seed import from `app/data/ASCII/dailytran.txt` via `DataSeeder`) — S-08 adds **no** schema change.

Field dictionary (FACT, `app/cpy/CVTRA05Y.cpy:4-17`; target mapping from the Flyway V2 `transactions` table + `Transaction` entity):
| COBOL field | PIC | Java (`Transaction`) | PostgreSQL column |
|---|---|---|---|
| TRAN-ID | X(16) | `tranId` String | transactions.tran_id varchar(16) PK |
| TRAN-TYPE-CD | X(02) | `tranTypeCode` | tran_type_code varchar(2) |
| TRAN-CAT-CD | 9(04) | `tranCategoryCode` Integer (zero-padded to 4 on display) | tran_category_code integer |
| TRAN-SOURCE | X(10) | `tranSource` | tran_source varchar(10) |
| TRAN-DESC | X(100) | `tranDescription` | tran_description varchar(100) |
| TRAN-AMT | S9(09)V99 | `tranAmount` BigDecimal | tran_amount numeric(19,2) |
| TRAN-MERCHANT-ID | 9(09) | `tranMerchantId` Long (zero-padded to 9 on display) | tran_merchant_id bigint |
| TRAN-MERCHANT-NAME | X(50) | `tranMerchantName` | tran_merchant_name varchar(50) |
| TRAN-MERCHANT-CITY | X(50) | `tranMerchantCity` | tran_merchant_city varchar(50) |
| TRAN-MERCHANT-ZIP | X(10) | `tranMerchantZip` | tran_merchant_zip varchar(10) |
| TRAN-CARD-NUM | X(16) | `tranCardNumber` | tran_card_number varchar(16) |
| TRAN-ORIG-TS | X(26) | `tranOriginTimestamp` LocalDateTime | tran_origin_timestamp timestamp(6) |
| TRAN-PROC-TS | X(26) | `tranProcessTimestamp` LocalDateTime | tran_process_timestamp timestamp(6) |
| FILLER | X(20) | — | not mapped |

Key semantics: the screen field is X(16) space-padded; VSAM compares the 16 bytes verbatim. Stored keys are trailing-space-stripped on import (`CobolFieldReader`), so the target compares the entered id with trailing spaces removed — identical outcome for every 16-byte key (leading spaces / case are **not** normalized, matching the source).

Session/COMMAREA: general info via S-01 server session + `SecurityContext` (S01-B6, no change). `CDEMO-CT01-TRN-SELECTED` (inbound pre-selection) becomes the route query parameter `tranId`; `CDEMO-FROM-PROGRAM` (PF3 return target) becomes the optional `returnUrl` query parameter defaulting to `/menu` (S01-B3 idiom). The list-paging members of `CDEMO-CT01-INFO` (TRNID-FIRST/LAST, PAGE-NUM, NEXT-PAGE-FLG) are declared but never referenced by COTRN01C — they belong to COTRN00C and are not carried.

## 5. Boundary table (headline) — S08-B1..S08-B4 (register append is owned by the integration stage; not edited here)

| ID | Class | Contract | Direction | Cite | Decision taken in this stream |
|---|---|---|---|---|---|
| S08-B1 | B4 data-access leaf | CICS READ TRANSACT keyed by TRAN-ID (UPDATE, never rewritten); RESP 0/13/other | outbound | COTRN01C.cbl:269-296 | Shared `TransactionRepository.findById` (Spring Data JPA, read-only, no lock) — **verbatim key**, not the baseline's numeric+zero-pad edit; repository exception → "other RESP" result |
| S08-B2 | B5 inbound pre-selection | COTRN00C XCTLs with `CDEMO-CT01-TRN-SELECTED` populated → auto-fetch | inbound | COTRN01C.cbl:103-108; COTRN00C.cbl:186-195 | Route contract `/transactions/view?tranId=<id>`: pre-fills and fetches on render; S-07 consumes it when it migrates |
| S08-B3 | B5 outbound return | PF3 → `CDEMO-FROM-PROGRAM` (default COMEN01C) | outbound | COTRN01C.cbl:115-122 | `returnUrl` query param (internal path only), default `redirect:/menu` (S01-B3) |
| S08-B4 | B5 cross-program switch | PF5 → XCTL COTRN00C (S-07, not migrated) | outbound | COTRN01C.cbl:125-127 | Resolved through the `MenuService` catalogue / `UI_ROUTES`: main-menu option `06` (Transaction List = COTRN00C, `COMEN02Y.cpy`); not browsable → coming-soon message on this screen, no navigation (S-07's wave flips the entry to `/transactions/list`) |

No stored procedures, no external systems, no lead-time requests. All contracts resolved from source; **no unresolved-contract blockers**.

## 6. Waves (leaf-first)

| Wave | Content | Repos touched |
|---|---|---|
| 1 (only) | `transaction-view.html` + `UiController` `/transactions/view` GET/POST (unsigned bounce to `/signon`), verbatim-key lookup + verbatim messages, unit + MockMvc + Testcontainers tests per FR | spring-boot/ |

`UI_ROUTES` entry for option 07 (COTRN01C) flips to `/transactions/view` when the wave merges. Shared-port note: no new module-level seams; consumes S-01 session, entry-point, `aid` and registry seams unchanged.

## 7. Risks

1. Amount edit picture `+99999999.99` drops the 9th integer digit of S9(09)V99 amounts (`:49`, `:177`) — reproduced exactly; visible only for |amount| ≥ 100,000,000.00 (none in seed data). LOW.
2. PF5 target (COTRN00C) not migrated: behavior is the coming-soon idiom until S-07 lands and `UI_ROUTES` gets its `/transactions/list` entry. LOW (by design, hard-scope rule).
3. Display truncation of description/merchant name/city (60/30/25) is a screen-real-estate artifact of the 3270 map; reproduced for parity, flagged for the UX pass at STOP D. LOW.

## 8. Validation
(1) single program inventoried entry→hard stop (1/1, none absent); (2) trivial DAG, single wave; (3) claims cited `<file>:<line>`; (4) surfaces are ONLINE only (one screen, 4 AIDs); (5) all four crossings in the boundary table with decisions; (6) sole data-access leaf S08-B1 resolved onto the shared Postgres layer (no schema delta).
