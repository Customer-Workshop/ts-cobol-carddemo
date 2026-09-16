# S-01 UI verification

Verification only; no application or test changes. Local H2 server started with Java 21 and Maven; startup confirms 10 seeded users. Fixture credentials supplied by requester; no external secrets required.

## Source grounding
- `spring-boot/src/main/java/com/carddemo/ui/UiController.java:59-89`: sign-on GET/POST, errors, role redirect, farewell.
- `spring-boot/src/main/java/com/carddemo/ui/UiController.java:95-165`: menu routes, dispatch, signoff and invalid AID.
- `spring-boot/src/main/java/com/carddemo/service/MenuService.java:37-56,76-102,109-120`: catalogues, validation and concrete dispatch.
- `spring-boot/src/main/java/com/carddemo/security/SecurityConfig.java:43-76`: unsigned redirect and admin 403.
- `spring-boot/src/main/resources/templates/signon.html:20-53`, `menu.html:9-44`, `admin-menu.html:9-44`: fields, buttons and keyboard handlers.

All open questions resolved: routes and controls known, seed data available, server started. New HTML surface reuses existing services and must match the requested 3270 messages and menu behavior.

## Flow 1: sign-on and regular-user menu (scenarios 01-06)
1. Navigate to http://localhost:8080/signon. PASS iff green-on-dark 3270 screen shows Tran CC00, COSGN00C, date/time, User ID and Password inputs, ENTER=Sign-on and F3=Exit. Save s01-01-signon-render.png.
2. Click ENTER=Sign-on with blank fields. Expect "Please enter User ID ..."; save s01-02-blank-userid.png. Enter USER0001, leave password blank and click ENTER=Sign-on. Expect "Please enter Password ..."; save s01-02b-blank-passwd.png.
3. Replace ID with XXXX, password PASSWORD, click ENTER=Sign-on. Expect "User not found. Try again ..."; save s01-03-user-not-found.png. Replace with USER0001 / BADPASS and submit. Expect "Wrong Password. Try again ..."; save s01-04-wrong-password.png.
4. Replace with lowercase user0001 / password, submit. Expect /menu, Main Menu, 11 numbered options from Account View to Pending Authorization View, option 11 not installed, option input and ENTER=Continue/F3=Exit. Save s01-05-main-menu.png.
5. Enter 99 and click ENTER=Continue: expect "Please enter a valid option number..."; save s01-06-invalid-option.png. Replace with 11, submit: expect not-installed message naming Pending Authorization View; save s01-07-not-installed.png. Replace with 3, submit: expect /api/cards JSON; save s01-08-dispatch-cards.png. Navigate fresh to /menu.
6. Press keyboard F3. Expect /signon rather than menu; save s01-09-pf3-signoff.png.

## Flow 2: admin, keyboard and access controls (scenarios 07-10)
7. Submit ADMIN001 / PASSWORD. Expect /admin/menu, Admin Menu, six numbered options; save s01-10-admin-menu.png. Enter 1, click ENTER=Continue: expect /api/admin/users JSON; save s01-11-admin-dispatch.png. Navigate fresh to /admin/menu. Submit 7: expect "Please enter a valid option number..."; save s01-12-admin-invalid-option.png. Submit 5: expect not-installed message; save s01-13-admin-not-installed.png.
8. Press keyboard F5. Expect invalid-key message and preserved Admin Menu and option value; save s01-14-invalid-key.png.
9. F3 signoff, submit USER0001 / PASSWORD. Spot-check keyboard F5 on /menu: same invalid-key message and preserved Main Menu; save s01-14b-main-invalid-key.png. Navigate directly to /admin/menu: expect Forbidden/status=403, not admin menu; save s01-15-admin-denied.png. Navigate /menu and press F3. Direct navigate /menu again: expect redirect to /signon; save s01-16-unsigned-menu-redirect.png.
10. Press F3 on sign-on. Expect plain farewell "Thank you for using CardDemo application..."; save s01-17-farewell.png.

One continuous maximized-browser recording; annotate setup and each scenario, assert every checkpoint from visible pixels, save full uncropped screenshots. Differences are failures, not fixes. API dispatch content is fixture data; no writes beyond session authentication.
