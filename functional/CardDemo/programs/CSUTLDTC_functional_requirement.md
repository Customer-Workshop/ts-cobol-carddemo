# `CSUTLDTC` — program functional requirements (`!mf_program_fr_generation`)

| Item | Value |
|---|---|
| Stream | S-01 AccountView (`functional/CardDemo/AccountView_functional_requirement.md`, approved STOP C, D-0033..D-0036) |
| Wave | **1** (`AccountView_migration_plan.md` §6, wave 1) |
| Process type | ONLINE stream; this program has surface **none** (called subroutine, no screen) |
| Inputs consumed | stream FR §3.4, §4 (FR-23, FR-24), §5.4, §6.6; analysis §2, §5 (B-0014, B-0015); plan §3.3, §9 (DV-04), §10 (Q-07, Q-08) |
| Evidence policy | Source-derived (`app/cbl/CSUTLDTC.cbl`, `app/cpy/CSUTLDWY.cpy`, `app/cpy/CSUTLDPY.cpy`); FACT = read from source, INFERRED = depends on z/OS Language Environment behaviour absent from the repo |
| Branch | `devin/1788757216-cardemo-account-view-stream` |

Section order is identical across the four program FRs of this stream.

---

## 1. Identity and role

| Item | Value | Cite |
|---|---|---|
| Program id | `CSUTLDTC` | `app/cbl/CSUTLDTC.cbl:20` (`PROGRAM-ID`) |
| Source | `app/cbl/CSUTLDTC.cbl` (157 lines, pure COBOL, no CICS commands, no I/O) | whole file |
| Role in the stream | **utility / called sub-flow**: validates a date string against a picture mask by delegating to the LE callable service `CEEDAYS` and returns a fixed 80-byte result plus a severity return code | `:83-100`, `:116-120` |
| Shared program | **Yes — ported once by S-01 on behalf of the module (D-0020).** S-01 has **no runtime caller**; the callers are S-08 `COTRN02C.cbl:393,413`, S-09 `CORPT00C.cbl:392,412` and S-02 via `app/cpy/CSUTLDPY.cpy:293-296` | plan §3.3 B-0014; analysis §2 |
| Target unit | `service/DateValidationService` (Spring `@Service`, pure function, in-process) | plan §3.3 B-0014 |
| Target contract | `DateValidationService#validate(DateValidationRequest(String date, String mask)) -> DateValidationResult(int severity, String messageNumber, String reasonText, String formatted80)` | plan §3.3 B-0014 (D-0020) |

Why wave 1 although nothing in S-01 calls it: the plan places it with the other leaves (data seams, session model) so that S-02/S-08/S-09 inherit a finished, tested port (plan §6 wave 1, D-0020).

---

## 2. Trigger / caller contract

**Legacy trigger (FACT)**: static `CALL 'CSUTLDTC' USING <date>, <format>, <result>`. Example caller shape from the shared edit copybook:

```cobol
INITIALIZE WS-DATE-VALIDATION-RESULT
MOVE 'YYYYMMDD'                   TO WS-DATE-FORMAT
CALL 'CSUTLDTC'
USING WS-EDIT-DATE-CCYYMMDD
    , WS-DATE-FORMAT
    , WS-DATE-VALIDATION-RESULT
IF WS-SEVERITY-N = 0 ... ELSE ... WS-SEVERITY ... WS-MSG-NO
```
(`app/cpy/CSUTLDPY.cpy:290-314`)

**Linkage (FACT)** — `app/cbl/CSUTLDTC.cbl:83-88`:

| Parameter | PIC | Direction | Meaning |
|---|---|---|---|
| `LS-DATE` | `X(10)` | IN | date text to test, as typed/assembled by the caller (e.g. `20240229`); padded to 10 with spaces by the caller |
| `LS-DATE-FORMAT` | `X(10)` | IN | CEEDAYS picture mask, e.g. `YYYYMMDD` (`CSUTLDWY.cpy:58` default value) |
| `LS-RESULT` | `X(80)` | OUT | formatted result, layout §3 |
| `RETURN-CODE` | special register | OUT | `= WS-SEVERITY-N` (`:98`), i.e. `0` valid / `3` invalid / other as returned by CEEDAYS |

