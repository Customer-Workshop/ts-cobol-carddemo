Id: R-0001
Date: 2026-09-07
Requester: `!mf_stream_migration_plan` / https://partner-workshops.devinenterprise.com/sessions/7425b46a9daf4302a8ba5556dede4295
Team: Data Management
Stream: AccountView
Status: SENT (simulated)
Assumed answer: the committed ASCII/EBCDIC fixture images under `app/data/` are the extract; the one-shot `import` profile loads them into PostgreSQL 16 (target state D5) and wave 1 proceeds on that data
Blocking: no

# Request — initial data extract and load: ACCTDAT, CUSTDAT, CCXREF (+CXACAIX), USRSEC -> PostgreSQL 16

## What we need

A point-in-time extract of four VSAM datasets, for the initial load of the migrated
Account View stream (S-01) into PostgreSQL 16:

| Dataset | DSNAME | Record layout | Key | Target table |
|---|---|---|---|---|
| ACCTDAT | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `app/cpy/CVACT01Y.cpy` (300 bytes) | `ACCT-ID 9(11)` | `accounts` |
| CUSTDAT | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | `app/cpy/CVCUS01Y.cpy` (500 bytes) | `CUST-ID 9(09)` | `customers` |
| CCXREF (base of the `CXACAIX` path) | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | `app/cpy/CVACT03Y.cpy` (50 bytes) | `XREF-CARD-NUM X(16)` | `card_xrefs` |
| USRSEC | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` | `app/cpy/CSUSR01Y.cpy` (80 bytes) | `SEC-USR-ID X(08)` | `users` |

## Format

- Fixed-length sequential unload (IDCAMS REPRO to PS), one file per dataset, record length exactly
  as in the copybook, no headers, no delimiters.
- EBCDIC (IBM037) is acceptable; ASCII is acceptable. State which. Signed numerics must keep
  zoned-decimal overpunch signs (`{`, `}`, `A-I`, `J-R`) — do not re-encode them.
- Packed/binary fields: none in these four layouts; do not repack.

## Also needed (answers, not data)

1. **`CXACAIX` alternate-index definition** (the IDCAMS `DEFINE AIX` for
   `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH`): is it `UNIQUEKEY` or `NONUNIQUEKEY`? The repo has no
   `DEFINE` for it and `COACTVWC` reads the path expecting a single record
   (`app/cbl/COACTVWC.cbl:727-735`). This decides whether an account can have more than one card
   cross-reference row (boundary B-0007, plan open question Q-04).
2. **Date-field content**: `ACCT-OPEN-DATE`, `ACCT-EXPIRAION-DATE`, `ACCT-REISSUE-DATE` are
   `PIC X(10)`. Confirm they always hold `YYYY-MM-DD`; any other content (spaces, `00000000`,
   `MM/DD/YYYY`) changes the target column type from `DATE` to `VARCHAR(10)` (plan open question Q-10).
3. **Refresh cadence** during coexistence: how often may we re-extract while the legacy datasets
   remain the source of truth (target state D8: re-import, no dual-write)?

## Why / when

Needed at the **start of wave 1** (Flyway schema + repositories + fixture load). Without it the
target database is loaded from the committed sample files only, which are demo-sized (50 accounts,
50 cross-reference rows) and cannot evidence multi-card accounts or production date formats.

## Assumption in force

`app/data/ASCII/{acctdata,custdata,cardxref}.txt` and `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
are treated as the extract. Measured on the committed fixture: 50 cross-reference rows over 50
distinct account ids (`awk '{print substr($0,26,11)}' app/data/ASCII/cardxref.txt | sort -u | wc -l`
-> 50), i.e. **one card per account today**, so the deterministic first-row rule chosen for B-0007
(lowest card number) has no observable effect on this data. Date fields in `custdata.txt` and
`acctdata.txt` are ISO `YYYY-MM-DD` in the sample, so `DATE` columns are used, with a wave-1 gate
that falls back to `VARCHAR(10)` if the real extract disagrees.

## Log

- 2026-09-07 `SENT (simulated)` by `!mf_stream_migration_plan`; proceeding on the assumption above.
