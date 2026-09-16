# CORPT00C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CORPT00C — `app/cbl/CORPT00C.cbl`. Stream S-10 (ONLINE half), wave 1.
- Role: transaction-report request screen — derives the date range for Monthly /
  Yearly / Custom, validates custom dates via CSUTLDTC, requires explicit
  confirmation, then writes a 14-card job stream (TRNRPT00 → TRANREPT) to TDQ
  `JOBS` for submission through the JES internal reader.

## 2. Trigger / caller contract
- CICS transaction `CR00` (`app/csd/CARDDEMO.CSD:409-410`), map CORPT0A / mapset
  CORPT00 (`app/bms/CORPT00.bms`). Reached from the main-menu route table.
- `EIBCALEN = 0` → XCTL `COSGN00C` (:172-174); otherwise COMMAREA moved to
  `CARDDEMO-COMMAREA` (:176-178).
- Pseudo-conversational loop: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(...)`
  (:199-201); WS-TRANID = 'CR00' (:38).
- PF3 → XCTL `COMEN01C` (:187-189); other AID → invalid-key message (:190-194).
- Exit path: RETURN-TO-PREV-SCREEN XCTLs `CDEMO-TO-PROGRAM`, defaulting to
  `COSGN00C` when blank (:540-551).

## 3. Inputs and outputs
Inputs (CORPT0AI): MONTHLYI/YEARLYI/CUSTOMI X(1) flags; SDTMMI/SDTDDI/SDTYYYYI
X(2)/X(2)/X(4); EDTMMI/EDTDDI/EDTYYYYI X(2)/X(2)/X(4); CONFIRMI X(1); AID key.
Reads: none (no file access — all validation is field edits + CSUTLDTC CALL).
Writes: extrapatrition TDQ `JOBS` (DDNAME=INREADER, 80-byte fixed, MOD —
`app/csd/CARDDEMO.CSD:499-505`), one `WRITEQ TD` per JCL card (:517-523).
Calls: `CSUTLDTC` ×2 (start then end) with (date X(10), format X(10), result)
(:388-426).
Outputs (CORPT0AO): ERRMSGO X(78) (green on success :445-456, else default red),
cursor positioning via `-1` length fields, standard header fields.

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CORPT00C-01 | MONTHLY flag non-blank | range = current-month-01..last-day (bump month w/ year rollover, minus-one-day arithmetic); name 'Monthly'; flows to SUBMIT-JOB-TO-INTRDR | :213-236, :229-230 | FR-S10-01 |
| CORPT00C-02 | YEARLY flag | range = year-01-01..year-12-31; name 'Yearly' | :239-253 | FR-S10-02 |
| CORPT00C-03 | CUSTOM flag | field-edit sequence over the six date fields | :256-258 | FR-S10-03 |
| CORPT00C-04 | multiple flags | first EVALUATE branch wins (Monthly > Yearly > Custom precedence) | :212-256 | FR-S10-04 |
| CORPT00C-05 | any custom field blank | `Start/End Date - <Month/Day/Year> cannot be empty...`, cursor, error flag | :258-303 | FR-S10-05 |
| CORPT00C-06 | month non-numeric or > '12' | `...Not a valid Month...` | :329-336, :354-360 | FR-S10-06 |
| CORPT00C-07 | day non-numeric or > '31' | `...Not a valid Day...` | :337-344, :362-368 | FR-S10-07 |
| CORPT00C-08 | year non-numeric | `...Not a valid Year...` | :346-352, :370-378 | FR-S10-08 |
| CORPT00C-09 | short digits (e.g. ' 7') | NUMVAL-C normalization → passes numeric/range edits as 07 | :305-327 | FR-S10-09 |
| CORPT00C-10 | dates assembled YYYY-MM-DD | CALL CSUTLDTC per date; sev 0000 accept; non-0000 with msg 2513 accept; otherwise `...Not a valid date...` | :388-426 | FR-S10-10 |
| CORPT00C-11 | CONFIRM blank | `Please confirm to print the <name> report...`, cursor CONFIRM | :464-473 | FR-S10-11 |
| CORPT00C-12 | CONFIRM N/n | INITIALIZE-ALL-FIELDS, screen resent, no submit | :478-483 | FR-S10-12 |
| CORPT00C-13 | CONFIRM other | `"<v>" is not a valid value to confirm...` | :485-493 | FR-S10-13 |
| CORPT00C-14 | CONFIRM Y/y | JOB-DATA cards written to TDQ 'JOBS' until '/*EOF'/spaces; success → INITIALIZE-ALL-FIELDS + green `<name> report submitted for printing ...` | :445-456, :498-535 | FR-S10-14 |
| CORPT00C-15 | WRITEQ RESP≠NORMAL | `Unable to Write TDQ (JOBS)...`, cursor MONTHLY, no success msg | :524-535 | FR-S10-15 |
| CORPT00C-16 | EIBCALEN=0 | XCTL COSGN00C | :172-174 | (S-01 seam) |
| CORPT00C-17 | PF3 | XCTL COMEN01C | :187-189 | FR-S10-20 |
| CORPT00C-18 | other AID | `Invalid key pressed. Please see below...` | :190-194 | FR-S10-20 |

## 5. Business rules and validations

Sequence in PROCESS-ENTER-KEY (:208-256): report-type EVALUATE (exactly one of
three WHENs fires — any non-blank flag counts; no "pick one" edit) → for Monthly/
Yearly compute range → SUBMIT-JOB-TO-INTRDR → for Custom run field edits
(blank checks → NUMVAL-C normalization → numeric + month≤12 + day≤31 + year
numeric) → assemble `YYYY-MM-DD` strings → two CSUTLDTC calls (tolerate sev≠0000
iff msg 2513) → SUBMIT-JOB-TO-INTRDR.
Submit sequence (:460-536): confirm-blank check → confirm-value EVALUATE
(Y / N / other) → write JOB-LINES(1..1000) one card per WRITEQ TD until
'/*EOF'/spaces/LOW-VALUES (:498-514). JOB-DATA (:81-127) = `//TRNRPT00 JOB
'TRAN REPORT'` + `//STEP10 EXEC PROC=TRANREPT` + SYMNAMES override
(TRAN-CARD-NUM,263,16,ZD; TRAN-PROC-DT,305,10,CH; PARM-START/END-DATE,C'..')
+ `//STEP10R.DATEPARM DD *` inline dates + `/*` + `/*EOF`.
**No start≤end comparison exists** — an inverted range submits and produces an
empty INCLUDE set (documented deviation D-1 in stream FR §11).

