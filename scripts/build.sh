#!/usr/bin/env bash
# Builds the Angular UI, bundles it into the Spring Boot jar and runs all tests.
# Output: backend/target/salary-management.jar (single deployable: API + UI).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKIP_TESTS="${SKIP_TESTS:-false}"

echo "==> Building UI"
cd "$ROOT/frontend"
npm ci --no-audit --no-fund
if [[ "$SKIP_TESTS" != "true" ]]; then
  npx ng test --watch=false
fi
npx ng build

echo "==> Bundling UI into backend"
STATIC="$ROOT/backend/src/main/resources/static"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R "$ROOT/frontend/dist/frontend/browser/." "$STATIC/"

echo "==> Building backend"
cd "$ROOT/backend"
if [[ "$SKIP_TESTS" == "true" ]]; then
  ./mvnw -q -DskipTests package
else
  ./mvnw -q package
fi

echo "==> Done: $ROOT/backend/target/salary-management.jar"
