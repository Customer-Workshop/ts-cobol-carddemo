# S-06 Card Update — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Derived from `S06_card_update_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; cites are raw line numbers of `app/cbl/COCRDUPC.cbl` unless prefixed.

## 1. Purpose and scope
Let a signed-on user locate one credit card by account number + card number, edit its embossed name, active status and expiry month/year, validate the edits, confirm with F5 and rewrite the card record with optimistic-concurrency protection. Process type ONLINE. Trigger: CICS transaction CCUP (`app/csd/CARDDEMO.CSD:367-369`), main-menu option 05 (`app/cpy/COMEN02Y.cpy:52`). Hard stop: PF3 / return transfers to COMEN01C (S-01) and COCRDLIC (S-04). Exclusions: card list (COCRDLIC), card view (COCRDSLC).

## 2. Actors and preconditions
- Actor: any signed-on user; `CDEMO-USER-TYPE` is not inspected by COCRDUPC (no admin restriction).
- Precondition: CARDDAT file open (`LIT-CARDFILENAME` `'CARDDAT '` `:231-232`), keyed by 16-digit card number (`app/cpy/CVACT02Y.cpy`).
- Entry either from the menu with an empty screen (`:504-509`) or from the card list with account + card already in the COMMAREA (`:510-516`, seam S06-B2).

## 3. Surface specification
### Card update screen CCRDUPA (`app/bms/COCRDUP.bms`)
| Field | Type | Length | Behaviour |
|---|---|---|---|
| Account Number (ACCTSID) | input, `IC` cursor default | 11 | search key; protected once details are fetched |
| Card Number (CARDSID) | input | 16 | search key; protected once fetched |
| Name on card (CRDNAME) | input | 50 | editable only in states S/E |
| Card Active Y/N (CRDSTCD) | input | 1 | editable only in states S/E |
| Expiry Date month (EXPMON) | input, right-justified zero-filled | 2 | editable only in states S/E |
| Expiry Date year (EXPYEAR) | input, right-justified zero-filled | 4 | editable only in states S/E |
| Expiry Date day (EXPDAY) | dark, protected | 2 | carried from the fetched record |
| INFOMSG | display | 40 | state prompt |
| ERRMSG | display, bright | 80 | validation / file message |
| FKEYS | display | — | `ENTER=Process F3=Exit`; `F5=Save F12=Cancel` shown only in state N (`:1310-1316`) |

Screen states and attributes: see analysis §3.1. Blank fields that fail validation are redisplayed as `*` (`:1245-1307`), and `*` typed back in is treated as blank (`:589-635`).

