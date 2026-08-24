# Current architecture context

Short map of the current architecture locked by `ADR-001`. This is not a full system design. `AGENTS.md`, this file, and ADR-001 must agree: ADR-001 is the approved architecture contract.

## Current modules

| Path | Role | Status |
| --- | --- | --- |
| `/apps/platform-service` | Spring Boot modular monolith | tenant/session foundation, ingest, rules scoring, and prediction reads |
| `/apps/worker` | Same-version background processor | tenant-tagged event consumption, rescoring, retry, and DLQ |
| `/libs/rules-scoring` | Shared RULES_BASELINE domain jar | used by platform-service and worker |
| `/apps/console` | Angular application | onboarding and risk shell |

PostgreSQL is the only approved primary database. Local queues and object storage may use SQS/S3 or LocalStack-compatible substitutes. MongoDB is not approved. Redis is not approved unless a later ADR says so.

## Retired scaffold

PR-034R removed `api-gateway`, `core-service`, and `common-models` after the user approved their exact retirement. They were unused placeholders and no approved module depended on them. Their Spring Cloud, MongoDB, and Redis wiring was not part of the application.

Do not recreate those paths, add an extra gateway layer, or reintroduce MongoDB or Redis without a later ADR and explicit approval.

New work goes into the current `apps/` modules and inward-facing shared libraries under `libs/`.

## Dependency direction

Modules depend inward on domain contracts, not on controllers or cloud clients. Queue, storage, authentication, and explanation adapters stay behind interfaces.
