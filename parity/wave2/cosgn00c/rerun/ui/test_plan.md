# COSGN00C PR #96 rerun

Checkout 445b4b9 incorporates PR head 60413a0. Existing Angular :4200 and backend :8080 with supplied PostgreSQL and fixture credentials; no installs/restarts. Browser access confirmed. No unresolved access questions.

Source trace retained from original run: app.routes.ts:7-12 exposes /signon and /menu; signon.component.html:1-89 maps outputs, input attributes, buttons and footer; signon.component.ts:90-165 handles Enter, normalization, exits and focus. Current diff changes focusAfterError around lines 159-172 to include `Wrong Password. Try again ...` in the password-focus branch. Other triggers remain unchanged.

Record one continuous browser run. Check 6 verifies the fix; other checks are explicitly requested regression coverage. Use native clicks/keys; DOM inspection only for exact strings, attributes, focus and resource timings.

1. Reload /signon. Expect noneditable OUTPUT fields CC00, AWS Mainframe Modernization, date mm/dd/yy, COSGN00C, CardDemo, time hh:mm:ss, CARDDEMO, CICS. Only two editable inputs; USERID maxLength 8/autofocus/active, PASSWD maxLength 8/type password. Labels `User ID     :`, `Password    :`, each `(8 Char)`. ERRMSG empty, exact footer `ENTER=Sign-on  F3=Exit`, Exit button present, `Invalid key pressed` absent in DOM.
2. Empty user + Enter: exactly `Please enter User ID ...`, userId focus, /signon unchanged.
3. USER0001 + empty password + Enter: `Please enter Password ...`, password focus.
4. Clear both + Enter: `Please enter User ID ...`, userId focus.
5. NOBODY01/PASSWORD + Enter: `User not found. Try again ...`, userId focus.
6. USER0002/WRONGPWD + Enter: `Wrong Password. Try again ...`, password focus (must not be userId).
7. Replace with lowercase user0002/password + Enter: /menu; capture exact rendered content and identity/type visibility. No application URL contains CC00.
8. Direct /signon; ADMIN002/PASSWORD + Enter: /menu; capture content.
9. Direct /signon, click Exit; reload and F3; reload and Escape. Each: `Thank you for using CardDemo application...`, no inputs; capture remaining elements.
10. Reload; press F1, F7, F12, PageDown individually. Expect ERRMSG empty, forbidden literal absent, no new application resource requests. Dismiss native browser Help/caret-browsing/DevTools UI. F5 explicitly excluded.
11. Type 123456789 into each field. Expect values 12345678, length/maxLength 8, password masked, ERRMSG empty.

Capture screenshots for initial map, every error, both menus, each exit, keys and length cap. Save annotated MP4 plus derived WEBP and exact-text PASS/FAIL report only in this rerun/ui directory.
