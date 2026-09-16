# S-10 Reports — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-16). Derived from `S10_reports_analysis.md` and source.
Language: English (source labels are English).
Encoding note: sources are ASCII; report output is EBCDIC-independent — cites are raw
line numbers.

## 1. Purpose and scope

Let a signed-on user request the **Daily Transaction Report** for a date range —
Monthly (current month), Yearly (current year), or Custom (explicit start/end) —
and submit it for batch production. The online half is screen CORPT0A (transaction
CR00, program CORPT00C); the batch half is job TRNRPT00 / proc TRANREPT (REPRO
unload → DFSORT filter/sort → CBTRN03C) producing `TRANREPT(+1)`. Process type
ONLINE+BATCH. Hard stop: the 133-column report file written by CBTRN03C STEP10R.
Exclusions: print/distribution downstream of the report dataset; CSUTLDTC internals
(S-09); menu shell return targets (S-01).

## 2. Actors and preconditions

- Actor: any signed-on CardDemo user (reached from the main menu route table —
  `COMEN02Y.cpy` option for CR00/CORPT00C).
- Preconditions: `transactions`, `card_xrefs`, `transaction_types`,
  `transaction_categories` populated (legacy: TRANSACT/CARDXREF/TRANTYPE/TRANCATG
  VSAM KSDS); no pre-submit check on data presence in legacy.

## 3. Surface specification

### Screen CORPT0A (`app/bms/CORPT00.bms`, fields `app/cpy-bms/CORPT00.CPY`)

| Field | Label (verbatim) | I/O | Len/PIC | Edits |
|---|---|---|---|---|
| MONTHLY | `Monthly (Current Month)` (bms:89-93) | INPUT | X(1), IC cursor | any non-blank selects (:213) |
| YEARLY | `Yearly (Current Year)` (bms:105) | INPUT | X(1) | any non-blank (:239) |
| CUSTOM | `Custom (Date Range)` (bms:114-118) | INPUT | X(1) | any non-blank (:256) |
| SDTMM/SDTDD/SDTYYYY | `Start Date :` `__/__/____` `(MM/DD/YYYY)` (bms:121-148) | INPUT | X(2)/X(2)/X(4), NUM | custom edits §4 |
| EDTMM/EDTDD/EDTYYYY | `End Date :` (bms:150-176) | INPUT | X(2)/X(2)/X(4), NUM | custom edits §4 |
| CONFIRM | `The Report will be submitted for printing. Please confirm:` `(Y/N)` (bms:198-211) | INPUT | X(1) | Y/y submit, N/n clear, other→error (:464-493) |
| ERRMSG | — | OUTPUT | X(78) RED | message line |
| Header | `Tran:`/`Date:`/`Prog:`/`Time:`/`Transaction Reports` (bms:29-79) | OUTPUT | — | standard header |

AID: ENTER = process; PF3 = back to menu (XCTL COMEN01C, :187-189); other AID =
`Invalid key pressed` (:190-194). No AID-0 path → unauthenticated entry XCTLs to
COSGN00C (:172-174).

### Report layout (CVTRA07Y.cpy, byte contract — 133 cols)

- `REPORT-NAME-HEADER`: `DALYREPT` + `Daily Transaction Report` + `Date Range: ` +
  start + ` to ` + end (CVTRA07Y.cpy:3-11).
- `TRANSACTION-HEADER-1/2`: column headers + 133×`-` rule (:14-38).
- `TRANSACTION-DETAIL-REPORT`: tran-id, account-id, `type-cd`-`type-desc(15)`,
  `cat-cd`-`cat-desc(29)`, source, `-ZZZ,ZZZ,ZZZ.ZZ` amount (:16-30).
- Totals lines: `Page Total`/`Account Total`/`Grand Total` + `.` leaders +
  `+ZZZ,ZZZ,ZZZ.ZZ` (:40-57).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S10-01 | Type select | MONTHLY non-blank | range = 1st..last day of current month; report name 'Monthly'; submit proceeds | CORPT00C | :213-236 | S10-B1/B2 | TBD |
