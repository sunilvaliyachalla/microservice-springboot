const API_BASE_URL = 'http://localhost:8080/api';

export async function fetchAPI(endpoint, options = {}) {
    const config = {
        headers: {
            'Content-Type': 'application/json'
        },
        ...options,
    };

    if (config.body) {
        config.body = JSON.stringify(config.body);
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
    
    if (!response.ok) {
        throw new Error(`API Error: ${response.statusText}`);
    }

    // Attempt to parse JSON, or return empty obj if it's a 200/204 No Content
    const text = await response.text();
    return text ? JSON.parse(text) : {};
}
