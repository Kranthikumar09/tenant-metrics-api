# AGENTS.md

Working rules for the Multi-Tenant Churn Intelligence SaaS. Read this file, `docs/agent/current-state.md`, the proposed task specification, and only the directly relevant blueprint sections before changing code.

## Operating model

Work on exactly one small pull request at a time.

1. Inspect the current repository and only the relevant blueprint sections.
2. Propose one small PR using the proposal format in this file.
3. Stop and wait for explicit approval.
4. After `APPROVE PR-XXX`, implement the entire approved PR autonomously.
5. Follow test-driven development.
6. Run the complete applicable validation.
7. Create a branch, commits, and a draft PR when GitHub access is available.
8. Produce a completion report and recommend the next small PR.
9. Stop and wait for approval again.

Approval is required once per PR, not for individual files, edits, safe local commands, or test runs.

Do not begin implementation until the user replies `APPROVE PR-XXX`.
Read-only inspection and planning are allowed before approval.

Do not attempt to build the entire product in one session or load the entire blueprint into working context for every task.

## Source-of-truth priority

When instructions conflict, use this order:

1. The user's latest explicit instruction
2. This file
3. Approved Architecture Decision Records
4. The approved task specification
5. The attached product/engineering blueprint
6. Existing implementation

Do not silently resolve a meaningful conflict. Explain it and request a decision.

## Approved architecture contract

ADR-001 is the approved architecture contract. Read `docs/architecture/ADRs/ADR-001-mvp-architecture.md` and `docs/architecture/context-map.md` before adding modules or dependencies.

- Target layout is `/apps/platform-service`, `/apps/worker`, and `/apps/console`.
- `/apps/worker` is a same-version background process, not an independently extracted microservice.
- Existing `api-gateway`, `core-service`, and `common-models` are frozen legacy modules.
- New implementation must not build on the frozen modules.
- New modules must not introduce MongoDB or Redis.
- MongoDB and Redis removal from legacy modules requires separate approval.
- Work must proceed through small PRs with approval between PRs.

## Cost-optimized MVP

Use this baseline. ADR-001 is the architecture contract that defines it:

- Java 21
- Spring Boot modular monolith at `/apps/platform-service`
- `/apps/worker` for event, feature, scoring, and webhook processing
- Angular frontend at `/apps/console`, which may initially be served from the Spring Boot deployment
- PostgreSQL as the primary database
- Local SQS/S3 or LocalStack-compatible substitutes for queues and object storage
- Docker Compose for local development
- Supabase PostgreSQL/Auth/Storage when managed services are required
- Railway or another approved low-cost container host
- GitHub Actions for CI
- Python scripts or jobs for offline ML training
- Rules-based churn scoring before learned models
- Provider interfaces around storage, queues, authentication, and explanations

Do not provision the following during the MVP unless a later ADR or explicit approval changes it:

- AWS WAF
- Managed AWS API Gateway
- Bedrock
- Redis
- Kubernetes
- Multiple independently extracted microservices
- Multiple cloud environments
- Multi-region deployment
- Terraform or CDK
- Paid monitoring platforms

SQS, S3, and the `/apps/worker` process are approved by ADR-001. Prefer LocalStack-compatible substitutes locally. Design replaceable interfaces where later migration may be necessary. Do not build speculative infrastructure.

## Context management

Do not repeatedly consume the complete blueprint.

During onboarding, read only: executive verdict, product contract, decisions to lock, recommended architecture, AI-assisted development rules, milestone headings, and launch requirements.

Maintain these working-memory files:

- `docs/agent/project-index.md`
- `docs/agent/current-state.md`
- `docs/agent/backlog.md`
- `docs/agent/decisions-needed.md`

`project-index.md` contains blueprint section names, a one- or two-sentence summary per section, the relevant repository module or directory, and whether the section is implemented, pending, or deferred. Do not copy the blueprint into it.

For each PR, load only:

- this file
- `docs/agent/current-state.md`
- the proposed task specification
- directly relevant blueprint sections
- directly relevant modules and tests
- applicable ADRs and contracts

At the end of every completed PR, update `current-state.md` and `backlog.md`.

## PR capacity

Classify proposed work as:

- XS: documentation, isolated configuration, or a very small behavior
- S: one observable behavior within one bounded area
- M: multiple behaviors, modules, or architectural concerns
- L: milestone-sized or cross-system implementation

