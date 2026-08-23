# Current state

Last updated after PR-022R — transactional accepted-event outbox.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith with JDBC, Flyway, hashed-key TenantContext, tenant-scoped event persistence, a PostgreSQL transactional outbox for LocalStack SQS delivery, `RULES_BASELINE` scores, and cursor-paginated prediction reads. `/apps/worker` is a same-version non-web process that consumes tenant-tagged SQS messages, permanently rejects missing or mismatched tenant tags, and deletes a valid message only after its persisted event is visible and its tenant-scoped score refresh succeeds. Both call `/libs/rules-scoring` for the rules engine. `/apps/console` is an Angular onboarding and risk shell with no client tenant switch. Neither module has MongoDB or Redis. Frozen legacy modules are unchanged. Local Compose starts PostgreSQL and LocalStack SQS/S3.

- Branch: `cursor/pr-022r-transactional-outbox-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- Product contract: `docs/product/PRD.md`
- Threat model: `docs/security/threat-model.md`
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Frozen legacy modules: `common-models`, `core-service`, `api-gateway`
- Target modules: `/apps/platform-service` (skeleton), `/apps/worker` (skeleton), `/apps/console` (onboarding/risk shell)
- Frontend: Angular console skeleton
- Database migrations: `V1__platform_bootstrap.sql`, `V2__tenant_scoped_events.sql`, `V3__account_scores.sql`, `V4__accepted_event_outbox.sql`
- CI: `.github/workflows/verify.yml` runs `./scripts/verify.sh`
- Canonical verify command: `./scripts/verify.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | ADR-001, PRD, data classification, ADR template, threat model, events:batch, and cursor-paginated prediction-read OpenAPI exist |
| Backend | `platform-service` with Actuator, JDBC, Flyway, hashed API-key TenantContext, tenant-scoped event persistence, transactional outbox delivery to SQS, shared `RULES_BASELINE` scores, and cursor-paginated prediction reads; `worker` retains valid messages until their persisted event can be scored and permanently rejects invalid tenant tags |
| Tests | platform-service context, health, PostgreSQL bootstrap, tenant-isolation, event-batch, persistence, transactional-outbox enqueue, rules-score, prediction-read, and prediction-cursor; shared rules-scoring unit tests; worker context-load, consume, retry, successful-delete, and rescore tests; console onboarding/risk contract tests |
| Persistence | Flyway V1 bootstrap, V2 `ingested_events` / `ingest_receipts`, V3 `account_scores`, and V4 `accepted_event_outbox`; worker uses the same PostgreSQL store without owning Flyway |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | `docker-compose.yml` starts PostgreSQL and LocalStack SQS/S3 |
| CI | GitHub Actions runs `./scripts/verify.sh` with contents:read and no deploy credentials |
| Angular console | onboarding and risk routes; no prediction fetch yet |

## Known contradictions

Still open:

1. MongoDB and Redis remain installed/declared on frozen legacy modules. Removal requires a later PR that lists exact files and is approved.
2. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
3. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.
4. Blueprint suggested one AWS region; ADR-001 did not select AWS. Region remains `BLOCKED` in the PRD.
5. The M0 exit gate asked for a named churn label; the PRD still marks the default label `BLOCKED`.

## What PR-022R added

- Accepted events and their SQS delivery intent are committed together in PostgreSQL
- A scheduled dispatcher locks pending rows, publishes through the existing queue provider, and marks successful deliveries
- Idempotent replay creates one outbox row per tenant-owned event
- Delivery remains at-least-once; a crash after SQS accepts a message but before PostgreSQL marks it published can produce a duplicate

## What PR-021R added

- Valid tenant-tagged messages remain retryable when their PostgreSQL event is not yet visible
- A message is recorded as accepted and deleted only after its score refresh succeeds
- Missing or mismatched tenant tags remain permanent rejections; a missing `event_id` is reported as `missing_event`
- The ingestion publish path still lacks a transactional outbox; that is the next stabilization priority

## What PR-020 added

- `/apps/console` Angular shell with `/onboarding` and `/risk`
- No client-side tenant switch and no MongoDB or Redis
- `./scripts/verify.sh` runs console tests and `ng build`

## What PR-019 added

- `/libs/rules-scoring` holds the only `RulesBaselineScorer`
- platform-service and worker depend on that jar; they no longer copy the rules engine
- The shared jar is not a microservice and has no MongoDB or Redis

## What PR-018 added

- `GET /v1/predictions` pages with `limit` (default 50, max 500) and an opaque `cursor`
- `next_cursor` is present only when another page exists
- The cursor encodes the last `account_external_id` only; tenant stays on `TenantContext`
- Invalid cursor or `limit` returns 400 Problem Details

## What PR-017 added

- Worker refreshes `RULES_BASELINE` scores after an accepted tenant-tagged message
- Mismatched or missing tenant tags do not write scores
- Worker context-load tests still boot without PostgreSQL
- The rules engine is shared via `/libs/rules-scoring`

## What PR-016 added

- `GET /v1/accounts/{account_external_id}/prediction` and `GET /v1/predictions`
- Reads bind tenant from `TenantContext`; guessed IDs and forged headers cannot cross tenants
- `risk_probability` is omitted; `explanation_status` is `none`
- Cursor pagination is PR-018; the Angular console is later

## What PR-015 added

- Transparent `RULES_BASELINE` health score after each newly accepted event
- `risk_probability` is always null; no churn label and no learned model
- Scores are tenant-scoped by `TenantContext`; client tenant claims cannot retag them
- No prediction read API yet

## What PR-014 added

- Worker polls `accepted-events` and requires matching body and attribute `tenant_id`
- Missing or mismatched tenant tags are rejected and not processed
- No scoring yet

## What PR-013 added

- Accepted events are published to LocalStack SQS queue `accepted-events`
- Every message includes `tenant_id`; client tenant claims cannot retag it
- Replay does not enqueue a second message
- Worker does not consume yet

## What PR-012 added

- Flyway `V2__tenant_scoped_events.sql` with non-null `tenant_id` primary keys
- JDBC writes for events and idempotency receipts
- Tenant-negative persistence tests; no queue publish yet

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
7. `apps/platform-service` scoring and `apps/worker` scoring packages, unless the task is about frozen modules
