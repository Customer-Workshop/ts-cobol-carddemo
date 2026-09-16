# S-04 Card List — Stream Analysis (`!mf_stream_analysis`)

Stream: **S-04 Card List** (ONLINE). Entry transaction **CCLI**, program **COCRDLIC**, mapset **COCRDLI** / map **CCRDLIA**.
Sources: `app/cbl/COCRDLIC.cbl` (1,420 lines), `app/bms/COCRDLI.bms`, `app/cpy/CVCRD01Y.cpy`, `app/cpy/COCOM01Y.cpy`,
`app/cpy/CVACT02Y.cpy`, `app/cpy/CSSTRPFY.cpy`, `app/csd/CARDDEMO.CSD:203,357-358`. Inventory row: `CardDemo_inventory.md:97`.

## 1. Pinned stream
| Item | Value | Cite |
|---|---|---|
| Transaction | `CCLI` → `PROGRAM(COCRDLIC)` | `CARDDEMO.CSD:357-358` |
| Program literals | `LIT-THISPGM='COCRDLIC'`, `LIT-THISTRANID='CCLI'`, `LIT-THISMAPSET='COCRDLI'`, `LIT-THISMAP='CCRDLIA'` | `COCRDLIC.cbl:179-186` |
| Upstream caller | COMEN01C main-menu option 03 "Credit Card List" (XCTL with `CARDDEMO-COMMAREA`) | `COCRDLIC.cbl:187-194`; registry `appsettings.json` Main[03] |
| Downstream XCTL targets (off-stream) | COCRDSLC/CCDL (card detail), COCRDUPC/CCUP (card update), COMEN01C/CM00 (exit) | `:195-210`, `:402-405`, `:538-541`, `:566-569` |
| Files | `CARDDAT` KSDS (key CARD-NUM X(16)), `CARDAIX` declared but **never used** | `:213-217`; STARTBR/READNEXT/READPREV only on `LIT-CARD-FILE` |
| User-type restriction | none — the program only stamps `CDEMO-USRTYP-USER` into the COMMAREA, no gate | `:320`, `:388`, `:466` |

## 2. Program inventory + leaf-first DAG
Single program, no CALLs (only `COPY CSSTRPFY` AID mapping, `:1416`). Leaf: COCRDLIC. DAG depth 1 → one wave.
Out of stream (must remain behind the disabled route registry): COCRDSLC, COCRDUPC (S-05/S-06 per inventory catalogue), COMEN01C (S-01, delivered).

## 3. Surfaces (ONLINE)
### COCRDLIC — screen CCRDLIA / mapset COCRDLI (`app/bms/COCRDLI.bms`)
| Field | Pos | Len | Attr | Meaning | Cite |
|---|---|---|---|---|---|
| TRNNAME / PGMNAME | (1,7) / (2,7) | 4 / 8 | ASKIP | `CCLI` / `COCRDLIC` | bms:34-37, 57-60; cbl:649-650 |
| TITLE01 / TITLE02 | (1,21) / (2,21) | 40 | ASKIP | `AWS Mainframe Modernization` / `CardDemo` (COTTL01Y) | bms:38-41, 61-64; cbl:647-648 |
| CURDATE / CURTIME | (1,71) / (2,71) | 8 | ASKIP | `mm/dd/yy`, `hh:mm:ss` | bms:47-51, 70-74; cbl:652-664 |
| literal | (4,31) | 17 | — | `List Credit Cards` | bms:75-78 |
| PAGENO | (4,76) | 3 | — | `WS-CA-SCREEN-NUM` (PIC 9(1)) | bms:82-83; cbl:237, 667 |
| ACCTSID | (6,44) | 11 | FSET,IC,UNPROT, green underline | account filter | bms:89-93; cbl:969 |
| CARDSID | (7,44) | 16 | FSET,UNPROT, green underline | card-number filter | bms:101-105; cbl:970 |
| column heads | row 9/10 | — | — | `Select`, `Account Number`, ` Card Number `, `Active ` | bms:106-138 |
| CRDSEL1..7 | (11..17,12) | 1 | FSET,PROT (program flips to `DFHBMFSE` when row filled and not protected) | selection code S/U | bms:140-145; cbl:748-832 |
| ACCTNO1..7 | (11..17,22) | 11 | PROT | `CARD-ACCT-ID` | bms:146-150; cbl:684 |
| CRDNUM1..7 | (11..17,43) | 16 | PROT | `CARD-NUM` | bms:151-155; cbl:685 |
| CRDSTS1..7 | (11..17,67) | 1 | PROT | `CARD-ACTIVE-STATUS` | bms:156-160; cbl:686 |
| INFOMSG | (20,19) | 45 | PROT, dark until set | `WS-INFO-MSG` | bms:324-328; cbl:669-671, 926-930 |
| ERRMSG | (23,1) | 78 | ASKIP,BRT,red | `WS-ERROR-MSG` (PIC X(75)) | bms:331-334; cbl:117, 924 |
| footer | (24,1) | 78 | ASKIP | `  F3=Exit F7=Backward  F8=Forward` | bms:335-339 |

