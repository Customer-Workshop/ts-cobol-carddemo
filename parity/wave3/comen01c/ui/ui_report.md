# COMEN01C /menu independent browser parity verification

Completed the requested browser procedure on PR #97 head `7ca7906`, branch `devin/1788757216-w3-parity`. Angular `http://localhost:4200` used its configured `/api` proxy to Spring Boot `http://localhost:8080`, with the supplied real PostgreSQL environment. No mocks or application-source edits. No Maven commands were run and the background Maven verification was not disturbed.

**Mismatch requiring disposition:** Escape, F3 and the menu Exit button all returned to `/signon`, but **none displayed** `Thank you for using CardDemo application...`. A fresh sign-on form appeared instead. Logout/route protection worked. This is marked FAIL against the supplied expected UI behavior, independently of whether the legacy menu intentionally returns to a fresh sign-on map.

**Browser caveat:** F1 opened Chrome Help in another tab; F5 reloaded the menu and refreshed its time; F7 opened Chrome's caret-browsing prompt (cancelled); F12 opened DevTools (closed). Tab moved focus to Continue. Thus literal “nothing changes” is not true for browser-owned behavior; the requested application-level absence of an invalid-key error passed. Browser-intercepted key delivery to the Angular handler is not independently proven.

## Results

`Invalid` below means the exact ERRMSG `Please enter a valid option number...`.
`Facade` means the exact ERRMSG `Option not available in this release`.

| Case | Input/action | Expected | Observed | Verdict |
|---|---|---|---|---|
| 1 route guard | Direct `/menu`, unauthenticated | `/signon` | `/signon`, empty sign-on form | PASS |
| 2 user entry | USER0001 / PASSWORD | `/menu` | `/menu` | PASS |
| 2 header | Initial menu | Tran: CM00; AWS Mainframe Modernization; Prog: COMEN01C; CardDemo; formatted date/time | All exact; Date: `09/07/26`; Time: `09:13:37` | PASS |
| 2 screen title | Initial menu | Main Menu | `Main Menu` | PASS |
| 2 options | Initial menu | Exactly 11 specified rows in order, no 12th | All 11 exact labels listed below; no twelfth button/row | PASS |
| 2 option field | Initial menu | Prompt `Please select an option :`; maxlength=2 | Exact prompt; DOM `maxlength="2"`; empty field | PASS |
| 2 footer/error | Initial menu | `ENTER=Continue  F3=Exit`; empty ERRMSG element | Exact footer textContent including two spaces; `[data-field="ERRMSG"]` exists and is empty | PASS |
| 2 user role | USER0001 | No admin banner | No ADMINBANNER element or banner text | PASS |
| 3 disabled rows | Click rows 2..11 | Disabled/inert | Each has disabled=true and aria-disabled=true; clicks did not navigate or populate option/error | PASS |
| 3 active row | Click row 1 | `/accounts/view` | `/accounts/view`; `Account View` and `Not yet available in this release.` | PASS |
| 3 session retention | Browser Back | Signed-in `/menu` | Full menu, no sign-on redirect | PASS |
| 4 edit | Empty + Enter | Invalid; echo `00` | Invalid; field `00`; `/menu` | PASS |
| 4 edit | `0` + Enter | Invalid; echo `00` | Invalid; field `00`; `/menu` | PASS |
| 4 edit | `A` + Enter | Invalid; echo `0A` | Invalid; field `0A`; `/menu` | PASS |
| 4 edit | `12` + Enter | Invalid; echo `12` | Invalid; field `12`; `/menu` | PASS |
| 4 edit | `99` + Continue | Invalid; echo `99` | Invalid; field `99`; `/menu` | PASS |
| 4 edit | Space then `1` + Enter | `/accounts/view` | `/accounts/view`, same placeholder; option field no longer on destination | PASS |
| 4 edit | `01` + Enter | `/accounts/view` | `/accounts/view`, same placeholder; option field no longer on destination | PASS |
| 4 edit | `1` + Enter | `/accounts/view` | `/accounts/view`, same placeholder; option field no longer on destination | PASS |
| 4 facade | `2` + Enter | Facade; echo `02`; stay menu | Facade; field `02`; `/menu` | PASS |
| 4 facade | `11` + Enter | Facade; echo `11`; stay menu | Facade; field `11`; `/menu` | PASS |
| 4 length | Type `123`, do not submit | Holds only `12` | Field `12`; prior facade error unchanged | PASS |
| 5 DV-03 | F1 | No application invalid-key error; note interception | Chrome Help tab opened; original menu unchanged when returned | PASS (browser caveat) |
| 5 DV-03 | F5 | No application invalid-key error; note interception | Menu reloaded, time refreshed to `09:15:58`, no error | PASS (browser caveat) |
| 5 DV-03 | F7 | No application invalid-key error; note interception | Chrome caret prompt, cancelled; menu unchanged | PASS (browser caveat) |
| 5 DV-03 | F12 | No application invalid-key error; note interception | Chrome DevTools opened, closed; menu unchanged | PASS (browser caveat) |
| 5 DV-03 | PageDown | No application state/error change | Menu remained visible; empty option/error | PASS |
| 5 DV-03 | Tab | No application state/error change | Focus moved to Continue; empty option/error | PASS |
| 6 Escape routing | Escape from authenticated menu | Sign off to `/signon` | `/signon` fresh form | PASS |
| 6 Escape message | Escape | `Thank you for using CardDemo application...` | No thank-you text | FAIL |
| 6 F3 routing | Re-enter authenticated menu, F3 | Sign off to `/signon` | `/signon`; F3 not browser-intercepted | PASS |
| 6 F3 message | F3 | Same thank-you text | No thank-you text | FAIL |
| 6 F3 invalidation | Direct `/menu` after F3 | `/signon` | `/signon` | PASS |
| 6 Exit routing | Re-enter authenticated menu, click Exit | Sign off to `/signon` | `/signon` fresh form | PASS |
| 6 Exit message | Click Exit | Same thank-you text | No thank-you text | FAIL |
| 6 Exit invalidation | Direct `/menu` after Exit | `/signon` | `/signon` | PASS |
| 7 admin entry | ADMIN001 / PASSWORD | `/menu` | `/menu` | PASS |
| 7 banner | Initial admin menu | `Administration is not available in this release` | Exact text visible | PASS |
| 7 rows | Admin menu | Same 11 labels/order/disabled flags | Identical to user menu | PASS |
| 7 option 1 | Admin clicks row 1 | `/accounts/view` | `/accounts/view`, Account View placeholder | PASS |
| 7 forbidden admin error | Admin menu | No `No access - Admin Only option` | Absent from visible text and DOM | PASS |
| 8 forbidden phrases | Initial user and admin menu DOM + visible text | None of `No access`, `is not installed`, `coming soon`, `Invalid key` | Case-insensitive regex scans of body.innerText and documentElement.outerHTML both returned null for each menu | PASS |

