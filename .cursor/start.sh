#!/usr/bin/env bash
# Per-boot startup for the approved local dependencies. Application processes
# are launched by the terminals in environment.json.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

docker compose up -d postgres localstack

for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U platform -d platform >/dev/null 2>&1 \
    && curl -fsS http://localhost:4566/_localstack/health >/dev/null; then
    echo "start.sh complete: PostgreSQL and LocalStack SQS/S3 are ready."
    exit 0
  fi
  sleep 1
done

echo "start.sh: dependencies did not become ready within 60 seconds" >&2
docker compose ps >&2
exit 1
