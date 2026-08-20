# 06_decisions — dated decision log (append-only)

Schema: | Date | Decision | Options considered | Decided by | Ref |

| Date | Decision | Options considered | Decided by | Ref |
|---|---|---|---|---|
| 2026-08-20 | Engagement started; pre-migration artifacts drafted; all target-state fields PROPOSED pending STOP A | — | Devin (draft) | functional/CARDDEMO/CardDemo_target_state.md |
| 2026-08-20 | STOP A: target stack = C#/.NET 8 + ASP.NET Core (all surfaces); UI = Angular 17+; data target = PostgreSQL; topology = single repo (backend/, frontend/ subdirs of ts-cobol-carddemo). PROPOSED defaults (EF Core migrations, xUnit, batch console host w/ restart table, exit-code contract) accepted. | Java/Spring vs .NET; Angular/React/API-only; Postgres/Aurora/SQL Server; single vs split repos | dhrov.subramanian (STOP A) | functional/CARDDEMO/CardDemo_target_state.md |
