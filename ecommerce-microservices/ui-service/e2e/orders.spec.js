import { test, expect } from '@playwright/test';
import { mockProducts, mockOrders, signIn } from './helpers';

test.describe('Order management', () => {
    test.beforeEach(async ({ page }) => {
        await mockProducts(page);
    });

    test('lists orders with resolved product names', async ({ page }) => {
        await mockOrders(page);
        await signIn(page);
        await page.getByRole('button', { name: 'Manage Orders' }).click();

        const row = page.getByRole('row', { name: /ORD-1001/ });
        await expect(row).toBeVisible();
        await expect(row).toContainText('Mechanical Keyboard (#1)');
        await expect(row).toContainText('2');
    });

    test('marks orders whose product no longer exists', async ({ page }) => {
        await mockOrders(page, [{ id: 9, orderNumber: 'ORD-9999', productId: 777, quantity: 1 }]);
        await signIn(page);
        await page.getByRole('button', { name: 'Manage Orders' }).click();

        await expect(page.getByText('Unknown Product (#777)')).toBeVisible();
    });

    test('creates an order for a selected product', async ({ page }) => {
        const orders = await mockOrders(page, []);
        await signIn(page);
        await page.getByRole('button', { name: 'Manage Orders' }).click();
        await expect(page.getByText('No orders found...')).toBeVisible();

        await page.getByRole('button', { name: '+ Create Order' }).click();

        // Order number is prefilled; pick a product and quantity.
        const orderNumberInput = page.locator('.input-group', { hasText: 'Order Number' }).locator('input');
        await expect(orderNumberInput).toHaveValue(/^ORD-/);
        await orderNumberInput.fill('ORD-E2E-1');
        await page.locator('.select-dark').selectOption({ label: 'Wireless Mouse - $49.50' });
        await page.locator('.input-group', { hasText: 'Quantity' }).locator('input').fill('3');
        await page.getByRole('button', { name: 'Save Order' }).click();

        const row = page.getByRole('row', { name: /ORD-E2E-1/ });
        await expect(row).toBeVisible();
        await expect(row).toContainText('Wireless Mouse (#2)');
        expect(orders.find(o => o.orderNumber === 'ORD-E2E-1')).toMatchObject({
            productId: 2,
            quantity: 3,
        });
    });

    test('edits an order quantity', async ({ page }) => {
        const orders = await mockOrders(page);
        await signIn(page);
        await page.getByRole('button', { name: 'Manage Orders' }).click();

        await page.getByRole('row', { name: /ORD-1001/ })
            .getByRole('button', { name: 'Edit' }).click();
        await page.locator('.input-group', { hasText: 'Quantity' }).locator('input').fill('7');
        await page.getByRole('button', { name: 'Save Order' }).click();

        await expect(page.getByRole('row', { name: /ORD-1001/ })).toContainText('7');
        expect(orders.find(o => o.id === 1).quantity).toBe(7);
    });

    test('deletes an order after confirmation', async ({ page }) => {
        const orders = await mockOrders(page);
        await signIn(page);
        await page.getByRole('button', { name: 'Manage Orders' }).click();

        page.on('dialog', dialog => dialog.accept());
        await page.getByRole('row', { name: /ORD-1001/ })
            .getByRole('button', { name: 'Delete' }).click();

        await expect(page.getByText('No orders found...')).toBeVisible();
        expect(orders).toHaveLength(0);
    });
});