Control flow (FACT): `PROCEDURE DIVISION USING ...` -> `INITIALIZE WS-MESSAGE`-equivalent moves (`:90-93`), `PERFORM A000-MAIN` (`:94`), `MOVE WS-MESSAGE TO LS-RESULT` (`:97`), `MOVE WS-SEVERITY-N TO RETURN-CODE` (`:98`), `EXIT PROGRAM` (`:100`). `GOBACK` and a `DISPLAY` are commented out (`:96`, `:101`) — dead.

**Target trigger**: any Spring bean injects `DateValidationService` and calls `validate(new DateValidationRequest(date, mask))`. No HTTP endpoint is created for it in S-01 (no S-01 caller; plan §6 wave 1 "no screen program").

---

## 3. Inputs and outputs at field level

### 3.1 Inputs

| Legacy field | PIC | Target field | Notes | Cite |
|---|---|---|---|---|
| `LS-DATE` | `X(10)` | `DateValidationRequest.date` (`String`) | wrapped into a VSTRING `WS-DATE-TO-TEST` = 2-byte binary length (`LENGTH OF LS-DATE` = 10) + text (`:105-107`); copied to result field `WS-DATE` (`:108`, then overwritten — see DV-04) | `:105-108` |
| `LS-DATE-FORMAT` | `X(10)` | `DateValidationRequest.mask` (`String`) | wrapped into VSTRING `WS-DATE-FORMAT` (`:109-112`); copied verbatim to result `WS-DATE-FMT` (`:113`) | `:109-113` |

### 3.2 Outputs — the 80-byte result (`WS-MESSAGE`, `CSUTLDTC.cbl:42-57`; twin layout `app/cpy/CSUTLDWY.cpy:60-85` `WS-DATE-VALIDATION-RESULT`)

| Offset (1-based) | Len | Legacy field | Content | Target field |
|---|---|---|---|---|
| 1 | 4 | `WS-SEVERITY` / `WS-SEVERITY-N 9(4)` | severity, zero-padded, from `SEVERITY OF FEEDBACK-CODE` (`:123`) | `DateValidationResult.severity` (`int`) and bytes 1-4 of `formatted80` |
| 5 | 11 | FILLER | `'Mesg Code:'` + 1 space | `formatted80` |
| 16 | 4 | `WS-MSG-NO` / `WS-MSG-NO-N 9(4)` | message number from `MSG-NO OF FEEDBACK-CODE` (`:124`) | `DateValidationResult.messageNumber` (`String`, 4 chars zero-padded) |
| 20 | 1 | FILLER | space | `formatted80` |
| 21 | 15 | `WS-RESULT` | reason text per §5 catalogue (`:128-149`) | `DateValidationResult.reasonText` (`String`, exactly 15 chars, space-padded) |
| 36 | 1 | FILLER | space | `formatted80` |
| 37 | 9 | FILLER | `'TstDate:'` + 1 space | `formatted80` |
| 46 | 10 | `WS-DATE` | the date under test — **DV-04**: target writes the 10-char `date` as passed (legacy writes the VSTRING group, `:122`, see §7) | `formatted80` |
| 56 | 1 | FILLER | space | `formatted80` |
| 57 | 10 | FILLER | `'Mask used:'` | `formatted80` |
| 67 | 10 | `WS-DATE-FMT` | mask as passed | `formatted80` |
| 77 | 4 | FILLER | spaces (1 + 3) | `formatted80` |

`formatted80` is always exactly 80 characters. `RETURN-CODE` -> `severity` (`:98`).

### 3.3 Field-dictionary names

There is no table for this program; the analysis field dictionary (§4) has no rows for it. The only "dictionary" is the result layout above, which the wave-1 unit tests assert byte-for-byte against `CSUTLDWY.cpy:60-85` (plan §3.3 coexistence verification).

