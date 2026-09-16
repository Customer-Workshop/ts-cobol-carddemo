# S-17 — Data Read / Verify Jobs — Functional Requirements

## 1. Purpose and scope

Migrate the four READ* verify jobs — READACCT (CBACT01C), READCARD (CBACT02C), READCUST (CBCUS01C), READXREF (CBACT03C) — plus the assembler date-edit COBDATFT, to Java Spring Batch jobs launched via `POST /api/admin/jobs/{jobName}`. Behavior parity is defined by source: sequential full-file reads in key order, record dumps to the job log, fixed/variable flat-file outputs for READACCT, and abend-on-file-error semantics. Scope: `devin/1789516557-carddemo-java-engagement`; docs in `functional/CARDDEMO/`; code later in `spring-boot/`.

## 2. Actors and preconditions

- **Actor**: batch operator / scheduler, launching each job explicitly (the CA-7 chain becomes a documented job-order config, not an enforced engine).
- **Preconditions**: `accounts`/`cards`/`card_xrefs`/`customers` tables populated (VSAM equivalents); `spring.batch.job.enabled=false`; each run gets fresh `run.id`; output directory `carddemo.batch.output-dir` writable. Operational parity note: legacy ran these inside the file-close window (CLOSEFIL…OPENFIL, ca7:340-453) — the target preserves the order contract in job-order config only.

## 3. Surface specification

| Legacy surface | Target surface |
|---|---|
| JCL job READACCT | `readacctJob` (`Cbact01JobConfiguration`), single step |
| JCL job READCARD | `readcardJob` (`Cbact02JobConfiguration`) |
| JCL job READCUST | `readcustJob` (`Cbcus01JobConfiguration`) |
| JCL job READXREF | `readxrefJob` (`Cbact03JobConfiguration`) |
| ACCTFILE/CARDFILE/CUSTFILE/XREFFILE KSDS | `RepositoryItemReader`/`JpaPagingItemReader` sorted by PK |
| OUTFILE→PSCOMP FB 107 | `<output-dir>/ACCTDATA.PSCOMP` fixed-width 107-char lines |
| ARRYFILE→ARRYPS FB 110 | `<output-dir>/ACCTDATA.ARRYPS` fixed-width 110-char lines |
| VBRCFILE→VBPS VB 84 (recs 12/39) | `<output-dir>/ACCTDATA.VBPS` variable-length lines 12 and 39 chars |
| PREDEL deletes (IEFBR14) | writer `shouldDeleteIfExists` on each output |
| SYSOUT DISPLAY | job log (SLF4J) per-record dump lines |
| `CALL 'COBDATFT'` | `util/DateEditService` |
| `CALL 'CEE3ABD' ABCODE 999` | step failure → exit code 999 equivalent |
| CA-7 trigger order | job-order config + explicit launches in order |

## 4. Functional requirements (KEEP)

| FR | Flow | Trigger | Result | Program | Cite | Boundary | Test |
|---|---|---|---|---|---|---|---|
| S17-FR-01 | Read every account row in `acctId` order, dump raw record then per-field labelled lines to log | `readacctJob` launch | one dump block per row; EOF at status '10' | CBACT01C | CBACT01C.cbl main loop | S17-B2/B6 | row count = table count; sample fields equal |
| S17-FR-02 | Write PSCOMP record per account: field-for-field layout, zoned-decimal display numerics, COMP-3 `CURR-CYC-DEBIT`, pad to 107 | per account | 107-char line appended | CBACT01C | READACCT.jcl:37-39; WS layout | S17-B5/B7 | golden-file byte compare vs expected fixture |
| S17-FR-03 | Reformat `ACCT-REISSUE-DATE` `YYYY-MM-DD`→`YYYYMMDD` via DateEditService type '2'→'2', first 10 bytes into field | per account | reformatted date in PSCOMP + VB2 year | CBACT01C+COBDATFT | CBACT01C.cbl:231 | S17-B3 | known-date rows produce 8-digit output + 2 pad chars |
| S17-FR-04 | Write ARRYPS record: acct-id + 5×(curr-bal, cyc-debit COMP-3) using constants 1005.00/1525.00/-1025.00/-2500.00, pad to 110 | per account | 110-char line | CBACT01C | CBACT01C.cbl:256-260 | S17-B5/B7 | constants present at fixed offsets |
| S17-FR-05 | Write VBPS variable records: VB1 = 12 chars (id+status), VB2 = 39 chars (id+curr-bal+credit+reissue-YYYY) | per account | two lines per account, lengths 12 then 39 | CBACT01C | READACCT.jcl:45-47 | S17-B5 | line lengths alternate 12/39 per record pair |
| S17-FR-06 | Substitute constant `2525.00` when `ACCT-CURR-CYC-DEBIT` = 0 | per account | PSCOMP field shows 2525.00 | CBACT01C | CBACT01C.cbl:236-237 | — | zero-debit row yields constant |
| S17-FR-07 | Abend: any file status other than '00'/'10' on read or write → step failure + exit code 999 equivalent | open/read/write error | job fails, non-zero exit | all four | CBACT01C.cbl:410 etc. | S17-B4 | forced read failure → FAILED + code |
| S17-FR-08 | Read every card row in `cardNumber` order; dump `CARD-RECORD` once per row to log | `readcardJob` | one dump line per row | CBACT02C | CBACT02C.cbl:78,96 (commented) | S17-B2/B6 | exactly one line per row |
| S17-FR-09 | Read every xref row in `xrefCardNumber` order; dump `CARD-XREF-RECORD` **twice** per row | `readxrefJob` | two dump lines per row (parity quirk) | CBACT03C | CBACT03C.cbl:78,96 | S17-B2/B6 | lines = 2× row count |
| S17-FR-10 | Read every customer row in `custId` order; dump `CUSTOMER-RECORD` **twice** per row | `readcustJob` | two dump lines per row | CBCUS01C | CBCUS01C.cbl:78,96 | S17-B2/B6 | lines = 2× row count |
| S17-FR-11 | Job order `readacctJob → readcardJob → readcustJob → readxrefJob` documented; each waits on predecessor success | ops run | chain completes in order | — | ca7:340-453 | S17-B1 | job-order config listed; exit non-zero skips rest |
| S17-FR-12 | DateEditService `'1'`→`'1'`: `YYYYMMDD`→`YYYY-MM-DD`; `'2'`→`'2'`: `YYYY-MM-DD`→`YYYYMMDD`; anything else → `INVALID INPUT` in error field, return value normal | direct call | per contract | COBDATFT | COBDATFT.asm | S17-B3 | unit table over all four type combos + bad type |

