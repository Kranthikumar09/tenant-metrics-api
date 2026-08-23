# Current state

Last updated after PR-011 — Events batch.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith with JDBC, Flyway, Testcontainers PostgreSQL tests, hashed-key TenantContext, and in-memory `POST /v1/events:batch`. `/apps/worker` is a same-version non-web background process skeleton. Neither module has MongoDB or Redis. Frozen legacy modules are unchanged. Local Compose starts PostgreSQL and LocalStack SQS/S3.

- Branch: `cursor/pr-011-events-batch-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- Product contract: `docs/product/PRD.md`
- Threat model: `docs/security/threat-model.md`
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Frozen legacy modules: `common-models`, `core-service`, `api-gateway`
- Target modules: `/apps/platform-service` (skeleton), `/apps/worker` (skeleton), `/apps/console` (not created)
- Frontend: none
- Database migrations: `V1__platform_bootstrap.sql`
- CI: `.github/workflows/verify.yml` runs `./scripts/verify.sh`
- Canonical verify command: `./scripts/verify.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | ADR-001, PRD, data classification, ADR template, threat model, and events:batch OpenAPI exist |
| Backend | `platform-service` with Actuator, JDBC, Flyway, hashed API-key TenantContext, and in-memory event batch ingest; `worker` boots as a non-web process; legacy modules still empty scaffolds |
| Tests | platform-service context, health, PostgreSQL bootstrap, tenant-isolation, and event-batch; worker context-load and non-web assertion |
| Persistence | Flyway `V1__platform_bootstrap.sql` in `platform-service`; worker has no datastore |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | `docker-compose.yml` starts PostgreSQL and LocalStack SQS/S3 |
| CI | GitHub Actions runs `./scripts/verify.sh` with contents:read and no deploy credentials |
| Angular console | none |

## Known contradictions

Still open:

1. MongoDB and Redis remain installed/declared on frozen legacy modules. Removal requires a later PR that lists exact files and is approved.
2. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
3. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.
4. Foundation PRs were merged into stacked feature branches, not `origin/main`. `main` may still lack ADR-001 and later foundation work until that stack is merged there.
5. Blueprint suggested one AWS region; ADR-001 did not select AWS. Region remains `BLOCKED` in the PRD.
6. The M0 exit gate asked for a named churn label; the PRD still marks the default label `BLOCKED`.

## What PR-011 added

- `contracts/openapi/churn-api.yaml` for `POST /v1/events:batch`
- Tenant-bound ingest from `TenantContext`; client tenant claims are ignored
- In-memory idempotency receipts and per-tenant `event_id` dedup
- Batch size capped at 500; no PostgreSQL event table yet

## What PR-010 added

- Immutable `TenantContext` resolved from a SHA-256 hashed API key
- Client `X-Tenant-ID` / `tenant-id` headers are stripped before resolution
- `GET /v1/tenant-context` requires a verified key; forged headers cannot change tenant
- No production IdP

## What PR-009 added

- `/apps/worker` Spring Boot process with `spring.main.web-application-type=none`
- Enforcer ban on MongoDB and Redis
- No dependency on frozen legacy modules
- `./scripts/verify.sh` now tests and packages the worker

## What PR-007 added

- LocalStack SQS/S3 on Compose

## Next session load list

1. `AGENTS.md`
2. this file
3. `docs/product/PRD.md`
4. `docs/security/threat-model.md`
5. `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
6. `docs/architecture/context-map.md`
7. `apps/platform-service` and `apps/worker` only, unless the task is about frozen modules
