/**
 * Patient guide screenshot capture script.
 *
 * Run against the local podman compose stack with seeded data:
 *   cd kdiab-ui
 *   npx playwright test e2e/auth.setup.ts
 *   npx playwright test e2e/screenshots.spec.ts --project=chromium
 *
 * All PNGs are written to ../../docs/images/patient-guide/ relative to the
 * kdiab-ui directory (i.e. docs/images/patient-guide/ in the repo root).
 *
 * This spec is excluded from the regular CI test run via the `screenshots`
 * project in playwright.config.ts. It is a manual capture script only.
 */

import { test } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(__dirname, '../../docs/images/patient-guide')

function shot(name: string) {
  return path.join(OUT, `${name}.png`)
}

// ---------------------------------------------------------------------------
// Auth bridge: storageState captures localStorage; react-oidc-context uses
// sessionStorage. Copy the OIDC user key back to sessionStorage before each
// page load so the app starts in the authenticated state.
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    // Force English so navTo regex patterns work regardless of sarah's saved locale (DE).
    // Sarah's user-settings API response may later override this to 'de', so navTo
    // calls also use bilingual patterns (/analytics|analyse/i etc.) as a second guard.
    localStorage.setItem('i18nextLng', 'en')
    // Copy OIDC user from localStorage to sessionStorage (react-oidc-context uses sessionStorage)
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith('oidc.user:')) {
        sessionStorage.setItem(key, localStorage.getItem(key) ?? '')
      }
    }
  })
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function waitReady(page: import('@playwright/test').Page) {
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => null)
}

async function navTo(page: import('@playwright/test').Page, label: RegExp | string) {
  // Nav uses <button> elements inside <nav className="tab-nav">, not <a> links
  const btn = page.locator('nav.tab-nav button').filter({ hasText: label }).first()
  await btn.waitFor({ state: 'visible', timeout: 8_000 })
  await btn.click()
  await waitReady(page)
}

// ---------------------------------------------------------------------------
// 1. Login page — captured before auth storage state is applied
// ---------------------------------------------------------------------------

test('01 login page', async ({ browser }) => {
  // Use a fresh context without the auth storage state.
  // After auth.setup.ts runs, Keycloak has a server-side SSO session, so clicking
  // "Log in" auto-completes and returns to the app without showing the credentials form.
  // We capture the pre-login app shell only; the Keycloak form screenshot is skipped.
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await ctx.newPage()
  await page.goto('/')
  await page.waitForLoadState('domcontentloaded')
  const loginBtn = page.locator('button').filter({ hasText: /log.?in/i }).first()
  await loginBtn.waitFor({ state: 'visible', timeout: 10_000 })
  await page.screenshot({ path: shot('login-page'), fullPage: false })
  await ctx.close()
})

// ---------------------------------------------------------------------------
// 2. Dashboard
// ---------------------------------------------------------------------------

test('02 dashboard overview', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await page.screenshot({ path: shot('dashboard-overview'), fullPage: false })
})

test('03 dashboard hero tile', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  // GlucoseHeroTile uses className="card" — no hero/glucose class on the outer element
  const hero = page.locator('.card').first()
  await hero.waitFor({ state: 'visible', timeout: 12_000 })
  const box = await hero.boundingBox()
  if (box) {
    await page.screenshot({
      path: shot('dashboard-hero-tile'),
      clip: { x: 0, y: 0, width: 1280, height: Math.min(380, box.y + box.height + 40) },
    })
  } else {
    await page.screenshot({ path: shot('dashboard-hero-tile'), fullPage: false })
  }
})

test('04 dashboard CGM chart', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  // SVG only renders when backend serves CGM data; screenshot whatever is on screen
  await page.locator('svg').first().waitFor({ state: 'visible', timeout: 12_000 }).catch(() => null)
  await page.screenshot({ path: shot('dashboard-cgm-chart'), fullPage: false })
})

test('05 dashboard basal chart', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  // Scroll down past the CGM chart to reveal the basal chart
  await page.keyboard.press('End')
  await waitReady(page)
  await page.screenshot({ path: shot('dashboard-basal-chart'), fullPage: false })
})

test('06 dashboard quick log buttons', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  const quickLog = page.locator('[class*="quick-log"], [aria-label*="quick"], [class*="QuickLog"]').first()
  const visible = await quickLog.isVisible().catch(() => false)
  if (visible) {
    await quickLog.scrollIntoViewIfNeeded()
    await page.screenshot({ path: shot('dashboard-quick-log'), fullPage: false })
  } else {
    await page.screenshot({ path: shot('dashboard-quick-log'), fullPage: false })
  }
})

