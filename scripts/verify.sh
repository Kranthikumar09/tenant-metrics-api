#!/usr/bin/env bash
# Canonical verification for the approved platform-service path.
# Does not run frozen legacy module tests, including the pre-existing
# core-service context-load failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

echo "==> Agent and architecture docs"
./scripts/check-agent-docs.sh

echo "==> rules-scoring tests"
./mvnw -ntp -pl libs/rules-scoring test

echo "==> platform-service tests"
./mvnw -ntp -pl apps/platform-service -am test

echo "==> worker tests"
./mvnw -ntp -pl apps/worker -am test

echo "==> platform-service package"
./mvnw -ntp -pl apps/platform-service -am package

echo "==> worker package"
./mvnw -ntp -pl apps/worker -am package

echo "==> platform-service and worker dependency trees (ban MongoDB and Redis)"
tree_log="$(mktemp)"
./mvnw -ntp -pl apps/platform-service,apps/worker,libs/rules-scoring dependency:tree | tee "${tree_log}"
if grep -Ei 'org\.mongodb:|spring-boot-starter-data-mongodb|spring-data-mongodb|spring-boot-starter-data-redis|spring-data-redis|lettuce-core|redis\.clients' "${tree_log}"; then
  echo "verify.sh: FAIL banned MongoDB or Redis library found in dependency tree"
  rm -f "${tree_log}"
  exit 1
fi
rm -f "${tree_log}"

echo
echo "verify.sh: PASS"
