# COACTUPC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COACTUPC — `app/cbl/COACTUPC.cbl` (cites `:line`). Stream S-03, wave 1 (single program).
- Role: pseudo-conversational maintenance screen — fetch account + customer by account id, edit, validate, confirm (F5), atomic rewrite of ACCTDAT and CUSTDAT.

## 2. Trigger / caller contract
- CICS transaction `CAUP` (:536, `app/csd/CARDDEMO.CSD`). Entered by XCTL from `COMEN01C` option 02 with `CARDDEMO-COMMAREA` (`CDEMO-FROM-PROGRAM='COMEN01C'`, `CDEMO-PGM-CONTEXT` = enter) (:880-897).
- Re-entered via `EXEC CICS RETURN TRANSID('CAUP') COMMAREA(WS-COMMAREA)` where `WS-COMMAREA` = `CARDDEMO-COMMAREA` ‖ `WS-THIS-PROGCOMMAREA` (ACUP-OLD-DETAILS, ACUP-NEW-DETAILS, `ACUP-CHANGE-ACTION`) (:1007-1019).
- PF3: `CDEMO-TO-PROGRAM` ← `CDEMO-FROM-PROGRAM` (or `COMEN01C`/`CM00` if blank), `CDEMO-FROM-*` ← this program, SYNCPOINT, XCTL (:927-960).

## 3. Inputs and outputs
Inputs (map `CACTUPA`/`COACTUP`, `app/bms/COACTUP.bms`): ACCTSID X(11) plus all editable detail fields listed in `S03_account_update_analysis.md` §3;
AID (`CCARD-AID-*`: ENTER, PF3, PF5, PF12). Blank or `*` in any field is normalised to LOW-VALUES on receive (:1057-1430).
Reads: CXACAIX by account id (:3650-3698), ACCTDAT (:3701-3748, READ UPDATE :3893-3912), CUSTDAT (:3752-3797, READ UPDATE :3914-3933).
Writes: REWRITE ACCTDAT from `ACCT-UPDATE-RECORD` (:3945-3975, :4053-4066), REWRITE CUSTDAT from `CUST-UPDATE-RECORD` (:3977-4050, :4068-4105); SYNCPOINT ROLLBACK when the customer rewrite fails (:4079-4105).
Outputs: INFOMSGO X(45), ERRMSGO X(78) (from `WS-RETURN-MSG` X(75), :479), header fields (:2668-2694), per-field colour/`*` marking (:3016-3480), F5/F12 brightness (:3566-3584), outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COACTUPC-01 | first entry from menu | Search state, `Enter or update id of account to update` | :964-975, :2955-2962 | FR-S03-01 |
| COACTUPC-02 | ENTER, account id blank/`*` | `No input received` | :1057-1064, :1434-1440 | FR-S03-02 |
| COACTUPC-03 | account id not numeric or zero | `Account Number if supplied must be a 11 digit Non-Zero Number` | :1797-1815 | FR-S03-03 |
| COACTUPC-04 | xref NOTFND | `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` | :3669-3684 | FR-S03-04 |
| COACTUPC-05 | account NOTFND | `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000` | :3717-3733 | FR-S03-05 |
| COACTUPC-06 | customer NOTFND | `CustId:<id> not found in customer master.Resp: 000000013  REAS:0000000` | :3766-3783 | FR-S03-06 |
| COACTUPC-07 | all three found | Details state; `ACUP-OLD-DETAILS` built (:3801-3885); `Update account details presented above.` | :2568-2580, :2787-2867 | FR-S03-07 |
| COACTUPC-08 | ENTER, nothing changed | `No change detected with respect to values fetched.` | :1681-1777 | FR-S03-08 |
| COACTUPC-09 | ENTER, something changed | edits in fixed order, first message wins, every failing field flagged | :1469-1677 | FR-S03-09..22 |
| COACTUPC-10 | all edits pass | `ACUP-CHANGES-OK-NOT-CONFIRMED`; `Changes validated.Press F5 to save`; all protected; F5/F12 bright | :1670-1674, :2582-2590 | FR-S03-23 |
| COACTUPC-11 | edits fail | `ACUP-CHANGES-NOT-OK`; error line = first message; invalid fields red / `*` | :1469, :2596-2598, CSSETATY.cpy | FR-S03-24 |
| COACTUPC-12 | F5 in confirm state | `9600-WRITE-PROCESSING`; on success `Changes committed to database` | :2600-2612, :3888-4105 | FR-S03-25, 34 |
| COACTUPC-13 | account READ UPDATE fails | `Could not lock account record for update`; state L | :3903-3912 | FR-S03-26 |
| COACTUPC-14 | customer READ UPDATE fails | `Could not lock customer record for update` | :3924-3933 | FR-S03-27 |
| COACTUPC-15 | stored ≠ fetched snapshot | `Record changed by some one else. Please review`; Details w/ originals | :4109-4200, :2608-2609 | FR-S03-28 |
| COACTUPC-16 | REWRITE fails | `Update of record failed`; rollback; state F | :4053-4105 | FR-S03-29 |
| COACTUPC-17 | ENTER after commit | back to Details | :2626-2634 | FR-S03-30 |
| COACTUPC-18 | F12 after fetch | re-read, Details, edits discarded | :908-915, :2571-2580 | FR-S03-31 |
| COACTUPC-19 | F3 | exit to caller | :927-960 | FR-S03-32 |
| COACTUPC-20 | other AID | treated as ENTER (web: invalid-key message) | :905-915 | FR-S03-33 |

