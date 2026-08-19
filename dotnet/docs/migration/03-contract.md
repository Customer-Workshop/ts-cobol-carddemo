# Semantics Contract

Every row: ID / decision / test or explicit N/A. Parity tests live in
`dotnet/backend/tests/`; oracle scenarios in `dotnet/parity/`.

| ID | Item | Decision | Test |
| --- | --- | --- | --- |
| SEM-001 | ROUNDED vs truncation | Default COMPUTE truncates toward zero; ROUNDED = half-away-from-zero. `LegacyMoney.Truncate`/`Round` are the only permitted rounding paths. | LegacyMoneyTests |
| SEM-002 | Intermediate precision of chained COMPUTE | Recompile-to-run oracle inherits GnuCOBOL intermediates, which can differ from IBM ARITH(COMPAT); risk recorded per manifest (ADR-0001). CBACT04C's single-expression COMPUTE verified exact. | GoldenInvariantTests.InterestAmounts_MatchLegacyMoneyFormula |
| SEM-003 | ON SIZE ERROR | Not used by wave-0/1/2 programs. | N/A (revisit per wave) |
| SEM-004 | COMP encodings/endianness | IBM COMP is big-endian halfword/fullword; only used in LINKAGE PARM length (driver builds it). No COMP fields in wave-0 record types. | N/A (driver DRVACT04.cbl) |
| SEM-005 | COMP-3 packed decimal | Used only in CBTRN03C working storage (page counter), not in file records; no decoder needed yet. | N/A (wave 2 adds if surfaced) |
| SEM-006 | Zoned sign overpunch | ASCII files keep EBCDIC overpunch (`{`/`}`/A–R); preferred sign on write is C/D zone. | ZonedDecimalTests, round-trip tests |
| SEM-007 | SIGN SEPARATE/LEADING | Not used in estate copybooks. | N/A |
| SEM-008 | PIC editing (Z, floating sign, CR/DB) | Report fields in CVTRA07Y use edited PICs; preserved via line-exact report goldens. | tran-report goldens |
| SEM-009 | REDEFINES aliasing | Present in CICS copybooks; wave-0 record types have none. | N/A (per-wave) |
| SEM-010 | OCCURS DEPENDING ON | Not used in shared records. | N/A |
| SEM-011 | Group vs elementary MOVE | Encoders emit full byte images (group-MOVE semantics: no numeric editing on group moves). | DecoderGoldenRoundTripTests |
| SEM-012 | Reference modification | `TRAN-PROC-TS (1:10)` date compare is substring + lexicographic. | LegacyDateTests, tran-report goldens |
| SEM-013 | PERFORM THRU fall-through | Used with EXIT paragraphs only in estate; no mid-range entry. | N/A (structural) |
| SEM-014 | GO TO DEPENDING | Not used. | N/A |
| SEM-015 | INSPECT/STRING/UNSTRING, NUMVAL | STRING used to build TRAN-ID/card numbers; behavior captured in goldens. | interest-calc goldens |
| SEM-016 | SORT stability | No SORT verbs in COBOL (sorts live in JCL DFSORT steps, out of modeled jobs). | N/A |
| SEM-017 | COMP-1/COMP-2 | Not used. | N/A |
| SEM-018 | Invalid-data tolerance | Decoders accept F-zone digits on signed fields (COBOL tolerance) but reject non-digit bytes loudly. | ZonedDecimalTests.DecodeSigned_RejectsGarbage |
| SEM-019 | LOW-/HIGH-VALUES sentinels | STARTBR patterns in CICS use LOW-VALUES; batch waves don't. | N/A (CICS waves) |
| SEM-020 | BLANK WHEN ZERO | Not used in shared records. | N/A |
| SEM-021 | ARITH / NUMPROC / TRUNC | Residual risk at recompile-to-run level; documented per manifest + ADR-0001. | manifests |
| SEM-022 | EBCDIC vs ASCII collation | Harness runs on ASCII byte order; keys in modeled scenarios are digits-only so orderings coincide. True-EBCDIC collation is a residual risk (ADR-0001). | manifests |
| SEM-023 | FILE STATUS mapping | Estate checks '00'/'10' only in modeled paths; harness preserves; `FixedRecordFile` throws on short records. | oracle scenarios |
| SEM-024 | Duplicate keys / AIX | CARDXREF modeled primary-key-only in harness (AIX not exercised by wave 1/2). | manifest note |
| SEM-025 | Variable-length records | Not used in modeled files. | N/A |
| SEM-026 | Date representation | Empirically `PIC X(10)` 'YYYY-MM-DD' compared lexicographically; timestamps X(26) DB2-style. No day serials in wave scope. | LegacyDateTests |
| SEM-027 | Date windowing | No 2-digit windowing in batch scope. | N/A |
| SEM-028 | ACCEPT date/time determinism | `FUNCTION CURRENT-DATE` frozen via faketime (2025-08-01 09:00:00); PARM date via driver. | run-scenarios.sh |
| SEM-029 | Arithmetic operation order | Ported code must mirror COBOL statement order; enforced by golden reconciliation. | GoldenInvariantTests |
| SEM-030 | CICS COMMAREA/EIBAID/HANDLE | Deferred to CICS waves; oracle level is data+COMMAREA only, never screens. | N/A (future waves) |
| SEM-B01 | CBACT04C final-account flush skipped (`PERFORM UNTIL` re-tests before EOF ELSE branch) — last account's interest transactions are written but its record is never REWRITE'n | **Bug-for-bug preserved** | GoldenInvariantTests (UntouchedAccounts, BalanceDeltas) |
| SEM-B02 | CBTRN03C date filter `NEXT SENTENCE` jumps past page/total accumulation for out-of-window records, so a trailing out-of-window record suppresses grand totals | **Bug-for-bug preserved** | tran-report vs tran-report-eof goldens |
