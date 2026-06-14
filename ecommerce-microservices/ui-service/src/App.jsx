import React, { useState } from 'react';
import ProductList from './components/ProductList';
import OrderList from './components/OrderList';
import Login from './components/Login';
import { getToken, logout as apiLogout } from './api';
import './index.css';

function App() {
  const [activeTab, setActiveTab] = useState('products');
  const [authed, setAuthed] = useState(Boolean(getToken()));

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
        <p>Full CRUD actions via API Gateway.</p>
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
      </nav>

      <main>
        {activeTab === 'products' && <ProductList />}
        {activeTab === 'orders' && <OrderList />}
      </main>
    </div>
  );
}

export default App;
