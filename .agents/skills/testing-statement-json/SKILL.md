---
name: testing-statement-json
description: How to run and verify the COBOL statement JSON extract (CBSTM04C) and the standalone statement viewer in ts-cobol-carddemo.
---

# Testing the statement JSON extract + statement viewer

## Regenerate the extract (GnuCOBOL, no mainframe needed)
- `bash scripts/run_statement_json.sh` from the repo root. Requires `cobc` (GnuCOBOL) on PATH.
- Expected tail of output for the committed sample data: `statements: 50` and `transactions: 300`
  (plus `CBSTM04C STATEMENTS WRITTEN : +000000050` / `TRANSACTIONS WRITTEN: +000000300`).
- The script rewrites `app/data/json/statements.json`, which is committed. The only expected
  diff after a re-run is the `generatedAt` timestamp — restore with
  `git checkout app/data/json/statements.json` when done.
- The script also creates a scratch `build/` directory at the repo root. It may not be
  gitignored, so `rm -rf build` afterwards to keep `git status` clean.

## Validating the JSON contract
- `jq empty app/data/json/statements.json` for syntax.
- Schema: `app/data/json/statements.schema.json` is draft-07 with `additionalProperties: false`.
  Validate with python `jsonschema` (installed): `Draft7Validator(schema).iter_errors(doc)`.
- Good adversarial check: independently recompute `totalDebits` (sum of positive amounts) and
  `totalCredits` (abs of sum of negative amounts) per statement and compare with the
  COBOL-produced values, and assert `accountId` matches `^[0-9]{11}$` and is unique.

## Viewing the statement viewer
- `app/html/statement-viewer.html` is vanilla JS and fetches `../data/json/statements.json`,
  so it must be served over HTTP (file:// will fail CORS): `python3 -m http.server 8877`
  run from the `app/` directory, then open
  `http://localhost:8877/html/statement-viewer.html`.
- There is no mock/fallback data path: if the JSON is missing the page shows a red error
  banner naming the URL and renders no data. Temporarily `mv` the JSON aside and hard-reload
  (ctrl+shift+r) to prove the page is driven only by the real extract, then restore it.
- UI paths: `#account-select` lists one option per statement as `accountId — customer.name`;
  clicking a `thead th` toggles sort (first click asc ▲, second desc ▼); switching account
  resets the sort. Verify sorted order against `jq` min/max values for the selected account.
