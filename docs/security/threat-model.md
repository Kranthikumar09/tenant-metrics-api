# Threat model

- Status: Draft Milestone 0 STRIDE review
- Date: 2026-08-22
- Sources: blueprint tenant-isolation model and prompt 0.2; ADR-001; `docs/product/PRD.md`; `docs/security/data-classification.md`
- Rule: residual risk is Open or `BLOCKED` until a named control exists and is verified. Do not accept residual risk in this file.

This is a data-flow review of the approved target, not a claim that the controls are implemented. Most surfaces below do not exist in code yet. No application code is added by this PR.

Owners are roles, not named people. Named human owners remain `BLOCKED`.

## Trust boundaries

| Boundary | Inside | Outside |
| --- | --- | --- |
| Public edge | `/apps/platform-service` after credential validation | Browser, integration clients, customer webhook receivers |
| Tenant context | Immutable `TenantContext` resolved from a server-side credential | Any client header, path, or body field claiming a tenant |
| Data plane | PostgreSQL tenant-scoped rows; queue messages and object prefixes tagged with tenant | Other tenants, support tools, CI logs |
| Control plane | Auth, API keys, audit, usage | Billing provider, IdP, GitHub Actions |
| Worker | Same-version `/apps/worker` using workload identity | Customer networks, model-training hosts |
| Deferred AI | Not in MVP unless a later ADR | Bedrock or any explanation provider |

## Data flows

1. Console user authenticates with OIDC; server resolves workspace membership and RBAC.
2. Integration client presents an API key or client credential; server hashes and looks up the tenant.
3. Edge rejects a forged tenant header and creates request-scoped `TenantContext`.
4. Account upsert and event batch validate input, persist or enqueue with `tenant_id`, and return a request ID.
5. Worker reads tenant-tagged queue messages, updates PostgreSQL, and may write tenant-prefixed object storage.
6. Prediction reads and webhook outbox stay tenant-scoped. Webhooks call customer URLs with signed payloads.
7. CI clones this repository and runs `./scripts/verify.sh`. Deploy and cloud credentials are not in CI.
8. Backups and support access, when added, must remain tenant-safe and audited.

## STRIDE catalog

Columns: asset, attacker, abuse path, control, verification, owner, residual risk.

