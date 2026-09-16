# COPAUS0C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COPAUS0C — `app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl`. Stream S-19, wave 2.
- Role: entry/list — pending-authorization list for an account: VSAM context reads, IMS summary GU + child GNP fill (5 rows/page), S-select to detail, PF7/PF8 keyset paging.

## 2. Trigger / caller contract
- CICS transaction `CPVS` (`csd/CRDDEMO2.csd`); reached by XCTL from COMEN01C option 11 behind the INQUIRE PROGRAM probe (`app/cbl/COMEN01C.cbl:147-162`) with `CARDDEMO-COMMAREA`; re-entered pseudo-conversationally (`COPAUS0C.cbl:254`).
- COMMAREA extension `CDEMO-CPVS-INFO` (`:117-126`): SEL-FLG, PAU-SELECTED X(8), PAUKEY-PREV-PG X(8) OCCURS 20, PAUKEY-LAST X(8), PAGE-NUM S9(4), NEXT-PAGE-FLG, AUTH-KEYS X(8) OCCURS 5.
- Outbound: XCTL COPAUS1C via `LIT-DETAILPGM` with selected key (:287-321); XCTL CDEMO-TO-PROGRAM on PF3 (:675) and on no-context entry.

## 3. Inputs and outputs
Inputs (map COPAU0A / mapset COPAU00, `bms/COPAU00.bms`): ACCTID X(11) UNPROT (:84-88); SEL0001..SEL0005 X(1) UNPROT; FSET echo of displayed fields. AID key.
Reads: CXACAIX xref by acct (`:818-820` → XREF-CARD-NUM), ACCTDAT by acct (`:869-871`), CUSTDAT by cust (`:919-920`), IMS PAUTSUM0 GU WHERE ACCNTID (`:973-977`), PAUTDTL1 GNP (:461-496) / qualified GNP reposition (:525-538).
Outputs: CNAME/CUSTID/ACCSTAT/PHONE1/APPRCNT/DECLCNT/CREDLIM/CASHLIM/APPRAMT/DECLAMT; rows PTRNID/PDATE/PTIME/PTYPE/A-D/PSTS/PAMT ×5; ERRMSG; header fields; outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COPAUS0C-01 | entry w/ acct id | context reads; NOTFND → error on screen | :754-755,:818-920 | FR-S19-02 |
| COPAUS0C-02 | summary GU STATUS-OK | summary counts/limits rendered | :973-993 | FR-S19-02 |
| COPAUS0C-03 | summary GU GE | empty list, context still shown | :981-983 | FR-S19-02 |
| COPAUS0C-04 | detail fill | ≤5 rows, newest-first (9's-complement asc) | :461-496,:545-593 | FR-S19-03 |
| COPAUS0C-05 | row format | date MM/DD/YY, time HH:MM:SS, A/D from resp '00' | :521-540 | FR-S19-03 |
| COPAUS0C-06 | PF8 | next page or 'You are already at the bottom of the page...' | :391-440,:403 | FR-S19-04 |
| COPAUS0C-07 | PF7 | previous page via PAUKEY-PREV-PG stack | :365-368 | FR-S19-05 |
| COPAUS0C-08 | 'S' row select | PAU-SELECTED=AUTH-KEYS(n), XCTL detail | :287-321 | FR-S19-06 |
| COPAUS0C-09 | non-S selection | evaluated, ignored (no error) | :313-321 | FR-S19-06 |
| COPAUS0C-10 | PF3 | XCTL CDEMO-TO-PROGRAM | :674-675 | FR-S19-11 |
| COPAUS0C-11 | IMS other-status | ' System error while ... Code:' + status, screen re-sent | :505-513,:530-538,:985-993 | FR-S19-03 |

## 5. Business rules and validations
ENTER: selection scan (:287-307) → 'S' hands off; else context chain CXACAIX→ACCTDAT→CUSTDAT→GU summary→GNP fill. Paging: PF7 loads start key from stack then repositions via qualified GNP (:365-368,:525); PF8 continues from PAUKEY-LAST; after 5 rows one extra GNP peeks for NEXT-PAGE flag (:435-449).

## 6. Data access and boundaries
- IMS reads via PSB PSBPAUTB (S19-B3, DECIDED: JPA `findById` + `findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc`; qualified GNP = key predicate). REPL not used here.
- VSAM reads (S19-B6, DECIDED: existing `CardXrefRepository.findByAcctId`, `AccountRepository`, `CustomerRepository`; read-only).
- Hand-off to COPAUS1C (S19-B2: `/ui/pending-auth/detail?key=`); return (B-012: back to menu). Paging stack (S19-B8: session/cursor).

## 7. Error and edge behavior
Non-'S' selection characters are evaluated then ignored (:313-321); summary GE leaves context visible with an empty list; the 5-row fill stops on AUTHS-EOF without error; extra GNP peek sets NEXT-PAGE (:435-449); IMS 'other' statuses stringify into the message line.

## 8. Hard-stop boundary
Delegates detail/fraud to COPAUS1C/COPAUS2C (same stream) and all menu/auth behavior to S-01. Never writes pending-auth data.

## 9. Demoted mechanics
`EXEC DLI SCHD/TERM` PSB schedule (:253-262) → n/a; `SEND/RECEIVE MAP` → Thymeleaf render; `RETURN TRANSID` → session; header ASSIGN fills → envelope.

## 10. Traceability
COPAUS0C-01..11 ↔ FR-S19-02..06,11 ↔ `PendingAuthServiceTests`/`PendingAuthControllerIT`.
