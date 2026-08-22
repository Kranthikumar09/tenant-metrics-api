# Current state

Last updated after PR-004.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith skeleton with JDBC, Flyway, and Testcontainers PostgreSQL tests. It has no MongoDB or Redis. Frozen legacy modules are unchanged. The Milestone 0 product contract and data classification exist. The threat model does not.

- Branch: `cursor/pr-004-data-classification-adr-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- ADR template: `docs/architecture/ADRs/ADR-template.md`
- Product contract: `docs/product/PRD.md`
- Data classification: `docs/security/data-classification.md`
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
| Product docs | ADR-001, PRD, data classification, and ADR template exist; threat model and OpenAPI do not |
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

## What PR-004 added

- `docs/security/data-classification.md` with allowed classes, forbidden data, and `BLOCKED` retention/DPA items
- `docs/architecture/ADRs/ADR-template.md`
- Docs check requires both files and forbids invented PCI/HIPAA claims

## What PR-002 added

- `docs/product/PRD.md` with buyer, scored entity, horizon, v1 use cases, metrics, SLOs, and a traceability table
- Platform-wide default churn label and concrete leakage tests marked `BLOCKED`

## Next session load list

1. `AGENTS.md`
2. this file
3. `docs/product/PRD.md`
4. `docs/security/data-classification.md`
5. `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
6. `docs/architecture/context-map.md`
7. `apps/platform-service` only, unless the task is about frozen modules
