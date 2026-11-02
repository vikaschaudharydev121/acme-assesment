# Performance Considerations

## Measured (10,000 employees, production Docker image, Apple-silicon laptop)

100 sequential requests per endpoint after warm-up, authenticated as `hr.manager`:

| Endpoint | p50 | p95 |
|----------|-----|-----|
| `GET /employees?size=20` (default page) | 4.9 ms | 7.4 ms |
| `GET /employees?size=100&q=smith&sort=annualSalary,desc` | 9.9 ms | 13.1 ms |
| `GET /employees?country=IN&department=Engineering&page=20` | 4.2 ms | 4.9 ms |
| `GET /insights/summary` | 12.2 ms | 15.9 ms |
| `GET /insights/by-country` | 11.5 ms | 15.1 ms |
| `GET /insights/by-department` | 13.6 ms | 17.0 ms |
| `GET /insights/by-job-title?country=IN` | 6.0 ms | 8.0 ms |

Seeding 10,000 employees plus 10,000 history rows: **~110 ms**. Container memory: ~520 MiB.
Target from the requirements (p95 < 300 ms) is met with more than 15× headroom.

## What makes it fast

- **Server-side paging, sorting and filtering.** The browser never receives more than 100 rows (`max-page-size: 100`).
  Paging always appends `id` to the sort, so pages are stable and never duplicate or skip rows.
- **Indexes** on `(country_code, department)`, `department`, `job_title`, `(last_name, first_name)`; unique indexes on
  `email` and `employee_code`.
- **No N+1 queries.** Country/currency/rate come from an in-memory `CountryCatalog` (8 rows, migration-managed), so mapping
  a page of employees never joins or re-queries.
- **Narrow projection for analytics.** Insights load only `(country, department, job_title, salary)` via a JPQL constructor
  expression: no entities, no persistence-context overhead, no personal data.
- **SQLite WAL mode.** Readers never block the writer and vice versa; `busy_timeout` absorbs brief write contention.
- **Frontend:** lazy-loaded routes (initial bundle ≈ 95 kB transferred), `switchMap` cancels superseded searches,
  300 ms debounce on the search box, reference data cached with `shareReplay`.
- **JDBC batch inserts** (batches of 1,000 in one transaction) for the seed instead of 10k ORM saves.

## Known costs

- The free-text search uses `LIKE '%term%'` across name, email and code, which cannot use a B-tree index. At 10k rows the full scan
  is ~5 ms. Beyond ~1M rows: SQLite FTS5 (or Postgres `pg_trgm`).
- Insights aggregate in-process on every request (O(n log n) for the medians).

## Scaling path (if ACME grows 10–100×)

1. **≤ 100k employees:** no change needed. Insights stay ~100 ms. Add `Cache-Control: private, max-age=60` or a
   Caffeine cache evicted on writes if dashboards are hammered.
2. **100k–1M:** move to PostgreSQL (same Flyway SQL, minor type tweaks). Use `percentile_cont` in SQL so only aggregates
   leave the database. Use trigram/FTS indexes for search.
3. **Beyond, or many concurrent HR users:** precompute aggregates (materialised views refreshed on write, or an event-driven
   read model). Run stateless API instances behind a load balancer (JWT already stateless), with login throttling in Redis.
