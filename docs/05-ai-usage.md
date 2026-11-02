# How AI Was Used

The solution was built with an agentic AI coding assistant (Claude Code) running in the IDE with shell access.
The aim was to use AI for **speed on well-understood work** while keeping **humans-in-the-loop style verification**
on everything that matters: every claim was checked by running code, tests, a real browser or a real container.

## Workflow

1. **Requirements first.** The brief was turned into a one-page requirements doc (`01-requirements.md`) with explicit
   non-goals *before* any code, then an architecture/API/security design (`02-architecture.md`).
2. **Vertical slices with incremental commits:** schema → employee CRUD → security → insights → seed → tests → UI → packaging.
   Each slice was compiled and exercised (curl / tests) before the next began. See `git log`.
3. **Verify, don't trust.** After each slice the assistant ran the app and probed it: curl journeys, the full test suite,
   a headless-browser walkthrough with screenshots, and the production Docker image.
4. **Repeatability.** Test suites and the end-to-end smoke test were each run 10× to check for flakiness (`06-test-report.md`).

## Representative prompts / instructions

> Act as a software developer. Build the salary-management application described in the attached brief (Java backend,
> Angular UI, relational DB, seed of 10,000 employees). Make it secure and structured the way large engineering
> organisations structure services. Then act as the product user (HR Manager), test every step, and run the tests 10 times.

Instructions the assistant followed when working:

- "Write the requirements and design down before code; record what is deliberately out of scope and why."
- "Package by feature; keep controllers thin; business rules in services; pure functions for maths so they are unit-testable."
- "Never trust client input: validate DTOs, whitelist sort fields, escape LIKE wildcards, reject unknown JSON properties."
- "Prove security properties with tests that act like an attacker (missing CSRF, viewer writes, tampered tokens, mass assignment)."
- "After building UI, look at it in a real browser at desktop and phone widths before calling it done."

## Where verification caught AI-generated defects

These are the most instructive moments. In each case the first version *looked* right, compiled, and was wrong:

| Defect | How it was caught | Fix |
|--------|------------------|-----|
| **CSRF bypass.** Using `http.oauth2ResourceServer()` with a cookie-based token resolver silently exempted cookie-authenticated writes from CSRF (Spring treats "has bearer token" as CSRF-exempt). | A test expecting 403 got 201; reproduced with curl against the running app (`POST` with cookie and no XSRF header → **201**). | Registered `BearerTokenAuthenticationFilter` manually; added a real-HTTP `BrowserSessionHttpTest` because MockMvc's `jwt()` helper also skips CSRF and could never have caught it. |
| **Phone layout hid "Sign out".** The toolbar overflowed at 390 px and pushed the logout button off-screen. | Headless-browser QA at phone width + screenshot. | Icon-only navigation below 720 px, with aria-labels. |
| **Container crash on start.** sqlite-jdbc extracts its native library to `/tmp`, but the hardened container mounts `/tmp` `noexec`. | Running the real production image with `docker compose`. | Ship the native library in a read-only path at build time rather than weakening `noexec`. |
| **Healthcheck that could never pass.** `/dev/tcp` is a bash feature; the image's `sh` is dash. | Inspected container health state. | Use `bash -c`. |
| **Non-web seed run failed.** The security filter chain required `HttpSecurity`, which does not exist without a web server. | Running `scripts/seed.sh`. | `@ConditionalOnWebApplication` on the filter chain bean. |
| **Surefire `argLine` overrode the JaCoCo agent.** Coverage would silently be empty. | Review of the POM. | `@{argLine}` late-binding. |

## What AI was *not* trusted with

- **Security-relevant behaviour** was only accepted once a test proved it (cookie flags, CSRF, RBAC, headers, lockout,
  generic auth errors, actuator exposure, mass assignment).
- **Numbers shown to HR.** Percentile maths is pinned by tests against known Excel results.
- **Framework-version specifics** (Angular 22 / Spring Boot 3.5 APIs) were validated by compiling and running, not assumed.
