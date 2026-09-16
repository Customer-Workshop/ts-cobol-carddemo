# S-07 Transaction List — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Stream from catalogue row S-07 (`CardDemo_inventory.md` §5); process type **ONLINE**.
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md` (C# 12 / .NET 8 + ASP.NET Core 8, Angular 18 + Material, PostgreSQL 16 + EF Core 8, single repo). S-01 shell conventions reused (JWT `SessionContext`, `authGuard`, menu route registry, AID parity helper).

## 1. Pinned stream

- **Entry point (proof)**: `CT00 -> COTRN00C` (`app/csd/CARDDEMO.CSD:419-420`); mapset `COTRN00` (`:145`), program (`:257`). Reached from main menu option 06 (`app/cpy/COMEN02Y.cpy:58`, registry row `Id 06 / COTRN00C`, flag disabled until integration).
- **Hard stop**: `XCTL COTRN01C` on row selection (`COTRN00C.cbl:186-195`) is stream S-08's entry — OUT of scope. `XCTL COMEN01C` on PF3 (`:122-124`) and `XCTL COSGN00C` on entry without COMMAREA (`:107-109`) are S-01 shell routes, consumed as-is.
- **Exclusions**: COTRN01C (Transaction View), COTRN02C (Transaction Add), all other menu targets.
- Pseudo-conversational shape: `EXEC CICS RETURN TRANSID('CT00') COMMAREA(CARDDEMO-COMMAREA)` (`:138-141`); state extension `CDEMO-CT00-INFO` appended to the common COMMAREA (`:62-70`).

## 2. Program inventory + leaf-first DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COTRN00C | app/cbl/COTRN00C.cbl | entry/list (browse TRANSACT, page ±10) | CICS STARTBR/READNEXT/READPREV/ENDBR TRANSACT (`:593-696`); XCTL COTRN01C (`:192-195`); XCTL COMEN01C / COSGN00C via RETURN-TO-PREV-SCREEN (`:510-521`) | no (single program stream) | yes |

No subroutine CALLs; single node DAG (COTRN00C → {TRANSACT read-only, COTRN01C (S-08), COMEN01C (S-01)}). One wave.

Copybooks: `COCOM01Y.cpy` (COMMAREA), `COTRN00.CPY` (map I/O, `app/cpy-bms/COTRN00.CPY`), `COTTL01Y.cpy` (titles), `CSDAT01Y.cpy` (date/time/timestamp shapes), `CSMSG01Y.cpy` (invalid-key/farewell), `CVTRA05Y.cpy` (TRAN-RECORD), `DFHAID`, `DFHBMSCA`.

## 3. Surfaces (ONLINE)

### COTRN00C — screen COTRN0A / mapset COTRN00 (`app/bms/COTRN00.bms`, fields `app/cpy-bms/COTRN00.CPY`)
24x80 map, `CTRL=(ALARM,FREEKB)`. Layout (rows):

| Row | Field | ATTRB | Len | Content / source |
|---|---|---|---|---|
| 1 | `Tran:` TRNNAME | ASKIP,FSET | 4 | `CT00` (`COTRN00C.cbl:573`) |
| 1 | TITLE01 | ASKIP,FSET | 40 | CCDA-TITLE01 (`:571`) |
| 1 | `Date:` CURDATE | ASKIP,FSET | 8 | `mm/dd/yy` current date (`:576-580`) |
| 2 | `Prog:` PGMNAME | ASKIP,FSET | 8 | `COTRN00C` (`:574`) |
| 2 | TITLE02 | ASKIP,FSET | 40 | CCDA-TITLE02 (`:572`) |
| 2 | `Time:` CURTIME | ASKIP,FSET | 8 | `hh:mm:ss` (`:582-586`) |
| 4 | literal | ASKIP,BRT | 17 | `List Transactions` (bms:75-79) |
| 4 | `Page:` PAGENUM | ASKIP,FSET | 8 | CDEMO-CT00-PAGE-NUM 9(08) → `00000001` (`:324`, `:373`) |
| 6 | `Search Tran ID:` TRNIDIN | **UNPROT**,FSET | 16 | search key; cursor home (`:105`, `-1` to TRNIDINL) |
| 8-9 | column headings | ASKIP | — | `Sel`, ` Transaction ID `, `  Date  `, `     Description          `, `   Amount   ` + dashes (bms:103-152) |
| 10-19 | SEL0001..SEL0010 | **UNPROT**,FSET, underline | 1 | selection character per row (`:149-178`) |
| 10-19 | TRNID01..10 | ASKIP,FSET | 16 | TRAN-ID (`:392`, …) |
| 10-19 | TDATE01..10 | ASKIP,FSET | 8 | `mm/dd/yy` from TRAN-ORIG-TS (`:384-388`) |
| 10-19 | TDESC01..10 | ASKIP,FSET | 26 | TRAN-DESC X(100) truncated to 26 (`:395`) |
| 10-19 | TAMT001..010 | ASKIP,FSET | 12 | TRAN-AMT edited `+99999999.99` (`:56`, `:383`) |
| 21 | literal | ASKIP,BRT | 50 | `Type 'S' to View Transaction details from the list` (bms:444-448) |
| 23 | ERRMSG | ASKIP,BRT,FSET, RED | 78 | WS-MESSAGE (`:531`) |
| 24 | literal | ASKIP, YELLOW | 48 | `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` (bms:454-458) |

Because every data field is `FSET`, the RECEIVE returns the previously displayed rows, page number and selection characters; a redisplay that does not repopulate rows therefore leaves them on screen (input and output structures overlay each other).

**AID dispatch** (`:119-134`): ENTER → PROCESS-ENTER-KEY; PF3 → COMEN01C; PF7 → PROCESS-PF7-KEY; PF8 → PROCESS-PF8-KEY; other → `CCDA-MSG-INVALID-KEY` (`Invalid key pressed. Please see below...`, `CSMSG01Y.cpy`) with cursor on TRNIDIN.

**ENTER flow** (`:146-229`), in order:
1. Selection scan — first `SEL000nI` not spaces/low-values wins; its flag + that row's `TRNID0nI` go to `CDEMO-CT00-TRN-SEL-FLG/-SELECTED` (`:148-182`).
2. If flag and selected id both non-blank: `'S'`/`'s'` → XCTL COTRN01C with FROM-TRANID/PROGRAM and PGM-CONTEXT 0 (`:186-195`, **hard stop**); anything else → `Invalid selection. Valid value is S` and processing **continues** (`:196-203`).
3. Search key: blank → `LOW-VALUES` (start of file); `IS NUMERIC` on the full 16-char field → key; else `Tran ID must be Numeric ...`, ERR-FLG on, screen sent (`:206-219`). BMS pads a shorter entry with spaces, so only a 16-digit entry passes the NUMERIC test.
4. Page number reset to 0; PROCESS-PAGE-FORWARD (`:224-225`). With ERR-FLG on, the browse is skipped after STARTBR (`:283`), so the numeric error is final and the search input is **not** cleared (`:227-229`).

**Forward page** (`:279-328`): STARTBR (GTEQ default) at TRAN-ID; NOTFND → `You are at the top of the page...` (`:605-611`), EOF set, no rows touched. When entered via PF8 the record at the start key (last displayed row) is skipped by one READNEXT (`:285-287`). Rows 1-10 cleared (`:289-293`), then filled by READNEXT until 10 or ENDFILE (`:297-303`, ENDFILE → `You have reached the bottom of the page...` `:639-645`). Full page → page number +1 and a peek READNEXT decides `NEXT-PAGE-YES/NO` (peek ENDFILE also raises the bottom message) (`:305-313`). Partial page → `NEXT-PAGE-NO`, page +1 only if ≥1 row (`:314-320`). PAGENUM refreshed and TRNIDIN cleared (`:324-325`). `CDEMO-CT00-TRNID-FIRST` = row-1 id, `-TRNID-LAST` = row-10 id (only when row 10 is filled) (`:392-393`, `:438-439`).

**PF7** (`:234-252`): key = TRNID-FIRST (or LOW-VALUES); `NEXT-PAGE-YES` forced; page > 1 → PROCESS-PAGE-BACKWARD, else `You are already at the top of the page...` sent **without ERASE**.
**Backward page** (`:333-376`): STARTBR at TRNID-FIRST; one READPREV consumes the current first row (`:339-341`); rows cleared; READPREV fills rows 10→1 (`:349-357`, ENDFILE → `You have reached the top of the page...` `:673-679`). If 10 rows were read a peek READPREV runs: record exists and page > 1 → page −1, otherwise page = 1 (peek ENDFILE also raises the top message) (`:359-369`). Search input is not cleared on this path.
**PF8** (`:257-274`): key = TRNID-LAST (or HIGH-VALUES); `NEXT-PAGE-YES` → PROCESS-PAGE-FORWARD, else `You are already at the bottom of the page...` sent without ERASE.

**Any file error** (RESP other than NORMAL/NOTFND/ENDFILE): `Unable to lookup transaction...`, ERR-FLG on, screen sent (`:612-618`, `:646-652`, `:680-686`).

## 4. Data + field dictionary

TRANSACT KSDS (`WS-TRANSACT-FILE 'TRANSACT'` `:39`), key TRAN-ID X(16) at offset 0, browse-only (no WRITE/REWRITE/DELETE). Record `TRAN-RECORD` (`app/cpy/CVTRA05Y.cpy`, RECLN 350) — fields used by this program:

| Field | PIC | Target type | Postgres column (shared layer 468e17d) | Screen edit |
|---|---|---|---|---|
| TRAN-ID | X(16) | string | transactions.tran_id (PK, collation "C") | shown as-is; also search key / paging cursors |
| TRAN-DESC | X(100) | string | transactions.tran_desc | first 26 chars |
| TRAN-AMT | S9(09)V99 | decimal | transactions.tran_amt | `+99999999.99` (sign always shown; high-order digit dropped for |amt| ≥ 10^8 — COBOL MOVE truncation) |
| TRAN-ORIG-TS | X(26) `yyyy-MM-dd HH:mm:ss.ffffff` | DateTime? | transactions.tran_orig_ts | `mm/dd/yy` (`WS-TIMESTAMP` → `WS-CURDATE-MM-DD-YY`, `CSDAT01Y.cpy`) |

Key ordering: VSAM KSDS byte order = Postgres collation "C" on `tran_id` (`LegacyColumnConventions.KeyCollation`), so `ORDER BY tran_id` reproduces STARTBR/READNEXT/READPREV sequence.

Session/paging state (`CDEMO-CT00-INFO` `:62-70`): TRNID-FIRST X(16), TRNID-LAST X(16), PAGE-NUM 9(08), NEXT-PAGE-FLG X(1) 'Y'/'N', TRN-SEL-FLG X(1), TRN-SELECTED X(16). Target: client-held `TransactionListState` round-tripped in each request/response (pseudo-conversational state, no server session).

## 5. Boundary table (headline) — S07-B1..S07-B5

| ID | Class | Contract | Direction | Cite | Decision |
|---|---|---|---|---|---|
| S07-B1 | B5 cross-program switch | XCTL COTRN01C with COMMAREA (selected TRAN-ID) | outbound → S-08 | COTRN00C.cbl:186-195 | Resolve via menu route registry entry `ProgramKey=COTRN01C` (Id 07, disabled): "coming soon" idiom (FR-S01-13/14) until S-08 lands; selected id carried in the result for the future route |
| S07-B2 | B5 inbound return routing | XCTL COMEN01C on PF3 | outbound → S-01 shell | :122-124, :510-521 | Angular `router.navigateByUrl('/menu')` (S01-B3 route contract) |
| S07-B3 | B4 data-access leaf | STARTBR/READNEXT/READPREV/ENDBR TRANSACT, RESP protocol NORMAL/NOTFND/ENDFILE/other | outbound | :593-696 | Shared `ITransactionRepository.BrowseAsync/BrowseBackwardAsync` (landed 468e17d); no schema extension needed |
| S07-B4 | B5 entry guard | EIBCALEN = 0 → XCTL COSGN00C | outbound → S-01 | :107-109 | `authGuard` redirect to `/signin` (existing) |
| S07-B5 | B10 shared data contract | CDEMO-CT00-INFO paging state appended to CARDDEMO-COMMAREA | both | :62-70 | Stream-local `TransactionListState` DTO (first/last id, page number, next-page flag) |

All contracts resolved from source; no unresolved-contract blockers; no external lead time.

## 6. Waves (leaf-first)

| Wave | Content | Repos touched |
|---|---|---|
| 1 | COTRN00C: `TransactionListService` + `POST /api/v1/transactions/list`, Angular `TransactionListComponent` at `/transactions/list` (authGuard), unit + Testcontainers tests, specs | backend/, frontend/ |

Menu registry flag for `06 / COTRN00C` stays disabled (integration stage flips it).

## 7. Risks

1. Source quirks preserved on purpose (documented in FR §7): STARTBR NOTFND uses the "top of the page" wording; PF7-at-top forces `NEXT-PAGE-YES`; PF8 after that from a partial page yields the NOTFND message; selection characters persist on screen. LOW (parity by design).
2. `IS NUMERIC` over the full 16-char field means a shorter search entry is rejected; UI enforces `maxlength=16` and passes the raw value. LOW.
3. Concurrent deletion of the first/last displayed record changes STARTBR positioning; target mirrors GTEQ semantics via `>=`/`<` key comparisons. LOW.
4. COTRN01C hand-off is a disabled route until S-08 lands; "coming soon" message is the S-01 idiom, not a COTRN00C message. LOW (approved seam pattern).

## 8. Validation
Single program, single wave; every screen field, message, PF key and file RESP path enumerated with cites; boundaries S07-B1..B5 decided from existing seams; no source edits; no `.migration/` edits.
