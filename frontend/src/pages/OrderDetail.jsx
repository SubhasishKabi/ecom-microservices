import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';

const CANCELLABLE_STATUSES = ['PENDING', 'PROCESSING', 'CONFIRMED'];
// A return in any of these states blocks a new request for the same order (only REJECTED allows retrying).
const OPEN_RETURN_STATUSES = ['REQUESTED', 'APPROVED', 'REFUNDED'];

const formatMoney = (value) => `$${Number(value || 0).toFixed(2)}`;

function formatDateTime(value) {
  if (!value) return '';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

function statusBadgeClass(status) {
  if (status === 'CANCELLED') return 'badge badge-danger';
  if (status === 'CONFIRMED' || status === 'SHIPPED' || status === 'DELIVERED') return 'badge badge-success';
  return 'badge';
}

function returnBadgeClass(status) {
  if (status === 'REJECTED') return 'badge badge-danger';
  if (status === 'APPROVED' || status === 'REFUNDED') return 'badge badge-success';
  return 'badge badge-warning';
}

function SummaryRow({ label, value, strong }) {
  return (
    <div className="flex-between" style={{ marginBottom: '0.5rem' }}>
      <span className={strong ? undefined : 'muted'} style={strong ? { fontWeight: 700 } : undefined}>{label}</span>
      <span style={strong ? { fontWeight: 700 } : undefined}>{value}</span>
    </div>
  );
}

export default function OrderDetail() {
  const { id } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // productId -> ProductResponse, or null if it could not be loaded.
  const [products, setProducts] = useState({});
  const [returns, setReturns] = useState([]);

  const [showCancelForm, setShowCancelForm] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');

  const [returnReason, setReturnReason] = useState('');
  const [submittingReturn, setSubmittingReturn] = useState(false);
  const [returnError, setReturnError] = useState('');
  const [returnMessage, setReturnMessage] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    setOrder(null);
    api.get(`/api/orders/${id}`)
      .then(({ data }) => { if (!cancelled) setOrder(data); })
      .catch((err) => {
        if (cancelled) return;
        setError(err.response?.status === 404
          ? 'Order not found.'
          : err.response?.data?.message || 'Could not load this order. Please try again.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  // Optional nicer display: look up product names for each line (falls back to the product ID).
  const productIdsKey = (order?.items || []).map((i) => i.productId).join(',');

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

  // Returns only exist for delivered orders; load the user's returns and keep the ones for this order.
  const orderId = order?.id;
  const isDelivered = order?.status === 'DELIVERED';

  useEffect(() => {
    setReturns((prev) => (prev.length === 0 ? prev : []));
    if (orderId == null || !isDelivered) return undefined;
    let cancelled = false;
    api.get('/api/orders/returns/my', { params: { page: 0, size: 50 } })
      .then(({ data }) => {
        if (!cancelled) setReturns((data?.content || []).filter((r) => r.orderId === orderId));
      })
      .catch(() => { if (!cancelled) setReturns([]); });
    return () => { cancelled = true; };
  }, [orderId, isDelivered]);

  const handleCancel = async (e) => {
    e.preventDefault();
    setCancelling(true);
    setCancelError('');
    try {
      const reason = cancelReason.trim();
      const { data: cancelledOrder } = await api.post(`/api/orders/${id}/cancel`, reason ? { reason } : {});
      let fresh = cancelledOrder;
      try {
        fresh = (await api.get(`/api/orders/${id}`)).data;
      } catch {
        // Refetch failed; the cancel response already reflects the new state.
      }
      setOrder(fresh);
      setShowCancelForm(false);
      setCancelReason('');
    } catch (err) {
      setCancelError(err.response?.data?.message || 'Could not cancel this order. Please try again.');
    } finally {
      setCancelling(false);
    }
  };

  const handleReturn = async (e) => {
    e.preventDefault();
    const reason = returnReason.trim();
    if (!reason) {
      setReturnError('Please tell us why you want to return this order.');
      return;
    }
    setSubmittingReturn(true);
    setReturnError('');
    setReturnMessage('');
    try {
      const { data } = await api.post(`/api/orders/${id}/return`, { reason });
      setReturns((list) => [data, ...list]);
      setReturnReason('');
      setReturnMessage('Return requested — pending admin review.');
    } catch (err) {
      setReturnError(err.response?.data?.message || 'Could not submit your return request. Please try again.');
    } finally {
      setSubmittingReturn(false);
    }
  };

  if (loading) return <div className="spinner" />;

  if (error || !order) {
    return (
      <div>
        <h2 className="page-title">Order details</h2>
        <div className="card">
          <p className="error-text">{error || 'Order not found.'}</p>
          <Link to="/orders">Back to my orders</Link>
        </div>
      </div>
    );
  }

  const canCancel = CANCELLABLE_STATUSES.includes(order.status);
  const canRequestReturn = isDelivered && !returns.some((r) => OPEN_RETURN_STATUSES.includes(r.status));
  const address = order.shippingAddress;
  const shippingFee = Number(order.shippingFee || 0);

  return (
    <div>
      <Link to="/orders" style={{ display: 'inline-block', marginTop: '1rem' }}>&larr; Back to my orders</Link>
      <div className="flex-between flex-wrap gap-1">
        <h2 className="page-title">Order #{order.id}</h2>
        <span className={statusBadgeClass(order.status)} style={{ fontSize: '0.85rem' }}>{order.status}</span>
      </div>
      <p className="muted" style={{ marginTop: '-0.75rem' }}>Placed {formatDateTime(order.createdAt)}</p>

      {order.cancellationReason && (
        <div className="card" style={{ marginBottom: '1.25rem', borderColor: 'var(--color-danger)' }}>
          <strong>Cancellation reason:</strong> {order.cancellationReason}
        </div>
      )}

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', marginBottom: '1.25rem' }}>
        <section className="card">
          <h3 style={{ marginTop: 0 }}>Shipping address</h3>
          {address ? (
            <p className="muted" style={{ margin: 0, lineHeight: 1.6 }}>
              {address.street}<br />
              {address.city}{address.state ? `, ${address.state}` : ''} {address.zipcode}<br />
              {address.country}
            </p>
          ) : (
            <p className="muted" style={{ margin: 0 }}>No shipping address recorded.</p>
          )}
        </section>

        <section className="card">
          <h3 style={{ marginTop: 0 }}>Payment summary</h3>
          <SummaryRow label="Subtotal" value={formatMoney(order.subtotal)} />
          <SummaryRow label="Tax" value={formatMoney(order.tax)} />
          <SummaryRow label="Shipping" value={shippingFee === 0 ? 'Free' : formatMoney(shippingFee)} />
          <hr style={{ border: 0, borderTop: '1px solid var(--color-border)', margin: '0.75rem 0' }} />
          <SummaryRow label="Total" value={formatMoney(order.totalAmount)} strong />
        </section>
      </div>

      <section className="card" style={{ padding: 0, overflowX: 'auto', marginBottom: '1.25rem' }}>
        <h3 style={{ margin: 0, padding: '1.25rem 1.25rem 0.5rem' }}>Items</h3>
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
            {(order.items || []).map((item) => {
              const product = products[item.productId];
              return (
                <tr key={item.id}>
                  <td>
                    {product ? (
                      <Link to={`/products/${product.id}`}>{product.name}</Link>
                    ) : (
                      <span>Product #{item.productId}</span>
                    )}
                  </td>
                  <td>{item.quantity}</td>
                  <td>{formatMoney(item.price)}</td>
                  <td style={{ fontWeight: 600 }}>
                    {formatMoney(item.subTotal ?? Number(item.price || 0) * (item.quantity || 0))}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>

      {canCancel && (
        <section className="card" style={{ marginBottom: '1.25rem' }}>
          <h3 style={{ marginTop: 0 }}>Cancel order</h3>
          {!showCancelForm ? (
            <>
              <p className="muted">You can cancel this order until it ships.</p>
              <button className="btn-danger" onClick={() => { setShowCancelForm(true); setCancelError(''); }}>
                Cancel order
              </button>
            </>
          ) : (
            <form onSubmit={handleCancel}>
              <div className="form-group">
                <label htmlFor="cancel-reason">Reason (optional)</label>
                <input id="cancel-reason" value={cancelReason} maxLength={500}
                       placeholder="e.g. Ordered by mistake"
                       onChange={(e) => setCancelReason(e.target.value)} />
              </div>
              {cancelError && <p className="error-text">{cancelError}</p>}
              <div className="flex gap-1">
                <button type="submit" className="btn-danger" disabled={cancelling}>
                  {cancelling ? 'Cancelling...' : 'Confirm cancellation'}
                </button>
                <button type="button" className="btn-secondary" disabled={cancelling}
                        onClick={() => { setShowCancelForm(false); setCancelReason(''); setCancelError(''); }}>
                  Keep order
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {isDelivered && (
        <section className="card">
          <h3 style={{ marginTop: 0 }}>Returns</h3>

          {returnMessage && <p style={{ color: 'var(--color-success)' }}>{returnMessage}</p>}

          {returns.length > 0 && (
            <div className="grid" style={{ gap: '0.75rem', marginBottom: '1rem' }}>
              {returns.map((r) => (
                <div key={r.id} className="card" style={{ background: 'var(--color-bg)', padding: '0.9rem' }}>
                  <div className="flex-between flex-wrap gap-1">
                    <strong>Return #{r.id}</strong>
                    <span className={returnBadgeClass(r.status)}>{r.status}</span>
                  </div>
                  <div className="muted" style={{ fontSize: '0.8rem', marginTop: '0.25rem' }}>
                    Requested {formatDateTime(r.createdAt)}
                  </div>
                  <p style={{ margin: '0.5rem 0 0' }}><strong>Reason:</strong> {r.reason}</p>
                  {r.adminNote && <p style={{ margin: '0.25rem 0 0' }}><strong>Note from store:</strong> {r.adminNote}</p>}
                </div>
              ))}
            </div>
          )}

          {canRequestReturn ? (
            <form onSubmit={handleReturn}>
              <div className="form-group">
                <label htmlFor="return-reason">Reason for return</label>
                <textarea id="return-reason" rows={3} required maxLength={1000} value={returnReason}
                          placeholder="Tell us what went wrong"
                          onChange={(e) => setReturnReason(e.target.value)} />
              </div>
              {returnError && <p className="error-text">{returnError}</p>}
              <button type="submit" disabled={submittingReturn}>
                {submittingReturn ? 'Submitting...' : 'Request return'}
              </button>
            </form>
          ) : (
            returns.length > 0 && !returnMessage && (
              <p className="muted" style={{ margin: 0 }}>A return has already been requested for this order.</p>
            )
          )}
        </section>
      )}
    </div>
  );
}
