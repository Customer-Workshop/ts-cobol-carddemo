# COACTVWC browser parity

Setup resolved: Angular localhost:4200 proxies /api to supplied backend localhost:8080 with supplied PostgreSQL fixture. USER0001/PASSWORD successfully signed on to /menu. Browser maximized. No implementation files edited.

## Resolved questions / source grounding
- Navigation: frontend/src/app/app.routes.ts:8-13, core/auth.guard.ts:11-21; signon template and menu template option button choose route to account view.
- Lookup: features/account-view/account-view.component.html:22-43 Search submit; .ts:111-130 calls core/account.service.ts view; backend AccountController.java:35-49 handles blank/nonblank GET.
- Error / exit states: account-view.component.ts:133-171, F3/Escape handled on window. DOM sections remain present with blank outputs when no data.
- Oracle: app/bms/COACTVW.bms map fields, labels, money PICOUT and ERRMSG/INFOMSG; user explicitly requires full 10-character ZIP despite legacy map width 5.
- All credentials/access and state triggers resolved. Scope excludes all other menu options.

## One flow with required adversarial branches
1. Fresh browser cookies: type /accounts/view in address bar; expect /signon. Sign on with verified fixture identity as transition to test screen; click menu 01 Account View.
2. Entry: expect /accounts/view, CAVW, COACTVWC, View Account, actual date/time, exact `Enter or update id of account to display`; ACCTSID empty, maxlength 11; all account/customer output values empty, ERRMSG empty. Capture DOM+pixels.
3. Click Search blank: exact red `No input received`; expected ACCTSID `*` and error styling. Record actual value/styles.
4. Type `1234567890A`, Search; then `1234567890`, Search: exact red `Account Filter must  be a non-zero 11 digit number`, retain typed input.
5. Type `00000000099`, Search: starts `Account:00000000099 not found in Cross ref file.  Resp:` and contains ` Reas:`; output data blank.
6. Type `00000000901`, Search: starts `Account:00000000901 not found in Acct Master file.Resp:`; all account/customer values blank; report if labels/sections persist.
7. Type `00000000902`, Search: starts `CustId:999999902 not found in customer master.Resp: `; account Y, balance +194.00, limits +2020.00/+1020.00, dates 2014-11-20/2025-05-20/2025-05-20, zero cycle money, group A000000000, customer blank. Capture exact padded strings.
8. Type `00000000027`, Search: no error; capture all DOM labels/values, full ZIP 07923-8822, phone 1 (935)027-1145, all other names/dates/customer fields verbatim; check expected account values from request, customer 000000027, SSN 980-16-1210, FICO 078. Inspect money computed whitespace/font and visible gaps.
9. Focus input and press physical F5: expect unchanged populated form, no network requests/navigation/messages/Invalid key. Passive CDP logs and page timeOrigin detect reload.
10. From view click Exit; reenter via 01 Account View, focus input F3; reenter then Escape. Each /menu without re-sign-on. Passive network logs distinguish exit-specific API calls from destination guard/menu calls.
11. Report exact reading-order labels and every output; failure for any requested mismatch. Save ui-01..ui-NN PNG+JSON and short annotated recording.
