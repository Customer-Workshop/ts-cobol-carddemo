# COTRN01C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COTRN01C — `app/cbl/COTRN01C.cbl`. Stream S-08, wave 1 (sole program).
- Role: entry/validator/display — single-transaction view screen keyed by transaction ID. Stream-owned, not shared.

## 2. Trigger / caller contract
- CICS transaction `CT01` (`app/csd/CARDDEMO.CSD:429-430`). Entered with `CARDDEMO-COMMAREA` from the main menu (option 07) or from COTRN00C (`app/cbl/COTRN00C.cbl:186-195`, which sets `CDEMO-TO-PROGRAM='COTRN01C'`, `CDEMO-FROM-PROGRAM='COTRN00C'`, `CDEMO-PGM-CONTEXT=0` and the selected id). `EIBCALEN = 0` → XCTL COSGN00C (`COTRN01C.cbl:94-96`).
- Re-entered pseudo-conversationally with its own COMMAREA (`:136-139`); `CDEMO-PGM-REENTER` distinguishes first entry from re-entry (`:99-100`).
- Stream-specific COMMAREA extension `CDEMO-CT01-INFO` (`:53-61`): only `CDEMO-CT01-TRN-SELECTED` X(16) is read (`:103-108`); the paging members are unused here.

## 3. Inputs and outputs
Inputs (map COTRN1A / mapset COTRN01, `app/cpy-bms/COTRN01.CPY`): TRNIDINI X(16) (dict: transactions.tran_id); AID key (EIBAID).
Reads: TRANSACT KSDS record TRAN-RECORD (`app/cpy/CVTRA05Y.cpy:4-17`), key = TRNIDINI verbatim (`:172`, `:269-278`).
Outputs: TRNIDO X(16), CARDNUMO X(16), TTYPCDO X(2), TCATCDO X(4), TRNSRCO X(10), TDESCO X(60), TRNAMTO X(12), TORIGDTO X(10), TPROCDTO X(10), MIDO X(9), MNAMEO X(30), MCITYO X(25), MZIPO X(10); ERRMSGO X(78); header TRNNAME/PGMNAME/TITLE01/TITLE02/CURDATE/CURTIME (`:243-262`); outgoing COMMAREA on XCTL (`:197-208`, `:115-127`).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COTRN01C-01 | no COMMAREA | transfer to sign-on | :94-96 | FR-S08-01 |
| COTRN01C-02 | first entry, no selected id | blank screen, cursor TRNIDIN | :98-102, :109 | FR-S08-02 |
| COTRN01C-03 | first entry, selected id present | id pre-filled, fetch performed | :103-108 | FR-S08-03 |
| COTRN01C-04 | ENTER, blank id | `Tran ID can NOT be empty...`, details untouched | :147-152, :158 | FR-S08-04 |
| COTRN01C-05 | ENTER, non-blank id | 13 detail fields cleared before READ | :158-173 | FR-S08-05 |
| COTRN01C-06 | READ RESP NOTFND | `Transaction ID NOT found...` | :283-288 | FR-S08-06 |
| COTRN01C-07 | READ RESP other | `Unable to lookup Transaction...`; RESP/RESP2 displayed | :289-295 | FR-S08-07 |
| COTRN01C-08 | READ RESP NORMAL | 13 detail fields populated, message blank | :176-191 | FR-S08-08 |
| COTRN01C-09 | amount display | `+99999999.99` edit picture | :49, :177, :183 | FR-S08-09 |
| COTRN01C-10 | timestamp display | first 10 chars of X(26) | :185-186 | FR-S08-10 |
| COTRN01C-11 | long text display | truncation to map lengths 60/30/25 | :184, :188-189 | FR-S08-11 |
| COTRN01C-12 | id typed | verbatim key, X(16) | :172 | FR-S08-12 |
| COTRN01C-13 | PF3 | XCTL `CDEMO-FROM-PROGRAM`, default COMEN01C | :115-122 | FR-S08-13 |
| COTRN01C-14 | PF4 | all fields + message cleared, cursor TRNIDIN | :123-124, :301-326 | FR-S08-14 |
| COTRN01C-15 | PF5 | XCTL COTRN00C | :125-127 | FR-S08-15 |
| COTRN01C-16 | other AID | `Invalid key pressed. Please see below...` | :128-131 | FR-S08-16 |
| COTRN01C-17 | any send | header + footer literals | :243-262; bms:75-79, 263-268 | FR-S08-17 |

## 5. Business rules and validations
Single edit: id mandatory (`:147`). No case folding, no numeric check, no length check beyond the X(16) map field. Order: blank check → clear details → READ → populate (`:146-192`). Error flag `WS-ERR-FLG` short-circuits populate (`:176`).

## 6. Data access and boundaries
- S08-B1: `EXEC CICS READ DATASET('TRANSACT') INTO(TRAN-RECORD) RIDFLD(TRAN-ID) KEYLENGTH(16) UPDATE RESP RESP2` (`:269-278`) → `ITransactionRepository.GetByIdAsync(id.TrimEnd())` (EF Core, `AsNoTracking`, no lock). RESP protocol: NORMAL → found; NOTFND → not found; other → store error.
- S08-B2 (inbound `CDEMO-CT01-TRN-SELECTED`) → `tranId` query parameter; S08-B3 (PF3 return) → `returnUrl`; S08-B4 (PF5 → COTRN00C) → registry option 06 via S-01 `MenuService.select`.

## 7. Error and edge behavior
- Blank id error leaves previously displayed details on screen (clear happens only when `NOT ERR-FLG-ON`, `:158`).
- Failed READ (either branch) shows an empty detail area because the clear precedes the READ (`:159-173`).
- Amount ≥ 100,000,000.00 in magnitude loses its high-order digit in display (`+99999999.99`); zero displays `+00000000.00`.
- Blank timestamp displays blank (`MOVE SPACES` semantics of X(26) → X(10)).
- `READ UPDATE` lock is released at task end; no REWRITE exists — read-only in target.

## 8. Hard-stop boundary
Transfers to COSGN00C (`:94-96`), `CDEMO-FROM-PROGRAM`/COMEN01C (`:115-122`, `:197-208`) and COTRN00C (`:125-127`) are consumed as S-01 routes / registry entries; COTRN00C remains disabled in the registry until S-07 lands.

## 9. Demoted mechanics
SEND/RECEIVE MAP (`:213-238`), RETURN TRANSID (`:136-139`), `INITIALIZE-ALL-FIELDS` map plumbing (`:310-326`), header date/time formatting (`:243-262`), `DISPLAY` diagnostics (`:290`), WS-RESP-CD/WS-REAS-CD (`:36-37`).

## 10. Traceability
Backend: `backend/CardDemo.Application/Transactions/TransactionViewService.cs`, `TransactionViewMapper.cs`, `TransactionViewModels.cs`; `backend/CardDemo.Api/Controllers/TransactionViewController.cs`; tests `backend/CardDemo.Tests/Transactions/*`.
Frontend: `frontend/src/app/transactions/transaction-view.component.ts|html|scss|spec.ts`, `transaction-view.service.ts`; route `/transactions/view` in `frontend/src/app/app.routes.ts`.
