# Decisions needed

Record conflicts that a later ADR or explicit user instruction must resolve. Do not implement around these silently.

## Open decisions

### D-004 — Churn label and horizon

The blueprint stop condition still applies: do not implement model training until the churn label, prediction horizon, scored entity, and leakage rules are approved.

`docs/product/PRD.md` now names scored entity (`ACCOUNT`) and prediction horizon (30 days) as defaults. The platform-wide default churn label and concrete leakage tests remain `BLOCKED`.

### D-005 — Branch naming

The operating prompt asked for `pr/PR-XXX-short-description`. Cloud Agent policy requires `cursor/<descriptive-name>-9d98`.

Assumption: use `cursor/pr-XXX-short-description-9d98` unless the user overrides it.

### D-008 — Production OIDC provider and tenant configuration

ADR-002 defines a provider-neutral OIDC Authorization Code with PKCE contract. The production provider, tenant, client registration, claims mapping, credentials, and redirect domains remain `BLOCKED` and require a separate approval. Do not add production credentials to the repository.

## Resolved

### D-001 — Three-module scaffold

Resolved by ADR-001 and PR-034R: `/apps/platform-service`, `/apps/worker`, and `/apps/console` replaced the unused placeholders, which were then removed after explicit approval. Do not reuse the old names for new apps.

### D-002 — MongoDB and Redis

Resolved by ADR-001 and PR-034R: MongoDB is not approved, and Redis requires a later ADR with a concrete requirement. Their unused legacy dependencies and developer bootstrap daemons were removed.

### D-003 — AWS stack versus MVP

Resolved by ADR-001: PostgreSQL is the primary store. Local SQS/S3 or LocalStack-compatible substitutes are approved. `/apps/worker` is a same-version background process, not a microservice extraction. WAF, API Gateway, Bedrock, Terraform/CDK, and multi-environment cloud accounts remain unapproved.

### D-007 — Console browser authentication and session

Resolved by ADR-002: use a same-origin, server-managed session backed by PostgreSQL, with provider-neutral OIDC Authorization Code plus PKCE, a Secure/HttpOnly session cookie, CSRF protection, and server-side tenant binding. API keys and OAuth tokens must not be stored by Angular.

### D-006 — When to delete frozen scaffold modules

Resolved by the user's explicit approval of PR-034R: delete every tracked file under `api-gateway`, `core-service`, and `common-models`, then remove their root POM and Cursor references after behavior-neutral build verification.
