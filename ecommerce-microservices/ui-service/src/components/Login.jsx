import React, { useState } from 'react';
import { login } from '../api';

export default function Login({ onAuthenticated }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSubmitting(true);
        try {
            await login(username, password);
            onAuthenticated();
        } catch (err) {
            setError(err.message || 'Authentication failed');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay active">
            <div className="modal">
                <h2>Sign In</h2>
                <form onSubmit={handleSubmit}>
                    <div className="input-group">
                        <label>Username</label>
                        <input
                            value={username}
                            onChange={e => setUsername(e.target.value)}
                            required
                            placeholder="your username"
                        />
                    </div>
                    <div className="input-group">
                        <label>Password</label>
                        <input
                            type="password"
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            required
                            placeholder="your password"
                        />
                    </div>
                    {error && <p style={{ color: '#f85149', marginTop: '0.5rem' }}>{error}</p>}
                    <div className="modal-actions">
                        <button type="submit" className="btn primary" disabled={submitting}>
                            {submitting ? 'Please wait...' : 'Sign In'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
