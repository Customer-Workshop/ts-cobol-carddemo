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
| CustomerandAccountDataManagement | `modernized/backend` accounts/customers/cards modules + `modernized/batch` export jobs | — | Pending |
| TransactionProcessingandValidation | `modernized/batch` post-transactions job (staging: `daily_transactions`/`daily_rejects` tables) | — | Pending |
| StatementandReportGeneration | `modernized/batch` interest-calc / generate-statements / transaction-report jobs (`statements`/`reports` tables) | — | Pending |
| DataBackup,Archival,andIndexing | `modernized/batch` archival jobs (versioned `statements`/`reports` rows replace GDG) | — | Pending |
| FileAccessControlandDataCoordination | Superseded by transactional RDBMS (PostgreSQL + Prisma) | — | Superseded |
