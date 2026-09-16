# S-04 Card List — Stream Functional Requirements (`!mf_stream_fr_generation`)

Stream S-04, transaction `CCLI`, program `COCRDLIC` (`app/cbl/COCRDLIC.cbl`), map `CCRDLIA` (`app/bms/COCRDLI.bms`).
Analysis: [S04_card_list_analysis.md](S04_card_list_analysis.md). Program FR: [programs/COCRDLIC_functional_requirement.md](programs/COCRDLIC_functional_requirement.md).
Cites of the form `:nnn` refer to `COCRDLIC.cbl`; `bms:nnn` to `COCRDLI.bms`.

## 1. Purpose and scope
Paged browse of the card master (`CARDDAT`, key card number) seven rows at a time, optionally filtered by an exact account id and/or exact card number, with per-row selection `S` (view detail) or `U` (update) that hands the chosen card to the downstream card programs. In scope: COCRDLIC only. Out of scope (behind the disabled route registry): COCRDSLC, COCRDUPC.

## 2. Actors and preconditions
Any signed-on CardDemo user (admin or regular); the program performs no user-type gate (`:320`, `:388`). Reached from main-menu option 03 with `CARDDEMO-COMMAREA` (`CDEMO-PGM-ENTER`, `CDEMO-FROM-PROGRAM='COMEN01C'`) or on pseudo-conversational re-entry with its own paging COMMAREA (`:315-343`).

## 3. Surface specification
### Card list screen CCRDLIA (`app/bms/COCRDLI.bms`)
| Field | Len | In/Out | Rule | Cite |
|---|---|---|---|---|
| Account Number filter (ACCTSID) | 11 | in/out | blank/zero = no filter; else must be 11 digits | bms:89-93; `:1003-1030` |
| Credit Card Number filter (CARDSID) | 16 | in/out | blank/zero = no filter; else must be 16 digits | bms:101-105; `:1036-1067` |
| Page (PAGENO) | 3 | out | `WS-CA-SCREEN-NUM` PIC 9(1) | bms:82; `:667` |
| Row 1..7: Select (CRDSEL n) | 1 | in/out | `S`, `U`, or blank; protected when the row is empty or a filter is invalid | bms:140; `:748-832` |
| Row 1..7: Account Number (ACCTNO n) | 11 | out | `CARD-ACCT-ID` | bms:146; `:684` |
| Row 1..7: Card Number (CRDNUM n) | 16 | out | `CARD-NUM` | bms:151; `:685` |
| Row 1..7: Active (CRDSTS n) | 1 | out | `CARD-ACTIVE-STATUS` | bms:156; `:686` |
| Info message (INFOMSG) | 45 | out | `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` or blank | bms:324; `:112-116`, `:895-931` |
| Error message (ERRMSG) | 78 | out | red; catalogue §5 | bms:331; `:924` |
| Footer | 78 | out | `  F3=Exit F7=Backward  F8=Forward` | bms:335-339 |

