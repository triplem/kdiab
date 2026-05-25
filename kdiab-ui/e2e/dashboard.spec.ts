import { test, expect } from '@playwright/test'

test.describe('dashboard golden path', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3005/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })
  })

  test('glucose hero tile shows a numeric value', async ({ page }) => {
    // Hero tile or glucose summary card visible
    const hero = page.locator('[class*="hero"], [class*="glucose-value"], [class*="current-glucose"]').first()
    await expect(hero).toBeVisible({ timeout: 10_000 })
    const text = await hero.innerText()
    // Should contain at least one digit
    expect(/\d/.test(text)).toBe(true)
  })

  test('window picker buttons are present and clickable', async ({ page }) => {
    const windowLabels = ['2h', '4h', '6h', '12h', '24h']
    for (const label of windowLabels) {
      const btn = page.locator('button', { hasText: label }).first()
      await expect(btn).toBeVisible({ timeout: 8_000 })
    }

    // Click each window button and confirm the chart SVG is still present
    for (const label of windowLabels) {
      const btn = page.locator('button', { hasText: label }).first()
      await btn.click()
      await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => null)
      const svg = page.locator('svg').first()
      await expect(svg).toBeVisible({ timeout: 8_000 })
    }
  })

  test('trend chart SVG is rendered', async ({ page }) => {
    const svg = page.locator('svg').first()
    await expect(svg).toBeVisible({ timeout: 10_000 })
    const box = await svg.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.width).toBeGreaterThan(100)
    expect(box!.height).toBeGreaterThan(50)
  })

  test('no [object Object] or error banners on dashboard', async ({ page }) => {
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('[object Object]')

    const errorBanner = page.locator('[class*="error"]:not([class*="validation"]):not([class*="form"])').first()
    const hasError = await errorBanner.isVisible().catch(() => false)
    expect(hasError).toBe(false)
  })
})