## 5. Business rules and validations
Order and messages: stream FR §4 (FR-S03-09..22). Helper paragraphs: `1215-EDIT-MANDATORY` (:1824-1852), `1220-EDIT-YESNO` (:1856-1894),
`1225-EDIT-ALPHA-REQD` (:1898-1951), `1235-EDIT-ALPHA-OPT` (:2012-2057), `1245-EDIT-NUM-REQD` (:2109-2176), `1250-EDIT-SIGNED-9V2` (:2180-2221),
`1260-EDIT-US-PHONE-NUM` (:2225-2427), `1265-EDIT-US-SSN` (:2431-2489), `1270-EDIT-US-STATE-CD` (:2493-2511), `1275-EDIT-FICO-SCORE` (:2514-2531),
`1280-EDIT-US-STATE-ZIP-CD` (:2536-2558), `EDIT-DATE-CCYYMMDD` + `EDIT-DATE-OF-BIRTH` (`CSUTLDPY.cpy`).
Rule details:
- Alphabetic = A-Z, a-z (`LIT-ALL-ALPHA-FROM-X`, :587-591); after converting those to spaces the trimmed remainder must be empty (:1925-1933).
- Numeric-required = the full field width must be digits (`IS NUMERIC`), then `NUMVAL ≠ 0` (:2130-2170).
- Money = `TEST-NUMVAL-C = 0` (:2192-2205); value is `NUMVAL-C` (:1078-1083).
- Date = year 4 digits and century 19/20; month 1-12; day 1-31; 30-day months; February with leap rule (`CSUTLDPY.cpy:19-283`). DOB additionally `< today` (`:333-360`).
- Change detection `1205-COMPARE-OLD-NEW`: account group (all fields) then customer group; text compared `UPPER-CASE(TRIM())`, money numerically, dates/phones/SSN/EFT/FICO byte-exact (:1681-1777).
- Concurrency `9700-CHECK-CHANGE-IN-REC`: same comparison rules applied between stored record and `ACUP-OLD-*` (:4109-4200).

## 6. Data access and boundaries
- S03-B2 (B4 data-access): three keyed reads + two READ UPDATE/REWRITE in one CICS UOW; **DECIDED**: one PostgreSQL transaction in `AccountUpdateRepository`, row locks via `FOR UPDATE NOWAIT`, `SaveChanges` for both rows, rollback on any failure.
- S03-B3 (B10 COMMAREA state): **DECIDED**: client carries `original`/`updated` snapshots; server is stateless and re-validates on save.
- S03-B1 (B5 outbound PF3): **DECIDED**: navigate to `/menu`.
- S03-B4: `COCRDUPC/COCRDLIC/COCRDSLC` literals unused; off-stream.

## 7. Error and edge behavior
- `WS-RETURN-MSG` is 75 wide: long lookup messages are truncated (see stream FR §11).
- Deviations D1-D6 (stream FR §11): customer-lock failure mis-branch, account-miss continue, stale snapshot after commit, abend on no-change after failure, LOW-VALUES vs SPACES trim quirk, lock semantics.
- Account id field is editable again in L/F states (`WHEN OTHER`, :3006-3007) — preserved.
- `INFOMSG` dark when empty (:3567-3571) — preserved (info line hidden when blank).

## 8. Hard-stop boundary
None beyond S03-B1..B5; no program outside S-03 is implemented.

## 9. Demoted mechanics
HANDLE ABEND / ABEND-ROUTINE (:4203-4222), SEND MAP ERASE/CURSOR (:3589-3603), attribute bytes (DFHBM*), `CSUTLDTC` LE call, SYNCPOINT before XCTL,
`CDEMO-LAST-MAP/MAPSET` bookkeeping.

## 10. Traceability
Backend: `backend/CardDemo.Application/AccountUpdate/*`, `backend/CardDemo.Infrastructure/Persistence/AccountUpdateRepository.cs`,
`backend/CardDemo.Api/Controllers/AccountUpdateController.cs`; tests `backend/CardDemo.Tests/AccountUpdate/*`.
Frontend: `frontend/src/app/account-update/*`; route `accounts/update` in `frontend/src/app/app.routes.ts`.
