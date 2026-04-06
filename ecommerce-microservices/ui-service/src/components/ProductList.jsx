import React, { useState, useEffect } from 'react';
import { fetchAPI } from '../api';

export default function ProductList() {
    const [products, setProducts] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingProduct, setEditingProduct] = useState(null);
    const [formData, setFormData] = useState({ name: '', price: '' });

    const loadProducts = async () => {
        try {
            const data = await fetchAPI('/products');
            setProducts(data);
        } catch (e) {
            console.error('Failed to load products', e);
        }
    };

    useEffect(() => { loadProducts(); }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            if (editingProduct) {
                await fetchAPI(`/products/${editingProduct.id}`, { method: 'PUT', body: formData });
            } else {
                await fetchAPI('/products', { method: 'POST', body: formData });
            }
            setIsModalOpen(false);
            setEditingProduct(null);
            setFormData({ name: '', price: '' });
            loadProducts();
        } catch (e) {
            alert('Action failed');
        }
    };

    const deleteProduct = async (id) => {
        if (!window.confirm('Are you sure you want to delete this product?')) return;
        try {
            await fetchAPI(`/products/${id}`, { method: 'DELETE' });
            loadProducts();
        } catch (e) {
            alert('Failed to delete');
        }
    };

    const startEdit = (p) => {
        setEditingProduct(p);
        setFormData({ name: p.name, price: p.price });
        setIsModalOpen(true);
    };

    const startAdd = () => {
        setEditingProduct(null);
        setFormData({ name: '', price: '' });
        setIsModalOpen(true);
    };

    return (
        <section className="card scale-in">
            <div className="card-header">
                <h2>Products</h2>
                <button className="btn primary" onClick={startAdd}>+ Add Product</button>
            </div>
            <div className="table-container">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Name</th>
                            <th>Price</th>
                            <th style={{ textAlign: 'right' }}>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {products.length === 0 ? (
                            <tr><td colSpan="4" style={{ textAlign: 'center' }}>No products found...</td></tr>
                        ) : products.map(p => (
                            <tr key={p.id}>
                                <td>#{p.id}</td>
                                <td>{p.name}</td>
                                <td style={{ color: '#3fb950', fontWeight: 'bold' }}>${p.price.toFixed(2)}</td>
                                <td style={{ textAlign: 'right' }}>
                                    <button className="btn outline sm-btn mr-2" onClick={() => startEdit(p)}>Edit</button>
                                    <button className="btn danger sm-btn" onClick={() => deleteProduct(p.id)}>Delete</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {isModalOpen && (
                <div className="modal-overlay active">
                    <div className="modal">
                        <h2>{editingProduct ? 'Edit Product' : 'Add Product'}</h2>
                        <form onSubmit={handleSubmit}>
                            <div className="input-group">
                                <label>Name</label>
                                <input value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} required placeholder="e.g. Mechanical Keyboard" />
                            </div>
                            <div className="input-group">
                                <label>Price ($)</label>
                                <input type="number" step="0.01" value={formData.price} onChange={e => setFormData({ ...formData, price: parseFloat(e.target.value) })} required placeholder="149.99" />
                            </div>
                            <div className="modal-actions">
                                <button type="button" className="btn outline" onClick={() => setIsModalOpen(false)}>Cancel</button>
                                <button type="submit" className="btn primary">Save Product</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </section>
    );
}
