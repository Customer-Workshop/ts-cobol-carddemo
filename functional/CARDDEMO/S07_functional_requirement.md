# S-07 Transaction List — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Derived from `S07_tran_list_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; cites are raw line numbers of `app/cbl/COTRN00C.cbl` unless prefixed.

## 1. Purpose and scope
Let a signed-on user browse the transaction file ten records at a time in transaction-id order, optionally starting at a typed transaction id, page forward/backward, and pick one row for detail viewing. Process type ONLINE. Trigger: CICS transaction CT00 (`app/csd/CARDDEMO.CSD:419-420`), main-menu option 06. Hard stop: `XCTL COTRN01C` (Transaction View, S-08). Exclusions: COTRN01C, COTRN02C.

## 2. Actors and preconditions
- Actor: any signed-on CardDemo user ('A' or 'U'); the program has no user-type gate (no reference to `CDEMO-USER-TYPE`).
- Precondition: a valid CARDDEMO-COMMAREA (session) — entry with `EIBCALEN = 0` bounces to sign-on (`:107-109`). TRANSACT KSDS open (`'TRANSACT'` `:39`), keyed TRAN-ID X(16).

## 3. Surface specification
### Screen COTRN0A (`app/bms/COTRN00.bms`, fields `app/cpy-bms/COTRN00.CPY`)
| Field | Label (verbatim) | I/O | Len/PIC | Edits |
|---|---|---|---|---|
| TRNIDIN | `Search Tran ID:` (bms:94) | INPUT | X(16), cursor home (`:105`) | blank → start of file; else must be NUMERIC over all 16 chars (`:206-219`) |
| SEL0001..0010 | `Sel` column (bms:107) | INPUT | X(1) ×10 | first non-blank wins; `S`/`s` selects, anything else `Invalid selection. Valid value is S` (`:148-203`) |
| TRNID01..10 | ` Transaction ID ` | OUTPUT | X(16) ×10 | TRAN-ID |
| TDATE01..10 | `  Date  ` | OUTPUT | X(8) ×10 | `mm/dd/yy` from TRAN-ORIG-TS (`:384-388`) |
| TDESC01..10 | `     Description          ` | OUTPUT | X(26) ×10 | TRAN-DESC first 26 chars (`:395`) |
| TAMT001..010 | `   Amount   ` | OUTPUT | X(12) ×10 | TRAN-AMT as `+99999999.99` (`:56`, `:383`) |
| PAGENUM | `Page:` (bms:84) | OUTPUT | X(8) ← 9(08) | CDEMO-CT00-PAGE-NUM (`:324`, `:373`) |
| ERRMSG | — | OUTPUT | X(78), red | messages per §5 |
| Header | `Tran:`/`Prog:`/`Date:`/`Time:` + titles | OUTPUT | — | `:567-586` |
Instruction text: `Type 'S' to View Transaction details from the list` (bms:444-448); footer `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` (bms:454-458); title `List Transactions` (bms:75-79).
All data fields are FSET: a redisplay that does not repopulate rows keeps the rows, page number and selection characters previously shown.

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S07-01 | Entry guard | Program entered without COMMAREA | Control returns to sign-on (COSGN00C) | COTRN00C | :107-109, :510-521 | S07-B4 | authGuard spec (S-01) |
| FR-S07-02 | First display | First entry from the menu (PGM-CONTEXT enter) | Page 1 from the start of the file displayed, search field blank, re-enter flag set | COTRN00C | :112-116, :206-207, :224-225 | S07-B3 | TransactionListServiceTests.FirstEntry_*; component spec ngOnInit |
| FR-S07-03 | Search | ENTER with blank search id | List restarts from the lowest transaction id; page number becomes 1 | COTRN00C | :206-207, :224-225, :305-307 | S07-B3 | Service + integration Enter_Blank* |
| FR-S07-04 | Search | ENTER with a 16-digit search id | First row is the first transaction with id ≥ key (GTEQ browse); page number 1; search field cleared | COTRN00C | :209-210, :593-600, :228, :325 | S07-B3 | Enter_NumericKey* |
| FR-S07-05 | Search validation | ENTER with a search id that is not numeric over all 16 positions | `Tran ID must be Numeric ...`; rows, page number and search input unchanged | COTRN00C | :211-218, :283, :227-229 | — | Enter_NonNumeric* |
| FR-S07-06 | Row rendering | A page is populated | ≤10 rows: id X(16), date `mm/dd/yy`, description 26 chars, amount `+99999999.99`; unfilled rows blank | COTRN00C | :289-303, :383-445, :450-505 | — | RowMapping*; component row spec |
| FR-S07-07 | Forward paging | Full page and another record follows | Page number +1, next-page flag Y, no message | COTRN00C | :305-310 | S07-B3 | Enter_FullPage*; PF8_* |
| FR-S07-08 | End of file | Fewer than 10 records remain, or exactly 10 with none after | `You have reached the bottom of the page...`; next-page flag N; page number +1 only if ≥1 row shown | COTRN00C | :297-320, :639-645 | S07-B3 | *Bottom* |
| FR-S07-09 | Key beyond file | Start key greater than every transaction id (STARTBR NOTFND) | `You are at the top of the page...`; rows unchanged; page number 0 (ENTER) or unchanged (PF8); next-page N | COTRN00C | :605-611, :283-320 | S07-B3 | *NotFound* |
| FR-S07-10 | PF8 | Next-page flag Y | Next 10 records after the last displayed id (record at that id skipped); search field cleared | COTRN00C | :257-268, :285-287, :325 | S07-B3 | PF8_* |
| FR-S07-11 | PF8 | Next-page flag N | `You are already at the bottom of the page...`; screen preserved (no erase), rows/page unchanged | COTRN00C | :269-273 | — | PF8_AtBottom* |
| FR-S07-12 | PF7 | Page number > 1 | Previous 10 records before the first displayed id, filled bottom-up; page −1 when more records precede, else page = 1; next-page flag Y | COTRN00C | :234-246, :333-369 | S07-B3 | PF7_* |
| FR-S07-13 | PF7 | Page number ≤ 1 | `You are already at the top of the page...`; screen preserved; next-page flag set Y (source side effect) | COTRN00C | :242, :245-251 | — | PF7_AtTop* |
| FR-S07-14 | Start of file | Backward paging reaches the first record (peek or mid-fill ENDFILE) | `You have reached the top of the page...`; page number 1 when 10 rows filled, unchanged when fewer | COTRN00C | :351-369, :673-679 | S07-B3 | PF7_*Top* |
| FR-S07-15 | Row selection | `S`/`s` typed on a row with an id (first non-blank selection wins) | Transfer to Transaction View (COTRN01C) with the selected id in context | COTRN00C | :148-195 | **S07-B1** (disabled route → coming-soon idiom) | Select_* |
| FR-S07-16 | Row selection | Other character on a row with an id | `Invalid selection. Valid value is S`; search/paging still processed; message stays unless a paging message overwrites it | COTRN00C | :196-203, :206-229 | — | Select_Invalid* |
| FR-S07-17 | Row selection | Selection typed on a row without an id | Ignored (no message); normal ENTER processing | COTRN00C | :183-184 | — | Select_BlankRow* |
| FR-S07-18 | PF3 | PF3 on the list | Return to main menu (COMEN01C) | COTRN00C | :122-124, :510-521 | S07-B2 | component spec F3/Exit |
| FR-S07-19 | Invalid key | Any AID other than ENTER/PF3/PF7/PF8 | `Invalid key pressed. Please see below...`; screen redisplayed unchanged | COTRN00C | :129-133; CSMSG01Y.cpy:20-21 | — | component spec invalid key |
| FR-S07-20 | File error | Browse/read RESP other than NORMAL/NOTFND/ENDFILE | `Unable to lookup transaction...`; no paging; rows/state unchanged | COTRN00C | :612-618, :646-652, :680-686 | S07-B3 | *StoreError* |
| FR-S07-21 | Ordering | Any page | Rows in TRAN-ID key order (VSAM KSDS byte order) | COTRN00C | :593-600, :626-634, :660-668 | S07-B3 | integration Ordering* |

