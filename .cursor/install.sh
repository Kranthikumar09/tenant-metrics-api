#!/usr/bin/env bash
# Idempotent repository bootstrap for the approved application layout.
# Runtime containers and application processes are started separately.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

./mvnw -B -ntp -DskipTests -pl apps/platform-service,apps/worker -am install
(
  cd apps/console
  npm ci
)

echo "install.sh complete: approved Maven reactor built and console dependencies installed."
