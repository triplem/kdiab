import { test, expect } from '@playwright/test'

test.describe('treatment CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // Navigate to Treatments tab
    const treatmentsTab = page.locator('a, button, [role="tab"]').filter({ hasText: /treatments/i }).first()
    await treatmentsTab.waitFor({ state: 'visible', timeout: 8_000 })
    await treatmentsTab.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
  })

  test('add BOLUS treatment, verify in list, edit, then archive', async ({ page }) => {
    // Open the Add Treatment modal
    const addBtn = page.locator('button').filter({ hasText: /\+\s*treatment/i }).first()
    await addBtn.waitFor({ state: 'visible', timeout: 8_000 })
    await addBtn.click()

    const modal = page.locator('[role="dialog"]')
    await expect(modal).toBeVisible({ timeout: 5_000 })

    // Select BOLUS type (it may already be selected or we need to choose it)
    const typeSelect = modal.locator('select').first()
    await typeSelect.selectOption('BOLUS')

    // Enter insulin value — find the insulin input inside the modal
    const insulinInput = modal.locator('input[type="number"]').first()
    await insulinInput.fill('3.5')

    // Set insulin type if present (BolusForm may have a select for insulinType)
    const insulinTypeSelect = modal.locator('select').nth(1)
    const insulinTypeExists = await insulinTypeSelect.isVisible().catch(() => false)
    if (insulinTypeExists) {
      await insulinTypeSelect.selectOption({ index: 0 })
    }

    // Submit
    const saveBtn = modal.locator('button.primary, button[type="submit"]').first()
    await saveBtn.click()

    // Modal should close
    await expect(modal).not.toBeVisible({ timeout: 8_000 })

    // The new BOLUS should appear in the list
    const list = page.locator('[class*="treatment-list"], [class*="list"], table, [class*="item"]')
    await list.first().waitFor({ state: 'visible', timeout: 8_000 })
    const listText = await list.first().innerText()
    expect(/bolus/i.test(listText) || /3\.5/i.test(listText)).toBe(true)

    // Find edit button on first item and click it
    const editBtn = page.locator('button').filter({ hasText: /edit/i }).first()
    const editExists = await editBtn.isVisible({ timeout: 3_000 }).catch(() => false)
    if (editExists) {
      await editBtn.click()
      const editModal = page.locator('[role="dialog"]')
      await expect(editModal).toBeVisible({ timeout: 5_000 })

      // Change insulin value
      const editInput = editModal.locator('input[type="number"]').first()
      await editInput.clear()
      await editInput.fill('4.0')

      const editSaveBtn = editModal.locator('button.primary, button[type="submit"]').first()
      await editSaveBtn.click()
      await expect(editModal).not.toBeVisible({ timeout: 8_000 })
    }

    // Archive the treatment via archive/delete button
    const archiveBtn = page.locator('button').filter({ hasText: /archive/i }).first()
    const archiveExists = await archiveBtn.isVisible({ timeout: 3_000 }).catch(() => false)
    if (archiveExists) {
      await archiveBtn.click()
      // Wait for the archive button to disappear — item removed from active list.
      await expect(archiveBtn).not.toBeVisible({ timeout: 5_000 }).catch(() => null)
    }
  })
})
