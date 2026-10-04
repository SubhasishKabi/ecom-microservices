import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

// Client-side estimate only (mirrors order-service defaults). The authoritative numbers come back from the server.
const TAX_RATE_ESTIMATE = 0.08;
const SHIPPING_FEE_ESTIMATE = 9.99;
const FREE_SHIPPING_THRESHOLD = 100;

const formatMoney = (value) => `$${Number(value || 0).toFixed(2)}`;

function pickDefaultAddressId(user) {
  const list = user?.addresses || [];
  const preferred = list.find((a) => a.isDefault)
    || (user?.defaultAddress && list.find((a) => a.id === user.defaultAddress.id))
    || list[0];
  return preferred ? preferred.id : null;
}

function SummaryRow({ label, value, strong }) {
  return (
    <div className="flex-between" style={{ marginBottom: '0.5rem' }}>
      <span className={strong ? undefined : 'muted'} style={strong ? { fontWeight: 700 } : undefined}>{label}</span>
      <span style={strong ? { fontWeight: 700 } : undefined}>{value}</span>
    </div>
  );
}

export default function Checkout() {
  const { user } = useAuth();
  const { items, loading: cartLoading, refreshCart } = useCart();
  const navigate = useNavigate();
  // productId -> ProductResponse, or null if it could not be loaded. A missing key means "still loading".
  const [products, setProducts] = useState({});
  const [selectedAddressId, setSelectedAddressId] = useState(() => pickDefaultAddressId(user));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const addresses = user?.addresses || [];

  // Keep the selection valid if the profile (and its address list) is refreshed.
  useEffect(() => {
    setSelectedAddressId((current) => {
      const list = user?.addresses || [];
      return list.some((a) => a.id === current) ? current : pickDefaultAddressId(user);
    });
  }, [user]);

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

  const canPlaceOrder = !submitting && items.length > 0 && addresses.length > 0 && selectedAddressId != null;

  const placeOrder = async () => {
    if (!canPlaceOrder) return;
    setSubmitting(true);
    setError('');
    try {
      const { data } = await api.post('/api/orders/checkout', { addressId: selectedAddressId });
      navigate(data?.id != null ? `/orders/${data.id}` : '/orders', { replace: true });
      // The server cleared the cart on success; sync the client-side copy.
      refreshCart();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not place your order. Please try again.');
      setSubmitting(false);
    }
  };

  if (cartLoading && items.length === 0) return <div className="spinner" />;

  if (items.length === 0) {
    return (
      <div>
        <h2 className="page-title">Checkout</h2>
        <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
          <p className="muted">Your cart is empty.</p>
          <Link to="/products" className="btn">Browse products</Link>
        </div>
      </div>
    );
  }

  const subtotal = items.reduce((sum, i) => sum + Number(i.price || 0) * (i.quantity || 0), 0);
  const estShipping = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE_ESTIMATE;
  const estTax = Math.round(subtotal * TAX_RATE_ESTIMATE * 100) / 100;
  const estTotal = subtotal + estTax + estShipping;

  return (
    <div>
      <h2 className="page-title">Checkout</h2>
      <div className="flex flex-wrap gap-2" style={{ alignItems: 'flex-start' }}>
        <div className="grid" style={{ flex: '2 1 480px', minWidth: 0 }}>
          <section className="card">
            <h3 style={{ marginTop: 0 }}>Shipping address</h3>
            {addresses.length === 0 ? (
              <p>
                Please add a shipping address in your profile first.{' '}
                <Link to="/profile">Go to profile</Link>
              </p>
            ) : (
              <div className="grid" style={{ gap: '0.75rem' }} role="radiogroup" aria-label="Shipping address">
                {addresses.map((addr) => {
                  const selected = selectedAddressId === addr.id;
                  return (
                    <label key={addr.id} className="card" style={{
                      display: 'flex', gap: '0.75rem', alignItems: 'flex-start', cursor: 'pointer',
                      padding: '0.9rem', fontWeight: 400,
                      borderColor: selected ? 'var(--color-primary)' : undefined,
                    }}>
                      <input type="radio" name="shippingAddress" value={addr.id} checked={selected}
                             onChange={() => setSelectedAddressId(addr.id)}
                             style={{ width: 'auto', marginTop: 3 }} />
                      <div>
                        <div className="flex gap-1" style={{ alignItems: 'center' }}>
                          <strong>{addr.type}</strong>
                          {addr.isDefault && <span className="badge badge-success">Default</span>}
                        </div>
                        <div className="muted" style={{ marginTop: '0.25rem' }}>
                          {addr.street}, {addr.city}, {addr.state} {addr.zipcode}, {addr.country}
                        </div>
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </section>

          <section className="card" style={{ padding: 0, overflowX: 'auto' }}>
            <h3 style={{ margin: 0, padding: '1.25rem 1.25rem 0.5rem' }}>Order items</h3>
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const product = products[item.productId];
                  return (
                    <tr key={item.id}>
                      <td>
                        <div className="flex gap-1" style={{ alignItems: 'center' }}>
                          <div style={{
                            width: 44, height: 44, borderRadius: 8, overflow: 'hidden',
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
                            {product && <span style={{ fontWeight: 600 }}>{product.name}</span>}
                          </div>
                        </div>
                      </td>
                      <td>{item.quantity}</td>
                      <td>{formatMoney(item.price)}</td>
                      <td style={{ fontWeight: 600 }}>{formatMoney(Number(item.price || 0) * (item.quantity || 0))}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </section>
        </div>

        <aside className="card" style={{ flex: '1 1 260px' }}>
          <h3 style={{ marginTop: 0 }}>Order summary</h3>
          <SummaryRow label="Subtotal" value={formatMoney(subtotal)} />
          <SummaryRow label="Estimated tax (8%)" value={formatMoney(estTax)} />
          <SummaryRow label="Estimated shipping" value={estShipping === 0 ? 'Free' : formatMoney(estShipping)} />
          <hr style={{ border: 0, borderTop: '1px solid var(--color-border)', margin: '0.75rem 0' }} />
          <SummaryRow label="Estimated total" value={formatMoney(estTotal)} strong />
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Estimate only. Prices, stock, tax and shipping are re-checked and finalized when you place the order.
          </p>
          {error && <p className="error-text">{error}</p>}
          <button style={{ width: '100%' }} disabled={!canPlaceOrder} onClick={placeOrder}>
            {submitting ? 'Placing order...' : 'Place order'}
          </button>
          <div style={{ textAlign: 'center', marginTop: '0.75rem' }}>
            <Link to="/cart">Back to cart</Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
