# CBSTM03B — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBSTM03B — `app/cbl/CBSTM03B.CBL` (~230 lines). Stream S-16, wave 1.
- Role: **file-handler subroutine** — generic VSAM IO shim called by CBSTM03A
  for every TRXFL/XREFFILE/CUSTFILE/ACCTFILE operation. Demoted seam in target:
  repositories replace it entirely; this doc records the contract for parity
  review, not a port.

## 2. Trigger / caller contract
- Called only by CBSTM03A: `CALL 'CBSTM03B' USING LK-M03B-AREA` (×15).
- Linkage (`CBSTM03B.CBL:99-112`): `LK-M03B-DD X(08)` selects the file;
  `LK-M03B-OPER X(01)` selects the operation — 'O'pen, 'C'lose, 'R'ead-seq,
  'K' key-read, 'W'rite, 'Z' rewrite; `LK-M03B-RC X(02)` returns the file
  status; `LK-M03B-KEY X(25)` + `LK-M03B-KEY-LN` supply the key for K ops;
  `LK-M03B-FLDT X(1000)` is the record buffer.
- Returns via GOBACK; unknown DD falls through to 9999-GOBACK silently.

## 3. Inputs and outputs
- TRNXFILE: ORGANIZATION INDEXED, ACCESS SEQUENTIAL, key FD-TRNXS-ID
  (card X(16)+id X(16)), 350B record (:31-36, :55-63).
- XREFFILE: INDEXED SEQUENTIAL, key FD-XREF-CARD-NUM, 50B (:37-42, :65-69).
- CUSTFILE: INDEXED **RANDOM**, key FD-CUST-ID 9(9), 500B (:43-48, :71-75).
- ACCTFILE: INDEXED **RANDOM**, key FD-ACCT-ID 9(11), 300B (:49-54, :77-81).

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBSTM03B-01 | OPER='O' per DD | OPEN INPUT on the selected file | :135-136, :159-160, :183-184, :208-209 | FR-S16-03 |
| CBSTM03B-02 | OPER='R' TRNX/XREF | READ next → LK-M03B-FLDT; EOF → RC '10' | :139-141, :163-165 | FR-S16-01/02 |
| CBSTM03B-03 | OPER='K' CUST/ACCT | keyed READ by LK-M03B-KEY(1:KEY-LN) | :189-190, :214-215 | FR-S16-03 |
| CBSTM03B-04 | OPER='C' | CLOSE file | :146-147, :170-171, :195-196, :220-221 | — |
| CBSTM03B-05 | always | file status → LK-M03B-RC (caller checks ≠00/04 → abend) | :152, :176, :201, :226 | FR-S16-12 |
| CBSTM03B-06 | unknown DD | falls through to GOBACK — silent no-op (RC stale) | :115-126 | edge |

## 5. Business rules and validations

None — pure IO delegation. All error semantics live in the caller.

## 6. Data access and boundaries

- S16-B9: the whole subroutine is the boundary seam — target replaces it with
  `TransactionRepository`/`CardXrefRepository`/`CustomerRepository`/
  `AccountRepository`. Nothing ported.
- S16-B1: physical layer = VSAM KSDS (all four files) → Postgres tables.

## 7. Error and edge behavior

- No abend logic of its own — RC surfacing only; the caller owns failure
  policy.
- 'W' and 'Z' ops are declared but never invoked by CBSTM03A (read-only flow) —
  vestigial branches.

## 8. Hard-stop boundary

Per-call GOBACK; owns no cross-call state beyond open file cursors.

## 9. Demoted mechanics

The entire program — DD dispatch table, key marshalling, status plumbing —
replaced by repository beans in the target.

## 10. Traceability

CBSTM03B-01..06 → FR-S16-01..03,12 → repository methods in `cbstm03Job` — no
direct target unit; verify repository error→step-failure mapping covers the
RC≠00/04→abend contract.
