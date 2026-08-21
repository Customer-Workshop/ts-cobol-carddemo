# 06_decisions — dated decision log (append-only)

Schema: | Date | Decision | Options considered | Decided by | Ref |

| Date | Decision | Options considered | Decided by | Ref |
|---|---|---|---|---|
| 2026-08-20 | Engagement started; pre-migration artifacts drafted; all target-state fields PROPOSED pending STOP A | — | Devin (draft) | functional/CARDDEMO/CardDemo_target_state.md |
| 2026-08-20 | STOP A: target stack = C#/.NET 8 + ASP.NET Core (all surfaces); UI = Angular 17+; data target = PostgreSQL; topology = single repo (backend/, frontend/ subdirs of ts-cobol-carddemo). PROPOSED defaults (EF Core migrations, xUnit, batch console host w/ restart table, exit-code contract) accepted. | Java/Spring vs .NET; Angular/React/API-only; Postgres/Aurora/SQL Server; single vs split repos | project owner (STOP A) | functional/CARDDEMO/CardDemo_target_state.md |
| 2026-08-20 | STOP B: stream S-01 Sign-on + menu shell selected; type ONLINE; hard stop = default (registered boundaries) | 22 cataloged streams | project owner (STOP B) | functional/CARDDEMO/CardDemo_inventory.md |
| 2026-08-20 | S-01 boundary decisions S01-B1..B6: feature-flagged route registry (B1/B2), Angular router return routing (B3), target-owned Postgres users table + EF Core repo, no SP (B4), deferred USRSEC single-writer to S-12 with seed-import parity meanwhile (B5), SessionContext/JWT COMMAREA seam ported once by S-01 (B6). Password-hashing deviation flagged for STOP C. | see plan §3 | Devin (plan; pending STOP C ratification) | functional/CARDDEMO/S01_SignonMenu_migration_plan.md |
| 2026-08-20 | STOP C: S-01 migration plan approved (3 waves, boundary decisions S01-B1..B6, Phase 0 scaffolding). Password-hashing deviation approved (target stores hashed passwords). | approve / approve w-changes / reject; hash vs clear-text | project owner (STOP C) | functional/CARDDEMO/S01_SignonMenu_migration_plan.md |
