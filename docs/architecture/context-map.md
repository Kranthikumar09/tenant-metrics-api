# Current architecture context

Short map of what exists today versus the target locked by `ADR-001`. This is not a full system design. `AGENTS.md`, this file, and ADR-001 must agree: ADR-001 is the approved architecture contract.

## Current modules

`api-gateway`, `core-service`, and `common-models` are frozen legacy modules. New implementation must not build on the frozen modules.

| Module | Role today | Allowed to change in feature PRs? |
| --- | --- | --- |
| `common-models` | Shared DTO jar (`TenantDto`, `ApiResponse`) | Frozen legacy module. Do not treat as `apps/console`. |
| `core-service` | Empty Spring Boot web app with JPA, PostgreSQL driver, and MongoDB starters | Frozen legacy module. Do not treat as `apps/worker` or `platform-service`. |
| `api-gateway` | Empty Spring Cloud Gateway app | Frozen legacy module. Do not treat as the public monolith. |

There is no `/apps` directory, no Docker Compose, no Angular workspace, and no LocalStack configuration.

## Target modules

| Path | Role | Status |
| --- | --- | --- |
| `/apps/platform-service` | Spring Boot modular monolith | pending |
| `/apps/worker` | Same-version background processor | pending |
| `/apps/console` | Angular application | pending |

PostgreSQL is the only approved primary database. Local queues and object storage may use SQS/S3 or LocalStack-compatible substitutes. MongoDB is not approved. Redis is not approved unless a later ADR says so.

## Freeze

Do not delete, move, or strip dependencies from `api-gateway`, `core-service`, or `common-models` until a later PR lists the exact files and is approved. MongoDB and Redis removal from legacy modules requires separate approval.

New modules must not introduce MongoDB or Redis.

New work goes into the target `apps/` modules after those modules exist. New implementation must not build on the frozen modules.

## Dependency direction

Target modules should depend inward on domain contracts, not on controllers or cloud clients. Queue, storage, authentication, and explanation adapters stay behind interfaces.

Current modules do not yet implement that shape. They remain placeholders only.
