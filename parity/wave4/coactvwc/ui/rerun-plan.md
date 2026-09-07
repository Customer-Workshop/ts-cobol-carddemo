# Focused rerun: f16c141 merged at 584f7b7

## Setup and resolved questions
- Same verified UI route: sign-on USER0001/PASSWORD -> menu button 01 Account View.
- Sign-on completed against restarted backend; PostgreSQL unchanged.
- Starting ng serve found port occupied. Existing watcher PID 12249 serves the new `ficoScoreDisplay` / blank-star code, so reused it. No dependency installs or service restarts.
- Source grounding: account-view.component.ts:99-127 header/init and FICO; :200-209 blank-error handling; template :24-27 field-error/ARIA binding and :95 FICO output; AccountController.java header mapping `/view/header`. This replaces prior placeholder headers, blank error echo and unpadded FICO.

## One focused browser flow
1. Click menu `01. Account View`. Expect /accounts/view and initial header `CAVW`, `COACTVWC`, `AWS Mainframe Modernization`, `CardDemo`, date `09/07/26` and actual HH:mm:ss; exact `Enter or update id of account to display`, no error, blank input/data. Passive network must include GET /api/accounts/view/header, no account-ID lookup before Search. Save rerun-01.
2. Click Search with empty field. Expect red `No input received`, input value `*`, red text (`rgb(198, 40, 40)`), error styling, focused ACCTSID. Save rerun-02. Select input value, type `00000000027`, Search.
3. Expect no error, `FICO Score:` value `078`. Save rerun-03, re-extract all labels/values and compare against ui-09 JSON excluding changed header clock and expected FICO padding. Full ZIP/phones and all money strings remain unchanged.
4. Regression: replace input with `1234567890A`, Search -> exact `Account Filter must  be a non-zero 11 digit number`, echoed field. Save rerun-04.
5. Regression: replace with `00000000901`, Search -> exact `Account:00000000901 not found in Acct Master file.Resp:0000000013 Reas:0000`; all data values blank (labels may remain). Save rerun-05.
6. Regression: replace with `00000000902`, Search -> exact `CustId:999999902 not found in customer master.Resp: 0000000013 REAS:0000000`; account values identical to ui-08, customer values blank. Save rerun-06.
7. Regression: click Exit -> /menu still signed in. Save rerun-07.

Scope excludes F5, additional exit keys, other menu options, and other previous tests.
