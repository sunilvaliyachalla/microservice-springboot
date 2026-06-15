import React, { useState } from 'react';
import ProductList from './components/ProductList';
import OrderList from './components/OrderList';
import UserManagement from './components/UserManagement';
import Login from './components/Login';
import { getToken, getRole, logout as apiLogout } from './api';
import './index.css';

const PRIVILEGED_ROLES = ['SUPERADMIN', 'ADMIN', 'MANAGER'];

function App() {
  const [activeTab, setActiveTab] = useState('products');
  const [authed, setAuthed] = useState(Boolean(getToken()));

  const role = getRole();
  const canManageUsers = PRIVILEGED_ROLES.includes(role);

  const handleLogout = () => {
    apiLogout();
    setAuthed(false);
  };

  if (!authed) {
    return (
      <div className="app-container">
        <header>
          <h1><span className="gradient-text">E-Commerce</span> React Hub</h1>
          <p>Please sign in to continue.</p>
        </header>
        <Login onAuthenticated={() => setAuthed(true)} />
      </div>
    );
  }

  return (
    <div className="app-container">
      <header>
        <h1><span className="gradient-text">E-Commerce</span> React Hub</h1>
        <p>Signed in{role ? ` as ${role}` : ''}. Full CRUD actions via API Gateway.</p>
        <button className="btn outline sm-btn" onClick={handleLogout}>Sign Out</button>
      </header>

      <nav className="tab-nav">
        <button
            className={`btn ${activeTab === 'products' ? 'primary' : 'outline'}`}
            onClick={() => setActiveTab('products')}
        >
            Manage Products
        </button>
        <button
            className={`btn ${activeTab === 'orders' ? 'primary' : 'outline'}`}
            onClick={() => setActiveTab('orders')}
        >
            Manage Orders
        </button>
        {canManageUsers && (
          <button
              className={`btn ${activeTab === 'users' ? 'primary' : 'outline'}`}
              onClick={() => setActiveTab('users')}
          >
              Manage Users
          </button>
        )}
      </nav>

      <main>
        {activeTab === 'products' && <ProductList />}
        {activeTab === 'orders' && <OrderList />}
        {activeTab === 'users' && canManageUsers && <UserManagement />}
      </main>
    </div>
  );
}

export default App;
