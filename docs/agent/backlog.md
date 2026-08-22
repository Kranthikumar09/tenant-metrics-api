# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-002 — Create the Milestone 0 product contract (`docs/product/PRD.md`)**

- Capacity: S
- Why next: foundation CI is in place; product defaults and BLOCKED items are still missing
- Main dependency: docs / ADR-001
- Out of scope: application code, threat model, OpenAPI, scoring implementation

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-004 | Data classification and ADR template | XS | PR-002 | Remaining M0 product-pack item |
| PR-005 | Threat model | S | PR-004 | Remaining M0 exit gate |
| PR-007 | LocalStack-compatible SQS/S3 on Compose | S | ADR-001, PR-3 | Do not add Redis or MongoDB |
| PR-009 | `/apps/worker` same-version process skeleton | S | ADR-001, PR-1 | Not a microservice extraction |

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
| PR-5 | GitHub Actions CI for `./scripts/verify.sh` | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
