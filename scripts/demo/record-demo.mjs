// Records a captioned walkthrough of the running app as the HR Manager, saved as a .webm video.
//
//   node scripts/demo/record-demo.mjs
//
// Env: BASE_URL (default http://localhost:8080), MANAGER_PASSWORD / VIEWER_PASSWORD (default: read
// from .env), OUT (default docs/demo/acme-salary-demo.webm), PLAYWRIGHT_MODULE (path to a
// playwright-compatible package if `playwright` is not installed locally), CHROMIUM_PATH (browser binary).
//
// The demo creates one employee and deletes it again, so it leaves the data as it found it.

import { mkdirSync, readFileSync, renameSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');

const env = readDotEnv(resolve(ROOT, '.env'));
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const MANAGER_PASSWORD = process.env.MANAGER_PASSWORD ?? env.APP_MANAGER_PASSWORD ?? 'Manager@12345';
const VIEWER_PASSWORD = process.env.VIEWER_PASSWORD ?? env.APP_VIEWER_PASSWORD ?? 'Viewer@12345';
const OUT = resolve(ROOT, process.env.OUT ?? 'docs/demo/acme-salary-demo.webm');
const SIZE = { width: 1280, height: 720 };

const DEMO_EMAIL = `priya.venkatesh.demo${Date.now() % 100000}@acme.example`;

// ---------------------------------------------------------------------------------------------
// Overlay: a visible cursor, click ripple and caption banner, injected into every page.
// ---------------------------------------------------------------------------------------------
const OVERLAY = () => {
  const install = () => {
    if (document.getElementById('demo-cursor') || !document.body) return;
    const style = document.createElement('style');
    style.textContent = `
      #demo-cursor { position: fixed; left: 0; top: 0; width: 22px; height: 22px; margin: -11px 0 0 -11px;
        border-radius: 50%; background: rgba(255, 193, 7, .55); border: 2px solid rgba(0,0,0,.55);
        z-index: 2147483647; pointer-events: none; transition: transform .55s cubic-bezier(.4,0,.2,1); }
      #demo-cursor.click { animation: demo-click .35s ease-out; }
      @keyframes demo-click { 0% { box-shadow: 0 0 0 0 rgba(255,193,7,.9); } 100% { box-shadow: 0 0 0 22px rgba(255,193,7,0); } }
      #demo-caption { position: fixed; left: 50%; bottom: 84px; transform: translateX(-50%); max-width: 1000px;
        padding: 12px 22px; border-radius: 12px; background: rgba(15, 23, 42, .88); color: #fff;
        font: 500 20px/1.35 Roboto, system-ui, sans-serif; text-align: center; z-index: 2147483646;
        pointer-events: none; opacity: 0; transition: opacity .3s; box-shadow: 0 6px 24px rgba(0,0,0,.25); }
      #demo-caption.show { opacity: 1; }
      #demo-caption.top { bottom: auto; top: 76px; }
      #demo-caption small { display: block; font-size: 15px; font-weight: 400; opacity: .8; margin-top: 4px; }`;
    document.head.appendChild(style);
    const cursor = document.createElement('div');
    cursor.id = 'demo-cursor';
    cursor.style.transform = 'translate(640px, 360px)';
    const caption = document.createElement('div');
    caption.id = 'demo-caption';
    document.body.append(cursor, caption);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
};

async function caption(page, title, detail = '', holdMs = 0, { top = false } = {}) {
  await page.evaluate(
    ([t, d, atTop]) => {
      const el = document.getElementById('demo-caption');
      if (!el) return;
      el.innerHTML = '';
      el.append(document.createTextNode(t));
      if (d) {
        const small = document.createElement('small');
        small.textContent = d;
        el.append(small);
      }
      el.classList.toggle('show', !!t);
      el.classList.toggle('top', atTop);
    },
    [title, detail, top],
  );
  if (holdMs) await page.waitForTimeout(holdMs);
}

async function pointAt(page, locator) {
  await locator.scrollIntoViewIfNeeded();
  const box = await locator.boundingBox();
  if (!box) return;
  const x = Math.round(box.x + box.width / 2);
  const y = Math.round(box.y + box.height / 2);
  await page.evaluate(([cx, cy]) => {
    const c = document.getElementById('demo-cursor');
    if (c) c.style.transform = `translate(${cx}px, ${cy}px)`;
  }, [x, y]);
  await page.waitForTimeout(600);
}

async function click(page, locator) {
  await pointAt(page, locator);
  await page.evaluate(() => {
    const c = document.getElementById('demo-cursor');
    if (!c) return;
    c.classList.remove('click');
    void c.offsetWidth;
    c.classList.add('click');
  });
  await locator.click();
  await page.waitForTimeout(350);
}

async function type(page, locator, text, delay = 55) {
  await click(page, locator);
  await locator.fill('');
  await locator.pressSequentially(text, { delay });
}

async function chooseOption(page, combobox, optionName) {
  await click(page, combobox);
  await click(page, page.getByRole('option', { name: optionName }));
}

/** Full-screen title card drawn over the current app page (keeps the recording at native scale). */
async function card(page, html, holdMs) {
  await page.evaluate((content) => {
    const host = document.createElement('div');
    host.id = 'demo-card';
    host.innerHTML = `<style>
        #demo-card{position:fixed;inset:0;z-index:2147483647;display:grid;place-items:center;
          font-family:Roboto,system-ui,sans-serif;color:#fff;
          background:linear-gradient(135deg,#0b3d91,#1565c0 55%,#1e88e5)}
        #demo-card .wrap{max-width:1000px;padding:40px} #demo-card h1{font-size:54px;margin:0 0 12px;font-weight:700}
        #demo-card h2{font-size:26px;margin:0 0 28px;font-weight:400;opacity:.9}
        #demo-card ul{font-size:22px;line-height:1.7;margin:0;padding-left:26px}
        #demo-card .grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 48px}
        #demo-card .muted{color:#fff;opacity:.75;font-size:18px;margin-top:28px}
        #demo-card .k{font-size:44px;font-weight:700}
        #demo-card .stat{background:rgba(255,255,255,.12);border-radius:14px;padding:16px 20px}
      </style><div class="wrap">${content}</div>`;
    document.body.appendChild(host);
  }, html);
  await page.waitForTimeout(holdMs);
  await page.evaluate(() => document.getElementById('demo-card')?.remove());
}

// ---------------------------------------------------------------------------------------------
// The walkthrough
// ---------------------------------------------------------------------------------------------
async function demo(page) {
  // 0. Title, drawn over the sign-in page
  await page.goto(BASE_URL);
  await page.getByLabel('Username').waitFor();
  await card(page, `
    <h1>ACME Salary Management</h1>
    <h2>Managing pay for 10,000 employees across 8 countries — for the HR Manager</h2>
    <div class="grid"><ul><li>Employee directory &amp; editing</li><li>Salary history with reasons</li><li>Pay insights by country, department, job title</li></ul>
    <ul><li>Spring Boot 3.5 · Java 21 · SQLite</li><li>Angular 22 · Angular Material</li><li>Secure by default · Docker</li></ul></div>`, 6500);

  // 1. Sign in
  await caption(page, 'Sign in', 'Every page and API call requires an authenticated HR user', 1500);
  await type(page, page.getByLabel('Username'), 'hr.manager');
  await type(page, page.getByLabel('Password', { exact: true }), 'not-my-password');
  await click(page, page.getByRole('button', { name: 'Sign in' }));
  await page.getByRole('alert').waitFor();
  await caption(page, 'A wrong password gets a generic message',
    'No hint whether the account exists · repeated failures lock the username for 15 minutes', 3500);
  await type(page, page.getByLabel('Password', { exact: true }), MANAGER_PASSWORD, 25);
  await click(page, page.getByRole('button', { name: 'Sign in' }));
  await page.waitForURL(/\/employees/);
  await page.locator('tr.mat-mdc-row').first().waitFor();
  await page.waitForFunction(() => /\d,\d{3} total/.test(document.querySelector('.page-header p')?.textContent ?? ''));

  // 2. Directory
  await caption(page, 'The employee directory — all 10,000 employees',
    'Salaries in each employee’s local currency, with a USD equivalent for comparison', 4500);
  await caption(page, 'Paging, sorting and filtering happen on the server', 'Fast at any size — the browser only ever loads one page');
  await click(page, page.getByRole('button', { name: 'Annual salary' }));
  await click(page, page.getByRole('button', { name: 'Annual salary' }));
  await page.waitForTimeout(1500);
  await caption(page, 'Search by name, email or employee code');
  await type(page, page.getByTestId('search'), 'smith', 120);
  await page.waitForFunction(() => document.querySelector('.page-header p')?.textContent?.includes('matching'));
  await page.waitForTimeout(1500);
  await caption(page, 'Narrow down by country and department');
  await chooseOption(page, page.getByRole('combobox', { name: 'Country' }), 'India');
  await page.waitForTimeout(2200);
  await click(page, page.getByRole('button', { name: /Clear/ }));
  await page.waitForTimeout(1200);

  // 3. Add employee
  await caption(page, 'Adding a new hire', 'The form validates as you go — the server re-checks everything');
  await click(page, page.getByTestId('add-employee'));
  const add = page.getByRole('dialog');
  await add.waitFor();
  await click(page, add.getByRole('button', { name: 'Add employee' }));
  await caption(page, 'Required fields are flagged before anything is sent', '', 2500);
  await caption(page, 'Adding a new hire');
  await type(page, add.getByLabel('First name'), 'Priya');
  await type(page, add.getByLabel('Last name'), 'Venkatesh');
  await type(page, add.getByLabel('Work email'), DEMO_EMAIL, 25);
  await type(page, add.getByRole('combobox', { name: 'Job title' }), 'Software Engineer', 35);
  await page.keyboard.press('Escape');
  await type(page, add.getByRole('combobox', { name: 'Department' }), 'Engineering', 35);
  await page.keyboard.press('Escape');
  await chooseOption(page, add.getByRole('combobox', { name: 'Country' }), /India/);
  await caption(page, 'The currency follows the country — no INR salary in Germany by mistake');
  await type(page, add.getByLabel('Hire date'), '2024-04-15', 0).catch(async () => add.getByLabel('Hire date').fill('2024-04-15'));
  await type(page, add.getByLabel('Annual gross salary'), '2500000', 80);
  await page.waitForTimeout(1500);
  await click(page, add.getByRole('button', { name: 'Add employee' }));
  await page.locator('mat-snack-bar-container').waitFor();
  await caption(page, 'Saved with a new employee code — and its first salary-history entry', '', 3000);
  await add.waitFor({ state: 'detached' });

  // 4. Raise with mandatory reason
  await type(page, page.getByTestId('search'), DEMO_EMAIL, 15);
  await page.waitForFunction(() => document.querySelectorAll('tr.mat-mdc-row').length === 1);
  await page.waitForTimeout(800);
  await caption(page, 'Giving Priya a raise');
  await click(page, page.getByRole('button', { name: 'Edit Priya Venkatesh' }));
  const edit = page.getByRole('dialog');
  await edit.waitFor();
  await type(page, edit.getByLabel('Annual gross salary'), '2800000', 80);
  await caption(page, 'The change and its percentage are shown before saving',
    'Any change of salary or currency must give a reason', 3000);
  await click(page, edit.getByRole('button', { name: 'Save changes' }));
  await caption(page, 'Saving without a reason is blocked', '', 2200);
  await type(page, edit.getByLabel('Reason for compensation change'), 'Promotion to Software Engineer II', 40);
  await page.waitForTimeout(800);
  await click(page, edit.getByRole('button', { name: 'Save changes' }));
  await edit.waitFor({ state: 'detached' });
  await page.waitForTimeout(1200);

  // 5. Salary history
  await caption(page, 'Every pay change is on record: who, when, why, before and after');
  await click(page, page.getByRole('button', { name: 'Salary history for Priya Venkatesh' }));
  const history = page.getByRole('dialog');
  await history.locator('.timeline li').nth(1).waitFor();
  await page.waitForTimeout(4500);
  await caption(page, 'If two HR users edit the same person, the second save is refused',
    'Optimistic locking prevents silently overwriting a colleague’s change', 4000);
  await click(page, history.getByRole('button', { name: 'Close' }));

  // 6. Insights
  await caption(page, 'Now: how does ACME pay people?');
  await click(page, page.getByRole('link', { name: /Insights/ }));
  await page.getByTestId('kpi-headcount').filter({ hasText: /\d/ }).waitFor();
  await page.locator('section.panel tr.mat-mdc-row').first().waitFor();
  await page.waitForTimeout(800);
  await caption(page, 'Headcount, payroll, median pay and the middle 50% — across the whole org',
    'Medians, not just averages — a few executives can’t skew the picture', 5000);
  await page.mouse.wheel(0, 330);
  await caption(page, 'Pay by country, in local currency and in USD',
    'Percentiles match Excel’s PERCENTILE.INC, so HR can reconcile with their old spreadsheets', 5500, { top: true });
  await page.mouse.wheel(0, 460);
  await page.waitForTimeout(600);
  await caption(page, 'Compare departments or job titles, organisation-wide or within one country', '', 3000);
  await caption(page, '“What do we pay a Software Engineer in India?”');
  await click(page, page.getByText('Job title', { exact: true }));
  await chooseOption(page, page.locator('section.panel').nth(1).getByRole('combobox', { name: 'Country' }), /India/);
  await type(page, page.getByTestId('group-filter'), 'Software Engineer', 60);
  await page.waitForTimeout(800);
  await pointAt(page, page.locator('section.panel').nth(1).locator('tr.mat-mdc-row').last().locator('td').first());
  await caption(page, '“What do we pay a Software Engineer in India?”',
    'Answered in seconds: headcount, median and pay range, in rupees', 6000, { top: true });

  // 7. Clean up the demo employee
  await click(page, page.getByRole('link', { name: /Employees/ }));
  await page.locator('tr.mat-mdc-row').first().waitFor();
  await type(page, page.getByTestId('search'), DEMO_EMAIL, 10);
  await page.waitForFunction(() => document.querySelectorAll('tr.mat-mdc-row').length === 1);
  await caption(page, 'Removing a record asks for confirmation first');
  await click(page, page.getByRole('button', { name: 'Delete Priya Venkatesh' }));
  await page.waitForTimeout(1500);
  await click(page, page.getByRole('dialog').getByRole('button', { name: 'Delete' }));
  await page.waitForTimeout(1800);

  // 8. Read-only viewer
  await caption(page, 'Signing out…');
  await click(page, page.getByRole('button', { name: 'Sign out' }));
  await page.waitForURL(/\/login/);
  await caption(page, 'A second role: HR Viewer — e.g. a finance partner who needs answers, not edit rights');
  await type(page, page.getByLabel('Username'), 'hr.viewer');
  await type(page, page.getByLabel('Password', { exact: true }), VIEWER_PASSWORD, 25);
  await click(page, page.getByRole('button', { name: 'Sign in' }));
  await page.waitForURL(/\/employees/);
  await page.locator('tr.mat-mdc-row').first().waitFor();
  await pointAt(page, page.getByTestId('current-user'));
  await caption(page, 'Viewers see everything but cannot add, edit or delete',
    'Enforced by the server on every request — not just hidden buttons', 5000);
  await pointAt(page, page.getByRole('button', { name: /^Salary history/ }).first());
  await page.waitForTimeout(1200);
  await caption(page, '');
  await click(page, page.getByRole('button', { name: 'Sign out' }));
  await page.waitForURL(/\/login/);

  // 9. Closing card
  await card(page, `
    <h1>Built to be trusted</h1>
    <div class="grid" style="margin-bottom:28px">
      <div class="stat"><div class="k">73</div>backend tests · 95% line coverage</div>
      <div class="stat"><div class="k">31</div>frontend tests</div>
      <div class="stat"><div class="k">42</div>end-to-end checks incl. attack probes</div>
      <div class="stat"><div class="k">10×</div>every suite re-run — zero flaky results</div>
    </div>
    <ul><li>HttpOnly session cookie + CSRF protection · BCrypt · login throttling · role-based access</li>
    <li>Strict Content-Security-Policy · non-root, read-only container · fail-fast on weak secrets</li></ul>
    <div class="muted">Docs: requirements · architecture · decisions · performance · AI usage · test report — see the repository README</div>`, 9000);
}

// ---------------------------------------------------------------------------------------------
const recordDir = resolve(dirname(OUT), '.recording');
rmSync(recordDir, { recursive: true, force: true });
mkdirSync(recordDir, { recursive: true });

// CHROMIUM_PATH lets the recorder reuse an already-installed Chrome/Chromium build.
const browser = await chromium.launch(
  process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {},
);
const context = await browser.newContext({
  viewport: SIZE,
  deviceScaleFactor: 1,
  recordVideo: { dir: recordDir, size: SIZE },
});
await context.addInitScript(OVERLAY);
const page = await context.newPage();
let failure;
try {
  await demo(page);
} catch (err) {
  failure = err;
  await page.screenshot({ path: resolve(dirname(OUT), 'demo-failure.png') }).catch(() => {});
} finally {
  const video = page.video();
  await context.close();
  await browser.close();
  const recorded = await video.path();
  renameSync(recorded, OUT);
  rmSync(recordDir, { recursive: true, force: true });
}
if (failure) {
  console.error('Demo failed:', failure.message);
  process.exit(1);
}
console.log(`Demo video written to ${OUT}`);

function readDotEnv(path) {
  try {
    return Object.fromEntries(
      readFileSync(path, 'utf8')
        .split('\n')
        .filter((line) => /^[A-Z_]+=/.test(line))
        .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]),
    );
  } catch {
    return {};
  }
}
