# S-05 Card View — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Derived from `S05_card_view_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; cites are `<file>:<line>` into `app/cbl/COCRDSLC.cbl` unless another file is named.

## 1. Purpose and scope
Display the details of one credit card (embossed name, expiry month/year, active flag) for an account number + card number typed by the user, or pre-selected from the card list. Process type ONLINE. Trigger: CICS transaction CCDL (`app/csd/CARDDEMO.CSD:347-348`), main-menu option 04 (`app/cpy/COMEN02Y.cpy:46`). Hard stop: the PF3 transfer back to the caller (COMEN01C or COCRDLIC). Exclusions: COCRDLIC (S-04), COCRDUPC (S-06), dead account-path read `9150-GETCARD-BYACCT` (`:779-810`).

## 2. Actors and preconditions
- Actor: any signed-on user (no user-type gate anywhere in the program; option 04 is 'U'-accessible in `COMEN02Y.cpy`).
- Precondition: CARDDAT file open (`app/csd/CARDDEMO.CSD:25-26`); card records per `app/cpy/CVACT02Y.cpy:16-23`.
- Session: `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy:19-44`) carries FROM-PROGRAM, PGM-CONTEXT, CDEMO-ACCT-ID 9(11), CDEMO-CARD-NUM 9(16).

## 3. Surface specification
### Card detail screen CCRDSLA (`app/bms/COCRDSL.bms`)
| Field | Label (verbatim) | I/O | Len/PIC | Edits |
|---|---|---|---|---|
| ACCTSID | `Account Number    :` (bms:83) | INPUT | X(11), IC cursor (bms:84) | `*`/blank/zero → not provided (`:613-620`, `:651-660`); must be 11 numeric (`:665-674`); read-only when entered from the card list (`:505-508`) |
| CARDSID | `Card Number       :` (bms:95) | INPUT | X(16) (bms:96) | same shape (`:622-629`, `:691-701`, `:706-715`) |
| CRDNAME | `Name on card      :` (bms:106) | OUTPUT | X(50) | `CARD-EMBOSSED-NAME` (`:474-476`) |
| CRDSTCD | `Card Active Y/N   : ` (bms:115) | OUTPUT | X(1) | `CARD-ACTIVE-STATUS` (`:484`) |
| EXPMON / EXPYEAR | `Expiry Date       : ` (bms:125) | OUTPUT | X(2) / X(4) | month/year slices of `CARD-EXPIRAION-DATE` (`:477-482`; layout `:84-90`) |
| INFOMSG | — | OUTPUT | X(40) | `Please enter Account and Card Number` / `   Displaying requested details` (`:127-133`, `:489-496`) |
| ERRMSG | — | OUTPUT | X(80) (message X(75)) | error catalogue §5 |
| Header | `Tran:`/`Date:`/`Prog:`/`Time:` | OUTPUT | — | `:427-447` |
Title: `View Credit Card Detail` (bms:78). Footer: `ENTER=Search Cards  F3=Exit` (bms:152).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S05-01 | Initial display | Screen opened from the main menu (PGM-ENTER, no card-list context) | Empty detail screen; info `Please enter Account and Card Number`; no error; cursor on Account; both inputs editable | COCRDSLC | :349-356, :457-460, :489-496, :509-511, :522-523 | — | CardViewComponent spec "initial display" |
| FR-S05-02 | Account edit | Account blank, `*`, or zero; card supplied | Message `Account number not provided`; Account shows `*` in red; cursor on Account; no read performed | COCRDSLC | :613-620, :651-660, :541-545, :516-518 | — | CardViewServiceTests.AccountBlank_*; spec "blank account" |
| FR-S05-03 | Account edit | Account not exactly 11 numeric digits | Message `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`; Account cleared and red; cursor on Account; no read | COCRDSLC | :665-674, :462-463, :533-535 | — | CardViewServiceTests.AccountNotNumeric_*; spec "invalid account" |
| FR-S05-04 | Card edit | Account valid; card blank, `*`, or zero | Message `Card number not provided`; Card shows `*` in red; cursor on Card; no read | COCRDSLC | :622-629, :691-701, :547-551, :519-521 | — | CardViewServiceTests.CardBlank_*; spec "blank card" |
| FR-S05-05 | Card edit | Account valid; card not exactly 16 numeric digits | Message `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`; Card cleared and red; cursor on Card | COCRDSLC | :706-715, :468-469, :537-539 | — | CardViewServiceTests.CardNotNumeric_*; spec "invalid card" |
| FR-S05-06 | Cross-field edit | Both account and card not provided | Message `No input received` (replaces the field prompts); both fields `*` red; cursor on Account | COCRDSLC | :637-640 | — | CardViewServiceTests.BothBlank_* |
| FR-S05-07 | Message precedence | Both fields fail and at least one is non-blank | The Account message is shown (first failure wins); both fields are still flagged red/`*`; cursor on Account | COCRDSLC | :656-658, :668-672, :696-698, :709-713, :515-518 | — | CardViewServiceTests.AccountMessageWins_* |
| FR-S05-08 | Zero handling | Account `00000000000` or card `0000000000000000` (all-zero digits) | Treated exactly as "not provided" (FR-S05-02/04/06) | COCRDSLC | :653, :693 | — | CardViewServiceTests.ZeroValue_* |
| FR-S05-09 | Card lookup | Valid account and card; card record exists | Name on card, expiry month `MM`, expiry year `YYYY`, active Y/N displayed; info `   Displaying requested details`; account and card echoed; no error; cursor on Account | COCRDSLC | :736-754, :474-484, :465, :471, :522-523 | S05-B1 | CardViewServiceTests.CardFound_*; CardViewIntegrationTests.SeedCard_*; spec "found" |
| FR-S05-10 | Card lookup | Valid inputs; no record with that card number | Message `Did not find cards for this search condition`; both fields red, values retained; detail fields blank; cursor on Account | COCRDSLC | :755-761, :533-539, :516-518 | S05-B1 | CardViewServiceTests.CardNotFound_*; CardViewIntegrationTests.UnknownCard_* |
| FR-S05-11 | Card lookup | Card store unavailable / other read error | Message `File Error: READ     on CARDDAT   returned RESP <resp>,RESP2 <resp2>` (75-char frame); Account red; detail fields blank | COCRDSLC | :762-771, :102-121 | S05-B1, S05-B4 | CardViewServiceTests.StoreError_* |
| FR-S05-12 | Card lookup key | Valid inputs; card exists but belongs to a different account | Card is read by card number only — details displayed, entered account echoed unchanged | COCRDSLC | :740-750 | S05-B1 | CardViewServiceTests.AccountNotCrossChecked_*; CardViewIntegrationTests.ForeignAccount_* |
| FR-S05-13 | Entry from card list | Caller COCRDLIC passes account + card in the COMMAREA (PGM-ENTER) | Edits skipped; card read and displayed immediately (or FR-S05-10/11 outcome); Account and Card fields protected (read-only) | COCRDSLC | :339-348, :505-508, :529-531; COCRDLIC.cbl:519-540 | S05-B2 | CardViewServiceTests.FromCardList_*; spec "card-list context" |
| FR-S05-14 | Exit | PF3 | Control returns to the calling program (COCRDLIC when it was the caller, else main menu COMEN01C) | COCRDSLC | :305-334 | S05-B3 | spec "exit"/"F3" |
| FR-S05-15 | AID handling | Any AID other than ENTER/PF3 | Treated as ENTER: inputs are processed as a search; **no** invalid-key message | COCRDSLC | :291-299 | — | spec "unmapped function key" |
| FR-S05-16 | Screen parity | Screen rendered | Title `View Credit Card Detail`; Account input max 11, Card input max 16; footer `ENTER=Search Cards  F3=Exit`; separate info and error message areas | COCRDSLC | bms:78-152 | — | spec "layout" |

