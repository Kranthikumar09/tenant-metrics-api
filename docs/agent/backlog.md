# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-032R — Paginate account score history**

- Capacity: S
- Why next: current-risk pagination is complete, but account history still stops at its first 50 revisions even though the tenant/account-bound backend cursor already exists
- Main dependency: PR-030R
- In scope: retain the history `next_cursor`, request later revisions for the selected account through the same-origin session, append the page, and expose accessible loading/completion/safe-retry states
- Out of scope: current-list changes, backend API changes, infinite scrolling, filtering/sorting, learned-model training, customer-facing explanations, production deployment, Redis, MongoDB

## Later candidates

Select the next candidate after PR-032R using the product contract and remaining milestone risks; do not pre-commit to learned-model or deployment work.

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
| PR-031R | Paginate the current prediction list | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