---

## 4. Functional requirements owned by this program

Ownership is **exclusive**: no other S-01 program validates dates. `COACTVWC` moves date fields as unvalidated text (`COACTVWC.cbl:487-489`, `:507`) and never calls this program (stream FR §2 row "Date utility caller").

| Program req | Stream req | Business trigger | Observable result | Source cite |
|---|---|---|---|---|
| DTC-01 | **FR-23** | a caller passes a date and a mask that parse as a real calendar date | `severity = 0`, `messageNumber = '0000'`, `reasonText = 'Date is valid  '`, `formatted80 = '0000Mesg Code: 0000 Date is valid   TstDate:<date> Mask used:<mask>    '`; return code 0 | `CSUTLDTC.cbl:88-100`, `:105-130` |
| DTC-02 | **FR-24** | the date does not parse for the mask | `severity = 3` (`0003`), `messageNumber` in {`2507`,`2508`,`2509`,`2513`,`2517`,`2518`,`2520`,`2521`} with the matching 15-char `reasonText` of §5; any other feedback -> `'Date is invalid'` with the severity/number as returned; return code = severity | `:60-73`, `:122-149` |
| DTC-03 | **FR §5.4 result catalogue** (contract, part of FR-24) | any call | the 80-byte layout of §3.2 is produced exactly, so a caller reproducing the legacy string comparison (`CSUTLDPY.cpy:298`, `:309`, `:311`) still works | `:42-57`, `:97`; `CSUTLDWY.cpy:60-85` |

No further requirement is introduced: the stream FR lists FR-23 and FR-24 only for DTC (§4, §9, §10).

---

## 5. Business rules and validations

| Rule | Behaviour | Blocking? | FACT / INFERRED | Cite |
|---|---|---|---|---|
| R-1 Reason catalogue | feedback token -> severity/msg/text: | result, never a failure | see per row | `:61-73`, `:128-149` |
| | `X'0000000000000000'` (`FC-INVALID-DATE`, misnamed success token) -> `0000` / `0000` / `Date is valid  ` | | **FACT** (constant) / mapping to "parses" **contractual** (Q-08) | |
| | `X'000309CB...'` -> `0003` / `2507` / `Insufficient   ` | | FACT constant / input mapping **INFERRED** | |
| | `X'000309CC...'` -> `0003` / `2508` / `Datevalue error` | | FACT / **INFERRED** | |
| | `X'000309CD...'` -> `0003` / `2509` / `Invalid Era    ` | | FACT / **INFERRED** | |
| | `X'000309D1...'` -> `0003` / `2513` / `Unsupp. Range  ` | | FACT / mapping **contractual** (Q-08; caller branch `COTRN02C.cbl:398-401`) | |
| | `X'000309D5...'` -> `0003` / `2517` / `Invalid month  ` | | FACT / **INFERRED** | |
| | `X'000309D6...'` -> `0003` / `2518` / `Bad Pic String ` | | FACT / **INFERRED** | |
| | `X'000309D8...'` -> `0003` / `2520` / `Nonnumeric data` | | FACT / **INFERRED** | |
| | `X'000309D9...'` -> `0003` / `2521` / `YearInEra is 0 ` | | FACT / **INFERRED** | |
| | any other token -> severity and msg **as returned**, text `Date is invalid` | | FACT | `:146-147` |
| R-2 Severity/msg extraction | bytes 1-2 of the token = `SEVERITY S9(4) BINARY`, bytes 3-4 = `MSG-NO S9(4) BINARY`, moved to the 4-digit zoned fields | — | FACT | `:70-73`, `:123-124` |
| R-3 Return code | `RETURN-CODE = severity` | — | FACT | `:98` |
| R-4 Mask echo | mask is echoed verbatim in bytes 67-76 | — | FACT | `:113` |
| R-5 Date echo | intended: date echoed in bytes 46-55 (`:108`); actual legacy: overwritten by the VSTRING group at `:122` (2 binary length bytes + first 8 chars). **Target = intended value (DV-04, Q-07)** | — | FACT (defect) | `:108`, `:122` |
| R-6 Never throws | an invalid date is a *result*, not an exception; the legacy has no failure path at all | n/a | FACT (no `ON EXCEPTION`, no abend) | whole `A000-MAIN` |