## 4. Functional requirements (KEEP)
| ID | Flow | Business trigger | Observable result | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|
| FR-S04-01 | Initial list | Entry from the menu (fresh context) | Page 1: the first 7 cards in card-number order, filters blank, info message shown | `:315-343`, `:458-482`, `:1123-1263` | — | CardListServiceTests / CardListIntegrationTests / component spec |
| FR-S04-02 | Screen layout | Screen displayed | 7 result rows (Select 1 / Account 11 / Card 16 / Active 1), filters 11 and 16 wide, page number, 45-char info area, 78-char red error area, footer `F3=Exit F7=Backward F8=Forward` | bms:82-339 | — | component spec |
| FR-S04-03 | Filter validation | Account filter present but not an 11-digit number | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`; field red with cursor; previously listed rows retained (no re-read); all Select fields protected | `:1017-1025`, `:431-435`, `:872-875`, `:752` | — | service + integration + spec |
| FR-S04-04 | Filter validation | Card filter present but not a 16-digit number | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`; same redisplay as FR-S04-03. When both filters fail the account message wins | `:1052-1062` (`IF WS-ERROR-MSG-OFF`) | — | service + integration + spec |
| FR-S04-05 | Filtering | Valid account and/or card filter + ENTER | Only cards whose account id equals the account filter and whose card number equals the card filter are listed (AND). The browse restarts from the current first anchor, not from the start of the file | `:1382-1411`, `:1159-1160`, `:574-582` | — | service + integration |
| FR-S04-06 | Empty result | Page 1 yields no matching card | `NO RECORDS FOUND FOR THIS SEARCH CONDITION.`; info message suppressed; no rows | `:1241-1245`, `:926-930` | — | service + integration + spec |
| FR-S04-07 | Page forward | PF8 while a next page exists | Page number +1; rows start at the stored next-page anchor; after the 7th row an unfiltered look-ahead read decides whether a next page exists and stores its key as the anchor | `:486-497`, `:1191-1214` | — | service + integration + spec |
| FR-S04-08 | End of file | Fewer than 7 matching rows remain, or nothing follows the 7th row | `NO MORE RECORDS TO SHOW`; next-page indicator cleared; info message still shown | `:1215-1221`, `:1233-1240`, `:910-916` | — | service + integration |
| FR-S04-09 | Repeated page forward | PF8 pressed again while no next page exists (last page already shown by a PF8) | Same page re-listed; `NO MORE PAGES TO DISPLAY`; info message blank. Any key other than PF8 resets the "last page shown" state | `:410-414`, `:572-582`, `:905-916` | — | service + spec |
| FR-S04-10 | Page backward | PF7 while not on page 1 | Page number −1; the 7 matching cards immediately preceding the current first anchor are listed; the new first anchor is the earliest listed card; the old first anchor becomes the next-page anchor. When the decrement lands on page 1 the message check of FR-S04-11 fires (`NO PREVIOUS PAGES TO DISPLAY`, info blank) because `1400-SETUP-MESSAGE` tests `CA-FIRST-PAGE` after the decrement | `:501-513`, `:1264-1372`, `:901-904` | — | service + integration + spec |
| FR-S04-11 | Page backward on first page | PF7 while on page 1 | Page re-listed from the first anchor; `NO PREVIOUS PAGES TO DISPLAY`; info message blank | `:439-454`, `:901-904` | — | service + spec |
| FR-S04-12 | Selection edit | A Select code other than `S`, `U`, blank | `INVALID ACTION CODE`; offending row(s) flagged red | `:1108-1113`, `:755-761` | — | service + spec |
| FR-S04-13 | Selection edit | More than one row carries `S`/`U` | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`; every `S`/`U` row flagged red; message wins over `INVALID ACTION CODE` | `:1079-1105` | — | service + spec |
| FR-S04-14 | Selection-error redisplay | FR-S04-12/13 raised (filters valid) | Rows re-read from the start of the file (uninitialized browse key) while the page number is kept; typed Select codes echoed; info message shown | `:300`, `:419-438`, `:683` | S04-B2 | service + integration |
| FR-S04-15 | Hand-off to detail | Exactly one `S` + ENTER, no other error | Transfer to card detail (COCRDSLC / CCDL) with the selected row's account id and card number as context | `:517-541` | S04-B1 | service + integration + spec |
| FR-S04-16 | Hand-off to update | Exactly one `U` + ENTER, no other error | Transfer to card update (COCRDUPC / CCUP) with the same context | `:545-569` | S04-B1 | service + integration + spec |
| FR-S04-17 | Exit | PF3 | Return to the main menu (COMEN01C) | `:384-406` | — | service + spec |
| FR-S04-18 | AID handling | Any AID other than ENTER/PF3/PF7/PF8 | Treated exactly as ENTER (list refreshed with current inputs); no invalid-key message | `:370-380` | S04-B3 | service + spec |
| FR-S04-19 | Info message | Screen displayed | `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` shown, except: a filter error, PF7 on page 1, `NO MORE PAGES TO DISPLAY`, or no records found | `:895-931` | — | service + spec |
| FR-S04-20 | Filter echo / cursor | Any redisplay after re-entry | Typed filter values echoed as entered (valid or not); fresh entry shows blanks; cursor on the first invalid filter, otherwise on the first flagged Select row 2–7 (row 1 never receives the cursor: `:755-761` sets colour only), otherwise on the account filter | `:837-889`, `:755-761`, `:770-783` | — | service + spec |
| FR-S04-21 | Select protection | Screen displayed | Select is enterable only on rows holding a card; all Select fields protected when a filter is invalid | `:748-832`, `:987`, `:1020`, `:1055` | — | service + spec |
| FR-S04-22 | Backward exhaustion | PF7 (not page 1) finds fewer than 7 preceding matching cards (filter changed between pages) | Found rows bottom-aligned (rows above blank); first anchor unchanged; error `File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090` | `:153-171`, `:1338-1369` | S04-B4 | service + integration |
| FR-S04-23 | Access | Unauthenticated request | Screen/API rejected; reachable only with a signed-on session (COMMAREA from the menu shell) | `:315-332`; S-01 session contract | — | integration (401) + route guard |

## 5. Validation and error catalogue
| Message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | account filter not `NUMERIC` (11 digits) | `:1021-1023` | yes | rows retained, Select protected, cursor on account |
| `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | card filter not `NUMERIC` (16 digits) and no earlier message | `:1056-1060` | yes | rows retained, Select protected, cursor on card |
| `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` | >1 `S`/`U` | `:1084-1087` | yes | re-list from file start (S04-B2), S/U rows red |
| `INVALID ACTION CODE` | Select code ∉ {S,U,blank} and no earlier message | `:1108-1113` | yes | re-list from file start (S04-B2), bad rows red |
| `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` | ENDFILE on page 1 with 0 rows | `:1241-1245` | info | empty rows, no info message |
| `NO MORE RECORDS TO SHOW` | ENDFILE during forward read (no earlier message) | `:1218-1220`, `:1238-1240` | info | rows shown, next page off |
| `NO MORE PAGES TO DISPLAY` | PF8, no next page, last page already shown | `:905-909` | info | same page, info blank |
| `NO PREVIOUS PAGES TO DISPLAY` | PF7 on page 1 | `:901-904` | info | same page, info blank |
| `File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090` | READPREV exhausted before 7 rows | `:1361-1369` | info | partial rows bottom-aligned |
| `PF03 PRESSED.EXITING` | PF3 | `:396` | — | set in working storage but never displayed (XCTL to menu) |
| `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | see FR-S04-19 | `:115-116` | info | — |

Validation order (blocking, first message wins): account filter → card filter → (skipped if a filter failed) selection count → per-row selection code (`:985-997`, `:1075-1077`).

## 6. Field and data derivations
- Filter "supplied": `NOT (LOW-VALUES OR SPACES OR numeric value 0)`; `NUMERIC` class test on the full X(11)/X(16) field ⇒ exactly 11/16 digits (`:1007-1017`, `:1042-1052`).
- Filter comparison: `CARD-ACCT-ID = CC-ACCT-ID` (numeric vs X(11), i.e. 11-digit equality), `CARD-NUM = CC-CARD-NUM-N` (`:1386`, `:1397`).
- Page anchors: first = card number of row 1; next = key of the raw look-ahead record after row 7, else row 7 (`:1173-1176`, `:1194-1214`); on backward paging next-anchor := old first anchor (`:1268`).
- Page number: `PIC 9(1)` — 1 on fresh entry; `+1` on PF8 (10 truncates to 0), `−1` on PF7 (unsigned; 0−1 stores 1); set to 1 when 0 at first row (`:237`, `:492`, `:508`, `:1177-1178`).
- Selected row: last row index holding `S`/`U` (`:1099-1102`); with exactly one selection this is that row.

## 7. Mechanics (demoted, cited)
CICS SEND/RECEIVE plumbing (`:938-983`), COMMAREA (de)serialization (`:326-331`, `:604-620`), `CSSTRPFY` AID copy (`:1416`), header date/time formatting (`:645-664`), attribute bytes (`DFHBMFSE/DFHBMPRO/DFHBMPRF/DFHRED`), the dead `'*'` marker branch on row 1 (`:757-759`, unreachable: error flags are only set on non-blank codes), unused `CARDAIX` literal (`:215-217`), `WS-CA-LAST-CARD-NUM` value after ENDFILE (never consumed — a fresh read always precedes its next use).

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S04-01: Given a signed-on user selecting Credit Card List, When the screen opens, Then page 1 shows the 7 lowest card numbers with account id and status, filters blank, info message present.
- FR-S04-02: Given the screen, Then filters accept ≤11 / ≤16 characters, 7 row slots exist with Select ≤1 char, and the footer reads `F3=Exit F7=Backward F8=Forward`.
- FR-S04-03: Given rows listed, When account filter `123` + ENTER, Then the account message shows, rows are unchanged and Select fields are protected.
- FR-S04-04: Given account `12345678901` and card `ABC`, When ENTER, Then the card message shows; Given both invalid, Then only the account message shows.
- FR-S04-05: Given account `00000000010` owns 3 cards, When filtering by it from page 1, Then only those 3 cards list; adding the card filter narrows to that one card.
- FR-S04-06: Given a filter matching nothing, When ENTER on page 1, Then `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` and no info message.
- FR-S04-07: Given 8+ cards, When PF8 on page 1, Then page 2 starts at card 8.
- FR-S04-08: Given 10 cards, When PF8 to page 2, Then 3 rows and `NO MORE RECORDS TO SHOW`.
- FR-S04-09: Given page 2 reached by PF8 and at end, When PF8 again, Then `NO MORE PAGES TO DISPLAY` and the info message is blank.
- FR-S04-10: Given page 2, When PF7, Then page 1 with the 7 cards preceding page 2's first card.
- FR-S04-11: Given page 1, When PF7, Then page 1 re-listed with `NO PREVIOUS PAGES TO DISPLAY`.
- FR-S04-12: Given `X` in row 2, When ENTER, Then `INVALID ACTION CODE` and row 2 flagged.
- FR-S04-13: Given `S` in rows 1 and 3, When ENTER, Then `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` and rows 1 and 3 flagged.
- FR-S04-14: Given page 3 and an invalid selection, When ENTER, Then the rows shown are those of the start of the file while the page number stays 3.
- FR-S04-15/16: Given exactly one `S` (`U`), When ENTER, Then the hand-off target is COCRDSLC (COCRDUPC) with that row's account id and card number; with the target route disabled the registry's coming-soon message is shown instead.
- FR-S04-17: Given the screen, When PF3, Then the main menu is shown.
- FR-S04-18: Given the screen, When F5 (or any F-key other than F3/F7/F8), Then the list is refreshed exactly as for ENTER and no invalid-key message appears.
- FR-S04-19: Given any normal display, Then the info message reads `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD`; Given a filter error / PF7 on page 1 / `NO MORE PAGES TO DISPLAY` / no records, Then it is blank.
- FR-S04-20: Given a typed filter, When redisplayed, Then the same text is shown; the cursor sits on the first invalid filter or on the account filter.
- FR-S04-21: Given a page with 3 rows, Then rows 4–7 have no enterable Select; Given a filter error, Then all Select fields are protected.
- FR-S04-22: Given page 2 reached with no filter, When the account filter is changed and PF7 pressed so that fewer than 7 preceding cards match, Then the found cards occupy the bottom rows, the first anchor is unchanged and the file-error message shows.
- FR-S04-23: Given no session token, When calling the list API or opening the route, Then 401 / redirect to sign-on.

## 9. Traceability matrix
FR-S04-01..23 → COCRDLIC → cites §4 → `backend/CardDemo.Tests/Cards/CardListServiceTests.cs` (unit, all FRs), `backend/CardDemo.Tests/Cards/CardListIntegrationTests.cs` (Testcontainers PostgreSQL: 01, 03-08, 10, 14-16, 22, 23), `frontend/src/app/cards/card-list.component.spec.ts` (UI-owned: 02, 03, 06, 07, 09-13, 15-21).

## 10. Program index
| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| COCRDLIC | card list browse / select | FR-S04-01..23 | [programs/COCRDLIC_functional_requirement.md](programs/COCRDLIC_functional_requirement.md) |

## 11. Open questions and assumptions
1. **S04-B2 (FR-S04-14)**: the selection-error path re-reads from an uninitialized key (spaces) — the mainframe shows page-1 data under the current page number. Ported for parity; owner may elect to re-list from the current first anchor at the integration stage.
2. **S04-B3 (FR-S04-18)**: COCRDLIC remaps unknown AIDs to ENTER instead of emitting `Invalid key pressed...`; the S-01 shared invalid-key helper is therefore not applicable to this screen (source-derived).
3. **S04-B4 (FR-S04-22)**: RESP2 `90` for READPREV ENDFILE is taken from CICS documentation, not from source; RESP `20` = `DFHRESP(ENDFILE)`.
4. The unfiltered look-ahead (FR-S04-07) can announce a next page that turns out empty under a filter; ported for parity.
5. Header date/time (`mm/dd/yy`, `hh:mm:ss`) are rendered client-side from the browser clock — equivalent to `FUNCTION CURRENT-DATE` on the CICS region.
