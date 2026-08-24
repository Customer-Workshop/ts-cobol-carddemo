# DJ-106 — Interest & Fees Analytics: Shared CSV Contract (LOCKED)

Single source of truth for the interest & fees analytics slice. Both workstreams
(the COBOL extract and the HTML dashboard) code to this contract exactly.
Any change requires updating this document first.

## Extract output file

- **Path:** `app/data/reports/interest-summary.csv`
- **Encoding:** ASCII, Unix line endings (`\n`)
- **Format:** comma-separated, exactly one header row, one data row per account
- **Sort order:** ascending by `acct_id`
- **Quoting/escaping:** none. No field may contain a comma, quote, or newline.
  All values are written as plain unquoted text. `acct_group_id` values are
  alphanumeric group codes (e.g. `DEFAULT`, `A000000000`) and never contain
  delimiters; numeric fields use only digits, an optional leading minus, and a
  decimal point.

## Header row (exact, in order)

```
acct_id,acct_group_id,category_count,total_balance,total_interest,total_fees,avg_interest_rate
```

## Columns

| # | Column | Type/format | Definition |
|---|--------|-------------|------------|
| 1 | `acct_id` | 11 digits, zero-padded | `TRANCAT-ACCT-ID` (`PIC 9(11)`) from `app/cpy/CVTRA01Y.cpy` |
| 2 | `acct_group_id` | string, trimmed, may be empty | `ACCT-GROUP-ID` (`PIC X(10)`) from the account record (`app/cpy/CVACT01Y.cpy`), trailing spaces trimmed; **empty string** when the account is not found in the account file |
| 3 | `category_count` | integer, no padding | number of transaction-category balance rows (`CVTRA01Y` records) contributing to the account |
| 4 | `total_balance` | decimal, exactly 2 decimal places, leading `-` for negatives, no thousands separators, no leading zeros (other than `0.xx`) | sum of `TRAN-CAT-BAL` (`PIC S9(09)V99`) across the account's category rows |
| 5 | `total_interest` | same numeric format as `total_balance` | sum of per-category monthly interest computed exactly as `CBACT04C` paragraph `1300-COMPUTE-INTEREST` does: `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, where the rate comes from the disclosure group lookup below; categories with rate `0` contribute `0.00` |
| 6 | `total_fees` | same numeric format | CardDemo has no explicit fee amount (`1400-COMPUTE-FEES` in `CBACT04C` is unimplemented), so this is always `0.00`. Do not invent fees. |
| 7 | `avg_interest_rate` | decimal, exactly 2 decimal places | balance-weighted average `DIS-INT-RATE` across the account's categories: `sum(TRAN-CAT-BAL * DIS-INT-RATE) / sum(TRAN-CAT-BAL)` when `sum(TRAN-CAT-BAL) <> 0`, else `0.00` |

## Source records feeding the extract

- **Driver file:** transaction category balance file — copybook
  `app/cpy/CVTRA01Y.cpy` (`TRAN-CAT-BAL-RECORD`, RECLN 50), read sequentially.
  Key: `TRANCAT-ACCT-ID` / `TRANCAT-TYPE-CD` / `TRANCAT-CD`; balance:
  `TRAN-CAT-BAL`.
- **Account lookup:** `app/cpy/CVACT01Y.cpy` (`ACCOUNT-RECORD`, RECLN 300),
  keyed by `ACCT-ID`; supplies `ACCT-GROUP-ID`.
- **Rate lookup:** `app/cpy/CVTRA02Y.cpy` (`DIS-GROUP-RECORD`, RECLN 50),
  keyed by `DIS-ACCT-GROUP-ID` / `DIS-TRAN-TYPE-CD` / `DIS-TRAN-CAT-CD`;
  supplies `DIS-INT-RATE` (`PIC S9(04)V99`). When the account's group has no
  record for the type/category (file status `23`), retry with group
  `DEFAULT`, exactly as `CBACT04C` paragraph `1200-GET-INTEREST-RATE` /
  `1200-A-GET-DEFAULT-INT-RATE` does.

## Local fixtures (ASCII)

`app/data/ASCII/tcatbal.txt`, `app/data/ASCII/acctdata.txt`,
`app/data/ASCII/discgrp.txt`. These use zoned-decimal overpunch signs —
compile with GnuCOBOL `-fsign=EBCDIC` and include path `-I app/cpy`
(copybooks are tab-indented; re-indent to column 12 in a scratch dir before
compiling, see `scripts/local_compile.sh` / `scripts/run_interest_calc.sh`).

## Consumers

- **Producer:** `app/cbl/CBACT05C.cbl` (new batch extract), JCL
  `app/jcl/INTRPT.jcl`, local runner `scripts/run_interest_report.sh`.
- **Consumer:** `scripts/generate_interest_dashboard.py` →
  `docs/reports/interest-dashboard.html` (self-contained, stdlib-only
  generator, inline CSS/SVG, no CDN/JS framework).

Jira: DJ-106
