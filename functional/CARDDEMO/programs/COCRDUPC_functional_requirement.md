# COCRDUPC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COCRDUPC — `app/cbl/COCRDUPC.cbl`. Stream S-06, single program (waves 1 backend / 2 frontend).
- Role: entry / validator / updater — credit card detail update screen: search by account + card, edit name/status/expiry, confirm, rewrite with change detection.

## 2. Trigger / caller contract
- CICS transaction `CCUP` (`app/csd/CARDDEMO.CSD:367-369`). Entered from the main menu option 05 (`app/cpy/COMEN02Y.cpy:52`) with `CARDDEMO-COMMAREA`, or from the card list COCRDLIC with `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` populated (`COCRDLIC.cbl:204-206`; `COCRDUPC.cbl:510-516`).
- Re-entered pseudo-conversationally with `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` (change-action state, OLD image, NEW image) (`:284-311`, `:554-561`).
- Exits: PF3 → `CDEMO-FROM-PROGRAM` / COMEN01C (`:435-478`); after C/L/F or PF12 when the caller was the list → COCRDLIC (`:480-503`).

## 3. Inputs and outputs
Inputs (map CCRDUPA / mapset COCRDUP, `app/bms/COCRDUP.bms`): ACCTSIDI X(11), CARDSIDI X(16), CRDNAMEI X(50), CRDSTCDI X(1), EXPMONI X(2) (right-justified, zero fill), EXPYEARI X(4) (right-justified, zero fill), EXPDAYI X(2) (dark protected, FSET). AID key (EIBAID via CSSTRPFY).
Reads: CARDDAT `CARD-RECORD` (`app/cpy/CVACT02Y.cpy`) by `CARD-NUM` (`:1382-1391`).
Writes: CARDDAT REWRITE of `CARD-UPDATE-RECORD` (`:1460-1483`).
Outputs: all map fields, INFOMSGO X(40), ERRMSGO X(80), attribute/colour bytes, FKEYSC legend, cursor position, outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COCRDUPC-01 | first entry from menu | empty search screen, prompt | :504-509, :1145-1147 | FR-S06-01 |
| COCRDUPC-02 | unmapped AID / PF5 outside N / PF12 before fetch | processed as ENTER | :413-424 | FR-S06-02 |
| COCRDUPC-03 | PF3 | XCTL to caller (menu) | :435-478 | FR-S06-03 |
| COCRDUPC-04 | account blank | `Account number not provided` | :721-738 | FR-S06-04 |
| COCRDUPC-05 | account not 11 digits | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | :740-748 | FR-S06-05 |
| COCRDUPC-06 | card blank | `Card number not provided` | :762-779 | FR-S06-06 |
| COCRDUPC-07 | card not 16 digits | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | :784-792 | FR-S06-07 |
| COCRDUPC-08 | both search fields blank | `No input received` | :657-663 | FR-S06-08 |
| COCRDUPC-09 | both search fields invalid | first message only, both flagged | :732, :776 | FR-S06-09 |
| COCRDUPC-10 | valid keys, card exists | OLD image, state S | :951-957, :1343-1412 | FR-S06-10 |
| COCRDUPC-11 | card not found | `Did not find cards for this search condition` | :1395-1400 | FR-S06-11 |
| COCRDUPC-12 | other read RESP | file-error template | :1401-1409, :146-158 | FR-S06-12 |
| COCRDUPC-13 | no change vs OLD | `No change detected with respect to values fetched.` | :680-688 | FR-S06-13 |
| COCRDUPC-14 | name blank | `Card name not provided` | :806-823 | FR-S06-14 |
| COCRDUPC-15 | name non-alpha | `Card name can only contain alphabets and spaces` | :825-838 | FR-S06-15 |
| COCRDUPC-16 | status not Y/N | `Card Active Status must be Y or N` | :845-870 | FR-S06-16 |
| COCRDUPC-17 | month invalid | `Card expiry month must be between 1 and 12` | :877-908 | FR-S06-17 |
| COCRDUPC-18 | year invalid | `Invalid card expiry year` | :913-943 | FR-S06-18 |
| COCRDUPC-19 | multiple edit failures | first message, all flagged, state E | :690-712 | FR-S06-19 |
| COCRDUPC-20 | all edits pass | state N, F5/F12 legend | :706-712, :1310-1316 | FR-S06-20 |
| COCRDUPC-21 | ENTER in N | redisplay N | :685-688, :1015-1020 | FR-S06-21 |
| COCRDUPC-22 | PF5 in N, unchanged | REWRITE, state C | :985-1013, :1456-1495 | FR-S06-22 |
| COCRDUPC-23 | PF5 in N, record changed | `Record changed by some one else. Please review`, state S | :1449-1454, :1498-1519 | FR-S06-23 |
| COCRDUPC-24 | READ UPDATE fails | `Could not lock record for update`, state L | :1436-1447 | FR-S06-24 |
| COCRDUPC-25 | REWRITE fails | `Update of record failed`, state F | :1487-1491 | FR-S06-25 |
| COCRDUPC-26 | ENTER after C/L/F | reset to fresh screen | :517-528 | FR-S06-26 |
| COCRDUPC-27 | PF12 after fetch | re-read, state S, edit message kept | :958-965 | FR-S06-27 |
| COCRDUPC-28 | entry from list | immediate read, state S | :510-516 | FR-S06-28 |
| COCRDUPC-29 | screen | BMS field lengths, dark day | bms:84-146 | FR-S06-29 |

