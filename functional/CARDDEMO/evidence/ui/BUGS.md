# PostgreSQL browser sign-off — defects and divergences

Run: 2026-09-16, existing `http://localhost:8080`, branch `devin/1789516557-carddemo-java-engagement`. Runtime Java process had `SPRING_PROFILES_ACTIVE=postgres`. No restart, application fixes, database workarounds, or commits were performed in this test run. All mutations used browser forms. Sign-off is **not clean**.

Evidence paths below are filenames relative to this directory. Full-page PNGs preserve the actual UI; none is a mock. Recording: `/home/ubuntu/screencasts/carddemo-postgres-ui/carddemo-postgres-ui-edited.mp4`.

## UI-01 — Card list U-selection loses zero padding (functional bug)
- Screen: S04 `/cards/list` → S06 `/cards/update`.
- Repro: regular user → main option 3 → enter `U` on card `0500024453765740`, account `00000000050` → ENTER.
- Expected: fetched, editable selected card (S06 FR-S06-28; S04 selection seam).
- Actual: URL contains `accountId=50`; account field is `50`, details blank, error `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`.
- Workaround used only to continue testing: manually enter `00000000050`, fetch, edit, ENTER/F5. This does not make the navigation assertion pass.
- Evidence: `06-card-list-page1.png`, `08-card-update-list-seam-bug.png`, `08-card-update-before.png`.

## UI-02 — Account customer-name layout hides/mislabels fields (UI bug)
- Screens: S02 account view and S03 account update.
- Repro: main options 1/2, fetch account `00000000001`, inspect customer-name row.
- Expected: first/middle/last name values legible and aligned with their labels (S02 FR-S02-07/14; S03 customer fields).
- Actual view: `Kessler` begins under the middle-name area, before the Last Name label. Actual update: middle name `Madeline` is displayed under Last Name; true last-name input/value `Kessler` is clipped from the visible row. DOM contains the surname but the full-page screenshot does not expose it.
- First-name edit still persisted after resolving fixture-validation problems.
- Evidence: `04-account-view.png`, `05-account-update-before.png`, `05-account-update-saved.png`.

## UI-03 — Transaction detail clips amount and dates (UI bug)
- Screen: S08 `/transactions/view`.
- Repro: main option 6, select any seeded transaction with `S`; also search/select newly added `0000000996722788`.
- Expected: readable amount and complete origin/processing dates, plus complete field labels (S08 display-field contract).
- Actual: Amount, Orig Date and Proc Date values are clipped to short prefixes; Type CD/Category labels also clip. New transaction DOM contains `+00000001.23` and `2026-09-16`, but these cannot be read in full on screen.
- Evidence: `10-transaction-view-selected.png`, `10-transaction-view-added.png`. List evidence `09-transaction-list-added.png` confirms correct stored amount independently of clipped detail.

## UI-04 — TRANREPT totals double the sole detail amount (functional bug)
- Screen/job: S10 `/reports` → TRANREPT.
- Repro in this run: add current-month transaction `0000000996722788`, amount `+00000001.23`; main option 9; select Monthly `S`, confirm `Y`, ENTER. This was done before bill payment.
- Expected: generated report for 2026-09-01 through 2026-09-30; totals equal sum of printed details (S10 report layout/totals contract).
- Actual: UI says `Monthly report submitted for printing ...`; generated file has exactly one transaction line for `1.23`, but both Page Total and Grand Total are `2.46`.
- Evidence: `13-reports-before.png`, `13-reports-submitted.png`, `13-TRANREPT-output.txt` (verbatim copy of `spring-boot/target/carddemo-batch/cbtrn03-report.txt` at generation time).
- The batch invocation/output-generation assertion passed; report arithmetic failed. Later payment means rerunning the same month now produces a different input set.

## DATA-01 — Seeded account fails update validation (fixture usability issue)
- Screen: S03 `/accounts/update`, account `00000000001`.
- Repro: fetch the seeded account, change only first name Immanuel → Devin, ENTER.
- Expected for an ordinary seeded happy path: validate the edit without first repairing unrelated fixture fields.
- Actual: original FICO `274` rejected with `FICO Score: should be between 300 and 850`; after correcting FICO and ZIP, original phone2 area `373` rejected with `Phone Number 2: Not valid North America general purpose area code`.
- These validations are consistent with the FRs; this is **not** a claim that the validation rules are wrong. NC/ZIP `12546` was also highlighted; it was changed to `27501` without isolating a standalone ZIP error.
- To exercise commit explicitly, also changed FICO to `700`, ZIP to `27501`, phone2 to `(919)693-8684`. Fresh view confirmed persistence. Seed fixture values have not been restored.
- Evidence: `05-account-update-before.png`, `05-account-update-seed-validation.png`, `05-account-update-seed-phone-validation.png`, `05-account-update-saved.png`, `04-account-view-persisted.png`.

## UI-05 — Admin denial leaves 3270 UI (presentation divergence, not bypass)
- Screens: `/admin/menu`, `/admin/users`, `/ui/tran-types`.
- Repro: sign in as regular USER0001, navigate directly to each URL.
- Expected: deny admin access; a consistent usable no-access presentation is desirable.
- Actual: all deny with HTTP 403, so security gating passed. Menu/users show Spring Whitelabel Error Page; transaction types show raw JSON with `No access - Admin Only option...`.
- S01's menu no-access behavior is not an exact specification of direct-URL rendering; classify this as presentation inconsistency, not proven security/parity failure.
- Evidence: `03-admin-denied-user.png`, `12-users-denied-user.png`, `21-tran-types-denied-user.png`.