Execute only XS or S work. Split M and L work into multiple independent PRs.

A normal PR should target:

- one observable behavior or one enabling foundation
- approximately 100–400 handwritten lines where practical
- no more than eight hand-edited source/configuration files where practical
- no more than one database migration
- no unrelated refactoring
- no speculative future features

Generated files and lockfiles do not count toward the size target. Their changes must still be reviewed.

A thin vertical slice may cross API, service, repository, and test layers when all changes are necessary for one observable behavior.

Do not combine unrelated frontend, backend, infrastructure, and refactoring work in one PR.

## Proposal required before every PR

Respond using this format before implementation:

```
## PROPOSED PR
**PR ID:** PR-XXX
**Title:**
**Capacity:** XS or S
**Customer/engineering outcome:**
**Why this should be next:**
**Blueprint sections required:**
**Current repository state:**
**In scope:**
**Out of scope:**
**Expected files/modules:**
**Tests to write first:**
**Validation commands:**
**Risks:**
**Dependencies or decisions required:**
**Estimated review size:**
**Recommended action:** Approve, modify or defer this PR.
```

End with:

`AWAITING APPROVAL: Reply APPROVE PR-XXX to begin.`

## TDD requirements

After approval, follow Red-Green-Refactor:

1. Write or update a test that expresses the approved behavior.
2. Run it and confirm that it fails for the expected reason.
3. Implement the minimum production code needed to pass.
4. Run the focused test again.
5. Refactor without changing behavior.
6. Run the module test suite.
7. Run the full applicable project validation.

Never write all production code before tests, delete or weaken a valid test to pass the build, disable validation, reduce assertions without justification, mock the behavior being tested, leave placeholder TODOs, claim TDD without identifying the initial failing test, or hide a pre-existing or newly introduced failure.

For tenant-owned behavior, include positive and negative tenant-isolation tests.
For persistence behavior, prefer integration tests using the project's approved PostgreSQL test approach.
For API behavior, validate both the success contract and the error contract.
For concurrency or idempotency behavior, include duplicate/retry tests where applicable.

## Build validation

Discover and use repository-standard commands. The project should eventually expose one canonical command such as `./scripts/verify.sh` or `make verify`.

Until that pipeline exists, run every check that applies to the PR. Do not claim completion unless those checks pass.

If a test was already failing before the change:

1. Verify it against the starting commit.
2. Report it as pre-existing.
3. Do not silently expand scope to fix it.
4. Ask for approval if fixing it requires another PR.

## Git and pull requests

After approval:

- Confirm the working tree state and preserve unrelated user changes.
- Create a branch named `cursor/pr-XXX-short-description-9d98` unless a later instruction changes the convention.
- Make small, logical commits.
- Never commit credentials, `.env` files, generated secrets, or local build artifacts.
- Never force-push, merge the PR, deploy to production, or modify unrelated files to make the diff appear clean.

If GitHub access exists, open a draft PR after validation.
If GitHub access is unavailable, prepare the branch name, commit messages, PR title, complete PR description, and testing evidence.

## External action boundaries

Separate approval is required before creating a paid cloud resource, changing billing settings, purchasing or configuring a domain, deploying to production, accessing production customer data, running a destructive database migration, deleting cloud or repository resources, rotating production credentials, changing tenant-isolation rules, promoting a learned model, enabling customer-facing AI explanations, or making customer-facing performance or accuracy claims.

Local Docker resources, local databases, tests, formatting, compilation, and approved branch work do not require repeated conversational approval after the PR is approved.

## Stop conditions

Stop and ask for direction when:

- product behavior is genuinely undefined
- the blueprint and code conflict materially
- a proposed change weakens tenant isolation
- a credential, paid account, or production permission is missing
- the approved scope must grow beyond an S-sized PR
- a database operation could cause data loss
- tests reveal an architectural problem outside the PR
- a model cannot meet the documented evaluation gate
- an external dependency or provider assumption cannot be verified
- the working tree contains conflicting user changes

Do not guess through these conditions.

## Completion report

After implementation, respond using the repository completion-report format: PR ID, status, branch, draft PR URL, delivered behavior, TDD evidence, changed files, validation evidence, acceptance criteria, security and tenant review, deviations, remaining risks, repository-memory update, and at most three proposed next PRs.

End with:

`AWAITING APPROVAL: No additional implementation will begin until the next PR is approved.`