## 5. Validation and error catalogue
| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `Invalid selection. Valid value is S` | selection char ≠ S/s | :198-200 | non-blocking (processing continues) | redisplay after paging |
| `Tran ID must be Numeric ...` | search id fails NUMERIC | :213-215 | blocking | rows/page unchanged, input kept |
| `You are at the top of the page...` | STARTBR NOTFND | :608-609 | blocking | rows unchanged |
| `You have reached the bottom of the page...` | READNEXT ENDFILE | :642-643 | informational | page shown, next-page N |
| `You have reached the top of the page...` | READPREV ENDFILE | :676-677 | informational | page shown, page = 1 |
| `You are already at the top of the page...` | PF7 with page ≤ 1 | :248-249 | blocking | screen preserved |
| `You are already at the bottom of the page...` | PF8 with next-page N | :270-271 | blocking | screen preserved |
| `Unable to lookup transaction...` | RESP other | :615-616, :649-650, :683-684 | blocking | rows/state unchanged |
| `Invalid key pressed. Please see below...` | unmapped AID | :132; CSMSG01Y.cpy:20-21 | blocking | redisplay |
All message text is source-proven (working-storage literals or copybooks). All are rendered in the single red ERRMSG field.

## 6. Field and data derivations
- Date: `TRAN-ORIG-TS(1:4)(3:2)` → yy, `(6:2)` → mm, `(9:2)` → dd → `mm/dd/yy` (`:384-388`, `CSDAT01Y.cpy` WS-TIMESTAMP / WS-CURDATE-MM-DD-YY).
- Amount: `S9(09)V99` → `+99999999.99`: explicit sign, 8 integer digits zero-filled (high-order digit truncated for |amt| ≥ 100,000,000), 2 decimals (`:56`, `:383`).
- Description: first 26 characters of TRAN-DESC X(100) (`:395`).
- Page number: `9(08)` moved to X(8) → zero-filled 8 digits (`:324`).
- Paging cursors: TRNID-FIRST = row 1 id, TRNID-LAST = row 10 id, only when that row is filled (`:392-393`, `:438-439`).
- Forward key: blank search → LOW-VALUES; PF8 → TRNID-LAST or HIGH-VALUES when blank (`:259-263`). Backward key: TRNID-FIRST or LOW-VALUES (`:236-240`).