## 5. Validation and error catalogue
| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `Account number not provided` | account blank/`*`/zero (and card not also blank) | :657 (88-level :137) | blocking | Account `*` red, cursor Account |
| `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | account not 11 numeric | :670 | blocking | Account cleared red, cursor Account |
| `Card number not provided` | card blank/`*`/zero, account valid | :697 (88-level :139) | blocking | Card `*` red, cursor Card |
| `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | card not 16 numeric, account valid | :711 | blocking | Card cleared red, cursor Card |
| `No input received` | both blank | :639 (88-level :141) | blocking | both `*` red, cursor Account |
| `Did not find cards for this search condition` | READ NOTFND | :760 (88-level :151) | blocking | both red, values kept, cursor Account |
| `File Error: READ     on CARDDAT   returned RESP …,RESP2 …` | READ other RESP | :767-771, frame :102-121 | blocking | Account red, cursor Account |
| `   Displaying requested details` (INFO) | READ NORMAL | :754 (88-level :129) | — | details shown |
| `Please enter Account and Card Number` (INFO) | any screen without a found card | :131, :489-491 | — | — |
Unreachable 88-levels (not ported): `PF03 pressed.Exiting`, `Account number must be a non zero 11 digit number`, `Card number if supplied must be a 16 digit number`, `Did not find this account in cards database`, `Error reading Card Data File`, `Looks Good.... so far` (:135-155).

