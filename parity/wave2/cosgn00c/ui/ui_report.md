# COSGN00C independent browser parity report

Executed in real, maximized Chrome against the already-running Angular → Spring Boot → supplied PostgreSQL environment. Branch `devin/1788757216-w2-parity`, HEAD `01a7138`. No application files changed; no installs/restarts.

**Result: FAIL against the requested oracle — check 6 focuses `userId`, not `password`.** All requested browser checks executed except the explicitly skipped F5. `/menu` is a placeholder; this verifies sign-on navigation, not a functional menu or displayed identity.

Exact strings below are copied from DOM (`""` denotes empty). Spaces inside code spans are significant. Error states 2–6 all remained at `http://localhost:4200/signon`.

## 1. Initial COSGN0A field map — PASS

| Field | Exact observed value / attributes |
|---|---|
| TRNNAME | `CC00`, top-left header area |
| TITLE01 | `AWS Mainframe Modernization` |
| CURDATE | `09/07/26` (mm/dd/yy) |
| PGMNAME | `COSGN00C` |
| TITLE02 | `CardDemo` |
| CURTIME | `07:51:58` (hh:mm:ss) |
| APPLID | `CARDDEMO` |
| SYSID | `CICS` |
| USERID | value `""`, id `userId`, type `text`, maxlength attribute `"8"`, autofocus attribute present; activeElement.id `userId` |
| PASSWD | value `""`, id `password`, type `password`, maxlength attribute `"8"`; entered passwords visibly masked |
| User label / hint | `User ID     :` / `(8 Char)` |
| Password label / hint | `Password    :` / `(8 Char)` |
| ERRMSG | `""` |
| Footer | `ENTER=Sign-on  F3=Exit` (DOM contains exactly two spaces before F3) |
| Buttons | `Sign-on`, `Exit` |

All eight display fields are `OUTPUT` elements, not inputs; none contenteditable. The only inputs are USERID and PASSWD. `document.documentElement.outerHTML.includes('Invalid key pressed')` returned `false`. This is a loaded-DOM check, not a search through all downloaded JavaScript bundles. Header label colons wrap onto separate lines for Tran/Prog/Date/Time at this viewport; requested values remain visible.

## 2–12. Interactions

| Check | Result | Exact observed text and state |
|---|---|---|
| 2. Empty user + Enter | PASS | `Please enter User ID ...`; activeElement.id `userId`; no navigation. Password was also empty. |
| 3. USER0001 / empty password + Enter | PASS | `Please enter Password ...`; activeElement.id `password`. |
| 4. Both cleared + Enter | PASS | `Please enter User ID ...`; values `["",""]`; activeElement.id `userId`. User error wins. |
| 5. NOBODY01 / PASSWORD | PASS | `User not found. Try again ...`; activeElement.id `userId`. |
| 6. USER0002 / WRONGPWD | **FAIL** | Text matches `Wrong Password. Try again ...`, but activeElement.id is **`userId`**, expected **`password`**. Blue focus outline visibly surrounds User ID. |
| 7. lowercase user0002 / password | PASS | URL `http://localhost:4200/menu`; body innerText exactly `"CardDemo\nMain Menu\nNot yet available in this release."`; no user id/type displayed. |
| 8. ADMIN002 / PASSWORD | PASS | URL `http://localhost:4200/menu`; body innerText exactly `"CardDemo\nMain Menu\nNot yet available in this release."`; no user id/type displayed. |
| 9a. Click Exit | PASS | `Thank you for using CardDemo application...`; zero inputs; only button `Sign on again`; ERRMSG `""`; URL `/signon`. |
| 9b. Reload + F3 | PASS | `Thank you for using CardDemo application...`; zero inputs; only button `Sign on again`; ERRMSG `""`; URL `/signon`. |
| 9c. Reload + Escape | PASS | `Thank you for using CardDemo application...`; zero inputs; only button `Sign on again`; ERRMSG `""`; URL `/signon`. |
| 10a. F1 | PASS (application behavior) | ERRMSG `""`, forbidden literal absent, new app resource entries `[]`. Chrome opened its help tab; closed that tab and verified unchanged sign-on. |
| 10b. F7 | PASS (application behavior) | ERRMSG `""`, forbidden literal absent, new app resource entries `[]`. Chrome showed `Turn on caret browsing?`; clicked `Cancel`. |
| 10c. F12 | PASS (application behavior) | ERRMSG `""`, forbidden literal absent, new app resource entries `[]`. Chrome opened DevTools; closed it. |
| 10d. PageDown | PASS | ERRMSG `""`, forbidden literal absent, new app resource entries `[]`; sign-on unchanged. |
| 10e. F5 | SKIP (requested) | Not pressed; browser reload was excluded by the requested procedure. |
| 11. Nine characters per input | PASS | Typed `123456789` into each. Both DOM values `12345678`, length 8, maxLength 8; password type `password`; ERRMSG `""`. No length message. |
| 12. Evidence | PASS | Initial state, all five error cases, both menus, three thank-you states, keys and length-cap screenshots saved; continuous annotated MP4 and derived animated WEBP saved. |

