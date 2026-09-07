# 04 — Boundary register (module-wide, APPEND-ONLY)

Owned by `!mf_migration_setup`; rows are appended by `!mf_stream_analysis` and resolved by
`!mf_boundary_resolution`. Never rewrite or delete a row; to change a row's state add a new row
with the same `Id` and a later date. Resolution notes live in
`functional/CardDemo/boundaries/<Id>.md`.

## Schema

| Column | Definition |
|---|---|
| `Id` | `B-<nnnn>`, sequential across the module, never reused |
| `Date` | ISO date the row was written |
| `Stream` | stream that found it (e.g. `AccountView`) |
| `Kind` | `PROGRAM` (calls/XCTLs into another stream's program) · `DATASET` (reads/writes data owned elsewhere) · `SCHEDULER` (job dependency) · `EXTERNAL` (MQ, DB2, IMS, FTP, SSO, other system) · `SHARED-UTIL` (common utility) · `DATA-TARGET` (schema/ownership question) |
| `Legacy side` | program / dataset / job on the legacy side, with `path:line` cite |
| `Other side` | what is on the other side of the boundary (stream, system, team) |
| `Direction` | `IN` · `OUT` · `BOTH` |
| `State` | `OPEN` · `PROPOSED` · `DECIDED` · `IMPLEMENTED` · `RETIRED` |
| `Decision ref` | `D-<nnnn>` in `06_decisions.md` once decided |
| `Note` | one line; details in `functional/CardDemo/boundaries/<Id>.md` |

## Register

| Id | Date | Stream | Kind | Legacy side | Other side | Direction | State | Decision ref | Note |
|---|---|---|---|---|---|---|---|---|---|
| B-0000 | 2026-09-07 | (setup) | — | — | — | — | RETIRED | — | Register initialized by `!mf_migration_setup`; no boundaries analysed yet (Account View analysis pending `!mf_stream_analysis`) |