## 7. Mechanics (demoted, cited) and preserved source quirks
Demoted: pseudo-conversational RETURN TRANSID (`:138-141`); COMMAREA re-enter flag (`:112-113`); map SEND with/without ERASE (`:533-549`); header date/time (`:567-586`); intermediate SENDs inside the RESP handlers (`:611`, `:645`, `:679`) — the final SEND of the paging paragraph carries the same message and the observable result is one screen; ENDBR after a failed STARTBR (`:322`, `:371`) would raise INVREQ in CICS — not reproduced.
Preserved quirks (source-derived, kept for parity): NOTFND wording `You are at the top of the page...` even for a key beyond the end (FR-S07-09); PF7-at-top forces next-page Y so a following PF8 browses from TRNID-LAST (or HIGH-VALUES → NOTFND path) (FR-S07-13); PF8 from a full last page whose peek failed yields an empty page with the bottom message and unchanged page number only when reached via the forced flag; selection characters are never cleared by the program; the numeric error leaves STARTBR positioned but skips the browse (`:283`) — final message is the numeric error.

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S07-01: Given no session, When `/transactions/list` is opened, Then the sign-on screen is shown.
- FR-S07-02: Given a session, When the list opens, Then rows 1-10 are the 10 lowest transaction ids and page shows `00000001`.
- FR-S07-03: Given any page, When ENTER with blank search id, Then the first page is shown again as page 1.
- FR-S07-04: Given id `0000000000000005` exists, When ENTER with that search id, Then row 1 is that id, page 1, search field cleared.
- FR-S07-05: Given any page, When ENTER with search id `12A`, Then `Tran ID must be Numeric ...`, rows/page unchanged, `12A` still in the field.
- FR-S07-06: Given a transaction amount 12.34 dated 2022-07-19 with a 40-char description, When shown, Then amount `+00000012.34`, date `07/19/22`, description = first 26 chars.
- FR-S07-07: Given ≥11 records from the key, When a page is shown, Then no message, page +1, PF8 enabled.
- FR-S07-08: Given exactly 10 records remain, When shown, Then `You have reached the bottom of the page...` and PF8 → `You are already at the bottom of the page...`.
- FR-S07-09: Given search id greater than all ids, When ENTER, Then `You are at the top of the page...`, rows unchanged, page `00000000`.
- FR-S07-10: Given page 1 rows 1-10 with more, When PF8, Then rows are records 11-20, page 2.
- FR-S07-11: Given next-page N, When PF8, Then `You are already at the bottom of the page...`, rows unchanged.
- FR-S07-12: Given page 3, When PF7, Then rows are the 10 records before the first displayed id, page 2.
- FR-S07-13: Given page 1, When PF7, Then `You are already at the top of the page...`, rows unchanged.
- FR-S07-14: Given page 2 whose predecessors are exactly records 1-10, When PF7, Then rows 1-10, page 1 and `You have reached the top of the page...`.
- FR-S07-15: Given `S` on row 3, When ENTER, Then Transaction View is requested for row 3's id (currently: coming-soon message, S07-B1).
- FR-S07-16: Given `X` on row 3 and blank search, When ENTER, Then `Invalid selection. Valid value is S` and page 1 redisplayed.
- FR-S07-17: Given `X` on an empty row only, When ENTER, Then no selection message; normal paging.
- FR-S07-18: Given the list, When F3/Exit, Then the main menu is shown.
- FR-S07-19: Given the list, When F5, Then `Invalid key pressed. Please see below...` and rows unchanged.
- FR-S07-20: Given the transaction store unreachable, When ENTER, Then `Unable to lookup transaction...`.
- FR-S07-21: Given ids `0000000000000009`, `0000000000000010`, `0000000000000100`, When listed, Then order is 0009, 0010, 0100 (byte order).

