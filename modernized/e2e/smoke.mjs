// E2E smoke test against the REAL backend (no MSW mocks):
// sign-in -> account view -> transaction list.
// Requires: backend on :3000, frontend (built with VITE_USE_MOCKS=false) on
// BASE_URL (default http://localhost:5173), and a seeded database.
// Run: node e2e/smoke.mjs   (playwright + chromium must be installed)
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';

const browser = await chromium.launch();
const page = await browser.newPage();
page.setDefaultTimeout(15000);

try {
  // Sign in as USER0001/PASSWORD
  await page.goto(`${BASE}/signon`);
  await page.fill('#userId', 'USER0001');
  await page.fill('#password', 'PASSWORD');
  await page.click('button[type=submit]');
  await page.waitForURL('**/menu');
  console.log('ok: sign-in');

  // Account view
  await page.goto(`${BASE}/accounts/view`);
  await page.fill('#accountId', '00000000001');
  await page.click('button:has-text("Search")');
  await page.waitForSelector('text=Current Balance');
  console.log('ok: account view');

  // Transaction list
  await page.goto(`${BASE}/transactions`);
  await page.waitForSelector('table.data tbody tr');
  console.log('ok: transaction list');

  console.log('SMOKE PASSED');
  await browser.close();
} catch (err) {
  console.error('SMOKE FAILED:', err);
  await browser.close();
  process.exit(1);
}
