import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useCart } from '../context/CartContext';

const formatMoney = (value) => `$${Number(value || 0).toFixed(2)}`;

export default function Cart() {
  const { items, loading, removeFromCart, refreshCart, itemCount } = useCart();
  const navigate = useNavigate();
  // productId -> ProductResponse, or null if it could not be loaded. A missing key means "still loading".
  const [products, setProducts] = useState({});
  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState('');

  // Only refetch product details when the set of products changes, not on every cart refresh.
  const productIdsKey = items.map((i) => i.productId).join(',');

  useEffect(() => {
    const ids = productIdsKey ? productIdsKey.split(',') : [];
    if (ids.length === 0) return undefined;
    let cancelled = false;
    Promise.all(
      ids.map((pid) => api.get(`/api/products/${pid}`).then((res) => res.data).catch(() => null))
    ).then((results) => {
      if (cancelled) return;
      const next = {};
      ids.forEach((pid, idx) => { next[pid] = results[idx]; });
      setProducts(next);
    });
    return () => { cancelled = true; };
  }, [productIdsKey]);

  const handleRemove = async (productId) => {
    setBusyId(productId);
    setActionError('');
    try {
      await removeFromCart(productId);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not remove this item. Please try again.');
    } finally {
      setBusyId(null);
    }
  };

  const handleQuantityChange = async (productId, newQuantity) => {
    if (newQuantity < 1) return;
    setBusyId(productId);
    setActionError('');
    try {
      await api.put(`/api/cart/items/${productId}`, { productId, quantity: newQuantity });
      await refreshCart();
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not update quantity (insufficient stock?).');
    } finally {
      setBusyId(null);
    }
  };

  if (loading && items.length === 0) return <div className="spinner" />;

  if (items.length === 0) {
    return (
      <div>
        <h2 className="page-title">Your cart</h2>
        <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
          <p className="muted">Your cart is empty.</p>
          <Link to="/products" className="btn">Continue shopping</Link>
        </div>
      </div>
    );
  }

  const subtotal = items.reduce((sum, i) => sum + Number(i.price || 0) * (i.quantity || 0), 0);
  const hasUnavailable = items.some((i) => products[i.productId] === null || products[i.productId]?.active === false);

  return (
    <div>
      <h2 className="page-title">Your cart</h2>
      <div className="flex flex-wrap gap-2" style={{ alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: '2 1 520px', minWidth: 0, padding: 0, overflowX: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Price</th>
                <th>Qty</th>
                <th>Subtotal</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const product = products[item.productId];
                const lineSubtotal = Number(item.price || 0) * (item.quantity || 0);
                return (
                  <tr key={item.id}>
                    <td>
                      <div className="flex gap-1" style={{ alignItems: 'center' }}>
                        <div style={{
                          width: 56, height: 56, borderRadius: 8, overflow: 'hidden',
                          background: '#f1f2f6', flexShrink: 0,
                        }}>
                          {product?.imageUrl && (
                            <img src={product.imageUrl} alt={product.name}
                                 style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          )}
                        </div>
                        <div>
                          {product === undefined && <span className="muted">Loading...</span>}
                          {product === null && (
                            <>
                              <div style={{ fontWeight: 600 }}>Product unavailable</div>
                              <div className="muted" style={{ fontSize: '0.8rem' }}>ID: {item.productId}</div>
                            </>
                          )}
                          {product && (
                            <>
                              <Link to={`/products/${product.id}`} style={{ fontWeight: 600 }}>{product.name}</Link>
                              {product.brand && <div className="muted" style={{ fontSize: '0.8rem' }}>{product.brand}</div>}
                              {product.active === false && (
                                <span className="badge badge-danger">No longer available</span>
                              )}
                              {product.active !== false && product.stockQuantity != null
                                && product.stockQuantity < item.quantity && (
                                <span className="badge badge-warning">Only {product.stockQuantity} left in stock</span>
                              )}
                            </>
                          )}
                        </div>
                      </div>
                    </td>
                    <td>{formatMoney(item.price)}</td>
                    <td>
                      <div className="flex gap-1" style={{ alignItems: 'center' }}>
                        <button className="btn-secondary" style={{ padding: '0.2rem 0.6rem' }}
                                disabled={busyId === item.productId || item.quantity <= 1}
                                onClick={() => handleQuantityChange(item.productId, item.quantity - 1)}>−</button>
                        <span>{item.quantity}</span>
                        <button className="btn-secondary" style={{ padding: '0.2rem 0.6rem' }}
                                disabled={busyId === item.productId}
                                onClick={() => handleQuantityChange(item.productId, item.quantity + 1)}>+</button>
                      </div>
                    </td>
                    <td style={{ fontWeight: 600 }}>{formatMoney(lineSubtotal)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <button className="btn-secondary" disabled={busyId === item.productId}
                              onClick={() => handleRemove(item.productId)}>
                        {busyId === item.productId ? 'Removing...' : 'Remove'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {actionError && <p className="error-text" style={{ padding: '0 0.75rem 0.75rem' }}>{actionError}</p>}
        </div>

        <aside className="card" style={{ flex: '1 1 260px' }}>
          <h3 style={{ marginTop: 0 }}>Order summary</h3>
          <div className="flex-between" style={{ marginBottom: '0.5rem' }}>
            <span className="muted">Subtotal ({itemCount} {itemCount === 1 ? 'item' : 'items'})</span>
            <strong>{formatMoney(subtotal)}</strong>
          </div>
          <p className="muted" style={{ fontSize: '0.85rem' }}>Tax and shipping are calculated at checkout.</p>
          {hasUnavailable && (
            <p className="error-text">
              Some items are no longer available and will prevent checkout. Please remove them.
            </p>
          )}
          <button style={{ width: '100%' }} onClick={() => navigate('/checkout')}>Proceed to checkout</button>
          <div style={{ textAlign: 'center', marginTop: '0.75rem' }}>
            <Link to="/products">Continue shopping</Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
