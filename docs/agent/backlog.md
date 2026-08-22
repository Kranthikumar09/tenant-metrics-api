# Backlog

Small PRs only. Do not implement the next item until it is approved.

## Next recommended PR

**PR-002 — Create the Milestone 0 product contract (`docs/product/PRD.md`)**

- Capacity: S
- Why next: Blueprint M0 requires a PRD before application code. The agent memory now exists, so the next durable artifact is the product contract.
- Main dependency: approved blueprint product-contract section
- Out of scope for PR-002: threat model, ADRs, application code, module collapse

## Later candidates

| ID | Title | Capacity | Depends on | Notes |
| --- | --- | --- | --- | --- |
| PR-003 | ADR-001 cost-optimized modular-monolith baseline | S | PR-002 | Must decide what to do with `api-gateway`, MongoDB, and Redis |
| PR-004 | Data classification and ADR template | XS | PR-002 | Remaining M0 product-pack item |
| PR-005 | Threat model | S | PR-004 | Remaining M0 exit gate |
| PR-006 | Canonical `./scripts/verify.sh` | S | PR-001 | Establish the verification pipeline before feature work |
| PR-007 | Local PostgreSQL Docker Compose | S | PR-003 | Do not add Redis or MongoDB |
| PR-008 | GitHub Actions CI for the current checks | S | PR-006 | No cloud credentials |

## Completed

| ID | Title | Status |
| --- | --- | --- |
| PR-001 | Agent operating system and repository working memory | implemented in this branch |

## Intentionally not scheduled

- Amazon SQS, S3, WAF, API Gateway, Bedrock, Redis, Kubernetes
- Terraform/CDK and multi-environment cloud accounts
- Learned-model training and customer-facing AI explanations
- Collapsing or rewriting the Maven modules before ADR-001
