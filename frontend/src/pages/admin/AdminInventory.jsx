import { Fragment, useEffect, useRef, useState } from 'react';
import api from '../../api/client';

const PAGE_SIZE = 10;
const COLS = 7;

const emptySeed = { productId: '', productName: '', totalStock: '', lowStockThreshold: '' };

const smallBtn = { padding: '0.35rem 0.65rem', fontSize: '0.85rem' };

const ACTION_BADGE = {
  RESTOCK: 'badge badge-success',
  ADJUST: 'badge',
  RESERVE: 'badge badge-warning',
  RELEASE: 'badge',
  REDUCE: 'badge badge-danger',
};

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

function fmtDate(v) {
  if (!v) return '—';
  const d = Array.isArray(v)
    ? new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0)
    : new Date(v);
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString();
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

export default function AdminInventory() {
  // Low-stock alert section
  const [lowStock, setLowStock] = useState([]);
  const [lowLoading, setLowLoading] = useState(true);
  const [lowError, setLowError] = useState('');
  const [lowTick, setLowTick] = useState(0);

  // Paginated inventory table (InventoryResponse rows, keyed by productId)
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  const [notice, setNotice] = useState('');

  // Inline restock form (one row at a time)
  const [restockFor, setRestockFor] = useState(null);
  const [restockForm, setRestockForm] = useState({ quantity: '', reference: '' });
  const [restockBusy, setRestockBusy] = useState(false);
  const [restockError, setRestockError] = useState('');

  // Audit logs fetched on demand: { [productId]: { loading, error, logs } }. A present key means "expanded".
  const [audit, setAudit] = useState({});

  // Seed / adjust form
  const [seed, setSeed] = useState(emptySeed);
  const [seedBusy, setSeedBusy] = useState(false);
  const [seedMsg, setSeedMsg] = useState({ text: '', error: false });
  const seedRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    setLowLoading(true);
    setLowError('');
    api
      .get('/api/inventory/low-stock')
      .then((res) => {
        if (!cancelled) setLowStock(Array.isArray(res.data) ? res.data : []);
      })
      .catch((err) => {
        if (!cancelled) setLowError(errMsg(err, 'Could not load low-stock items'));
      })
      .finally(() => {
        if (!cancelled) setLowLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [lowTick]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .get('/api/inventory', { params: { page, size: PAGE_SIZE } })
      .then((res) => {
        if (cancelled) return;
        const p = readPage(res.data);
        setRows(p.content);
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(errMsg(err, 'Could not load inventory'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, reloadTick]);

  const reloadTable = () => setReloadTick((t) => t + 1);
  const reloadLow = () => setLowTick((t) => t + 1);

  const loadAudit = (productId) => {
    setAudit((a) => ({ ...a, [productId]: { loading: true, error: '', logs: a[productId]?.logs || [] } }));
    api
      .get(`/api/inventory/${productId}/audit-logs`)
      .then((res) => {
        const logs = Array.isArray(res.data) ? res.data : [];
        // Only store the result if the panel is still expanded.
        setAudit((a) => (a[productId] ? { ...a, [productId]: { loading: false, error: '', logs } } : a));
      })
      .catch((err) => {
        const msg = errMsg(err, 'Could not load audit log');
        setAudit((a) => (a[productId] ? { ...a, [productId]: { loading: false, error: msg, logs: [] } } : a));
      });
  };

  const toggleAudit = (productId) => {
    if (audit[productId]) {
      setAudit((a) => {
        const next = { ...a };
        delete next[productId];
        return next;
      });
    } else {
      loadAudit(productId);
    }
  };

  const openRestock = (productId) => {
    setRestockFor(productId);
    setRestockForm({ quantity: '', reference: '' });
    setRestockError('');
  };

  const submitRestock = async (e) => {
    e.preventDefault();
    const productId = restockFor;
    const quantity = Number(restockForm.quantity);
    if (restockForm.quantity === '' || !Number.isInteger(quantity) || quantity <= 0) {
      setRestockError('Quantity must be a whole number greater than zero.');
      return;
    }
    const body = { quantity };
    const reference = restockForm.reference.trim();
    if (reference) body.reference = reference;

    setRestockBusy(true);
    setRestockError('');
    setNotice('');
    try {
      const { data } = await api.post(`/api/inventory/${productId}/restock`, body);
      setRows((rs) => rs.map((r) => (r.productId === productId ? { ...r, ...(data || {}) } : r)));
      setRestockFor(null);
      setNotice(`Added ${quantity} unit${quantity === 1 ? '' : 's'} to product #${productId}.`);
      reloadLow();
      if (audit[productId]) loadAudit(productId);
    } catch (err) {
      setRestockError(errMsg(err, 'Restock failed'));
    } finally {
      setRestockBusy(false);
    }
  };

  const prefillSeed = (r) => {
    setSeed({
      productId: String(r.productId ?? ''),
      productName: r.productName || '',
      totalStock: r.totalStock != null ? String(r.totalStock) : '',
      lowStockThreshold: r.lowStockThreshold != null ? String(r.lowStockThreshold) : '',
    });
    setSeedMsg({ text: '', error: false });
    seedRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const submitSeed = async (e) => {
    e.preventDefault();
    setSeedMsg({ text: '', error: false });

    const productId = Number(seed.productId);
    const totalStock = Number(seed.totalStock);
    if (seed.productId === '' || !Number.isInteger(productId) || productId <= 0) {
      setSeedMsg({ text: 'Product ID must be a positive whole number.', error: true });
      return;
    }
    if (seed.totalStock === '' || !Number.isInteger(totalStock) || totalStock < 0) {
      setSeedMsg({ text: 'Total stock must be a whole number, zero or greater.', error: true });
      return;
    }
    const body = { productId, totalStock };
    if (seed.productName.trim()) body.productName = seed.productName.trim();
    if (seed.lowStockThreshold !== '') {
      const threshold = Number(seed.lowStockThreshold);
      if (!Number.isInteger(threshold) || threshold < 0) {
        setSeedMsg({ text: 'Low-stock threshold must be a whole number, zero or greater.', error: true });
        return;
      }
      body.lowStockThreshold = threshold;
    }

    setSeedBusy(true);
    try {
      const { data } = await api.post('/api/inventory', body);
      setSeedMsg({
        text:
          `Inventory for product #${data?.productId ?? productId} saved: total ${data?.totalStock ?? totalStock}, ` +
          `available ${data?.availableStock ?? '?'}, low-stock threshold ${data?.lowStockThreshold ?? '?'}.`,
        error: false,
      });
      setSeed(emptySeed);
      reloadTable();
      reloadLow();
      if (audit[productId]) loadAudit(productId);
    } catch (err) {
      setSeedMsg({ text: errMsg(err, 'Could not save inventory'), error: true });
    } finally {
      setSeedBusy(false);
    }
  };

  return (
    <div>
      <h2 className="page-title">Inventory</h2>

      {/* Low-stock alerts */}
      <div className="card" style={{ marginBottom: '1.25rem', borderColor: lowStock.length > 0 ? 'var(--color-warning)' : undefined }}>
        <div className="flex-between flex-wrap gap-1">
          <h3 style={{ margin: 0 }}>
            Low stock{' '}
            {!lowLoading && !lowError && (
              <span className={lowStock.length > 0 ? 'badge badge-warning' : 'badge badge-success'}>{lowStock.length}</span>
            )}
          </h3>
          <button className="btn-secondary" style={smallBtn} onClick={reloadLow} disabled={lowLoading}>Refresh</button>
        </div>
        {lowLoading ? (
          <div className="spinner" style={{ margin: '1rem auto' }} />
        ) : lowError ? (
          <p className="error-text">{lowError}</p>
        ) : lowStock.length === 0 ? (
          <p className="muted" style={{ marginBottom: 0 }}>No products are at or below their low-stock threshold.</p>
        ) : (
          <div style={{ overflowX: 'auto', marginTop: '0.5rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Product ID</th>
                  <th>Product</th>
                  <th>Available</th>
                  <th>Threshold</th>
                </tr>
              </thead>
              <tbody>
                {lowStock.map((item) => (
                  <tr key={item.productId}>
                    <td>{item.productId}</td>
                    <td>{item.productName || <span className="muted">—</span>}</td>
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
      </div>

      {notice && (
        <div className="card" style={{ marginBottom: '1rem', borderColor: 'var(--color-success)' }}>
          <div className="flex-between gap-1">
            <span>{notice}</span>
            <button className="btn-secondary" style={smallBtn} onClick={() => setNotice('')}>Dismiss</button>
          </div>
        </div>
      )}

      {/* All inventory rows */}
      <div className="card">
        <div className="flex-between flex-wrap gap-1" style={{ marginBottom: '0.5rem' }}>
          <h3 style={{ margin: 0 }}>All inventory</h3>
          <div className="flex gap-1" style={{ alignItems: 'center' }}>
            <span className="muted" style={{ fontSize: '0.9rem' }}>
              {loading ? 'Loading...' : `${totalElements} row${totalElements === 1 ? '' : 's'}`}
            </span>
            <button className="btn-secondary" style={smallBtn} onClick={reloadTable} disabled={loading}>Refresh</button>
          </div>
        </div>

        {error ? (
          <div>
            <p className="error-text">{error}</p>
            <button className="btn-secondary" onClick={reloadTable}>Retry</button>
          </div>
        ) : loading && rows.length === 0 ? (
          <div className="spinner" />
        ) : rows.length === 0 ? (
          <p className="muted">No inventory rows yet. Use the form below to seed stock for a product.</p>
        ) : (
          <div style={{ overflowX: 'auto', opacity: loading ? 0.5 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>Product ID</th>
                  <th>Product</th>
                  <th>Total</th>
                  <th>Reserved</th>
                  <th>Available</th>
                  <th>Low threshold</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const a = audit[r.productId];
                  const isLow = r.availableStock != null && r.lowStockThreshold != null && r.availableStock <= r.lowStockThreshold;
                  const restockOpen = restockFor === r.productId;
                  return (
                    <Fragment key={r.productId}>
                      <tr>
                        <td>{r.productId}</td>
                        <td>{r.productName || <span className="muted">—</span>}</td>
                        <td>{r.totalStock}</td>
                        <td>{r.reservedStock}</td>
                        <td>
                          {r.availableStock}{' '}
                          {isLow && <span className="badge badge-warning">Low</span>}
                        </td>
                        <td>{r.lowStockThreshold}</td>
                        <td>
                          <div className="flex gap-1 flex-wrap">
                            <button style={smallBtn} disabled={restockBusy && restockOpen}
                                    onClick={() => (restockOpen ? setRestockFor(null) : openRestock(r.productId))}>
                              {restockOpen ? 'Close' : 'Restock'}
                            </button>
                            <button className="btn-secondary" style={smallBtn} onClick={() => toggleAudit(r.productId)}>
                              {a ? 'Hide audit log' : 'View audit log'}
                            </button>
                            <button className="btn-secondary" style={smallBtn} onClick={() => prefillSeed(r)}
                                    title="Load this row into the seed / adjust form">
                              Adjust
                            </button>
                          </div>
                        </td>
                      </tr>

                      {restockOpen && (
                        <tr>
                          <td colSpan={COLS} style={{ background: 'var(--color-bg)' }}>
                            <form onSubmit={submitRestock} className="flex gap-1 flex-wrap" style={{ alignItems: 'flex-end' }}>
                              <div className="form-group" style={{ width: 150, marginBottom: 0 }}>
                                <label>Quantity to add</label>
                                <input type="number" min="1" step="1" required autoFocus value={restockForm.quantity}
                                       onChange={(e) => setRestockForm((f) => ({ ...f, quantity: e.target.value }))} />
                              </div>
                              <div className="form-group" style={{ flex: 1, minWidth: 180, marginBottom: 0 }}>
                                <label>Reference (optional)</label>
                                <input maxLength={255} placeholder="e.g. PO-1234 (defaults to your admin account)"
                                       value={restockForm.reference}
                                       onChange={(e) => setRestockForm((f) => ({ ...f, reference: e.target.value }))} />
                              </div>
                              <button type="submit" disabled={restockBusy}>{restockBusy ? 'Saving...' : 'Add stock'}</button>
                              <button type="button" className="btn-secondary" disabled={restockBusy} onClick={() => setRestockFor(null)}>
                                Cancel
                              </button>
                            </form>
                            {restockError && <p className="error-text">{restockError}</p>}
                          </td>
                        </tr>
                      )}

                      {a && (
                        <tr>
                          <td colSpan={COLS} style={{ background: 'var(--color-bg)' }}>
                            <div className="flex-between" style={{ marginBottom: '0.4rem' }}>
                              <strong style={{ fontSize: '0.9rem' }}>Audit log - product #{r.productId} (newest first)</strong>
                              <button className="btn-secondary" style={smallBtn} disabled={a.loading} onClick={() => loadAudit(r.productId)}>
                                Reload
                              </button>
                            </div>
                            {a.loading ? (
                              <div className="spinner" style={{ margin: '0.75rem auto' }} />
                            ) : a.error ? (
                              <p className="error-text">{a.error}</p>
                            ) : a.logs.length === 0 ? (
                              <p className="muted" style={{ margin: 0 }}>No audit entries for this product.</p>
                            ) : (
                              <div style={{ maxHeight: 320, overflowY: 'auto' }}>
                                <table>
                                  <thead>
                                    <tr>
                                      <th>When</th>
                                      <th>Action</th>
                                      <th>Qty</th>
                                      <th>Stock before → after</th>
                                      <th>Reference</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {a.logs.map((log, idx) => (
                                      <tr key={log.id ?? idx}>
                                        <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(log.timestamp)}</td>
                                        <td><span className={ACTION_BADGE[log.action] || 'badge'}>{log.action}</span></td>
                                        <td>{log.quantity ?? '—'}</td>
                                        <td>{log.previousStock ?? '—'} → {log.newStock ?? '—'}</td>
                                        <td className="muted">{log.referenceId || '—'}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={totalPages} onChange={setPage} disabled={loading} />
      </div>

      {/* Seed / adjust */}
      <div className="card" style={{ marginTop: '1.25rem' }} ref={seedRef}>
        <h3 style={{ marginTop: 0 }}>Seed / adjust inventory</h3>
        <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
          Inventory rows are <strong>not</strong> created automatically when a product is created - seed stock here for each
          new product ID. If a row already exists for the product, <em>total stock</em> is set to the given absolute value
          (reserved stock is preserved and must not exceed it). Leave the threshold blank to keep the current value (new rows
          default to 10).
        </p>
        <form onSubmit={submitSeed}>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.75rem' }}>
            <div className="form-group">
              <label>Product ID *</label>
              <input type="number" min="1" step="1" required value={seed.productId}
                     onChange={(e) => setSeed((s) => ({ ...s, productId: e.target.value }))} />
            </div>
            <div className="form-group">
              <label>Product name</label>
              <input maxLength={255} value={seed.productName}
                     onChange={(e) => setSeed((s) => ({ ...s, productName: e.target.value }))} />
            </div>
            <div className="form-group">
              <label>Total stock *</label>
              <input type="number" min="0" step="1" required value={seed.totalStock}
                     onChange={(e) => setSeed((s) => ({ ...s, totalStock: e.target.value }))} />
            </div>
            <div className="form-group">
              <label>Low-stock threshold</label>
              <input type="number" min="0" step="1" placeholder="10" value={seed.lowStockThreshold}
                     onChange={(e) => setSeed((s) => ({ ...s, lowStockThreshold: e.target.value }))} />
            </div>
          </div>
          {seedMsg.text && <p className={seedMsg.error ? 'error-text' : 'muted'}>{seedMsg.text}</p>}
          <div className="flex gap-1">
            <button type="submit" disabled={seedBusy}>{seedBusy ? 'Saving...' : 'Save inventory'}</button>
            <button type="button" className="btn-secondary" disabled={seedBusy}
                    onClick={() => { setSeed(emptySeed); setSeedMsg({ text: '', error: false }); }}>
              Clear
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
