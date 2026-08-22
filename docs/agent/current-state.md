# Current state

Last updated after PR-005.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith skeleton with JDBC, Flyway, and Testcontainers PostgreSQL tests. It has no MongoDB or Redis. Frozen legacy modules are unchanged. Milestone 0 docs now include the PRD, data classification, ADR template, and a STRIDE threat model. Identity and ingestion code do not exist.

- Branch: `cursor/pr-005-threat-model-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- ADR template: `docs/architecture/ADRs/ADR-template.md`
- Product contract: `docs/product/PRD.md`
- Data classification: `docs/security/data-classification.md`
- Threat model: `docs/security/threat-model.md`
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Frozen legacy modules: `common-models`, `core-service`, `api-gateway`
- Target modules: `/apps/platform-service` (skeleton), `/apps/worker` (not created), `/apps/console` (not created)
- Frontend: none
- Database migrations: `V1__platform_bootstrap.sql`
- CI: `.github/workflows/verify.yml` runs `./scripts/verify.sh`
- Canonical verify command: `./scripts/verify.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | ADR-001, PRD, data classification, ADR template, and threat model exist; OpenAPI does not |
| Backend | `platform-service` with Actuator, JDBC, and Flyway; legacy modules still empty scaffolds |
| Tests | context, `/actuator/health`, and PostgreSQL bootstrap query via Testcontainers |
| Persistence | Flyway `V1__platform_bootstrap.sql` in `platform-service`; legacy `core-service` still declares JPA/MongoDB |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | `docker-compose.yml` starts PostgreSQL only |
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

## What PR-005 added

- `docs/security/threat-model.md` STRIDE catalog for approved and planned surfaces
- Required scenarios: cross-tenant, forged tenant header, replay, webhook SSRF, resource exhaustion, prompt/data leakage, poisoned training data
- Residual risk stays Open or `BLOCKED`; acceptance is not recorded here

## What PR-004 added

- `docs/security/data-classification.md` and `docs/architecture/ADRs/ADR-template.md`

## Next session load list

1. `AGENTS.md`
2. this file
3. `docs/product/PRD.md`
4. `docs/security/data-classification.md`
5. `docs/security/threat-model.md`
6. `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
7. `docs/architecture/context-map.md`
8. `apps/platform-service` only, unless the task is about frozen modules