Exact observed rows, in order:

```text
01. Account View
02. Account Update
03. Credit Card List
04. Credit Card View
05. Credit Card Update
06. Transaction List
07. Transaction View
08. Transaction Add
09. Transaction Reports
10. Bill Payment
11. Pending Authorization View
```

The initial DOM was inspected live through the browser, including `data-field`, disabled flags, maxlength and forbidden-phrase scans. The computer tool's temporary HTML paths were unavailable to shell copying, so raw HTML snapshots were not retained; this does not invalidate the live inspections. Full-screen screenshots and recording are retained.

## Visual evidence

| User menu — COMEN1A field map | Admin menu — target-only banner |
|---|---|
| ![User menu](https://partner-workshops.devinenterprise.com/attachments/4ba38adf-72bf-4c8c-866f-cb445e3fcf9d/user_menu.png) | ![Admin menu](https://partner-workshops.devinenterprise.com/attachments/077d0014-7cc9-4167-94af-672c77f06341/admin_menu.png) |

| FAIL: Escape returns to sign-on without thank-you | PASS: unavailable option 2 echoes 02 |
|---|---|
| ![Missing thank-you after Escape](https://partner-workshops.devinenterprise.com/attachments/80270700-b8a7-4e16-a0ae-97b1043886ba/escape_signon.png) | ![Facade option 02](https://partner-workshops.devinenterprise.com/attachments/e51b4392-edf7-4574-a9e4-540b368208fa/facade_02.png) |

## Local artifact paths

All paths below are relative to the absolute evidence directory:
`/home/ubuntu/repos/ts-cobol-carddemo/parity/wave3/comen01c/ui/`.

- Report: `ui_report.md`
- Annotated continuous recording: `recording.mp4`
- Plan/server log: `test_plan.md`, `frontend_run.log`
- Screenshots: `initial_guard.png`, `user_menu.png`, `admin_menu.png`, `account_view.png`, `space1_destination.png`, `01_destination.png`, `1_destination.png`, `invalid_empty.png`, `invalid_0.png`, `invalid_A.png`, `invalid_12.png`, `invalid_99.png`, `facade_02.png`, `facade_11.png`, `maxlength_123.png`, `escape_signon.png`, `f3_signon.png`, `exit_signon.png`, `post_exit_guard.png`, `f7_browser_prompt.png`, `f12_browser_devtools.png`.

## Setup and remaining needs

- Frontend dependencies were missing; ran `npm ci`, then `npx ng serve`. Server remains running on 4200 (shell d3577c). Existing proxy configuration was used without modification.
- npm ci completed but reported 57 dependency audit findings (7 low, 17 moderate, 32 high, 1 critical). No audit fixes were attempted; this is setup output, not a runtime parity verdict.
- Blueprint was consulted. Suggested setup documentation: ensure frontend dependencies are installed and document starting Angular with the existing proxy while the real backend/seeded PostgreSQL are running; do not duplicate an already-running backend/Maven process.
- No SKILL.md changes suggested for this narrowly scoped verification.
- User decision needed: resolve or explicitly accept the missing menu-exit thank-you relative to the requested expectation. No credentials or further user participation needed.


## Verifier ruling on the three "message" FAIL rows (added after the run, COBOL-derived)
The expectation given to the browser run ("sign-off text after Exit") came from the orchestrator brief, not from the source. The
COBOL says otherwise: `COMEN01C.cbl:96-98` PF3 -> `RETURN-TO-SIGNON-SCREEN` (`:196-203`) = `EXEC CICS XCTL PROGRAM('COSGN00C')`
with **no COMMAREA**; `COSGN00C.cbl:80-83` then takes the `EIBCALEN = 0` branch and sends a **blank** sign-on map. The
`Thank you for using CardDemo application...` text is only set when COSGN00C itself receives PF3 (`COSGN00C.cbl:88-89`). A fresh
sign-on form with no thank-you text is therefore the parity-correct result. Rows "6 Escape message", "6 F3 message" and
"6 Exit message" are re-classified **PASS** in `COMEN01C_parity_results.md`; the session is invalidated (verified by the
post-exit guard rows and by HTTP M-09b/c -> 401). The `POST /api/auth/signoff` JSON does carry the thank-you text (HTTP M-09a);
that is COSGN00C's wave-2 contract and is not displayed by the menu, matching the legacy.
