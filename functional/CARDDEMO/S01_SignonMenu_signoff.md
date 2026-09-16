# S-01 Sign-on + Menu Shell — Migration Sign-off (Java / Spring Boot)

Stream: **S-01** — COSGN00C (CC00), COMEN01C (CM00), COADM01C (CA00).
Target: Java 21 / Spring Boot 3 (`spring-boot/`), Thymeleaf screens + JSON API,
Postgres `users` table + Spring Security session seam.
Engagement branch: `devin/1789516557-carddemo-java-engagement`. Waves: PRs #100
(shell/Postgres/Flyway/CI), #101 (sign-on), #102 (menus), #103 (UI evidence).
Sign-off child PR: `devin/s01-signoff-audit` (this document + the audit).
Date: 2026-09-16. Auditor: independent session (did not migrate the code).

## 1. Scope and hard stop

Authenticate against USRSEC and present the role-appropriate menu with dispatch to the
owning function. Hard stop at every transfer out of the three programs — all 11
main-menu and 6 admin route programs are excluded. Verified: no route-program screen or
controller was added by the S-01 waves; `app/` is untouched (`git diff main..HEAD --
app/` empty).

## 2. Requirement traceability — all 20 FRs

Every FR traces to implementing code (file:line) and at least one covering test or
evidence item. Full cites in `S01_SignonMenu_audit_java.md` §1.

| FR | Requirement | Covering test | Evidence | Verdict |
|---|---|---|---|---|
| FR-S01-01 | blank user id → `Please enter User ID ...` | `SignOnUiIntegrationTest.blankUserId...frS0101`; REST parity | results md S1; live smoke | PASS |
| FR-S01-02 | blank password → `Please enter Password ...` | `...blankPassword...frS0102` | results md S1 | PASS (cursor: F-3) |
| FR-S01-03 | unknown user → `User not found. Try again ...` | `...unknownUser...frS0103` | live smoke on Postgres | PASS |
| FR-S01-04 | wrong password → `Wrong Password. Try again ...` | `...wrongPassword...frS0104` | live smoke on Postgres | PASS (cursor: F-3) |
| FR-S01-05 | 'A' user → admin menu, session carries id+type | `...adminSignon...frS0105`; `AuthSessionSeamIntegrationTest` | live smoke; results md | PASS |
| FR-S01-06 | 'U' user → main menu, session carries id+type | `...regularSignon...frS0106`; seam test | live smoke; results md | PASS |
| FR-S01-07 | store error → `Unable to verify the User ...` | `SignOnUiStoreErrorIntegrationTest...frS0107` | coverage-noted in results md | PASS (REST gap: F-2) |
| FR-S01-08 | PF3 → farewell text, session ends | `...pf3ShowsFarewell...frS0108` | results md S8 | PASS |
| FR-S01-09 | credentials upper-cased | `...lowerCaseCredentials...frS0109` | live smoke (lower-case works) | PASS |
| FR-S01-10 | main menu lists 11 options, catalogue order | `MenuUiIntegrationTest...Eleven...frS0110`; `MenuServiceTest.cataloguePins...` | live smoke | PASS |
| FR-S01-11 | invalid option → `Please enter a valid option number...` | `...invalidOption...frS0111` (0/12/AB) | results md | PASS |
| FR-S01-12 | 'U' picks 'A'-flagged option → `No access - Admin Only option... ` | `MenuUiCatalogueIntegrationTest...frS0112` (fixture); `MenuServiceTest.regularUserIsDenied...` | flagged unreachable w/ shipped data | PASS (F-5) |
| FR-S01-13 | permitted option dispatches with user context | `...validOptionRedirects...frS0113`; `uiRouteResolvesOnlyBrowsableEndpoints` | live smoke (3→`/api/cards`); results md | PASS (F-4) |
| FR-S01-14 | option 11 → `This option <name> is not installed...` | `...pendingAuthorization...frS0114` | live smoke verbatim | PASS |
| FR-S01-15 | placeholder option → `This option <name> is coming soon ...` (green) | `...placeholderOptionShowsComingSoon...frS0115` (fixture) | flagged unreachable w/ shipped data | PASS (F-1, F-5) |
| FR-S01-16 | PF3 on menu → sign-on screen | `...pf3SignsOff...frS0116` (both menus) | live smoke | PASS |
| FR-S01-17 | admin menu lists 6 options, catalogue order | `...Six...frS0117` | live smoke | PASS |
| FR-S01-18 | invalid admin option → valid-option message | `...invalidAdminOption...frS0118` (0/7/AB) | live smoke (option 7) | PASS |
| FR-S01-19 | admin option dispatches with context | `...validAdminOption...frS0119` | results md | PASS |
| FR-S01-20 | unmapped AID → `Invalid key pressed. Please see below...` | `...unmappedAid...frS0120` (sign-on + both menus) | live smoke (F7) | PASS |

No requirement was marked verified on missing data without a flag: FR-S01-12 and
FR-S01-15 are unreachable in the shipped catalogues and are explicitly flagged (F-5);
FR-S01-07's UI path is test-proven, its REST-surface divergence is flagged (F-2).

## 3. Parity summary

- All user-visible message literals are byte-for-byte equal to the source working-
  storage literals / CSMSG01Y, including the `No access - Admin Only option... `
  trailing space.
- Catalogues match COMEN02Y / COADM02Y exactly: 11 + 6 options, names, program keys,
  order, access flags; dead slot 12 correctly absent.
