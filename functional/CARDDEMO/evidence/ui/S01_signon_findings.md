# S01 — Sign-on and menus
Verdict: **pass for exercised authentication/routing; presentation divergence for denial**.

Compared with `S01_SignonMenu_functional_requirement.md`.
- Invalid user and wrong password produced the expected distinct messages. F3 from signon displayed farewell.
- Lowercase regular credentials authenticated to `/menu` (11 options); ADMIN001 authenticated to `/admin/menu` (six options).
- Every main option 1–11 and admin option 1–6 was submitted and reached its routed screen. Main 12/admin 7 returned invalid-option errors.
- Regular-user direct requests to admin menu, users and transaction types all denied 403. Whitelabel/JSON denial presentations diverge from the normal terminal UI (BUGS UI-05).
- Both roles signed off; a protected admin-users request after admin signoff redirected to `/signon`.
- Not-installed/coming-soon: **untested/unreachable configuration**; all options are installed, no DUMMY target. Exhaustive invalid keys, blank-input permutations and session-expiry timing not tested.

Evidence: `01-*.png`, `02-*.png`, `03-*.png`, `12-users-denied-user.png`, `21-tran-types-denied-user.png`; target screens substantiate numbered dispatch.