## 4. Data + field dictionary
| COBOL item | PIC | Role | Target |
|---|---|---|---|
| `CARD-NUM` | X(16) | KSDS key, row column, browse RID | `Card.CardNumber` (PK `card_num`, existing) |
| `CARD-ACCT-ID` | 9(11) | row column, account filter comparand | `Card.AccountId` (idx `ix_cards_card_acct_id`, existing) |
| `CARD-ACTIVE-STATUS` | X(1) | row column | `Card.ActiveStatus` (existing) |
| `CC-ACCT-ID` / `-N` | X(11) / 9(11) | typed account filter; `NUMERIC` class test | `accountFilter` string, regex `^\d{11}$` |
| `CC-CARD-NUM` / `-N` | X(16) / 9(16) | typed card filter | `cardFilter` string, regex `^\d{16}$` |
| `WS-EDIT-SELECT(1..7)` | X(1) ×7 | selection codes; 88s `SELECT-OK`='S','U'; `SELECT-BLANK`=' ',LOW-VALUES | `selections[7]` |
| `WS-CA-FIRST-CARD-NUM` / `WS-CA-LAST-CARD-NUM` | X(16) | page anchors carried in COMMAREA | `CardListPageState.FirstCardNumber/LastCardNumber` |
| `WS-CA-SCREEN-NUM` | 9(1) | page number (single digit, wraps) | `CardListPageState.ScreenNumber` |
| `WS-CA-LAST-PAGE-DISPLAYED` | 9(1) | 0 = last page shown, 9 = not shown | `CardListPageState.LastPageShown` |
| `WS-CA-NEXT-PAGE-IND` | X(1) | 'Y' next page exists | `CardListPageState.NextPageExists` |
| `WS-SCREEN-ROWS(1..7)` | 28 bytes ×7 | displayed rows carried in COMMAREA | `CardListPageState.Rows` |
| `CDEMO-ACCT-ID`, `CDEMO-CARD-NUM` | 9(11), 9(16) | hand-off context to detail/update | `CardListNavigationTarget.AccountId/CardNumber` |
Shared data layer already provides every column and index the program touches (`cards` table, `CardConfiguration`); **no schema extension is needed**. Only additive repository methods (raw key-ordered browse verbs) are required.