## 5. Error catalogue

| Condition | Legacy behavior | Target |
|---|---|---|
| Open error | abend 999 | step FAILED, exit non-zero |
| Read/write status not 00/10 | display status then abend 999 | log status then step FAILED |
| DateEdit bad type/outtype | `INVALID INPUT` in COERMSG, RC 0 | `errorMessage="INVALID INPUT"`, normal return — caller ignores it, preserving silent-garbage parity |

## 6. Field/data derivations

- PSCOMP layout: `OUT-ACCT-REC` — id 11 + status 1 + three money fields zoned 12 each + 3 dates 10 each + cyc-credit zoned 12 + cyc-debit COMP-3 6 + group 10 ≈ 106 bytes + pad → 107 (INFERRED pad, S17-B7).
- ARRYPS layout ≈ 105 bytes + pad → 110 (INFERRED pad, S17-B7).
- Zoned-decimal writes use `-fsign=EBCDIC` overpunch semantics via the writer analogue of `CobolFieldReader`.
- VB2 year = first 4 chars of the *source* `YYYY-MM-DD` reissue date, not the reformatted output.

## 7. Mechanics (non-functional)

- `spring.batch.job.enabled=false`; explicit launch only; each run unique `run.id`.
- Chunk-oriented or tasklet steps; full-table scans are single-pass — no commit/checkpoint mid-job (matches legacy).
- H2 for unit tests; Postgres profile for integration.

## 8. Acceptance criteria

- **Given** a populated `accounts` table **when** `readacctJob` runs **then** the log shows one dump block per account and the output dir contains PSCOMP/ARRYPS/VBPS byte-faithful to the layouts above, and rerunning deletes prior files.
- **Given** `cards` populated **when** `readcardJob` runs **then** exactly one dump line per card.
- **Given** `card_xrefs` / `customers` populated **when** `readxrefJob` / `readcustJob` run **then** two dump lines per row.
- **Given** a forced repository error **when** any S-17 job runs **then** the step fails and the process reports the 999-equivalent exit.
- **Given** `('1','20250916','1')`, `('2','2025-09-16','2')`, `('1',...,'2')`, `('3',...)` **when** DateEditService is called **then** outputs are `2025-09-16`, `20250916`, `INVALID INPUT`, `INVALID INPUT` respectively.

## 9. Traceability

Each FR carries program + cite + boundary id; wave mapping in `S17_data_read_verify_migration_plan.md`; program-level detail in `functional/CARDDEMO/programs/*.md` for CBACT01C, CBACT02C, CBACT03C, CBCUS01C, COBDATFT.

## 10. Program index

| Program | Job | FRs | Program FR doc |
|---|---|---|---|
| CBACT01C | readacctJob | 01–07 | programs/CBACT01C_functional_requirement.md |
| CBACT02C | readcardJob | 07–08 | programs/CBACT02C_functional_requirement.md |
| CBACT03C | readxrefJob | 07, 09 | programs/CBACT03C_functional_requirement.md |
| CBCUS01C | readcustJob | 07, 10 | programs/CBCUS01C_functional_requirement.md |
| COBDATFT | (library) | 12 | programs/COBDATFT_functional_requirement.md |

## 11. Open questions

- S17-B7 pad content for the two short layouts (space pad proposed).
- Whether CA-7 order needs enforcement hooks or documentation suffices (default: docs only).
