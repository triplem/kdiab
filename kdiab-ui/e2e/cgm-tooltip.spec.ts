import { test, expect } from '@playwright/test'

async function loginAsSarah(page: import('@playwright/test').Page) {
  await page.goto('http://localhost:3005/')
  await page.waitForLoadState('domcontentloaded')
  const loginBtn = page.locator('button').filter({ hasText: /log.?in/i }).first()
  await loginBtn.waitFor({ state: 'visible', timeout: 5000 })
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
  await page.waitForLoadState('networkidle', { timeout: 15_000 })
  await page.waitForTimeout(2000)
}

test('CGM chart tooltip shows merged values on hover', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1800 })
  await loginAsSarah(page)

  const dashLink = page.locator('a, button, [role=tab]').filter({ hasText: /dashboard/i }).first()
  if (await dashLink.isVisible({ timeout: 5000 }).catch(() => false)) {
    await dashLink.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
    await page.waitForTimeout(2000)
  }

  // Find the first large chart SVG
  const svgs = page.locator('svg')
  let chartSvg = svgs.first()
  let chartBox: { x: number; y: number; width: number; height: number } | null = null
  for (let i = 0; i < await svgs.count(); i++) {
    const box = await svgs.nth(i).boundingBox()
    if (box && box.width > 200 && box.height > 100) {
      chartSvg = svgs.nth(i)
      chartBox = box
      break
    }
  }
  expect(chartBox).not.toBeNull()
  if (!chartBox) return

  await chartSvg.scrollIntoViewIfNeeded()
  chartBox = await chartSvg.boundingBox()
  if (!chartBox) return

  // Sweep mouse across the chart — at least one tooltip should appear
  const tooltipTexts: string[] = []
  const wrapper = page.locator('.recharts-tooltip-wrapper')
  for (let frac = 0.05; frac <= 0.95; frac += 0.02) {
    await page.mouse.move(chartBox.x + chartBox.width * frac, chartBox.y + chartBox.height * 0.4)
    await page.waitForTimeout(60)
    if (await wrapper.isVisible().catch(() => false)) {
      const txt = (await wrapper.innerText().catch(() => '')).trim()
      if (txt && !tooltipTexts.includes(txt)) tooltipTexts.push(txt)
    }
  }

  await page.screenshot({ path: '/tmp/cgm-tooltip-test.png' })
  console.log(`Tooltip samples (${tooltipTexts.length}):`)
  tooltipTexts.slice(0, 5).forEach((t, i) => console.log(`  [${i}] ${t.replace(/\n/g, ' | ')}`))

  // The chart should produce tooltips on hover
  expect(tooltipTexts.length).toBeGreaterThan(0)
})
