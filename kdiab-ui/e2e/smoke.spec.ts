import { test, expect } from '@playwright/test';

test('app loads and shows title or auth redirect', async ({ page }) => {
  await page.goto('/');
  // The app either shows the kdiab shell or redirects to Keycloak for auth
  await expect(page).toHaveTitle(/kdiab|Keycloak/i);
});

test('unauthenticated request returns non-server-error status', async ({ request }) => {
  const response = await request.get('/');
  expect(response.status()).toBeLessThan(500);
});
