# ADR-001: MVP modular-monolith layout and data stores

- Status: Accepted
- Date: 2026-08-22
- Deciders: product owner instruction on 2026-08-22

## Context

When this ADR was accepted, the repository contained a three-module Maven scaffold (`api-gateway`, `core-service`, `common-models`). That split did not match the approved product shape. `core-service` also declared Spring Data MongoDB, and Cloud Agent scripts installed MongoDB and Redis, even though no application code used either store.

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

At acceptance, `api-gateway`, `core-service`, and `common-models` were frozen placeholders pending an exact cleanup proposal. PR-034R later received explicit approval to retire every file in those modules and remove their unused reactor dependencies. They are not part of the approved architecture and must not be recreated.

## Consequences

- Feature code belongs in the approved `apps/` layout; shared domain code may live under `libs/`.
- New implementation must not recreate or depend on the retired scaffold.
- Provider interfaces should wrap queues, object storage, authentication, and explanations so LocalStack-compatible local substitutes can be replaced later.
- `AGENTS.md` and `docs/architecture/context-map.md` must stay consistent with this ADR. SQS, S3, and `/apps/worker` are approved here; they are not forbidden.
- The root reactor and local developer tooling must describe only the approved application layout.

## Original decision scope

The original documentation-only ADR did not itself change application code, POMs, Docker, or `.cursor/` scripts. Those changes were delivered through separately approved PRs.

## Follow-up

1. Completed: create `/apps/platform-service` without MongoDB or Redis.
2. Completed in PR-034R: retire the unused scaffold, MongoDB dependency, Redis/MongoDB bootstrap, and Spring Cloud gateway BOM.
3. Completed: add Docker Compose for PostgreSQL and LocalStack-compatible SQS/S3.
4. Completed: add `/apps/worker` and `/apps/console` in separate PRs.