**Target-side mapping rule (decided, B-0015)**: build a `DateTimeFormatter` from `mask` (`YYYYMMDD` -> `uuuuMMdd` etc.), parse with `ResolverStyle.STRICT`; success -> `0000`; a parsed date outside the CEEDAYS-supported range -> `2513`; the seven INFERRED codes are assigned from *distinguishable* `java.time` failures (non-digit input -> `2520`; month 13 -> `2517`; day-of-month impossible -> `2508`; unparseable mask -> `2518`; short/empty input -> `2507`; …) and **each such assignment is INFERRED and must be labelled so in the wave-1 test names**; anything not mapped -> `Date is invalid` with severity `0003`, msg `0000`-style "as returned" value defined by the service (plan §3.3 B-0015, Q-08). The exact input->code table for the INFERRED rows is a wave-1 implementation choice recorded in the decision log, **not** a parity claim.

Blocking vs warning: not applicable — the program never blocks; the **caller** decides (e.g. `CSUTLDPY.cpy:298-316` treats any non-zero severity as an input error).

---

## 6. Data access and boundaries

**Data access**: none. No files, no `EXEC CICS`, no `EXEC SQL`, no working-storage that survives the call. Pure function (FACT, whole file; analysis §5.2).

| Boundary | Register status | Decided target mechanism | Error / timeout contract | Cite |
|---|---|---|---|---|
| **B-0014** `CSUTLDTC` shared utility (B2) | DECIDED (D-0020, plan §3.3) | in-process `@Service` `DateValidationService` with the typed record contract of §1; `formatted80` retained; callers branch on `severity`/`messageNumber` | never throws for an invalid date; **only a `null` argument** (`date` or `mask`) is a programming error -> `IllegalArgumentException` -> `GlobalExceptionHandler` -> HTTP 500 when reached over HTTP. No timeout (in-process, no I/O) | `.migration/04_boundary_register.md` B-0014; plan §3.3 |
| **B-0015** `CEEDAYS` external runtime (B11) | DECIDED as a **deferral with re-entry condition** (plan §3.3) | `java.time` substitute inside the service (private feedback mapper); `0000` and `2513` contractual, the seven other codes INFERRED, fallback `Date is invalid` | as B-0014. **Re-entry**: re-open when S-08 TranAdd or S-09 TranReports is planned, or earlier if a CEEDAYS feedback catalogue / real LE run becomes available | register B-0015; plan §3.3, §10 Q-08 |

No undecided boundary: both rows carry a decision. The deferral in B-0015 is a *decided* mechanism (substitute + named re-entry), not an open item for this wave.

Transaction behaviour: none (no unit of work).

---

## 7. Error and edge behavior

