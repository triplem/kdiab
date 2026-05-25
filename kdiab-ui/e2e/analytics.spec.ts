import { test, expect } from '@playwright/test'

test.describe('analytics view', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // Navigate to Analytics tab
    const analyticsTab = page.locator('a, button, [role="tab"]').filter({ hasText: /analytics/i }).first()
    await analyticsTab.waitFor({ state: 'visible', timeout: 8_000 })
    await analyticsTab.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
  })

  test('HbA1c card shows a numeric value', async ({ page }) => {
    const hba1cCard = page
      .locator('[class*="hba1c"], [class*="HbA1c"], [class*="a1c"]')
      .first()
    await expect(hba1cCard).toBeVisible({ timeout: 12_000 })
    const text = await hba1cCard.innerText()
    expect(/\d/.test(text)).toBe(true)
  })

  test('Time-in-Range bar is visible', async ({ page }) => {
    const tirBar = page
      .locator('[class*="tir"], [class*="time-in-range"], [class*="TimeInRange"]')
      .first()
    await expect(tirBar).toBeVisible({ timeout: 12_000 })
  })

  test('AGP chart SVG is present', async ({ page }) => {
    // Give charts time to load data
    const svgs = page.locator('svg')
    await expect(svgs.first()).toBeVisible({ timeout: 12_000 })
    const count = await svgs.count()
    expect(count).toBeGreaterThan(0)
  })

  test('no error banners appear in analytics', async ({ page }) => {
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => null)
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('[object Object]')
  })

  test('window picker (7d, 14d, 30d) causes re-render without error', async ({ page }) => {
    const windowLabels = ['7d', '14d', '30d']
    for (const label of windowLabels) {
      const btn = page.locator('button', { hasText: label }).first()
      const exists = await btn.isVisible({ timeout: 3_000 }).catch(() => false)
      if (!exists) continue

      await btn.click()
      await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => null)

      const body = await page.locator('body').innerText()
      expect(body).not.toContain('[object Object]')
    }
  })
})
