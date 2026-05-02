import { test, expect } from '@playwright/test';

test('capture dashboard for ux review', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Log in' }).click();

  await page.getByLabel('Username or email').fill('mike');
  await page.locator('input[name="password"]').fill('password');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page.getByText('Loading...')).not.toBeVisible();
  await expect(page.getByRole('heading', { name: 'Profiles' })).toBeVisible();

  // Wait an extra moment for animations/render
  await page.waitForTimeout(1000);
  await page.screenshot({ path: 'ux_review_dashboard.png', fullPage: true });
});
