# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-009 — `/apps/worker` same-version process skeleton**

- Capacity: S
- Why next: Local queues exist; the approved worker process does not
- Main dependency: ADR-001, PR-1
- Out of scope: microservice extraction, Redis, MongoDB, Bedrock, real AWS accounts

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-010 | Identity and immutable TenantContext | S | PR-005, PRD | Must implement forged-header and cross-tenant tests |

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
| PR-007 | LocalStack-compatible SQS/S3 on Compose | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