No observed application URL contained `CC00`; it appeared only as display text on sign-on. Exit leaves the CardDemo toolbar, screen header (CC00 / AWS Mainframe Modernization / COSGN00C / CardDemo / CARDDEMO / CICS with date/time), and exact footer `ENTER=Sign-on  F3=Exit`; form, intro and banner disappear. Initial exit dates were `09/07/26`; times were `07:53:52` (button), `07:54:08` (F3), `07:54:24` (Escape).

For DV-03, inspected `performance.getEntriesByType('resource')` after marking a baseline: only `/api/auth/header` existed before keys, and each key added zero app resource entries. Browser-owned Help traffic is not an application request; no claim of zero browser-global traffic is made.

## Evidence paths

Absolute base: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/ui/`

- Recording: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/ui/cosgn00c_parity.webp`
- MP4: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/ui/cosgn00c_parity.mp4`
- Report: `/home/ubuntu/repos/ts-cobol-carddemo/parity/wave2/cosgn00c/ui/ui_report.md`
- Screenshots (append to absolute base): `01_initial.png`, `02_empty_user.png`, `03_empty_password.png`, `04_both_empty.png`, `05_user_not_found.png`, `06_wrong_password_focus_failure.png`, `07_user_menu.png`, `08_admin_menu.png`, `09_exit_button.png`, `09_f3.png`, `09_escape.png`, `10_f1.png`, `10_f7.png`, `10_f12.png`, `10_pagedown.png`, `11_length_cap.png`.

<details open>
<summary>Key visual evidence</summary>

| Wrong password — incorrect User ID focus | Empty password — correct Password focus |
|---|---|
| ![Wrong password focus failure](https://partner-workshops.devinenterprise.com/attachments/9637c7a1-d713-42dd-a4bf-81be75e15d74/06_wrong_password_focus_failure.png) | ![Empty password correct focus](https://partner-workshops.devinenterprise.com/attachments/b31cec18-56e8-4ae1-927a-1e0d4148f829/03_empty_password.png) |

</details>

<details>
<summary>Remaining screenshots</summary>

| Initial screen | Empty user |
|---|---|
| ![Initial field map](https://partner-workshops.devinenterprise.com/attachments/7af1c5e8-28a9-4cfc-9579-933f44fb1e69/01_initial.png) | ![Empty user](https://partner-workshops.devinenterprise.com/attachments/83fa4dca-94e0-4c22-9605-f85afababc6a/02_empty_user.png) |
| **Both empty** | **Unknown user** |
| ![Both empty](https://partner-workshops.devinenterprise.com/attachments/19c58b15-290f-4762-b5ca-80886fc6dff1/04_both_empty.png) | ![Unknown user](https://partner-workshops.devinenterprise.com/attachments/81da4644-5712-4d24-aad4-87ca4b3309c9/05_user_not_found.png) |
| **User menu** | **Admin menu** |
| ![User menu](https://partner-workshops.devinenterprise.com/attachments/9ccf324f-c81c-4f8d-aa75-f8d90861e838/07_user_menu.png) | ![Admin menu](https://partner-workshops.devinenterprise.com/attachments/9386edaa-02f1-4f81-b80a-805fab46e0fe/08_admin_menu.png) |
| **Exit button** | **F3 exit** |
| ![Exit button](https://partner-workshops.devinenterprise.com/attachments/bb416c66-5d72-4269-91ed-e7ec57c50446/09_exit_button.png) | ![F3 exit](https://partner-workshops.devinenterprise.com/attachments/43ce3e92-f726-4e9d-8163-2cdc90c73196/09_f3.png) |
| **Escape exit** | **Eight-character cap** |
| ![Escape exit](https://partner-workshops.devinenterprise.com/attachments/5f2fad42-0925-4c9f-abb5-f60e23b192d5/09_escape.png) | ![Length cap](https://partner-workshops.devinenterprise.com/attachments/5513b44d-45f1-4da3-aa58-ce1df30efaf5/11_length_cap.png) |
| **After F1** | **After F7** |
| ![F1](https://partner-workshops.devinenterprise.com/attachments/6c859d3b-5de2-4c90-93ed-fbbb89747a97/10_f1.png) | ![F7](https://partner-workshops.devinenterprise.com/attachments/9ffc8198-d72d-4ed2-b501-80fa630e745e/10_f7.png) |
| **After F12** | **After PageDown** |
| ![F12](https://partner-workshops.devinenterprise.com/attachments/c01064e6-1776-4de0-8da0-d11d1eed28e8/10_f12.png) | ![PageDown](https://partner-workshops.devinenterprise.com/attachments/3576ae0c-c373-4179-a8a5-887db1d9c4e0/10_pagedown.png) |

</details>

SKILL.md suggestions: none. Blueprint updates: none; existing blueprint was read, and no dependency installs/tool installs/service starts were performed. User action needed: none for executing tests; resolve or explicitly accept check 6 before claiming parity.
