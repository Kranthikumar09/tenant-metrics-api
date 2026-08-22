# Decisions needed

Record conflicts that a later ADR or explicit user instruction must resolve. Do not implement around these silently.

## Open decisions

### D-001 — Keep, freeze, or replace the three-module scaffold

The repository currently has `api-gateway`, `core-service`, and `common-models`. Both the blueprint and the cost-optimized MVP require a Spring Boot modular monolith.

Options:

1. Freeze the existing modules and add `apps/platform-service` as the real application.
2. Collapse into one deployable module in a dedicated later PR after ADR-001.
3. Keep the names but change their meaning, which is likely to confuse reviewers.

Blocked application work: module layout, packaging, and local run commands.

### D-002 — MongoDB and Redis already in the environment

`core-service` depends on Spring Data MongoDB. `.cursor/install.sh` and `start.sh` install Redis and MongoDB. The MVP override forbids introducing Redis and treats PostgreSQL as the only primary database.

Assumption until ADR-001: do not add new MongoDB or Redis usage. Removal or replacement is a later approved PR.

### D-003 — Blueprint AWS stack versus cost-optimized MVP

The blueprint recommends SQS, S3, WAF, API Gateway, Bedrock, Terraform/CDK, and separate workers. The operating prompt forbids those for the MVP unless separately approved.

Assumption until an ADR says otherwise: follow the cost-optimized MVP override and keep provider interfaces for later migration.

### D-004 — Churn label and horizon

The blueprint stop condition still applies: do not implement model training until the churn label, prediction horizon, scored entity, and leakage rules are approved. PR-002 should name these as explicit defaults or `BLOCKED` items, not invent customer facts.

### D-005 — Branch naming

The operating prompt asked for `pr/PR-XXX-short-description`. Cloud Agent policy requires `cursor/<descriptive-name>-9d98`.

Assumption: use `cursor/pr-XXX-short-description-9d98` unless the user overrides it.

## Resolved in PR-001

None. PR-001 only documents the conflicts.
