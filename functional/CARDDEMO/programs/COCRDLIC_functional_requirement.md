# COCRDLIC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COCRDLIC — `app/cbl/COCRDLIC.cbl`. Stream S-04, wave 1.
- Role: card master browse — 7-row paged list over `CARDDAT` in card-number order with optional exact account/card filters, PF7/PF8 paging, single-row `S`/`U` selection handing off to card detail/update.

## 2. Trigger / caller contract
- CICS transaction `CCLI` (`app/csd/CARDDEMO.CSD:357-358`); XCTL'd from COMEN01C (option 03) with `CARDDEMO-COMMAREA`; re-entered pseudo-conversationally with `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` (`COCRDLIC.cbl:315-343`, `:604-620`).
- Exits: XCTL COMEN01C on PF3 (`:402-405`); XCTL COCRDSLC on `S` (`:538-541`); XCTL COCRDUPC on `U` (`:566-569`) — the latter two are off-stream (S04-B1).

## 3. Inputs and outputs
Inputs: ACCTSIDI X(11), CARDSIDI X(16), CRDSEL1I..7I X(1) of map CCRDLIA (`:969-978`); AID; paging COMMAREA (first/last anchors, page number, last-page-shown, next-page indicator, 7 rows) (`:229-260`).
Outputs: 7 rows (ACCTNO/CRDNUM/CRDSTS/CRDSEL echo), PAGENO, INFOMSG, ERRMSG, filter echo, attributes/cursor (`:642-932`); outgoing COMMAREA with `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` on hand-off (`:531-534`, `:559-562`).

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COCRDLIC-01 | fresh entry | page 1 of cards from lowest key, filters blank | `:315-343`, `:458-482` | FR-S04-01 |
| COCRDLIC-02 | screen | layout per CCRDLIA | bms | FR-S04-02 |
| COCRDLIC-03 | account filter not 11 digits | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, rows kept, Select protected | `:1017-1025`, `:431-435` | FR-S04-03 |
| COCRDLIC-04 | card filter not 16 digits | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` (account message wins) | `:1052-1062` | FR-S04-04 |
| COCRDLIC-05 | valid filters | equality filter(s) applied from current first anchor | `:1382-1411`, `:574-582` | FR-S04-05 |
| COCRDLIC-06 | no rows on page 1 | `NO RECORDS FOUND FOR THIS SEARCH CONDITION.`, no info | `:1241-1245`, `:926-930` | FR-S04-06 |
| COCRDLIC-07 | PF8, next page exists | page+1 from next anchor; raw look-ahead | `:486-497`, `:1191-1214` | FR-S04-07 |
| COCRDLIC-08 | ENDFILE while reading forward | `NO MORE RECORDS TO SHOW`, next page off | `:1215-1221`, `:1233-1240` | FR-S04-08 |
| COCRDLIC-09 | PF8 again on last page | `NO MORE PAGES TO DISPLAY`, info blank | `:410-414`, `:905-909` | FR-S04-09 |
| COCRDLIC-10 | PF7, not page 1 | page−1 with the 7 preceding matching rows | `:501-513`, `:1264-1372` | FR-S04-10 |
| COCRDLIC-11 | PF7 on page 1 | re-list, `NO PREVIOUS PAGES TO DISPLAY`, info blank | `:439-454`, `:901-904` | FR-S04-11 |
| COCRDLIC-12 | Select ∉ {S,U,blank} | `INVALID ACTION CODE`, row red | `:1108-1113` | FR-S04-12 |
| COCRDLIC-13 | >1 S/U | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`, S/U rows red | `:1079-1105` | FR-S04-13 |
| COCRDLIC-14 | selection error | re-read from uninitialized key (file start), page number kept, codes echoed | `:300`, `:419-438`, `:683` | FR-S04-14 |
| COCRDLIC-15 | one `S` + ENTER | hand-off COCRDSLC with acct/card | `:517-541` | FR-S04-15 |
| COCRDLIC-16 | one `U` + ENTER | hand-off COCRDUPC with acct/card | `:545-569` | FR-S04-16 |
| COCRDLIC-17 | PF3 | main menu | `:384-406` | FR-S04-17 |
| COCRDLIC-18 | other AID | treated as ENTER | `:370-380` | FR-S04-18 |
| COCRDLIC-19 | display | info `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` rules | `:895-931` | FR-S04-19 |
| COCRDLIC-20 | redisplay | filter echo, cursor placement | `:837-889` | FR-S04-20 |
| COCRDLIC-21 | display | Select protected on empty rows / filter error | `:748-832` | FR-S04-21 |
| COCRDLIC-22 | READPREV exhausted | bottom-aligned partial rows, first anchor kept, file-error text | `:1338-1369` | FR-S04-22 |
| COCRDLIC-23 | access | signed-on session required | `:315-332` | FR-S04-23 |

## 5. Business rules and validations
Order (`:985-997`): account filter → card filter → selection array (skipped when a filter failed, `:1075-1077`). Filter "supplied" = not LOW-VALUES/SPACES/zero; must pass `NUMERIC` on the full field. Selection: count S/U (>1 ⇒ error), then per-row code check; selected row = last S/U index (`:1099-1102`). Every AID except PF8 resets "last page shown" (`:410-414`). Dispatch order (`:418-583`): INPUT-ERROR → PF7@page1 → PF3/re-enter from elsewhere → PF8&next → PF7 → ENTER+S → ENTER+U → OTHER.

## 6. Data access and boundaries
`CARDDAT` KSDS via STARTBR GTEQ / READNEXT / READPREV / ENDBR on `CARD-NUM` (`:1129-1136`, `:1146-1154`, `:1273-1280`, `:1294-1302`, `:1322-1330`). Columns read: CARD-NUM, CARD-ACCT-ID, CARD-ACTIVE-STATUS (`CVACT02Y.cpy`). `CARDAIX` unused. Boundaries S04-B1..B4 (analysis §6).

## 7. Error and edge behavior
- Look-ahead after row 7 is unfiltered (`:1197-1214`): next-page flag may be set with no further matching rows → PF8 shows an empty page + `NO MORE RECORDS TO SHOW`.
- `WS-CA-SCREEN-NUM` PIC 9(1): wraps at 10, `0−1` stores 1; set to 1 when 0 at row 1 (`:1177-1178`).
- READPREV `WHEN OTHER` (incl. ENDFILE) → `File Error: READ     on CARDDAT   returned RESP <9 digits> ,RESP2 <9 digits>` truncated to 75 (`:153-171`, `:1361-1369`).
- STARTBR RESP is not checked (`:1129-1136`); a failing STARTBR surfaces as a READ file error on the first READNEXT.
- `PF03 PRESSED.EXITING` is set but never rendered (`:396`).

## 8. Hard-stop boundary
Owns listing, filtering, paging, selection edits, messaging and hand-off context only. Card detail (COCRDSLC) and card update (COCRDUPC) are other streams and stay behind the disabled route registry.

## 9. Demoted mechanics
SEND/RECEIVE plumbing, COMMAREA copy, AID copybook, header date/time formatting, attribute bytes, dead `'*'` marker branch (`:757-759`), `WS-CA-LAST-CARD-NUM` value after ENDFILE (never consumed).

## 10. Traceability
COCRDLIC-01..23 ↔ FR-S04-01..23 ↔ `CardListServiceTests`, `CardListIntegrationTests`, `card-list.component.spec.ts`.
