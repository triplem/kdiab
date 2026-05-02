import { test, expect } from '@playwright/test';

test('App should display the login prompt when unauthenticated', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('h2')).toContainText('T1D Measure Manager');
  await expect(page.locator('button', { hasText: 'Log in' })).toBeVisible();
});
