# S-19 Pending Authorization View — Stream Functional Requirements (`!mf_stream_fr_generation`)

Stream: S-19 (ONLINE). Analysis: `S19_pending_auth_view_analysis.md`. Plan: `S19_pending_auth_view_migration_plan.md`.
Program FRs: `programs/COPAUS0C_*.md`, `programs/COPAUS1C_*.md`, `programs/COPAUS2C_*.md`.

## 1. Purpose and scope

Pending-authorization self-service view: from main-menu option 11 a user sees account/customer context plus the pending-auth summary (counts and amounts) and up to five pending authorizations, selects one for a read-only detail screen, pages forward/backward across the remaining details, and can mark or unmark an authorization as fraud — which writes the AUTHFRDS journal and flips the segment's `PA-AUTH-FRAUD` flag.

In scope: COPAUS0C (list), COPAUS1C (detail + fraud toggle), COPAUS2C (fraud journal write). Out of scope: producing authorizations (S-20) and the MQ demo (S-22).

## 2. Actors and preconditions

- Signed-on user type 'U' (option 11 carries `requiredUserType 'U'`, `COMEN02Y.cpy:89`); reached via menu XCTL; `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` carried in the COMMAREA/session from prior screens — the stream *also* accepts the acct id as a screen input (`ACCTID` field).
- Preconditions: `pending_auth_summary`/`pending_auth_detail`/`auth_frauds` tables exist (V2601); account xref/account/customer rows exist (shared data from S-02..S-05 seeds).

## 3. Surface specification

### List screen COPAU0A (`bms/COPAU00.bms`) → `/ui/pending-auth`

INPUT: `acctId` (X(11), numeric), `sel[1..5]` (X(1), 'S' to select).
DISPLAY: customer name, cust id, acct status, phone; approval count, decline count, credit limit, cash limit, approved amt, declined amt; per-row: transaction id, date (MM/DD/YY), time (HH:MM:SS), type, A/D, STS, amount.
Footer: `Type 'S' to View Authorization details from the list` / `ENTER=Continue F3=Back F7=Backward F8=Forward` (`COPAU00.bms:501,:511`).

### Detail screen COPAU1A (`bms/COPAU01.bms`) → `/ui/pending-auth/detail`

DISPLAY-only: card #, auth date/time, resp code+reason, auth code, amount, POS entry mode, source, MCC, card expiry, auth type, tran id, match status, fraud status, merchant name/id/city/state/zip.
Footer: `F3=Back F5=Mark/Remove Fraud F8=Next Auth` (`COPAU01.bms:292`).

### API endpoints

