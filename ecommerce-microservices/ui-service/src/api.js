const API_BASE_URL = 'http://localhost:8080/api';
const TOKEN_KEY = 'auth_token';
const ROLE_KEY = 'auth_role';

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function getRole() {
    return localStorage.getItem(ROLE_KEY);
}

export function setToken(token) {
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
    } else {
        localStorage.removeItem(TOKEN_KEY);
    }
}

export function setRole(role) {
    if (role) {
        localStorage.setItem(ROLE_KEY, role);
    } else {
        localStorage.removeItem(ROLE_KEY);
    }
}

export function logout() {
    setToken(null);
    setRole(null);
}

export class UnauthorizedError extends Error {}

export async function fetchAPI(endpoint, options = {}) {
    const token = getToken();
    const config = {
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...options,
    };

    if (config.body) {
        config.body = JSON.stringify(config.body);
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

    if (response.status === 401) {
        // Token missing/expired: clear it so the app returns to the login screen.
        logout();
        throw new UnauthorizedError('Session expired. Please sign in again.');
    }

    if (!response.ok) {
        let message = response.statusText;
        try {
            const errBody = await response.json();
            if (errBody && errBody.message) message = errBody.message;
        } catch (_) {
            // ignore parse errors, fall back to statusText
        }
        throw new Error(message);
    }

    const text = await response.text();
    return text ? JSON.parse(text) : {};
}

export async function login(username, password) {
    const data = await fetchAPI('/auth/login', { method: 'POST', body: { username, password } });
    setToken(data.token);
    setRole(data.role);
    return data;
}

// Admin-only: create a user with a role. Requires the caller to be signed in
// with a higher-privileged role; the gateway enforces authentication.
export async function createUser(username, password, role) {
    return fetchAPI('/auth/users', { method: 'POST', body: { username, password, role } });
}
