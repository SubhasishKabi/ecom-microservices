import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/client';

const PAGE_SIZE = 10;

const formatMoney = (value) => `$${Number(value || 0).toFixed(2)}`;

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? String(value)
    : d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function statusBadgeClass(status) {
  if (status === 'CANCELLED') return 'badge badge-danger';
  if (status === 'CONFIRMED' || status === 'SHIPPED' || status === 'DELIVERED') return 'badge badge-success';
  return 'badge';
}

export default function Orders() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api.get('/api/orders', { params: { page, size: PAGE_SIZE } })
      .then(({ data }) => {
        if (cancelled) return;
        setOrders(data?.content || []);
        setTotalPages(data?.totalPages ?? data?.page?.totalPages ?? 0);
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Could not load your orders. Please try again.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [page]);

  if (loading && orders.length === 0) return <div className="spinner" />;

  return (
    <div>
      <h2 className="page-title">My orders</h2>

      {error && <p className="error-text">{error}</p>}

      {!error && orders.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
          <p className="muted">You haven't placed any orders yet.</p>
          <Link to="/products" className="btn">Start shopping</Link>
        </div>
      )}

      {orders.length > 0 && (
        <div className="card" style={{ padding: 0, overflowX: 'auto', opacity: loading ? 0.6 : 1 }}>
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Placed</th>
                <th>Items</th>
                <th>Total</th>
                <th>Status</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => {
                const itemCount = (o.items || []).reduce((sum, i) => sum + (i.quantity || 0), 0);
                return (
                  <tr key={o.id} onClick={() => navigate(`/orders/${o.id}`)} style={{ cursor: 'pointer' }}>
                    <td>
                      <Link to={`/orders/${o.id}`} onClick={(e) => e.stopPropagation()} style={{ fontWeight: 600 }}>
                        #{o.id}
                      </Link>
                    </td>
                    <td>{formatDate(o.createdAt)}</td>
                    <td>{itemCount}</td>
                    <td style={{ fontWeight: 600 }}>{formatMoney(o.totalAmount)}</td>
                    <td><span className={statusBadgeClass(o.status)}>{o.status}</span></td>
                    <td style={{ textAlign: 'right' }}>
                      <Link to={`/orders/${o.id}`} onClick={(e) => e.stopPropagation()}>View</Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex gap-1" style={{ alignItems: 'center', justifyContent: 'center', marginTop: '1rem' }}>
          <button className="btn-secondary" disabled={page === 0 || loading} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span className="muted">Page {page + 1} of {totalPages}</span>
          <button className="btn-secondary" disabled={page >= totalPages - 1 || loading}
                  onClick={() => setPage((p) => p + 1)}>
            Next
          </button>
        </div>
      )}
    </div>
  );
}
