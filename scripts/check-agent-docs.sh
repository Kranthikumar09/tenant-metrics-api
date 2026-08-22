#!/usr/bin/env bash
# Verifies the agent operating-system files exist and contain required headings.
# This is the PR-001 contract: a new session can continue from repository memory.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0

require_file() {
  local path="$1"
  if [[ ! -f "${ROOT}/${path}" ]]; then
    echo "MISSING FILE: ${path}"
    failures=$((failures + 1))
    return 0
  fi
  echo "FOUND FILE: ${path}"
}

require_heading() {
  local path="$1"
  local heading="$2"
  if [[ ! -f "${ROOT}/${path}" ]]; then
    return 0
  fi
  if ! grep -q -F -- "${heading}" "${ROOT}/${path}"; then
    echo "MISSING HEADING in ${path}: ${heading}"
    failures=$((failures + 1))
    return 0
  fi
  echo "FOUND HEADING in ${path}: ${heading}"
}

forbid_text() {
  local path="$1"
  local text="$2"
  if [[ ! -f "${ROOT}/${path}" ]]; then
    return 0
  fi
  if grep -q -F -- "${text}" "${ROOT}/${path}"; then
    echo "OUTDATED TEXT in ${path}: ${text}"
    failures=$((failures + 1))
    return 0
  fi
  echo "ABSENT OUTDATED TEXT in ${path}: ${text}"
}

require_file "AGENTS.md"
require_file "README.md"
require_file "docs/agent/project-index.md"
require_file "docs/agent/current-state.md"
require_file "docs/agent/backlog.md"
require_file "docs/agent/decisions-needed.md"

require_heading "AGENTS.md" "## Operating model"
require_heading "AGENTS.md" "## Source-of-truth priority"
require_heading "AGENTS.md" "## Cost-optimized MVP"
require_heading "AGENTS.md" "## Context management"
require_heading "AGENTS.md" "## PR capacity"
require_heading "AGENTS.md" "## TDD requirements"
require_heading "AGENTS.md" "## Build validation"
require_heading "AGENTS.md" "## Git and pull requests"
require_heading "AGENTS.md" "## Stop conditions"

require_heading "README.md" "AGENTS.md"
require_heading "README.md" "Churn Intelligence"

require_heading "docs/agent/project-index.md" "## How to use this index"
require_heading "docs/agent/project-index.md" "Executive verdict"
require_heading "docs/agent/project-index.md" "Product contract"
require_heading "docs/agent/project-index.md" "implemented"
require_heading "docs/agent/project-index.md" "pending"
require_heading "docs/agent/project-index.md" "deferred"

require_heading "docs/agent/current-state.md" "## Snapshot"
require_heading "docs/agent/current-state.md" "## Repository maturity"
require_heading "docs/agent/current-state.md" "## Known contradictions"

require_heading "docs/agent/backlog.md" "## Next recommended PR"
require_heading "docs/agent/backlog.md" "PR-002"

require_heading "docs/agent/decisions-needed.md" "## Open decisions"
require_heading "docs/agent/decisions-needed.md" "modular monolith"

require_file "docs/architecture/ADRs/ADR-001-mvp-architecture.md"
require_file "docs/architecture/context-map.md"

require_heading "docs/architecture/ADRs/ADR-001-mvp-architecture.md" "Status: Accepted"
require_heading "docs/architecture/ADRs/ADR-001-mvp-architecture.md" "platform-service"
require_heading "docs/architecture/ADRs/ADR-001-mvp-architecture.md" "PostgreSQL"
require_heading "docs/architecture/ADRs/ADR-001-mvp-architecture.md" "MongoDB"
require_heading "docs/architecture/ADRs/ADR-001-mvp-architecture.md" "Redis"
require_heading "docs/architecture/context-map.md" "## Current modules"
require_heading "docs/architecture/context-map.md" "## Target modules"
require_heading "docs/architecture/context-map.md" "Freeze"

require_heading "AGENTS.md" "## Approved architecture contract"
require_heading "AGENTS.md" "ADR-001 is the approved architecture contract"
require_heading "AGENTS.md" "frozen legacy modules"
require_heading "AGENTS.md" "must not build on the frozen modules"
require_heading "AGENTS.md" "must not introduce MongoDB or Redis"
require_heading "AGENTS.md" "MongoDB and Redis removal from legacy modules requires separate approval"
require_heading "AGENTS.md" "/apps/worker"
require_heading "AGENTS.md" "LocalStack"

forbid_text "AGENTS.md" "Separate worker infrastructure"
forbid_text "AGENTS.md" "Do not provision or introduce the following during the MVP unless separately approved:"

require_heading "docs/architecture/context-map.md" "must not build on the frozen modules"
require_heading "docs/architecture/context-map.md" "frozen legacy modules"

require_file "docker-compose.yml"
require_heading "docker-compose.yml" "postgres:16-alpine"
forbid_text "docker-compose.yml" "mongo"
forbid_text "docker-compose.yml" "redis"

if [[ "${failures}" -gt 0 ]]; then
  echo
  echo "check-agent-docs: FAIL (${failures} missing required items)"
  exit 1
fi

echo
echo "check-agent-docs: PASS"
exit 0
