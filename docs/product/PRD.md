# Product requirements (Milestone 0)

- Status: Draft product contract
- Date: 2026-08-22
- Sources: blueprint product contract and decisions-to-lock; ADR-001 for architecture
- Rule: use blueprint recommended defaults where they exist. Do not invent customer facts. Unresolved business choices are `BLOCKED`.

This file is the product contract for later PRs. It does not authorize application code, model training, or paid cloud resources.

## Buyer, user, and value

| Item | v1 definition | Status |
| --- | --- | --- |
| Default product | B2B SaaS API and console that helps subscription businesses identify accounts likely to churn, understand measurable drivers, and trigger retention workflows | Default |
| Economic buyer | Head of Customer Success, Product, or Revenue Operations at a subscription business | Default |
| Primary users | Customer Success managers, product analysts, growth teams, and integration engineers | Default |
| Core job | Prioritize the right customer accounts for intervention before renewal or cancellation | Default |
| Output | Risk score, risk band, top drivers, model version, score timestamp, and optional explanation | Default |
| Action | Webhook, dashboard queue, or API query that feeds the tenant's existing workflow | Default |
| Ideal customer profile | B2B subscription software with a customer-account object and at least 6 months of event/outcome history | Default |

Explanations may describe an already-computed score. An LLM is not the authoritative scoring engine.

## Scored entity

Default scored entity is `ACCOUNT`.

`USER` and `SUBSCRIPTION` are later options. They need different features and actions and are out of v1 unless a later ADR changes this.

## Churn label

Every tenant configuration must specify a positive label. Allowed families from the blueprint:

- cancellation
- non-renewal
- downgrade below an agreed threshold
- a tenant-supplied outcome event

**BLOCKED:** the platform-wide default positive label is not chosen. Do not implement model training, advertise predictive accuracy, or invent a customer-specific label until the product owner names the default and the leakage rules.

Cold start uses transparent rules and cohort baselines. Do not claim predictive accuracy until historical labels exist and an evaluation report passes agreed thresholds.

## Prediction horizon

| Item | Recommended default | Status |
| --- | --- | --- |
| Prediction horizon | 30 days before the expected churn event | Default |
| Observation window | 60 days of activity | Default |
| Label delay / leakage rules | Outcomes that were not knowable at prediction time must not be used for training | **BLOCKED** — rule family is named; the concrete leakage tests are not approved |
| Intervention exclusions | Test accounts, employees, already-cancelled accounts, and insufficient-history subjects | Default exclusion families; exact filters **BLOCKED** |

**STOP:** do not implement model training until the churn label, prediction horizon, scored entity, and leakage rules are approved. Horizon and scored entity now have named defaults. The churn label and concrete leakage tests remain `BLOCKED`.

## v1 use cases

In-scope capabilities:

1. Tenant onboarding
2. Auth and API keys
3. Customer/account upsert
4. Batch event ingestion
5. Configurable churn definition
6. Baseline (rules) risk score
7. Predictions API
8. Customer console / dashboard
9. Signed webhooks
10. Usage metering
11. Audit trail

Architecture for those capabilities follows ADR-001: `/apps/platform-service`, `/apps/worker`, `/apps/console`, PostgreSQL, and LocalStack-compatible queues/storage.

## Non-goals

Not in v1:

- Tenant-specific learned models
- Warehouse-native ingestion
- Salesforce/HubSpot connectors
- Automated playbooks
- Real-time streaming features
- Enterprise SSO/SCIM
- Dedicated tenant deployments
- MongoDB or Redis
- AWS WAF, managed API Gateway, Bedrock, Kubernetes, Terraform/CDK

Not a v1 promise:

- Perfect predictions
- Autonomous retention actions
- Causal claims
- Real-time scores for every event
- Compliance certification before controls and audit evidence exist

## Activation metric

A tenant is activated when it sends a valid event and retrieves a score within 30 minutes of setup.

Cold-start time-to-value: first useful at-risk list within 24 hours from transparent rules. Model-based timing depends on label history and is not a v1 promise.

## Model-value metric

Model value is lift in the top-risk decile and recall at the customer's intervention capacity, not raw accuracy.

Business outcome is measured improvement in retained revenue or reduced churn versus a holdout/control workflow. That outcome is not a launch claim until evaluation evidence exists.

## SLO targets

| Target | Recommended default | Status |
| --- | --- | --- |
| Public API availability | 99.5% beta; 99.9% GA | Default |
| Reliability properties | No cross-tenant access; no duplicate side effects; score freshness and webhook delivery meet published SLOs | Default properties; numeric freshness/webhook SLOs **BLOCKED** |
| Initial capacity envelope | 100 tenants, 10M events/month, 500-event batch, 500 request/s shared peak | Planning envelope only; replace with load-test evidence before launch |

## Open decisions

| ID | Topic | Working default | Status |
| --- | --- | --- | --- |
| P-001 | Platform-wide default churn label | None | **BLOCKED** |
| P-002 | Concrete leakage / label-delay tests | Principle only: no future information in training | **BLOCKED** |
| P-003 | Exact intervention-exclusion filters | Families named above | **BLOCKED** |
| P-004 | Score freshness and webhook numeric SLOs | Meet published SLOs | **BLOCKED** |
| P-005 | Beta region | Blueprint suggested one AWS region; ADR-001 did not select AWS. Hosting remains Railway or another approved low-cost host | **BLOCKED** — do not provision a cloud region |
| P-006 | Data sensitivity | No secrets, card data, health data, or free-form PII in event metadata | Default; see `docs/security/data-classification.md` |
| P-007 | Pricing amounts | Unit is monthly platform fee plus metered accepted events | Amounts **BLOCKED** |

## Resolved decisions

- P-008 — Frozen module deletion: resolved by the explicit approval of PR-034R. The unused `api-gateway`, `core-service`, and `common-models` files and their MongoDB, Redis, and Spring Cloud wiring were retired without changing active product behavior.

## Traceability

| v1 capability | Acceptance metric or gate |
| --- | --- |
| Tenant onboarding | Activation metric |
| Auth and API keys | No cross-tenant access; tenant resolved from a verified credential |
| Customer/account upsert | Valid account exists before a score can be retrieved |
| Batch event ingestion | Activation metric; idempotent side effects |
| Configurable churn definition | Tenant can name subject, label, window, and horizon; training stays **BLOCKED** until P-001 and P-002 |
| Baseline risk score | Cold-start at-risk list within 24 hours; no predictive-accuracy claim |
| Predictions API | Activation metric; score includes model version and timestamp |
| Customer console | Primary users can see risk, drivers, and a queue |
| Signed webhooks | Delivery meets published SLO once P-004 is unblocked |
| Usage metering | Count accepted events against the pricing unit |
| Audit trail | Security-relevant actions are attributable per tenant |
