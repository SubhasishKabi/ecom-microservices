import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api/client';

// Statuses counted as "sales". PENDING/PROCESSING are not yet paid/confirmed and CANCELLED never completed.
const REVENUE_STATUSES = ['CONFIRMED', 'SHIPPED', 'DELIVERED'];
// Most recent N orders fetched per status. Anything older is NOT included in the figures below.
const ORDER_SAMPLE_SIZE = 200;
const TOP_N = 10;

const cardGrid = { gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '1rem' };

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
  if (v == null || Number.isNaN(Number(v))) return '—';
  return `$${Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function settle(promise, label) {
  return promise
    .then((res) => ({ ok: true, data: res.data }))
    .catch((err) => ({ ok: false, error: `${label}: ${errMsg(err, 'request failed')}` }));
}

function StatCard({ label, value, hint }) {
  return (
    <div className="card">
      <div style={{ fontSize: '1.8rem', fontWeight: 700, lineHeight: 1.1 }}>{value}</div>
      <div className="muted" style={{ fontSize: '0.9rem', marginTop: '0.3rem' }}>{label}</div>
      {hint && <div className="muted" style={{ fontSize: '0.75rem', marginTop: '0.35rem' }}>{hint}</div>}
    </div>
  );
}

function buildReport(orderResults) {
  const errors = [];
  const truncated = [];
  // Union by id: an order could show up under two statuses if it transitioned between our calls.
  const byId = new Map();

  orderResults.forEach((r, i) => {
    const s = REVENUE_STATUSES[i];
    if (!r.ok) {
      errors.push(r.error);
      return;
    }
    const p = readPage(r.data);
    p.content.forEach((o) => {
      if (o && o.id != null) byId.set(o.id, o);
    });
    if (p.totalElements > p.content.length) {
      truncated.push({ status: s, included: p.content.length, total: p.totalElements });
    }
  });

  const orders = Array.from(byId.values());
  const perStatus = {};
  REVENUE_STATUSES.forEach((s) => { perStatus[s] = { count: 0, revenue: 0 }; });

  let totalRevenue = 0;
  const productAgg = new Map();

  orders.forEach((o) => {
    const amount = Number(o.totalAmount) || 0;
    totalRevenue += amount;
    if (perStatus[o.status]) {
      perStatus[o.status].count += 1;
      perStatus[o.status].revenue += amount;
    }
    (Array.isArray(o.items) ? o.items : []).forEach((it) => {
      if (it?.productId == null) return;
      const key = String(it.productId);
      const qty = Number(it.quantity) || 0;
      const lineTotal = it.subTotal != null ? Number(it.subTotal) || 0 : (Number(it.price) || 0) * qty;
      const cur = productAgg.get(key) || { productId: key, quantity: 0, revenue: 0 };
      cur.quantity += qty;
      cur.revenue += lineTotal;
      productAgg.set(key, cur);
    });
  });

  // productId is shown as-is: resolving names would need one product-service call per id (N calls), skipped for simplicity.
  const topProducts = Array.from(productAgg.values())
    .sort((a, b) => b.quantity - a.quantity)
    .slice(0, TOP_N);

  return {
    errors,
    truncated,
    orderCount: orders.length,
    totalRevenue,
    avgOrderValue: orders.length ? totalRevenue / orders.length : 0,
    perStatus,
    topProducts,
    anyOrdersLoaded: orderResults.some((r) => r.ok),
  };
}

export default function AdminReports() {
  const [report, setReport] = useState(null);
  const [lowStock, setLowStock] = useState({ ok: true, items: [], error: '' });
  const [loading, setLoading] = useState(true);
  const [fatal, setFatal] = useState('');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFatal('');

    Promise.all([
      ...REVENUE_STATUSES.map((s) =>
        settle(
          api.get('/api/orders/admin/all', {
            params: { status: s, page: 0, size: ORDER_SAMPLE_SIZE, sort: 'createdAt,desc' },
          }),
          `${s} orders`
        )
      ),
      settle(api.get('/api/inventory/low-stock'), 'Low-stock items'),
    ])
      .then((results) => {
        if (cancelled) return;
        const orderResults = results.slice(0, REVENUE_STATUSES.length);
        const low = results[REVENUE_STATUSES.length];
        setReport(buildReport(orderResults));
        setLowStock(
          low.ok
            ? { ok: true, items: Array.isArray(low.data) ? low.data : [], error: '' }
            : { ok: false, items: [], error: low.error }
        );
      })
      .catch(() => {
        if (!cancelled) setFatal('Something went wrong while building the report.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [tick]);

  const maxQty = report && report.topProducts.length ? report.topProducts[0].quantity : 0;

  return (
    <div>
      <div className="flex-between flex-wrap gap-1">
        <h2 className="page-title">Reports</h2>
        <button className="btn-secondary" onClick={() => setTick((t) => t + 1)} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      <div className="card" style={{ marginBottom: '1.25rem', borderColor: 'var(--color-warning)' }}>
        <span className="badge badge-warning">Approximate</span>
        <p style={{ margin: '0.5rem 0 0', fontSize: '0.9rem' }}>
          There is no dedicated reporting endpoint in the backend. Everything on this page is aggregated in your browser
          from the regular admin APIs, using at most the {ORDER_SAMPLE_SIZE} most recent orders for each of{' '}
          {REVENUE_STATUSES.join(', ')}. Revenue is <strong>gross</strong> (order totals incl. tax and shipping; refunds and
          returns are not subtracted) and excludes PENDING, PROCESSING and CANCELLED orders. Treat these figures as
          indicative, not as accounting numbers.
        </p>
      </div>

      {fatal && <p className="error-text">{fatal}</p>}

      {loading && !report ? (
        <div className="spinner" />
      ) : report ? (
        <div style={{ opacity: loading ? 0.6 : 1 }}>
          {report.errors.length > 0 && (
            <div className="card" style={{ marginBottom: '1rem', borderColor: 'var(--color-danger)' }}>
              <p className="error-text" style={{ marginTop: 0 }}>
                Some order data could not be loaded, so the figures below are incomplete:
              </p>
              <ul className="muted" style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.85rem' }}>
                {report.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </div>
          )}

          {report.truncated.length > 0 && (
            <div className="card" style={{ marginBottom: '1rem' }}>
              <p style={{ marginTop: 0, fontSize: '0.9rem' }}>
                <span className="badge badge-warning">Partial data</span> Only the most recent orders were included:
              </p>
              <ul className="muted" style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.85rem' }}>
                {report.truncated.map((t) => (
                  <li key={t.status}>{t.status}: {t.included} of {t.total} orders</li>
                ))}
              </ul>
            </div>
          )}

          {/* Sales / revenue */}
          <h3 style={{ marginTop: 0 }}>Sales</h3>
          {report.anyOrdersLoaded ? (
            <>
              <div className="grid" style={cardGrid}>
                <StatCard label="Total revenue (gross)" value={money(report.totalRevenue)} />
                <StatCard label="Orders counted" value={report.orderCount} />
                <StatCard label="Average order value" value={report.orderCount ? money(report.avgOrderValue) : '—'} />
              </div>
              <div className="card" style={{ marginTop: '1rem', overflowX: 'auto' }}>
                <table>
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Orders</th>
                      <th>Revenue (gross)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {REVENUE_STATUSES.map((s) => (
                      <tr key={s}>
                        <td><span className={s === 'DELIVERED' ? 'badge badge-success' : 'badge'}>{s}</span></td>
                        <td>{report.perStatus[s].count}</td>
                        <td>{money(report.perStatus[s].revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            <p className="muted">No order data available.</p>
          )}

          {/* Top products */}
          <h3 style={{ marginTop: '1.5rem' }}>Top {TOP_N} products by units sold</h3>
          <div className="card">
            {report.topProducts.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>No order items in the sampled orders.</p>
            ) : (
              <>
                <div className="flex" style={{ flexDirection: 'column', gap: '0.65rem' }}>
                  {report.topProducts.map((p) => {
                    const pct = maxQty > 0 ? Math.max((p.quantity / maxQty) * 100, 2) : 0;
                    return (
                      <div key={p.productId}>
                        <div className="flex-between gap-1" style={{ fontSize: '0.85rem', marginBottom: '0.2rem' }}>
                          <Link to={`/products/${p.productId}`}>Product #{p.productId}</Link>
                          <span className="muted">
                            <strong style={{ color: 'var(--color-text)' }}>{p.quantity}</strong> sold · {money(p.revenue)}
                          </span>
                        </div>
                        <div style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', borderRadius: 999, height: 12, overflow: 'hidden' }}>
                          <div style={{ width: `${pct}%`, height: '100%', background: 'var(--color-primary)', borderRadius: 999 }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
                <p className="muted" style={{ fontSize: '0.75rem', margin: '0.75rem 0 0' }}>
                  Products are shown by ID only (resolving names would need one product-service call per product). Revenue
                  per product is the sum of line subtotals in the sampled orders.
                </p>
              </>
            )}
          </div>

          {/* Low stock */}
          <h3 style={{ marginTop: '1.5rem' }}>
            Low stock{' '}
            {lowStock.ok && (
              <span className={lowStock.items.length > 0 ? 'badge badge-warning' : 'badge badge-success'}>
                {lowStock.items.length}
              </span>
            )}
          </h3>
          <div className="card">
            {!lowStock.ok ? (
              <p className="error-text" style={{ margin: 0 }}>{lowStock.error}</p>
            ) : lowStock.items.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>No products are at or below their low-stock threshold.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table>
                  <thead>
                    <tr>
                      <th>Product ID</th>
                      <th>Product</th>
                      <th>Total</th>
                      <th>Reserved</th>
                      <th>Available</th>
                      <th>Threshold</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lowStock.items.map((item) => (
                      <tr key={item.productId}>
                        <td>{item.productId}</td>
                        <td>{item.productName || <span className="muted">—</span>}</td>
                        <td>{item.totalStock}</td>
                        <td>{item.reservedStock}</td>
                        <td>
                          <span className={item.availableStock <= 0 ? 'badge badge-danger' : 'badge badge-warning'}>
                            {item.availableStock}
                          </span>
                        </td>
                        <td>{item.lowStockThreshold}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="muted" style={{ fontSize: '0.8rem', margin: '0.75rem 0 0' }}>
              Restock from the <Link to="/admin/inventory">Inventory</Link> page.
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