| FR-S10-02 | Type select | YEARLY non-blank | range = Jan-01..Dec-31 of current year; name 'Yearly'; submit | CORPT00C | :239-253 | S10-B1/B2 | TBD |
| FR-S10-03 | Type select | CUSTOM non-blank | per-field date edits then submit with typed range; name 'Custom' | CORPT00C | :256-258 | — | TBD |
| FR-S10-04 | Precedence | multiple types non-blank | first EVALUATE match wins (Monthly→Yearly→Custom order) | CORPT00C | :212-256 | — | TBD |
| FR-S10-05 | Custom edit | any of 6 date fields blank | field-specific `... cannot be empty...` message, cursor on field, no submit | CORPT00C | :258-303 | — | TBD |
| FR-S10-06 | Custom edit | month not numeric or > '12' | `Start/End Date - Not a valid Month...` | CORPT00C | :329-336, :354-360 | — | TBD |
| FR-S10-07 | Custom edit | day not numeric or > '31' | `Start/End Date - Not a valid Day...` | CORPT00C | :337-344, :362-368 | — | TBD |
| FR-S10-08 | Custom edit | year not numeric | `Start/End Date - Not a valid Year...` | CORPT00C | :346-352, :370-378 | — | TBD |
| FR-S10-09 | Custom edit | input digits short of field (e.g. ' 7') | NUMVAL-C normalizes before range checks — ' 7'→07 accepted | CORPT00C | :305-327 | — | TBD |
| FR-S10-10 | Date validation | start/end pass field edits | CSUTLDTC (format 'YYYY-MM-DD'): sev 0000 accept; non-0000 accepted iff msg 2513; else `Start/End Date - Not a valid date...` | CORPT00C→CSUTLDTC | :388-426 | S10-B5 | TBD |
| FR-S10-11 | Confirm | CONFIRM blank | `Please confirm to print the <name> report...`, cursor CONFIRM, no submit | CORPT00C | :464-473 | — | TBD |
| FR-S10-12 | Confirm | CONFIRM N/n | all fields cleared, screen resent, no submit | CORPT00C | :478-483 | — | TBD |
| FR-S10-13 | Confirm | CONFIRM other | `"<v>" is not a valid value to confirm...`, no submit | CORPT00C | :485-493 | — | TBD |
| FR-S10-14 | Submit | CONFIRM Y/y | 14-card JCL (TRNRPT00 + SYMNAMES PARM-START/END-DATE + STEP10R.DATEPARM DD *) written to TDQ 'JOBS' → green `<name> report submitted for printing ...` | CORPT00C | :81-127, :445-456, :498-535 | S10-B1 | TBD |
| FR-S10-15 | Submit failure | TDQ write non-NORMAL | `Unable to Write TDQ (JOBS)...`, no success message | CORPT00C | :524-535 | S10-B1 | TBD |
| FR-S10-16 | Job steps | submitted/scheduled TRANREPT | STEP05R REPRO unload TRANSACT→BKUP(+1); STEP05R SORT INCLUDE proc-dt in [start,end] FIELDS card A →DALY(+1) | TRANREPT.jcl | :23-55 | S10-B3/B4/B8 | TBD |
| FR-S10-17 | Report content | CBTRN03C run | only rows with `TRAN-PROC-TS(1:10)` in [start,end]; ordered by card; detail lines carry id, acct (via XREF), type-cd+desc, cat-cd+desc, source, edited amount | CBTRN03C | :173-195, :361-373 | S10-B3 | TBD |
| FR-S10-18 | Report breaks | — | headers every 20 detail lines; `Page Total` per page; `Account Total` on card-number break; `Grand Total` at EOF | CBTRN03C | :131, :181-188, :198-203, :282-285 | — | TBD |
| FR-S10-19 | Report failure | any file IO error | DISPLAY diagnostics + step abends (CEE3ABD 999); no partial-tran-row output contract beyond buffered writes | CBTRN03C | :626-630 | S10-B7 | TBD |
| FR-S10-20 | Navigation | PF3 on CORPT0A | return to calling program (COMEN01C default via CDEMO-TO-PROGRAM; sign-on when session empty) | CORPT00C | :187-189, :540-551 | S10-B6 | TBD |

## 5. Validation and error catalogue

| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `Start/End Date - <part> cannot be empty...` | blank custom field | CORPT00C.cbl:259-303 | blocking | stay, cursor on field |
| `Start/End Date - Not a valid Month/Day/Year...` | range/numeric edit | :329-378 | blocking | stay |
| `Start/End Date - Not a valid date...` | CSUTLDTC sev≠0000, msg≠2513 | :399-405, :419-425 | blocking | stay |
| `Please confirm to print the <name> report...` | confirm blank | :464-473 | blocking | stay, cursor CONFIRM |
| `"<v>" is not a valid value to confirm...` | confirm ∉ {Y,y,N,n,blank} | :485-493 | blocking | stay |
| `Unable to Write TDQ (JOBS)...` | TDQ RESP≠NORMAL | :524-535 | blocking | stay |
| `<name> report submitted for printing ...` (GREEN) | successful submit | :445-456 | terminal | fields reset |
| `Invalid key pressed. Please see below...` | unmapped AID | :190-194 + CSMSG01Y | blocking | redisplay |

All message text is source-proven working-storage literals.

## 6. Field and data derivations

- Monthly range: start `YYYY-MM-01`; end = day before next-month-01 via
  `INTEGER-OF-DATE`/`DATE-OF-INTEGER` (:223-230) — i.e. last day of current month.