| Case | Legacy behaviour | Target behaviour | Class | Cite |
|---|---|---|---|---|
| Valid date (`20240229`, `YYYYMMDD`) | `0000 … Date is valid`, RC 0 | same (`severity 0`) | business (FR-23) | `:116-130` |
| Impossible calendar date (`20240230`) | CEEDAYS feedback -> most likely `2508 Datevalue error` (**INFERRED**) | `0003`/INFERRED code/text; asserted against the chosen mapping | business (FR-24) | `:132-147` |
| Month > 12 (`20241301`) | **INFERRED** `2517 Invalid month` | `0003`/`2517` INFERRED | business (FR-24) | `:138-139` |
| Non-digit input (`2024AB01`) | **INFERRED** `2520 Nonnumeric data` | `0003`/`2520` INFERRED | business (FR-24) | `:142-143` |
| Out-of-range year for CEEDAYS (Lilian range starts 15 Oct 1582) | `2513 Unsupp. Range` — **contractual** (callers test it, `COTRN02C.cbl:398-401`) | `0003`/`2513` | business (FR-24) | `:136-137` |
| Empty / all-space date | **INFERRED** `2507 Insufficient` | `0003`/`2507` INFERRED | edge (FR-24) | `:130-131` |
| Mask not a valid picture (`ZZZZ`) | **INFERRED** `2518 Bad Pic String` | `0003`/`2518` INFERRED | edge (FR-24) | `:140-141` |
| Any other CEEDAYS token | severity/msg as returned, `Date is invalid` | fallback branch | business (FR-24) | `:146-147` |
| Date longer than 10 / mask longer than 10 | impossible: linkage is `X(10)` — the caller truncates | service accepts `String`; values longer than 10 are a caller error — **INFERRED policy: treat as programming error like `null`** (wave-1 decision, record it) | technical | `:84-86` |
| `null` argument | impossible in COBOL | `IllegalArgumentException` (B-0014 contract) | technical | plan §3.3 |
| `TstDate:` segment content | 2 binary length bytes + 8 chars (defect) | intended 10-char date — **DV-04** (Q-07); parity test asserts the *intended* value explicitly | deliberate deviation | `:108`, `:122` |
| Restart / rerun | n/a (ONLINE utility, stateless) | n/a | — | — |

Technical failures handled by the legacy program: **none** (no `ON EXCEPTION`, no `HANDLE`). Marked technical.

### Deliberate deviation owned here

| Id | Legacy (cite) | Target | Required parity assertion |
|---|---|---|---|
| **DV-04** | `MOVE WS-DATE-TO-TEST TO WS-DATE` copies the whole VSTRING group (2 binary bytes + text) into `WS-DATE X(10)`, overwriting the correct value set at `:108` (`CSUTLDTC.cbl:122`) | bytes 46-55 of `formatted80` hold the date exactly as passed | `DateValidationServiceTest` asserts `formatted80.substring(45,55).equals(date)` and a comment/tag naming DV-04 / Q-07 so the difference is visible, not silent |

### Q-resolutions relevant here (approved, plan §10)

- **Q-07**: port the intended `TstDate:` value (DV-04).
- **Q-08**: `java.time` substitute; `0000` and `2513` contractual; the other seven tokens INFERRED and labelled; unmapped -> `Date is invalid`; B-0015 deferred with re-entry.

---

## 8. Hard-stop boundary

This program's own delegation boundary is the `CALL "CEEDAYS"` (`:116-120`): everything about *how* a date is judged valid belongs to the runtime it calls, and in the target to `java.time` (B-0015). `CSUTLDTC` is **not responsible for**:

- deciding what a caller does with a non-zero severity (S-02/S-08/S-09 callers; `CSUTLDPY.cpy:298-316`) — out of S-01 scope entirely;
- any screen, message text shown to a user, or HTTP endpoint — none exists in S-01 for this program;
- the S-01 Account View date fields, which are displayed unvalidated by `COACTVWC` (`COACTVWC.cbl:487-489`, `:507`) — see `COACTVWC_functional_requirement.md`.

Nothing in this document reaches past the stream hard stop (`COACTVWC.cbl:349-352`); the program is upstream of every screen.

---

## 9. Acceptance criteria

All criteria are for `DateValidationService` alone (wave-1 unit tests; no database, no HTTP). Each is traceable to a program requirement and its stream requirement.

