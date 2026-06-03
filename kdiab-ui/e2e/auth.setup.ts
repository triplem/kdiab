import { test as setup } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const authFile = path.join(__dirname, '.auth/sarah.json')

setup('authenticate as sarah', async ({ page }) => {
  await page.goto('/')
  await page.waitForLoadState('domcontentloaded')

  const loginBtn = page.locator('button').filter({ hasText: /log.?in/i }).first()
  await loginBtn.waitFor({ state: 'visible', timeout: 10_000 })
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15_000 }),
    loginBtn.click(),
  ])

  await page.locator('#username').fill('sarah')
  await page.locator('#password').fill('password')
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20_000 }),
    page.locator('[type=submit]').click(),
  ])

  // Wait until the app shell is visible — glucose hero tile or nav element
  await page.waitForSelector('[class*="hero"], [class*="glucose"], nav a, [role="navigation"] a', {
    state: 'visible',
    timeout: 15_000,
  })

  await page.context().storageState({ path: authFile })
})
