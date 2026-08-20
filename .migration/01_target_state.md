# 01_target_state — pointer

Authoritative artifact: `functional/CARDDEMO/CardDemo_target_state.md` (same repo/branch).

Summary: CONFIRMED at STOP A (2026-08-20, dhrov.subramanian): C#/.NET 8 + ASP.NET Core on all
surfaces; Angular 17+ UI; PostgreSQL via EF Core 8 migrations; single-repo topology (`backend/`,
`frontend/` subdirs). Batch = .NET console host with restart/checkpoint table and legacy
exit-code contract. MQ broker choice deferred to the stream that needs it (boundary register).
