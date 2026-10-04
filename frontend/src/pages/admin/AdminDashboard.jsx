import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api/client';

const ORDER_STATUSES = ['PENDING', 'PROCESSING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

// The returns endpoint has no status filter, so pending returns are counted client-side within this sample.
const RETURNS_SAMPLE_SIZE = 50;

const cardGrid = { gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', gap: '1rem' };

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

// Never rejects: resolves to { ok, data } or { ok: false, error } so one failing call doesn't blank the dashboard.
function settle(promise, label) {
  return promise
    .then((res) => ({ ok: true, data: res.data }))
    .catch((err) => ({ ok: false, error: `${label}: ${errMsg(err, 'request failed')}` }));
}

function StatCard({ label, value, hint, to, tone }) {
  const color =
    tone === 'warning' ? 'var(--color-warning)'
      : tone === 'danger' ? 'var(--color-danger)'
        : tone === 'success' ? 'var(--color-success)'
          : 'var(--color-text)';
  const body = (
    <>
      <div style={{ fontSize: '2rem', fontWeight: 700, lineHeight: 1.1, color }}>{value == null ? '—' : value}</div>
      <div className="muted" style={{ fontSize: '0.9rem', marginTop: '0.3rem' }}>{label}</div>
      {hint && <div className="muted" style={{ fontSize: '0.75rem', marginTop: '0.35rem' }}>{hint}</div>}
    </>
  );
  if (to) {
    return (
      <Link to={to} className="card" style={{ display: 'block', color: 'inherit', textDecoration: 'none' }}>
        {body}
      </Link>
    );
  }
  return <div className="card">{body}</div>;
}

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [fatal, setFatal] = useState('');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFatal('');

    Promise.all([
      settle(api.get('/api/inventory/low-stock'), 'Low-stock items'),
      // Approximation: there is no status filter on the returns endpoint, so we fetch the most recent
      // RETURNS_SAMPLE_SIZE returns and count REQUESTED ones client-side. Older pending requests beyond the
      // sample are not counted, so this can under-report on large datasets.
      settle(
        api.get('/api/orders/returns/admin/all', { params: { page: 0, size: RETURNS_SAMPLE_SIZE, sort: 'createdAt,desc' } }),
        'Return requests'
      ),
      // One tiny (size=1) call per status; we only read totalElements from the page metadata.
      ...ORDER_STATUSES.map((s) =>
        settle(api.get('/api/orders/admin/all', { params: { status: s, page: 0, size: 1 } }), `${s} orders`)
      ),
    ])
      .then(([low, returns, ...orderResults]) => {
        if (cancelled) return;
        const errors = [];

        let lowStockCount = null;
        if (low.ok) lowStockCount = Array.isArray(low.data) ? low.data.length : 0;
        else errors.push(low.error);

        let pendingReturns = null;
        let returnsSampled = 0;
        let returnsTotal = 0;
        if (returns.ok) {
          const p = readPage(returns.data);
          pendingReturns = p.content.filter((r) => r.status === 'REQUESTED').length;
          returnsSampled = p.content.length;
          returnsTotal = p.totalElements;
        } else {
          errors.push(returns.error);
        }

        const orderCounts = {};
        let totalOrders = 0;
        let allOrdersOk = true;
        orderResults.forEach((r, i) => {
          const s = ORDER_STATUSES[i];
          if (r.ok) {
            orderCounts[s] = readPage(r.data).totalElements;
            totalOrders += orderCounts[s];
          } else {
            orderCounts[s] = null;
            allOrdersOk = false;
            errors.push(r.error);
          }
        });

        setStats({
          lowStockCount,
          pendingReturns,
          returnsSampled,
          returnsTotal,
          orderCounts,
          totalOrders: allOrdersOk ? totalOrders : null,
          errors,
        });
      })
      .catch(() => {
        if (!cancelled) setFatal('Something went wrong while building the dashboard.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [tick]);

  const pendingHint =
    stats && stats.pendingReturns != null && stats.returnsTotal > stats.returnsSampled
      ? `Approximate: counted within the ${stats.returnsSampled} most recent of ${stats.returnsTotal} returns`
      : undefined;

  return (
    <div>
      <div className="flex-between flex-wrap gap-1">
        <h2 className="page-title">Dashboard</h2>
        <button className="btn-secondary" onClick={() => setTick((t) => t + 1)} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {fatal && <p className="error-text">{fatal}</p>}

      {loading && !stats ? (
        <div className="spinner" />
      ) : stats ? (
        <div style={{ opacity: loading ? 0.6 : 1 }}>
          {stats.errors.length > 0 && (
            <div className="card" style={{ marginBottom: '1rem', borderColor: 'var(--color-danger)' }}>
              <p className="error-text" style={{ marginTop: 0 }}>Some figures could not be loaded:</p>
              <ul className="muted" style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.85rem' }}>
                {stats.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </div>
          )}

          <h3 style={{ marginTop: 0 }}>Needs attention</h3>
          <div className="grid" style={cardGrid}>
            <StatCard
              label="Low-stock items"
              value={stats.lowStockCount}
              tone={stats.lowStockCount > 0 ? 'warning' : undefined}
              to="/admin/inventory"
            />
            <StatCard
              label="Pending return requests"
              value={stats.pendingReturns}
              tone={stats.pendingReturns > 0 ? 'warning' : undefined}
              hint={pendingHint}
              to="/admin/returns"
            />
            <StatCard
              label="Orders awaiting shipment (CONFIRMED)"
              value={stats.orderCounts.CONFIRMED}
              to="/admin/orders"
            />
          </div>

          <h3 style={{ marginTop: '1.5rem' }}>Orders by status</h3>
          <div className="grid" style={cardGrid}>
            {ORDER_STATUSES.map((s) => (
              <StatCard
                key={s}
                label={s}
                value={stats.orderCounts[s]}
                tone={s === 'DELIVERED' ? 'success' : s === 'CANCELLED' ? 'danger' : undefined}
              />
            ))}
            <StatCard label="All orders" value={stats.totalOrders} to="/admin/orders" />
          </div>

          <p className="muted" style={{ fontSize: '0.8rem', marginTop: '1rem' }}>
            Figures are composed client-side from the regular admin APIs (no dedicated stats endpoint exists). For revenue and
            top products see <Link to="/admin/reports">Reports</Link>.
          </p>
        </div>
      ) : null}
    </div>
  );
}