## 5. Control flow (source-derived)
1. Init: clear work areas, `WS-ERROR-MSG-OFF`; first entry (`EIBCALEN=0`) or entry from another program with `CDEMO-PGM-ENTER` resets the paging COMMAREA (page 1, last-page-not-shown, anchors = spaces) — `:300-343`.
2. AID mapping (`CSSTRPFY`); valid set = ENTER, PF3, PF7, PF8; **any other AID is remapped to ENTER** (`:370-380`). No invalid-key message exists in this program.
3. PF3 on re-entry → XCTL COMEN01C (`:384-406`). `WS-EXIT-MESSAGE` is set but never displayed (control leaves).
4. Re-entry → RECEIVE + edits (`:951-1121`): account filter → card filter → selection array (array edit skipped when a filter failed, `:1075-1077`).
5. `CA-LAST-PAGE-NOT-SHOWN` reset for every AID except PF8 (`:410-414`).
6. Dispatch EVALUATE (`:418-583`), first match wins: INPUT-ERROR → PF7 on first page → PF3/re-enter-from-elsewhere (fresh list) → PF8 with next page → PF7 not first page → ENTER+'S' → ENTER+'U' → OTHER (re-list from current first key).
7. Browse: `9000-READ-FORWARD` (`:1123-1263`) STARTBR GTEQ, READNEXT loop applying `9500-FILTER-RECORDS` (`:1382-1411`), fills up to 7 rows; on the 7th row a **raw, unfiltered** look-ahead READNEXT decides `CA-NEXT-PAGE-EXISTS` and stores that record's key as the next anchor (`:1191-1231`). ENDFILE → `NO MORE RECORDS TO SHOW`; ENDFILE on page 1 with 0 rows → `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` (`:1233-1245`). `9100-READ-BACKWARDS` (`:1264-1380`) positions at the first key, discards it, READPREV fills rows 7→1; running out of records before row 1 hits `WHEN OTHER` (file-error text, `:1361-1369`).
8. Send map (`:624-950`): header, rows, attributes (protect empty rows / all rows on filter error; red + cursor on error rows), filter echo, cursor, message selection (`:895-931`).

## 6. Boundary table (headline) — S04-B1..S04-B4, status DECIDED in the migration plan
| ID | Boundary | Evidence | Decision |
|---|---|---|---|
| S04-B1 | XCTL to COCRDSLC ('S') / COCRDUPC ('U') — off-stream targets | `:517-569` | Resolve via the S-01 route registry: enabled → navigate with account/card context; disabled → `This option <name> is coming soon ...` idiom (MenuService). Never implement the targets here. |
| S04-B2 | Selection-error redisplay browses from an uninitialized RID (spaces ⇒ start of file) while keeping the page number | `:300`, `:419-438` | Ported as-is (parity). Flagged as open question for the owner. |
| S04-B3 | Unmapped AID keys are remapped to ENTER (not `Invalid key pressed...`) | `:370-380` | Ported as-is; documented divergence from the S-01 *convention*, not from source. |
| S04-B4 | READPREV exhaustion emits a CICS `WS-FILE-ERROR-MESSAGE` with RESP/RESP2 | `:153-171`, `:1361-1369` | Ported with the same layout; RESP=ENDFILE(20), RESP2 90 taken from CICS documentation (not derivable from source). |

## 7. Waves
Wave 1 (only): COCRDLIC — backend `CardListService` state machine + `CardsController` (`/api/v1/cards/list`), Angular `CardListComponent` at `/cards/list` (authGuard), tests. Registry flag `Main[03] COCRDLIC` stays **disabled** (integration stage flips it).

## 8. Risks
- Page number is `PIC 9(1)`: `ADD +1` past 9 truncates to 0, `SUBTRACT 1` from 0 stores 1 (unsigned). Ported literally; visible only beyond 9 pages.
- Look-ahead is unfiltered, so with a filter the next-page indicator can be set although no further matching rows exist; PF8 then shows an empty page with `NO MORE RECORDS TO SHOW` (parity).
- Filter changes are applied from the *current* first key, not from the start of the file (`:574-582`) — parity.
- `CARDAIX` alternate index is declared but unused; the account filter is a sequential filter over the primary key order (parity; the target pushes the equality filter into SQL, same result set and order).

## 9. Validation
All behaviours above are cited to `COCRDLIC.cbl` / `COCRDLI.bms` line ranges; no CALLed subprograms; no DB2/MQ; no batch. Analysis complete.
