# S-01 AccountView — artifact index (stream close, 2026-09-07)

Stream branch `devin/1788757216-cardemo-account-view-stream` @ `7420c93`. Merge to `main` reserved to a human reviewer (D-0044).

## Governance
| Artifact | Path |
|---|---|
| Target state (STOP A) | `functional/CardDemo/CardDemo_target_state.md` |
| Module inventory (STOP B) | `functional/CardDemo/CardDemo_inventory.md`, `functional/CardDemo/inventory/`, `functional/CardDemo/diagrams/` |
| Stream analysis | `functional/CardDemo/AccountView_analysis.md` |
| Stream functional requirements (FR-01..25, E-01..14) | `functional/CardDemo/AccountView_functional_requirement.md` |
| Migration plan (4 waves, 16 boundaries, DV-01..05, Q-01..14) | `functional/CardDemo/AccountView_migration_plan.md` |
| Program FRs | `functional/CardDemo/programs/{CSUTLDTC,COSGN00C,COMEN01C,COACTVWC}_functional_requirement.md` |
| Sign-off package (STOP E) | `functional/CardDemo/AccountView_signoff.md` |
| Ledger: context, target, conventions, glossary | `.migration/00_context.md` .. `03_glossary.md` |
| Boundary register | `.migration/04_boundary_register.md` |
| Progress log | `.migration/05_progress.md` |
| Decision log (D-0001..D-0044) | `.migration/06_decisions.md` |
| Runbook, simulated requests R-0001..R-0003 | `.migration/07_runbook.md`, `.migration/requests/` |

## Implementation (PRs into the stream branch)
| Wave | Program(s) | PR | Parity |
|---|---|---|---|
| 1 | Phase 0 scaffolding, data seams (Flyway V1, import), CSUTLDTC | #95 | `parity/wave1/Wave1_parity_results.md` (23/23; seams 1,700 fields 0 unexplained; Q-10 200/0) |
| 2 | COSGN00C sign-on | #96 | `parity/wave2/Wave2_parity_results.md` (20/20 after 1 fix cycle) |
| 3 | COMEN01C menu | #97 | `parity/wave3/COMEN01C_parity_results.md` (19/19) |
| 4 | COACTVWC Account View | #98 | `parity/wave4/COACTVWC_parity_results.md` (24/24 after 1 fix cycle; 145/145 fields) |
| UI e2e | full journey | — | `parity/ui/AccountView_ui_test_report.md`, `parity/ui/evidence/` (29 scenarios) |

Code: `backend/` (Java 21 / Spring Boot 3.4 / Flyway / Testcontainers), `frontend/` (Angular 18 + Material), `.github/workflows/`. Legacy `app/` untouched.

## Carried into UAT
R-0001 AIX uniqueness on real data (accepted residual risk); R-0002 SSO deferred (re-entry: IdP registration / non-demo env); B-0015 CEEDAYS deferred (re-entry S-08/S-09); D-UI-01 menu Tab order; FR footnotes per D-0042 (75-byte error texts, `ficoScore` X(3), DV-03 browser-native keys); A-20 generic-500 path untested; DV-07 label in tests; npm dev-only advisory (`tar` via `@angular/cli`).
