# COCRDSLC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COCRDSLC — `app/cbl/COCRDSLC.cbl`. Stream S-05, single wave.
- Role: entry/validator/display — credit-card detail screen: edits account + card number, keyed read of CARDDAT, displays name/expiry/status.

## 2. Trigger / caller contract
- CICS transaction `CCDL` (`app/csd/CARDDEMO.CSD:347-348`); reached from main-menu option 04 (`app/cpy/COMEN02Y.cpy:46`) or from the card list COCRDLIC with a pre-selected card (`app/cbl/COCRDLIC.cbl:519-540`).
- COMMAREA `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy:19-44`): FROM-PROGRAM, PGM-CONTEXT (enter/re-enter), CDEMO-ACCT-ID 9(11), CDEMO-CARD-NUM 9(16), LAST-MAPSET. Fresh init when `EIBCALEN = 0` or coming from COMEN01C on first entry (`:257-268`).
- Re-entered pseudo-conversationally with its own COMMAREA (`:394-406`).

## 3. Inputs and outputs
Inputs (map CCRDSLA / mapset COCRDSL, `app/bms/COCRDSL.bms`): ACCTSIDI X(11) (dict: cards.card_acct_id — echo only), CARDSIDI X(16) (dict: cards.card_num). AID key (EIBAID via `CSSTRPFY`).
Reads: CARDDAT KSDS `CARD-RECORD` (`app/cpy/CVACT02Y.cpy:16-23`) keyed by card number (`:740-750`).
Outputs: CRDNAMEO X(50), EXPMONO X(2), EXPYEARO X(4), CRDSTCDO X(1), INFOMSGO X(40), ERRMSGO X(80); field colour/cursor attributes; outgoing COMMAREA on PF3 XCTL.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COCRDSLC-01 | first entry from menu | empty screen, prompt info, cursor Account | :349-356, :457-460, :489-496 | FR-S05-01 |
| COCRDSLC-02 | account blank/`*`/zero | `Account number not provided`, Account `*` red, cursor Account | :613-620, :651-660, :541-545 | FR-S05-02 |
| COCRDSLC-03 | account not 11 numeric | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, Account cleared red | :665-674, :462-463, :533-535 | FR-S05-03 |
| COCRDSLC-04 | card blank/`*`/zero | `Card number not provided`, Card `*` red, cursor Card | :622-629, :691-701, :547-551 | FR-S05-04 |
| COCRDSLC-05 | card not 16 numeric | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`, Card cleared red | :706-715, :468-469, :537-539 | FR-S05-05 |
| COCRDSLC-06 | both blank | `No input received` | :637-640 | FR-S05-06 |
| COCRDSLC-07 | both fail, account message first | first message kept (`IF WS-RETURN-MSG-OFF`) | :656-658, :668-672, :696-698, :709-713 | FR-S05-07 |
| COCRDSLC-08 | all-zero input | treated as blank | :653, :693 | FR-S05-08 |
| COCRDSLC-09 | READ NORMAL | name, expiry MM/YYYY, status; `   Displaying requested details` | :752-754, :474-484 | FR-S05-09 |
| COCRDSLC-10 | READ NOTFND | `Did not find cards for this search condition`, both red | :755-761 | FR-S05-10 |
| COCRDSLC-11 | READ other RESP | `File Error: READ     on CARDDAT   returned RESP …,RESP2 …` | :762-771, :102-121 | FR-S05-11 |
| COCRDSLC-12 | key = card number only | account not compared with CARD-ACCT-ID | :740-750 | FR-S05-12 |
| COCRDSLC-13 | FROM-PROGRAM = COCRDLIC | edits skipped, COMMAREA keys read, inputs protected | :339-348, :505-508 | FR-S05-13 |
| COCRDSLC-14 | PF3 | XCTL to FROM-PROGRAM (fallback COMEN01C) | :305-334 | FR-S05-14 |
| COCRDSLC-15 | unmapped AID | remapped to ENTER, no message | :291-299 | FR-S05-15 |
| COCRDSLC-16 | screen layout | title, labels, lengths 11/16, footer | bms:78-152 | FR-S05-16 |

## 5. Business rules and validations
Sequence (`2200-EDIT-MAP-INPUTS`, `:608-641`): normalise `*`/spaces → blank → account edit (blank/zero → BLANK; not numeric → NOT-OK) → card edit (same) → both blank → `No input received`. Message slot is first-writer-wins except the both-blank override. Any failure blocks the read (`:360-363`). Read (`:736-773`): NORMAL / NOTFND / OTHER protocol. All blocking; no warnings.

## 6. Data access and boundaries
- CARDDAT read-only keyed read (S05-B1, **DECIDED**: shared Postgres `cards` + `ICardRepository.GetByCardNumberAsync`; exception → COCRDSLC-11 message). No writes, no commit scope.
- Inbound card-list hand-off (S05-B2, DECIDED: `/cards/view?accountId=&cardNumber=&returnUrl=`, `fromCardList=true` API flag).
- PF3 return (S05-B3, DECIDED: `returnUrl` else `/menu`).
- File-error diagnostic codes (S05-B4, DECIDED: frame verbatim, RESP = unsigned HResult, RESP2 = 0).
- Dead code: `9150-GETCARD-BYACCT` via CARDAIX (`:779-810`) — not ported.

## 7. Error and edge behavior
`*` and spaces both mean "not entered" (`:613-620`); BLANK fields redisplay as red `*` on re-entry (`:541-551`); NOT-OK fields are cleared because the COMMAREA key is zeroed (`:673`, `:714`, `:462-471`); NOTFND keeps both values and flags both red (`:757-758`); the account is never compared with the card's owner (`:740-750`); `CC-…-N EQUAL ZEROS` on non-numeric data is undefined — target treats any all-`0` value as blank, any other non-11/16-digit value as not numeric; RECEIVE MAP RESP unchecked (`:597-601`).

## 8. Hard-stop boundary
Delegates everything past PF3: card list (COCRDLIC/S-04) and main menu (COMEN01C/S-01). Not responsible for the account-path search (dead code) or card update.

## 9. Demoted mechanics
HANDLE ABEND / ABEND-ROUTINE (`:250-252`, `:857-880`); `YYYY-STORE-PFKEY` (`:284-285`); RETURN TRANSID (`:402-406`); header date/time (`:429-447`); ERASE/CURSOR/FREEKB send (`:569-576`); `SEND-PLAIN-TEXT`/`SEND-LONG-TEXT` (`:820-849`); `UNEXPECTED DATA SCENARIO` (`:373-380`); PF3 COMMAREA bookkeeping incl. `SET CDEMO-USRTYP-USER` (`:323-330`, deviation: JWT identity not downgraded).

## 10. Traceability
COCRDSLC-01..16 ↔ FR-S05-01..16 (table §4) ↔ `CardViewServiceTests`, `CardViewIntegrationTests`, `CardViewApiIntegrationTests`, `card-view.component.spec.ts`.
