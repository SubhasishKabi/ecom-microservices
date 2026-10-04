import { useEffect, useState } from 'react';
import api from '../../api/client';

const PAGE_SIZE = 10;

const STATUSES = ['PENDING', 'PROCESSING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

const STATUS_BADGE = {
  PENDING: 'badge badge-warning',
  PROCESSING: 'badge badge-warning',
  CONFIRMED: 'badge',
  SHIPPED: 'badge',
  DELIVERED: 'badge badge-success',
  CANCELLED: 'badge badge-danger',
};

// The only admin transitions the backend allows: CONFIRMED -> SHIPPED -> DELIVERED.
const NEXT_ACTION = {
  CONFIRMED: { status: 'SHIPPED', label: 'Mark Shipped' },
  SHIPPED: { status: 'DELIVERED', label: 'Mark Delivered' },
};

const smallBtn = { padding: '0.35rem 0.65rem', fontSize: '0.85rem' };

function errMsg(err, fallback = 'Request failed') {
  const res = err?.response;
  const data = res?.data;
  if (data && typeof data === 'object' && data.message) return data.message;
  if (typeof data === 'string' && data.trim() && data.length < 300) return data;
  if (res) return `${fallback} (HTTP ${res.status})`;
  return err?.message ? `${fallback}: ${err.message}` : fallback;
}

function readPage(data) {
  const meta = data?.page || data || {};
  return {
    content: Array.isArray(data?.content) ? data.content : [],
    totalPages: meta.totalPages ?? 0,
    totalElements: meta.totalElements ?? 0,
  };
}

function money(v) {
  if (v == null || v === '' || Number.isNaN(Number(v))) return '—';
  return `$${Number(v).toFixed(2)}`;
}

function fmtDate(v) {
  if (!v) return '—';
  const d = Array.isArray(v)
    ? new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0)
    : new Date(v);
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString();
}

function shortAddress(a) {
  if (!a) return '—';
  return [a.city, a.state, a.zipcode, a.country].filter(Boolean).join(', ') || '—';
}

function fullAddress(a) {
  if (!a) return '';
  return [a.street, a.city, a.state, a.zipcode, a.country].filter(Boolean).join(', ');
}

function Pager({ page, totalPages, onChange, disabled }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="flex gap-1" style={{ alignItems: 'center', justifyContent: 'flex-end', marginTop: '0.75rem' }}>
      <button className="btn-secondary" style={smallBtn} disabled={disabled || page <= 0} onClick={() => onChange(page - 1)}>
        Prev
      </button>
      <span className="muted" style={{ fontSize: '0.9rem' }}>Page {page + 1} of {totalPages}</span>
      <button className="btn-secondary" style={smallBtn} disabled={disabled || page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </div>
  );
}

export default function AdminOrders() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .get('/api/orders/admin/all', {
        params: { status: status || undefined, page, size: PAGE_SIZE, sort: 'createdAt,desc' },
      })
      .then((res) => {
        if (cancelled) return;
        const p = readPage(res.data);
        if (p.content.length === 0 && page > 0 && p.totalPages > 0 && page >= p.totalPages) {
          setPage(p.totalPages - 1);
          return;
        }
        setRows(p.content);
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(errMsg(err, 'Could not load orders'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [status, page, reloadTick]);

  const reload = () => setReloadTick((t) => t + 1);

  const changeFilter = (e) => {
    setStatus(e.target.value);
    setPage(0);
    setActionError('');
    setNotice('');
  };

  const advance = async (order) => {
    const next = NEXT_ACTION[order.status];
    if (!next) return;
    setBusyId(order.id);
    setActionError('');
    setNotice('');
    try {
      await api.put(`/api/orders/admin/${order.id}/status`, { status: next.status });
      setNotice(`Order #${order.id} marked ${next.status}.`);
      reload();
    } catch (err) {
      setActionError(`Order #${order.id}: ${errMsg(err, 'Status update failed')}`);
      // 409 = invalid transition, usually because the order changed in the meantime; re-sync the list.
      if (err?.response?.status === 409) reload();
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h2 className="page-title">Orders</h2>

      <div className="card">
        <div className="flex-between flex-wrap gap-1" style={{ marginBottom: '0.75rem' }}>
          <div className="flex gap-1" style={{ alignItems: 'center' }}>
            <label htmlFor="order-status-filter" style={{ fontWeight: 600, fontSize: '0.9rem' }}>Status</label>
            <select id="order-status-filter" value={status} onChange={changeFilter} style={{ width: 200 }}>
              <option value="">All</option>
              {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="flex gap-1" style={{ alignItems: 'center' }}>
            <span className="muted" style={{ fontSize: '0.9rem' }}>
              {loading ? 'Loading...' : `${totalElements} order${totalElements === 1 ? '' : 's'}`}
            </span>
            <button className="btn-secondary" style={smallBtn} onClick={reload} disabled={loading}>Refresh</button>
          </div>
        </div>

        {notice && <p style={{ color: 'var(--color-success)', margin: '0 0 0.5rem' }}>{notice}</p>}
        {actionError && <p className="error-text" style={{ marginTop: 0 }}>{actionError}</p>}

        {error ? (
          <div>
            <p className="error-text">{error}</p>
            <button className="btn-secondary" onClick={reload}>Retry</button>
          </div>
        ) : loading && rows.length === 0 ? (
          <div className="spinner" />
        ) : rows.length === 0 ? (
          <p className="muted">No orders{status ? ` with status ${status}` : ''}.</p>
        ) : (
          <div style={{ overflowX: 'auto', opacity: loading ? 0.5 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Created</th>
                  <th>Ship to</th>
                  <th>Items</th>
                  <th>Total</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((o) => {
                  const items = Array.isArray(o.items) ? o.items : [];
                  const qty = items.reduce((sum, it) => sum + (Number(it.quantity) || 0), 0);
                  const itemsTitle = items.map((it) => `Product #${it.productId} x ${it.quantity}`).join('\n');
                  const next = NEXT_ACTION[o.status];
                  const busy = busyId === o.id;
                  return (
                    <tr key={o.id}>
                      <td style={{ fontWeight: 600 }}>#{o.id}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(o.createdAt)}</td>
                      <td title={fullAddress(o.shippingAddress)}>{shortAddress(o.shippingAddress)}</td>
                      <td title={itemsTitle}>
                        {qty}
                        <span className="muted" style={{ fontSize: '0.8rem' }}>
                          {' '}({items.length} line{items.length === 1 ? '' : 's'})
                        </span>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>{money(o.totalAmount)}</td>
                      <td>
                        <span className={STATUS_BADGE[o.status] || 'badge'}>{o.status}</span>
                        {o.status === 'CANCELLED' && o.cancellationReason && (
                          <div className="muted" style={{ fontSize: '0.75rem', marginTop: '0.2rem' }}>{o.cancellationReason}</div>
                        )}
                      </td>
                      <td>
                        {next ? (
                          <button style={smallBtn} disabled={busyId !== null} onClick={() => advance(o)}>
                            {busy ? 'Updating...' : next.label}
                          </button>
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={totalPages} onChange={setPage} disabled={loading} />
      </div>
    </div>
  );
}
