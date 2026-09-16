# CBACT04C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBACT04C — `app/cbl/CBACT04C.cbl` (~707 lines). Stream S-15, wave 1.
- Role: monthly interest calculator — sweeps transaction-category balances by
  account, applies disclosure-group interest rates, writes interest
  transactions, and updates account balances + cycle totals.

## 2. Trigger / caller contract
- Executed as STEP15 of job INTCALC (`app/jcl/INTCALC.jcl:3`) —
  `EXEC PGM=CBACT04C,PARM='2022071800'` inside the MONTHLY-InterestCalculation
  Control-M chain (`CardDemo.controlm:64-90`).
- Linkage: `EXTERNAL-PARMS` — `PARM-LENGTH S9(04) COMP` + `PARM-DATE X(10)`
  (:176-180); JCL PARM is 10 chars `yyyymmddhh`; PARM-DATE seeds the interest
  transaction-id prefix.
- RC 0 on success; CEE3ABD abend on any file-status failure or unresolvable
  rate/account/xref.

## 3. Inputs and outputs
Inputs: TCATBALF KSDS seq (TRAN-CAT-BAL-RECORD 50B, `CVTRA01Y.cpy`); XREFFIL1
KSDS AIX-path keyed by acct (CARD-XREF-RECORD, `CVACT03Y.cpy`); ACCTFILE KSDS
I-O (ACCOUNT-RECORD 300B, `CVACT01Y.cpy`); DISCGRP KSDS (DIS-GROUP-RECORD,
`CVTRA02Y.cpy`); PARM (10-char run date).
Outputs: TRANSACT = SYSTRAN(+1) GDG, interest TRAN-RECORDs 350B
(`CVTRA05Y.cpy`); ACCTFILE REWRITE (balance + cycle reset); SYSOUT DISPLAY.

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBACT04C-01 | startup | open files; APPL-RESULT checks; first TCATBALF read | :183-186, :291-340 | FR-S15-01 |
| CBACT04C-02 | acct boundary | prior acct: `CURR-BAL += WS-TOTAL-INT`, `CYC-CREDIT=0`, `CYC-DEBIT=0`, REWRITE; then read new ACCTFILE + XREF (AIX) | :196-206, :343-375, :395-413 | FR-S15-02/09 |
| CBACT04C-03 | per tcatbal | DISCGRP keyed read (group+type+cat) | :210-213, :415-435 | FR-S15-03 |
| CBACT04C-04 | status 23 | retry key with DIS-ACCT-GROUP-ID = 'DEFAULT' | :436-444 | FR-S15-04 |
| CBACT04C-05 | DEFAULT also fails | abend | :444-457 | FR-S15-05 |
| CBACT04C-06 | rate ≠ 0 | `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200` (COBOL truncation); ADD to WS-TOTAL-INT; write SYSTRAN interest rec | :463-469, :214-216 | FR-S15-06/07/08 |
| CBACT04C-07 | interest rec | id = `PARM-DATE`+6-digit counter; type '01'; cat '05'; source 'System'; desc 'Int. for a/c <acct>'; amt; card=XREF-CARD-NUM; orig=proc=current DB2 ts | :473-514 | FR-S15-08 |
| CBACT04C-08 | missing acct or xref | INVALID KEY → 'ACCOUNT NOT FOUND' display → abend (status 23 ≠ '00') | :367-375, :395-411 | FR-S15-11 + §5 |
| CBACT04C-09 | EOF | final 1050-UPDATE-ACCOUNT for last account; close; RC 0 | :221-225 | FR-S15-10 |
| CBACT04C-10 | fees | 1400-COMPUTE-FEES = 'To be implemented' stub — no effect | :216, :518-520 | FR-S15-12 |
| CBACT04C-11 | any IO error | DISPLAY + IO-STATUS + CEE3ABD 999 | :326-340 and siblings | FR-S15-11 |

## 5. Business rules and validations

Sweep order = KSDS key order (acct, type, cat); account is updated exactly once
per group (on break or EOF). Rate lookup chain: account group → 'DEFAULT' →
abend. Missing account/xref → abend (not skip). No per-record validation edits
on TCATBALF — garbage balances accrue garbage interest (garbage-in tolerated).
`/1200` = annual % → monthly fraction. Fees path dead.

## 6. Data access and boundaries

- S15-B1: TCATBALF/ACCTFILE/DISCGRP → JPA repositories (ordered reader,
  keyed read+update, keyed read).
- S15-B2: XREFFIL1 AIX → `findByXrefAcctId` index read; miss → step failure.
- S15-B3: SYSTRAN(+1) → direct `transactions` insert (COMBTRAN eliminated).
- S15-B4: PARM → `parmDate` JobParameter; TRAN-ID derivation.
- S15-B7: CEE3ABD → exception → step FAILED.

## 7. Error and edge behavior

- All file statuses ≠ '00' → abend (INVALID KEY included for acct/xref; DISCGRP
  gets the DEFAULT retry first).
- Accounts without any tcatbal rows are untouched — no cycle reset.
- Rerun is additive (interest posts again) — non-idempotent, source-faithful.
- Zero balances with nonzero rate → interest 0 → txn written with amt 0
  (WS-MONTHLY-INT=0 writes? — the write is guarded by `DIS-INT-RATE ≠ 0` only,
  so a zero-balance category with a rate still writes a 0-amount txn — edge
  noted, verify against 1200-B-WRITE-TX call site :214-216).

## 8. Hard-stop boundary

SYSTRAN(+1) closed + final REWRITE + RC. COMBTRAN merge is a separate chain
step (eliminated in target — direct insert is equivalent).

## 9. Demoted mechanics

APPL-RESULT status plumbing; EXTERNAL-PARMS linkage; DISPLAY tracing; flag
arithmetic; DB2-format timestamp routine.

## 10. Traceability

CBACT04C-01..11 → FR-S15-01..13 → `Cbact04JobConfiguration` +
`AccountInterestReader` + `BatchJobService.calculateInterest`/`writeInterest` →
unit + integration tests.
