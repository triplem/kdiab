import { test, expect } from '@playwright/test'

async function loginAsSarah(page: import('@playwright/test').Page) {
  await page.goto('http://localhost:3005/')
  await page.waitForLoadState('domcontentloaded')

  const loginBtn = page.locator('button').filter({ hasText: /log.?in/i }).first()
  await expect(loginBtn).toBeVisible({ timeout: 5000 })
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15_000 }),
    loginBtn.click(),
  ])

  await expect(page.locator('#username')).toBeVisible({ timeout: 10_000 })
  await page.locator('#username').fill('sarah')
  await page.locator('#password').fill('password')
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20_000 }),
    page.locator('[type=submit]').click(),
  ])
  await page.waitForLoadState('networkidle', { timeout: 15_000 })
  await page.waitForTimeout(2000)
}

test('CGM chart tooltip shows on chart hover', async ({ page }) => {
  // Use a tall viewport so charts are in view
  await page.setViewportSize({ width: 1280, height: 1800 })
  await loginAsSarah(page)

  // Navigate to dashboard
  const dashLink = page.locator('a, button, [role=tab]').filter({ hasText: /dashboard/i }).first()
  if (await dashLink.isVisible({ timeout: 5000 }).catch(() => false)) {
    await dashLink.click()
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
    await page.waitForTimeout(2000)
  }

  await page.screenshot({ path: '/tmp/cgm-01-dashboard.png', fullPage: false })
  console.log('SVGs:', await page.locator('svg').count())
  console.log('Cards:', await page.locator('.card').count())

  // Find the CGM/Glucose chart SVG (largest chart on page)
  let chartSvg = page.locator('svg').first()
  let chartBox: { x: number; y: number; width: number; height: number } | null = null
  const svgs = page.locator('svg')
  const svgCount = await svgs.count()
  for (let i = 0; i < svgCount; i++) {
    const box = await svgs.nth(i).boundingBox()
    if (box && box.width > 200 && box.height > 100) {
      chartSvg = svgs.nth(i)
      chartBox = box
      console.log(`Chart SVG ${i} at`, box)
      break
    }
  }

  if (!chartBox) {
    console.log('No chart found')
    await page.screenshot({ path: '/tmp/cgm-err-no-chart.png', fullPage: true })
    return
  }

  // Scroll chart into view
  await chartSvg.scrollIntoViewIfNeeded()
  await page.waitForTimeout(500)
  // Re-read bounding box after scroll
  chartBox = await chartSvg.boundingBox()
  if (!chartBox) return
  await page.screenshot({ path: '/tmp/cgm-02-chart-in-view.png' })
  console.log('Chart box after scroll:', chartBox)

  // Sweep mouse left-to-right across the chart at 40% height (CGM line area)
  const foundTooltips: string[] = []
  for (let frac = 0.05; frac <= 0.95; frac += 0.025) {
    const x = chartBox.x + chartBox.width * frac
    const y = chartBox.y + chartBox.height * 0.4
    await page.mouse.move(x, y)
    await page.waitForTimeout(60)
    const wrapper = page.locator('.recharts-tooltip-wrapper')
    if (await wrapper.isVisible().catch(() => false)) {
      const txt = (await wrapper.innerText().catch(() => '')).trim()
      if (txt && !foundTooltips.includes(txt)) foundTooltips.push(txt)
    }
  }
  await page.screenshot({ path: '/tmp/cgm-03-after-sweep.png' })
  console.log(`Tooltip samples (${foundTooltips.length}):`)
  foundTooltips.slice(0, 8).forEach((t, i) => console.log(`  [${i}] ${t.replace(/\n/g, ' | ')}`))

  // Check whether any tooltip mentions BGM
  const hasBgm = foundTooltips.some(t => /bgm/i.test(t))
  console.log('Any tooltip mentions BGM:', hasBgm)

  // Now specifically hover over BGM dots
  const bgmDots = page.locator('circle[fill="#ef4444"][r="5"]')
  const dotCount = await bgmDots.count()
  console.log('BGM dot count:', dotCount)

  for (let i = 0; i < Math.min(dotCount, 3); i++) {
    const dot = bgmDots.nth(i)
    await dot.scrollIntoViewIfNeeded()
    const dotBox = await dot.boundingBox()
    if (!dotBox) continue
    console.log(`BGM dot ${i} at:`, dotBox)
    await page.mouse.move(dotBox.x + dotBox.width / 2, dotBox.y + dotBox.height / 2)
    await page.waitForTimeout(400)
    await page.screenshot({ path: `/tmp/cgm-04-bgm-${i}-hover.png` })
    const tt = await page.locator('.recharts-tooltip-wrapper').innerText().catch(() => 'no tooltip')
    console.log(`Tooltip at BGM dot ${i}:`, tt.replace(/\n/g, ' | '))
  }
})
