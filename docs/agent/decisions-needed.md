# Decisions needed

Record conflicts that a later ADR or explicit user instruction must resolve. Do not implement around these silently.

## Open decisions

### D-004 — Churn label and horizon

The blueprint stop condition still applies: do not implement model training until the churn label, prediction horizon, scored entity, and leakage rules are approved.

`docs/product/PRD.md` now names scored entity (`ACCOUNT`) and prediction horizon (30 days) as defaults. The platform-wide default churn label and concrete leakage tests remain `BLOCKED`.

### D-005 — Branch naming

The operating prompt asked for `pr/PR-XXX-short-description`. Cloud Agent policy requires `cursor/<descriptive-name>-9d98`.

Assumption: use `cursor/pr-XXX-short-description-9d98` unless the user overrides it.

### D-006 — When to delete frozen scaffold modules

ADR-001 freezes `api-gateway`, `core-service`, and `common-models`. It does not choose a deletion date. A later PR must list the exact files and receive approval before any module or dependency is removed.

The modular monolith target is already accepted; only the disposal of placeholders remains open.

### D-007 — Console browser authentication and session

The Angular console must not persist tenant API keys in browser storage. Prediction reads remain blocked until the browser-to-platform authentication and session contract is approved.

Recommended direction: serve the console and platform API from the same site, exchange identity for a short-lived server-managed session, and use Secure, HttpOnly, SameSite cookies with CSRF protection. PR-024R should record the decision before authentication or prediction-fetch code is added.

## Resolved

### D-001 — Three-module scaffold

Resolved by ADR-001: freeze the existing modules as placeholders. Add `/apps/platform-service`, `/apps/worker`, and `/apps/console` in later PRs. Do not reuse the old names for the new apps.

### D-002 — MongoDB and Redis

Resolved by ADR-001: MongoDB is not approved. Redis is not approved unless a later ADR states a concrete requirement. Do not add new usage. Removal is a later approved PR that lists exact files.

### D-003 — AWS stack versus MVP

Resolved by ADR-001: PostgreSQL is the primary store. Local SQS/S3 or LocalStack-compatible substitutes are approved. `/apps/worker` is a same-version background process, not a microservice extraction. WAF, API Gateway, Bedrock, Terraform/CDK, and multi-environment cloud accounts remain unapproved.
