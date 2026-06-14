const API_BASE_URL = 'http://localhost:8080/api';
const TOKEN_KEY = 'auth_token';

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
    } else {
        localStorage.removeItem(TOKEN_KEY);
    }
}

export function logout() {
    setToken(null);
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
        setToken(null);
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
    return data;
}

export async function register(username, password) {
    const data = await fetchAPI('/auth/register', { method: 'POST', body: { username, password } });
    setToken(data.token);
    return data;
}