// ---------------------------------------------------------------------------
// 3. Navigation bar
// ---------------------------------------------------------------------------

test('07 navigation bar', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  const nav = page.locator('nav, [role="navigation"]').first()
  await nav.waitFor({ state: 'visible', timeout: 8_000 })
  const box = await nav.boundingBox()
  if (box) {
    await page.screenshot({
      path: shot('nav-bar'),
      clip: { x: 0, y: box.y, width: 1280, height: Math.min(box.height + 10, 120) },
    })
  } else {
    await page.screenshot({ path: shot('nav-bar'), fullPage: false })
  }
})

// ---------------------------------------------------------------------------
// 4. Measures
// ---------------------------------------------------------------------------

test('08 add measure modal', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /measures|messungen/i)
  // The add button is "+ Measures" (primary button in main, not the "Log out" header button)
  const addBtn = page.locator('main button.primary').first()
  const addBtnVisible = await addBtn.isVisible().catch(() => false)
  if (addBtnVisible) {
    await addBtn.click()
    // Wait for the modal (role="dialog") to appear
    await page.waitForSelector('[role="dialog"]', { state: 'visible', timeout: 8_000 }).catch(() => null)
  }
  await page.screenshot({ path: shot('add-measure-modal'), fullPage: false })
  await page.keyboard.press('Escape')
})

test('09 measure list', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /measures|messungen/i)
  await page.screenshot({ path: shot('measure-list'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 5. Treatments
// ---------------------------------------------------------------------------

test('10 add treatment modal', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /treatments|behandlungen/i)
  // The add button is "+ Treatments" (primary button in main)
  const addBtn = page.locator('main button.primary').first()
  const addBtnVisible = await addBtn.isVisible().catch(() => false)
  if (addBtnVisible) {
    await addBtn.click()
    await page.waitForSelector('[role="dialog"]', { state: 'visible', timeout: 8_000 }).catch(() => null)
  }
  await page.screenshot({ path: shot('add-treatment-modal'), fullPage: false })
  await page.keyboard.press('Escape')
})

test('11 treatment list', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /treatments|behandlungen/i)
  await page.screenshot({ path: shot('treatment-list'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 6. Dose Calculator
// ---------------------------------------------------------------------------

test('12 dose calculator form', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /dose.?calc|dosis/i)
  await page.screenshot({ path: shot('dose-calculator'), fullPage: false })
})

test('13 dose calculator result', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /dose.?calc|dosis/i)
  // Fill in a sample calculation
  const bgInput = page.locator('input[type="number"]').first()
  const bgVisible = await bgInput.isVisible().catch(() => false)
  if (bgVisible) {
    await bgInput.fill('140')
    const calcBtn = page.locator('button').filter({ hasText: /calculat/i }).first()
    const calcVisible = await calcBtn.isVisible().catch(() => false)
    if (calcVisible) {
      await calcBtn.click()
      await waitReady(page)
    }
  }
  await page.screenshot({ path: shot('dose-calculator-result'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 7. Food Database
// ---------------------------------------------------------------------------

test('14 food database search', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  // Food DB tab only appears when VITE_FOOD_DATABASE_ENABLED=true
  const foodBtn = page.locator('nav.tab-nav button').filter({ hasText: /food.?db/i }).first()
  const foodBtnVisible = await foodBtn.isVisible().catch(() => false)
  if (foodBtnVisible) {
    await foodBtn.click()
    await waitReady(page)
  }
  await page.screenshot({ path: shot('food-db-search'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 8. Analytics
// ---------------------------------------------------------------------------

test('15 analytics HbA1c', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  // HbA1cCard uses className="card" — wait up to 15s for data; screenshot whatever loaded
  const hba1c = page.locator('.chart-section .card').first()
  await hba1c.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => null)
  const box = await hba1c.boundingBox().catch(() => null)
  if (box) {
    await page.screenshot({
      path: shot('analytics-hba1c'),
      clip: { x: 0, y: Math.max(0, box.y - 10), width: 1280, height: box.height + 20 },
    })
  } else {
    await page.screenshot({ path: shot('analytics-hba1c'), fullPage: false })
  }
})

test('16 analytics TIR', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  const tir = page.locator('[class*="tir"], [class*="TimeInRange"], [class*="time-in"]').first()
  await tir.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => null)
  await page.screenshot({ path: shot('analytics-tir'), fullPage: false })
})

test('17 analytics AGP chart', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  await page.waitForSelector('svg', { state: 'visible', timeout: 15_000 }).catch(() => null)
  await page.screenshot({ path: shot('analytics-agp'), fullPage: false })
})

test('18 analytics timeframe selector', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  const selector = page.locator('[class*="timeframe"], [class*="date-range"], select').first()
  const visible = await selector.isVisible().catch(() => false)
  if (visible) {
    await selector.scrollIntoViewIfNeeded()
    await page.screenshot({ path: shot('analytics-timeframe'), fullPage: false })
  } else {
    await page.screenshot({ path: shot('analytics-timeframe'), fullPage: false })
  }
})

test('19 analytics basal average', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  await page.keyboard.press('End')
  await waitReady(page)
  await page.screenshot({ path: shot('analytics-basal-avg'), fullPage: false })
})

test('20 analytics bolus average', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /analytics|analyse/i)
  await page.keyboard.press('End')
  await waitReady(page)
  await page.screenshot({ path: shot('analytics-bolus-avg'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 10. Report
// ---------------------------------------------------------------------------

test('22 report date range controls', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /report|bericht/i)
  await page.screenshot({ path: shot('report-date-range'), fullPage: false })
})

test('23 report page selection panel', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /report|bericht/i)
  const panel = page.locator('[class*="page-select"], [class*="PageSelect"], fieldset').first()
  const visible = await panel.isVisible().catch(() => false)
  if (visible) {
    await panel.scrollIntoViewIfNeeded()
  }
  await page.screenshot({ path: shot('report-page-selection'), fullPage: false })
})

test('24 report generated view', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /report|bericht/i)
  const generateBtn = page.locator('button').filter({ hasText: /generate/i }).first()
  const generateVisible = await generateBtn.isVisible().catch(() => false)
  if (generateVisible) await generateBtn.click()
  // Wait for report content to appear
  await page.waitForSelector('[class*="report"], [class*="Report"], [data-testid*="report"]', {
    state: 'visible',
    timeout: 30_000,
  }).catch(() => null)
  await waitReady(page)
  await page.screenshot({ path: shot('report-generated'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 11. Profiles
// ---------------------------------------------------------------------------

test('25 profile list', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /profiles?/i)
  await page.screenshot({ path: shot('profile-list'), fullPage: false })
})

test('26 profile editor', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /profiles?/i)
  const editBtn = page.locator('button').filter({ hasText: /edit/i }).first()
  const visible = await editBtn.isVisible().catch(() => false)
  if (visible) {
    await editBtn.click()
    await waitReady(page)
  }
  await page.screenshot({ path: shot('profile-editor'), fullPage: false })
})

