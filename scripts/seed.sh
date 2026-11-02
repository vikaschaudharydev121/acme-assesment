#!/usr/bin/env bash
# Seed script: loads the deterministic 10,000-employee data set into the database, then exits.
# Runs Flyway migrations first, so it also works against a brand-new database file.
#
#   scripts/seed.sh                 # seed only if the employee table is empty
#   scripts/seed.sh --reset         # wipe employees + history, then reseed (demo environments only)
#   APP_SEED_COUNT=500 scripts/seed.sh
#   APP_DB_PATH=/path/to/salary.db scripts/seed.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/salary-management.jar"
RESET=false
[[ "${1:-}" == "--reset" ]] && RESET=true

if [[ ! -f "$JAR" ]]; then
  echo "Building backend jar..."
  (cd "$ROOT/backend" && ./mvnw -q -DskipTests package)
fi

# No web server is started, so a throwaway JWT secret is enough to satisfy config validation.
APP_JWT_SECRET="${APP_JWT_SECRET:-seed-only-$(openssl rand -hex 24)}" \
java -jar "$JAR" \
  --spring.main.web-application-type=none \
  --spring.profiles.active=prod \
  --app.seed.enabled=true \
  --app.seed.reset="$RESET" \
  --app.seed.employee-count="${APP_SEED_COUNT:-10000}"
