import { test, expect } from '@playwright/test';
import { mockProducts, signIn } from './helpers';

test.describe('User management (role-gated)', () => {
    test.beforeEach(async ({ page }) => {
        await mockProducts(page);
    });

    test('SUPERADMIN sees the Manage Users tab and can assign ADMIN/MANAGER/USER', async ({ page }) => {
        await signIn(page, { role: 'SUPERADMIN' });

        await page.getByRole('button', { name: 'Manage Users' }).click();
        await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();

        const options = await page.locator('.select-dark option').allTextContents();
        expect(options).toEqual(['ADMIN', 'MANAGER', 'USER']);
    });

    test('ADMIN may only assign MANAGER and USER', async ({ page }) => {
        await signIn(page, { role: 'ADMIN' });

        await page.getByRole('button', { name: 'Manage Users' }).click();
        const options = await page.locator('.select-dark option').allTextContents();
        expect(options).toEqual(['MANAGER', 'USER']);
    });

    test('a plain USER has no Manage Users tab at all', async ({ page }) => {
        await signIn(page, { role: 'USER' });

        await expect(page.getByRole('button', { name: 'Manage Products' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Manage Users' })).toHaveCount(0);
    });

    test('creates a user and shows a success message', async ({ page }) => {
        let received;
        await page.route('**/api/auth/users', async route => {
            received = {
                body: route.request().postDataJSON(),
                authorization: route.request().headers()['authorization'],
            };
            await route.fulfill({
                status: 201,
                json: { id: 5, username: received.body.username, role: received.body.role },
            });
        });
        await signIn(page, { role: 'SUPERADMIN' });

        await page.getByRole('button', { name: 'Manage Users' }).click();
        await page.getByPlaceholder('new username').fill('warehouse-manager');
        await page.getByPlaceholder('at least 8 characters').fill('Secret123!');
        await page.locator('.select-dark').selectOption('MANAGER');
        await page.getByRole('button', { name: 'Create User' }).click();

        await expect(page.getByText('Created user "warehouse-manager" with role MANAGER.')).toBeVisible();
        expect(received.body).toMatchObject({ username: 'warehouse-manager', role: 'MANAGER' });
        expect(received.authorization).toMatch(/^Bearer /);
    });

    test('shows the backend error when creation is rejected', async ({ page }) => {
        await page.route('**/api/auth/users', route =>
            route.fulfill({ status: 409, json: { message: 'Username already taken' } }));
        await signIn(page, { role: 'SUPERADMIN' });

        await page.getByRole('button', { name: 'Manage Users' }).click();
        await page.getByPlaceholder('new username').fill('duplicate');
        await page.getByPlaceholder('at least 8 characters').fill('Secret123!');
        await page.getByRole('button', { name: 'Create User' }).click();

        await expect(page.getByText('Username already taken')).toBeVisible();
    });
});