| Id | Req | Given | When | Then |
|---|---|---|---|---|
| AC-DTC-01 | DTC-01 / FR-23 | `date='20240229'`, `mask='YYYYMMDD'` | `validate` | `severity==0`, `messageNumber=="0000"`, `reasonText=="Date is valid  "`, `formatted80=="0000Mesg Code: 0000 Date is valid   TstDate:20240229   Mask used:YYYYMMDD      "` (length 80) |
| AC-DTC-02 | DTC-01 / FR-23 | `date='2024-02-29'`, `mask='YYYY-MM-DD'` | `validate` | `severity==0`, `messageNumber=="0000"`, mask echoed at offset 67 |
| AC-DTC-03 | DTC-02 / FR-24 | `date='20240230'`, `mask='YYYYMMDD'` | `validate` | `severity==3`; `messageNumber` is one of the eight `0003` codes; `reasonText` is the 15-char text paired with that code in §5; `formatted80` bytes 1-4 == `"0003"` |
| AC-DTC-04 | DTC-02 / FR-24 (contractual) | `date='15000101'`, `mask='YYYYMMDD'` (before the supported range) | `validate` | `messageNumber=="2513"`, `reasonText=="Unsupp. Range  "` |
| AC-DTC-05 | DTC-02 / FR-24 (INFERRED) | `date='20241301'` | `validate` | `messageNumber=="2517"`, `reasonText=="Invalid month  "` — test named `…_INFERRED` |
| AC-DTC-06 | DTC-02 / FR-24 (INFERRED) | `date='2024AB01'` | `validate` | `messageNumber=="2520"`, `reasonText=="Nonnumeric data"` — `…_INFERRED` |
| AC-DTC-07 | DTC-02 / FR-24 (INFERRED) | `date='        '` (blank) | `validate` | `messageNumber=="2507"`, `reasonText=="Insufficient   "` — `…_INFERRED` |
| AC-DTC-08 | DTC-02 / FR-24 | an input the mapper does not classify | `validate` | `reasonText=="Date is invalid"`, `severity==3`, no exception |
| AC-DTC-09 | DTC-03 / §5.4 | any of the above | inspect `formatted80` | length is exactly 80; offsets 5-15 == `"Mesg Code: "`, 37-45 == `"TstDate: "`, 57-66 == `"Mask used:"`; offsets 77-80 spaces |
| AC-DTC-10 | DV-04 / Q-07 | any valid call | inspect `formatted80` offsets 46-55 | equals `date` right-padded to 10 (the *intended* value, not the legacy binary-prefixed bytes); test tagged DV-04 |
| AC-DTC-11 | B-0014 | `date==null` or `mask==null` | `validate` | `IllegalArgumentException`; no other input ever throws |
| AC-DTC-12 | B-0014 | 10 000 calls with mixed inputs | run | no state leaks between calls (pure function): identical input -> identical result regardless of call order |

Return-code parity: `severity` plays the role of `RETURN-CODE` (`:98`); no separate assertion needed beyond AC-DTC-01/03.

---

## 10. Open questions and assumptions

| Id | Item | Status | Owner / when |
|---|---|---|---|
| A-DTC-1 | Which concrete inputs produce `2507`, `2508`, `2509`, `2518`, `2520`, `2521` (and `2517`) in real CEEDAYS is **not in the repo**; the wave-1 mapping is INFERRED and must be labelled in test names and recorded as a decision-log row | approved as INFERRED (Q-08, B-0015 deferral) | wave-1 child records the chosen table; re-verified when S-08/S-09 are planned (B-0015 re-entry) |
| A-DTC-2 | `2509 Invalid Era` and `2521 YearInEra is 0` concern Japanese-era pictures; `java.time` with a Gregorian mask cannot produce them naturally. The service may leave them unreachable (fallback `Date is invalid`) — INFERRED acceptable under Q-08 | assumption | wave 1 |
| A-DTC-3 | The lower bound for `2513` is the Lilian epoch (15 Oct 1582) and the upper bound 31 Dec 9999 — **INFERRED** from the meaning of a Lilian date; not in the repo | assumption | wave 1; B-0015 re-entry |
| A-DTC-4 | Inputs longer than 10 chars are impossible in COBOL; target policy (reject as programming error vs truncate) is a wave-1 choice — recommend reject like `null` | assumption | wave 1 |
| A-DTC-5 | No S-01 runtime caller exists (R-10), so the wave-1 unit tests are the whole evidence base until S-08 | fact, carried from plan | — |
| — | Undecided boundary | **none** (B-0014 decided; B-0015 decided as deferral with re-entry) | — |

**Divergence from cross-check branches**: not consulted. This document derives from `app/` source on the work branch and the approved stream artifacts only.
