# Tenant Metrics API

Multi-Tenant Churn Intelligence SaaS for subscription businesses. The product identifies accounts likely to churn, explains measurable drivers, and feeds existing retention workflows.

The repository contains a working tenant-safe ingestion and rules-scoring spine, prediction read APIs, a background worker, and an Angular risk console. Learned-model training, production deployment, and several SaaS control-plane capabilities remain future work.

## Current shape

The approved application layout is:

- `/apps/platform-service` — Spring Boot modular monolith and HTTP API
- `/apps/worker` — same-version background queue processor
- `/apps/console` — Angular console
- `/libs/rules-scoring` — shared rules-baseline domain library

The abandoned `api-gateway`, `core-service`, and `common-models` scaffold has been retired. The MVP uses PostgreSQL plus LocalStack-compatible SQS/S3, not MongoDB, Redis, or an extra gateway service. Architecture is locked by ADR-001. The product contract is `docs/product/PRD.md`.

## Agent and product memory

Start here:

- `AGENTS.md` — operating model, MVP baseline, TDD, and stop conditions
- `docs/product/PRD.md` — Milestone 0 product contract and BLOCKED items
- `docs/security/data-classification.md` — allowed and forbidden data classes
- `docs/security/threat-model.md` — STRIDE catalog and required abuse scenarios
- `docs/architecture/ADRs/ADR-template.md` — format for later ADRs
- `docs/agent/current-state.md` — what exists now
- `docs/agent/project-index.md` — compact blueprint map
- `docs/agent/backlog.md` — next small PRs
- `docs/agent/decisions-needed.md` — unresolved conflicts

## Verification

Canonical verification:

```bash
./scripts/verify.sh
```

GitHub Actions runs the same script on every push and pull request. The workflow does not deploy and does not use cloud credentials.

Local PostgreSQL and LocalStack (SQS/S3 only):

```bash
docker compose up -d postgres localstack
```

Then run `platform-service` with `--spring.profiles.active=local`. LocalStack listens on `http://localhost:4566`. Do not put AWS keys in Compose; this stack is a local substitute only.
