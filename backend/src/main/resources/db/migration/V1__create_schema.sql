-- Reference data: one currency per country, and an auditable USD reference rate used only for
-- cross-country reporting. Salaries themselves are never converted or rewritten.
CREATE TABLE country (
    code      TEXT    NOT NULL PRIMARY KEY CHECK (length(code) = 2),
    name      TEXT    NOT NULL UNIQUE,
    currency  TEXT    NOT NULL CHECK (length(currency) = 3),
    usd_rate  REAL    NOT NULL CHECK (usd_rate > 0)
);

-- Salary is stored as whole units of the country's currency (SQLite has no exact decimal type).
-- Timestamps are epoch milliseconds; dates are ISO-8601 text (sortable).
CREATE TABLE employee (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code  TEXT    NOT NULL UNIQUE,
    first_name     TEXT    NOT NULL,
    last_name      TEXT    NOT NULL,
    email          TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    job_title      TEXT    NOT NULL,
    department     TEXT    NOT NULL,
    country_code   TEXT    NOT NULL REFERENCES country (code),
    annual_salary  INTEGER NOT NULL CHECK (annual_salary > 0),
    hire_date      TEXT    NOT NULL,
    version        INTEGER NOT NULL DEFAULT 0,
    created_at     INTEGER NOT NULL,
    created_by     TEXT    NOT NULL,
    updated_at     INTEGER NOT NULL,
    updated_by     TEXT    NOT NULL
);

CREATE INDEX ix_employee_country_department ON employee (country_code, department);
CREATE INDEX ix_employee_department ON employee (department);
CREATE INDEX ix_employee_job_title ON employee (job_title);
CREATE INDEX ix_employee_name ON employee (last_name, first_name);

-- Monotonic counter so employee codes are never reused, even after deletions.
CREATE TABLE employee_code_sequence (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    next_value  INTEGER NOT NULL
);
INSERT INTO employee_code_sequence (id, next_value) VALUES (1, 0);

-- Append-only log of compensation changes. Currency is captured because a relocation changes
-- the currency even when the number stays the same.
CREATE TABLE salary_history (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id        INTEGER NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    previous_salary    INTEGER,
    previous_currency  TEXT,
    new_salary         INTEGER NOT NULL,
    new_currency       TEXT    NOT NULL,
    reason             TEXT    NOT NULL,
    created_at         INTEGER NOT NULL,
    created_by         TEXT    NOT NULL
);

CREATE INDEX ix_salary_history_employee ON salary_history (employee_id, id);

CREATE TABLE app_user (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    username       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash  TEXT    NOT NULL,
    role           TEXT    NOT NULL CHECK (role IN ('HR_MANAGER', 'HR_VIEWER')),
    enabled        INTEGER NOT NULL DEFAULT 1,
    created_at     INTEGER NOT NULL
);
