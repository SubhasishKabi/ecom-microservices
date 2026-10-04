import { Fragment, useEffect, useState } from 'react';
import api from '../../api/client';

const PAGE_SIZE = 10;
const COLS = 8;

const STATUS_BADGE = {
  REQUESTED: 'badge badge-warning',
  APPROVED: 'badge',
  REJECTED: 'badge badge-danger',
  REFUNDED: 'badge badge-success',
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

export default function AdminReturns() {
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  // Inline decision panel: { id, approve, note } for the row being decided, or null.
  const [decision, setDecision] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .get('/api/orders/returns/admin/all', { params: { page, size: PAGE_SIZE, sort: 'createdAt,desc' } })
      .then((res) => {
        if (cancelled) return;
        const p = readPage(res.data);
        setRows(p.content);
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(errMsg(err, 'Could not load return requests'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, reloadTick]);

  const reload = () => setReloadTick((t) => t + 1);

  const openDecision = (id, approve) => {
    setDecision({ id, approve, note: '' });
    setActionError('');
    setNotice('');
  };

  const submitDecision = async (e) => {
    e.preventDefault();
    if (!decision || busyId !== null) return;
    const { id, approve } = decision;
    const note = decision.note.trim();
    setBusyId(id);
    setActionError('');
    setNotice('');
    try {
      // approve=true triggers a synchronous refund via payment-service, so this can take a moment.
      const { data } = await api.put(`/api/orders/returns/admin/${id}/decision`, { approve, note: note || null });
      // There is no GET-by-id for returns; the decision response is the updated ReturnResponse, so use it for the row.
      if (data && data.id != null) {
        setRows((rs) => rs.map((r) => (r.id === id ? data : r)));
      } else {
        reload();
      }
      setDecision(null);
      setNotice(
        approve
          ? `Return #${id} approved${data?.status ? ` - status is now ${data.status}` : ''}.`
          : `Return #${id} rejected.`
      );
    } catch (err) {
      setActionError(`Return #${id}: ${errMsg(err, approve ? 'Approval / refund failed' : 'Rejection failed')}`);
      // Re-sync so the row shows its real status after a failed (possibly partial) decision.
      reload();
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h2 className="page-title">Returns &amp; Refunds</h2>

      <div className="card">
        <div className="flex-between flex-wrap gap-1" style={{ marginBottom: '0.5rem' }}>
          <span className="muted" style={{ fontSize: '0.9rem' }}>
            {loading ? 'Loading...' : `${totalElements} return request${totalElements === 1 ? '' : 's'}`}
          </span>
          <button className="btn-secondary" style={smallBtn} onClick={reload} disabled={loading || busyId !== null}>
            Refresh
          </button>
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
          <p className="muted">No return requests.</p>
        ) : (
          <div style={{ overflowX: 'auto', opacity: loading ? 0.5 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>Return</th>
                  <th>Order</th>
                  <th>User ID</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th>Admin note</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const pending = r.status === 'REQUESTED';
                  const busy = busyId === r.id;
                  const panelOpen = pending && decision && decision.id === r.id;
                  return (
                    <Fragment key={r.id}>
                      <tr>
                        <td style={{ fontWeight: 600 }}>#{r.id}</td>
                        <td>#{r.orderId}</td>
                        <td>{r.userId || <span className="muted">—</span>}</td>
                        <td style={{ maxWidth: 260 }}>{r.reason || <span className="muted">—</span>}</td>
                        <td><span className={STATUS_BADGE[r.status] || 'badge'}>{r.status}</span></td>
                        <td className="muted" style={{ maxWidth: 220 }}>{r.adminNote || '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(r.createdAt)}</td>
                        <td>
                          {pending ? (
                            <div className="flex gap-1 flex-wrap">
                              <button style={smallBtn} disabled={busyId !== null} onClick={() => openDecision(r.id, true)}>
                                Approve
                              </button>
                              <button className="btn-danger" style={smallBtn} disabled={busyId !== null}
                                      onClick={() => openDecision(r.id, false)}>
                                Reject
                              </button>
                            </div>
                          ) : (
                            <span className="muted">—</span>
                          )}
                        </td>
                      </tr>
                      {panelOpen && (
                        <tr>
                          <td colSpan={COLS} style={{ background: 'var(--color-bg)' }}>
                            <form onSubmit={submitDecision} className="flex gap-1 flex-wrap" style={{ alignItems: 'flex-end' }}>
                              <div className="form-group" style={{ flex: 1, minWidth: 220, marginBottom: 0 }}>
                                <label>{decision.approve ? 'Approval note (optional)' : 'Rejection note (optional)'}</label>
                                <input
                                  autoFocus
                                  maxLength={255}
                                  disabled={busy}
                                  placeholder={decision.approve ? 'e.g. Item received in original condition' : 'e.g. Outside return window'}
                                  value={decision.note}
                                  onChange={(e) => setDecision((d) => (d ? { ...d, note: e.target.value } : d))}
                                />
                              </div>
                              <button type="submit" className={decision.approve ? undefined : 'btn-danger'} disabled={busy}>
                                {busy
                                  ? decision.approve ? 'Processing refund...' : 'Rejecting...'
                                  : decision.approve ? 'Confirm approve & refund' : 'Confirm reject'}
                              </button>
                              <button type="button" className="btn-secondary" disabled={busy} onClick={() => setDecision(null)}>
                                Cancel
                              </button>
                            </form>
                            {decision.approve && (
                              <p className="muted" style={{ fontSize: '0.8rem', margin: '0.5rem 0 0' }}>
                                Approving immediately issues a refund through payment-service; this can take a few seconds.
                              </p>
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
        <Pager page={page} totalPages={totalPages} onChange={setPage} disabled={loading || busyId !== null} />
      </div>
    </div>
  );
}
