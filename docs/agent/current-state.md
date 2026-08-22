# Current state

Last updated after PR-001.

## Snapshot

The repository is a greenfield Maven reactor with documentation memory for agent sessions. No customer-facing churn behavior exists.

- Branch baseline: `main` at the PR-001 starting commit
- Language: Java 21
- Build: Maven wrapper, Spring Boot 4.1.1 parent, Spring Cloud 2025.1.2 BOM
- Modules: `common-models`, `core-service`, `api-gateway`
- Frontend: none
- Database migrations: none
- CI: none
- Canonical verify command: not yet; PR-001 uses `./scripts/check-agent-docs.sh`

## Repository maturity

| Area | State |
| --- | --- |
| Product docs | Agent memory exists; PRD, ADRs, threat model, and OpenAPI do not |
| Backend | Two empty Spring Boot apps and two shared records |
| Tests | Module `contextLoads` tests plus the agent-docs check |
| Persistence | `core-service` declares JPA/PostgreSQL and MongoDB; no schema |
| Local environment | `.cursor/install.sh` and `start.sh` start PostgreSQL, Redis, and MongoDB |
| Docker / Compose | none |
| Angular console | none |

## Known contradictions

1. The current three-module split is the structure the blueprint and MVP override tell us not to start with.
2. The blueprint recommends AWS SQS, S3, WAF, API Gateway, Bedrock, and Terraform/CDK. The MVP override forbids those unless separately approved.
3. MongoDB and Redis are present in module dependencies and Cloud Agent scripts. The MVP baseline is PostgreSQL only.
4. `core-service` uses package `com.tenatmetrics`; other modules use `com.tenantmetrics`.
5. `ApiResponse` is a generic envelope; the blueprint requires Problem Details–compatible errors.

These contradictions are recorded, not resolved, in PR-001.

## What PR-001 added

- `AGENTS.md` with the durable operating model
- `README.md`
- `docs/agent/` working-memory files
- `scripts/check-agent-docs.sh`

## Next session load list

1. `AGENTS.md`
2. this file
3. the approved or proposed task specification
4. only the blueprint sections named in that specification
