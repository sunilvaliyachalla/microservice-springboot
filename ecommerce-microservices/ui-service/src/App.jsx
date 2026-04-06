import React, { useState } from 'react';
import ProductList from './components/ProductList';
import OrderList from './components/OrderList';
import './index.css';

function App() {
  const [activeTab, setActiveTab] = useState('products');

  return (
    <div className="app-container">
      <header>
        <h1><span className="gradient-text">E-Commerce</span> React Hub</h1>
        <p>Full CRUD actions via API Gateway.</p>
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
