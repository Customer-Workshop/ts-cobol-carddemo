# S-05 Card View — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Stream assigned from the inventory catalog (`CardDemo_inventory.md` §5, row S-05); process type **ONLINE**.
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md`. Shared seams consumed as-is from S-01 (JWT `SessionContext`, menu route registry, invalid-key helper) and from the Batch A shared data layer (`cards` table, `ICardRepository`).

## 1. Pinned stream

- **Entry point (proof)**: `CCDL -> COCRDSLC` (`app/csd/CARDDEMO.CSD:347-348`; program definition `:219-223`). Main-menu option 04 "Credit Card View" routes here (`app/cpy/COMEN02Y.cpy:46`).
- **Second inbound path**: the card list program COCRDLIC transfers control with an account/card pre-selected (`app/cbl/COCRDLIC.cbl:519-540`: FROM-PROGRAM=COCRDLIC, LAST-MAPSET=COCRDLI, PGM-ENTER, CDEMO-ACCT-ID/CDEMO-CARD-NUM set, XCTL COCRDSLC).
- **Hard stop**: the PF3 `XCTL PROGRAM(CDEMO-TO-PROGRAM)` back to the caller (`COCRDSLC.cbl:331-334`) — callers COMEN01C (S-01, migrated) and COCRDLIC (S-04, not this stream). No other outbound transfer exists.
- **Exclusions**: COCRDLIC (S-04) and COCRDUPC (S-06) are not touched; the account alternate-index path `9150-GETCARD-BYACCT` (`:779-810`) is dead code (never PERFORMed) and is not ported.
- Pseudo-conversational shape: `EXEC CICS RETURN TRANSID(CCDL) COMMAREA(WS-COMMAREA)` (`:402-406`).

## 2. Program inventory + leaf-first DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COCRDSLC | app/cbl/COCRDSLC.cbl (887 lines) | entry/validate/display (single-card detail) | CICS READ CARDDAT by card number (`:742-750`); XCTL CDEMO-TO-PROGRAM on PF3 (`:331-334`); dead READ CARDAIX (`:783-791`) | no | yes |

No subroutine CALLs. Copybooks: `CVCRD01Y` (CC-ACCT-ID/CC-CARD-NUM work fields + AID 88-levels), `COCOM01Y` (COMMAREA), `CVACT02Y` (CARD-RECORD), `COCRDSL` (map symbolic), `CSSTRPFY` (AID→CCARD-AID mapping), `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSUSR01Y`, `CVCUS01Y` (unused here).

DAG: `COCRDSLC -> CARDDAT` (leaf). Single-wave stream.

## 3. Surfaces (ONLINE)

### COCRDSLC — screen CCRDSLA / mapset COCRDSL (`app/bms/COCRDSL.bms`, 24x80, `CTRL=(FREEKB)` `:25`)

| Field | I/O | PIC / attrs | Edits / population (cite) |
|---|---|---|---|
| TRNNAME, TITLE01, CURDATE, PGMNAME, TITLE02, CURTIME | DISPLAY | header | `COCRDSLC.cbl:427-447` (transaction `CCDL`, program `COCRDSLC`, date mm/dd/yy, time hh:mm:ss) |
| screen title | DISPLAY | literal | `View Credit Card Detail` (bms:78) |
| ACCTSID | INPUT | X(11), `FSET,IC,NORM,UNPROT` (bms:84), label `Account Number    :` (bms:83) | `'*'`/spaces → blank (`:613-620`); blank/zero → `Account number not provided` (`:651-660`); not 11 numeric → `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` (`:665-674`); protected (`DFHBMPRF`) when entered from card list (`:505-508`) |
| CARDSID | INPUT | X(16), `FSET,NORM,UNPROT` (bms:96), label `Card Number       :` (bms:95) | same shape: `Card number not provided` (`:691-701`); `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` (`:706-715`) |
| CRDNAME | DISPLAY | X(50), underline (bms:107), label `Name on card      :` | `CARD-EMBOSSED-NAME` when found (`:474-476`) |
| CRDSTCD | DISPLAY | X(1), ASKIP (bms:116), label `Card Active Y/N   : ` | `CARD-ACTIVE-STATUS` (`:484`) |
| EXPMON / EXPYEAR | DISPLAY | X(2) / X(4), ASKIP (bms:126,133), label `Expiry Date       : ` | month = bytes 6-7, year = bytes 1-4 of `CARD-EXPIRAION-DATE` X(10) `YYYY-MM-DD` (`:477-482`, `:84-90`) |
| INFOMSG | DISPLAY | X(40), PROT (bms:139) | `Please enter Account and Card Number` or `   Displaying requested details` (`:127-133`, `:489-496`) |
| ERRMSG | DISPLAY | X(80), BRT (bms:144) | `WS-RETURN-MSG` X(75) (`:494`) |
| footer | DISPLAY | literal | `ENTER=Search Cards  F3=Exit` (bms:152) |

**AID handling (`:284-299`)**: only ENTER and PF3 are valid; **any other AID is re-mapped to ENTER** (`SET CCARD-AID-ENTER TO TRUE`, `:297-299`) — this program does *not* emit the CSMSG01Y invalid-key message. PF3 → XCTL back to `CDEMO-FROM-PROGRAM` (fallback COMEN01C / CM00 when blank, `:309-322`), also forcing `CDEMO-USRTYP-USER` (`:326`) and `PGM-ENTER` (`:327`).

**Dispatch (`:304-381`)**:
1. PF3 → return to caller (above).
2. PGM-ENTER and FROM-PROGRAM = COCRDLIC → skip edits, take `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` from the COMMAREA, read, display (`:339-348`).
3. PGM-ENTER (from menu / first display) → send empty prompt screen (`:349-356`).
4. PGM-REENTER → receive + edit inputs; on error redisplay, else read + display (`:357-371`).
5. OTHER → plain text `UNEXPECTED DATA SCENARIO` (`:373-380`, unreachable with a well-formed COMMAREA).

**Edit sequence (`2200-EDIT-MAP-INPUTS`, `:608-641`)**: account edit (`:647-679`) then card edit (`:685-719`), each: blank/zero → `FLG-…-BLANK` + prompt message; not numeric → `FLG-…-NOT-OK` + filter message; the message slot is set only if still empty (`IF WS-RETURN-MSG-OFF`) so **the account message wins over the card message**; then if both blank, the message is *replaced* by `No input received` (`:637-640`). Any failed edit sets `INPUT-ERROR` and the read is skipped.

**Read (`9100-GETCARD-BYACCTCARD`, `:736-773`)**: `EXEC CICS READ FILE(CARDDAT) RIDFLD(card number)` — **keyed by card number only; the account number is not compared with `CARD-ACCT-ID`**. NORMAL → `FOUND-CARDS-FOR-ACCOUNT`; NOTFND → both filters NOT-OK + `Did not find cards for this search condition`; OTHER → `File Error: READ     on CARDDAT   returned RESP nnn,RESP2 nnn` (frame `:102-121`, X(75) truncation).

**Screen state after processing (`1200`/`1300`, `:457-557`)**: account/card echo the edited values when non-zero, else blank (`:462-471`); a BLANK filter on re-entry displays `*` in red (`:541-551`); a NOT-OK filter is red (`:533-539`) and, because `CDEMO-ACCT-ID`/`CARD-NUM` were zeroed (`:673`, `:714`), the invalid value is *cleared*; cursor: account when account NOT-OK/BLANK, else card when card NOT-OK/BLANK, else account (`:515-524`); INFOMSG always visible (`:489-491`, `:553-557`).

## 4. Data + field dictionary

**Dataset**: CARDDAT VSAM KSDS `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` (`app/csd/CARDDEMO.CSD:25-26`), key `CARD-NUM` X(16); alternate index CARDAIX (`:13-14`) on `CARD-ACCT-ID` — defined but only referenced from dead code in this program. Read-only in this stream.

Field dictionary (FACT, `app/cpy/CVACT02Y.cpy:16-23` → shared data layer at commit 468e17d):
| COBOL field | PIC | C# (`CardDemo.Domain.Cards.Card`) | PostgreSQL (`cards`) | Used by S-05 |
|---|---|---|---|---|
| CARD-NUM | X(16) | `CardNumber` string | `card_num` varchar(16) PK | key |
| CARD-ACCT-ID | 9(11) | `AccountId` string | `card_acct_id` varchar(11), `ix_cards_card_acct_id` | not compared (echo only) |
| CARD-CVV-CD | 9(03) | `CvvCode` | `card_cvv_cd` | not displayed |
| CARD-EMBOSSED-NAME | X(50) | `EmbossedName` | `card_embossed_name` varchar(50) | CRDNAME |
| CARD-EXPIRAION-DATE | X(10) `YYYY-MM-DD` | `ExpirationDate` DateOnly? | `card_expiration_date` date | EXPMON/EXPYEAR |
| CARD-ACTIVE-STATUS | X(01) | `ActiveStatus` | `card_active_status` | CRDSTCD |
| FILLER | X(59) | — | — | — |

No schema extension is needed: every field COCRDSLC reads exists in the shared layer, and `ICardRepository.GetByCardNumberAsync` is the exact equivalent of the keyed READ.

Screen work fields (`app/cpy/CVCRD01Y.cpy`): `CC-ACCT-ID` X(11) / `CC-ACCT-ID-N` 9(11), `CC-CARD-NUM` X(16) / `CC-CARD-NUM-N` 9(16). COMMAREA (`app/cpy/COCOM01Y.cpy:19-44`): `CDEMO-ACCT-ID` 9(11), `CDEMO-CARD-NUM` 9(16), FROM/TO program, PGM-CONTEXT.

## 5. Boundary table (headline) — S05-B1..S05-B4 (register entries are appended by the integration stage; this stream does not edit `.migration/`)

| ID | Class | Contract | Direction | Cite | Decision taken in this stream |
|---|---|---|---|---|---|
| S05-B1 | B4 data-access leaf | CICS READ CARDDAT keyed by card number; RESP NORMAL/NOTFND/other | outbound | COCRDSLC.cbl:742-772 | **Reuse** shared `ICardRepository.GetByCardNumberAsync` (Postgres `cards`); found / null / exception → the three RESP outcomes |
| S05-B2 | B5 inbound switch | XCTL from COCRDLIC with COMMAREA account+card and FROM-PROGRAM/LAST-MAPSET context | inbound | COCRDLIC.cbl:519-540; COCRDSLC.cbl:339-348, :505-508 | Route contract `/cards/view?accountId=&cardNumber=&returnUrl=` (S01-B3 idiom): when the caller supplies both keys, the screen auto-reads with edits skipped and the two input fields are read-only; S-04 consumes this contract when it lands |
| S05-B3 | B5 outbound return | PF3 XCTL to CDEMO-FROM-PROGRAM (fallback COMEN01C) | outbound | COCRDSLC.cbl:305-334 | Exit navigates to `returnUrl` when supplied, else `/menu` |
| S05-B4 | Runtime diagnostic | File-error message embeds CICS RESP/RESP2 codes | — | COCRDSLC.cbl:102-121, :767-771 | Frame preserved verbatim (`File Error: READ     on CARDDAT   returned RESP …,RESP2 …`, 75 chars); RESP carries the target exception's unsigned HResult, RESP2 is zero (no secondary code exists in the target) |

All contracts resolved from source; **no unresolved-contract blockers**.

## 6. Waves

| Wave | Content | Repos touched |
|---|---|---|
| 1 | COCRDSLC: `CardViewService` + `GET /api/v1/cards/view`, Angular `CardViewComponent` at `/cards/view` (authGuard; registry flag for option 04 stays disabled), unit + Testcontainers + component specs | backend/, frontend/ |

## 7. Risks

1. **AID semantics differ from S-01**: unmapped keys behave as ENTER (`:297-299`) instead of showing the invalid-key message. Ported as-is (source parity) using the shared `classifyAidKey`, mapped `'invalid' → submit`. LOW, documented.
2. **Account not cross-checked** against the card's account (`:740-747`): a valid card with a foreign account still displays. Ported as-is; flagged as a source defect for the customer. MEDIUM (business), LOW (implementation).
3. Undefined COBOL behavior: `CC-ACCT-ID-N EQUAL ZEROS` on non-numeric data (`:653`, `:693`). Target rule: a value made only of `0` digits (any length) is "not provided" (IBM PACK semantics treat trailing blanks as zero digits); anything else that is not exactly 11/16 digits is "not numeric". LOW.
4. PF3 side effect `SET CDEMO-USRTYP-USER TO TRUE` (`:326`) is a COMMAREA mutation with no screen effect; the target session identity is the immutable JWT (S01-B6) so it is not ported. LOW, recorded as deviation.
5. Dead code / unused messages: `9150-GETCARD-BYACCT`, `Did not find this account in cards database`, `Error reading Card Data File`, `Account number must be a non zero 11 digit number`, `Card number if supplied must be a 16 digit number`, `Looks Good.... so far`, `PF03 pressed.Exiting` — none reachable; not ported. LOW.

## 8. Validation
(1) 1/1 program inventoried entry→hard stop, none absent; (2) single wave, DAG trivial; (3) claims cited `<file>:<line>`; (4) ONLINE surface only (screen/AID/edits); (5) all crossings in the boundary table with contracts; (6) sole data leaf S05-B1 resolved to the shared Postgres layer without schema change.
