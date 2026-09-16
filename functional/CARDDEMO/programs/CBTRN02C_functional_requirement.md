# CBTRN02C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBTRN02C — `app/cbl/CBTRN02C.cbl`. Stream S-14, wave 1.
- Role: daily-transaction posting program — validates each DALYTRAN record,
  writes valid ones to TRANSACT, updates transaction-category balances and
  account balances, and emits a reject file for failures.

## 2. Trigger / caller contract
- Executed as STEP15 of job POSTTRAN (`app/jcl/POSTTRAN.jcl`), itself triggered
  by the daily scheduler chain (Control-M `DAILY-TransactionBackup` /
  CA-7 SCHID 030 — see `S14_daily_posting_analysis.md` §1).
- No linkage parameters; batch entry.
- Return code: `RETURN-CODE = 4` when `WS-REJECT-COUNT > 0` (:229-231), else 0;
  unrecoverable IO → CEE3ABD abend 999.

## 3. Inputs and outputs
Inputs: DALYTRAN PS (DALYTRAN-RECORD 350B, `CVTRA06Y.cpy`); XREFFILE KSDS
(CARD-XREF-RECORD 50B, `CVACT03Y.cpy`); ACCTFILE KSDS I-O (ACCOUNT-RECORD 300B,
`CVACT01Y.cpy`); TCATBALF KSDS I-O (TRAN-CAT-BAL-RECORD 50B, `CVTRA01Y.cpy`).
Outputs: TRANFILE KSDS write (TRAN-RECORD 350B, `CVTRA05Y.cpy`); DALYREJS GDG(+1)
sequential output 430B (= 350B record + 80B trailer); SYSOUT diagnostics.

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBTRN02C-01 | startup | open DALYTRAN/TRANFILE/XREFFILE/ACCTFILE/TCATBALF/DALYREJS; status≠00 → DISPLAY + abend | :195-205, :291-303 | FR-S14-12 |
| CBTRN02C-02 | XREF invalid key | reason 100 'INVALID CARD NUMBER FOUND' → reject | :380-392 | FR-S14-01 |
| CBTRN02C-03 | ACCT invalid key | reason 101 'ACCOUNT RECORD NOT FOUND' → reject | :394-401 | FR-S14-02 |
| CBTRN02C-04 | `CYC-CREDIT − CYC-DEBIT + AMT > CREDIT-LIMIT` | reason 102 'OVERLIMIT TRANSACTION' → reject | :403-412 | FR-S14-03 |
| CBTRN02C-05 | `ACCT-EXPIRAION-DATE < DALYTRAN-ORIG-TS(1:10)` | reason 103 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION' → reject | :414-419 | FR-S14-04 |
| CBTRN02C-06 | validation pass | TRAN-RECORD populated from DALYTRAN fields; TRAN-PROC-TS = current DB2-format timestamp; TRANFILE WRITE | :424-442, :692-705 | FR-S14-05 |
| CBTRN02C-07 | TCATBALF key found | `ADD DALYTRAN-AMT TO TRAN-CAT-BAL` + REWRITE | :527-528 | FR-S14-06 |
| CBTRN02C-08 | TCATBALF key absent | build record (TRANCAT-ACCT-ID/TYPE-CD/CD + amt) + WRITE | :467-526 | FR-S14-07 |
| CBTRN02C-09 | account update | `ADD AMT TO ACCT-CURR-BAL`; amt≥0 → `ADD AMT TO ACCT-CURR-CYC-CREDIT`; amt<0 → `ADD AMT TO ACCT-CURR-CYC-DEBIT`; REWRITE | :545-554 | FR-S14-08 |
| CBTRN02C-10 | REWRITE invalid key | reason 109 appended; record routed to rejects | :554-559 | FR-S14-09 |
| CBTRN02C-11 | per reject | write 350B record + trailer (9(04) reason + X(76) desc) to DALYREJS; `WS-REJECT-COUNT` +1 | :208-219, :446-465 | FR-S14-13 |
| CBTRN02C-12 | EOF | DISPLAY 'TRANSACTIONS REJECTED :<n>'; RC=4 iff n>0 | :228-231 | FR-S14-10/11 |
| CBTRN02C-13 | any file error | DISPLAY + IO-STATUS + CEE3ABD 999 → job abend | :249-268 | FR-S14-12 |

## 5. Business rules and validations

Main loop (:202-219): read DALYTRAN → reset validation trailer → 1500-A XREF
lookup → (found) 1500-B ACCT lookup with overlimit + expiry checks → if reason=0
then 2000-POST-TRANSACTION else 2500-WRITE-REJECT. Posting order inside
2000/2500: TRANFILE write → TCATBALF create-or-update → ACCTFILE update —
**no transaction boundary on the mainframe** (partial post possible on mid-loop
abend); target runs per-chunk transactional (safer deviation, documented).
Validation reason precedence is order-dependent: card → account → overlimit →
expiry (an overlimit AND expired account reports 102, not 103 — both checks run
but reason is last-write-wins... verify: :403-412 sets 102 then :414-419 sets
103 — last failure wins → 103 reported when both fail. Recorded faithfully.)

## 6. Data access and boundaries

- S14-B2: all VSAM → JPA (`TransactionRepository`,
  `AccountRepository`, `TransactionCategoryBalanceRepository`,
  `CardXrefRepository`). Physical layer resolved: VSAM KSDS, target-owned.
- S14-B3: DALYTRAN → `dailyFile` flat file; DALYREJS(+1) → `cbtrn02-rejects.txt`.
- S14-B5: CEE3ABD → exception → step FAILED; RC=4 → job exit description.

## 7. Error and edge behavior

- Reject never stops the loop; only IO errors do.
- 103 vs 102 precedence: expiry overwrites overlimit when both apply (last write
  wins within one record's checks — source order :403→:414).
- Zero-length DALYTRAN: RC 0, zero reads, zero rejects.
- Rerun is non-idempotent — additive double-post (no dedupe key exists).

## 8. Hard-stop boundary

DALYREJS closed + final RC. Everything after (TRANBKP, WAITSTEP, OPENFIL) is the
job shell/chain, not this program.

## 9. Demoted mechanics

END-OF-FILE flag; file-status dispatch tables; DISPLAY trace spam; timestamp
formatting routine Z-GET-DB2-FORMAT-TIMESTAMP (:692-705).

## 10. Traceability

CBTRN02C-01..13 → FR-S14-01..13 → `Cbtrn02JobConfiguration` +
`BatchJobService.postDaily`/`validate` → unit + golden-file reject tests.
