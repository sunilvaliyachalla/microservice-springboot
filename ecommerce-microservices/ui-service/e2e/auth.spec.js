import { test, expect } from '@playwright/test';
import { mockAuth, mockProducts, mockOrders, signIn, FAKE_TOKEN } from './helpers';

test.describe('Authentication', () => {
    test('unauthenticated visitor sees the sign-in screen and no data tabs', async ({ page }) => {
        await page.goto('/');

        await expect(page.getByRole('heading', { name: 'Sign In' })).toBeVisible();
        await expect(page.getByText('Please sign in to continue.')).toBeVisible();
        await expect(page.getByRole('button', { name: 'Manage Products' })).toHaveCount(0);
    });

    test('there is no self-registration option', async ({ page }) => {
        await page.goto('/');

        await expect(page.getByText(/register|create account|sign up/i)).toHaveCount(0);
    });

    test('invalid credentials show an error and stay on the sign-in screen', async ({ page }) => {
        await mockAuth(page);
        await page.goto('/');

        await page.getByPlaceholder('your username').fill('tester');
        await page.getByPlaceholder('your password').fill('WrongPass123!');
        await page.getByRole('button', { name: 'Sign In' }).click();

        await expect(page.getByText('Invalid credentials')).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Sign In' })).toBeVisible();
    });

    test('successful sign-in shows the app with the signed-in role', async ({ page }) => {
        await mockProducts(page);
        await signIn(page, { role: 'SUPERADMIN' });

        await expect(page.getByText('Signed in as SUPERADMIN')).toBeVisible();
        await expect(page.getByRole('button', { name: 'Manage Orders' })).toBeVisible();
    });

    test('the JWT is stored and sent as a Bearer token on API calls', async ({ page }) => {
        const seenHeaders = {};
        await mockProducts(page, undefined, seenHeaders);
        await signIn(page);

        await expect(page.getByText('Mechanical Keyboard')).toBeVisible();
        expect(seenHeaders.authorization).toBe(`Bearer ${FAKE_TOKEN}`);

        const storedToken = await page.evaluate(() => localStorage.getItem('auth_token'));
        expect(storedToken).toBe(FAKE_TOKEN);
    });

    test('sign out clears the session and returns to the sign-in screen', async ({ page }) => {
        await mockProducts(page);
        await signIn(page);

        await page.getByRole('button', { name: 'Sign Out' }).click();

        await expect(page.getByRole('heading', { name: 'Sign In' })).toBeVisible();
        const storedToken = await page.evaluate(() => localStorage.getItem('auth_token'));
        expect(storedToken).toBeNull();
    });

    test('an expired session (401 from API) drops the user back to sign-in', async ({ page }) => {
        await mockOrders(page);
        await mockProducts(page);
        await signIn(page);
        await expect(page.getByText('Mechanical Keyboard')).toBeVisible();

        // Backend now rejects the token.
        await page.unroute('**/api/products');
        await page.route('**/api/products', route =>
            route.fulfill({ status: 401, json: { message: 'Invalid or expired token' } }));

        await page.getByRole('button', { name: 'Manage Orders' }).click();
        await page.getByRole('button', { name: 'Manage Products' }).click();

        const storedToken = await page.evaluate(() => localStorage.getItem('auth_token'));
        expect(storedToken).toBeNull();
    });
});