| ID | Surface / STRIDE | Asset | Attacker | Abuse path | Control | Verification | Owner | Residual risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-01 | Tenant resolution / Elevation | Tenant authorization context | External client | Send a forged tenant header (`X-Tenant-ID` or equivalent) to act as another tenant | Strip or reject client tenant headers before auth; resolve tenant from the verified credential; immutable `TenantContext` | Negative test: Tenant A credential + Tenant B header or IDs fails | Engineering | Open until a production IdP replaces the hashed test-key store |
| T-02 | API / Information disclosure | Tenant events, scores, accounts | Tenant A caller | Read or mutate Tenant B objects by guessing IDs (cross-tenant) | Bind `tenant_id` on every query, unique constraint, cache key, queue message, object prefix, audit event, and metric | Tenant-negative suite for read, update, delete, and duplicate external IDs | Engineering | Open until persistence is tenant-scoped |
| T-03 | Event ingestion / Replay | Event side effects, usage | Client or intermediary | Replay an accepted batch to duplicate scores, webhooks, or usage | `event_id` unique per tenant; `Idempotency-Key` on side-effect APIs | Duplicate/retry tests leave one persisted effect | Engineering | Open until receipts are stored in PostgreSQL |
| T-04 | Event ingestion / Denial | API availability | Abusive or buggy client | Oversized batch, nested JSON, or decompression bomb (resource exhaustion) | OpenAPI/JSON Schema limits, batch size, payload and decompression limits, plan rate limits | Tests for oversized body, malformed input, and bounded batch | Engineering | Open until payload, decompression, and rate limits exist |
| T-05 | Console login / Spoofing | Workspace session | Phishing or stolen cookie | Steal a console session and change API keys or webhooks | OIDC/OAuth 2.1 + PKCE; server-side RBAC; no client-side tenant switch | Auth tests; session expiry; two-tenant console isolation later | Engineering | Open until console exists |
| T-06 | API keys / Information disclosure | Hashed API keys | Repo, logs, or leaked fixture | Recover a raw key from source, logs, or frontend | Hash keys at rest; prefix + last-used + expiry; never log secrets | Secret scan; classification forbid-list | Engineering | Open until key store exists |
| T-07 | Queues / Tampering | Tenant-tagged messages | Compromised worker or mis-published message | Process Tenant B work on a Tenant A consumer | Tenant key on every message; worker uses workload identity; no shared static secret | Consumer rejects missing/mismatched tenant tag | Engineering | Open until worker/queue PR |
| T-08 | PostgreSQL / Information disclosure | Tenant rows | App bug or shared query | Query without tenant predicate | Tenant-scoped repositories; optional RLS later | Cross-tenant SQL tests | Engineering | Open until schema PR |
| T-09 | Object storage / Information disclosure | Raw-event archive, artifacts | Caller or misconfigured prefix | List or read another tenant's objects | Tenant prefix isolation; no global list | Prefix-isolation tests | Engineering | Open until storage PR |
| T-10 | Webhooks / SSRF | Internal network, metadata service | Tenant admin | Register a webhook URL that targets internal IPs (webhook SSRF) | SSRF-safe URL validation, HTTPS in production, DNS/IP revalidation, timeouts | Tests for private-network targets and DNS rebinding | Engineering | Open until webhook PR |
| T-11 | Webhooks / Tampering | Customer receiver | Network attacker | Forge or replay a delivery | HMAC with version and timestamp; documented verification window | Replay-window and signature tests | Engineering | Open until webhook PR |
| T-12 | Billing webhooks / Spoofing | Entitlements | External caller | Forge a billing-provider webhook to raise quotas | Provider signature verification; no tenant header trust | Unsigned/forged billing webhook rejected | Engineering | Open until billing PR |
| T-13 | Support/admin / Elevation | Any tenant | Insider or stolen admin | Silent tenant impersonation | Separate privileged role, MFA, reason-for-access, immutable audit | Audit contains actor, tenant, reason | Product owner | Open; named admin process **BLOCKED** |
| T-14 | CI/CD / Tampering | Build and release | Compromised workflow or dependency | Inject deploy credentials or unpinned actions | `contents: read` only; pinned actions; no deploy job; no cloud credentials | Workflow docs check forbids AWS actions and `pull_request_target` | Engineering | Open: CI exists; deploy still forbidden |
| T-15 | Backups / Information disclosure | Database and object backups | Stolen snapshot or over-broad restore | Restore Tenant B data into Tenant A | Tenant-safe backup/restore procedure; encryption at rest | Restore proof when backups exist | Engineering | Open; restore SLA **BLOCKED** |
| T-16 | Model jobs / Tampering | Training set and scores | Tenant or insider | Inject poisoned training data so a later model mis-ranks accounts | No training until churn label and leakage tests are approved; time-split evaluation; no future information | Leakage tests; training remains **BLOCKED** (PRD P-001/P-002) | Product owner | **BLOCKED** |
| T-17 | Explanations / Information disclosure | Prompts, scores, event text | Prompt injection or provider staff | Cause prompt/data leakage of tenant events to an LLM or Bedrock | No Bedrock in MVP; explanations optional and after a score; never put secrets or raw payloads in prompts | Prompt-injection fixtures only after an explanation ADR | Product owner | **BLOCKED** — Bedrock not approved |

## Required scenarios

These rows are mandatory and must stay in the catalog:

| Scenario | Catalog ID |
| --- | --- |
| cross-tenant | T-02 |
| forged tenant header | T-01 |
| replay | T-03, T-11 |
| webhook SSRF | T-10 |
| resource exhaustion | T-04 |
| prompt/data leakage | T-17 |
| poisoned training data | T-16 |

## Open items

| ID | Topic | Status |
| --- | --- | --- |
| TM-001 | Named human owners for each row | **BLOCKED** — roles only |
| TM-002 | Residual-risk acceptance | **BLOCKED** until a named control is verified |
| TM-003 | Executable OWASP/SSRF/restore suite | **BLOCKED** until those surfaces exist |
