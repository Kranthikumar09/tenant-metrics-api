# Current state

Last updated for PR-029R — immutable tenant score history.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith with JDBC, Flyway, hashed-key TenantContext, PostgreSQL-backed Spring sessions, browser CSRF protection, a provider-neutral OIDC Authorization Code login adapter with S256 PKCE, server-side tenant membership resolution, tenant-scoped event persistence, a PostgreSQL transactional outbox for LocalStack SQS delivery, `RULES_BASELINE` scores, and cursor-paginated prediction reads. Validated OIDC issuer and subject resolve to one enabled PostgreSQL membership; missing, disabled, or ambiguous membership fails closed, and tenant claims remain non-authoritative. The adapter activates only when an OIDC client registration exists; no production provider or credentials are configured. `/apps/worker` is a same-version non-web process that consumes tenant-tagged SQS messages, permanently rejects missing or mismatched tenant tags, and deletes a valid message only after its persisted event is visible and its tenant-scoped score refresh succeeds. Valid messages that cannot be completed are bounded by an SQS redrive policy and retain their tenant tag in `accepted-events-dlq`. Both call `/libs/rules-scoring` for the rules engine. `/apps/console` is an Angular onboarding and risk shell whose risk route lists the first 50 current predictions through the same-origin opaque server session with loading, empty, error, and populated states. It has no client tenant switch and never reads or stores API keys, bearer/OIDC tokens, tenant headers, or the HttpOnly session cookie. Neither module has MongoDB or Redis. Frozen legacy modules are unchanged. Local Compose starts PostgreSQL and LocalStack SQS/S3.

Flyway V7 now retains every current-score insert/update as an append-only tenant-owned revision. The platform exposes newest-first cursor pages at `GET /v1/accounts/{account_external_id}/prediction-history`; the cursor carries no tenant or account authority.

- Branch: `cursor/pr-029r-score-history-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- Browser session decision: `docs/architecture/ADRs/ADR-002-console-browser-session.md` (Accepted)
- Product contract: `docs/product/PRD.md`
- Threat model: `docs/security/threat-model.md`
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Frozen legacy modules: `common-models`, `core-service`, `api-gateway`
- Target modules: `/apps/platform-service` (active modular monolith), `/apps/worker` (active queue consumer), `/apps/console` (onboarding/risk shell)
- Frontend: Angular console skeleton
- Database migrations: `V1__platform_bootstrap.sql`, `V2__tenant_scoped_events.sql`, `V3__account_scores.sql`, `V4__accepted_event_outbox.sql`, `V5__browser_sessions.sql`, `V6__tenant_memberships.sql`, `V7__account_score_history.sql`
- CI: `.github/workflows/verify.yml` runs `./scripts/verify.sh`
- Canonical verify command: `./scripts/verify.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | ADR-001, ADR-002, PRD, data classification, ADR template, threat model, events:batch, and cursor-paginated current/history prediction-read OpenAPI exist |
| Backend | `platform-service` with Actuator, JDBC, Flyway, PostgreSQL Spring Session, browser CSRF, conditional provider-neutral OIDC login, enabled-membership resolution, API-key/session TenantContext, tenant-scoped event persistence, transactional outbox delivery to SQS, shared `RULES_BASELINE` current/history scores, and cursor-paginated prediction reads; `worker` retains valid messages until their persisted event can be scored and permanently rejects invalid tenant tags |
| Tests | platform-service context, health, PostgreSQL bootstrap, browser-session cookie/persistence/CSRF, OIDC Authorization Code/state/nonce/PKCE contract, OIDC membership mapping and failure, membership resolution and deny-by-default routes, tenant-isolation, event-batch, persistence, transactional-outbox enqueue, rules-score, prediction-read, prediction-cursor, and immutable tenant-history tests; shared rules-scoring unit tests; worker context-load, consume, bounded DLQ redrive, successful-delete, and rescore tests; console onboarding/risk contract tests |
| Persistence | Flyway V1 bootstrap, V2 `ingested_events` / `ingest_receipts`, V3 `account_scores`, V4 `accepted_event_outbox`, V5 Spring Session tables, V6 tenants/users/memberships, and V7 append-only `account_score_history`; worker uses the same PostgreSQL store without owning Flyway |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | `docker-compose.yml` starts PostgreSQL and LocalStack SQS/S3 |
| CI | GitHub Actions runs `./scripts/verify.sh` with contents:read and no deploy credentials |
| Angular console | onboarding and risk routes; the risk route reads the first 50 current predictions through the same-origin session and renders loading, empty, safe error, retry, and semantic-table states; no login UI yet; API keys and OAuth tokens are forbidden from browser storage by ADR-002 |

## What PR-029R added

