# S-01 UI verification — sign-on + menu shell (COSGN00C / COMEN01C / COADM01C)

UI-level verification of stream S-01 against the real app in a maximized browser.
Verification only — no application code or tests were changed.

## How this was run

- Branch under test: `devin/1789516557-carddemo-java-engagement` (waves 1–3 merged).
- App: `cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn spring-boot:run`,
  default H2 profile, seeded on startup from `app/data` (10 USRSEC users).
- Credentials: `USER0001` / `PASSWORD` (type `U`), `ADMIN001` / `PASSWORD` (type `A`).
- Browser: full-size Chrome at `http://localhost:8080`, keyboard F-keys used for AID parity.
- One continuous screen recording of the whole run: `carddemo-s01-verification.mp4`.
- Test plan used: `test-plan.md`. Screenshots are full, uncropped PNGs.

## Results — all 10 scenarios PASS, no defects found

| # | Scenario | FR-S01 | Expected | Observed | Verdict | Evidence |
|---|----------|--------|----------|----------|---------|----------|
| 01 | Sign-on screen renders | — (screen shell) | 3270 header, USERID/PASSWD fields, ENTER/F3 hints | CC00/COSGN00C header, date/time, credential fields, ENTER=Sign-on + F3=Exit shown | PASS | `s01-01-signon-render.png` |
| 02 | Blank submit → field errors | FR-S01-01, FR-S01-02 | `Please enter User ID ...`, then `Please enter Password ...` | Both messages shown verbatim, screen redisplayed | PASS | `s01-02-blank-userid.png`, `s01-02b-blank-passwd.png` |
| 03 | Bad credentials | FR-S01-03, FR-S01-04 | `User not found. Try again ...` / `Wrong Password. Try again ...` | `XXXX` → user-not-found; `USER0001`/`BADPASS` → wrong-password | PASS | `s01-03-user-not-found.png`, `s01-04-wrong-password.png` |
| 04 | Lower-case creds → main menu | FR-S01-09, FR-S01-06 | Upper-case rule; `U` user lands on `/menu` | `user0001`/`password` signed on, landed on `/menu`; 11 options listed, #11 marked "not installed" | PASS | `s01-05-main-menu.png` |
| 05 | Menu selection outcomes | FR-S01-10, FR-S01-11, FR-S01-14, FR-S01-13 | Invalid option → valid-option error; #11 → not-installed; valid → dispatch | `99` → `Please enter a valid option number...`; `11` → not-installed naming Pending Authorization View; `3` → dispatched to `/api/cards` JSON | PASS | `s01-06-invalid-option.png`, `s01-07-not-installed.png`, `s01-08-dispatch-cards.png` |
| 06 | PF3 on main menu | FR-S01-16 | Sign-off → back at `/signon` | Keyboard F3 returned to blank sign-on screen | PASS | `s01-09-pf3-signoff.png` |
| 07 | Admin sign-on + admin menu | FR-S01-05, FR-S01-17, FR-S01-19, FR-S01-18 | `A` user lands `/admin/menu`, 6 options; valid dispatches; invalid errors | ADMIN001 → `/admin/menu` with 6 options; `1` → `/api/admin/users` JSON (10 users); `7`/`AB` → valid-option error; `5` → not-installed | PASS | `s01-10-admin-menu.png`, `s01-11-admin-dispatch.png`, `s01-12-admin-invalid-option.png`, `s01-13-admin-not-installed.png` |
| 08 | Invalid AID key | FR-S01-20 | `Invalid key pressed. Please see below...`, screen preserved | F5 on admin menu → invalid-key message, selection preserved; F5 on main menu → same | PASS | `s01-14-invalid-key.png`, `s01-14b-main-invalid-key.png` |
| 09 | Access control | FR-S01-12 (admin-only guard equivalent) | `U` user direct-hit `/admin/menu` → denied; unsigned `/menu` → `/signon` | USER0001 → `/admin/menu` got a 403 Forbidden page; after sign-off, `/menu` redirected to `/signon` | PASS | `s01-15-admin-denied.png`, `s01-16-unsigned-menu-redirect.png` |
| 10 | PF3 on sign-on | FR-S01-08 | Plain-text farewell, session ends | `Thank you for using CardDemo application...` shown | PASS | `s01-17-farewell.png` |

## Coverage notes — FRs not verifiable on this UI

- **FR-S01-07** (`Unable to verify the User ...` on security-store failure): requires
  breaking the security store — not reachable without modifying the app. Covered at
  program level only.
- **FR-S01-12** (literal `No access - Admin Only option` on an `A`-flagged main-menu
  option): the web main-menu catalogue contains no `A`-flagged options, so the message
  path is unreachable from the UI. The equivalent guard (regular user → `/admin/menu`
  → 403) was verified in scenario 09.
- **FR-S01-15** (`coming soon` for DUMMY placeholder options): no DUMMY options exist
  in the catalogue — unreachable.
- Dispatch targets are JSON API responses (`/api/cards`, `/api/admin/users`), not 3270
  screens — those surfaces belong to later waves and are out of S-01's scope.
