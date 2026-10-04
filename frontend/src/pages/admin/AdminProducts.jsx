import { useEffect, useState } from 'react';
import api from '../../api/client';

const PAGE_SIZE = 10;

const emptyForm = {
  name: '',
  description: '',
  price: '',
  discount: '',
  brand: '',
  category: '',
  stockQuantity: '',
  imageUrl: '',
  active: true,
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

// Tolerates both the classic Spring Page JSON and the newer { content, page: {...} } DTO shape.
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

export default function AdminProducts() {
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');

  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState('');

  const [reactivateId, setReactivateId] = useState('');
  const [reactivating, setReactivating] = useState(false);
  const [reactivateMsg, setReactivateMsg] = useState({ text: '', error: false });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .get('/api/products', { params: { page, size: PAGE_SIZE, sort: 'createdAt,desc' } })
      .then((res) => {
        if (cancelled) return;
        const p = readPage(res.data);
        // If the current page no longer exists (e.g. items were deactivated), step back to the last page.
        if (p.content.length === 0 && page > 0 && p.totalPages > 0 && page >= p.totalPages) {
          setPage(p.totalPages - 1);
          return;
        }
        setRows(p.content);
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(errMsg(err, 'Could not load products'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, reloadTick]);

  // Category / brand suggestions for the form. Failure is harmless: the inputs are free text.
  useEffect(() => {
    Promise.all([api.get('/api/products/categories'), api.get('/api/products/brands')])
      .then(([catRes, brandRes]) => {
        setCategories((Array.isArray(catRes.data) ? catRes.data : []).filter(Boolean));
        setBrands((Array.isArray(brandRes.data) ? brandRes.data : []).filter(Boolean));
      })
      .catch(() => {});
  }, []);

  const reload = () => setReloadTick((t) => t + 1);

  const setField = (field) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [field]: value }));
  };

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
    setNotice('');
    setFormOpen(true);
  };

  const startEdit = (p) => {
    setEditingId(p.id);
    setForm({
      name: p.name || '',
      description: p.description || '',
      price: p.price != null ? String(p.price) : '',
      discount: p.discount != null ? String(Number(p.discount)) : '',
      brand: p.brand || '',
      category: p.category || '',
      stockQuantity: p.stockQuantity != null ? String(p.stockQuantity) : '',
      imageUrl: p.imageUrl || '',
      active: p.active !== false,
    });
    setFormError('');
    setNotice('');
    setFormOpen(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
  };

  const submitForm = async (e) => {
    e.preventDefault();
    setFormError('');
    setNotice('');

    const price = Number(form.price);
    const discount = form.discount === '' ? 0 : Number(form.discount);
    const stockQuantity = form.stockQuantity === '' ? 0 : Number(form.stockQuantity);

    if (!form.name.trim()) { setFormError('Name is required.'); return; }
    if (!form.category.trim()) { setFormError('Category is required.'); return; }
    if (form.price === '' || !(price > 0)) { setFormError('Price must be greater than zero.'); return; }
    if (Number.isNaN(discount) || discount < 0 || discount > 100) {
      setFormError('Discount is a percentage and must be between 0 and 100.');
      return;
    }
    if (!Number.isInteger(stockQuantity) || stockQuantity < 0) {
      setFormError('Stock quantity must be a whole number, zero or greater.');
      return;
    }

    // ProductRequest shape. Strings are sent trimmed (possibly empty) so clearing a field on edit actually clears it.
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      price,
      discount,
      brand: form.brand.trim(),
      category: form.category.trim(),
      stockQuantity,
      imageUrl: form.imageUrl.trim(),
      active: !!form.active,
    };

    setSaving(true);
    try {
      if (editingId) {
        const id = editingId;
        const { data } = await api.put(`/api/products/${id}`, payload);
        setRows((rs) => rs.map((r) => (r.id === id ? (data && data.id != null ? data : { ...r, ...payload }) : r)));
        setNotice(`Product #${id} updated.`);
        closeForm();
      } else {
        const { data } = await api.post('/api/products', payload);
        setForm(emptyForm);
        setNotice(
          `Product #${data?.id ?? ''} "${data?.name ?? payload.name}" created. Inventory rows are not created automatically - ` +
          `seed stock for this product ID on the Inventory page before it can be ordered.`
        );
        // Newest first, so the created product shows up on page 1.
        if (page === 0) reload();
        else setPage(0);
      }
    } catch (err) {
      setFormError(errMsg(err, editingId ? 'Could not update product' : 'Could not create product'));
    } finally {
      setSaving(false);
    }
  };

  // Rows are patched in place (not refetched) because GET /api/products only returns active products:
  // a refetch would make a just-deactivated product vanish before the admin could undo it.
  const setStatus = async (p, active) => {
    setBusyId(p.id);
    setActionError('');
    setNotice('');
    try {
      await api.put(`/api/products/${p.id}/status`, null, { params: { active } });
      setRows((rs) => rs.map((r) => (r.id === p.id ? { ...r, active } : r)));
      setNotice(`Product #${p.id} ${active ? 'reactivated' : 'deactivated'}.`);
    } catch (err) {
      setActionError(`Product #${p.id}: ${errMsg(err, 'Status update failed')}`);
    } finally {
      setBusyId(null);
    }
  };

  const softDelete = async (p) => {
    const ok = window.confirm(
      `Delete "${p.name}"?\n\nThis is a soft delete: the product is deactivated (active=false) and can be reactivated later.`
    );
    if (!ok) return;
    setBusyId(p.id);
    setActionError('');
    setNotice('');
    try {
      await api.delete(`/api/products/${p.id}`);
      setRows((rs) => rs.map((r) => (r.id === p.id ? { ...r, active: false } : r)));
      setNotice(`Product #${p.id} deleted (soft delete - it is now inactive).`);
      if (editingId === p.id) closeForm();
    } catch (err) {
      setActionError(`Product #${p.id}: ${errMsg(err, 'Delete failed')}`);
    } finally {
      setBusyId(null);
    }
  };

  const reactivateById = async (e) => {
    e.preventDefault();
    const id = reactivateId.trim();
    if (!/^\d+$/.test(id)) {
      setReactivateMsg({ text: 'Enter a numeric product ID.', error: true });
      return;
    }
    setReactivating(true);
    setReactivateMsg({ text: '', error: false });
    try {
      await api.put(`/api/products/${id}/status`, null, { params: { active: true } });
      setReactivateMsg({ text: `Product #${id} reactivated.`, error: false });
      setReactivateId('');
      reload();
    } catch (err) {
      setReactivateMsg({ text: errMsg(err, `Could not reactivate product #${id}`), error: true });
    } finally {
      setReactivating(false);
    }
  };

  return (
    <div>
      <div className="flex-between flex-wrap gap-1">
        <h2 className="page-title">Products</h2>
        {!formOpen && <button onClick={openCreate}>+ New product</button>}
      </div>

      {notice && (
        <div className="card" style={{ marginBottom: '1rem', borderColor: 'var(--color-success)' }}>
          <div className="flex-between gap-1">
            <span>{notice}</span>
            <button className="btn-secondary" style={smallBtn} onClick={() => setNotice('')}>Dismiss</button>
          </div>
        </div>
      )}

      {formOpen && (
        <div className="card" style={{ marginBottom: '1.25rem' }}>
          <h3 style={{ marginTop: 0 }}>{editingId ? `Edit product #${editingId}` : 'Create product'}</h3>
          <form onSubmit={submitForm}>
            <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem' }}>
              <div className="form-group">
                <label>Name *</label>
                <input required value={form.name} onChange={setField('name')} />
              </div>
              <div className="form-group">
                <label>Category *</label>
                <input required list="admin-product-categories" value={form.category} onChange={setField('category')} />
              </div>
              <div className="form-group">
                <label>Brand</label>
                <input list="admin-product-brands" value={form.brand} onChange={setField('brand')} />
              </div>
              <div className="form-group">
                <label>Price ($) *</label>
                <input type="number" required min="0.01" step="0.01" value={form.price} onChange={setField('price')} />
              </div>
              <div className="form-group">
                <label>Discount (%)</label>
                <input type="number" min="0" max="100" step="0.01" placeholder="0" value={form.discount} onChange={setField('discount')} />
              </div>
              <div className="form-group">
                <label>Stock quantity</label>
                <input type="number" min="0" step="1" placeholder="0" value={form.stockQuantity} onChange={setField('stockQuantity')} />
              </div>
              <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                <label>Image URL</label>
                <input placeholder="https://..." value={form.imageUrl} onChange={setField('imageUrl')} />
              </div>
              <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                <label>Description</label>
                <textarea rows={3} value={form.description} onChange={setField('description')} />
              </div>
            </div>
            <label className="flex gap-1" style={{ alignItems: 'center', fontWeight: 400, marginBottom: '0.75rem' }}>
              <input type="checkbox" style={{ width: 'auto' }} checked={!!form.active} onChange={setField('active')} />
              Active (visible in the storefront)
            </label>
            <p className="muted" style={{ fontSize: '0.8rem', marginTop: 0 }}>
              Note: the stock quantity here is the catalog field only. Inventory-service stock (used for reservations at
              checkout) is managed separately on the Inventory page.
            </p>
            {formError && <p className="error-text">{formError}</p>}
            <div className="flex gap-1">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving...' : editingId ? 'Save changes' : 'Create product'}
              </button>
              <button type="button" className="btn-secondary" onClick={closeForm} disabled={saving}>
                {editingId ? 'Cancel edit' : 'Close'}
              </button>
            </div>
          </form>
          <datalist id="admin-product-categories">
            {categories.map((c) => <option key={c} value={c} />)}
          </datalist>
          <datalist id="admin-product-brands">
            {brands.map((b) => <option key={b} value={b} />)}
          </datalist>
        </div>
      )}

      <div className="card">
        <div className="flex-between flex-wrap gap-1" style={{ marginBottom: '0.5rem' }}>
          <span className="muted">
            {loading ? 'Loading...' : `${totalElements} active product${totalElements === 1 ? '' : 's'}`}
          </span>
          <button className="btn-secondary" style={smallBtn} onClick={reload} disabled={loading}>Refresh</button>
        </div>
        <p className="muted" style={{ fontSize: '0.8rem', marginTop: 0 }}>
          The product list API only returns active products. A product you deactivate or delete stays visible here (marked
          Inactive) until you refresh or change page; to reactivate an older one, use "Reactivate by ID" below.
        </p>
        {actionError && <p className="error-text">{actionError}</p>}

        {error ? (
          <div>
            <p className="error-text">{error}</p>
            <button className="btn-secondary" onClick={reload}>Retry</button>
          </div>
        ) : loading && rows.length === 0 ? (
          <div className="spinner" />
        ) : rows.length === 0 ? (
          <p className="muted">No products found.</p>
        ) : (
          <div style={{ overflowX: 'auto', opacity: loading ? 0.5 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>Image</th>
                  <th>Name</th>
                  <th>Category</th>
                  <th>Brand</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => {
                  const hasDiscount = Number(p.discount) > 0;
                  const isActive = p.active !== false;
                  const busy = busyId === p.id;
                  return (
                    <tr key={p.id} style={isActive ? undefined : { opacity: 0.65 }}>
                      <td>
                        <div style={{ width: 44, height: 44, borderRadius: 6, overflow: 'hidden', background: '#f1f2f6' }}>
                          {p.imageUrl && (
                            <img src={p.imageUrl} alt={p.name} loading="lazy"
                                 style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          )}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{p.name}</div>
                        <div className="muted" style={{ fontSize: '0.8rem' }}>#{p.id}</div>
                      </td>
                      <td>{p.category || <span className="muted">—</span>}</td>
                      <td>{p.brand || <span className="muted">—</span>}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <div style={{ fontWeight: 600 }}>{money(p.effectivePrice ?? p.price)}</div>
                        {hasDiscount && (
                          <div style={{ fontSize: '0.8rem' }}>
                            <span className="muted" style={{ textDecoration: 'line-through' }}>{money(p.price)}</span>{' '}
                            <span className="badge badge-success">-{Number(p.discount)}%</span>
                          </div>
                        )}
                      </td>
                      <td>{p.stockQuantity ?? 0}</td>
                      <td>
                        {isActive
                          ? <span className="badge badge-success">Active</span>
                          : <span className="badge badge-danger">Inactive</span>}
                      </td>
                      <td>
                        <div className="flex gap-1 flex-wrap">
                          <button className="btn-secondary" style={smallBtn} disabled={busy} onClick={() => startEdit(p)}>
                            Edit
                          </button>
                          {isActive ? (
                            <>
                              <button className="btn-secondary" style={smallBtn} disabled={busy} onClick={() => setStatus(p, false)}>
                                {busy ? '...' : 'Deactivate'}
                              </button>
                              <button className="btn-danger" style={smallBtn} disabled={busy} onClick={() => softDelete(p)}
                                      title="Soft delete: sets active=false; can be reactivated later">
                                Delete
                              </button>
                            </>
                          ) : (
                            <button style={smallBtn} disabled={busy} onClick={() => setStatus(p, true)}>
                              {busy ? '...' : 'Reactivate'}
                            </button>
                          )}
                        </div>
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

      <div className="card" style={{ marginTop: '1.25rem' }}>
        <h3 style={{ marginTop: 0 }}>Reactivate by ID</h3>
        <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
          Inactive products are not returned by the list API, so reactivate them here using their product ID.
        </p>
        <form onSubmit={reactivateById} className="flex gap-1 flex-wrap" style={{ alignItems: 'flex-end' }}>
          <div className="form-group" style={{ width: 180, marginBottom: 0 }}>
            <label>Product ID</label>
            <input inputMode="numeric" value={reactivateId} onChange={(e) => setReactivateId(e.target.value)} />
          </div>
          <button type="submit" disabled={reactivating}>{reactivating ? 'Reactivating...' : 'Reactivate'}</button>
        </form>
        {reactivateMsg.text && (
          <p className={reactivateMsg.error ? 'error-text' : 'muted'}>{reactivateMsg.text}</p>
        )}
      </div>
    </div>
  );
}
