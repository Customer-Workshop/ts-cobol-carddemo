# S-06 Card Update — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Process type **ONLINE**. Batch A stream; built on the shared data layer landed on `devin/1787242078-carddemo-premigration` (commit 468e17d).
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md` (C#/.NET 8 + ASP.NET Core, Angular 18, PostgreSQL 16, single repo). S-01 conventions reused, never forked: JWT `SessionContext`, `authGuard`, menu route registry, exact message parity, Controller/Service/Repository layering.

## 1. Pinned stream

- **Entry point (proof)**: `CCUP -> COCRDUPC` (`app/csd/CARDDEMO.CSD:367-369`); program defined `:227`. Main-menu option 05 "Credit Card Update" (`app/cpy/COMEN02Y.cpy:52`), user type 'U' — **no admin restriction**.
- **Inbound callers**: main menu COMEN01C (XCTL, S-01) and card list COCRDLIC (`app/cbl/COCRDLIC.cbl:204-206` XCTL with account/card keys in the COMMAREA; S-04, not migrated).
- **Outbound XCTL (hard stop)**: PF3 → `CDEMO-FROM-PROGRAM` or `LIT-MENUPGM` COMEN01C (`COCRDUPC.cbl:435-478`); after a completed/failed update when the caller was the list → COCRDLIC (`:480-503`). Both are cross-stream edges: menu = S-01 route `/menu`; list = disabled route registry entry (S-04).
- **Pseudo-conversational shape**: `EXEC CICS RETURN TRANSID(CCUP) COMMAREA(...)` (`:554-561`) carrying a program-private COMMAREA extension `WS-THIS-PROGCOMMAREA` (`:284-311`: change-action state, OLD and NEW card images).

## 2. Program inventory + DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COCRDUPC | app/cbl/COCRDUPC.cbl | entry / validate / update (card detail edit) | CICS READ CARDDAT (`:1382`), READ UPDATE + REWRITE CARDDAT (`:1427`, `:1478`); XCTL COMEN01C (`:473`), COCRDLIC (`:496`) | consumes shared card data layer | yes |

Single-program stream: the DAG is `COCRDUPC -> CARDDAT` (leaf). No subroutine CALLs, no absent programs. Copybooks: `COCOM01Y` (COMMAREA), `CVACT02Y` (CARD-RECORD), `CVCRD01Y` (CC-ACCT-ID/CC-CARD-NUM work area), `CSSTRPFY` (AID mapping), `COCRDUP` map copybook.

## 3. Surface (ONLINE) — screen CCRDUPA / mapset COCRDUP (`app/bms/COCRDUP.bms`)

| Field | I/O | PIC | POS | Edits / attributes (cite) |
|---|---|---|---|---|
| ACCTSID | INPUT (search) | X(11) | (7,45) | mandatory when searching — "Account number not provided" (`:721-738`); all 11 chars must be digits — "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" (`:740-746`); '*' on input = blank (`:589-591`); protected once details are fetched (`:1180-1196`) |
| CARDSID | INPUT (search) | X(16) | (8,45) | mandatory — "Card number not provided" (`:762-779`); 16 digits — "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" (`:784-790`); '*' = blank (`:598-600`) |
| CRDNAME | INPUT (edit) | X(50) | (11,25) | mandatory — "Card name not provided" (`:806-823`); alphabets and spaces only — "Card name can only contain alphabets and spaces" (`:825-838`); protected until fetched, protected again once validated (`:1186-1231`) |
| CRDSTCD | INPUT (edit) | X(1) | (13,25) | must be 'Y' or 'N' (upper case only, `:91`) — "Card Active Status must be Y or N" for blank and for any other value (`:845-870`) |
| EXPMON | INPUT (edit) | X(2) | (15,25) | `JUSTIFY=(RIGHT)` → BMS zero-fills on the left (`bms:129`); blank or not 01..12 — "Card expiry month must be between 1 and 12" (`:877-908`, 88 `VALID-MONTH` `:95`) |
| EXPYEAR | INPUT (edit) | X(4) | (15,30) | `JUSTIFY=(RIGHT)` zero fill (`bms:137`); blank or not 1950..2099 — "Invalid card expiry year" (`:913-943`, 88 `VALID-YEAR` `:99`) |
| EXPDAY | DISPLAY (dark, protected, FSET) | X(2) | (15,36) | never editable; round-trips from the fetched record into the rewrite (`:636-637`, `:1466-1472`) |
| INFOMSG | DISPLAY | X(40) | (20,25) | state prompt (`3250-SETUP-INFOMSG` `:1138-1164`) |
| ERRMSG | DISPLAY | X(80) | (23,1) | `WS-RETURN-MSG` X(75) (`:1092`) |
| FKEYS / FKEYSC | DISPLAY | — | (24,1)/(24,40) | "ENTER=Process F3=Exit" always; "F5=Save F12=Cancel" bright only while awaiting confirmation (`:1310-1316`) |

### 3.1 State machine (`CCUP-CHANGE-ACTION`, `:288-296`)

| Value | 88 name | Meaning | Screen attributes (`:1168-1236`) | INFOMSG (`:1138-1164`) |
|---|---|---|---|---|
| low-values/spaces | `CCUP-DETAILS-NOT-FETCHED` | search screen | account + card editable, details protected | "Please enter Account and Card Number" |
| `S` | `CCUP-SHOW-DETAILS` | record fetched | account + card protected, details editable | "Details of selected card shown above" |
| `E` | `CCUP-CHANGES-NOT-OK` | edit errors | as `S` | "Update card details presented above." |
| `N` | `CCUP-CHANGES-OK-NOT-CONFIRMED` | validated, awaiting F5 | everything protected, F5/F12 legend shown | "Changes validated.Press F5 to save" |
| `C` | `CCUP-CHANGES-OKAYED-AND-DONE` | rewritten | everything protected | "Changes committed to database" |
| `L` / `F` | `CCUP-CHANGES-FAILED` | lock failed / rewrite failed | account + card editable (OTHER branch) | "Changes unsuccessful. Please try again" |

Displayed values: OLD image while `S` (`:1100-1117`), NEW image (as typed) while `E`/`N`/`C`/`L`/`F` (`:1118-1135`). Blank fields in error are shown as `*` (`:1245-1307`); the search fields only when `CDEMO-PGM-REENTER`.

### 3.2 AID handling (`:407-424`)
Valid: ENTER, PF3 always; PF5 only in state `N`; PF12 only when details have been fetched. **Every other AID (including F1/F2/F4/F6–F11 and PF5/PF12 outside their window) is silently remapped to ENTER** (`:422-424`). There is no "Invalid key pressed" message in this program — see §7 risk 1.

### 3.3 Main flow (`:429-543`)
1. PF3 → XCTL to caller/menu (`:435-478`).
2. State `C`/`L`/`F` with caller = list, or PF12 with caller = list → XCTL back to COCRDLIC (`:480-503`).
3. Fresh entry from the menu / no COMMAREA → initialise, `CDEMO-PGM-ENTER`, empty screen (`:504-509`).
4. Entry from the list (`CDEMO-PGM-ENTER` + caller COCRDLIC) → read with the keys already in the COMMAREA, state `S` (`:510-516`).
5. State `C`/`L`/`F` + ENTER → reset everything and show the fresh search screen (`:517-528`).
6. Otherwise → `1000-PROCESS-INPUTS` then `2000-DECIDE-ACTION` (`:530-533`).

### 3.4 Input processing and validation order (`1000`/`1100`/`1200`, `:564-715`)
- RECEIVE MAP; `*` or spaces in any field → low-values (`:589-635`).
- **Not fetched**: `1210-EDIT-ACCOUNT` then `1220-EDIT-CARD`; if both blank the message is overridden by "No input received" (`:646-663`). First error message wins otherwise (`WS-RETURN-MSG-OFF` guards, e.g. `:732`).
- **Fetched**: copy OLD keys to the COMMAREA; if `UPPER(NEW) = UPPER(OLD)` over name+year+month+day+status → "No change detected with respect to values fetched." (`:680-683`). No edits when no changes, or when already in `N`/`C` (`:685-688`). Else state ← `E` and run `1230` name → `1240` status → `1250` month → `1260` year, all four (no short-circuit); if none failed state ← `N` (`:690-712`).

### 3.5 Action decision (`2000`, `:948-1027`)
- Not fetched, or PF12: if both search keys valid → `9000-READ-DATA`; found → `S`. PF12 therefore re-reads the record and abandons the typed changes **but keeps whatever message the edit pass produced** (edits run before the decision).
- `S` with no changes / error → redisplay; `E` → redisplay with errors; `N` + PF5 → `9200-WRITE-PROCESSING`; `N` + ENTER → redisplay the confirmation.

### 3.6 Data access
- `9100-GETCARD-BYACCTCARD` (`:1376-1412`): `READ CARDDAT RIDFLD(card number)` — **the account id is only format-checked, never matched to the card** (account-path read is commented out `:1379-1380`). RESP NORMAL → OLD image (name upper-cased `:1354-1358`); NOTFND → "Did not find cards for this search condition", both search fields flagged; other RESP → `WS-FILE-ERROR-MESSAGE` ("File Error: READ     on CARDDAT   returned RESP nnnnnnnnn ,RESP2 nnnnnnnnn ", `:1401-1409`, `:146-158`).
- `9200-WRITE-PROCESSING` (`:1420-1495`): `READ UPDATE` (any failure → "Could not lock record for update", state `L`); `9300-CHECK-CHANGE-IN-REC` compares CVV, upper-cased name, year, month, day, status of the just-locked record with the OLD image (`:1498-1519`) — mismatch → "Record changed by some one else. Please review", OLD image refreshed, state `S`; REWRITE from `CARD-UPDATE-RECORD`; failure → "Update of record failed", state `F`; success → `C`.

## 4. Data + field dictionary

Dataset CARDDAT (`app/cpy/CVACT02Y.cpy`, RECLN 150), keyed by CARD-NUM; already ported by the shared data layer as `cards` (`CardDemo.Domain.Cards.Card`, `CardConfiguration`). No new columns or indexes are needed by this stream.

| COBOL field | PIC | Target | Used by COCRDUPC |
|---|---|---|---|
| CARD-NUM | X(16) | `Card.CardNumber` PK | key |
| CARD-ACCT-ID | 9(11) | `Card.AccountId` | displayed / rewritten (see D1) |
| CARD-CVV-CD | 9(03) | `Card.CvvCode` | concurrency compare / rewritten (see D1) |
| CARD-EMBOSSED-NAME | X(50) | `Card.EmbossedName` | edited |
| CARD-EXPIRAION-DATE | X(10) `yyyy-MM-dd` | `Card.ExpirationDate` (`DateOnly?`) | year/month edited, day carried |
| CARD-ACTIVE-STATUS | X(01) | `Card.ActiveStatus` | edited |

Conversational state: `WS-THIS-PROGCOMMAREA` (`:284-311`) = change-action + OLD image + NEW image. Target: the Angular component holds this screen state and sends it with every request (stateless API), mirroring the COMMAREA round trip.

## 5. Boundary table (headline) — S06-B1..S06-B4 (not written to `.migration/`, per scope rule)

| ID | Class | Contract | Direction | Cite | Decision |
|---|---|---|---|---|---|
| S06-B1 | B4 data-access leaf | CICS READ / READ UPDATE / REWRITE on CARDDAT with RESP protocol; expiry stored as text `yyyy-MM-dd` | outbound | `:1382`, `:1427`, `:1478` | EF Core repository extension `ICardRepository.RewriteAsync` (row lock + compare-then-rewrite inside one transaction). Target `DateOnly` column rejects calendar-invalid month/day combinations the VSAM text field accepted → surfaced as the REWRITE-failure path ("Update of record failed"). Store exceptions on read render the legacy file-error template with RESP 17 (IOERR analogue), RESP2 0 |
| S06-B2 | B5 inbound switch | COCRDLIC passes account + card in the COMMAREA and expects a return XCTL | inbound | `:483-503`, `:510-516`, `COCRDLIC.cbl:204-206` | Route `/cards/update?acctId=&cardNum=` auto-fetches (equivalent to the list-entry read). Return-to-list stays behind the disabled S-04 registry entry: exit goes to `/menu`, post-update ENTER resets the screen |
| S06-B3 | B5 outbound switch | PF3 → COMEN01C | outbound | `:435-478` | S-01 route `/menu` |
| S06-B4 | B10 shared data contract | CARDDAT is read by S-04/S-05 and written here and by batch | both | `CVACT02Y.cpy` | Shared `cards` table + repository; this stream is the only online writer; single-writer decision remains with the data-layer owner |

## 6. Waves

| Wave | Content | Repos touched |
|---|---|---|
| 1 | `ICardRepository.RewriteAsync` (lock + rewrite), `CardUpdateService` state machine + edits, `POST /api/v1/cards/update`, unit + Testcontainers tests | backend/ |
| 2 | `CardUpdateComponent` (CCRDUPA), route `/cards/update` (authGuard), specs | frontend/ |

Both waves land in this one branch (single-program stream).

## 7. Risks / source quirks carried into FRs

1. **AID parity differs from S-01**: unmapped function keys act as ENTER here (`:422-424`) rather than raising "Invalid key pressed..." — the S-01 helper is not applicable; kept source-faithful. LOW.
2. **Source defect — rewrite overwrites unedited fields**: `CCUP-NEW-CVV-CD` is never populated (`INITIALIZE` `:586`) and `CARD-UPDATE-ACCT-ID` is taken from the typed account (`:1463-1465`), so every legacy save writes CVV `000` and the typed account id. Target preserves the stored CVV and account id (deviation D1, FR doc §11). MEDIUM.
3. Account id is never matched to the card (`:1379-1380`) — a valid 11-digit id plus any existing card number fetches that card. Kept as-is (source behaviour). LOW.
4. PF12 keeps the edit-pass message on the refreshed screen (`:669-712` then `:958-965`). Kept as-is. LOW.
5. Concurrency compare includes CVV (`:1507`); the browser never sees CVV, so the target compares the five displayed fields only (deviation D2). LOW.

## 8. Validation
(1) 1/1 program inventoried, entry → hard stop; (2) waves are a topological order (data seam before UI); (3) claims cited `<file>:<line>`; (4) surface is ONLINE only; (5) all crossings in §5 with contracts; (6) sole data leaf S06-B1 physical layer resolved (VSAM → shared Postgres `cards`).
