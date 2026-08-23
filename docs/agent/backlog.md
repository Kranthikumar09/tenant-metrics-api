# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-024R — Lock the console browser authentication and session contract**

- Capacity: XS
- Why next: The console cannot safely call prediction APIs until browser authentication is defined; API keys must not be stored in Angular
- Main dependency: PR-020
- Recommended decision: same-origin, server-managed session with Secure, HttpOnly cookies; document the contract before implementation
- Out of scope: authentication code, production IdP setup, console prediction fetch, Redis, MongoDB, production deployment

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-021 | Console lists current predictions | S | PR-024R | BLOCKED until browser authentication/session behavior is approved |
| PR-023 | Score history | S | PR-021 | Versioned score rows beyond current snapshot |

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
| PR-023R | Add a worker dead-letter queue and bounded redrive policy | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
