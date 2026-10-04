import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';

const PAGE_SIZE = 20;

function formatDateTime(value) {
  if (!value) return '';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

function typeLabel(type) {
  if (!type) return 'General';
  const text = type.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function typeBadgeClass(type) {
  if (type === 'ORDER_CANCELLED' || type === 'PAYMENT_FAILED') return 'badge badge-danger';
  if (type === 'ORDER_CONFIRMED' || type === 'ORDER_SHIPPED' || type === 'ORDER_DELIVERED'
    || type === 'REFUND_COMPLETED') return 'badge badge-success';
  if (type === 'BACK_IN_STOCK') return 'badge badge-warning';
  return 'badge';
}

// Order/payment/refund notifications carry the order ID as referenceId; back-in-stock carries the product ID.
function linkFor(n) {
  if (!n.referenceId) return null;
  if (n.type?.startsWith('ORDER_') || n.type === 'PAYMENT_FAILED' || n.type === 'REFUND_COMPLETED') {
    return { to: `/orders/${n.referenceId}`, label: 'View order' };
  }
  if (n.type === 'BACK_IN_STOCK') return { to: `/products/${n.referenceId}`, label: 'View product' };
  return null;
}

export default function Notifications() {
  const [notifications, setNotifications] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [markingAll, setMarkingAll] = useState(false);
  // Guards against double-clicks firing duplicate mark-read requests.
  const pendingReads = useRef(new Set());

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    Promise.all([
      api.get('/api/notifications', { params: { unreadOnly, page, size: PAGE_SIZE } }),
      api.get('/api/notifications/unread-count').catch(() => null),
    ])
      .then(([listRes, countRes]) => {
        if (cancelled) return;
        const data = listRes.data || {};
        setNotifications(data.content || []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        if (countRes) setUnreadCount(countRes.data?.count ?? 0);
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Could not load notifications. Please try again.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [page, unreadOnly, reloadKey]);

  const markRead = async (n) => {
    if (n.isRead || pendingReads.current.has(n.id)) return;
    pendingReads.current.add(n.id);
    setActionError('');
    try {
      const { data } = await api.put(`/api/notifications/${n.id}/read`);
      setNotifications((list) => list.map((x) => (x.id === n.id ? { ...x, ...(data || {}), isRead: true } : x)));
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not mark the notification as read.');
    } finally {
      pendingReads.current.delete(n.id);
    }
  };

  const markAllRead = async () => {
    setMarkingAll(true);
    setActionError('');
    try {
      await api.put('/api/notifications/read-all');
      // In "unread only" mode the current page may no longer exist, so go back to the first one.
      if (unreadOnly) setPage(0);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not mark notifications as read.');
    } finally {
      setMarkingAll(false);
    }
  };

  const hasUnread = unreadCount > 0 || notifications.some((n) => !n.isRead);

  return (
    <div>
      <div className="flex-between flex-wrap gap-1">
        <h2 className="page-title">
          Notifications{' '}
          {unreadCount > 0 && <span className="badge" style={{ verticalAlign: 'middle' }}>{unreadCount} unread</span>}
        </h2>
        <div className="flex gap-2" style={{ alignItems: 'center' }}>
          <label className="flex gap-1" style={{ alignItems: 'center', cursor: 'pointer' }}>
            <input type="checkbox" style={{ width: 'auto' }} checked={unreadOnly}
                   onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
            Unread only
          </label>
          <button className="btn-secondary" disabled={markingAll || !hasUnread} onClick={markAllRead}>
            {markingAll ? 'Marking...' : 'Mark all read'}
          </button>
        </div>
      </div>

      {error && <p className="error-text">{error}</p>}
      {actionError && <p className="error-text">{actionError}</p>}

      {loading && notifications.length === 0 ? (
        <div className="spinner" />
      ) : (
        <>
          {!error && notifications.length === 0 && (
            <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
              <p className="muted" style={{ margin: 0 }}>
                {unreadOnly ? 'No unread notifications.' : 'No notifications yet.'}
              </p>
            </div>
          )}

          {notifications.length > 0 && (
            <div className="grid" style={{ gap: '0.75rem', opacity: loading ? 0.6 : 1 }}>
              {notifications.map((n) => {
                const unread = !n.isRead;
                const link = linkFor(n);
                return (
                  <div
                    key={n.id}
                    className="card"
                    role={unread ? 'button' : undefined}
                    tabIndex={unread ? 0 : undefined}
                    title={unread ? 'Mark as read' : undefined}
                    onClick={() => markRead(n)}
                    onKeyDown={(e) => {
                      // Only when the card itself is focused, so Enter on the nested link still navigates.
                      if (unread && e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
                        e.preventDefault();
                        markRead(n);
                      }
                    }}
                    style={{
                      cursor: unread ? 'pointer' : 'default',
                      background: unread ? '#f5f6ff' : 'var(--color-surface)',
                      borderLeft: unread ? '4px solid var(--color-primary)' : '1px solid var(--color-border)',
                      padding: '0.9rem 1.1rem',
                    }}
                  >
                    <div className="flex-between gap-1" style={{ alignItems: 'flex-start' }}>
                      <div className="flex gap-1 flex-wrap" style={{ alignItems: 'center' }}>
                        {unread && (
                          <span aria-label="Unread" style={{
                            width: 8, height: 8, borderRadius: '50%', background: 'var(--color-primary)',
                            display: 'inline-block', flexShrink: 0,
                          }} />
                        )}
                        <span style={{ fontWeight: unread ? 700 : 500 }}>{n.title}</span>
                        <span className={typeBadgeClass(n.type)}>{typeLabel(n.type)}</span>
                      </div>
                      <span className="muted" style={{ fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                        {formatDateTime(n.createdAt)}
                      </span>
                    </div>
                    <p style={{ margin: '0.4rem 0 0', color: unread ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
                      {n.message}
                    </p>
                    {link && (
                      <Link to={link.to} style={{ display: 'inline-block', marginTop: '0.4rem', fontSize: '0.85rem' }}>
                        {link.label} &rarr;
                      </Link>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
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
