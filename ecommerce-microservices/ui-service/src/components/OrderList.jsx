import React, { useState, useEffect } from 'react';
import { fetchAPI } from '../api';

export default function OrderList() {
    const [orders, setOrders] = useState([]);
    const [products, setProducts] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingOrder, setEditingOrder] = useState(null);
    const [formData, setFormData] = useState({ orderNumber: '', productId: '', quantity: 1 });

    const loadData = async () => {
        try {
            const [ordersData, productsData] = await Promise.all([
                fetchAPI('/orders'),
                fetchAPI('/products')
            ]);
            setOrders(ordersData);
            setProducts(productsData);
        } catch (e) {
            console.error('Failed to load data', e);
        }
    };

    useEffect(() => { loadData(); }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            if (editingOrder) {
                await fetchAPI(`/orders/${editingOrder.id}`, { method: 'PUT', body: formData });
            } else {
                await fetchAPI('/orders', { method: 'POST', body: formData });
            }
            setIsModalOpen(false);
            setEditingOrder(null);
            setFormData({ orderNumber: '', productId: '', quantity: 1 });
            loadData();
        } catch (e) {
            alert('Action failed');
        }
    };

    const deleteOrder = async (id) => {
        if (!window.confirm('Are you sure you want to delete this order?')) return;
        try {
            await fetchAPI(`/orders/${id}`, { method: 'DELETE' });
            loadData();
        } catch (e) {
            alert('Failed to delete');
        }
    };

    const startEdit = (o) => {
        setEditingOrder(o);
        setFormData({ orderNumber: o.orderNumber, productId: o.productId, quantity: o.quantity });
        setIsModalOpen(true);
    };

    const startAdd = () => {
        setEditingOrder(null);
        setFormData({ 
            orderNumber: `ORD-${Date.now().toString().slice(-4)}`, 
            productId: products.length > 0 ? products[0].id : '', 
            quantity: 1 
        });
        setIsModalOpen(true);
    };

    return (
        <section className="card scale-in">
            <div className="card-header">
                <h2>Orders</h2>
                <button className="btn primary" onClick={startAdd}>+ Create Order</button>
            </div>
            <div className="table-container">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Order Number</th>
                            <th>Product Details</th>
                            <th>Qty</th>
                            <th style={{ textAlign: 'right' }}>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {orders.length === 0 ? (
                            <tr><td colSpan="5" style={{ textAlign: 'center' }}>No orders found...</td></tr>
                        ) : orders.map(o => {
                            const productInfo = products.find(p => p.id === o.productId);
                            return (
                                <tr key={o.id}>
                                    <td>#{o.id}</td>
                                    <td><span className="badge">{o.orderNumber}</span></td>
                                    <td>
                                        {productInfo ? `${productInfo.name} (#${o.productId})` : `Unknown Product (#${o.productId})`}
                                    </td>
                                    <td>{o.quantity}</td>
                                    <td style={{ textAlign: 'right' }}>
                                        <button className="btn outline sm-btn mr-2" onClick={() => startEdit(o)}>Edit</button>
                                        <button className="btn danger sm-btn" onClick={() => deleteOrder(o.id)}>Delete</button>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {isModalOpen && (
                <div className="modal-overlay active">
                    <div className="modal">
                        <h2>{editingOrder ? 'Edit Order' : 'Create Order'}</h2>
                        <form onSubmit={handleSubmit}>
                            <div className="input-group">
                                <label>Order Number</label>
                                <input value={formData.orderNumber} onChange={e => setFormData({ ...formData, orderNumber: e.target.value })} required />
                            </div>
                            <div className="input-group">
                                <label>Select Product</label>
                                <select 
                                    className="select-dark" 
                                    value={formData.productId} 
                                    onChange={e => setFormData({ ...formData, productId: parseInt(e.target.value) })} 
                                    required
                                >
                                    <option value="" disabled>Select a product...</option>
                                    {products.map(p => (
                                        <option key={p.id} value={p.id}>{p.name} - ${p.price.toFixed(2)}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="input-group">
                                <label>Quantity</label>
                                <input type="number" min="1" value={formData.quantity} onChange={e => setFormData({ ...formData, quantity: parseInt(e.target.value) })} required />
                            </div>
                            <div className="modal-actions">
                                <button type="button" className="btn outline" onClick={() => setIsModalOpen(false)}>Cancel</button>
                                <button type="submit" className="btn primary">Save Order</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </section>
    );
}
