# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-1 — Add an empty `/apps/platform-service` Maven module that boots without MongoDB or Redis**

- Capacity: S
- Why next: first independently buildable piece of the layout locked by ADR-001
- Main dependency: ADR-001
- Out of scope: deleting `api-gateway`, `core-service`, or `common-models`; removing Mongo/Redis from those modules; worker; console; LocalStack; Docker Compose

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-002 | Create the Milestone 0 product contract (`docs/product/PRD.md`) | S | PR-001 docs | Product defaults; mark unresolved items BLOCKED |
| PR-1a | Align `AGENTS.md` cost-optimized MVP section with ADR-001 | XS | PR-0 | Removes the stale forbid-list for local SQS/S3 and worker |
| PR-004 | Data classification and ADR template | XS | PR-002 | Remaining M0 product-pack item |
| PR-005 | Threat model | S | PR-004 | Remaining M0 exit gate |
| PR-006 | Canonical `./scripts/verify.sh` | S | PR-001 | Establish the verification pipeline before feature work |
| PR-007 | Local PostgreSQL and LocalStack-compatible Compose | S | ADR-001, PR-1 | Do not add Redis or MongoDB |
| PR-008 | GitHub Actions CI for the current checks | S | PR-006 | No cloud credentials |

## Completed

| ID | Title | Status |
| --- | --- | --- |
| PR-001 | Agent operating system and repository working memory | implemented |
| PR-0 | ADR-001 MVP architecture and current context map | implemented in this branch |

## Intentionally not scheduled

- Deleting or collapsing `api-gateway`, `core-service`, or `common-models` without an exact file list and approval
- Removing MongoDB or Redis without an exact file list and approval
- AWS WAF, API Gateway, Bedrock, Kubernetes, Terraform/CDK
- Learned-model training and customer-facing AI explanations
- Microservice extraction
