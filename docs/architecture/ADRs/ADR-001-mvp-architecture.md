# ADR-001: MVP modular-monolith layout and data stores

- Status: Accepted
- Date: 2026-08-22
- Deciders: product owner instruction on 2026-08-22

## Context

The repository currently contains a three-module Maven scaffold (`api-gateway`, `core-service`, `common-models`). That split does not match the approved product shape. `core-service` also declares Spring Data MongoDB, and Cloud Agent scripts install MongoDB and Redis, even though no application code uses either store.

Two earlier baselines conflicted:

- The blueprint's production-oriented AWS options (SQS, S3, managed edge, Terraform/CDK).
- The original cost-optimized operating prompt, which forbade SQS, S3, and a separate worker until an ADR said otherwise.

This ADR locks the approved MVP architecture so later PRs do not silently keep the scaffold, MongoDB, or Redis.

## Decision

The MVP architecture is:

- `/apps/platform-service`: Spring Boot modular monolith for identity, tenancy, HTTP API, and control plane.
- `/apps/worker`: same-version background process for event, feature, scoring, and webhook work. This is not an independently extracted microservice.
- `/apps/console`: Angular application.
- PostgreSQL is the primary source of truth.
- Local asynchronous messaging and object storage use SQS/S3 or LocalStack-compatible substitutes.
- MongoDB is not approved for the MVP.
- Redis is not approved unless a later ADR states a concrete requirement.
- Microservice extraction is deferred until measured scaling or ownership needs exist.

Existing `api-gateway`, `core-service`, and `common-models` are frozen placeholders. They are not the target applications. They must not be deleted, renamed, or have dependencies removed until a later PR lists the exact files and receives approval.

## Consequences

- New feature code belongs in the approved `apps/` layout, created by later PRs.
- Provider interfaces should wrap queues, object storage, authentication, and explanations so LocalStack-compatible local substitutes can be replaced later.
- `AGENTS.md` still contains the older forbid-list for SQS, S3, and worker infrastructure. Until that file is updated in a later approved docs PR, this ADR and the latest explicit user instruction take precedence for those items.
- This ADR does not add Docker Compose, LocalStack, or application modules.

## Non-goals

- No application code, POM, Docker, or `.cursor/` script changes.
- No deletion of existing modules or dependencies.
- No product PRD, threat model, or feature implementation.

## Follow-up

1. Create `/apps/platform-service` as an independently buildable module that boots without MongoDB or Redis.
2. Later, list and remove MongoDB dependencies and daemons only after explicit approval.
3. Later, list and remove Redis from Cloud Agent scripts only after explicit approval.
4. Add Docker Compose for PostgreSQL and LocalStack-compatible SQS/S3.
5. Add `/apps/worker` and `/apps/console` in separate PRs.