## 5. Business rules and validations
- Search pass (not fetched): account edit → card edit; both blank → `No input received`; first message wins; both run (`:646-663`, `:721-799`).
- Change pass (fetched): OLD keys copied to COMMAREA; case-insensitive no-change compare over name/year/month/day/status; skip edits when no change or state N/C; else state E and name → status → month → year, all run, first message wins, state N if none failed (`:665-712`).
- Blank detection: `*`, spaces or low-values (`:589-635`). Month/year zero-filled left by BMS before edit (`bms:129,137`).
- Name rule: after replacing A-Z/a-z with spaces the trimmed remainder must be empty (`:825-838`, alphabets + spaces only). Status rule: `Y`/`N` exactly (`:91`). Month 1..12, year 1950..2099 numerically (`:95`, `:99`); non-numeric text fails both (verified with GnuCOBOL).
- Cursor: FOUND/NO-CHANGES → name; else first flagged field in order account, card, name, status, month, year; default account (`:1232-1244`).

## 6. Data access and boundaries
- CARDDAT read by card number (S06-B1, DECIDED: shared Postgres `cards` + `ICardRepository.GetByCardNumberAsync`; store exception → COCRDUPC-12 template with RESP `000000017`).
- CARDDAT READ UPDATE + compare + REWRITE (S06-B1, DECIDED: `ICardRepository.RewriteAsync` — `SELECT … FOR UPDATE` inside one transaction, caller-supplied compare-then-mutate; not found / exception on lock → COCRDUPC-24; exception on save or calendar-invalid date → COCRDUPC-25).
- Inbound from list (S06-B2, DECIDED: query-parameter auto-fetch; return-to-list behind the disabled S-04 registry entry). Outbound to menu (S06-B3, DECIDED: `/menu`).
- **Deviation D1**: the legacy REWRITE stores CVV `000` (`CCUP-NEW-CVV-CD` never assigned, `:586`, `:1464-1465`) and the typed account id (`:1463`); target preserves stored CVV and account id (source defect, data-corrupting).
- **Deviation D2**: CVV excluded from the change-detection compare (`:1507`); five displayed fields compared.
- **Deviation D3 (storage)**: expiry stored as `date`; non-calendar month/day surfaces as COCRDUPC-25.

## 7. Error and edge behavior
- Account id is only format-checked, never matched against the card's account (`:1379-1380` commented out) — kept.
- PF12 runs the edit pass first, so its message survives onto the refreshed screen (`:669-712`, `:958-965`) — kept.
- States L/F redisplay the NEW image with the search fields editable (`:1118-1135`, `:1172-1178`); next ENTER resets (`:517-528`).
- `Did not find this account in cards database` (`:202`) and `Error reading Card Data File` (`:212`) are declared but never set.
- `Changes validated.Press F5 to save` has no space after the period — reproduced verbatim (`:167`).

## 8. Hard-stop boundary
Delegates card listing (COCRDLIC) and menu rendering (COMEN01C); does not own account/customer data.

## 9. Demoted mechanics
RECEIVE/SEND MAP with `CURSOR` (`:579`, `:1329-1339`); `-1` length cursor protocol; DFHBMPRF/DFHBMFSE attribute bytes and DFHRED colours (`:1168-1307`); header init (`3100-SCREEN-INIT`); RETURN TRANSID (`:554-561`); `CDEMO-LAST-MAPSET`/`LAST-MAP` bookkeeping; `ABEND-ROUTINE` (`:1534-1560`).

## 10. Traceability
COCRDUPC-01..29 ↔ FR-S06-01..29 (table §4) ↔ `backend/CardDemo.Tests/Cards/CardUpdateServiceTests.cs`, `CardUpdateIntegrationTests.cs`, `frontend/src/app/cards/card-update.component.spec.ts`.
