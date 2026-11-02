# Decisions & Trade-offs

Short ADR-style records of the choices that shaped the system: what was chosen, what was rejected, and why.

| # | Decision | Alternatives considered | Why |
|---|----------|------------------------|-----|
| D1 | **Spring Boot 3.5 / Java 21 + Angular 22 + Angular Material** | Spring Boot 4.0, React | The brief asks for Angular with Java. Boot 3.5 is the mature, widely deployed line, with a stable Spring Security 6 API I can verify. Java 21 is the LTS baseline (it builds on the JDK 25 installed here). |
| D2 | **SQLite** (WAL, `foreign_keys=on`) via Flyway | PostgreSQL, H2 | The brief suggests SQLite. 10k rows is tiny, and a single-file DB means zero ops. Tests run on the *same engine* as production (no H2 dialect drift). Trade-off: one writer at a time, which fits a small HR team. Moving to Postgres = new JDBC URL + dialect; the migrations are plain SQL. |
| D3 | **Salary as integer whole units in local currency** | `DECIMAL`, minor units (cents), store USD | SQLite has no exact decimal, so integers avoid float drift. Annual salaries are whole numbers in practice. Storing local currency keeps the number HR actually negotiated, with USD derived at read time. |
| D4 | **Currency derived from country** | Currency column per employee | A single source of truth makes "an INR salary for someone in Germany" impossible. Relocation is modelled as a compensation change that needs a reason. |
| D5 | **Static, versioned FX reference rates** | Live FX API | Deterministic, auditable, offline-capable. HR comparisons should not move with daily FX noise. A rate change is a migration, so it is reviewed and traceable. |
| D6 | **Append-only salary history, written in the same transaction** | Generic audit log, DB triggers | HR's real question is "why does this person earn X?". A typed history with a mandatory reason answers it directly. Service-layer `Propagation.MANDATORY` guarantees atomicity with the change. |
| D7 | **Optimistic locking (`@Version`) + client-sent version** | Last-write-wins, pessimistic locks | Two HR users editing the same record get a clear 409 instead of silently losing a raise. No lock is held while a dialog is open. |
| D8 | **JWT in an HttpOnly, Secure, SameSite=Strict cookie + CSRF token** | JWT in localStorage + `Authorization` header; server sessions | localStorage tokens are readable by any XSS. A cookie needs CSRF protection, which Spring + Angular support natively via the cookie-to-header pattern. Stateless JWT keeps horizontal scaling and IdP migration simple. |
| D9 | **Register the JWT filter manually instead of `oauth2ResourceServer()`** | The DSL | Found during testing: the DSL exempts bearer-token requests from CSRF. That is correct for headers, but a CSRF hole when the token comes from a cookie. See commit `fix(security): enforce CSRF…`. |
| D10 | **Two roles: HR_MANAGER (write), HR_VIEWER (read)** | Single role; fine-grained permissions | Matches the persona plus a realistic read-only stakeholder, proves RBAC end-to-end, and avoids premature permission modelling. |
| D11 | **Insights computed in-process from a narrow projection** | SQL `GROUP BY`, materialised views | SQLite has no `PERCENTILE_CONT`; medians and quartiles are what HR needs (means are skewed by executives). At 10k rows the projection + aggregation costs ~10 ms. The maths lives in a pure, unit-tested class. Scaling path: see `04-performance.md`. |
| D12 | **Percentiles match Excel's `PERCENTILE.INC`** | Nearest-rank | The HR team is migrating *from* Excel. Identical numbers build trust and make reconciliation trivial. |
| D13 | **Single deployable (UI bundled in the jar)** | Separate UI host + CORS | Same origin means no CORS, `SameSite=Strict` cookies work, and there is one artefact to deploy, version and roll back. |
| D14 | **Package-by-feature** (`employee`, `salary`, `insights`, `auth`, `security`, `reference`, `seed`, `common`) | Layered packages (`controllers/`, `services/`…) | Each capability is cohesive and boundaries are visible. `insights` reads through a read-only projection repository and cannot mutate employees. |
| D15 | **Deterministic seed (fixed random seed and dates), JDBC batch** | Faker at random, JPA saves | Identical data on every machine makes demos, screenshots and bug reports reproducible. Batch insert loads 10k rows + history in ~100 ms. |
| D16 | **Hard delete with cascaded history** | Soft delete / termination status | Simplest correct model for v1. The confirmation dialog states the consequence. A termination workflow (keep history, exclude from headcount) is the natural v2 step. |

## Known limitations (accepted for v1)

- **Logout does not revoke the JWT server-side.** The cookie is cleared, but a stolen token stays valid until it expires (8 h). Mitigations: short TTL, HttpOnly cookie. Next step: a `jti` deny-list or short access tokens with refresh.
- **Login throttling is per-username and in-memory.** An attacker can deliberately lock an account for 15 min, and the state is per instance. Next step: per-IP rate limiting at the edge plus a shared store (Redis) when running more than one instance.
- **Sorting by salary in a multi-country list compares local amounts.** It is useful within one country (the common case, via the filter). A USD-normalised sort would need a computed column.
- **Bootstrap users only.** No user-management UI or SSO. The JWT/role design is IdP-ready (swap the decoder for an issuer URI).
