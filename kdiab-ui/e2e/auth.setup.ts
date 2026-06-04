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

  // Wait for the redirect back to the app on port 3005
  await page.waitForURL(/localhost:3005/, { timeout: 30_000 })
  await page.waitForLoadState('networkidle', { timeout: 30_000 })

  // Confirm the authenticated dashboard is rendered (nav buttons only appear when logged in)
  await page.waitForSelector('nav.tab-nav button', { state: 'visible', timeout: 30_000 })

  // react-oidc-context stores the OIDC user in sessionStorage, which Playwright's
  // storageState() does not capture. Copy it to localStorage so the screenshot tests
  // can inject it back via addInitScript before each page load.
  await page.evaluate(() => {
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)
      if (key?.startsWith('oidc.user:')) {
        localStorage.setItem(key, sessionStorage.getItem(key) ?? '')
      }
    }
  })

  await page.context().storageState({ path: authFile })
})
