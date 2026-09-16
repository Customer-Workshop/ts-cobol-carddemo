# CBTRN03C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBTRN03C — `app/cbl/CBTRN03C.cbl`. Stream S-10 (BATCH half), wave 1.
- Role: daily-transaction report writer — reads the sorted/filtered DALY(+1)
  extract, resolves account and description fields, and emits a 133-column print
  report with page, account, and grand totals (`TRANREPT(+1)`).

## 2. Trigger / caller contract
- Executed as STEP10R of job TRANREPT (`app/jcl/TRANREPT.jcl:59-80`) — also the
  submitted job TRNRPT00 from CORPT00C (in-stream `//STEP10R.DATEPARM DD *`,
  `CORPT00C.cbl:112-119`).
- Parameters: DATEPARM file (PS `AWS.M2.CARDDEMO.DATEPARM` or in-stream DD) =
  `WS-START-DATE` X(10) + `WS-END-DATE` X(10) (:122-126); read once at open
  (:220-233); on DATEPARM read error → DISPLAY + abend path.
- No linkage; batch entry via `EXEC PGM=CBTRN03C`.

## 3. Inputs and outputs
Inputs: TRANFILE seq (`TRANSACT.DALY(+1)`, TRAN-RECORD 350B — `CVTRA05Y.cpy`);
CARDXREF KSDS keyed by card (50B — `CVACT03Y.cpy`); TRANTYPE KSDS (60B —
`CVTRA03Y.cpy`); TRANCATG KSDS (60B — `CVTRA04Y.cpy`); DATEPARM.
Outputs: TRANREPT report file LRECL 133 FB (`TRANREPT.jcl:76-80`), laid out by
`CVTRA07Y.cpy`. DISPLAY diagnostics to SYSOUT.

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBTRN03C-01 | startup | files opened; DATEPARM read → WS-START/END-DATE; 'Reporting from <s> to <e>' displayed | :164-233 | FR-S10-16 |
| CBTRN03C-02 | detail row | written iff `TRAN-PROC-TS(1:10)` in [start,end] inclusive | :173-174 | FR-S10-17 |
| CBTRN03C-03 | card-number break | first row of a new card → XREFFILE keyed read for account id; account-total for previous card emitted | :181-188 | FR-S10-18 |
| CBTRN03C-04 | per row | TRANTYPE keyed lookup → type desc; TRANCATG keyed lookup → cat desc | :189-195 | FR-S10-17 |
| CBTRN03C-05 | detail line | CVTRA07Y TRANSACTION-DETAIL-REPORT: id, acct, `cd`-`desc15`, `cd`-`desc29`, source, `-ZZZ,ZZZ,ZZZ.ZZ` amount | :361-373; CVTRA07Y:16-30 | FR-S10-17 |
| CBTRN03C-06 | every 20 lines | page headers + rule + running `Page Total` | :131, :282-285 | FR-S10-18 |
| CBTRN03C-07 | card break | `Account Total` line (+ZZZ,ZZZ,ZZZ.ZZ) | :198-203; CVTRA07Y:45-49 | FR-S10-18 |
| CBTRN03C-08 | EOF | `Grand Total` line; files closed | :198-213; CVTRA07Y:51-57 | FR-S10-18 |
| CBTRN03C-09 | any file IO error | DISPLAY + IO-STATUS + `CALL 'CEE3ABD'` abend 999 | :626-630 | FR-S10-19 |
| CBTRN03C-10 | header | REPORT-NAME-HEADER + 'Date Range: ' + start+' to '+end at report start | CVTRA07Y:3-11 | FR-S10-17 |

## 5. Business rules and validations

Sequential sweep: date filter is a string compare on `TRAN-PROC-TS(1:10)` (ISO
text dates, so lexicographic = chronological). Input is already sorted by
TRAN-CARD-NUM by the upstream DFSORT (TRANREPT.jcl:46-48) — the program assumes
card order for its break logic. Page size fixed 20 (:131). No runtime edits on
DATEPARM content — malformed parm reads as garbage-in.

## 6. Data access and boundaries

- S10-B3: TRANFILE/CARDXREF/TRANTYPE/TRANCATG VSAM reads →
  `TransactionRepository.findByTranProcessTimestampBetween(start,end)` +
  `CardXrefRepository` + `TransactionTypeRepository` +
  `TransactionCategoryRepository` (all JPA; physical layer resolved — VSAM KSDS).
- S10-B2: DATEPARM → `startDate`/`endDate` JobParameters.
- S10-B4: TRANREPT(+1) → `cbtrn03-report.txt` flat file under job output dir.
- S10-B7: CEE3ABD 999 → exception → step FAILED → job exit non-zero.

## 7. Error and edge behavior

- All errors funnel to 9999-ABEND-PROGRAM via Z-DISPLAY-IO-STATUS + CEE3ABD
  (abend code 999) — no partial-file cleanup; target equivalent = step failure.
- EOF without any matching row: still writes headers + grand total 0 — no
  empty-report special case.
- Account totals rely on input sort order; an out-of-order card just splits one
  account into two total blocks (garbage-in tolerated, no assertion).

## 8. Hard-stop boundary

TRANREPT(+1) closed at step end. No downstream consumers inside the stream.

## 9. Demoted mechanics

AT END/end-of-file flag plumbing; page/counter arithmetic; MOVE-based line
assembly; DISPLAY tracing; file open/close boilerplate.

## 10. Traceability

CBTRN03C-01..10 → FR-S10-16..19 → `Cbtrn03JobConfiguration` +
`BatchJobService.reportLine` + `ReportLineAggregator` → golden-file test of
`cbtrn03-report.txt` vs a hand-derived CVTRA07Y report.
