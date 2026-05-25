import { test, expect } from '@playwright/test'

test.describe('dose calculator', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // Navigate to Dose Calc tab
    const doseTab = page
      .locator('a, button, [role="tab"]')
      .filter({ hasText: /dose/i })
      .first()
    await doseTab.waitFor({ state: 'visible', timeout: 8_000 })
    await doseTab.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
  })

  test('entering glucose and carbs produces a bolus recommendation', async ({ page }) => {
    // Find glucose input
    const glucoseInput = page
      .locator('input[type="number"]')
      .first()
    await glucoseInput.waitFor({ state: 'visible', timeout: 8_000 })
    await glucoseInput.fill('180')

    // Find carbs input (second number input or labelled "carbs")
    const carbsInput = page
      .locator('input[type="number"]')
      .nth(1)
    const carbsVisible = await carbsInput.isVisible({ timeout: 3_000 }).catch(() => false)
    if (carbsVisible) {
      await carbsInput.fill('40')
    }

    // Submit the form
    const calcBtn = page
      .locator('button[type="submit"], button.primary')
      .filter({ hasText: /calc|recommend|compute/i })
      .first()
    const calcExists = await calcBtn.isVisible({ timeout: 3_000 }).catch(() => false)

    if (calcExists) {
      await calcBtn.click()
    } else {
      // Try any submit-style button
      await page.locator('button[type="submit"]').first().click()
    }

    // A recommendation result should appear — contains at least one digit
    const result = page
      .locator('[class*="result"], [class*="recommendation"], [class*="dose"]')
      .first()
    await expect(result).toBeVisible({ timeout: 8_000 })
    const text = await result.innerText()
    expect(/\d/.test(text)).toBe(true)

    // No error banners
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('[object Object]')
  })
})
