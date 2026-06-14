import React, { useState } from 'react';
import { createUser, getRole } from '../api';

// Roles a caller may create, by their own role (must be strictly lower).
const ASSIGNABLE_ROLES = {
    SUPERADMIN: ['ADMIN', 'MANAGER', 'USER'],
    ADMIN: ['MANAGER', 'USER'],
    MANAGER: ['USER'],
};

export default function UserManagement() {
    const role = getRole();
    const assignable = ASSIGNABLE_ROLES[role] || [];

    const [formData, setFormData] = useState({ username: '', password: '', role: assignable[0] || '' });
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setMessage('');
        setError('');
        setSubmitting(true);
        try {
            const created = await createUser(formData.username, formData.password, formData.role);
            setMessage(`Created user "${created.username}" with role ${created.role}.`);
            setFormData({ username: '', password: '', role: assignable[0] || '' });
        } catch (err) {
            setError(err.message || 'Failed to create user');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <section className="card scale-in">
            <div className="card-header">
                <h2>User Management</h2>
            </div>
            <form onSubmit={handleSubmit} style={{ maxWidth: '420px' }}>
                <div className="input-group">
                    <label>Username</label>
                    <input
                        value={formData.username}
                        onChange={e => setFormData({ ...formData, username: e.target.value })}
                        required
                        minLength={3}
                        placeholder="new username"
                    />
                </div>
                <div className="input-group">
                    <label>Password</label>
                    <input
                        type="password"
                        value={formData.password}
                        onChange={e => setFormData({ ...formData, password: e.target.value })}
                        required
                        minLength={8}
                        placeholder="at least 8 characters"
                    />
                </div>
                <div className="input-group">
                    <label>Role</label>
                    <select
                        className="select-dark"
                        value={formData.role}
                        onChange={e => setFormData({ ...formData, role: e.target.value })}
                        required
                    >
                        {assignable.map(r => <option key={r} value={r}>{r}</option>)}
                    </select>
                </div>
                {message && <p style={{ color: '#3fb950', marginTop: '0.5rem' }}>{message}</p>}
                {error && <p style={{ color: '#f85149', marginTop: '0.5rem' }}>{error}</p>}
                <div className="modal-actions">
                    <button type="submit" className="btn primary" disabled={submitting || assignable.length === 0}>
                        {submitting ? 'Creating...' : 'Create User'}
                    </button>
                </div>
            </form>
        </section>
    );
}
