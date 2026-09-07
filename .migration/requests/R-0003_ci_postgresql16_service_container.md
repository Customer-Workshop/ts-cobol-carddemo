Id: R-0003
Date: 2026-09-07
Requester: `!mf_stream_migration_plan` / https://partner-workshops.devinenterprise.com/sessions/7425b46a9daf4302a8ba5556dede4295
Team: Infrastructure
Stream: AccountView
Status: SENT (simulated)
Assumed answer: GitHub-hosted runners are available and may run service containers plus Docker-based Testcontainers, so `backend-ci.yml` declares a `postgres:16` service and integration tests run against it
Blocking: no

# Request — PostgreSQL 16 in CI (service container + Docker for Testcontainers)

## What we need

For the repository `Cognition-Partner-Workshops/ts-cobol-carddemo`, confirmation that CI runners
can provide:

1. A **`postgres:16` service container** for the backend workflow
   (`.github/workflows/backend-ci.yml`), reachable on `localhost:5432` inside the job, with a
   throwaway database and credentials supplied by the workflow.
2. A usable **Docker daemon in the job** so that Testcontainers-PostgreSQL integration tests can
   start their own container (target state D1 / decision D-0015 requires parity evidence from real
   PostgreSQL 16, never H2).
3. Whether GitHub-hosted runners are permitted, or whether a self-hosted runner pool must be used
   (and if so, which labels and whether it has Docker and outbound access to pull `postgres:16`).
4. Any image-registry policy: must `postgres:16` come from an internal mirror rather than Docker Hub?

## Why / when

Needed **before the first wave PR is merged**. Decision D-0015 makes real PostgreSQL 16 the only
acceptable source of parity evidence; the H2 profile exists solely so that `mvn verify` runs
without Docker on a developer box. If CI cannot run PostgreSQL 16, the CI gate degrades to unit
tests only and parity evidence has to be produced and attached manually from a developer box —
that would be a documented weakening of the gate, not an accepted default.

## Assumption in force

`backend-ci.yml` is written with a `services: postgres: image: postgres:16` block and with
Testcontainers enabled; the local runbook equivalent is the Docker container `carddemo-pg` on host
port 5433 (`.migration/07_runbook.md` §4, decision D-0008).

## Log

- 2026-09-07 `SENT (simulated)` by `!mf_stream_migration_plan`; proceeding on the assumption above.
