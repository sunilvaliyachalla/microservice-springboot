import { expect } from '@playwright/test';

export const PRODUCTS = [
    { id: 1, name: 'Mechanical Keyboard', price: 149.99, stock: 10 },
    { id: 2, name: 'Wireless Mouse', price: 49.5, stock: 5 },
];

export const ORDERS = [
    { id: 1, orderNumber: 'ORD-1001', productId: 1, quantity: 2 },
];

export const FAKE_TOKEN = 'fake-jwt-token';

/**
 * Intercepts the auth endpoint. Password "WrongPass123!" fails with 401,
 * anything else succeeds with the given role.
 */
export async function mockAuth(page, role = 'SUPERADMIN') {
    await page.route('**/api/auth/login', async route => {
        const body = route.request().postDataJSON();
        if (body.password === 'WrongPass123!') {
            await route.fulfill({
                status: 401,
                json: { status: 401, error: 'Unauthorized', message: 'Invalid credentials' },
            });
        } else {
            await route.fulfill({
                json: {
                    token: FAKE_TOKEN,
                    tokenType: 'Bearer',
                    username: body.username,
                    role,
                    expiresInMs: 3600000,
                },
            });
        }
    });
}

/**
 * Stateful in-memory product API. Returns the backing array so tests can
 * assert against it. Also records the Authorization header of the last call.
 */
export async function mockProducts(page, initial = PRODUCTS, seenHeaders = {}) {
    const products = initial.map(p => ({ ...p }));

    await page.route('**/api/products', async route => {
        seenHeaders.authorization = route.request().headers()['authorization'];
        const method = route.request().method();
        if (method === 'GET') {
            return route.fulfill({ json: products });
        }
        if (method === 'POST') {
            const body = route.request().postDataJSON();
            const created = {
                id: products.length ? Math.max(...products.map(p => p.id)) + 1 : 1,
                ...body,
            };
            products.push(created);
            return route.fulfill({ status: 201, json: created });
        }
        return route.fallback();
    });

    await page.route('**/api/products/*', async route => {
        const method = route.request().method();
        const id = Number(route.request().url().split('/').pop());
        const index = products.findIndex(p => p.id === id);
        if (method === 'DELETE') {
            if (index >= 0) products.splice(index, 1);
            return route.fulfill({ status: 204, body: '' });
        }
        if (method === 'PUT') {
            if (index < 0) return route.fulfill({ status: 404, json: { message: 'Not found' } });
            products[index] = { ...products[index], ...route.request().postDataJSON() };
            return route.fulfill({ json: products[index] });
        }
        if (method === 'GET') {
            return index >= 0
                ? route.fulfill({ json: products[index] })
                : route.fulfill({ status: 404, json: { message: 'Not found' } });
        }
        return route.fallback();
    });

    return products;
}

/** Stateful in-memory order API. */
export async function mockOrders(page, initial = ORDERS) {
    const orders = initial.map(o => ({ ...o }));

    await page.route('**/api/orders', async route => {
        const method = route.request().method();
        if (method === 'GET') {
            return route.fulfill({ json: orders });
        }
        if (method === 'POST') {
            const body = route.request().postDataJSON();
            const created = {
                id: orders.length ? Math.max(...orders.map(o => o.id)) + 1 : 1,
                ...body,
            };
            orders.push(created);
            return route.fulfill({ status: 201, json: created });
        }
        return route.fallback();
    });

    await page.route('**/api/orders/*', async route => {
        const method = route.request().method();
        const id = Number(route.request().url().split('/').pop());
        const index = orders.findIndex(o => o.id === id);
        if (method === 'DELETE') {
            if (index >= 0) orders.splice(index, 1);
            return route.fulfill({ status: 204, body: '' });
        }
        if (method === 'PUT') {
            if (index < 0) return route.fulfill({ status: 404, json: { message: 'Not found' } });
            orders[index] = { ...orders[index], ...route.request().postDataJSON() };
            return route.fulfill({ json: orders[index] });
        }
        return route.fallback();
    });

    return orders;
}

/** Signs in through the real UI and waits for the main screen. */
export async function signIn(page, { username = 'tester', password = 'Password123!', role = 'SUPERADMIN' } = {}) {
    await mockAuth(page, role);
    await page.goto('/');
    await page.getByPlaceholder('your username').fill(username);
    await page.getByPlaceholder('your password').fill(password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page.getByRole('button', { name: 'Manage Products' })).toBeVisible();
}
