# Requirements — ACME Salary Management (v1)

## Goal
Replace the spreadsheets ACME's HR team uses to manage salaries for ~10,000 employees in several
countries with a secure web application. The **HR Manager** must be able to (1) keep salary data
correct and current, and (2) answer "how do we pay people?" questions in seconds rather than hours
of pivot tables.

## Persona & key jobs-to-be-done
**HR Manager** (primary, read/write) and **HR Viewer** (secondary, read-only — e.g. a finance partner
or HRBP who needs answers but must not edit pay).

| # | Job | Success measure |
|---|-----|-----------------|
| J1 | Find any employee quickly | Search by name / email / employee code, filter by country & department, < 1 s on 10k rows |
| J2 | Add, correct, or remove an employee record | Validated form; duplicate emails rejected; concurrent edits never silently overwrite each other |
| J3 | Change someone's salary and know *why* it changed later | Every salary change is recorded with old/new value, reason, who and when |
| J4 | Answer pay questions | Org headcount & payroll; per-country min/median/avg/max; per-department comparison; "what do we pay a *Software Engineer* in *India*?" |
| J5 | Trust that the data is safe | Authenticated access only, role-based permissions, brute-force protection, audit columns |

## Scope & features (in)
1. **Authentication & roles** — username/password login, `HR_MANAGER` (CRUD) and `HR_VIEWER` (read-only).
2. **Employee directory** — server-side paginated, sortable, searchable, filterable table.
3. **Employee CRUD** — create / edit / delete with validation and optimistic locking.
4. **Salary history** — immutable log of every salary change with a mandatory reason.
5. **Pay insights** — org summary, by-country, by-department (optionally within a country), and job-title-in-country lookup.
   Salaries are stored in the **local currency** of the employee's country; cross-country figures are normalised to **USD** using a reference rate table.
6. **Seed script** — deterministic generator for 10,000 realistic employees across 8 countries.
7. **Deployable artefact** — single Docker image (API + UI), health check, CI pipeline.

## Deliberately out of scope (and why)
| Left out | Reasoning |
|----------|-----------|
| Payroll run, tax, payslips, benefits | Different product (payroll engine); the ask is *managing and understanding* salary data. |
| Live FX rates | Adds an external dependency and makes numbers move day-to-day; a versioned reference table is auditable and deterministic. Easy to swap later. |
| Bulk Excel/CSV import & export | High value for migration, but needs mapping/validation UX done properly; v1 proves the core model first. Seed script covers data loading. |
| SSO / OIDC, MFA, user-management UI | Enterprise IdP integration is environment-specific. Architecture (JWT, role claims) is IdP-ready; users are bootstrapped from configuration. |
| Approval workflows for raises, compensation bands / pay-equity analytics | Valuable v2 features that depend on org policy decisions not given in the brief. |
| Org hierarchy / managers, performance data | Not needed to answer pay questions in v1; avoids modelling ambiguity. |
| Multi-tenancy, i18n | Single organisation, English-speaking HR team assumed. |

## Non-functional requirements
- **Security:** OWASP Top-10 aware — HttpOnly+SameSite JWT cookie, CSRF protection, BCrypt, RBAC on every endpoint,
  input validation, no stack traces to clients, strict security headers / CSP, secrets only via environment.
- **Performance:** list & insight endpoints p95 < 300 ms at 10k employees on a laptop.
- **Quality:** fast deterministic unit + slice tests run on every commit in CI; schema managed by migrations.
- **Operability:** one container, health endpoint, structured config per environment.

## Assumptions
Salaries are annual, gross, in whole units of the local currency. One country ⇒ one currency.
An employee belongs to exactly one country, department and job title at a time.
