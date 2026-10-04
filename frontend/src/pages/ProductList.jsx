import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../api/client';
import ProductCard from '../components/ProductCard';

const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'rating', label: 'Rating' },
];
const SORT_VALUES = SORT_OPTIONS.map((o) => o.value);

const RATING_OPTIONS = [
  { value: '', label: 'Any rating' },
  { value: '4', label: '4★ & up' },
  { value: '3', label: '3★ & up' },
  { value: '2', label: '2★ & up' },
  { value: '1', label: '1★ & up' },
];

// Reads (and sanitises) every filter from the query string, so shared / hand-edited URLs
// can't produce requests the backend would reject (non-numeric price, unknown sort field...).
// `page` is 1-based in the URL for readability and converted to 0-based for the API.
function readFilters(searchParams) {
  const nonNegativeNumber = (key) => {
    const raw = (searchParams.get(key) || '').trim();
    return raw !== '' && Number.isFinite(Number(raw)) && Number(raw) >= 0 ? raw : '';
  };
  const sort = searchParams.get('sort');
  const page = parseInt(searchParams.get('page'), 10);
  return {
    keyword: (searchParams.get('keyword') || '').trim(),
    category: searchParams.get('category') || '',
    brand: searchParams.get('brand') || '',
    minPrice: nonNegativeNumber('minPrice'),
    maxPrice: nonNegativeNumber('maxPrice'),
    minRating: nonNegativeNumber('minRating'),
    inStock: searchParams.get('inStock') === 'true',
    sort: SORT_VALUES.includes(sort) ? sort : 'newest',
    page: Number.isFinite(page) && page > 1 ? page : 1,
  };
}

