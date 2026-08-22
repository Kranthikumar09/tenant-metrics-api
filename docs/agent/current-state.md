# Current state

Last updated after PR-4.

## Snapshot

`/apps/platform-service` is a Java 21 Spring Boot 4.1.1 modular monolith skeleton with JDBC, Flyway, and Testcontainers PostgreSQL tests. It has no MongoDB or Redis. Frozen legacy modules are unchanged.

- Branch: `cursor/pr-4-verify-script-9d98`
- Architecture decision: `docs/architecture/ADRs/ADR-001-mvp-architecture.md` (Accepted)
- Context map: `docs/architecture/context-map.md`
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Frozen legacy modules: `common-models`, `core-service`, `api-gateway`
- Target modules: `/apps/platform-service` (skeleton), `/apps/worker` (not created), `/apps/console` (not created)
- Frontend: none
- Database migrations: `V1__platform_bootstrap.sql`
- CI: none
- Canonical verify command: `./scripts/verify.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | Agent memory and ADR-001 exist; PRD, threat model, and OpenAPI do not |
| Backend | `platform-service` with Actuator, JDBC, and Flyway; legacy modules still empty scaffolds |
| Tests | context, `/actuator/health`, and PostgreSQL bootstrap query via Testcontainers |
| Persistence | Flyway `V1__platform_bootstrap.sql` in `platform-service`; legacy `core-service` still declares JPA/MongoDB |
| Local environment | `.cursor/install.sh` and `start.sh` still start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | `docker-compose.yml` starts PostgreSQL only |
| Angular console | none |

## Known contradictions

Still open:

1. MongoDB and Redis remain installed/declared on frozen legacy modules. Removal requires a later PR that lists exact files and is approved.
2. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
3. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.
4. PR-0 and PR-0.1 were merged into stacked feature branches, not `origin/main`. `main` may still lack ADR-001 until that stack is merged there.

## What PR-2 added

- JDBC, Flyway, and the PostgreSQL driver on `platform-service`
- Testcontainers PostgreSQL tests
- One bootstrap migration
- MongoDB/Redis Enforcer rule kept

## What PR-1 added

- `/apps/platform-service` Spring Boot modular-monolith skeleton
- Actuator `/actuator/health`
- Maven Enforcer ban on MongoDB and Redis (direct and transitive)
- No JPA, JDBC, or PostgreSQL driver

## Next session load list

1. `AGENTS.md`
2. this file
3. `docs/architecture/ADRs/ADR-001-mvp-architecture.md`
4. `docs/architecture/context-map.md`
5. `apps/platform-service` only, unless the task is about frozen modules