## 9. Traceability matrix
FR-S07-02..17, 20, 21 → COTRN00C → `TransactionListService` (backend/CardDemo.Application/Transactions) → `TransactionListServiceTests` (unit, fake repository) + `TransactionListIntegrationTests` (Testcontainers Postgres, real repository + API).
FR-S07-01, 18, 19 + screen shape of 02, 05, 06, 11, 13, 15 → `TransactionListComponent` → `transaction-list.component.spec.ts`.

**FR-S07-19 target disposition:** F3 = Exit, F7 = backward, F8 = forward; any other F1–F12 shows `Invalid key pressed. Please see below...` via `frontend/src/app/shared/invalid-key.ts` (S-01 helper, unchanged). Non-function keys are ordinary web input.

**FR-S07-15 target disposition (S07-B1):** the XCTL target is resolved against the menu route registry entry with `ProgramKey = COTRN01C`. While that entry is disabled the response is the S-01 coming-soon idiom (`This option Transaction View is coming soon ...`, info); once S-08 lands the same call yields `navigate` with the registry route and the selected id.

## 10. Program index
| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| COTRN00C | transaction list browse/select | FR-S07-01..21 | [programs/COTRN00C_functional_requirement.md](programs/COTRN00C_functional_requirement.md) |

## 11. Open questions and assumptions
1. Search id shorter than 16 digits is rejected by the source NUMERIC test (BMS space padding); kept as-is — no deviation.
2. TRAN-ORIG-TS is always populated in shipped data; a null timestamp renders a blank date in the target (source would move spaces into numeric picture fields — undefined).
3. Coming-soon wording for the COTRN01C hand-off is the approved S01-B1 registry idiom, not a COTRN00C literal.
4. Header date/time and titles are demoted mechanics (as in S-01); `Tran:`/`Prog:` identifiers are rendered statically.
