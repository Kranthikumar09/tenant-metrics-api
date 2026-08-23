# Data classification

- Status: Draft classification for Milestone 0
- Date: 2026-08-22
- Sources: blueprint data-sensitivity default; `docs/product/PRD.md` item P-006; ADR-001
- Rule: do not invent a production region, retention period, or compliance certification

This file classifies data the product may store or process. It does not authorize collecting forbidden classes.

## Classes

| Class | Examples | Allowed in v1 | Handling |
| --- | --- | --- | --- |
| Public | published API docs, status-page text | Yes | No tenant restriction |
| Tenant operational | tenant ID from a verified credential, account external ID, namespaced event type, `occurred_at`, `received_at`, schema version | Yes | Tenant-scope every query and side effect |
| Tenant analytics | rules scores, risk band, drivers, model version, score timestamp | Yes | Tenant-scoped; do not claim predictive accuracy until labels exist |
| Allowlisted event metadata | bounded JSON properties on an approved allowlist | Yes, only after an allowlist exists | Size and depth limits; no unrestricted PII |
| Secrets | API keys, webhook signing material, credentials | Yes, in a secret store only | Never in source, logs, prompts, test fixtures, or frontend bundles |
| Audit | actor, tenant, action, target, result, request ID, timestamp | Yes | Append-only security and admin events |

## Forbidden data

Do not accept, store, or log:

- secrets or tokens inside event metadata
- card data
- health data
- free-form or unrestricted PII in event metadata
- client-supplied tenant identifiers as authority

Regulated-data programs and certifications are not claimed. A DPA/subprocessor list is **BLOCKED** until a later approved doc.

## Handling rules

1. Derive tenant context from a verified credential. Strip client tenant headers.
2. Minimize: store only fields required for the v1 event and prediction contracts.
3. Do not log secrets, tokens, raw event payloads, or personal data.
4. Event `properties` stay a bounded JSON object. An exact allowlist is **BLOCKED** until a later contract PR.
5. Encryption in transit and at rest are required when persistence is added. Key-management product choice is **BLOCKED**.
6. Tenant export/delete is required before launch. Retention days, delete SLA, and restore proof are **BLOCKED**.
7. Region and residency follow PRD P-005: do not provision a cloud region in this PR.

## Open items

| ID | Topic | Status |
| --- | --- | --- |
| C-001 | Event-metadata allowlist | **BLOCKED** |
| C-002 | Retention days and deletion SLA | **BLOCKED** |
| C-003 | DPA and subprocessor inventory | **BLOCKED** |
| C-004 | Secret-store and KMS product | **BLOCKED** |