test('27 profile history', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  // Navigate to Profile History via nav
  const histLink = page.locator('nav a, [role="navigation"] a').filter({ hasText: /history/i }).first()
  const visible = await histLink.isVisible().catch(() => false)
  if (visible) {
    await histLink.click()
    await waitReady(page)
  } else {
    await navTo(page, /profiles?/i)
  }
  await page.screenshot({ path: shot('profile-history'), fullPage: false })
})

test('28 profile diff view', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /profiles?/i)
  // Look for a proposed/pending profile diff link
  const diffLink = page.locator('a, button').filter({ hasText: /review|diff|proposed/i }).first()
  const visible = await diffLink.isVisible().catch(() => false)
  if (visible) {
    await diffLink.click()
    await waitReady(page)
  }
  await page.screenshot({ path: shot('profile-diff'), fullPage: false })
})

// ---------------------------------------------------------------------------
// 12. Settings
// ---------------------------------------------------------------------------

test('29 settings form', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /settings|einstellungen/i)
  await page.screenshot({ path: shot('settings-form'), fullPage: false })
})

test('30 settings alarm threshold validation error', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await waitReady(page)
  await navTo(page, /settings|einstellungen/i)
  // Enter an invalid alarm threshold order to trigger the validation error
  const urgentLowInput = page.locator('input[name*="urgentLow"], input[id*="urgentLow"]').first()
  const lowInput = page.locator('input[name*="alarmLow"], input[id*="alarmLow"]').first()
  const urgentLowVisible = await urgentLowInput.isVisible().catch(() => false)
  const lowVisible = await lowInput.isVisible().catch(() => false)
  if (urgentLowVisible && lowVisible) {
    // Set urgent low higher than low to trigger validation error
    const currentLow = await lowInput.inputValue()
    await urgentLowInput.fill(currentLow)
    const saveBtn = page.locator('button[type="submit"], button').filter({ hasText: /save/i }).first()
    const saveVisible = await saveBtn.isVisible().catch(() => false)
    if (saveVisible) {
      await saveBtn.click()
      await waitReady(page)
    }
  }
  await page.screenshot({ path: shot('settings-alarm-error'), fullPage: false })
})
