# Tenant Metrics API

Multi-Tenant Churn Intelligence SaaS for subscription businesses. The product identifies accounts likely to churn, explains measurable drivers, and feeds existing retention workflows.

This repository is in foundation setup. There is no customer-facing score or ingestion API yet.

## Current shape

The Maven reactor contains three placeholder modules from an earlier scaffold:

- `common-models`
- `core-service`
- `api-gateway`

The approved MVP direction is a Spring Boot modular monolith with PostgreSQL, not a microservice split and not MongoDB. That decision is recorded as open in `docs/agent/decisions-needed.md` and will be locked by an ADR before application behavior is added.

## Agent and product memory

Start here:

- `AGENTS.md` — operating model, MVP baseline, TDD, and stop conditions
- `docs/agent/current-state.md` — what exists now
- `docs/agent/project-index.md` — compact blueprint map
- `docs/agent/backlog.md` — next small PRs
- `docs/agent/decisions-needed.md` — unresolved conflicts

## Verification

Canonical verification:

```bash
./scripts/verify.sh
```

Local PostgreSQL:

```bash
docker compose up -d postgres
```

Then run `platform-service` with `--spring.profiles.active=local`.
