# CardDemo Modernization — Target Architecture

This document defines the reimagined, forward-engineered future state of the CardDemo
mainframe credit card management application, derived from the AWS Transform generated
requirement specifications in `docs/spec/`.

## Approach

Following the AWS Transform "reimagine" methodology, we forward-engineer from the
capability-level requirements (not a line-by-line COBOL translation). The legacy
CICS/BMS/VSAM/JCL stack is replaced by a modern cloud-ready full stack.

## Target Stack

| Legacy | Modern |
|--------|--------|
| COBOL/CICS online programs | Node.js 20 + TypeScript + NestJS REST API (`modernized/backend`) |
| BMS 3270 screens | React 18 + TypeScript + Vite SPA (`modernized/frontend`) |
| VSAM KSDS/AIX, GDG, QSAM | PostgreSQL 16 via Prisma ORM (`modernized/backend/prisma`) |
| JCL batch jobs + Control-M | TypeScript CLI batch jobs + node-cron scheduler (`modernized/batch`) |
| RACF / USRSEC | JWT auth with bcrypt-hashed users, role-based (USER/ADMIN) |
| EBCDIC flat files | JSON/CSV seed data + Prisma migrations & seeds |

## Monorepo Layout (npm workspaces)

```
modernized/
  package.json          # npm workspaces root, shared scripts (lint, typecheck, test, build)
  docker-compose.yml    # PostgreSQL 16 for local dev
  shared/               # @carddemo/shared — domain types, validation, OpenAPI contract
    openapi.yaml        # API contract (source of truth for backend & frontend)
  backend/              # @carddemo/backend — NestJS REST API + Prisma schema/migrations/seed
  batch/                # @carddemo/batch — batch jobs (posting, interest, statements, reports)
  frontend/             # @carddemo/frontend — React SPA
```

## Domain Model (from docs/spec/data-model.md)

Relational entities replacing the VSAM/GDG data stores:

- `users` (USRSEC) — id, firstName, lastName, password(bcrypt), role USER|ADMIN
- `customers` (CUSTDATA) — 9-digit id, names, address, phones, SSN, govt id, DOB, EFT account, primary-holder flag, FICO score
- `accounts` (ACCTDATA) — 11-digit id, activeStatus, currentBalance, creditLimit, cashCreditLimit, openDate, expirationDate, reissueDate, currCycleCredit, currCycleDebit, groupId
- `cards` (CARDDATA) — 16-char cardNumber, accountId, cvv, embossedName, expiryDate, activeStatus
- `card_xref` (CARDXREF) — cardNumber → customerId + accountId
- `transactions` (TRANSACT) — 16-char tranId, typeCode, categoryCode, source, description, amount, merchant id/name/city/zip, cardNumber, origTs, procTs
- `transaction_types` (TRANTYPE), `transaction_categories` (TRANCATG)
- `transaction_category_balances` (TCATBALF) — accountId + typeCode + categoryCode → balance
- `disclosure_groups` (DISCGRP) — accountGroupId + tranType + tranCat → interest rate
- `daily_transactions` staging + `daily_rejects` (DALYREJS) with reject reason
- `statements`, `reports` — generated artifacts (text + HTML), versioned (replaces GDG)
- `job_runs` — batch job execution/audit log (replaces Control-M history & GDG versioning)

Monetary values use `Decimal(12,2)`. Legacy fixed-width IDs are preserved as strings
with CHECK/length validation to maintain traceability to the spec.

## Capability → Component Mapping

| Spec capability | Component |
|-----------------|-----------|
| InteractiveNavigationandMenuControl | frontend screens + backend REST endpoints |
| Security,Validation,andApplicationSetup | backend auth module + user seed + date validation lib |
| CustomerandAccountDataManagement | backend accounts/customers/cards modules + batch read/export jobs |
| TransactionProcessingandValidation | batch `post-transactions` job (validation, rejects, posting, TCATBAL update) |
| StatementandReportGeneration | batch `interest-calc`, `generate-statements` (text+HTML), `transaction-report` jobs |
| DataStoreInitializationandLifecycle | Prisma migrations + seed scripts |
| DataBackup,Archival,andIndexing | batch archival jobs writing versioned artifacts (`statements`/`reports` tables + files) |
| FileAccessControlandDataCoordination | superseded by transactional RDBMS (documented in decision log) |

## Screens (replacing BMS maps)

Sign-on → Main Menu (user) / Admin Menu (admin) → Account View/Update, Card List/View/Update,
Transaction List/View/Add, Reports, Bill Pay, User Admin (list/add/update/delete).

## Requirement Traceability

Every implemented module references REQ IDs from `docs/spec/**/requirements.md` in code
comments (`// REQ-F-xxx`) and in `docs/TRACEABILITY.md`, mirroring the traceability.yaml
provided by AWS Transform.

## Delivery Model

Long-lived integration branch: `aws-transform`. All work lands via PRs into this branch.
