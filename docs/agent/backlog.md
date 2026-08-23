# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-018 — Prediction list cursor pagination**

- Capacity: S
- Why next: Prediction list is capped at 500 with no stable cursor
- Main dependency: PR-017
- Out of scope: learned models, webhooks, Redis, MongoDB, production IdP

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-019 | Extract shared rules scorer | S | PR-018 | Remove worker/platform duplication |

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
| PR-017 | Worker rescoring from tenant-tagged messages | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