## 4. Functional requirements (KEEP)
| ID | Category | Condition | Behaviour | Program | Cite | Boundary | Test |
|---|---|---|---|---|---|---|---|
| FR-S06-01 | Screen | First entry from the menu (or no COMMAREA) | Empty screen, search fields editable, detail fields protected, info `Please enter Account and Card Number`, cursor on account | COCRDUPC | `:504-509`, `:1145-1147`, `:1172-1178` | S06-B3 | unit + spec |
| FR-S06-02 | AID | Any key other than ENTER, PF3, PF5 (state N only), PF12 (details fetched only) | Treated exactly as ENTER; no invalid-key message exists in this program | COCRDUPC | `:413-424` | — | unit + spec |
| FR-S06-03 | Navigation | PF3 in any state | Transfer to the calling program (menu) — target route `/menu` | COCRDUPC | `:435-478` | S06-B3 | spec |
| FR-S06-04 | Search validation | ENTER while not fetched, account blank/zero/`*` | Error `Account number not provided`, account flagged (shown as `*`), cursor on account | COCRDUPC | `:721-738`, `:1245-1250` | — | unit |
| FR-S06-05 | Search validation | Account not exactly 11 digits (shorter values are space-padded and fail `IS NUMERIC`) | Error `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, account flagged | COCRDUPC | `:740-748` | — | unit |
| FR-S06-06 | Search validation | Card blank/zero/`*` | Error `Card number not provided` (only if no earlier message), card flagged | COCRDUPC | `:762-779` | — | unit |
| FR-S06-07 | Search validation | Card not exactly 16 digits | Error `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | COCRDUPC | `:784-792` | — | unit |
| FR-S06-08 | Search validation | Both search fields blank | Error `No input received` overrides the individual blank messages | COCRDUPC | `:657-663` | — | unit |
| FR-S06-09 | Search validation | Account fails and card fails | Only the first (account) message is shown; both fields flagged; cursor on account | COCRDUPC | `:732`, `:776`, `:1232-1244` | — | unit |
| FR-S06-10 | Lookup | Valid keys, card number exists | Card read by card number only (account not matched); OLD image captured with upper-cased name; state S; details editable, search fields protected; info `Details of selected card shown above`; cursor on name | COCRDUPC | `:951-957`, `:1343-1412`, `:1354-1358` | S06-B1 | unit + integration |
| FR-S06-11 | Lookup | Valid keys, card not found | Error `Did not find cards for this search condition`; both search fields flagged; stays not fetched | COCRDUPC | `:1395-1400` | S06-B1 | unit + integration |
| FR-S06-12 | Lookup | Other read failure | Error `File Error: READ     on CARDDAT   returned RESP nnnnnnnnn ,RESP2 nnnnnnnnn ` (75-char template); both search fields flagged | COCRDUPC | `:146-158`, `:1401-1409` | S06-B1 | unit |
| FR-S06-13 | Edit | ENTER in S/E, no field differs from OLD (case-insensitive over name, year, month, day, status) | Error `No change detected with respect to values fetched.`; OLD image redisplayed; state stays S | COCRDUPC | `:680-688`, `:967-975` | — | unit |
| FR-S06-14 | Edit | Name blank | Error `Card name not provided`; name flagged / `*` | COCRDUPC | `:806-823` | — | unit |
| FR-S06-15 | Edit | Name contains anything other than letters and spaces | Error `Card name can only contain alphabets and spaces` | COCRDUPC | `:825-838` | — | unit |
| FR-S06-16 | Edit | Status blank or not `Y`/`N` (upper case only) | Error `Card Active Status must be Y or N` | COCRDUPC | `:91`, `:845-870` | — | unit |
| FR-S06-17 | Edit | Month blank, non-numeric or not 01..12 (single digit is zero-filled by BMS JUSTIFY=RIGHT) | Error `Card expiry month must be between 1 and 12` | COCRDUPC | `:95`, `:877-908`; `bms:129` | — | unit |
| FR-S06-18 | Edit | Year blank, non-numeric or not 1950..2099 (zero-filled, so `25` → `0025` fails) | Error `Invalid card expiry year` | COCRDUPC | `:99`, `:913-943`; `bms:137` | — | unit |
| FR-S06-19 | Edit | Several fields invalid | All four edits run; first message in order name → status → month → year is shown; all failing fields flagged; cursor on the first failing field; state E; info `Update card details presented above.`; NEW values redisplayed | COCRDUPC | `:690-712`, `:1153-1154`, `:1222-1244` | — | unit |
| FR-S06-20 | Confirm | All edits pass | State N; everything protected; info `Changes validated.Press F5 to save`; `F5=Save F12=Cancel` legend | COCRDUPC | `:706-712`, `:1155-1156`, `:1310-1316` | — | unit + spec |
| FR-S06-21 | Confirm | ENTER while in N | Screen redisplayed unchanged in N | COCRDUPC | `:685-688`, `:1015-1020` | — | unit |
| FR-S06-22 | Save | PF5 in N, record locked, unchanged since fetch | Rewrite name (as typed), status, `year-month-<old day>`; state C; info `Changes committed to database`; everything protected | COCRDUPC | `:985-1013`, `:1420-1495` | S06-B1 | unit + integration |
| FR-S06-23 | Save | PF5 in N, record differs from OLD image (name case-insensitive, year, month, day, status) | Error `Record changed by some one else. Please review`; OLD image refreshed from the current record; state S | COCRDUPC | `:1449-1454`, `:1498-1519` | S06-B1 | unit + integration |
| FR-S06-24 | Save | READ UPDATE fails (record gone / store error) | Error `Could not lock record for update`; state L; info `Changes unsuccessful. Please try again` | COCRDUPC | `:1436-1447` | S06-B1 | unit + integration |
| FR-S06-25 | Save | REWRITE fails | Error `Update of record failed`; state F; info `Changes unsuccessful. Please try again` | COCRDUPC | `:1487-1491` | S06-B1 | unit |
| FR-S06-26 | Reset | ENTER after state C, L or F (caller = menu) | Everything re-initialised; fresh search screen as FR-S06-01 | COCRDUPC | `:517-528` | — | unit + spec |
| FR-S06-27 | Cancel | PF12 with details fetched | Record re-read; OLD image redisplayed in state S; message produced by the preceding edit pass (if any) is kept | COCRDUPC | `:958-965`, `:669-712` | S06-B1 | unit |
| FR-S06-28 | Entry from list | Called with account + card already known | Record read immediately and shown in state S (target: `/cards/update?acctId=&cardNum=`) | COCRDUPC | `:510-516`, `COCRDLIC.cbl:204-206` | S06-B2 | spec |
| FR-S06-29 | Screen | Field lengths | Inputs limited to 11 / 16 / 50 / 1 / 2 / 4 characters; day is read-only | COCRDUPC | `bms:84-146` | — | spec |

