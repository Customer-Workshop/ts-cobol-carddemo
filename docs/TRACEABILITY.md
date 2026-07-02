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
| InteractiveNavigationandMenuControl — sign-on (COSGN00C) | `modernized/frontend/src/screens/SignOn.tsx` + `src/auth/AuthContext.tsx` (credential edits, uppercase, role routing, session/JWT, route guards) | REQ-F-375–REQ-F-388, REQ-F-352 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — main/admin menus (COMEN01C/COADM01C) | `modernized/frontend/src/screens/MainMenu.tsx`, `AdminMenu.tsx`, `src/components/MenuScreen.tsx` (numbered options, keyboard selection, invalid-option message, admin gating) | REQ-F-335–REQ-F-348, REQ-F-066–REQ-F-076 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — account view/update (COACTVWC/COACTUPC) | `modernized/frontend/src/screens/AccountView.tsx`, `AccountUpdate.tsx`, `src/validation.ts` (account search edits, change detection, field edits incl. dates/SSN/FICO/phone/state-zip/monetary, confirm-before-save) | REQ-F-010–REQ-F-065 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — card list/view/update (COCRDLIC/COCRDSLC/COCRDUPC) | `modernized/frontend/src/screens/CardList.tsx` (7/page, filters, S/U selection), `CardView.tsx`, `CardUpdate.tsx` (name/status/expiry edits, no-change detection, PF5 confirm) | REQ-F-125–REQ-F-160, REQ-F-200–REQ-F-232, REQ-F-300–REQ-F-334 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — transaction list/view/add (COTRN00C/COTRN01C/COTRN02C) | `modernized/frontend/src/screens/TransactionList.tsx`, `TransactionView.tsx`, `TransactionAdd.tsx` (pagination + boundary messages, detail lookup, add-form edits + Y confirmation + new tran id) | REQ-F-389–REQ-F-409, REQ-F-414–REQ-F-443, REQ-F-474–REQ-F-497 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — bill pay (COBIL00C) | `modernized/frontend/src/screens/BillPay.tsx` (balance display, nothing-to-pay, confirmed full-balance payment) | REQ-F-096–REQ-F-124 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — reports (CORPT00C) | `modernized/frontend/src/screens/Reports.tsx` (monthly/yearly/custom ranges, date edits, Y/N confirmation, submission) | REQ-F-356–REQ-F-370 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — user admin (COUSR00C–COUSR03C) | `modernized/frontend/src/screens/UserList.tsx`, `UserAdd.tsx`, `UserUpdate.tsx`, `UserDelete.tsx` (paginated list, required-field edits, duplicate handling, change detection, confirmed delete) | REQ-F-505–REQ-F-547, REQ-F-553–REQ-F-611 | Implemented (frontend, mock-backed) |
| InteractiveNavigationandMenuControl — REST endpoints | `modernized/backend` (contract: `modernized/shared/openapi.yaml`; frontend developed against MSW mocks of the same contract) | — | Contract defined |
| CustomerandAccountDataManagement | `modernized/backend` accounts/customers/cards modules + `modernized/batch` export jobs | — | Pending |
| TransactionProcessingandValidation | `modernized/batch` post-transactions job (staging: `daily_transactions`/`daily_rejects` tables) | — | Pending |
| StatementandReportGeneration | `modernized/batch` interest-calc / generate-statements / transaction-report jobs (`statements`/`reports` tables) | — | Pending |
| DataBackup,Archival,andIndexing | `modernized/batch` archival jobs (versioned `statements`/`reports` rows replace GDG) | — | Pending |
| FileAccessControlandDataCoordination | Superseded by transactional RDBMS (PostgreSQL + Prisma) | — | Superseded |