## UI-06 — Pending authorization account status is blank (display divergence)
- Screen: S19 `/ui/pending-auth`.
- Repro: main option 11, fetch account `00000000001`.
- Expected: account/customer context including account status (FR-S19-02 and surface specification).
- Actual: `Acct Status:` has no value although account view shows Active `Y`.
- Evidence: `14-pending-auth-list.png`, `04-account-view.png`, `12-bill-payment-account-zero.png`.
- Pending summary balances are a distinct snapshot and were not assumed to equal account-master balances; no balance defect is asserted here.

## UI-07 — Transaction-type F2 opens search, not create state (FR divergence)
- Screen: S21 list `/ui/tran-types` → maintenance.
- Repro: admin option 5, no filters, click F2=Add.
- Expected: maintenance in create state (FR-S21-07).
- Actual: `Update Transaction Type`, blank code, description not editable, prompt `Enter transaction type to be maintained`. Entering absent `99` then ENTER yields `No record found for this key in database` and `Press F05 to add`; F5 is needed to enter new-details state.
- Add/validate/F5 commit then works, and fresh list/maintenance fetch confirms it. Scratch 99 was subsequently deleted and absence verified.
- Evidence: `22-tran-type-maint-before.png`, `22-tran-type-maint-confirm.png`, `22-tran-type-maint-saved.png`, `21-tran-types-persisted.png`.

## UI-08 — No-match transaction-type filter retains stale rows (presentation issue)
- Screen: S21 list.
- Repro: initial list types 01–07; type filter `99` (absent at that time); ENTER.
- Expected: no-match message; displayed results should not suggest 01–07 match 99.
- Actual: `No Records found for these filter conditions` appears, but old rows 01–07 remain visible (dimmed). F2 while in that no-match state did not navigate; clearing the filter allowed F2.
- FR-S21-03 specifies the no-match message, which is correct. It does not explicitly specify clearing old rows; the stale-row behavior is therefore recorded as a usability divergence, not an unequivocal storage/filtering defect.
- Evidence: `21-tran-types-no-match-before.png`.

## BOOT-01 — Clean postgres profile cannot boot without prior workarounds (reproduced firsthand)
Independently reproduced on 2026-09-16: a fresh empty `carddemo` database on PostgreSQL 14 fails to boot three times in a row under `SPRING_PROFILES_ACTIVE=postgres`. Each failure is fatal (`Application run failed` before Tomcat serves). These defects are invisible on the default H2 profile because Flyway is disabled there — they only surface on real PostgreSQL.

1. **Flyway V2601/JPA type mismatch.** `mvn spring-boot:run` dies in Hibernate schema validation: `wrong column type encountered in column [acct_id] in table [auth_frauds]; found [numeric (Types#NUMERIC)], but expecting [bigint (Types#BIGINT)]`. V2601 declares `auth_frauds.acct_id decimal(11,0)` and `cust_id decimal(9,0)` while `AuthFraud` maps `Long`→bigint; `pos_entry_mode smallint` versus entity `Integer` on **both** `auth_frauds` and `pending_auth_detail`.
2. **Column-name mismatch on `pending_auth_detail`.** After the type fixes, validation fails with `missing column [auth_date9c] in table [pending_auth_detail]`: V2601 creates `auth_date_9c`/`auth_time_9c` but implicit naming for entity fields `authDate9c`/`authTime9c` produces `auth_date9c`/`auth_time9c`. This also breaks every JPQL query against the entity at runtime, not just validation.
3. **DataSeeder violates `fk_trcat_type` (V2801).** With schema valid, seeding aborts: `insert or update on table "transaction_categories" violates foreign key constraint "fk_trcat_type" — Key (tran_type_code)=(01) is not present in table "transaction_types"`. `DataSeeder.run` calls `transactionCategoryRepository.saveAll(categories)` before `transactionTypeRepository.saveAll(types)` inside one transaction, and the flush reaches the FK-constrained child first. H2 never hits this because create-drop does not create the Flyway FK.

DB-level workarounds applied for this run (environment only; no code changes — fixes belong in migrations/seeder):
- `ALTER TABLE auth_frauds ALTER COLUMN acct_id TYPE bigint, ALTER COLUMN cust_id TYPE bigint;`
- `ALTER TABLE auth_frauds ALTER COLUMN pos_entry_mode TYPE integer;`
- `ALTER TABLE pending_auth_detail ALTER COLUMN pos_entry_mode TYPE integer;`
- `ALTER TABLE pending_auth_detail RENAME COLUMN auth_date_9c TO auth_date9c; ALTER TABLE pending_auth_detail RENAME COLUMN auth_time_9c TO auth_time9c;`
- `ALTER TABLE transaction_categories DROP CONSTRAINT fk_trcat_type;` → seed → `ALTER TABLE transaction_categories ADD CONSTRAINT fk_trcat_type FOREIGN KEY (tran_type_code) REFERENCES transaction_types (tran_type) ON DELETE RESTRICT;`

Repro: `CREATE USER carddemo / CREATE DATABASE carddemo` on a stock PostgreSQL, then `JAVA_HOME=<java21> SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run` in `spring-boot/` with unmodified migrations/entities. Screenshot: none (terminal output captured in session log).

## Sign-off limits
- All 11 main and 6 admin numbered routes and every requested online screen group were exercised.
- No installed option is configured as DUMMY/unavailable, so coming-soon/not-installed behavior could not be triggered without changing configuration.
- This was primary-flow/browser sign-off, not exhaustive FR validation, concurrency, database-failure injection, batch-only stream testing, or fresh-DB boot certification.
- S21 list F10 update/delete and multi-page traversal were not exercised; list had seven seed rows and F8 edge response was observed. Maintenance add/delete and persistence were exercised.
- No application code was fixed and no commits were created.
