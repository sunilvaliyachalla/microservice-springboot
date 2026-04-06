const API_BASE_URL = 'http://localhost:8080/api';

// Elements
const productsTableBody = document.querySelector('#productsTable tbody');
const ordersTableBody = document.querySelector('#ordersTable tbody');
const addProductForm = document.getElementById('addProductForm');
const modalOverlay = document.getElementById('addProductModal');
const toast = document.getElementById('toast');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    fetchProducts();
    fetchOrders();
});

// Fetch Products from Gateway
async function fetchProducts() {
    try {
        const response = await fetch(`${API_BASE_URL}/products`);
        if (!response.ok) throw new Error('Failed to fetch products');
        const products = await response.json();
        
        productsTableBody.innerHTML = products.map(p => `
            <tr>
                <td>#${p.id}</td>
                <td>${p.name}</td>
                <td style="color: #3fb950; font-weight: 600;">$${p.price.toFixed(2)}</td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error fetching products:', error);
        productsTableBody.innerHTML = `<tr><td colspan="3" style="text-align:center; color: var(--text-muted);">Failed to load products. Check API Gateway.</td></tr>`;
    }
}

// Fetch Orders from Gateway
async function fetchOrders() {
    try {
        const response = await fetch(`${API_BASE_URL}/orders`);
        if (!response.ok) throw new Error('Failed to fetch orders');
        const orders = await response.json();
        
        ordersTableBody.innerHTML = orders.map(o => `
            <tr>
                <td>#${o.id}</td>
                <td><span style="background: rgba(88,166,255,0.1); color: var(--primary-color); padding: 2px 8px; border-radius: 12px; font-size: 0.8rem; font-family: monospace;">${o.orderNumber}</span></td>
                <td>Prod #${o.productId}</td>
                <td>${o.quantity}</td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error fetching orders:', error);
        ordersTableBody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: var(--text-muted);">Failed to load orders. Check API Gateway.</td></tr>`;
    }
}

// Handle Add Product
addProductForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('productName').value;
    const price = parseFloat(document.getElementById('productPrice').value);

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Saving...';

    try {
        const response = await fetch(`${API_BASE_URL}/products`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, price })
        });

        if (!response.ok) throw new Error('Failed to add product');

        // Reset and close modal
        addProductForm.reset();
        toggleModal('addProductModal');
        
        // Show success notification
        showToast('Product added successfully!');
        
        // Refresh products list
        fetchProducts();
    } catch (error) {
        console.error('Error adding product:', error);
        showToast('Failed to add product. Please try again.', true);
    } finally {
        btn.disabled = false;
        btn.textContent = 'Save Product';
    }
});

// Utilities
function toggleModal(modalId) {
    const modal = document.getElementById(modalId);
    modal.classList.toggle('active');
}

function showToast(message, isError = false) {
    toast.textContent = message;
    toast.style.background = isError ? '#f85149' : '#238636';
    toast.classList.add('show');
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}
