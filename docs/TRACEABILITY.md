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
| InteractiveNavigationandMenuControl | `modernized/frontend` screens + `modernized/backend` REST endpoints (contract: `modernized/shared/openapi.yaml`) | — | Contract defined |
| CustomerandAccountDataManagement — sequential master readers/exports | `modernized/batch/src/jobs/exportMasters.ts` (`carddemo-batch export-masters`, labeled dumps of accounts/cards/xref/customers) | REQ-F-001–REQ-F-044 | Implemented (batch); online modules pending |
| TransactionProcessingandValidation | `modernized/batch/src/jobs/postTransactions.ts` + `src/lib/validation.ts` (`carddemo-batch post-transactions`; reject codes 100–103 per spec, 104–108 extensions; per-record atomic posting) | REQ-F-013–REQ-F-026, REQ-F-029–REQ-F-043 | Implemented |
| StatementandReportGeneration — interest | `modernized/batch/src/jobs/interestCalc.ts` + `src/lib/interest.ts` (`carddemo-batch interest-calc`; DEFAULT-group fallback, balance×rate/1200, cycle reset) | REQ-F-001–REQ-F-014, REQ-F-077–REQ-F-087, REQ-N-001 | Implemented |
| StatementandReportGeneration — statements | `modernized/batch/src/jobs/generateStatements.ts` + `src/lib/statementFormat.ts` (`carddemo-batch generate-statements`; text+HTML in `statements` table + files) | REQ-F-057–REQ-F-076 | Implemented |
| StatementandReportGeneration — transaction report | `modernized/batch/src/jobs/transactionReport.ts` + `src/lib/reportFormat.ts` (`carddemo-batch transaction-report`; date-range filter, page/account/grand totals, PENDING `job_runs` requests) | REQ-F-088–REQ-F-111 | Implemented |
| DataBackup,Archival,andIndexing | `modernized/batch/src/jobs/archiveData.ts` (`carddemo-batch archive-data`; generation-numbered JSON/CSV snapshots of transactions + category balances, retention limit default 5) | REQ-F-001–REQ-F-011 | Implemented (AIX/index REQ-F-012–REQ-F-023 superseded by RDBMS indexes) |
| Batch scheduling (Control-M) | `modernized/batch/src/scheduler.ts` (`carddemo-batch scheduler` / `run-pipeline`; node-cron, Job Dependencies ordering, `job_runs` audit rows, RC 0/12 semantics) | Job Dependencies sections | Implemented |
| FileAccessControlandDataCoordination | Superseded by transactional RDBMS (PostgreSQL + Prisma) | — | Superseded |
