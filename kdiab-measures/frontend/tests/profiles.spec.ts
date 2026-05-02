import { test, expect } from '@playwright/test';

test.describe('Authentication and Profile Management', () => {
  test('user can log in, create a profile, and view it', async ({ page }) => {
    // 1. Navigate to the app
    await page.goto('/');

    // 2. Expect landing page and login button
    await expect(page.getByRole('heading', { name: 'Welcome to T1D Profile Manager' })).toBeVisible();
    await page.getByRole('button', { name: 'Log in' }).click();

    // 3. Keycloak Login
    await expect(page).toHaveURL(/.*localhost:8081\/realms\/kdiab-profiles.*/);
    await page.getByLabel('Username or email').fill('mike');
    await page.locator('input[name="password"]').fill('password');
    await page.getByRole('button', { name: 'Sign In' }).click();

    // 4. Verify successful login and redirect to dashboard
    await expect(page.getByText('Loading...')).not.toBeVisible();
    await expect(page.getByRole('heading', { name: 'Profiles' })).toBeVisible();

    // 5. Navigate to New Profile
    await page.getByRole('button', { name: /Create New Profile/i }).click();
    
    // 6. Fill out the profile form
    await page.getByLabel('Name').fill('E2E Test Profile');
    
    // Select insulin type
    await page.getByLabel('Insulin Type').selectOption('Fiasp');
    
    // Duration
    await page.getByLabel('Duration of Action (min)').fill('320');

    // Add ICR tab
    await page.getByRole('button', { name: 'ICR' }).click();
    await page.getByRole('button', { name: 'Add ICR Segment' }).click();
    
    // Add ISF tab
    await page.getByRole('button', { name: 'ISF' }).click();
    await page.getByRole('button', { name: 'Add ISF Segment' }).click();

    // 7. Save Profile
    await page.getByRole('button', { name: 'Save Profile' }).click();

    // 8. Verify redirection back to Dashboard (My Profiles)
    await expect(page.getByText('E2E Test Profile').first()).toBeVisible();

    // 9. Logout
    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page.getByRole('heading', { name: 'Welcome to T1D Profile Manager' })).toBeVisible();
  });
});
