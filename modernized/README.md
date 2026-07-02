# CardDemo — Modernized

Forward-engineered modernization of the legacy CardDemo COBOL/CICS/VSAM application.
See `../docs/ARCHITECTURE.md` for the target architecture and `../docs/spec/**` for
the AWS Transform generated requirements.

## Workspaces (npm workspaces)

| Package | Purpose |
|---------|---------|
| `shared` (`@carddemo/shared`) | Domain types/enums, zod validation helpers, `openapi.yaml` API contract |
| `backend` (`@carddemo/backend`) | NestJS REST API (later phase) + Prisma schema, migrations, seed |
| `batch` (`@carddemo/batch`) | Batch jobs — posting, interest, statements, reports (later phase) |
| `frontend` (`@carddemo/frontend`) | React SPA (later phase) |

## Prerequisites

- Node.js 20+
- Docker (for local PostgreSQL)

## Quick start

```bash
cd modernized
npm install

# 1. Start PostgreSQL 16 (db carddemo, user/password carddemo, port 5432)
npm run db:up            # = docker compose up -d postgres

# 2. Apply migrations (uses backend/.env — copy backend/.env.example to backend/.env)
cp backend/.env.example backend/.env
npm run db:migrate       # = prisma migrate dev in @carddemo/backend

# 3. Seed sample data (customers, accounts, cards, transactions, reference data,
#    pending daily batch, and users ADMIN0001/PASSWORD + USER0001/PASSWORD)
npm run db:seed          # = prisma db seed in @carddemo/backend
```

## Workspace script conventions

All packages expose the same scripts; run them from the workspace root:

```bash
npm run lint         # eslint across the monorepo
npm run typecheck    # tsc --noEmit in every workspace
npm run test         # vitest (shared validation helper unit tests)
npm run build        # tsc build in every workspace
```

CI (`.github/workflows/modernized-ci.yml`) runs all of the above plus
`prisma migrate deploy` + `prisma db seed` against a PostgreSQL 16 service container
on every PR/push touching `modernized/**`.

## Database

- Connection string: `postgresql://carddemo:carddemo@localhost:5432/carddemo?schema=public`
- Schema: `backend/prisma/schema.prisma` — see `../docs/spec/data-model.md` for the
  legacy data-store mapping and `../docs/TRACEABILITY.md` for requirement traceability.
- Monetary values are `Decimal(12,2)`; legacy fixed-width IDs are preserved as strings.
