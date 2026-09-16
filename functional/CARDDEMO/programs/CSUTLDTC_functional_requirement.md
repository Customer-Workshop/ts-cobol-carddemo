# CSUTLDTC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CSUTLDTC — `app/cbl/CSUTLDTC.cbl`. Stream S-09, **wave 1** (leaf). Shared utility: also CALLed by S-10 (`app/cbl/CORPT00C.cbl:392`); ported once here (`CardDemo_inventory.md` §6), owner S-09.
- Role: date-validity utility — wraps the Language Environment callable service `CEEDAYS` (`:116-120`) and translates its feedback code into a fixed 80-byte, human-readable result plus a numeric return code.

## 2. Trigger / caller contract
- Static `CALL 'CSUTLDTC' USING date, format, result` (`COTRN02C.cbl:393-396, 413-416`); `PROCEDURE DIVISION USING LS-DATE X(10), LS-DATE-FORMAT X(10), LS-RESULT X(80)` (`:83-88`); `EXIT PROGRAM` with `RETURN-CODE` = severity (`:97-100`).
- Callers in this estate pass mask `'YYYY-MM-DD'` (`COTRN02C.cbl:60`, `CORPT00C.cbl:72`).
- Target (SUBTRANSACTION profile): in-process `CardDemo.Domain.Dates.DateValidationService.Validate(date, mask)` returning `DateValidationResult` (`Severity`, `MessageNumber`, `Verdict`, `ResultText` (80 chars), `IsValid`, `Date` as `DateOnly?`). No network hop; participates in the caller's scope.

## 3. Inputs and outputs
Inputs: date text X(10), mask X(10) — both passed to CEEDAYS as Vstrings of their full length (`:105-112`).
Output `LS-RESULT` (80 bytes, `WS-MESSAGE` `:42-57`, FILLER literals survive `INITIALIZE` `:90`):

| Offset | Len | Content |
|---|---|---|
| 0 | 4 | severity `9(4)` from the feedback code (`:123`) |
| 4 | 11 | `Mesg Code:` + space |
| 15 | 4 | message number `9(4)` (`:124`) |
| 19 | 1 | space |
| 20 | 15 | verdict text (`:128-149`) |
| 35 | 1 | space |
| 36 | 9 | `TstDate:` + space |
| 45 | 10 | the date tested |
| 55 | 1 | space |
| 56 | 10 | `Mask used:` |
| 66 | 10 | the mask |
| 76 | 4 | spaces |

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CSUTLDTC-01 | valid date for the mask | severity `0000`, message `0000`, verdict `Date is valid`, return code 0 | :129-130, feedback X'00..00' :62 | FR-S09-31 |
| CSUTLDTC-02 | CEEDAYS insufficient data | `0003` / `2507` / `Insufficient` | :63, :131-132 | FR-S09-31 |
| CSUTLDTC-03 | CEEDAYS bad date value (day invalid for month/year) | `0003` / `2508` / `Datevalue error` | :64, :133-134 | FR-S09-31, 16 |
| CSUTLDTC-04 | CEEDAYS invalid era | `0003` / `2509` / `Invalid Era    ` | :65, :135-136 | FR-S09-31 |
| CSUTLDTC-05 | CEEDAYS unsupported range (outside 1582-10-15..9999-12-31) | `0003` / `2513` / `Unsupp. Range  ` | :66, :137-138 | FR-S09-32 |
| CSUTLDTC-06 | CEEDAYS invalid month | `0003` / `2517` / `Invalid month  ` | :67, :139-140 | FR-S09-31, 16 |
| CSUTLDTC-07 | CEEDAYS bad picture string | `0003` / `2518` / `Bad Pic String ` | :68, :141-142 | FR-S09-31 |
| CSUTLDTC-08 | CEEDAYS non-numeric data | `0003` / `2520` / `Nonnumeric data` | :69, :143-144 | FR-S09-31 |
| CSUTLDTC-09 | CEEDAYS year-in-era zero | `0003` / `2521` / `YearInEra is 0 ` | :70, :145-146 | FR-S09-31 |
| CSUTLDTC-10 | any other feedback | verdict `Date is invalid`, severity/message as returned | :147-148 | FR-S09-31 |
| CSUTLDTC-11 | always | result laid out per §3; `RETURN-CODE` = severity | :42-57, :97-98 | FR-S09-31 |

Message numbers are the low half-word of the feedback token (`X'09CB'`=2507 … `X'09D9'`=2521, `:62-70`, `:72-73`); severity is the high half-word (`X'0003'`).

## 5. Business rules and validations
Pure classification, no state. Caller contract in this stream: valid ⇔ severity `0000`; additionally message `2513` is treated as acceptable by COTRN02C (`COTRN02C.cbl:397-400, 417-420`).

## 6. Data access and boundaries
None (no files, no CICS). Boundary S09-B4: shared-utility contract consumed by S-09 and S-10 — ported once as `DateValidationService`.

## 7. Error and edge behavior
- CEEDAYS is an LE service without COBOL source; the target emulates its classification (assumption A-1, stream FR §11): mask tokens `YYYY`, `MM`, `DD` plus literal separators; other tokens → 2518; input shorter than the mask → 2507; non-digit where a digit is expected → 2520; month ∉ 1..12 → 2517; day ∉ 1..days-in-month → 2508; computed date before 1582-10-15 → 2513 (dates after 9999-12-31 cannot be expressed in a 4-digit year). Year `0000` with a valid month/day → 2513 (range), consistent with CEEDAYS' Lillian floor.
- Only well-formed `dddd-dd-dd` strings reach the utility from COTRN02C (layout edits run first), so the reachable outcomes there are `0000`, `2508`, `2517`, `2513`.

## 8. Hard-stop boundary
Callers own what to do with the verdict; CSUTLDTC never displays (the `DISPLAY` is commented out, `:96`).

## 9. Demoted mechanics
Vstring length/text plumbing (`:25-40`, `:105-112`); `OUTPUT-LILLIAN` (computed but never returned, `:41`, `:114-119`); `INITIALIZE WS-MESSAGE` (`:90`); commented `GOBACK`/`DISPLAY` (`:96`, `:101`).

## 10. Traceability
CSUTLDTC-01..11 ↔ FR-S09-16, 31, 32 ↔ `backend/CardDemo.Tests/Dates/DateValidationServiceTests.cs`; consumed by `TransactionAddService` (FR-S09-16).