// Inverse of readFilters: only non-default values end up in the URL.
function toSearchParams(filters) {
  const params = new URLSearchParams();
  const keyword = (filters.keyword || '').trim();
  if (keyword) params.set('keyword', keyword);
  if (filters.category) params.set('category', filters.category);
  if (filters.brand) params.set('brand', filters.brand);
  // Prices are strings here, so "0" is still truthy and kept.
  if (filters.minPrice) params.set('minPrice', filters.minPrice);
  if (filters.maxPrice) params.set('maxPrice', filters.maxPrice);
  if (filters.minRating) params.set('minRating', filters.minRating);
  if (filters.inStock) params.set('inStock', 'true');
  if (filters.sort && filters.sort !== 'newest') params.set('sort', filters.sort);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

// Adds the currently selected value to the option list if the backend list doesn't contain it
// (e.g. a link with a category that has no products yet), so the <select> never shows blank.
function withCurrent(options, current) {
  return current && !options.includes(current) ? [current, ...options] : options;
}

export default function ProductList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryString = searchParams.toString();
  const filters = readFilters(searchParams);

  // Text/number inputs are edited locally and applied on submit, so typing doesn't push a
  // history entry (and fire a request) per keystroke. Selects/checkbox apply immediately.
  const [draft, setDraft] = useState({
    keyword: filters.keyword,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
  });
  const [formError, setFormError] = useState('');

  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Re-sync the drafts when the URL changes from outside (navbar search, back/forward, links).
  useEffect(() => {
    setDraft({ keyword: filters.keyword, minPrice: filters.minPrice, maxPrice: filters.maxPrice });
  }, [filters.keyword, filters.minPrice, filters.maxPrice]);

  useEffect(() => {
    api.get('/api/products/categories')
      .then((res) => setCategories(Array.isArray(res.data) ? res.data : []))
      .catch(() => setCategories([]));
    api.get('/api/products/brands')
      .then((res) => setBrands(Array.isArray(res.data) ? res.data : []))
      .catch(() => setBrands([]));
  }, []);

  useEffect(() => {
    let ignore = false;
    const f = readFilters(new URLSearchParams(queryString));
    const params = { page: f.page - 1, size: PAGE_SIZE, sort: f.sort };
    if (f.keyword) params.keyword = f.keyword;
    if (f.category) params.category = f.category;
    if (f.brand) params.brand = f.brand;
    if (f.minPrice) params.minPrice = f.minPrice;
    if (f.maxPrice) params.maxPrice = f.maxPrice;
    if (f.minRating) params.minRating = f.minRating;
    if (f.inStock) params.inStock = true;

    setLoading(true);
    setError('');
    api.get('/api/products', { params })
      .then((res) => {
        if (!ignore) setResult(res.data);
      })
      .catch(() => {
        if (!ignore) {
          setResult(null);
          setError('Could not load products. Please try again later.');
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => { ignore = true; };
  }, [queryString]);

  // Applies the given changes plus any un-submitted text drafts, always returning to page 1.
  const applyFilters = (changes = {}) => {
    const next = { ...filters, ...draft, ...changes, page: 1 };
    const invalidPrice = (v) => v !== '' && !(Number.isFinite(Number(v)) && Number(v) >= 0);
    if (invalidPrice(next.minPrice) || invalidPrice(next.maxPrice)) {
      setFormError('Prices must be positive numbers.');
      return;
    }
    if (next.minPrice !== '' && next.maxPrice !== '' && Number(next.minPrice) > Number(next.maxPrice)) {
      setFormError('Min price cannot be greater than max price.');
      return;
    }
    setFormError('');
    setSearchParams(toSearchParams(next));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    applyFilters();
  };

  const clearFilters = () => {
    setDraft({ keyword: '', minPrice: '', maxPrice: '' });
    setFormError('');
    // Keep the chosen sort order; only the filters are cleared.
    setSearchParams(toSearchParams({ sort: filters.sort, page: 1 }));
  };

  const goToPage = (page) => {
    setSearchParams(toSearchParams({ ...filters, page }));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const products = result?.content || [];
  const totalPages = result?.totalPages || 0;
  const totalElements = result?.totalElements || 0;
  const currentPage = (result?.number ?? filters.page - 1) + 1; // 1-based

  const hasActiveFilters = Boolean(
    filters.keyword || filters.category || filters.brand || filters.minPrice
    || filters.maxPrice || filters.minRating || filters.inStock,
  );

  // A small window of page numbers around the current page.
  const pageNumbers = [];
  if (totalPages > 1) {
    const start = Math.max(1, currentPage - 2);
    const end = Math.min(totalPages, currentPage + 2);
    for (let p = start; p <= end; p += 1) pageNumbers.push(p);
  }

  return (
    <div>
      <h2 className="page-title">
        {filters.keyword ? `Results for "${filters.keyword}"` : filters.category || 'All products'}
      </h2>

      <form className="card" onSubmit={handleSubmit} style={{ marginBottom: '1.25rem' }}>
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '0.75rem' }}>
          <div className="form-group">
            <label htmlFor="pl-keyword">Search</label>
            <input
              id="pl-keyword"
              type="search"
              placeholder="Search by name, brand..."
              value={draft.keyword}
              onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label htmlFor="pl-category">Category</label>
            <select
              id="pl-category"
              value={filters.category}
              onChange={(e) => applyFilters({ category: e.target.value })}
            >
              <option value="">All</option>
              {withCurrent(categories, filters.category).map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="pl-brand">Brand</label>
            <select
              id="pl-brand"
              value={filters.brand}
              onChange={(e) => applyFilters({ brand: e.target.value })}
            >
              <option value="">All</option>
              {withCurrent(brands, filters.brand).map((b) => (
                <option key={b} value={b}>{b}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="pl-min-price">Min price</label>
            <input
              id="pl-min-price"
              type="number"
              min="0"
              step="0.01"
              placeholder="0"
              value={draft.minPrice}
              onChange={(e) => setDraft({ ...draft, minPrice: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label htmlFor="pl-max-price">Max price</label>
            <input
              id="pl-max-price"
              type="number"
              min="0"
              step="0.01"
              placeholder="Any"
              value={draft.maxPrice}
              onChange={(e) => setDraft({ ...draft, maxPrice: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label htmlFor="pl-rating">Rating</label>
            <select
              id="pl-rating"
              value={filters.minRating}
              onChange={(e) => applyFilters({ minRating: e.target.value })}
            >
              {RATING_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="pl-sort">Sort by</label>
            <select
              id="pl-sort"
              value={filters.sort}
              onChange={(e) => applyFilters({ sort: e.target.value })}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex-between flex-wrap gap-1">
          <label className="flex gap-1" style={{ alignItems: 'center', fontWeight: 400, cursor: 'pointer' }}>
            <input
              type="checkbox"
              style={{ width: 'auto' }}
              checked={filters.inStock}
              onChange={(e) => applyFilters({ inStock: e.target.checked })}
            />
            In stock only
          </label>
          <div className="flex gap-1">
            {hasActiveFilters && (
              <button type="button" className="btn-secondary" onClick={clearFilters}>Clear filters</button>
            )}
            <button type="submit">Apply</button>
          </div>
        </div>
        {formError && <p className="error-text">{formError}</p>}
      </form>

      {loading ? (
        <div className="spinner" />
      ) : error ? (
        <div className="card" style={{ textAlign: 'center' }}>
          <p className="error-text">{error}</p>
        </div>
      ) : products.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
          <h3 style={{ marginTop: 0 }}>No products found</h3>
          <p className="muted">Try adjusting your search or filters.</p>
          {hasActiveFilters && (
            <button type="button" className="btn-secondary" onClick={clearFilters}>Clear filters</button>
          )}
        </div>
      ) : (
        <>
          <p className="muted" style={{ marginTop: 0 }}>
            {totalElements} product{totalElements === 1 ? '' : 's'} found
          </p>
          <div className="grid grid-products">
            {products.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </>
      )}

      {!loading && !error && totalPages > 1 && (
        <div className="flex flex-wrap gap-1" style={{ justifyContent: 'center', alignItems: 'center', marginTop: '1.5rem' }}>
          <button
            type="button"
            className="btn-secondary"
            disabled={currentPage <= 1}
            onClick={() => goToPage(currentPage - 1)}
          >
            Prev
          </button>
          {pageNumbers.map((p) => (
            <button
              key={p}
              type="button"
              className={p === currentPage ? undefined : 'btn-secondary'}
              aria-current={p === currentPage ? 'page' : undefined}
              onClick={() => p !== currentPage && goToPage(p)}
            >
              {p}
            </button>
          ))}
          <button
            type="button"
            className="btn-secondary"
            disabled={currentPage >= totalPages}
            onClick={() => goToPage(currentPage + 1)}
          >
            Next
          </button>
          <span className="muted" style={{ marginLeft: '0.5rem' }}>Page {currentPage} of {totalPages}</span>
        </div>
      )}
    </div>
  );
}
