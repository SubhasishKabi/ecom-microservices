import { useEffect, useState } from 'react';
import api from '../../api/client';
import { useAuth } from '../../context/AuthContext';

const PAGE_SIZE = 10;

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

export default function AdminUsers() {
  const { user: currentUser } = useAuth();

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
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
      .get('/api/users', { params: { search: search || undefined, page, size: PAGE_SIZE } })
      .then((res) => {
        if (cancelled) return;
        const p = readPage(res.data);
        setRows(p.content);
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(errMsg(err, 'Could not load users'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [search, page, reloadTick]);

  const reload = () => setReloadTick((t) => t + 1);

  const submitSearch = (e) => {
    e.preventDefault();
    setSearch(searchInput.trim());
    setPage(0);
  };

  const clearSearch = () => {
    setSearchInput('');
    setSearch('');
    setPage(0);
  };

  const toggleActive = async (u) => {
    const nextActive = u.active === false;
    setBusyId(u.id);
    setActionError('');
    setNotice('');
    try {
      await api.put(`/api/users/${u.id}/status`, null, { params: { active: nextActive } });
      // 200 = success (plain-text body); patch the row in place.
      setRows((rs) => rs.map((r) => (r.id === u.id ? { ...r, active: nextActive } : r)));
      setNotice(`${u.email || `User #${u.id}`} ${nextActive ? 'activated' : 'deactivated'}.`);
    } catch (err) {
      setActionError(`User #${u.id}: ${errMsg(err, 'Status update failed')}`);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h2 className="page-title">Users</h2>

      <div className="card">
        <form onSubmit={submitSearch} className="flex gap-1 flex-wrap" style={{ alignItems: 'center', marginBottom: '0.75rem' }}>
          <input
            style={{ flex: 1, minWidth: 200 }}
            placeholder="Search by name or email"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          <button type="submit">Search</button>
          {search && <button type="button" className="btn-secondary" onClick={clearSearch}>Clear</button>}
        </form>

        <div className="flex-between flex-wrap gap-1" style={{ marginBottom: '0.5rem' }}>
          <span className="muted" style={{ fontSize: '0.9rem' }}>
            {loading ? 'Loading...' : `${totalElements} user${totalElements === 1 ? '' : 's'}${search ? ` matching "${search}"` : ''}`}
          </span>
          <button className="btn-secondary" style={smallBtn} onClick={reload} disabled={loading}>Refresh</button>
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
          <p className="muted">No users found.</p>
        ) : (
          <div style={{ overflowX: 'auto', opacity: loading ? 0.5 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => {
                  const name = `${u.firstName || ''} ${u.lastName || ''}`.trim();
                  const isActive = u.active !== false;
                  const isSelf = currentUser && currentUser.id != null && String(currentUser.id) === String(u.id);
                  const busy = busyId === u.id;
                  return (
                    <tr key={u.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{name || <span className="muted">—</span>}</div>
                        <div className="muted" style={{ fontSize: '0.8rem' }}>#{u.id}</div>
                      </td>
                      <td>{u.email}</td>
                      <td>{u.phone || <span className="muted">—</span>}</td>
                      <td>
                        <span className={u.role === 'ADMIN' ? 'badge badge-warning' : 'badge'}>{u.role || 'UNKNOWN'}</span>
                      </td>
                      <td>
                        {isActive
                          ? <span className="badge badge-success">Active</span>
                          : <span className="badge badge-danger">Inactive</span>}
                      </td>
                      <td>
                        <button
                          className={isActive ? 'btn-danger' : undefined}
                          style={smallBtn}
                          disabled={busy || isSelf}
                          title={isSelf ? 'You cannot deactivate your own account' : undefined}
                          onClick={() => toggleActive(u)}
                        >
                          {busy ? 'Updating...' : isActive ? 'Deactivate' : 'Activate'}
                        </button>
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
