#!/usr/bin/env bash
# Per-boot startup for the tenant-metrics-api Cloud Agent environment.
# Starts PostgreSQL, Redis and MongoDB idempotently, ensures the application
# database exists, waits for readiness, then returns. No dependency installation
# or source builds happen here (see install.sh).
set -euo pipefail

DB_NAME=coredb
DB_USER=core
DB_PASSWORD=core

# --- PostgreSQL ---
PG_VER="$(ls /etc/postgresql 2>/dev/null | sort -V | tail -1)"
if ! sudo -u postgres pg_isready -q 2>/dev/null; then
  sudo pg_ctlcluster "${PG_VER}" main start
fi
for _ in $(seq 1 30); do
  sudo -u postgres pg_isready -q 2>/dev/null && break
  sleep 1
done
sudo -u postgres pg_isready -q

# Ensure the application role and database exist (idempotent).
sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}'"
sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 \
  || sudo -u postgres createdb -O "${DB_USER}" "${DB_NAME}"

# --- Redis ---
if ! redis-cli ping >/dev/null 2>&1; then
  sudo redis-server /etc/redis/redis.conf --daemonize yes --supervised no
fi

# --- MongoDB ---
if ! mongosh --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1; then
  sudo mkdir -p /var/lib/mongodb /var/log/mongodb
  sudo chown -R mongodb:mongodb /var/lib/mongodb /var/log/mongodb
  sudo -u mongodb mongod --config /etc/mongod.conf --fork
fi

# --- Readiness checks ---
for _ in $(seq 1 30); do
  redis-cli ping >/dev/null 2>&1 && break
  sleep 1
done
for _ in $(seq 1 30); do
  mongosh --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1 && break
  sleep 1
done

redis-cli ping >/dev/null
mongosh --quiet --eval 'db.runCommand({ping:1})' >/dev/null
echo "start.sh complete: PostgreSQL (${PG_VER}), Redis and MongoDB are up; database '${DB_NAME}' ready."
