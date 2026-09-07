# COMEN01C browser parity plan

Environment: head 7ca7906, Angular localhost:4200 proxying /api to real backend localhost:8080 and PostgreSQL. No mocks; no application source modifications.

Setup resolved: npm ci completed, ng serve ready, sign-on displays backend header. Seeded credentials supplied by lead; no additional access needed.

Code evidence: frontend/src/app/app.routes.ts:8-13 (guards); core/auth.guard.ts:11-21 (session check); features/signon/signon.component.ts:100-110 (landing route); features/menu/menu.component.ts:64-149 (load, role, select, normalized echo, exit and keys); menu.component.html:1-75 (map, disabled buttons, maxlength, ERRMSG); app/bms/COMEN01.bms:29-162 (map source). All navigation/state questions answered. Exit response text is discarded in implementation; evaluate the requested thank-you separately from routing/session invalidation.

1. Navigate directly to /menu unauthenticated: URL must become /signon. Submit USER0001/PASSWORD using sign-on form as the feature entry action: URL /menu.
2. Capture initial menu screenshot and DOM. Assert CM00, COMEN01C, AWS Mainframe Modernization, CardDemo, mm/dd/yy date and hh:mm:ss time; Main Menu; exactly the ordered 11 supplied option labels (Account View through Pending Authorization View), no twelfth row; prompt `Please select an option :`; OPTION maxlength=2; empty ERRMSG; footer `ENTER=Continue  F3=Exit`; no admin banner.
3. Click each disabled row 2..11: stay /menu, field/error unchanged. Click row 1: /accounts/view and Account View placeholder. Browser Back: /menu without reauthentication.
4. Submit each via Enter (use Continue for at least one). Empty, 0, A, 12, 99: exact ERRMSG `Please enter a valid option number...`, echoes 00, 00, 0A, 12, 99. Submit ` 1`, `01`, `1`: /accounts/view each; return via Back. Submit 2, 11: /menu, `Option not available in this release`, echoes 02, 11. Type 123 without submit: field must hold 12.
5. Clear option, focus menu; press F1, F5, F7, F12, PageDown, Tab individually. No application invalid-key state or `Invalid key` text; record browser interception/reload/focus movement separately.
6. Escape, F3, Exit button in separate authenticated sessions: /signon and session invalidated (direct /menu redirects). Expected thank-you `Thank you for using CardDemo application...`; record absence as mismatch, not silent pass.
7. Enter ADMIN001/PASSWORD: /menu, exact `Administration is not available in this release` banner, identical 11 rows, no `No access - Admin Only option`. Click row 1: /accounts/view.
8. Search initial user/admin DOM and visible menu text for `No access`, `is not installed`, `coming soon`, `Invalid key`: zero matches.

Evidence: annotated continuous browser recording, screenshots for initial user/admin menus, error/echo cases, destination and sign-off. Report table contains case/input/expected/observed/verdict with artifact paths. All final artifacts under this directory.
