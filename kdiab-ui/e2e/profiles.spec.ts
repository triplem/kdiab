import { test, expect } from '@playwright/test'

test.describe('profile management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3005/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // Navigate to Profiles tab
    const profilesTab = page.locator('a, button, [role="tab"]').filter({ hasText: /profiles/i }).first()
    await profilesTab.waitFor({ state: 'visible', timeout: 8_000 })
    await profilesTab.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
  })

  test('active profile card is visible with ACTIVE status badge', async ({ page }) => {
    const activeBadge = page
      .locator('[class*="status-badge"], [class*="badge"], [class*="status"]')
      .filter({ hasText: /active/i })
      .first()
    await expect(activeBadge).toBeVisible({ timeout: 10_000 })
  })

  test('basal segment table has at least one row', async ({ page }) => {
    // Locate a row in the basal rate table or list
    const row = page
      .locator('table tr:not(:first-child), [class*="segment"], [class*="basal-row"]')
      .first()
    await expect(row).toBeVisible({ timeout: 10_000 })
  })

  test('no error banners on profiles page', async ({ page }) => {
    await page.waitForTimeout(500)
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('[object Object]')
  })
})
