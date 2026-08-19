# ADR-0004: Bug-for-bug preservation of estate defects

## Status
Accepted.

## Decision
Two estate behaviors that look like defects are preserved exactly (the COBOL is
the oracle; deviations require user approval + contract entry):

1. **SEM-B01** — CBACT04C never flushes the final account: the driver loop is
   `PERFORM UNTIL END-OF-FILE = 'Y'`, which re-tests the condition before the
   `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch can run at EOF. The last account
   holding TCATBAL rows gets its interest transactions written but its account
   record is never rewritten. Goldens and .NET invariant tests encode this.

2. **SEM-B02** — CBTRN03C's date filter uses `NEXT SENTENCE`, skipping the
   remainder of the processing sentence for out-of-window records; with a
   trailing out-of-window record the report ends without grand totals. Captured
   as two goldens: `tran-report` (window bug visible) and `tran-report-eof`
   (clean EOF, totals printed).
