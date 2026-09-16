# S-18 — Branch Export / Import — Independent Audit

- **Stream**: S18 — branch_export_import (CBEXPORT / CBIMPORT → `cbexportJob` / `cbimportJob`)
- **Audited branch**: `devin/1789516557-carddemo-java-engagement` at HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (auditor branch `devin/audit-s18-java`)
- **Auditor**: independent auditor (did not perform the migration)
- **Date**: 2026-09-16
- **Scope**: `functional/CARDDEMO/S18_*` docs, `app/cbl/CBEXPORT.cbl`, `app/cbl/CBIMPORT.cbl`, `app/cpy/CVEXPORT.cpy`, `app/jcl/CBEXPORT.jcl`, `app/jcl/CBIMPORT.jcl`, `spring-boot/src/main/java/com/carddemo/batch/*` (export/import paths), `spring-boot/src/test/java` (stream tests), wave PR #113.

## Verdict: **PASS with findings**

No CRITICAL/HIGH findings and no required FR is wholly unimplemented. Findings are MEDIUM and below: the 999-equivalent exit code is not wired on these two jobs (contrary to the FR acceptance criterion and the sibling-stream pattern), and the export text encoder has two format deviations not captured in the docs (right-padded display numerics, silent truncation at PIC bounds).

## 1. Traceability matrix

| FR | Requirement | Implementation (spring-boot, `com.carddemo.batch` unless noted) | Named test | Status |
|---|---|---|---|---|
| S18-FR-01 | Sections C→A→X→T→D, PK order | `RepositorySequenceReader(List.of(customers, accounts, xrefs, transactions, cards))` CbexportJobConfiguration.java:84; PK sort per reader :152-159 | `S18ExportImportParityIntegrationTest.exportWritesTypedSectionsWithUniformHeader` (type-sequence collapse `"CAXTD"` :101) | PASS |
| S18-FR-02 | Header: type(1)+ts(26)+seq9+'0001'+'NORTH' | `BatchJobService.export` :715-717; `"%09d"` seq, `fixed("0001",4)`, `fixed("NORTH",5)` | same test — HEADER regex :47-48,:96, seq `substring(27,36)` :97 | PASS |
| S18-FR-03 | Per-type payload in CVEXPORT field order, text | `exportRecord` :455-484; width tables :705-712; import mirror :494-540. Field order verified against CVEXPORT.cpy:24-100 and CBEXPORT.cbl:282-299,351-362,415-417,470-482,535-540 | `exportThenImportRoundTripsAllTables` (round-trip equality sans timestamp :174-178); `BatchJobIntegrationTest.exportImportRoundTripsWholeMinuteTimestamps` :237-261 | PASS (layout verified by inspection; round-trip test proves self-consistency, not CVEXPORT ordering) |
| S18-FR-04 | Per-type + total counts at job end | `cbexportStats`/`cbexportStatsListener` :87-127 → `logExportStats` :558-566 | same test — all six stat lines asserted :102-110 | PASS |
| S18-FR-05 | Sequential read; dispatch C/A/X/T/D → entity persisted | `cbimportReader` :40-47; `importRecord` switch :494-540 → repository `save()` | `exportThenImportRoundTripsAllTables` | PASS |
| S18-FR-06 | Unknown type → 132-char error record, count | `importRecord` default :539; `importErrorRecord` :550-556; `cbimportWriter` :98-105 | `importWrites132ColumnErrorRecords` :114-151 (byte-checked); `errorRecordPadsMessageAndTrailsSpaces` :188-199 | PASS |
| S18-FR-07 | Short record (<500) → error, continue | `importRecord` :489-491 | `importWrites132ColumnErrorRecords` (300-char record → 132-char error line, job COMPLETED :119-143) | PASS |
| S18-FR-08 | Validation no-op stub logging fixed lines | `logImportValidation` :570-573 via `cbimportStatsListener` :86-92 | `importWrites132ColumnErrorRecords` :145-146 | PASS |
| S18-FR-09 | IO failure → step failure exit code | missing `inputFile` → `FlatFileItemReader` open fails → FAILED; **but** no `Abend999JobListener` on either job (see Finding F1) — exit code reported is `FAILED`, not `999` | `missingInputFileFailsTheStep` :182-185 (import side only; export-side IO failure has no named test) | PARTIAL |
| S18-FR-10 | `inputFile` JobParameter selects export file | `cbimportReader` `@Value("#{jobParameters['inputFile']}")` :40-47, default `service.output("EXPORT.DATA")` :43 | `importWrites132ColumnErrorRecords` passes `inputFile` :124 | PASS |
| S18-FR-11 | Rerun regenerates EXPORT.DATA | writer `shouldDeleteIfExists(true)` :131-137 | rerun covered by `exportThenImportRoundTripsAllTables` (second export :171); suite-level `BatchJobIntegrationTest` :107-108,:129 | PASS |
| S18-FR-12 | 'D' card records produced and consumed | export `"D"` :480-483 (widths :710); import `"D"` → `cards.save` :534-537 | `exportThenImportRoundTripsAllTables`; `'D'` in collapsed sequence :101 | PASS |