## 6. Field and data derivations
- `CC-ACCT-ID` ← ACCTSIDI unless `*`/spaces → LOW-VALUES (`:613-620`); `CC-CARD-NUM` likewise (`:622-629`).
- `CDEMO-ACCT-ID` ← `CC-ACCT-ID` when valid, zero otherwise (`:659`, `:673`, `:676`); `CDEMO-CARD-NUM` ← `CC-CARD-NUM-N` when valid (`:700`, `:714`, `:717`).
- From the card list: `CC-ACCT-ID-N` ← `CDEMO-ACCT-ID`, `CC-CARD-NUM-N` ← `CDEMO-CARD-NUM` (numeric moves, zero-padded display) (`:342-343`).
- Expiry: `CARD-EXPIRAION-DATE` X(10) `YYYY-MM-DD` → year bytes 1-4, month bytes 6-7 (`:84-90`, `:477-482`); day not displayed. Target: `Card.ExpirationDate` (DateOnly) → `MM` / `yyyy`; unparseable legacy dates (null) render blank.
- Read key: `WS-CARD-RID-CARDNUM` ← `CC-CARD-NUM` (`:740`).

## 7. Mechanics (demoted, cited)
HANDLE ABEND / ABEND-ROUTINE (`:250-252`, `:857-880`); COMMAREA init/restore (`:257-268`); `YYYY-STORE-PFKEY` via `CSSTRPFY` (`:284-285`, `:855`); RETURN TRANSID loop (`:394-406`); screen ERASE/CURSOR/FREEKB (`:569-576`); header date/time formatting (`:429-447`); RECEIVE MAP RESP captured but unchecked (`:597-601`); `SEND-PLAIN-TEXT`/`SEND-LONG-TEXT` (`:820-849`); `UNEXPECTED DATA SCENARIO` guard (`:373-380`); PF3 COMMAREA bookkeeping incl. `SET CDEMO-USRTYP-USER` (`:323-330`) — see §11.

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S05-01: Given a signed-on user selects option 04, When the screen opens, Then it is empty with `Please enter Account and Card Number` and the cursor on Account.
- FR-S05-02: Given Card `0500024453765740` and Account blank, When ENTER, Then `Account number not provided`, Account shows `*` in red.
- FR-S05-03: Given Account `12345` (or `ABCDEFGHIJK`), When ENTER, Then `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` and the Account field is cleared and red.
- FR-S05-04: Given Account `00000000050` and Card blank, When ENTER, Then `Card number not provided`, Card shows `*` in red, cursor on Card.
- FR-S05-05: Given Account `00000000050` and Card `1234`, When ENTER, Then `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`, Card cleared and red.
- FR-S05-06: Given both fields blank (or `*`), When ENTER, Then `No input received` and both fields show `*` in red.
- FR-S05-07: Given Account `ABC` and Card blank, When ENTER, Then the message is `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`; Given Account blank and Card `12`, Then `Account number not provided`; in both cases both fields are flagged.
- FR-S05-08: Given Account `00000000000`, When ENTER, Then behaves as blank account.
- FR-S05-09: Given Account `00000000050` and Card `0500024453765740` (seed), When ENTER, Then `Aniya Von`, expiry `03`/`2023`, active `Y`, info `   Displaying requested details`.
- FR-S05-10: Given Card `9999999999999999`, When ENTER, Then `Did not find cards for this search condition`, both fields red with values retained.
- FR-S05-11: Given the card store unavailable, When ENTER, Then the `File Error: READ     on CARDDAT   returned RESP …` message.
- FR-S05-12: Given Account `00000000001` and Card `0500024453765740` (belongs to account 50), When ENTER, Then the card details are displayed and Account still shows `00000000001`.
- FR-S05-13: Given navigation from the card list with account+card, When the screen opens, Then the card is displayed immediately and both inputs are read-only.
- FR-S05-14: Given the screen, When F3/Exit, Then navigate to the caller (`returnUrl`) or `/menu`.
- FR-S05-15: Given the screen with inputs typed, When F7 (any unmapped F-key), Then the search runs exactly as ENTER; no invalid-key message.
- FR-S05-16: Given the screen, Then Account/Card inputs are limited to 11/16 characters and the footer reads `ENTER=Search Cards  F3=Exit`.

