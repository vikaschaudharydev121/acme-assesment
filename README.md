# ACME Salary Management

Web application that replaces ACME's salary spreadsheets. An **HR Manager** can manage pay for 10,000 employees
across 8 countries and answer *"how do we pay people?"*: medians and ranges by country, department and job title.

**Stack:** Java 21 · Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) · SQLite + Flyway ·
Angular 22 + Angular Material · Docker · GitHub Actions

| | |
|---|---|
| 📄 Requirements (one page) | [docs/01-requirements.md](docs/01-requirements.md) |
| 🏗️ Architecture, data model, API, security design | [docs/02-architecture.md](docs/02-architecture.md) |
| ⚖️ Decisions & trade-offs | [docs/03-decisions-and-tradeoffs.md](docs/03-decisions-and-tradeoffs.md) |
| ⚡ Performance | [docs/04-performance.md](docs/04-performance.md) |
| 🤖 How AI was used (incl. defects caught) | [docs/05-ai-usage.md](docs/05-ai-usage.md) |
| ✅ Test strategy & 10× run report | [docs/06-test-report.md](docs/06-test-report.md) |
| 🎬 Demo video (2½ min) & script | [docs/demo/acme-salary-demo.webm](docs/demo/acme-salary-demo.webm) · [docs/07-demo-script.md](docs/07-demo-script.md) |

## Features

- **Sign-in with roles:** `HR_MANAGER` (read/write) and `HR_VIEWER` (read-only), enforced on the server.
- **Employee directory:** server-side search (name, email, code), country/department filters, sorting and paging.
- **Add / edit / delete** with validation, duplicate-email detection and optimistic locking (no lost updates).
- **Salary history:** every change of salary or currency (relocation) requires a reason and is recorded with who and when.
- **Pay insights:** org KPIs; pay by country in local currency and USD; department and job-title comparison with
  25th/50th/75th percentiles (Excel `PERCENTILE.INC`-compatible).
- **Seed script:** deterministic 10,000 employees.

## Run it

### Option A: Docker (production profile)

```bash
cp .env.example .env
# fill in APP_JWT_SECRET (openssl rand -base64 48) and the two passwords (>= 12 chars)
docker compose up -d --build
open http://localhost:8080          # sign in with the credentials from .env
```

The production profile refuses to start without a strong `APP_JWT_SECRET`. The container runs as a non-root user with a
read-only root filesystem, no Linux capabilities and a health check. Data persists in the `salary-data` volume.

### Option B: Local development

Prerequisites: JDK 21+, Node 22+.

```bash
# API on :8080. The dev profile seeds 10,000 employees and creates demo users.
cd backend && ./mvnw spring-boot:run

# UI on :4200 with hot reload; /api is proxied to :8080
cd frontend && npm ci && npm start
```

Dev-only demo accounts: `hr.manager` / `Manager@12345` and `hr.viewer` / `Viewer@12345`.
These exist **only** in the `dev` profile.

### Single jar

```bash
scripts/build.sh                                         # tests + UI + jar with UI bundled
APP_JWT_SECRET=$(openssl rand -base64 48) APP_MANAGER_PASSWORD=... \
  java -jar backend/target/salary-management.jar --spring.profiles.active=prod
```

## Seed script

```bash
scripts/seed.sh                  # seed 10,000 employees if the table is empty (idempotent)
scripts/seed.sh --reset          # wipe and reseed (demo environments only)
APP_SEED_COUNT=500 scripts/seed.sh
```

The seed is deterministic (fixed random seed and dates): every environment gets identical data.
Salaries follow a model of job-title base pay × country labour-cost factor, in local currency, with ±15% individual variation.

## Tests

```bash
cd backend && ./mvnw test                    # 73 tests, ~15 s, coverage in target/site/jacoco
cd frontend && npx ng test --watch=false     # 31 tests, ~1 s
scripts/smoke-test.sh                        # 42 end-to-end checks against a running instance
```

## Project layout

```
backend/                    Spring Boot API (package-by-feature)
  src/main/java/com/acme/salary/
    auth/                   login/logout/me, login throttling, user bootstrap
    security/               filter chain, JWT cookie, CSRF, roles, problem responses
    employee/               entity, service, search specifications, REST API
    salary/                 append-only salary history
    insights/               pay analytics (pure SalaryStats + read-only projection)
    reference/              countries, currencies, USD reference rates
    seed/                   deterministic data generator + seeder
    common/                 error handling (RFC 7807), paging, auditing, SQLite helpers
  src/main/resources/db/migration/   Flyway SQL
frontend/                   Angular 22 + Material
  src/app/core/             auth service, guards, interceptors, API models
  src/app/features/         login, employees, insights
  src/app/shared/           reusable UI (money pipe, confirm dialog)
docs/                       requirements, design, decisions, performance, AI usage, tests
scripts/                    build, seed, smoke test
```

## API overview

All endpoints are under `/api/v1` and return RFC 7807 `application/problem+json` on error. Full table in
[docs/02-architecture.md](docs/02-architecture.md#api-v1).

`POST /auth/login` · `POST /auth/logout` · `GET /auth/me` ·
`GET|POST /employees` · `GET|PUT|DELETE /employees/{id}` · `GET /employees/{id}/salary-history` ·
`GET /reference/countries|departments|job-titles` ·
`GET /insights/summary|by-country|by-department|by-job-title`
