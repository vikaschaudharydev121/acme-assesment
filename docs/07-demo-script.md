# Demo Script (≈ 5 minutes)

**Recorded demo:** [`docs/demo/acme-salary-demo.webm`](demo/acme-salary-demo.webm) (2 min 26 s, 1280×720, captioned, no audio).
It is generated from the running app by [`scripts/demo/record-demo.mjs`](../scripts/demo/record-demo.mjs), so it can be
re-recorded after any UI change:

```bash
npm i -D playwright && npx playwright install chromium   # once, or point PLAYWRIGHT_MODULE / CHROMIUM_PATH at an existing install
node scripts/demo/record-demo.mjs                        # reads passwords from .env; creates and deletes one demo employee
```

The table below is the script for a narrated, live recording.

A walkthrough for recording the video demo, told from the HR Manager's point of view.

**Setup:** `cp .env.example .env`, fill in the secrets, `docker compose up -d --build`, open http://localhost:8080.

| # | Show | Say |
|---|------|-----|
| 1 | Login page; try a wrong password | "Generic error: we don't reveal which accounts exist. Five failures lock the username for 15 minutes." |
| 2 | Sign in as `hr.manager` → Employees | "10,000 employees across 8 countries, paged on the server. Each salary is in local currency, with a USD equivalent for comparison." |
| 3 | Type "smith", filter Country = India, sort by Annual salary | "Search, filters and sort all run server-side; this stays fast at any size." |
| 4 | **Add employee** → submit empty, then fill in an Indian engineer | "Validation mirrors the server. Currency follows the country automatically." |
| 5 | **Edit** → change salary | "A pay change shows the delta (+12%) and *requires* a reason." Save. |
| 6 | **History** icon | "Every change is recorded: who, when, why, before and after." |
| 7 | Open the same employee in two tabs, save both | "The second save gets a conflict instead of silently overwriting the first." |
| 8 | **Insights** | "Headcount, payroll, median and middle 50% across the org. Pay by country in local currency." |
| 9 | Compare groups → Job title → India → filter "Software Engineer" | "That answers 'what do we pay a software engineer in India?' with the median and range, in rupees." |
| 10 | Sign out; sign in as `hr.viewer` | "Viewers can see everything but cannot change anything. The server enforces this, not just the UI." |
| 11 | Narrow the browser to phone width | "Responsive layout for HR on the go." |
| 12 | Terminal: `scripts/smoke-test.sh` | "42 end-to-end checks, including the attacks we defend against." |