## 2. COBOL parity spot-checks

| Item | Legacy (app/) | Target (spring-boot) | Result |
|---|---|---|---|
| Export finalize stats literals | `CBEXPORT.cbl:563-573` — 'Export completed', 'Customers/Accounts/XRefs/Transactions/Cards Exported:', 'Total Records Exported:' | `BatchJobService.logExportStats` :558-566 — same literals, `%09d` (PIC 9(09) renders zero-padded) | Match |
| Import finalize stats literals | `CBIMPORT.cbl:465-478` — 'Import completed', 'Total Records Read', per-type, 'Errors Written', 'Unknown Record Types' | `logImportStats` :575-585 — same literals and order | Match |
| Validation stub lines | `CBIMPORT.cbl:449-452` — 'Import validation completed' / 'No validation errors detected' (stub despite comments) | `logImportValidation` :570-573 | Match |
| Error record layout | `CBIMPORT.cbl:152-160` — ERR-TIMESTAMP X(26), `|`, ERR-RECORD-TYPE X(1), `|`, ERR-SEQUENCE 9(07), `|`, ERR-MESSAGE X(50), FILLER X(43), FD FB 132 (:106-109) | `importErrorRecord` :550-556 — `pad(ts,26)+"|"+pad(type,1)+"|"+"%07d"+'|'+pad(msg,50)` padded to 132 | Match byte-for-byte (87-char head + 45 spaces; COBOL's 130-char record is FB-padded to 132 identically) |
| Unknown-type message | `CBIMPORT.cbl:432` `'Unknown record type encountered'` | `importRecord` :539 — same literal | Match |
| ERR-TIMESTAMP | `CBIMPORT.cbl:429` `MOVE FUNCTION CURRENT-DATE` (21 chars) | `currentDateStamp` :743-749 — `yyyyMMddHHmmssSS` + `±HHMM` (21 chars, pad to 26); verified by test :191-193 | Match shape; value timezone-dependent (PR-disclosed) |
| ERR-SEQUENCE source | `CBIMPORT.cbl:431` moves `EXPORT-SEQUENCE-NUM` (record header) | `headerSequence` :733-742 parses `substring(27,36)` of the record, falls back to record number when unparseable | Match for well-formed headers; fallback is a target-only addition (documented in PR #113) |
| Header constants | `CBEXPORT.cbl:278-279` (and per-type paras :346-347, :411-412, :466-467, :531-532) `'0001'`, `'NORTH'` | `export` :716 `fixed("0001",4) + fixed("NORTH",5)` | Match |
| Header timestamp | `CBEXPORT.cbl:172-195` — one run-scoped `YYYY-MM-DD HH:MM:SS.00` (literal `.00`) | `EXPORT_TIMESTAMP_FORMAT` :39-40 `yyyy-MM-dd HH:mm:ss.SSSSSS`, `LocalDateTime.now()` **per record** :715 | 26-char width preserved; micros + per-record drift documented (analysis R4); per-record vs run-scoped is an additional minor drift (Finding F7) |
| Section order | `CBEXPORT.cbl:151-157` 2000→3000→4000→5000→5500 = C→A→X→T→D | reader list order :84 | Match |
| Record size | `CBEXPORT.jcl:33` `RECORDSIZE(500 500)`; FD 500 (`CBEXPORT.cbl:89-92`) | header 45 + `pad(data,455)` :717 = 500 chars | Match (text vs binary per S18-B6) |
| Export inputs | `CBEXPORT.jcl:49-58` five KSDS DISP=SHR | five `RepositoryItemReader`s :47-74, `findAll` PK-sorted | Match per S18-B2 |
| EXPFILE hand-off | KSDS `EXPORT.DATA` `KEYS(4 28)` (`CBEXPORT.jcl:30-38`, `CBIMPORT.jcl:28-29`) | flat `<output-dir>/EXPORT.DATA`; `inputFile` param :42-43 | Match per S18-B1/B5; key metadata dropped as documented |
| ERROUT | `CBIMPORT.jcl:56-60` FB 132 `IMPORT.ERRORS` | `<output-dir>/CBIMPORT.errors` :101 | Match |
| Abend mapping | `9999-ABEND-PROGRAM` → `CALL 'CEE3ABD'` (`CBEXPORT.cbl:576-579`, `CBIMPORT.cbl:481-484`); JCL `KEYS` abend on open failure | step FAILED → `BatchStatus.FAILED`; **no named exit code** on these jobs (Abend999JobListener exists :9-15 but is wired only on Cbact01:44, Cbact02:36, Cbact03:37, Cbact04:44, Cbcus01:36) | PARTIAL — Finding F1 |
| Import output DDs | CUSTOUT/ACCTOUT/XREFOUT/TRNXOUT/CARDOUT FB 500/300/50/350/150 (`CBIMPORT.jcl:33-52`; CARDOUT DD missing = S18-B7 defect) | JPA `save()` per entity :506,516,521,532,537 | Match per S18-B3/B7 |
| Error-write failure | `CBIMPORT.cbl:439-444` — failed error write only DISPLAYs, no abend, still counted | `FlatFileItemWriter` failure fails the step | Target stricter — Finding F9 (INFO) |
| Short record | N/A in legacy (FB 500 read can't produce one) | `importRecord` :489-491 `'RECORD IS SHORTER THAN 500 CHARACTERS'` | Target-only rule, documented in FR §5 | Match |
| 'D' records | `CBEXPORT.cbl:496-551` 5500/5600/5700 paras; `CBIMPORT.cbl:281-282,402-422` 2650 para (CARDOUT DD absent) | export `"D"` :480-483; import `"D"` :534-537 | Match per S18-B7 |
| Numeric display fields | CVEXPORT `9(09)`/`9(11)`/`9(04)` display PICs zero-fill | `fixed()` :719-725 writes `toString()` right-padded with spaces | Deviation from FR §6 encoding rule — Finding F2 |
| Amount widths | `S9(10)V99` (account :50-57), `S9(09)V99` (tran :71) | text widths 12 / 11 (:707, :709) — max rendered forms `9999999999.99` (13) / `999999999.99` (12) truncate one cent digit | Finding F3 |

## 3. Stub/placeholder sweep

Searched `spring-boot/src/main/java/com/carddemo/batch/` and the stream's tests for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, commented-out or trivially-true assertions (`assertTrue(true)`): **none found**.

- `logImportValidation` (:570-573) is an intentional parity no-op — it reproduces the legacy stub `3000-VALIDATE-IMPORT` (`CBIMPORT.cbl:449-452`), which itself does nothing but DISPLAY two fixed lines. Not a gap.
- `exportRecord` throws `IllegalArgumentException` for an unrecognized entity type (:484) — unreachable through the five readers; not a stub.
- All five stream tests are active, non-trivial, and byte-asserting (500-char lines, regex header, 132-char error layout, exact stats literals).

## 4. Documented deviations vs shipped code

| ID | Documented decision | Shipped | Compliant |
|---|---|---|---|
| S18-B1 | Flat `EXPORT.DATA` under output dir; fixed name; `shouldDeleteIfExists` | `service.output("EXPORT.DATA")` :134; default inputFile :43; writer :136 | yes |
| S18-B2 | PK-sorted repository readers C→A→X→T→D | readers :47-84, sort ASC :152-159 | yes |
| S18-B3 | Normalized outputs → JPA `save()`; ERROUT → flat 132-col error file | `importRecord` saves :506-537; `CBIMPORT.errors` :98-105 | yes |
| S18-B4 | Step failure → exit code | FAILED propagates, but the 999-named exit code is not set (Abend999JobListener not registered on `cbexportJob`/`cbimportJob` :41-44,:32-36) | **no — Finding F1** |
| S18-B5 | Explicit launch; `inputFile` = DD override | `BatchAdminController` POST `/api/admin/jobs/{jobName}` :15-24; param wired :40-47 | yes |
| S18-B6 | Text fixed-width 500-char records, `%09d` seq, text numerics | `export` :702-718 | yes |
| S18-B7 | CARDOUT missing-DD defect does not carry; cards → DB | import `"D"` → `cards.save` :534-537 | yes |
| PR #113 | ERROUT 132 layout; per-type stats; validation stub; blank dates → null; seq fallback to record number; "no named exit statuses elsewhere" | first five verified (:550-556,:575-585,:570-573,:730-732,:733-742); last claim **false** — `Abend999JobListener` is used by five sibling jobs | mostly yes; F1 |
| Undocumented | — | display numerics right-space-padded, not zero-filled (F2); amounts silently truncated at PIC bounds (F3); >500-char records silently truncated :493 (F6); per-record header timestamp (F7); null numerics → INVALID error record, not null (F5); parse-failure `INVALID <msg>` error path :544 (F10) | findings |

## 5. Test evidence

Commands run (scoped to this stream only, per audit scope; full-suite run belongs to the other audit):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='S18ExportImportParityIntegrationTest'
```

Observed (surefire `com.carddemo.S18ExportImportParityIntegrationTest.txt`):

- **Tests run: 5, Failures: 0, Errors: 0, Skipped: 0** — `exportWritesTypedSectionsWithUniformHeader`, `importWrites132ColumnErrorRecords`, `exportThenImportRoundTripsAllTables`, `missingInputFileFailsTheStep`, `errorRecordPadsMessageAndTrailsSpaces`.
- Job logs observed during the run show the COBOL-parity stat lines verbatim (e.g. `CBEXPORT: Total Records Exported: 000000008`, `CBIMPORT: Errors Written: 000000002`, `CBIMPORT: Unknown Record Types: 000000001`) and `BatchStatus.FAILED` for the missing-input case.

Additional (not executed by auditor): `BatchJobIntegrationTest` :107-108 launches both jobs in the chain test and asserts `EXPORT.DATA` size is a multiple of 501 (:129); `exportImportRoundTripsWholeMinuteTimestamps` :237-261 exercises `exportRecord`/`importRecord` directly.

## 6. Findings

| Sev | Title | Evidence | Recommendation |
|---|---|---|---|
| MEDIUM | F1 — 999-equivalent exit code not emitted: neither job registers `Abend999JobListener`, so a failed run reports exit code `FAILED` via `POST /api/admin/jobs/{job}` instead of the `999` used by every other abend-parity job. FR-09 acceptance says "the 999-equivalent exit"; PR #113's claim that "nothing else in the codebase uses named exit statuses" is wrong. | `CbexportJobConfiguration` :41-44 and `CbimportJobConfiguration` :32-36 (no `.listener`); `Abend999JobListener` :9-15; wired on `Cbact01JobConfiguration` :44, `Cbact02` :36, `Cbact03` :37, `Cbact04` :44, `Cbcus01` :36; `BatchAdminController` :23 returns `getExitCode()` | Add `.listener(new Abend999JobListener())` to both job builders, or document why S-18 deliberately diverges from the sibling-stream pattern. |
| MEDIUM | F2 — Display-numeric fields are exported space-padded-right, not zero-padded, contradicting the stream contract (`Long` zero-padded where legacy PIC is numeric display — FR doc §6) and COBOL `9(n)` display semantics. Affected: `EXP-CUST-SSN` 9(09), `EXP-ACCT-ID` 9(11), `EXP-XREF-CUST-ID` 9(09), `EXP-TRAN-CAT-CD` 9(04) (COMP/COMP-3 fields are free-form under S18-B6). Round-trip still works (import trims+parses), so this is a contract/documentation breach, not a data-loss bug. | `BatchJobService.export` :714 calls `fixed()` :719-725 (`toString()` + `repeat(' ')`); `CVEXPORT.cpy` :36, :48, :86, :68 | Zero-pad display-numeric fields (`%0Nd`) in `exportRecord`, or amend the FR §6 encoding rule to state left-justified space padding — then verify `importRecord` still parses. |
| MEDIUM | F3 — Amount text widths truncate at PIC bounds, silently corrupting large values on round-trip. Account amounts `S9(10)V99` render up to 13 chars (`9999999999.99`) into width 12; transaction `S9(09)V99` renders 12 chars into width 11; `fixed()` :723 hard-truncates the last cent digit, and import parses the truncated text without complaint. | widths `:707` (`12`×5), `:709` (`11`); `fixed` :723; `decimalText` :729; `CVEXPORT.cpy` :50-57, :71 | Widen amount fields by one char (or assert on overflow instead of truncating). Even if current data never reaches the bound, silent truncation is a data-integrity hazard. |
| LOW | F4 — FR-09 trace is PARTIAL: only the import-side failure path has a named test (`missingInputFileFailsTheStep`). The export-side IO-failure → FAILED path (repository read error, writer open failure) is untested for this stream. | `CbexportJobConfiguration` :129-137; test file :182-185 covers import only | Add a failure-mode test for `cbexportJob` (e.g. unwritable output dir). |
| LOW | F5 — Null numeric fields do not round-trip: export renders them blank, import throws `NumberFormatException` inside `Long.parseLong`/`Integer.parseInt`, landing the record in `CBIMPORT.errors` instead of persisting a null field. PR #113 fixed blank *dates* but not blank numerics (`custSsn`, `tranMerchantId`, `cardCvvCode`, `custFicoCreditScore` are all nullable). Seed data populates them, so untriggered in tests. | `:503`,`:520`,`:527`,`:535-537` parse calls vs `parseDate` :730-732 blank→null; `Customer.java` :24 (nullable `custSsn`) | Extend the blank→null handling to nullable numeric fields for symmetry. |
| LOW | F6 — Records longer than 500 chars are silently truncated on import (`substring(45,500)`); legacy fixed-length read of a malformed record would surface a file-status error. | `importRecord` :493 | Consider an explicit `>500` check producing an error record. |
| INFO | F7 — Header timestamp is `LocalDateTime.now()` per record; legacy generates one run-scoped timestamp (`1050-GENERATE-TIMESTAMP`). Values within one export can differ by microseconds. Harmless but a drift beyond the documented R4 micros-vs-`.00` note. | `export` :715; `CBEXPORT.cbl` :172-195 | Generate the timestamp once per step (e.g. step-scoped bean) for strict parity. |
| INFO | F8 — Legacy mid-run DISPLAYs are not ported: 'CBEXPORT: Starting Customer Data Export', 'Export Date/Time', 'Processing ... records', per-section counts (`:163-169,:245,:254,:314,:323,:378,:387,:433,:442,:498,:507`); 'CBIMPORT: Starting Customer Data Import', 'Import Date/Time' (`CBIMPORT.cbl:176-193`). FR-04 only requires finalize stats, so coverage is met; noted for operators diffing logs. | cited lines | Optional: emit progress logs. |
| INFO | F9 — Error-file write failure is fatal in target (chunk writer exception → FAILED); legacy `2750-WRITE-ERROR` only DISPLAYs and continues counting (`CBIMPORT.cbl:439-444`). Target is stricter — likely preferable, but a behavior difference. | `cbimportWriter` :98-105; `CBIMPORT.cbl` :439-444 | Document the stricter behavior, or leave as-is intentionally. |
| INFO | F10 — Parse-failure path produces `INVALID <exception message>` error records — a target-only error class with no legacy equivalent (COBOL MOVEs bytes verbatim). Reasonable hardening; message content includes Java exception text (`"INVALID For input string: ..."`), which is not a legacy literal. | `importRecord` :542-545 | No action required; noted for completeness. |