## 5. Validation and error catalogue
| Message (exact) | Trigger | Cite |
|---|---|---|
| `Account number not provided` | FR-S06-04 | `:178` |
| `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | FR-S06-05 | `:745` |
| `Card number not provided` | FR-S06-06 | `:180` |
| `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | FR-S06-07 | `:789` |
| `No input received` | FR-S06-08 | `:186` |
| `No change detected with respect to values fetched.` | FR-S06-13 | `:188` |
| `Card name not provided` | FR-S06-14 | `:182` |
| `Card name can only contain alphabets and spaces` | FR-S06-15 | `:184` |
| `Card Active Status must be Y or N` | FR-S06-16 | `:196` |
| `Card expiry month must be between 1 and 12` | FR-S06-17 | `:198` |
| `Invalid card expiry year` | FR-S06-18 | `:200` |
| `Did not find cards for this search condition` | FR-S06-11 | `:204` |
| `File Error: READ     on CARDDAT   returned RESP ...` | FR-S06-12 | `:146-158` |
| `Could not lock record for update` | FR-S06-24 | `:206` |
| `Record changed by some one else. Please review` | FR-S06-23 | `:208` |
| `Update of record failed` | FR-S06-25 | `:210` |
| Info `Please enter Account and Card Number` | states not fetched / fresh | `:163` |
| Info `Details of selected card shown above` | state S | `:161` |
| Info `Update card details presented above.` | state E | `:165` |
| Info `Changes validated.Press F5 to save` | state N | `:167` |
| Info `Changes committed to database` | state C | `:169` |
| Info `Changes unsuccessful. Please try again` | states L / F | `:171` |
Declared but unreachable in this program: `Did not find this account in cards database` (`:202`), `Error reading Card Data File` (`:212`).

## 6. Field and data derivations
- OLD image: `CARD-NUM`, `CARD-ACCT-ID`, `CARD-CVV-CD`, `UPPER(CARD-EMBOSSED-NAME)`, date split `(1:4)` year `(6:2)` month `(9:2)` day, `CARD-ACTIVE-STATUS` (`:1349-1366`).
- NEW image: typed name/status/month/year + day from the screen (= OLD day) (`:605-637`).
- Rewritten date: `STRING year '-' month '-' day` (`:1466-1472`); target composes `DateOnly(year, month, oldDay)`.
- Month/year inputs are zero-filled on the left to their BMS length before validation (`JUSTIFY=(RIGHT)`, `bms:129,137`).
- Displayed name is the upper-cased OLD value in state S and the typed NEW value otherwise (`:1100-1135`).

