import React, { useState } from 'react';
import { login, register } from '../api';

export default function Login({ onAuthenticated }) {
    const [mode, setMode] = useState('login');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSubmitting(true);
        try {
            if (mode === 'login') {
                await login(username, password);
            } else {
                await register(username, password);
            }
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
                <h2>{mode === 'login' ? 'Sign In' : 'Create Account'}</h2>
                <form onSubmit={handleSubmit}>
                    <div className="input-group">
                        <label>Username</label>
                        <input
                            value={username}
                            onChange={e => setUsername(e.target.value)}
                            required
                            minLength={3}
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
                            minLength={8}
                            placeholder="at least 8 characters"
                        />
                    </div>
                    {error && <p style={{ color: '#f85149', marginTop: '0.5rem' }}>{error}</p>}
                    <div className="modal-actions">
                        <button
                            type="button"
                            className="btn outline"
                            onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}
                        >
                            {mode === 'login' ? 'Need an account?' : 'Have an account?'}
                        </button>
                        <button type="submit" className="btn primary" disabled={submitting}>
                            {submitting ? 'Please wait...' : (mode === 'login' ? 'Sign In' : 'Register')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
