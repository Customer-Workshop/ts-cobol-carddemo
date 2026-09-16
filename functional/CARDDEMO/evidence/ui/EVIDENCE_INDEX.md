# CardDemo PostgreSQL browser evidence

Completed the browser procedure against the already-running PostgreSQL-profile deployment. **Not a clean sign-off**: see `BUGS.md`. No application fixes, restarts or commits.

## Artifact roots
- All filenames below resolve under `/home/ubuntu/repos/ts-cobol-carddemo/functional/CARDDEMO/evidence/ui/`.
- Recording: `/home/ubuntu/screencasts/carddemo-postgres-ui/carddemo-postgres-ui-edited.mp4`.
- Shareable archive of this directory: `/tmp/carddemo-ui-evidence.zip`.
- 72 PNG files were opened/verified as valid images. Primary populated account screenshots include the full document; the supplementary initial-entry account screenshot was taken before the explicit full-document capture helper was introduced.

## Per-stream verdicts and reports
| Stream | Verdict | Findings file |
|---|---|---|
| S01 signon/menus | Routing/auth pass; denial presentation divergence | S01_signon_findings.md |
| S02 account view | Display divergence | S02_account_view_findings.md |
| S03 account update | UI bug and fixture barrier; commit/persistence pass | S03_account_update_findings.md |
| S04 card list | U navigation bug; paging/S pass | S04_card_list_findings.md |
| S05 card view | Exercised flow pass | S05_card_view_findings.md |
| S06 card update | Selected-entry bug; manual-key save pass | S06_card_update_findings.md |
| S07 transaction list | Exercised flow pass | S07_transaction_list_findings.md |
| S08 transaction view | Clipped display bug | S08_transaction_view_findings.md |
| S09 transaction add | Exercised flow pass | S09_transaction_add_findings.md |
| S10 reports | Generated-total bug | S10_reports_findings.md |
| S11 bill payment | Exercised flow pass | S11_bill_payment_findings.md |
| S12 users | CRUD pass; denial presentation divergence | S12_users_findings.md |
| S19 pending auth | Navigation pass; blank status divergence | S19_pending_auth_findings.md |
| S21 transaction types | Add/delete pass; state/presentation divergences | S21_tran_types_findings.md |

Additional files: `TEST_PLAN.md`, `BUGS.md`, `13-TRANREPT-output.txt`, this index.

## Screenshot filenames
```text
01-admin-signoff-protected-redirect.png
01-signon-exit.png
01-signon-invalid.png
01-signon-wrong-password.png
01-signon.png
01-user-signoff.png
02-menu-invalid.png
02-menu-user.png
03-admin-denied-user.png
03-admin-menu-invalid.png
03-admin-menu.png
04-account-view-initial.png
04-account-view-persisted.png
04-account-view.png
05-account-update-before.png
05-account-update-confirm.png
05-account-update-saved.png
05-account-update-seed-phone-validation.png
05-account-update-seed-validation.png
06-card-list-page1.png
06-card-list-page2.png
07-card-view-persisted.png
07-card-view-selected.png
08-card-update-before.png
08-card-update-confirm.png
08-card-update-list-seam-bug.png
08-card-update-menu.png
08-card-update-saved.png
09-transaction-list-added.png
09-transaction-list-page1.png
09-transaction-list-page2.png
10-transaction-view-added.png
10-transaction-view-menu.png
10-transaction-view-selected.png
11-transaction-add-amount-validation.png
11-transaction-add-before.png
11-transaction-add-initial.png
11-transaction-add-saved.png
12-bill-payment-account-zero.png
12-bill-payment-before.png
12-bill-payment-saved.png
12-bill-payment-zero.png
12-users-denied-user.png
13-reports-before.png
13-reports-submitted.png
14-pending-auth-detail-before.png
14-pending-auth-fraud-marked.png
14-pending-auth-fraud-removed.png
14-pending-auth-list.png
14-pending-auth-next.png
14-pending-auth-page2.png
15-users-list-before.png
15-users-page2.png
16-user-add-before.png
16-user-add-saved.png
17-user-update-before.png
17-user-update-persisted.png
17-user-update-saved.png
18-user-delete-before.png
18-user-delete-saved.png
18-user-delete-verified.png
21-tran-types-denied-user.png
21-tran-types-list.png
21-tran-types-no-match-before.png
21-tran-types-persisted.png
22-tran-type-delete-before.png
22-tran-type-delete-verified.png
22-tran-type-deleted.png
22-tran-type-maint-before.png
22-tran-type-maint-confirm.png
22-tran-type-maint-menu-persisted.png
22-tran-type-maint-saved.png
```

## Mutations remaining in this demo
- Account1/customer1: first name Devin, FICO700, ZIP27501, phone2 `(919)693-8684`.
- Card `0500024453765740`: embossed name `DEVIN TEST CARD`.
- Added transaction `0000000996722788`, amount1.23, description `DEVIN UI SIGNOFF`.
- Bill payment `0000000996722789`, amount194.00; account1 balance now zero.
- Pending authorization fraud was marked then removed; journal history remains.
- Scratch user DEVTEST1 and type99 were created then deleted; fresh fetch verified absence.
- Browser ended signed out.

## Setup and coverage limits
Existing Java process/profile was inspected, not started or changed. Only extra dependency installed: `/usr/bin/python3 -m pip install --user websocket-client`, for screenshots via Chrome CDP. No app dependencies installed. Chrome maximized with wmctrl; no services started.

All main options1–11/admin1–6 and requested screen groups were exercised. Not-installed/coming-soon has no configured target. S21 list F10 flag mutation and actual multipage traversal were not exercised. Full validation matrices, concurrency/rollback fault injection and batch-only streams were not covered. Clean-DB boot failure is documented from handoff, not independently reproduced because restart was forbidden.
