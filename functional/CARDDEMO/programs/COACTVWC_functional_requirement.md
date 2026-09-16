# COACTVWC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COACTVWC — `app/cbl/COACTVWC.cbl`. Stream S-02, single wave.
- Role: inquiry screen — accepts an account id, reads card-xref (AIX by account), account master and
  customer master, displays account + customer details. Read-only; no updates, no callees.

## 2. Trigger / caller contract
- CICS transaction `CAVW` (`app/csd/CARDDEMO.CSD:362-367`); reached from the main menu option 01
  (`COMEN01C.cbl:183-186`, XCTL with `CARDDEMO-COMMAREA`) or by direct terminal entry (`EIBCALEN = 0`,
  `COACTVWC.cbl:282-290`); pseudo-conversational re-entry with `CARDDEMO-COMMAREA` +
  `THIS-PROGCOMMAREA` (`:291-297`, `:396-401`).
- On PF3 it XCTLs to `CDEMO-FROM-PROGRAM` (or `COMEN01C` when blank) with `CDEMO-PGM-ENTER` and
  `CDEMO-USRTYP-USER` set (`:324-352`).

## 3. Inputs and outputs
Inputs (map `CACTVWA`, `app/cpy-bms/COACTVW.CPY:55-60`): ACCTSIDI 9(11) (dict: `accounts.acct_id`),
AID (EIBAID).
Reads: `CXACAIX` (CARD-XREF-RECORD `app/cpy/CVACT03Y.cpy`, key XREF-ACCT-ID), `ACCTDAT`
(ACCOUNT-RECORD `CVACT01Y.cpy`, key ACCT-ID), `CUSTDAT` (CUSTOMER-RECORD `CVCUS01Y.cpy`, key CUST-ID).
Outputs: 30 output fields (analysis §2), INFOMSG X(45), ERRMSG X(78) ← WS-RETURN-MSG X(75);
COMMAREA `CDEMO-ACCT-ID`, `CDEMO-CUST-ID`, `CDEMO-CARD-NUM` (`:735-737`, `:679`).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COACTVWC-01 | first entry | empty account field, prompt info line | :353-360, :461-464, :528-530 | FR-S02-01 |
| COACTVWC-02 | ENTER, blank / `*` | `No input received`, field `*` red | :628-633, :653-661, :640-642, :561-565 | FR-S02-02 |
| COACTVWC-03 | ENTER, not 11 digits or zero | `Account Filter must  be a non-zero 11 digit number`, echo red | :666-676, :557-559 | FR-S02-03 |
| COACTVWC-04 | xref NOTFND | `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` | :741-758 | FR-S02-04 |
| COACTVWC-05 | acct NOTFND | `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000` | :789-807 | FR-S02-05 |
| COACTVWC-06 | cust NOTFND | `CustId:<id> not found in customer master.Resp: 000000013  REAS:0000000`; account block kept | :839-857, :471-491 | FR-S02-06 |
| COACTVWC-07 | all found | account + customer blocks filled, prompt info, no error | :471-523 | FR-S02-07 |
| COACTVWC-08 | amounts | `+ZZZ,ZZZ,ZZZ.99` edit | bms:118,137,156,166,187 | FR-S02-08 |
| COACTVWC-09 | customer fields | SSN `nnn-nn-nnnn`, FICO 3 digits, zip 5, phone 13 | :495-519 | FR-S02-09 |
| COACTVWC-10 | xref read | first xref of account (AIX) supplies customer id | :723-740 | FR-S02-10 |
| COACTVWC-11 | PF3 | XCTL to caller / menu | :324-352 | FR-S02-11 |
| COACTVWC-12 | other AID | forced to ENTER | :306-314 | FR-S02-12 |
| COACTVWC-13 | other RESP on any read | `File Error: READ     on <file>   returned RESP …,RESP2 …` | :759-769, :809-819, :858-868 | FR-S02-13 |
| COACTVWC-14 | map widths | ACCTSID 11 (MUSTFILL), output widths | bms:84-354 | FR-S02-14 |
| COACTVWC-15 | session | no user-type restriction | (no `CDEMO-USRTYP` test in program) | FR-S02-15 |

## 5. Business rules and validations
Sequence (blocking, first failure wins): blank/`*` → `No input received` → not-11-digits/zero → filter
message → xref read (NOTFND / other) → account read (NOTFND / other) → customer read (NOTFND / other,
non-blocking for the account block) → display (`:648-685`, `:687-870`, `:452-533`). All errors
redisplay the same screen; no abends on the business path.

## 6. Data access and boundaries
- Three read-only keyed reads (B-009, REUSE shared repositories: `ICardXrefRepository.GetFirstByAccountIdAsync`,
  `IAccountRepository.GetByIdAsync`, `ICustomerRepository.GetByIdAsync`). No writes, no commit scope.
- Return routing on PF3 (B-012) → `/menu`. Session (S01-B6) → JWT; `authGuard` only.
- S02-B1: AID fall-through to ENTER (`:311-314`) — source wins over the S-01 invalid-key convention.
- S02-B2 (deviation, technical path): RESP/RESP2 in the file-error message rendered as fixed IOERR
  codes `000000017 `/`000000120 ` because the target has no CICS RESP.

## 7. Error and edge behavior
`*` or spaces in the account field are treated as blank (`:628-633`); the intermediate
`Account number not provided` is overwritten by `No input received` (`:641`); customer-not-found still
shows the account block (`:471`); zip/phone truncated by alphanumeric MOVE into shorter map fields;
`WS-INFORM-OUTPUT` never SET → info line always the prompt.

## 8. Hard-stop boundary
Does not update anything (Account Update is COACTUPC, S-03). Does not render the menu; PF3 returns to
the caller.

## 9. Demoted mechanics
Pseudo-conversational RETURN TRANSID (`:396-401`); `CDEMO-USRTYP-USER` reset on exit (`:344`,
unreachable for admins — no admin-menu option); header date/time/APPLID (`:432-448`); map attribute
plumbing (`:537-590`); ABEND handler (`:920-936`); RECEIVE MAP RESP unchecked (`:596-615`).

## 10. Traceability
COACTVWC-01..15 ↔ FR-S02-01..15 (`S02_functional_requirement.md` §1) ↔ parity tests
(`backend/CardDemo.Tests/Accounts/*`, `frontend/src/app/account-view/account-view.component.spec.ts`).
