#!/usr/bin/env bash
# Idempotent repository bootstrap for the tenant-metrics-api Cloud Agent environment.
# Installs the database servers the two services need and warms the Maven build.
# Runtime daemons are started in start.sh, not here.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

export DEBIAN_FRONTEND=noninteractive

# --- System packages: PostgreSQL, Redis, MongoDB ---
# Register the MongoDB apt repository only once.
if [ ! -f /etc/apt/sources.list.d/mongodb-org-8.0.list ]; then
  . /etc/os-release
  curl -fsSL https://pgp.mongodb.com/server-8.0.asc \
    | sudo gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor --yes
  echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu ${VERSION_CODENAME}/mongodb-org/8.0 multiverse" \
    | sudo tee /etc/apt/sources.list.d/mongodb-org-8.0.list >/dev/null
fi

sudo apt-get update -qq
sudo apt-get install -y -qq \
  postgresql postgresql-contrib \
  redis-server \
  mongodb-org

# --- Warm the Maven dependency cache and produce runnable jars for both services ---
( cd api-gateway && ./mvnw -B -ntp -DskipTests package )
( cd core-service && ./mvnw -B -ntp -DskipTests package )

echo "install.sh complete: databases installed, both services built."
