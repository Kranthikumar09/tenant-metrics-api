# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-042R — Lock registered-account ingestion semantics**

- Capacity: XS
- Why next: PR-041R makes tenant accounts real, but event ingestion still accepts an arbitrary account ID and the PRD says a valid account must exist before its score can be retrieved; mixed-batch and unknown-account behavior must be explicit before runtime enforcement
- Main dependency: PR-041R, the existing `events:batch` accepted/rejected response, and the PRD account-upsert traceability gate
- In scope: lock in OpenAPI that an event for an account not registered in the verified tenant is an item-level rejection, a mixed batch still accepts valid registered-account events, another tenant's same external ID grants no authority, and idempotent replay returns the original counts; add contract guardrails and update repository memory
- Out of scope: Java or SQL changes, enforcing the rule at runtime, changing account upsert, rejecting an entire mixed batch, event/score foreign keys, account deletion, console onboarding, usage metering, webhooks, Redis, MongoDB, or new infrastructure

## Later candidates

Select the next candidate after PR-042R using the product contract and remaining milestone risks; do not pre-commit to learned-model or deployment work.

## Completed

| ID | Title | Status |
| --- | --- | --- |
| PR-001 | Agent operating system and repository working memory | implemented |
| PR-0 | ADR-001 MVP architecture and current context map | implemented |
| PR-0.1 | Align `AGENTS.md` and architecture docs with ADR-001 | implemented |
| PR-1 | `/apps/platform-service` skeleton without MongoDB, Redis, or PostgreSQL | implemented |
| PR-2 | PostgreSQL JDBC, Flyway, and Testcontainers for platform-service | implemented |
| PR-3 | Docker Compose for local PostgreSQL only | implemented |
| PR-4 | Canonical `./scripts/verify.sh` | implemented |
| PR-5 | GitHub Actions CI for `./scripts/verify.sh` | implemented |
| PR-002 | Milestone 0 product contract (`docs/product/PRD.md`) | implemented |
| PR-004 | Data classification and ADR template | implemented |
| PR-005 | STRIDE threat model | implemented |
| PR-007 | LocalStack-compatible SQS/S3 on Compose | implemented |
| PR-009 | `/apps/worker` same-version process skeleton | implemented |
| PR-010 | Identity and immutable TenantContext | implemented |
| PR-011 | Contract-first OpenAPI and `POST /v1/events:batch` | implemented |
| PR-012 | Persist tenant-scoped events | implemented |
| PR-013 | Enqueue accepted events for the worker | implemented |
| PR-014 | Worker consumes tenant-tagged event messages | implemented |
| PR-015 | Rules-based cold-start score | implemented |
| PR-016 | Predictions read API | implemented |
| PR-017 | Worker rescoring from tenant-tagged messages | implemented |
| PR-018 | Prediction list cursor pagination | implemented |
| PR-019 | Extract shared rules scorer | implemented |
| PR-020 | Angular console skeleton | implemented |
| PR-021R | Do not acknowledge worker messages when persisted event is unavailable | implemented |
| PR-022R | Persist event enqueue through a transactional outbox | implemented |
| PR-023R | Add a worker dead-letter queue and bounded redrive policy | implemented |
| PR-024R | Lock the console browser authentication and session contract | implemented |
| PR-025R | Add the PostgreSQL-backed browser session and CSRF foundation | implemented |
| PR-026R | Add server-side tenant membership resolution | implemented |
| PR-027R | Add a provider-neutral OIDC login adapter | implemented |
| PR-028R | Console lists current predictions | implemented |
| PR-029R | Add immutable tenant score history | implemented |
| PR-030R | Console shows account score history | implemented |
| PR-031R | Paginate the current prediction list | implemented |
| PR-032R | Paginate account score history | implemented |
| PR-033R | Add the same-origin Angular development proxy | implemented |
| PR-034R | Retire the legacy scaffold and align local tooling | implemented |
| PR-035R | Enforce the absolute browser-session lifetime | implemented |
| PR-036R | Add CSRF-protected browser logout | implemented |
| PR-037R | Revoke browser sessions when membership access is disabled | implemented |
| PR-038R | Add the Angular browser logout control | implemented |
| PR-039R | Enforce a bounded JSON body for event ingestion | implemented |
| PR-040R | Lock the tenant account-upsert API contract | implemented |
| PR-041R | Implement tenant-scoped account upsert | implemented in this branch |

## Intentionally not scheduled

- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
