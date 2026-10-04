import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import ProductCard from '../components/ProductCard';

export default function Wishlist() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [removingId, setRemovingId] = useState(null);
  const [actionError, setActionError] = useState('');

  const loadWishlist = useCallback(async () => {
    try {
      const { data } = await api.get('/api/wishlist');
      setItems(Array.isArray(data) ? data : []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load your wishlist. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadWishlist();
  }, [loadWishlist]);

  const retry = () => {
    setLoading(true);
    loadWishlist();
  };

  const handleRemove = async (productId) => {
    setRemovingId(productId);
    setActionError('');
    try {
      await api.delete(`/api/wishlist/${productId}`);
      // Drop it locally right away so the card disappears even if the refetch below fails.
      setItems((prev) => prev.filter((i) => (i.productId ?? i.product?.id) !== productId));
      await loadWishlist();
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not remove this item. Please try again.');
    } finally {
      setRemovingId(null);
    }
  };

  // Entries whose product was deleted come back with product === null; don't render those.
  const visibleItems = items.filter((item) => item && item.product);

  if (loading) return <div className="spinner" />;

  if (error && visibleItems.length === 0) {
    return (
      <div>
        <h2 className="page-title">My Wishlist</h2>
        <div className="card" style={{ textAlign: 'center' }}>
          <p className="error-text">{error}</p>
          <button type="button" className="btn-secondary" onClick={retry}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h2 className="page-title">
        My Wishlist
        {visibleItems.length > 0 && (
          <span className="muted" style={{ fontSize: '1rem', fontWeight: 400, marginLeft: '0.5rem' }}>
            ({visibleItems.length} item{visibleItems.length === 1 ? '' : 's'})
          </span>
        )}
      </h2>

      {(actionError || error) && <p className="error-text">{actionError || error}</p>}

      {visibleItems.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
          <h3 style={{ marginTop: 0 }}>Your wishlist is empty</h3>
          <p className="muted">Save products you love and find them here later.</p>
          <Link to="/products" className="btn">Browse products</Link>
        </div>
      ) : (
        <div className="grid grid-products">
          {visibleItems.map((item) => {
            const productId = item.productId ?? item.product.id;
            const removing = removingId === productId;
            return (
              <div
                key={item.id ?? productId}
                className="flex gap-1"
                style={{ flexDirection: 'column', justifyContent: 'space-between' }}
              >
                <ProductCard product={item.product} />
                <button
                  type="button"
                  className="btn-danger"
                  onClick={() => handleRemove(productId)}
                  disabled={removingId !== null}
                >
                  {removing ? 'Removing...' : 'Remove'}
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
