# Project index

Compact map of the product/engineering blueprint. Do not copy the blueprint here.

## How to use this index

Load a section only when the current PR needs it. Status values are `implemented`, `pending`, or `deferred`.

| Blueprint section | Summary | Repository mapping | Status |
| --- | --- | --- | --- |
| Executive verdict | Churn scoring is a measurable ML problem, not an LLM classifier. Start narrow with strong tenant isolation. | none | pending |
| Product contract | B2B API and console for account-level churn risk, drivers, and workflow actions. Cold start uses transparent rules. | `docs/product/PRD.md` | implemented |
| Decisions to lock | Lock ICP, scored entity, horizon, capacity, region, data sensitivity, and pricing before model work. | ADR-001 locks layout and stores; PRD names defaults and `BLOCKED` items | pending |
| Recommended architecture | Modular monolith, PostgreSQL source of truth, durable async work, replaceable provider seams. Local SQS/S3 or LocalStack are approved. | `docs/architecture/context-map.md`; active `apps/` and `libs/` layout only | implemented |
| Tenant isolation and security | Strip client tenant headers; resolve tenant from a verified credential; tenant-scope every query and side effect. | hashed API keys and validated OIDC memberships both create TenantContext; PostgreSQL sessions, CSRF, and provider-neutral OIDC exist; broader RBAC is later | pending |
| External API and data contracts | Versioned OpenAPI first. Account upsert, batch events, churn definition, predictions, webhooks, usage. | `contracts/openapi/churn-api.yaml` defines minimal tenant account upsert, byte-bounded events:batch, and cursor-paginated current and historical prediction reads | pending |
| Churn scoring and ML lifecycle | Rules baseline first; learned models only after labels, time-split evaluation, and a release gate. | `libs/rules-scoring`; platform and worker adapters; `ml/` (missing) | pending |
| Reliability, webhooks, and operations | Transactional outbox, signed webhooks, SLOs, tenant-safe telemetry. | accepted-event transactional outbox and bounded worker DLQ redrive exist; webhooks, SLOs, and telemetry are later | pending |
| SaaS control plane | Onboarding, RBAC, entitlements, usage, billing, console, supportability. | tenant/user/membership tables, session foundation, and console shell exist; onboarding workflow, RBAC, entitlements, usage, and billing are later | pending |
| AI-assisted development rules | One bounded change, persistent context pack, human review of architecture and security. | `AGENTS.md`, `docs/agent/` | implemented |
| Repository and deployment shape | Intended layout is `apps/platform-service`, `apps/worker`, `apps/console`, contracts, docs, and tests. | approved app layout exists; unused scaffold and gateway layer are retired; production deployment is pending | pending |
| Milestone 0 — product contract | PRD, context map, data classification, ADR template, threat model. No application code. | PRD, context map, data classification, ADR template, and threat model exist; default churn label remains BLOCKED | pending |
| Milestone 1 — repo, local env, CI | Buildable modular monolith, Compose, Testcontainers, canonical verify, CI. | Testcontainers, Compose, `./scripts/verify.sh`, and GitHub Actions verify workflow exist | implemented |
| Milestone 2 — identity and tenancy | OIDC, API keys, immutable TenantContext, RBAC, audit, tenant-scoped schema. | hashed API keys, sessions, TenantContext, provider-neutral OIDC, and enabled membership resolution exist; broader RBAC, key lifecycle, and audit are later | pending |
| Milestone 3 — contract-first ingestion | OpenAPI plus idempotent `POST /v1/events:batch`. | OpenAPI defines account upsert and event ingestion; event ingest is implemented and account persistence remains next | pending |
| Milestone 4 — durable event processing | Tenant-scoped event persistence and replay. Local SQS/S3 or LocalStack approved by ADR-001. | Events persist with a transactional outbox; worker rejects invalid tenant tags and uses bounded DLQ redrive when a valid event remains unavailable | pending |
| Milestone 5 — features and rules score | Versioned features and cold-start rules scoring with history. | shared `RULES_BASELINE` current scores and append-only PostgreSQL history exist; feature registry is later | pending |
| Milestone 6 — prediction API and console | Tenant-safe prediction reads and Angular onboarding/risk console. | current/history prediction endpoints, cursor pagination, session/membership foundation, and console current/history views exist; login UI remains later | pending |
| Milestone 7 — webhook reliability | Outbox, signing, retry, DLQ, replay. | accepted-event delivery has an outbox and worker DLQ; webhook delivery is not implemented | pending |
| Milestone 8 — learned-model pipeline | Point-in-time Python training and gated promotion. | none | pending |
| Milestone 9 — optional explanations | Provider interface; Bedrock adapter deferred unless approved. | none | deferred |
| Milestone 10 — entitlements and billing | Plans, immutable usage ledger, billing adapter. | none | pending |
| Milestone 11 — observability and recovery | OpenTelemetry, runbooks, restore proof. Paid monitoring deferred. | none | pending |
| Milestone 12 — load test and launch | Capacity evidence and launch review. | none | pending |
| Verification matrix | Unit, architecture, integration, contract, tenant-negative, security, ML, e2e, load, recovery. | canonical tests cover PostgreSQL, sessions/CSRF, memberships, tenant isolation, ingestion/outbox, worker/DLQ, current/history scoring and reads, and console contracts; load and recovery remain later | pending |
| CI/CD release gates | Format, tests, migrations, OpenAPI diff, scans, staged deploy. | `.github/workflows/verify.yml` runs `./scripts/verify.sh`; release/deploy gates are not present | pending |
| Cost and scale controls | Measure cost per accepted event; avoid extra always-on gateway layers. | none | pending |
| Launch checklist | Product, security, ML honesty, reliability, and operations gates. | none | pending |
