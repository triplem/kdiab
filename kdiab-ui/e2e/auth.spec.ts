import { test, expect } from '@playwright/test'

test('dashboard loads after OIDC login as sarah', async ({ page }) => {
  await page.goto('/')
  await page.waitForLoadState('networkidle', { timeout: 15_000 })

  // The storageState from auth.setup.ts keeps sarah logged in.
  // Confirm app shell is visible — navigation links present.
  const nav = page.locator('nav a, [role="navigation"] a').first()
  await expect(nav).toBeVisible({ timeout: 10_000 })

  // Glucose hero tile or any dashboard content is present in DOM.
  const hasHero = await page
    .locator('[class*="hero"], [class*="glucose"], [class*="dashboard"]')
    .first()
    .isVisible()
    .catch(() => false)

  // At minimum the page should not be the Keycloak login page.
  const url = page.url()
  expect(url).not.toContain('keycloak')
  expect(url).not.toContain('/realms/')

  if (!hasHero) {
    // Fallback: at least one SVG chart rendered means dashboard content is present.
    const svgCount = await page.locator('svg').count()
    expect(svgCount).toBeGreaterThan(0)
  }
})
