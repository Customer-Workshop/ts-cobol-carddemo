# CBTRN01C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBTRN01C — `app/cbl/CBTRN01C.cbl` (494 lines). Stream S-14, wave 1.
- Role: **orphan** validation sweep — reads the daily transaction file, verifies
  card→xref→account resolution, and DISPLAYs diagnostics. **No writes, no
  caller**: no JCL job references it (inventory §3.4); functionally a dry-run of
  CBTRN02C's validate half.

## 2. Trigger / caller contract
- No JCL executes it — dead code upstream. Kept because the baseline ports it as
  `cbtrn01Job` (a manual validation utility) and because docs must say plainly
  that it is not part of the daily chain.
- Launch (target): manual — `POST /api/admin/jobs/cbtrn01Job` with optional
  `dailyFile` param.

## 3. Inputs and outputs
Inputs: DALYTRAN PS (DALYTRAN-RECORD 350B); XREFFILE KSDS keyed read;
ACCTFILE KSDS keyed read.
Outputs: SYSOUT DISPLAY only — no file writes anywhere (:156-230).

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBTRN01C-01 | startup | 'START OF EXECUTION OF PROGRAM CBTRN01C' displayed; files opened | :156-159 | FR-S14-16 |
| CBTRN01C-02 | per DALYTRAN rec | record displayed; XREF keyed read by card | :168-172, :227-230 | FR-S14-16 |
| CBTRN01C-03 | XREF status≠0 (invalid key, `WS-XREF-READ-STATUS=4`) | `CARD NUMBER <n> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<id>` | :173-183 | FR-S14-16 |
| CBTRN01C-04 | XREF ok, ACCT keyed read fails | `ACCOUNT <id> NOT FOUND` | :175-178 | FR-S14-16 |
| CBTRN01C-05 | DALYTRAN read error | `ERROR READING DAILY TRANSACTION FILE` + IO-STATUS display + abend | :219-221 | FR-S14-12 |
| CBTRN01C-06 | EOF | 'END OF EXECUTION OF PROGRAM CBTRN01C'; RC 0 | :195 | FR-S14-16 |
| CBTRN01C-07 | always | **no writes** — TRANFILE/ACCTFILE/TCATBALF are opened but never written (verified: no WRITE/REWRITE verbs in source) | all | FR-S14-16 |

## 5. Business rules and validations

Validate-only subset of the posting rules: card resolvable via XREFFILE, then
account present via ACCTFILE. The overlimit/expiry checks of CBTRN02C are absent
here — this program's diagnostics cover only resolution failures.

## 6. Data access and boundaries

- S14-B2 (reads only): `CardXrefRepository`, `AccountRepository` via JPA.
- S14-B3: DALYTRAN flat file input; diagnostics → `cbtrn01-validation.txt`
  (baseline writer replaces SYSOUT DISPLAY).
- No boundary the posting program doesn't already own.

## 7. Error and edge behavior

- Per-record verify failure → diagnostic line, loop continues (never stops).
- File read error → abend path (same CEE3ABD idiom).
- Declared-but-unused files: it opens CUSTFILE/CARDFILE too (:30-50ish SELECTs)
  but never reads them — dead opens; target port correctly drops them.

## 8. Hard-stop boundary

EOF + close + RC 0. No downstream edges.

## 9. Demoted mechanics

Open/close of files never read; DISPLAY tracing; status plumbing.

## 10. Traceability

CBTRN01C-01..07 → FR-S14-16 → `Cbtrn01JobConfiguration` +
`BatchJobService.validateDaily` → unit/integration test over `dailytran.txt`
asserting diagnostic lines and zero writes.