- Yearly range: `YYYY-01-01`..`YYYY-12-31` (:239-252).
- The two dates flow to the job twice: SYMNAMES sort parms (`PARM-START-DATE`,
  `PARM-END-DATE` — filter) and `STEP10R.DATEPARM` (`start<sp>end` X(10)+X(10) —
  report header + program filter, CBTRN03C.cbl:122-126, :173-174). In target: a
  single `startDate`/`endDate` JobParameters pair feeding the repository query and
  the header.
- Report account id: `XREF-ACCT-ID` resolved by `TRAN-CARD-NUM` (CBTRN03C.cbl:181-188).
- Amount edits: detail `-ZZZ,ZZZ,ZZZ.ZZ`; totals `+ZZZ,ZZZ,ZZZ.ZZ` (CVTRA07Y).

## 7. Mechanics (demoted, cited)

Pseudo-conversational RETURN TRANSID (:199-201); COMMAREA + EIBCALEN plumbing
(:157-178); BMS cursor positioning via `-1` length fields; JCL-RECORD 80-byte card
image table + `/*EOF` terminator scan (:498-514); TDQ write loop (:517-523);
CEE3ABD abend wrapper (CBTRN03C.cbl:626-630); page-counter arithmetic
(CBTRN03C.cbl:282-285).

## 8. Acceptance criteria (Given/When/Then) — one per FR

- FR-S10-01: Given the report screen in month M, When Monthly selected + ENTER +
  confirm Y, Then a report job is launched for [M-01 .. last day of M].
- FR-S10-02: Given year Y, When Yearly + confirm Y, Then job launched for
  [Y-01-01 .. Y-12-31].
- FR-S10-03: Given valid custom start/end, When Custom + confirm Y, Then job
  launched for the typed range.
- FR-S10-04: Given Monthly and Custom both flagged, When ENTER, Then Monthly wins.
- FR-S10-05..08: Given Custom with a blank/out-of-range/non-numeric field, When
  ENTER, Then the matching verbatim message, no launch.
- FR-S10-09: Given Custom month typed ' 7', When ENTER, Then treated as 07 (passes
  month edit).
- FR-S10-10: Given custom year '0000' with valid MM/DD, When ENTER, Then accepted
  (2513 tolerance) and submitted.
- FR-S10-11..13: Given confirm blank/N/other, When ENTER, Then prompt / clear+stay /
  invalid-confirm message; no launch.
- FR-S10-14: Given confirmed submit, Then POST-side effect = one job execution with
  `startDate`,`endDate` params; UI shows the green submitted message.
- FR-S10-15: Given the launch seam failing, Then `Unable to Write TDQ (JOBS)...`
  equivalent error surfaced, no success.
- FR-S10-16/17: Given seeded transactions, When the job runs, Then output contains
  exactly the in-range rows ordered by card then tran-id with the resolved acct,
  type, category columns.
- FR-S10-18: Given >20 in-range rows and ≥2 cards, Then page rule/header every 20
  lines, an account-total line per card, a grand-total line at end.
- FR-S10-19: Given a data-access failure mid-run, Then the step fails (job exit
  non-zero) — no silent truncation.
- FR-S10-20: Given the report screen, When PF3/Back, Then the caller's screen/menu
  is shown (or sign-on when no session).

## 9. Traceability matrix

FR-S10-01..15,20 → CORPT00C → cites §4 → tests TBD (assigned in migration plan).
FR-S10-16..19 → TRANREPT/CBTRN03C → cites §4 → TBD.

## 10. Program index

| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| CORPT00C | report-request screen/validation/submit | FR-S10-01..15, 20 | [programs/CORPT00C_functional_requirement.md](programs/CORPT00C_functional_requirement.md) |
| CBTRN03C | report program | FR-S10-16..19 | [programs/CBTRN03C_functional_requirement.md](programs/CBTRN03C_functional_requirement.md) |
| TRANREPT | job shell (REPRO→SORT→CBTRN03C) | FR-S10-16 | JCL artifact, no program FR |
| CSUTLDTC | shared date utility | FR-S10-10 | [programs/CSUTLDTC_functional_requirement.md](programs/CSUTLDTC_functional_requirement.md) (S-09) |

## 11. Open questions and assumptions

1. **Byte-fidelity of report output**: target writes a flat file equivalent; exact
   reproduction of the 133-column CVTRA07Y layout (headers, rules, leaders) is
   assumed required unless sign-off relaxes it. Assumption A-1.
2. **Inverted range**: legacy submits it silently (empty report); baseline API
   rejects. Recorded deviation D-1 — keep the rejection, it's strictly safer.
3. **2513 tolerance**: reachable only for years outside 1582..9999 on a 4-digit
   field after the numeric edits — effectively year '0000'. Kept as accept per
   source (:399-405); LocalDate can't express it — in target this cell cannot
   produce a launchable parameter; document as unreachable-in-target edge.
4. Report output format (fixed 133-col EBCDIC-free text) — confirmed ASCII-safe
   target file.
