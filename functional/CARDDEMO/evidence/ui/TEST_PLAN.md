# PostgreSQL online screen sign-off plan

Environment: existing http://localhost:8080, branch devin/1789516557-carddemo-java-engagement. Do not restart, fix, or commit. Runtime process has SPRING_PROFILES_ACTIVE=postgres. Supplied ADMIN001 and USER0001 credentials suffice.

## Grounding and answered questions
- `spring-boot/src/main/java/com/carddemo/service/MenuService.java:37-56,109-128`: main options 1–11, admin 1–6 and concrete route map. All are installed; unavailable/coming-soon cannot be triggered by configured options.
- `spring-boot/src/main/java/com/carddemo/ui/UiController.java:144-220`: menu submission and transaction selection.
- `spring-boot/src/main/resources/templates/transaction-add.html:14-111`: transaction fields, ENTER, PF5 copy-last and Y confirmation.
- `spring-boot/src/main/resources/templates/reports.html:14-74`: monthly/yearly/custom and Y confirmation.
- `spring-boot/src/main/resources/templates/user-{list,add,update,delete}.html`: U/D selection, ENTER fetch/add and F5 save/delete.
- `spring-boot/src/main/java/com/carddemo/ui/TranTypeUiController.java:60-160`: list and stateful maintenance actions.
- Read stream requirements S01_SignonMenu, S02–S12, S19 and S21, especially screen fields, validation catalogues and acceptance criteria.
- Questions answered: menu navigation above; confirmation modes supplied by templates/controllers; expected legacy behavior specified by FRs; deployment reachable and no external access prerequisite.

## Browser assertions (record continuous session)
1. Sign-on: click ENTER with invalid user/password; expect exact missing-user/wrong-password messages. Click F3: farewell. Sign on regular user: `/menu`, 11 options. Sign off via F3: `/signon`; admin credentials: `/admin/menu`, six options. For regular user, navigate admin menu/users/tran-types: must deny access. Submit out-of-range menu option: valid-option error.
2. Dispatch every numbered main/admin option by typing option and clicking ENTER. Expect corresponding route/program, not a dead link or 500. Capture each.
3. Account view: enter `00000000001`, ENTER: account and customer populated. Update same account, capture original; change a text field to a valid distinct value, ENTER: `Changes validated.Press F5 to save`; F5: `Changes committed to database`. Reopen view: exact changed field persists. If seeded fields fail validation, record blocker and do not silently replace all data.
4. Cards: list displays seeded rows; F8 changes rows/page and F7 returns originals (or explicit edge message). Enter S in selection column and ENTER: detail for selected key. View via menu keys. Update selected card embossed name to `DEVIN TEST CARD`, ENTER then F5: committed; fresh view shows exact name.
5. Transactions: list, F8/F7 paging, S selection to view selected ID. On add, enter seeded card and valid type/category, source `DEVTEST`, description `DEVIN UI SIGNOFF`, amount `1.23`, ISO dates, merchant fields. Capture before; ENTER with Y confirmation: new transaction ID. Search list for returned ID and select: exact description and amount persist.
6. Bill payment: enter positive-balance seeded account, ENTER: original balance and confirm prompt. Capture before, Y then ENTER: payment ID; reopen account: balance zero; retry: `You have nothing to pay...`.
7. Reports: select monthly (S), confirm Y, ENTER. Expect submission confirmation and TRANREPT output/job evidence under target/carddemo-batch. Preserve actual success/failure rather than treating request acceptance as completion.
8. Pending auth: enter seeded account, ENTER: context/summary and up to five details. Select S/detail: exact selected authorization. If no seeded rows exist, label detail untested and report fixture gap.
9. Admin users: list original, add DEVTEST1 (Devin/Test, PASSWORD, U); expect added message. Fetch/update surname to Verified using F5; fresh fetch displays Verified. Delete scratch user using F5; fresh fetch says `User ID NOT found...`. Preserve seed users.
10. Tran-type admin: list/filter type; F2 add or admin option 6 maintenance. Use scratch type `99` if absent, description `DEVIN UI TEST`; ENTER/F5 per mode; reload filter must display saved value. Capture before and after. Delete scratch if reachable.

## Evidence and limitations
Save full-page PNGs by group prefix, per-stream findings, and BUGS.md here. Known clean-Postgres boot failures are reported from handoff, not independently reproduced because restart is prohibited: V2601 numeric/type mismatches, auth_date9c/auth_time9c naming mismatch, transaction_categories-before-types FK seeding failure.
No batch-only streams, concurrency/failure injection, or exhaustive validation permutations requested; do not imply these are proven.
