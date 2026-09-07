# COSGN00C browser parity rerun

**11/11 requested checks PASS.** Real, maximized Chrome against the existing Angular :4200 → Spring Boot :8080 → supplied carddemo-pg environment. Checkout `445b4b9` incorporates PR #96 head `60413a0`. No app files modified, dependencies installed, or services restarted. No PR actions performed.

**Key delta verified:** `USER0002` / `WRONGPWD` produces exactly `Wrong Password. Try again ...` and `document.activeElement.id === "password"` (type `password`). The visible blue focus outline is on Password, not User ID.

Strings below are copied from DOM. `""` means an empty string. Code spans preserve significant spaces.

## Per-check results

| Check | Result | Exact observations |
|---|---|---|
| 1. Initial COSGN0A map (Regression) | PASS | Field-by-field observations below. |
| 2. Empty user + Enter (Regression) | PASS | `Please enter User ID ...`; activeElement.id `userId`; URL `http://localhost:4200/signon`. Password was also empty. |
| 3. USER0001 / empty password + Enter (Regression) | PASS | `Please enter Password ...`; activeElement.id `password`; `/signon` unchanged. |
| 4. Both cleared + Enter (Regression) | PASS | Input values `["",""]`; `Please enter User ID ...`; activeElement.id `userId`; user error wins; `/signon` unchanged. |
| 5. NOBODY01 / PASSWORD (Regression) | PASS | `User not found. Try again ...`; activeElement.id `userId`; `/signon` unchanged. |
| 6. USER0002 / WRONGPWD — E-06 fix | PASS | `Wrong Password. Try again ...`; activeElement.id `password`, activeElement.type `password`; `/signon` unchanged. |
| 7. lowercase user0002 / password (Regression) | PASS | URL `http://localhost:4200/menu`; exact body innerText `"CardDemo\nMain Menu\nNot yet available in this release."`; no user id/type displayed. |
| 8. ADMIN002 / PASSWORD (Regression) | PASS | URL `http://localhost:4200/menu`; exact body innerText `"CardDemo\nMain Menu\nNot yet available in this release."`; no user id/type displayed. |
| 9. Exit button / F3 / Escape (Regression) | PASS | Independently reloaded before each keyboard exit. Each shows `Thank you for using CardDemo application...`; zero inputs; only button `Sign on again`; ERRMSG `""`; URL `http://localhost:4200/signon`. |
| 10. F1 / F7 / F12 / PageDown (Regression) | PASS | After **each** key: ERRMSG `""`; DOM contains no `Invalid key pressed`; new application resource entries `[]`. Browser-native behaviors detailed below. F5 excluded as requested. |
| 11. Nine-character input (Regression) | PASS | Typed `123456789` into each input. Both DOM values `12345678`, lengths 8, maxLength 8. Password visibly masked and type `password`. ERRMSG `""`; no length message. |

No observed application URL contained `CC00`; that identifier was display text only. The menu remains an existing placeholder: sign-on/navigation is verified, not a working menu or displayed identity.

## Initial field map

| Field | Exact rendered text / observed DOM attributes |
|---|---|
| TRNNAME | `CC00`, top-left header area |
| TITLE01 | `AWS Mainframe Modernization` |
| CURDATE | `09/07/26` (mm/dd/yy) |
| PGMNAME | `COSGN00C` |
| TITLE02 | `CardDemo` |
| CURTIME | `08:27:49` (hh:mm:ss) |
| APPLID | `CARDDEMO` |
| SYSID | `CICS` |
| USERID | id `userId`, value `""`, type `text`, maxlength attribute `"8"`, autofocus attribute present, initial activeElement.id `userId` |
| PASSWD | id `password`, value `""`, type `password`, maxlength attribute `"8"` |
| User label / hint | `User ID     :` / `(8 Char)` |
| Password label / hint | `Password    :` / `(8 Char)` |
| ERRMSG | `""` |
| Footer | `ENTER=Sign-on  F3=Exit` — exact DOM string includes **two spaces** before F3 |
| Buttons | `Sign-on`, `Exit` |

All eight display fields are non-contenteditable `OUTPUT` elements, not inputs. The only two inputs are USERID and PASSWD. The literal `Invalid key pressed` is absent from `document.documentElement.outerHTML`. This checks the loaded DOM, not every downloaded JavaScript bundle.

As before, header label colons for Tran/Prog/Date/Time wrap onto their own lines at this viewport. Requested output values remain visible. The footer's exact two spaces were verified from DOM, independently of browser whitespace rendering.

## Exit and unsupported-key details

Exit button, F3 and Escape retain the CardDemo toolbar, full screen header and exact footer `ENTER=Sign-on  F3=Exit`. Intro, banner and form disappear. Date is `09/07/26`; retained times are `08:30:18` (button), `08:30:40` (F3), `08:30:59` (Escape).

F1 opened Chrome Help (`Get started with Chrome`) in a separate tab; closed it and checked the unchanged app. F7 opened `Turn on caret browsing?`; clicked `Cancel`. F12 opened DevTools; closed it. PageDown left the app unchanged.

Resource Timing baseline contained only `http://localhost:4200/api/auth/header` among API requests. After a baseline timestamp, each key produced zero new entries in the application's `performance.getEntriesByType('resource')`. No claim of zero browser-global traffic is made: native Chrome Help generates separate traffic.

## Evidence

All new artifacts are in `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/rerun/ui/`; the original `ui/` directory was not touched.

- WEBP: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/rerun/ui/cosgn00c_rerun.webp`
- Annotated MP4: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/rerun/ui/cosgn00c_rerun.mp4`
- Report: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/rerun/ui/ui_report.md`
- Plan: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/rerun/ui/test_plan.md`

Screenshot filenames under the absolute directory above:

`01_initial.png`, `02_empty_user.png`, `03_empty_password.png`, `04_both_empty.png`, `05_user_not_found.png`, `06_wrong_password_focus_fixed.png`, `07_user_menu.png`, `08_admin_menu.png`, `09_exit_button.png`, `09_f3.png`, `09_escape.png`, `10_f1.png`, `10_f7.png`, `10_f12.png`, `10_pagedown.png`, `11_length_cap.png`.

<details open>
<summary>Verified password-focus evidence</summary>

| E-06 wrong password — fix verified | Empty password — Regression |
|---|---|
| ![Wrong password now focuses Password](https://partner-workshops.devinenterprise.com/attachments/72c62487-8cd5-495f-9cf7-27a9daae89e6/06_wrong_password_focus_fixed.png) | ![Empty password retains Password focus](https://partner-workshops.devinenterprise.com/attachments/8232f132-f7a8-4d45-8402-3f83f1af32cd/03_empty_password.png) |

</details>

Failures: none in the 11 requested checks. Incomplete coverage: none within those checks; F5 explicitly excluded. SKILL.md suggestions: none. Blueprint updates: none (existing blueprint already reviewed; no dependency/tool installs or service starts needed). User action needed: none. PR comment: none, as requested.