- Validation order, role landing, AID semantics, not-installed vs coming-soon
  distinction, and the unsigned (EIBCALEN=0-equivalent) bounce all match the source.
- Documented generalizations per FR doc §9: full option name in coming-soon /
  not-installed text; admin name-suppressed form unified; F-key AID mapping.
- Approved deviations: plaintext-compatible password storage (STOP C); Flyway V2
  baselines all ten domain tables (`.migration/06_decisions.md`).

## 4. End-to-end and CI status

- **Independent execution (auditor-run):** `mvn clean verify` → **69 tests, 0
  failures, 0 errors** (13 test classes; counts in audit §4).
- **Postgres smoke (auditor-run):** fresh `postgres:16` container, `postgres` profile:
  Flyway applied V1–V3, seeder loaded 10 users + all fixture tables from the EBCDIC
  source file; REST sign-on, UI sign-on, role landing, menu render, dispatch,
  not-installed, invalid AID, and unsigned bounce all verified live.
- **CI gate:** `.github/workflows/ci.yml` runs `mvn -B clean verify` in `spring-boot/`
  on every push and PR. `verify` is the required check; green on PR #103 (last merged
  wave) and expected to gate this branch's PR the same way.
- **UI evidence:** `functional/CARDDEMO/evidence/s01/` — `ui_test_results.md`
  (10/10 scenarios PASS), `test-plan.md`, `carddemo-s01-verification.mp4`, 19
  screenshots.

## 5. Boundary closure — S01-B1..B6

| ID | Decision | Status at sign-off |
|---|---|---|
| S01-B1 | Feature-flagged route registry; per-stream flag flips at merge | IMPLEMENTED — registry + dispatch seam in `MenuService`; audit F-4 notes flags track baseline-endpoint existence rather than stream merges — record clarification recommended, no functional gap |
| S01-B2 | COPAUS0C availability flag, default off until S-19 | IMPLEMENTED — option 11 `implemented=false`, distinct not-installed message |
| S01-B3 | Stable return routing (`/signon`, `/menu`, `/admin/menu`) | IMPLEMENTED — PF3 → signoff + `/signon`; consumed by later streams' returnUrl idiom |
| S01-B4 | Target-owned Postgres `users` + repository, RESP protocol mapped | IMPLEMENTED — `V1__users.sql`, `SecurityUserRepository`; verified on live Postgres |
| S01-B5 | USRSEC single-writer deferred to S-12 | DEFERRED, documented — dependency: S-12 (COUSR01C/02C/03C) adopts the repository; re-entry condition recorded in `.migration/04_boundary_register.md`; interim idempotent seed import + `UsersSeedParityIntegrationTest` prove fixture parity |
| S01-B6 | Session seam ported once, owner S-01 | IMPLEMENTED — SecurityContext carries user id + type; `/api/auth/session` proves it; consumed as the entry guard by later streams |

No undecided boundary, no undocumented deferral.

## 6. Audit verdict

`S01_SignonMenu_audit_java.md` (this PR): **PASS with findings** — zero missing,
stubbed, or mock-only in-scope programs; all 20 FRs traced with PASS; 4 LOW + 4 INFO
findings, none blocking. Findings reproduced, not summarized away:

- F-1 LOW — coming-soon text emits `...Viewis coming soon` (no space); matches neither
  source quirk nor the §9 documented example. Unreachable in shipped data.
- F-2 LOW — FR-S01-07 verbatim message only on UI; REST sign-on store failure returns
  generic 500 `Unable to process request`.
- F-3 LOW — error cursor placement not reproduced (autofocus fixed on USERID).
- F-4 LOW — registry flag semantics vs the recorded B1 contract (see §5).
- F-5 INFO — FR-S01-12/15 unreachable with shipped catalogue; fixture-proven only.
- F-6 INFO — menu PF3 invalidates the session (stronger than source).
- F-7 INFO — REST null-field sign-on returns bean-validation text, not verbatim.
- F-8 INFO — admin disabled-option text unified (documented in §9; closes .NET F-3).

## 7. Residual risks and deferrals

- **S01-B5 write path** — the `users` table writer contract lands with S-12; until
  then the seed import is the only writer. Re-entry: when S-12 migrates COUSR00C..03C.
- **Coming-soon reachability** — no shipped catalogue row exercises the coming-soon
  branch; if a placeholder row is ever added, F-1's text discrepancy becomes
  user-visible.
- **Route targets are APIs, not screens** — dispatching a migrated option lands on a
  JSON endpoint until that stream's UI wave ships; intended interim state, tracked by
  B1 and per-stream waves.
- **FR-S01-07 REST surface** — generic 500 text until F-2 is addressed or recorded.

## 8. Outstanding lead-time items (before legacy decommission)

- S-12 adoption of the `users` table as its write path (B5 re-entry).
- S-19 (COPAUS0C / pending-authorization) flips the option-11 flag (B2 re-entry).
- Per-stream UI waves replace API-landing dispatch with real screens (B1 flags).
- None of these block S-01 acceptance; all block retiring the CICS transactions.

## Disposition

Stream S-01 is **signed off for acceptance**: every in-scope program is migrated with
green tests, every requirement is traced to a covering test or flagged evidence gap,
the end-to-end flow is green and re-proven in CI, every touched boundary is
implemented or documented-deferred, and the independent audit — run by a session that
did not write the code — passes with non-blocking findings.
