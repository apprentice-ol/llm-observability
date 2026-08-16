const { chromium } = require('playwright');
const fs = require('fs');

const OO_URL = (process.env.OO_URL || 'http://host.docker.internal:5080').replace(/\/$/, '');
const OO_USER = process.env.OO_USER || 'admin@openobserve.io';
const OO_PASS = process.env.OO_PASS || 'OpenObserve@2026';
const LF_URL = (process.env.LF_URL || 'http://host.docker.internal:3000').replace(/\/$/, '');
const LF_USER = process.env.LF_USER || 'admin@rageoffer.com';
const LF_PASS = process.env.LF_PASS || 'RagAdmin@2026';
const OUT_DIR = process.env.OUT_DIR || '/tmp/screenshots';
const LF_TRACE_ID = process.env.LF_TRACE_ID || '05af3961df9bd12a6772483238e7c92d';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function captureOpenObserve(page) {
  await page.goto(`${OO_URL}/web/`, { waitUntil: 'networkidle' });
  await page.waitForSelector('input[data-test="login-user-id-field"], input[type="email"]');
  await page.fill('input[data-test="login-user-id-field"], input[type="email"]', OO_USER);
  await page.fill('input[data-test="login-password-field"], input[type="password"]', OO_PASS);
  await page.click('button[data-test="login-sign-in"], button[type="submit"]');
  await sleep(5000);
  await page.goto(`${OO_URL}/web/traces?stream=default&from=now-1h&to=now`, {
    waitUntil: 'networkidle',
  });
  await sleep(6000);
  await page.screenshot({ path: `${OUT_DIR}/openobserve-traces.png` });
}

async function captureLangfuse(page) {
  await page.goto(`${LF_URL}/auth/sign-in`, { waitUntil: 'networkidle' });
  await page.waitForSelector('input[name="email"], input[type="email"]');
  await page.fill('input[name="email"], input[type="email"]', LF_USER);
  await page.fill('input[name="password"], input[type="password"]', LF_PASS);
  await page.click('button[type="submit"]');
  await page.waitForFunction(() =>
    location.pathname.includes('/project/') ||
    !!document.querySelector('a[href*="/project/"]'), { timeout: 30000 });
  const currentPath = new URL(page.url()).pathname;
  const projectId = currentPath.includes('/project/')
    ? currentPath.split('/')[2]
    : await page.evaluate(() => {
        const link = document.querySelector('a[href*="/project/"]');
        return link ? link.getAttribute('href').split('/')[2] : null;
      });
  await page.goto(`${LF_URL}/project/${projectId}/traces`, { waitUntil: 'networkidle' });
  await sleep(6000);
  await page.screenshot({ path: `${OUT_DIR}/langfuse-traces.png` });
  if (LF_TRACE_ID) {
    await page.goto(`${LF_URL}/project/${projectId}/traces/${LF_TRACE_ID}`, {
      waitUntil: 'networkidle',
    });
    await sleep(8000);
    await page.screenshot({ path: `${OUT_DIR}/langfuse-trace.png` });
  } else {
    console.warn('LF_TRACE_ID not set, skip trace detail screenshot');
  }
}

(async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_PATH ||
      '/ms-playwright/chromium-1226/chrome-linux64/chrome',
    args: ['--no-sandbox'],
  });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();
  try {
    await captureOpenObserve(page);
    await captureLangfuse(page);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