- Flyway V7 backfills current scores and appends every later `account_scores` insert/update to tenant-owned history, including writes from the platform and worker
- PostgreSQL rejects history updates, deletes, and truncation so recorded score revisions are append-only
- `GET /v1/accounts/{account_external_id}/prediction-history` returns newest-first pages with a bounded opaque cursor and derives tenant scope only from verified `TenantContext`
- Cross-tenant guessed account IDs return 404; invalid limits/cursors return Problem Details; each cursor is a random identifier resolved only inside the verified tenant/account scope
- Console history visualization, retention deletion, learned models, explanations, and infrastructure remain out of scope

## What PR-028R added

- The Angular risk route requests the first page of `/v1/predictions?limit=50` with same-origin credentials and no browser-readable authentication or tenant authority
- Loading, empty, safe error, retry, and populated semantic-table states cover the customer-visible prediction-list slice
- Runtime response validation rejects malformed prediction payloads instead of rendering untrusted shapes
- Pagination controls, score history, login UI, production IdP configuration, and backend changes remain out of scope

## What PR-027R added

- Spring Security OAuth2 Client support activates only when a provider-neutral client registration is supplied; no production IdP or credential was selected
- Login initiation uses Authorization Code, OIDC state and nonce, and an enforced S256 PKCE challenge stored with the server-side authorization request
- The OIDC user adapter maps only the framework-validated issuer and subject through `TenantMembershipResolver`; missing membership returns a generic authentication failure
- The tenant-aware OIDC principal uses the internal user UUID as its name while `TenantResolutionFilter` binds the verified membership tenant and ignores tenant claims, headers, and query parameters
- Invalid state returns JSON 401 without attempting a code exchange or redirecting to an HTML error page

## Known contradictions

Still open:

1. MongoDB and Redis remain installed/declared on frozen legacy modules. Removal requires a later PR that lists exact files and is approved.
2. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
3. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.
4. Blueprint suggested one AWS region; ADR-001 did not select AWS. Region remains `BLOCKED` in the PRD.
5. The M0 exit gate asked for a named churn label; the PRD still marks the default label `BLOCKED`.

## What PR-026R added

- PostgreSQL tenants, OIDC identities, and tenant memberships with enabled-state controls and referential integrity
- Exact issuer-plus-subject lookup resolves only one enabled user, tenant, and membership; zero or multiple results fail closed
- `TenantSessionPrincipal` can be created only through the package-private verified-membership factory and uses the internal user UUID as its principal name
- Browser-session tests now obtain principals through the real membership resolver instead of constructing tenant authority directly
- Spring Security explicitly allows health, future login/OAuth paths, and tenant-filtered `/v1/**` routes; every other route is denied

## What PR-025R added

- Spring Security and Spring Session JDBC persist opaque browser sessions in PostgreSQL without Redis
- The `__Host-tm_session` cookie is Secure, HttpOnly, SameSite=Lax, Path `/`, and has no Domain attribute; idle timeout defaults to 30 minutes
- Unsafe browser-session requests require the `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF contract and return JSON Problem Details on denial
- Server-created session principals and hashed machine API keys resolve to the same immutable `TenantContext`; forged tenant claims remain ignored
- Production OIDC, eight-hour absolute-session enforcement, lifecycle revocation, and Angular API calls remain later work

## What PR-024R added

- ADR-002 accepts a same-origin, provider-neutral OIDC Authorization Code with PKCE browser login contract
- Only an opaque `__Host-tm_session` cookie reaches the browser; session and tenant authority remain server-side in PostgreSQL
- Angular uses the `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF convention while the authentication cookie remains Secure and HttpOnly
- Browser sessions and machine API keys must resolve to the same immutable `TenantContext`; browser tenant claims are never trusted
- No Java, Angular, dependency, migration, IdP, or infrastructure implementation was added

## What PR-023R added

- Worker startup creates `accepted-events-dlq` before the source queue and attaches a native SQS redrive policy
- `platform.events.queue.max-receive-count` bounds delivery attempts at five by default and remains configurable
- LocalStack integration proves an unavailable persisted event moves to the DLQ and retains its `tenant_id` message attribute
- Replay tooling, production alerting, and customer-facing queue controls remain out of scope

## What PR-022R added

- Accepted events and their SQS delivery intent are committed together in PostgreSQL
- A scheduled dispatcher locks pending rows, publishes through the existing queue provider, and marks successful deliveries
- Idempotent replay creates one outbox row per tenant-owned event
- Delivery remains at-least-once; a crash after SQS accepts a message but before PostgreSQL marks it published can produce a duplicate

## What PR-021R added

- Valid tenant-tagged messages remain retryable when their PostgreSQL event is not yet visible
- A message is recorded as accepted and deleted only after its score refresh succeeds
- Missing or mismatched tenant tags remain permanent rejections; a missing `event_id` is reported as `missing_event`
- PR-022R closed the ingestion publish gap with the PostgreSQL transactional outbox

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
8. `docs/architecture/ADRs/ADR-002-console-browser-session.md` for browser authentication or console API work
