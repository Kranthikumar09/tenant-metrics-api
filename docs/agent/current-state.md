# Current state

Last updated after PR-0.

## Snapshot

The repository is a greenfield Maven reactor with documentation memory and an accepted MVP architecture ADR. No customer-facing churn behavior exists. Target `apps/` modules do not exist yet.

- Branch: `cursor/pr-0-adr-001-mvp-architecture-9d98`
- Draft PR: https://github.com/Kranthikumar09/tenant-metrics-api/pull/3
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Current modules: `common-models`, `core-service`, `api-gateway` (frozen placeholders)
- Target modules: `/apps/platform-service`, `/apps/worker`, `/apps/console` (not created)
- Frontend: none
- Database migrations: none
- CI: none
- Canonical verify command: not yet; use `./scripts/check-agent-docs.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | Agent memory and ADR-001 exist; PRD, threat model, and OpenAPI do not |
| Backend | Two empty Spring Boot apps and two shared records |
| Tests | Module `contextLoads` tests plus the agent-docs check |
| Persistence | `core-service` still declares JPA/PostgreSQL and MongoDB; no schema |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | none |
| Angular console | none |

## Known contradictions

Resolved by ADR-001:

1. Target layout is `/apps/platform-service`, `/apps/worker`, and `/apps/console`, not the current three-module split.
2. PostgreSQL is the primary store. Local SQS/S3 or LocalStack-compatible substitutes are approved. MongoDB is not approved. Redis is not approved unless a later ADR says so.

Still open:

1. `AGENTS.md` still forbids SQS, S3, and a separate worker. ADR-001 and the latest user instruction take precedence until a later docs PR updates `AGENTS.md`.
2. MongoDB and Redis remain installed/declared. Removal requires a later PR that lists exact files and is approved.
3. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
4. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.

## What PR-0 added

- `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
- `docs/architecture/context-map.md`
- Docs-check coverage for those files

## Next session load list

1. `AGENTS.md`
2. this file
3. `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
4. `docs/architecture/context-map.md`
5. the approved or proposed task specification
