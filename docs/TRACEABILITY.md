# Requirement Traceability

Maps AWS Transform capabilities (`docs/spec/**`) to modernized components and the
REQ IDs they implement. Populated incrementally as components land; unimplemented
capabilities list their target component.

| Capability | Component | REQ IDs | Status |
|-----------|-----------|---------|--------|
| DataStoreInitializationandLifecycle | `modernized/backend/prisma/schema.prisma` + migrations + `seed.ts` (delete-then-populate, idempotent) | REQ-F-001–REQ-F-018, REQ-N-001 | Implemented |
| Security,Validation,andApplicationSetup — user security credential initialization | `modernized/backend/prisma/seed.ts` (users ADMIN0001/USER0001, bcrypt) | REQ-F-023–REQ-F-026, REQ-N-001 | Implemented |
| Security,Validation,andApplicationSetup — date validation service | `modernized/shared/src/validation.ts` (`validateDate` diagnostic result) | REQ-F-019–REQ-F-022 | Implemented |
| Security,Validation,andApplicationSetup — application/program registration | Superseded: CICS resource registration (REQ-F-001–REQ-F-018) replaced by REST routing in `modernized/backend` | REQ-F-001–REQ-F-018 | Superseded |
| InteractiveNavigationandMenuControl — sign-on (COSGN00C) | `modernized/backend/src/auth` (JWT signin, bearer guard, role guard) | REQ-F-082–REQ-F-092, REQ-F-375–REQ-F-388 | Implemented |
| InteractiveNavigationandMenuControl — account view/update (COACTVWC/COACTUPC) | `modernized/backend/src/accounts` (combined account+customer GET/PUT, field validation, change detection, atomic write) | REQ-F-005–REQ-F-081, REQ-N-001 | Implemented |
| InteractiveNavigationandMenuControl — bill payment (COBIL00C) | `modernized/backend/src/billpay` (full-balance payment, payment transaction, atomic balance update) | REQ-F-150–REQ-F-167 | Implemented |
| InteractiveNavigationandMenuControl — card list/view/update (COCRDLIC/COCRDSLC/COCRDUPC) | `modernized/backend/src/cards` (paginated list with account filter, get by number, validated update) | REQ-F-173–REQ-F-231, REQ-F-251–REQ-F-257 | Implemented |
| InteractiveNavigationandMenuControl — report submission (CORPT00C) | `modernized/backend/src/reports` (report list; POST records a PENDING `job_runs` row for the batch package) | REQ-F-356–REQ-F-370 | Implemented (execution by `modernized/batch` transaction-report) |
| InteractiveNavigationandMenuControl — transaction list/view/add (COTRN00C/COTRN01C/COTRN02C) | `modernized/backend/src/transactions` (paginated list with filters, get by id, validated create with xref check and id generation) | REQ-F-389–REQ-F-420, REQ-F-474–REQ-F-494 | Implemented |
| InteractiveNavigationandMenuControl — admin user management (COUSR00C–COUSR03C) | `modernized/backend/src/users` (admin-only CRUD, duplicate detection, change detection) | REQ-F-505–REQ-F-547, REQ-F-553–REQ-F-611 | Implemented |
| InteractiveNavigationandMenuControl — screens/navigation | `modernized/frontend` screens (contract: `modernized/shared/openapi.yaml`) | — | Contract defined |
| CustomerandAccountDataManagement — online view/update | `modernized/backend` accounts/cards modules | — | Implemented |
| CustomerandAccountDataManagement — sequential master readers/exports | `modernized/batch/src/jobs/exportMasters.ts` (`carddemo-batch export-masters`, labeled dumps of accounts/cards/xref/customers) | REQ-F-001–REQ-F-044 | Implemented |
| TransactionProcessingandValidation | `modernized/batch/src/jobs/postTransactions.ts` + `src/lib/validation.ts` (`carddemo-batch post-transactions`; reject codes 100–103 per spec, 104–108 extensions; per-record atomic posting) | REQ-F-013–REQ-F-026, REQ-F-029–REQ-F-043 | Implemented |
| StatementandReportGeneration — interest | `modernized/batch/src/jobs/interestCalc.ts` + `src/lib/interest.ts` (`carddemo-batch interest-calc`; DEFAULT-group fallback, balance×rate/1200, cycle reset) | REQ-F-001–REQ-F-014, REQ-F-077–REQ-F-087, REQ-N-001 | Implemented |
| StatementandReportGeneration — statements | `modernized/batch/src/jobs/generateStatements.ts` + `src/lib/statementFormat.ts` (`carddemo-batch generate-statements`; text+HTML in `statements` table + files) | REQ-F-057–REQ-F-076 | Implemented |
| StatementandReportGeneration — transaction report | `modernized/batch/src/jobs/transactionReport.ts` + `src/lib/reportFormat.ts` (`carddemo-batch transaction-report`; date-range filter, page/account/grand totals, PENDING `job_runs` requests) | REQ-F-088–REQ-F-111 | Implemented |
| DataBackup,Archival,andIndexing | `modernized/batch/src/jobs/archiveData.ts` (`carddemo-batch archive-data`; generation-numbered JSON/CSV snapshots of transactions + category balances, retention limit default 5) | REQ-F-001–REQ-F-011 | Implemented (AIX/index REQ-F-012–REQ-F-023 superseded by RDBMS indexes) |
| Batch scheduling (Control-M) | `modernized/batch/src/scheduler.ts` (`carddemo-batch scheduler` / `run-pipeline`; node-cron, Job Dependencies ordering, `job_runs` audit rows, RC 0/12 semantics) | Job Dependencies sections | Implemented |
| FileAccessControlandDataCoordination | Superseded by transactional RDBMS (PostgreSQL + Prisma) | — | Superseded |
