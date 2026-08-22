# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-3 — Add Docker Compose for local PostgreSQL**

- Capacity: XS
- Why next: local `spring-boot:run` still has no database
- Main dependency: PR-2
- Out of scope: Redis, MongoDB, LocalStack, frozen modules

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-002 | Create the Milestone 0 product contract (`docs/product/PRD.md`) | S | docs | Product defaults; mark unresolved items BLOCKED |
| PR-004 | Data classification and ADR template | XS | PR-002 | Remaining M0 product-pack item |
| PR-005 | Threat model | S | PR-004 | Remaining M0 exit gate |
| PR-006 | Canonical `./scripts/verify.sh` | S | PR-1 | Must not hide the pre-existing `core-service` failure |
| PR-007 | Local PostgreSQL and LocalStack-compatible Compose | S | ADR-001, PR-2 | Do not add Redis or MongoDB |
| PR-008 | GitHub Actions CI for the current checks | S | PR-006 | No cloud credentials |

## Completed

| ID | Title | Status |
| --- | --- | --- |
| PR-001 | Agent operating system and repository working memory | implemented |
| PR-0 | ADR-001 MVP architecture and current context map | implemented |
| PR-0.1 | Align `AGENTS.md` and architecture docs with ADR-001 | implemented |
| PR-1 | `/apps/platform-service` skeleton without MongoDB, Redis, or PostgreSQL | implemented |
| PR-2 | PostgreSQL JDBC, Flyway, and Testcontainers for platform-service | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
