# Test Strategy & Report

## Test pyramid

| Layer | Tool | Count | What it proves |
|-------|------|-------|----------------|
| Backend unit | JUnit 5, AssertJ, Mockito | 42 | Business rules in isolation: salary statistics (Excel-compatible), compensation-change rules, optimistic-lock checks, sort whitelist, LIKE escaping, login lockout over time (controllable clock), deterministic seed generator |
| Backend integration | Spring Boot Test + MockMvc on a throwaway SQLite file | 27 | Real HTTP contract, validation errors, RBAC (401/403), mass-assignment rejection, security headers, JWT login and cookie flags, insights numbers, seeding |
| Backend real-HTTP | `java.net.http` client with a cookie jar on a random port | 4 | Behaves like a browser: CSRF cookie issuance, **cookie-authenticated write without CSRF → 403**, stale cookie does not block login |
| Frontend unit | Vitest (Angular CLI) | 31 | Auth state, route guard, open-redirect protection, error mapping (no 5xx leakage), money formatting, compensation-change rule |
| End-to-end (API) | `scripts/smoke-test.sh` (curl) | 42 checks | The HR Manager journey and attacker probes against a running instance or container |
| End-to-end (UI) | Headless Chromium (Playwright) | 2 journeys | Manager: login → search → create → raise with reason → history → delete → insights → logout. Viewer: read-only UI, API write blocked, token not JS-readable, phone layout |

All automated tests are deterministic: fixed random seeds, fixed dates, an injected `Clock`, a fresh database per test context
and per-test data cleanup. No sleeps or network calls to third parties.

## 10× repeat run (2026-10-31)

Each suite was executed ten consecutive times to detect flakiness.

| Suite | Runs | Result per run | Flaky runs |
|-------|------|----------------|-----------|
| Backend (`./mvnw test`) | 10 | 73 passed, 0 failed, 0 errors | 0 |
| Frontend (`ng test`) | 10 | 31 passed | 0 |
| End-to-end smoke vs production container (`scripts/smoke-test.sh`) | 10 | 42/42 checks passed | 0 |

Backend coverage (JaCoCo): **95.5% lines, 75.7% branches**. Report: `backend/target/site/jacoco/index.html`.

## Manual / exploratory QA as the HR Manager (headless browser, screenshots reviewed)

| Scenario | Result |
|----------|--------|
| Wrong password shows a generic error; correct password lands on Employees | ✅ |
| Directory shows 10,000 employees with local currency + USD, sorted by name | ✅ |
| Search "smith" → 172 matches; country/department filters; clear filters | ✅ |
| Add employee: empty submit shows 8 "Required" errors; valid submit creates `EMP-010001` with INR currency | ✅ |
| Duplicate email shows the server's message under the email field | ✅ |
| Edit salary ₹25,00,000 → ₹28,00,000 shows "+12.0%"; saving without a reason is blocked | ✅ |
| Salary history shows initial salary + promotion with actor, time and reason | ✅ |
| Delete with confirmation; row disappears | ✅ |
| Insights KPIs, pay by country, and "Software Engineer in India" (529 people, median ₹28,94,400) | ✅ |
| Sign out; deep link to /insights redirects to login with `returnUrl` | ✅ |
| Viewer: no Add/Edit/Delete buttons; direct API DELETE → 403; `ACCESS_TOKEN` not visible to `document.cookie` | ✅ |
| Phone width (390 px): no horizontal page scroll, sign-out reachable | ✅ after fix (was broken; see `05-ai-usage.md`) |

## Running the tests

```bash
cd backend && ./mvnw test                     # backend unit + integration (+ coverage report)
cd frontend && npx ng test --watch=false      # frontend unit
scripts/smoke-test.sh                         # end-to-end against http://localhost:8080
```