`GET /api/pending-auth/{acctId}` (context + first page), `GET /api/pending-auth/{acctId}/details?after={key}&dir=fwd|bwd` (paged detail keys), `GET /api/pending-auth/{acctId}/details/{authKey}` (one detail), `POST /api/pending-auth/{acctId}/details/{authKey}/fraud` (toggle). Response shape = COPAU1A field set.

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S19-01 | entry guard | menu opt 11, program implemented | route to pending-auth screen | COMEN01C→COPAUS0C | COMEN01C.cbl:147-162 | S19-B1 | MenuService flag test |
| FR-S19-02 | list entry | ENTER with acct id (or session acct) | account+customer context + summary + ≤5 rows | COPAUS0C | :754-755,:973-977 | S19-B3,B6 | list API test |
| FR-S19-03 | page fill | GNP PAUTDTL1 loop | rows newest-first (9's-complement asc) | COPAUS0C | :461-496,:545-593 | S19-B3 | paging order test |
| FR-S19-04 | page fwd | PF8 | next ≤5 rows or 'already at the bottom' | COPAUS0C | :391-440 | S19-B8 | paging test |
| FR-S19-05 | page back | PF7 | previous page from 20-deep stack | COPAUS0C | :365-368 | S19-B8 | paging test |
| FR-S19-06 | select | 'S' on row | detail screen for that auth key | COPAUS0C→COPAUS1C | :287-321 | S19-B2 | select test |
| FR-S19-07 | detail render | entry/select | all CIPAUDTY fields + statuses shown | COPAUS1C | :439-496 | S19-B3 | detail API test |
| FR-S19-08 | mark fraud | F5 when not flagged | AUTHFRDS row written, segment AUTH_FRAUD='F', 'AUTH MARKED FRAUD...' | COPAUS1C+COPAUS2C | :228-264,:525-558 | S19-B4,B5,B7 | fraud flow test |
| FR-S19-09 | remove fraud | F5 when flagged F | AUTHFRDS row written w/ 'R', flag cleared, 'AUTH FRAUD REMOVED...' | COPAUS1C+COPAUS2C | :237-241 | S19-B4,B5,B7 | fraud flow test |
| FR-S19-10 | next auth | F8 | next detail in GNP order | COPAUS1C | :488-522 | S19-B3 | navigation test |
| FR-S19-11 | exit | F3 | return to caller (menu) | both | :675,:368 | B-012 | nav test |
| FR-S19-12 | fraud write dedup | AUTHFRDS PK clash (-803) | UPDATE path, still 'success' | COPAUS2C | :200-232 | S19-B5 | upsert test |

## 5. Validation and error catalogue

| Condition | Message / result (verbatim) | Cite |
|---|---|---|
| PF8 at last page | `You are already at the bottom of the page...` | COPAUS0C.cbl:403-404 |
| IMS read failure (summary) | ` System error while reading AUTH Summary: Code:` + status | COPAUS0C.cbl:985-993 |
| IMS read failure (details) | ` System error while reading AUTH Details: Code:` + status | COPAUS0C.cbl:505-513 |
| IMS reposition failure | ` System error while repos. AUTH Details: Code:` + status | COPAUS0C.cbl:530-538 |
| Detail-screen read failures | ` System error while reading Auth Summary/Auth Details/reading next Auth: Code:` | COPAUS1C.cbl:456-522 |
| Fraud update failure | ` System error while FRAUD Tagging, ROLLBACK||` + status | COPAUS1C.cbl:550-556 |
| DB2 write failure | ` SYSTEM ERROR DB2: CODE:` + code + `, STATE: ` + state | COPAUS2C.cbl:213 |
| DB2 success text | `ADD SUCCESS` / `UPDT SUCCESS` (internal status) | COPAUS2C.cbl:201,:232 |
| Fraud journal msgs | `AUTH MARKED FRAUD...` / `AUTH FRAUD REMOVED...` | COPAUS1C.cbl:540-546 |

## 6. Field and data derivations

- Row date: `PA-AUTH-ORIG-DATE` YYMMDD → `MM/DD/YY` (COPAUS0C.cbl:527-532); time `PA-AUTH-ORIG-TIME` HHMMSS → `HH:MM:SS` (:521-525); A/D = 'A' iff `PA-AUTH-RESP-CODE='00'` else 'D' (:534-540).
- Auth key on wire = `PA-AUTHORIZATION-KEY` = `PAUT9CTS` (complemented) X(8)-formatted; URL-safe authKey = the real timestamp composite `auth_date_9c:auth_time_9c`.
- AUTH_TS for AUTHFRDS = real (uncomplemented) `YY-MM-DD HH.MM.SS.mmm` via complement arithmetic (COPAUS2C.cbl:106-110).
- FRAUD_RPT_DATE = CURRENT DATE (DB2) (COPAUS2C.cbl:190).

## 7. Mechanics (demoted, cited)

`EXEC DLI SCHD/TERM` PSB scheduling (COPAUS0C.cbl:253-262, COPAUS1C.cbl:575-584) → nothing (JPA); `EXEC CICS SYNCPOINT` → `@Transactional` commit; `RETURN TRANSID` → session-backed re-entry; `DISPLAY 'RPT DT:'` (COPAUS1C.cbl:528) → logging; ASSIGN-time header fills → response envelope.

## 8. Acceptance criteria (Given/When/Then) — one per FR

- FR-S19-01: Given signed-on 'U' user and S-19 landed, When menu option 11 selected, Then pending-auth screen renders (not "not installed").
- FR-S19-02/03: Given acct 11111111111 has a summary + 7 details, When user enters it and presses ENTER, Then context + counts + amounts + 5 newest rows display newest-first.
- FR-S19-04/05: Given ≥6 rows, When F8 then F7, Then page 2 then page 1 render with 'already at bottom/top' at the ends.
- FR-S19-06/07: Given a listed row, When 'S' selected, Then its full detail renders.
- FR-S19-08: Given unflagged auth, When F5, Then auth_frauds row (F) exists, segment flag 'F', message shown.
- FR-S19-09: Given flagged auth, When F5, Then row written with 'R', flag cleared, removed message shown.
- FR-S19-10: Given ≥2 details, When F8, Then next (next-newest) detail renders.
- FR-S19-12: Given an existing (CARD_NUM,AUTH_TS) journal row, When fraud action re-runs, Then row updated, action reported success.

## 9. Traceability matrix

| FR | Program FR | Java surface |
|---|---|---|
| FR-S19-01..06 | COPAUS0C-01.. | `PendingAuthService`, `PendingAuthController`, `pending-auth.html` |
| FR-S19-07..10 | COPAUS1C-01.. | `PendingAuthDetailService`, detail endpoint/screen |
| FR-S19-08,09,12 | COPAUS2C-01.. | `AuthFraudService` + `AuthFraudRepository` |

## 10. Program index

| Program | Role | Program FR |
|---|---|---|
| COPAUS0C | list screen | programs/COPAUS0C_functional_requirement.md |
| COPAUS1C | detail + fraud toggle | programs/COPAUS1C_functional_requirement.md |
| COPAUS2C | fraud journal write | programs/COPAUS2C_functional_requirement.md |

## 11. Open questions and assumptions

1. PAUT9CTS is stored complemented; exposing it raw on the URL is opaque — we expose `authKey = date9c:time9c` and keep segment order semantics in the repository. Assumed acceptable (internal key only).
2. The list screen's acct search reads CXACAIX *by account* — if the acct has multiple cards the program takes the first xref row it gets; preserved (no fan-out).
3. Decline-reason text (resp reason codes) is displayed raw; no decode table exists in source — none added.