## 7. Mechanics (demoted, cited)
BMS SEND/RECEIVE (`:579`, `:1329`), COMMAREA round trip (`:554-561`), cursor positioning via `-1` lengths (`:1232-1244`), colour attributes red/default (`:1245-1307`), header date/time/transaction/program population (`3100-SCREEN-INIT`), `CDEMO-LAST-MAPSET` bookkeeping (`:466-470`, `:1320-1326`).

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S06-01: Given a signed-on user opens the screen, When nothing has been entered, Then the info prompt shows, account/card are editable and the detail fields are disabled.
- FR-S06-02: Given any state, When F7 is pressed, Then the same request as ENTER is processed and no invalid-key message appears.
- FR-S06-03: Given any state, When F3 / Exit, Then the app navigates to `/menu`.
- FR-S06-04..09: Given the search screen, When the listed inputs are entered, Then the listed exact message is returned, the listed fields are flagged and the state stays not fetched.
- FR-S06-10: Given a valid 11-digit account and an existing 16-digit card, When ENTER, Then the OLD image is returned with the upper-cased name and state S.
- FR-S06-11/12: Given valid keys, When the card is absent / the store fails, Then the exact message is returned and both search fields are flagged.
- FR-S06-13..19: Given state S, When the listed edits are submitted, Then the listed message, flags, cursor and state result.
- FR-S06-20/21: Given state S with valid changes, When ENTER (twice), Then state N with the confirmation prompt both times.
- FR-S06-22: Given state N, When F5 and the record is unchanged, Then the row holds the new name/status/`yyyy-MM-dd` with the old day, and state C is returned.
- FR-S06-23: Given state N, When F5 and another writer changed the record, Then the changed-by-someone message, the fresh OLD image and state S.
- FR-S06-24/25: Given state N, When the lock / rewrite fails, Then the exact message and state L / F with the failure prompt.
- FR-S06-26: Given state C/L/F, When ENTER, Then the fresh search screen.
- FR-S06-27: Given state S/E/N, When F12, Then the current record is re-read and shown in state S.
- FR-S06-28: Given `/cards/update?acctId=…&cardNum=…`, When the component loads, Then the fetch request is issued and the card displayed.
- FR-S06-29: Given the screen, Then the inputs carry the BMS maxlengths and the day field is read-only.

## 9. Traceability matrix
| FR | Backend | Frontend |
|---|---|---|
| 01, 26 | `CardUpdateService` fresh-screen path | `CardUpdateComponent` initial/reset render |
| 02 | `CardUpdateService.NormaliseAid` | `classifyCardUpdateKey` |
| 03, 28, 29 | — | component + route |
| 04–09 | `CardUpdateService.EditAccount/EditCard` | message area |
| 10–12 | `CardUpdateService.ReadCard` + `ICardRepository.GetByCardNumberAsync` | display |
| 13–21 | `CardUpdateService.EditChanges` | display / attributes |
| 22–25 | `CardUpdateService.Save` + `ICardRepository.RewriteAsync` | display |
| 27 | `CardUpdateService` cancel path | F12 handler |

## 10. Program index
| Program | Doc |
|---|---|
| COCRDUPC | `programs/COCRDUPC_functional_requirement.md` |

## 11. Open questions and assumptions
- **D1 (deviation, approved pattern "do not propagate source defects that corrupt data")**: the legacy rewrite stores CVV `000` (`CCUP-NEW-CVV-CD` is never assigned, `:586`, `:1464-1465`) and the *typed* account id (`:1463`). Target rewrites only name, status and expiry date; CVV and account id are preserved.
- **D2 (deviation)**: the concurrency compare omits CVV (`:1507`) because the browser never receives it; the five displayed fields are compared.
- **D3 (storage)**: the expiry date is a `date` column (`DateOnly`); a month/day combination that is not a calendar date (e.g. day 31 with month 02) is reported through the REWRITE-failure path FR-S06-25 instead of being stored as text.
- Assumption: RESP/RESP2 in FR-S06-12 have no target equivalent; the template is filled with `000000017` (IOERR analogue) and `000000000`.
- Assumption: the legacy "return to the card list" after an update (`:480-503`) is unreachable until S-04 migrates; the target resets the screen (FR-S06-26) and exits to `/menu`.
