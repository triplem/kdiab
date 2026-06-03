import { test as setup } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const authFile = path.join(__dirname, '.auth/sarah.json')

setup('authenticate as sarah', async ({ page }) => {
  await page.goto('/')
  await page.waitForLoadState('domcontentloaded')

  // Click the Log in button — triggers OIDC redirect to Keycloak
  const loginBtn = page.locator('button').filter({ hasText: /log.?in/i }).first()
  await loginBtn.waitFor({ state: 'visible', timeout: 10_000 })
  await loginBtn.click()

  // Wait for the Keycloak login form (URL contains /realms/ or /auth/)
  await page.waitForURL(/realms|\/auth\//, { timeout: 20_000 })

  await page.locator('#username').fill('sarah')
  await page.locator('#password').fill('password')
  await page.locator('[type=submit]').click()

  // Wait for the redirect back to the app (URL no longer contains keycloak/realms)
  await page.waitForURL(/localhost/, { timeout: 30_000 })
  await page.waitForLoadState('networkidle', { timeout: 30_000 })

  // Confirm the app shell is rendered
  await page.waitForSelector(
    '[class*="hero"], [class*="glucose"], nav a, [role="navigation"] a, [data-testid], main',
    { state: 'visible', timeout: 30_000 },
  )

  await page.context().storageState({ path: authFile })
})
