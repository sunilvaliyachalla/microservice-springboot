import { test, expect } from '@playwright/test';
import { mockProducts, signIn } from './helpers';

test.describe('Product management', () => {
    test('lists products with price and stock', async ({ page }) => {
        await mockProducts(page);
        await signIn(page);

        const row = page.getByRole('row', { name: /Mechanical Keyboard/ });
        await expect(row).toBeVisible();
        await expect(row).toContainText('$149.99');
        await expect(row).toContainText('10');
        await expect(page.getByRole('row', { name: /Wireless Mouse/ })).toBeVisible();
    });

    test('shows an empty state when there are no products', async ({ page }) => {
        await mockProducts(page, []);
        await signIn(page);

        await expect(page.getByText('No products found...')).toBeVisible();
    });

    test('adds a product through the modal', async ({ page }) => {
        const products = await mockProducts(page);
        await signIn(page);

        await page.getByRole('button', { name: '+ Add Product' }).click();
        await page.getByPlaceholder('e.g. Mechanical Keyboard').fill('USB Hub');
        await page.getByPlaceholder('149.99').fill('29.99');
        await page.getByPlaceholder('100').fill('42');
        await page.getByRole('button', { name: 'Save Product' }).click();

        await expect(page.getByRole('row', { name: /USB Hub/ })).toBeVisible();
        expect(products.find(p => p.name === 'USB Hub')).toMatchObject({
            name: 'USB Hub',
            price: 29.99,
            stock: 42,
        });
    });

    test('edits an existing product', async ({ page }) => {
        const products = await mockProducts(page);
        await signIn(page);

        await page.getByRole('row', { name: /Wireless Mouse/ })
            .getByRole('button', { name: 'Edit' }).click();
        await page.getByPlaceholder('e.g. Mechanical Keyboard').fill('Ergonomic Mouse');
        await page.getByPlaceholder('100').fill('8');
        await page.getByRole('button', { name: 'Save Product' }).click();

        await expect(page.getByRole('row', { name: /Ergonomic Mouse/ })).toBeVisible();
        expect(products.find(p => p.id === 2)).toMatchObject({ name: 'Ergonomic Mouse', stock: 8 });
    });

    test('deletes a product after confirmation', async ({ page }) => {
        const products = await mockProducts(page);
        await signIn(page);

        page.on('dialog', dialog => dialog.accept());
        await page.getByRole('row', { name: /Wireless Mouse/ })
            .getByRole('button', { name: 'Delete' }).click();

        await expect(page.getByRole('row', { name: /Wireless Mouse/ })).toHaveCount(0);
        expect(products.some(p => p.id === 2)).toBe(false);
    });

    test('cancelling the confirmation keeps the product', async ({ page }) => {
        const products = await mockProducts(page);
        await signIn(page);

        page.on('dialog', dialog => dialog.dismiss());
        await page.getByRole('row', { name: /Wireless Mouse/ })
            .getByRole('button', { name: 'Delete' }).click();

        await expect(page.getByRole('row', { name: /Wireless Mouse/ })).toBeVisible();
        expect(products.some(p => p.id === 2)).toBe(true);
    });
});