## 6. Data access and boundaries

- S10-B1 (module B-001): TDQ 'JOBS' intrdr submit → `POST /api/reports` /
  `ReportService.request` → `BatchJobLauncherService.launch("cbtrn03Job", params)`.
- S10-B5: CSUTLDTC calls → S-09's ported date-validation service (consume, never
  re-port). Message-table entry '2513' preserved in edit semantics.
- S10-B6: XCTLs → web routes (`/signon`, `/menu`, caller route).
- No direct file access — all persistence lives in the job half (CBTRN03C).

## 7. Error and edge behavior

- The confirm EVALUATE is exhaustive: blank prompts; Y submits; N clears; anything
  else is invalid-input — a second consecutive ENTER after an error re-runs the
  same edits (WS-ERR-FLG gates display only).
- Multiple report-type flags: first-match wins, silently — no "choose one"
  message exists.
- The submit loop caps at 1000 cards; JOB-DATA is 14 cards — '/*EOF' terminates.
- TDQ failure after some cards written: mainframe has no undo; target = single
  launch call (no partial state).

## 8. Hard-stop boundary

The 14th JCL card / the TDQ write completing submission. Everything downstream —
the TRNRPT00 job, TRANREPT steps, CBTRN03C — is the batch half of the stream,
not this program.

## 9. Demoted mechanics

COMMAREA plumbing (:157-178, :199-201); BMS cursor `-1` fields; header
population; JOB-LINES card-image table mechanics; RESP/RESP2 handling.
CEE3ABD is absent here — CORPT00C never abends; all failure paths are messages.

## 10. Traceability

CORPT00C-01..18 → FR-S10-01..15,20 → `ReportService` / `ReportController` /
`report.html` + `UiController` → service tests + MockMvc + UI spec tagged
FR-S10-xx.