## 9. Traceability matrix
FR-S05-02..13 → `CardViewService` (backend/CardDemo.Application/Cards/CardViewService.cs) → `CardViewServiceTests` (unit) + `CardViewIntegrationTests` (Testcontainers Postgres, seeded from `app/data/ASCII/carddata.txt`) + `CardViewApiIntegrationTests` (auth + HTTP contract).
FR-S05-01, 13, 14, 15, 16 and the screen-state rendering of 02..11 → `CardViewComponent` (frontend/src/app/cards/card-view.component.ts) → `card-view.component.spec.ts`.

**FR-S05-15 target disposition**: the shared `classifyAidKey` helper (`frontend/src/app/shared/invalid-key.ts`) is reused; `'exit'` → Exit, `'invalid'` → submit (ENTER), matching `:297-299`. This intentionally differs from the S-01 screens, whose COBOL emits the CSMSG01Y invalid-key message; COCRDSLC does not.

## 10. Program index
| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| COCRDSLC | card detail entry/validate/display | FR-S05-01..16 | [programs/COCRDSLC_functional_requirement.md](programs/COCRDSLC_functional_requirement.md) |

## 11. Open questions and assumptions
1. **Account not cross-checked** (FR-S05-12): the READ uses the card number only; a card is displayed even when the entered account is not its owner. Ported verbatim (source parity); recommended for customer review as a probable source defect.
2. **AID parity** (FR-S05-15): unmapped keys act as ENTER; no invalid-key message — differs from S-01 screens by source design.
3. **Zero test on non-numeric data** (`:653`, `:693`): undefined in the COBOL standard; the target treats any all-`0` value (of any length, e.g. `0`, `00000000000`) as "not provided" (IBM PACK semantics: trailing blanks pack to zero digits), and every other value that is not exactly 11/16 digits as "not numeric".
4. **File-error codes** (S05-B4): CICS RESP/RESP2 have no target equivalent; the frame is kept verbatim with RESP = unsigned HResult of the store exception and RESP2 = 0; trailing frame blanks are trimmed.
5. **PF3 COMMAREA mutation** (`:326`, `SET CDEMO-USRTYP-USER TO TRUE`): a session side effect with no screen behaviour; the target session identity is the JWT (S01-B6) and is not downgraded. Recorded as a deviation (storage/session level, no observable UI change within S-05).
6. **Return routing** (S05-B3): `CDEMO-FROM-PROGRAM` ↔ `returnUrl` query parameter; card-list context (S05-B2) ↔ presence of both `accountId` and `cardNumber` query parameters. S-04 must emit `/cards/view?accountId=…&cardNumber=…&returnUrl=/cards/list` (route name to be confirmed by S-04).
7. Screen header date/time and `Tran/Prog` labels are cosmetic runtime metadata (as in S-01) and not requirements.
