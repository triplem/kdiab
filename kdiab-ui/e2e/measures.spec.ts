import { test, expect } from '@playwright/test'

test.describe('measure CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // Navigate to Measures tab
    const measuresTab = page.locator('a, button, [role="tab"]').filter({ hasText: /measures/i }).first()
    await measuresTab.waitFor({ state: 'visible', timeout: 8_000 })
    await measuresTab.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
  })

  test('add BGM reading and verify it appears in the list', async ({ page }) => {
    // Open the Add Measure modal
    const addBtn = page.locator('button').filter({ hasText: /\+\s*measure/i }).first()
    await addBtn.waitFor({ state: 'visible', timeout: 8_000 })
    await addBtn.click()

    const modal = page.locator('[role="dialog"]')
    await expect(modal).toBeVisible({ timeout: 5_000 })

    // BGM should already be selected by default; confirm
    const typeSelect = modal.locator('select').first()
    await typeSelect.selectOption('BGM')

    // Enter a valid blood glucose value (120 mg/dL)
    const glucoseInput = modal.locator('input[type="number"]').first()
    await glucoseInput.fill('120')

    // Submit
    const saveBtn = modal.locator('button.primary, button[type="submit"]').first()
    await saveBtn.click()

    // Modal should close without error
    await expect(modal).not.toBeVisible({ timeout: 8_000 })

    // The new BGM reading should appear in the list
    const list = page.locator('[class*="measure-list"], [class*="list"], table, [class*="item"]').first()
    await list.waitFor({ state: 'visible', timeout: 8_000 })
    const listText = await list.innerText()
    // List should contain "BGM" or the glucose value
    expect(/BGM|120/i.test(listText)).toBe(true)
  })
})
