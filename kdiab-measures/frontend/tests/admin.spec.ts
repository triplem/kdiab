import { test, expect } from '@playwright/test';

test.describe('Admin Operations', () => {
  test('admin can log in and manage insulins', async ({ page }) => {
    // 1. Navigate to the app & Login as admin
    await page.goto('/');
    await page.getByRole('button', { name: 'Log in' }).click();

    // 2. Keycloak Login
    await page.getByLabel('Username or email').fill('admin');
    await page.locator('input[name="password"]').fill('password');
    await page.getByRole('button', { name: 'Sign In' }).click();

    // 3. Verify Admin Dashboard and Manage Insulin button
    await expect(page.getByText('Loading...')).not.toBeVisible();
    const manageInsulinsLink = page.getByRole('button', { name: /Manage Insulins/i });
    await expect(manageInsulinsLink).toBeVisible();
    await manageInsulinsLink.click();

    // 4. Verify Insulin List
    await expect(page.getByText('Humalog')).toBeVisible();
    await expect(page.getByText('Fiasp')).toBeVisible();
    await expect(page.getByText('Liumjev')).toBeVisible();

    // 5. Delete Example (Optional - avoiding destructive action in core seed tests if possible, so we just view)
    // We will just verify the elements loaded.
    const newInsulinInput = page.getByPlaceholder('New Insulin Name');
    await newInsulinInput.fill('AdminE2EInsulin');
    await page.getByRole('button', { name: 'Add Insulin' }).click();

    await expect(page.getByText('AdminE2EInsulin')).toBeVisible();
  });
});
