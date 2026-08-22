# Project index

Compact map of the product/engineering blueprint. Do not copy the blueprint here.

## How to use this index

Load a section only when the current PR needs it. Status values are `implemented`, `pending`, or `deferred`.

| Blueprint section | Summary | Repository mapping | Status |
| --- | --- | --- | --- |
| Executive verdict | Churn scoring is a measurable ML problem, not an LLM classifier. Start narrow with strong tenant isolation. | none | pending |
| Product contract | B2B API and console for account-level churn risk, drivers, and workflow actions. Cold start uses transparent rules. | `docs/product/PRD.md` | implemented |
| Decisions to lock | Lock ICP, scored entity, horizon, capacity, region, data sensitivity, and pricing before model work. | ADR-001 locks layout and stores; PRD names defaults and `BLOCKED` items | pending |
| Recommended architecture | Modular monolith, PostgreSQL source of truth, durable async work, replaceable provider seams. Local SQS/S3 or LocalStack are approved. | `docs/architecture/context-map.md`; current modules remain frozen placeholders | implemented |
| Tenant isolation and security | Strip client tenant headers; resolve tenant from a verified credential; tenant-scope every query and side effect. | threat model exists; no auth module yet | pending |
| External API and data contracts | Versioned OpenAPI first. Account upsert, batch events, churn definition, predictions, webhooks, usage. | `contracts/` (missing) | pending |
| Churn scoring and ML lifecycle | Rules baseline first; learned models only after labels, time-split evaluation, and a release gate. | `ml/` (missing) | pending |
| Reliability, webhooks, and operations | Transactional outbox, signed webhooks, SLOs, tenant-safe telemetry. | no outbox/worker yet | pending |
| SaaS control plane | Onboarding, RBAC, entitlements, usage, billing, console, supportability. | no control-plane module yet | pending |
| AI-assisted development rules | One bounded change, persistent context pack, human review of architecture and security. | `AGENTS.md`, `docs/agent/` | implemented |
| Repository and deployment shape | Intended layout is `apps/platform-service`, `apps/worker`, `apps/console`, contracts, docs, and tests. | `apps/platform-service` skeleton exists; worker and console do not | pending |
| Milestone 0 — product contract | PRD, context map, data classification, ADR template, threat model. No application code. | PRD, context map, data classification, ADR template, and threat model exist; default churn label remains BLOCKED | pending |
| Milestone 1 — repo, local env, CI | Buildable modular monolith, Compose, Testcontainers, canonical verify, CI. | Testcontainers, Compose, `./scripts/verify.sh`, and GitHub Actions verify workflow exist | implemented |
| Milestone 2 — identity and tenancy | OIDC, API keys, immutable TenantContext, RBAC, audit, tenant-scoped schema. | none | pending |
| Milestone 3 — contract-first ingestion | OpenAPI plus idempotent `POST /v1/events:batch`. | none | pending |
| Milestone 4 — durable event processing | Tenant-scoped event persistence and replay. Local SQS/S3 or LocalStack approved by ADR-001. | LocalStack SQS/S3 in Compose; worker and persistence do not exist | pending |
| Milestone 5 — features and rules score | Versioned features and cold-start rules scoring with history. | none | pending |
| Milestone 6 — prediction API and console | Tenant-safe prediction reads and Angular onboarding/risk console. | no `apps/console` | pending |
| Milestone 7 — webhook reliability | Outbox, signing, retry, DLQ, replay. | none | pending |
| Milestone 8 — learned-model pipeline | Point-in-time Python training and gated promotion. | none | pending |
| Milestone 9 — optional explanations | Provider interface; Bedrock adapter deferred unless approved. | none | deferred |
| Milestone 10 — entitlements and billing | Plans, immutable usage ledger, billing adapter. | none | pending |
| Milestone 11 — observability and recovery | OpenTelemetry, runbooks, restore proof. Paid monitoring deferred. | none | pending |
| Milestone 12 — load test and launch | Capacity evidence and launch review. | none | pending |
| Verification matrix | Unit, architecture, integration, contract, tenant-negative, security, ML, e2e, load, recovery. | `scripts/check-agent-docs.sh` only | pending |
| CI/CD release gates | Format, tests, migrations, OpenAPI diff, scans, staged deploy. | `.github/workflows/verify.yml` runs `./scripts/verify.sh`; release/deploy gates are not present | pending |
| Cost and scale controls | Measure cost per accepted event; avoid extra always-on gateway layers. | none | pending |
| Launch checklist | Product, security, ML honesty, reliability, and operations gates. | none | pending |
