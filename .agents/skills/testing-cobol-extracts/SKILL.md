---
name: testing-cobol-extracts
description: How to build, run, and verify the GnuCOBOL batch extracts and their HTML/JSON report generators in ts-cobol-carddemo (e.g. DJ-106 interest analytics), including reproducibility and "no data" checks.
---

# Testing CardDemo COBOL extracts + report generators

## Prerequisites
- `cobc` (GnuCOBOL) on PATH — installed by the repo blueprint (`apt-get install -y gnucobol`).
- `python3` (stdlib only for the report generators) and `ruff` for linting.
- No secrets or network access are required. **Devin Secrets Needed:** none.

## Running an extract locally
Runner scripts under `scripts/` wrap the JCL equivalents, e.g.:

```bash
./scripts/run_interest_report.sh    # app/data/reports/interest-summary.csv
./scripts/run_statement_json.sh     # app/data/json/statements.json
```

They compile into a scratch `build/` dir, re-indent the tab-indented copybooks from
`app/cpy` to column 12, compile with `-fsign=EBCDIC` (the ASCII fixtures in
`app/data/ASCII` use zoned-decimal overpunch signs), and load indexed files via a
local loader program before running the batch program.

Notes for testers:
- `build/` is scratch output — delete it after testing so `git status` stays clean.
- Chatty per-record messages such as `DISCLOSURE GROUP RECORD MISSING / TRY WITH
  DEFAULT GROUP CODE` are expected on the shipped fixtures and are not failures.
- Shipped fixture balances are all zero, so extracts legitimately produce `0.00`
  totals and empty `acct_group_id` values (rendered as `(unknown)` downstream).
  Do not treat all-zero KPIs as a bug without checking the fixtures.

## Reproducibility check (the highest-value assertion)
Committed outputs are supposed to be byte-identical to freshly generated ones.
Delete the committed artifact first so a stale file cannot mask a broken generator:

```bash
rm -f app/data/reports/interest-summary.csv
./scripts/run_interest_report.sh && ./scripts/run_interest_report.sh   # run twice
git status --porcelain    # must show no modification of the artifact
```

Also assert structure explicitly rather than eyeballing: exact header line, row
count, 7 fields per row (`awk -F, 'NF!=7'`), and ascending key order
(`cut -d, -f1 | sort -c`).

## Report generators
`scripts/generate_interest_dashboard.py` takes an optional positional input CSV and
`-o OUTPUT_HTML`, so edge cases can be exercised without dirtying the repo:

```bash
python3 scripts/generate_interest_dashboard.py                                  # defaults
python3 scripts/generate_interest_dashboard.py /tmp/missing.csv -o /tmp/x.html   # "no data" page, exit 0
: > /tmp/empty.csv && python3 scripts/generate_interest_dashboard.py /tmp/empty.csv -o /tmp/y.html
python3 -m unittest discover -s tests
ruff check scripts/ tests/
```

The HTML is self-contained (inline CSS + inline SVG, no CDN/JS), so view it directly
in Chrome via a `file://` URL — no dev server is needed. Maximize the window
(`wmctrl -r :ACTIVE: -b add,maximized_vert,maximized_horz`) and use `End` to reach the
bottom of the full account table for screenshot evidence.
