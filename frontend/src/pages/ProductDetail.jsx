import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import ProductCard from '../components/ProductCard';

const REVIEWS_PAGE_SIZE = 5;

function formatPrice(value) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function formatDate(value) {
  if (!value) return '';
  // LocalDateTime is normally serialised as an ISO string; tolerate Jackson's array form too.
  const date = Array.isArray(value)
    ? new Date(value[0], (value[1] || 1) - 1, value[2] || 1)
    : new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString();
}

function errorMessage(err, fallback) {
  return err?.response?.data?.message || fallback;
}

// The review endpoint takes the display name from an optional X-User-Name header (it falls back
// to "Customer" otherwise). Header values must be Latin-1, so strip anything else rather than
// let the browser reject the request.
function reviewerName(user) {
  const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ');
  return name.replace(/[^\x20-\x7E\xA0-\xFF]/g, '').trim();
}

function Stars({ value, size = '1rem' }) {
  const filled = Math.min(5, Math.max(0, Math.round(Number(value) || 0)));
  return (
    <span
      role="img"
      aria-label={`${Number(value) || 0} out of 5 stars`}
      style={{ color: 'var(--color-warning)', letterSpacing: 1, fontSize: size }}
    >
      {'★'.repeat(filled)}
      <span style={{ color: 'var(--color-border)' }}>{'★'.repeat(5 - filled)}</span>
    </span>
  );
}

export default function ProductDetail() {
  const { id } = useParams();
  // Keyed by id so navigating between products (e.g. from "You may also like") remounts the page
  // and resets every piece of per-product state instead of briefly showing the previous product.
  return <ProductDetailContent key={id} id={id} />;
}

function ProductDetailContent({ id }) {
  const { user } = useAuth();
  const { addToCart } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const userId = user?.id;

  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(''); // '' | 'notfound' | 'error'

  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [cartMsg, setCartMsg] = useState(null); // { type: 'success' | 'error', text }

  const [inWishlist, setInWishlist] = useState(false);
  const [wishlistBusy, setWishlistBusy] = useState(false);
  const [subscribed, setSubscribed] = useState(false);
  const [alertBusy, setAlertBusy] = useState(false);
  const [actionError, setActionError] = useState('');

  const [recommendations, setRecommendations] = useState([]);

  const [reviews, setReviews] = useState(null); // Page<ReviewResponse>
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewsLoading, setReviewsLoading] = useState(true);
  const [reviewsError, setReviewsError] = useState('');
  const [distribution, setDistribution] = useState(null);
  const [reviewsVersion, setReviewsVersion] = useState(0); // bumped to refetch after a new review

  const [reviewForm, setReviewForm] = useState({ rating: 0, comment: '' });
  const [hoverRating, setHoverRating] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [reviewSubmitted, setReviewSubmitted] = useState(false);

  const productId = product?.id;
  const stockQuantity = Number(product?.stockQuantity) || 0;
  const outOfStock = Boolean(product) && stockQuantity <= 0;

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  // Product
  useEffect(() => {
    let ignore = false;
    setLoading(true);
    api.get(`/api/products/${encodeURIComponent(id)}`)
      .then((res) => {
        if (ignore) return;
        if (res.data && res.data.id != null) setProduct(res.data);
        else setLoadError('notfound');
      })
      .catch((err) => {
        if (ignore) return;
        const status = err.response?.status;
        setLoadError(status === 404 || status === 400 ? 'notfound' : 'error');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => { ignore = true; };
  }, [id]);

  // Fire-and-forget "recently viewed" record (auth-only endpoint, so skip for anonymous visitors).
  useEffect(() => {
    if (!userId || !productId) return;
    api.post(`/api/recently-viewed/${productId}`).catch(() => {});
  }, [userId, productId]);

  // Wishlist status
  useEffect(() => {
    if (!userId || !productId) {
      setInWishlist(false);
      return undefined;
    }
    let ignore = false;
    api.get(`/api/wishlist/${productId}/exists`)
      .then((res) => { if (!ignore) setInWishlist(res.data === true || res.data === 'true'); })
      .catch(() => {});
    return () => { ignore = true; };
  }, [userId, productId]);

  // Back-in-stock alert status (only relevant while out of stock)
  useEffect(() => {
    if (!userId || !productId || !outOfStock) {
      setSubscribed(false);
      return undefined;
    }
    let ignore = false;
    api.get(`/api/stock-alerts/${productId}/subscribed`)
      .then((res) => { if (!ignore) setSubscribed(res.data === true || res.data === 'true'); })
      .catch(() => {});
    return () => { ignore = true; };
  }, [userId, productId, outOfStock]);

  // Recommendations
  useEffect(() => {
    if (!productId) return undefined;
    let ignore = false;
    api.get(`/api/products/${productId}/recommendations`)
      .then((res) => {
        if (!ignore) {
          const list = Array.isArray(res.data) ? res.data : [];
          setRecommendations(list.filter((p) => p && p.id !== productId));
        }
      })
      .catch(() => { if (!ignore) setRecommendations([]); });
    return () => { ignore = true; };
  }, [productId]);

  // Reviews (paged)
  useEffect(() => {
    if (!productId) return undefined;
    let ignore = false;
    setReviewsLoading(true);
    setReviewsError('');
    api.get(`/api/products/${productId}/reviews`, {
      params: { page: reviewPage, size: REVIEWS_PAGE_SIZE, sort: 'createdAt,desc' },
    })
      .then((res) => { if (!ignore) setReviews(res.data); })
      .catch(() => { if (!ignore) setReviewsError('Could not load reviews.'); })
      .finally(() => { if (!ignore) setReviewsLoading(false); });
    return () => { ignore = true; };
  }, [productId, reviewPage, reviewsVersion]);

  // Rating distribution
  useEffect(() => {
    if (!productId) return undefined;
    let ignore = false;
    api.get(`/api/products/${productId}/reviews/distribution`)
      .then((res) => { if (!ignore) setDistribution(res.data); })
      .catch(() => { if (!ignore) setDistribution(null); });
    return () => { ignore = true; };
  }, [productId, reviewsVersion]);

  const goToLogin = () => navigate('/login', { state: { from: location } });

  const changeQuantity = (delta) => {
    setQuantity((q) => Math.min(Math.max(1, q + delta), Math.max(1, stockQuantity)));
  };

  const handleAddToCart = async () => {
    if (!user) {
      goToLogin();
      return;
    }
    setAdding(true);
    setCartMsg(null);
    try {
      await addToCart(product.id, quantity);
      setCartMsg({ type: 'success', text: `Added ${quantity} to your cart.` });
    } catch (err) {
      setCartMsg({ type: 'error', text: errorMessage(err, 'Could not add this item to your cart.') });
    } finally {
      setAdding(false);
    }
  };

  const toggleWishlist = async () => {
    setWishlistBusy(true);
    setActionError('');
    try {
      if (inWishlist) {
        await api.delete(`/api/wishlist/${product.id}`);
        setInWishlist(false);
      } else {
        await api.post(`/api/wishlist/${product.id}`);
        setInWishlist(true);
      }
    } catch (err) {
      setActionError(errorMessage(err, 'Could not update your wishlist.'));
    } finally {
      setWishlistBusy(false);
    }
  };

  const toggleStockAlert = async () => {
    setAlertBusy(true);
    setActionError('');
    try {
      if (subscribed) {
        await api.delete(`/api/stock-alerts/${product.id}`);
        setSubscribed(false);
      } else {
        await api.post(`/api/stock-alerts/${product.id}`);
        setSubscribed(true);
      }
    } catch (err) {
      setActionError(errorMessage(err, 'Could not update your stock alert.'));
    } finally {
      setAlertBusy(false);
    }
  };

  const submitReview = async (e) => {
    e.preventDefault();
    setReviewError('');
    if (reviewForm.rating < 1 || reviewForm.rating > 5) {
      setReviewError('Please select a rating between 1 and 5 stars.');
      return;
    }
    setSubmitting(true);
    try {
      const name = reviewerName(user);
      await api.post(
        `/api/products/${product.id}/reviews`,
        { rating: reviewForm.rating, comment: reviewForm.comment.trim() },
        name ? { headers: { 'X-User-Name': name } } : undefined,
      );
      setReviewForm({ rating: 0, comment: '' });
      setReviewSubmitted(true);
      setReviewPage(0);
      setReviewsVersion((v) => v + 1);
      // The product's average rating / review count changed server-side; refresh them quietly.
      api.get(`/api/products/${product.id}`)
        .then((res) => { if (res.data && res.data.id != null) setProduct(res.data); })
        .catch(() => {});
    } catch (err) {
      setReviewError(errorMessage(err, 'Could not submit your review. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="spinner" />;

  if (loadError || !product) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem 1rem' }}>
        <h2>{loadError === 'error' ? 'Something went wrong' : 'Product not found'}</h2>
        <p className="muted">
          {loadError === 'error'
            ? 'We could not load this product right now. Please try again later.'
            : "The product you're looking for doesn't exist or is no longer available."}
        </p>
        <Link to="/products" className="btn">Back to products</Link>
      </div>
    );
  }

  // Strictly boolean: `discount && ...` would render a stray "0" in JSX when discount is 0.
  const hasDiscount = Number(product.discount) > 0;
  const rating = Number(product.rating) || 0;
  const reviewCount = Number(product.reviewCount) || 0;

  const reviewList = reviews?.content || [];
  const reviewTotalPages = reviews?.totalPages || 0;
  const reviewCurrentPage = reviews?.number ?? reviewPage;

  const distTotal = Number(distribution?.totalReviews) || 0;
  const distAverage = Number(distribution?.averageRating) || 0;

  return (
    <div>
      <nav className="muted" style={{ fontSize: '0.85rem', margin: '0.5rem 0 1rem' }}>
        <Link to="/products">Products</Link>
        {product.category && (
          <>
            {' / '}
            <Link to={`/products?category=${encodeURIComponent(product.category)}`}>{product.category}</Link>
          </>
        )}
        {' / '}
        <span>{product.name}</span>
      </nav>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', alignItems: 'start' }}>
        <div className="card" style={{ padding: '0.75rem' }}>
          <div
            style={{
              width: '100%', aspectRatio: '1 / 1', borderRadius: 8, overflow: 'hidden',
              background: '#f1f2f6', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {product.imageUrl ? (
              <img
                src={product.imageUrl}
                alt={product.name}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <span className="muted">No image available</span>
            )}
          </div>
        </div>

        <div className="card">
          <div className="muted" style={{ fontSize: '0.85rem' }}>
            {[product.brand, product.category].filter(Boolean).join(' · ')}
          </div>
          <h1 style={{ margin: '0.3rem 0 0.5rem', fontSize: '1.6rem' }}>{product.name}</h1>

          <div className="flex gap-1 flex-wrap" style={{ alignItems: 'center', marginBottom: '0.75rem' }}>
            {reviewCount > 0 ? (
              <>
                <Stars value={rating} />
                <span>{rating.toFixed(1)}</span>
                <a href="#reviews" className="muted" style={{ fontSize: '0.85rem' }}>
                  ({reviewCount} review{reviewCount === 1 ? '' : 's'})
                </a>
              </>
            ) : (
              <span className="muted" style={{ fontSize: '0.85rem' }}>No reviews yet</span>
            )}
          </div>

          <div className="flex gap-1 flex-wrap" style={{ alignItems: 'baseline', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '1.6rem', fontWeight: 700 }}>
              {formatPrice(product.effectivePrice ?? product.price)}
            </span>
            {hasDiscount && (
              <>
                <span className="muted" style={{ textDecoration: 'line-through' }}>{formatPrice(product.price)}</span>
                <span className="badge badge-danger">{Number(product.discount)}% off</span>
              </>
            )}
          </div>

          <div style={{ marginBottom: '1rem' }}>
            {outOfStock ? (
              <span className="badge badge-danger">Out of stock</span>
            ) : stockQuantity <= 5 ? (
              <span className="badge badge-warning">Only {stockQuantity} left in stock</span>
            ) : (
              <span className="badge badge-success">In stock</span>
            )}
          </div>

          {product.description && (
            <p style={{ lineHeight: 1.6, whiteSpace: 'pre-line' }}>{product.description}</p>
          )}

          {!outOfStock && (
            <div className="flex gap-2 flex-wrap" style={{ alignItems: 'center', marginTop: '1rem' }}>
              <div className="flex gap-1" style={{ alignItems: 'center' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  aria-label="Decrease quantity"
                  disabled={quantity <= 1}
                  onClick={() => changeQuantity(-1)}
                >
                  −
                </button>
                <span aria-live="polite" style={{ minWidth: '2rem', textAlign: 'center', fontWeight: 600 }}>
                  {quantity}
                </span>
                <button
                  type="button"
                  className="btn-secondary"
                  aria-label="Increase quantity"
                  disabled={quantity >= stockQuantity}
                  onClick={() => changeQuantity(1)}
                >
                  +
                </button>
              </div>
              <button type="button" onClick={handleAddToCart} disabled={adding}>
                {adding ? 'Adding...' : 'Add to cart'}
              </button>
            </div>
          )}

          {cartMsg && (
            <p className={cartMsg.type === 'error' ? 'error-text' : undefined}
               style={cartMsg.type === 'success' ? { color: 'var(--color-success)', fontSize: '0.9rem' } : undefined}>
              {cartMsg.text}
              {cartMsg.type === 'success' && <>{' '}<Link to="/cart">View cart</Link></>}
            </p>
          )}

          {user && (
            <div className="flex gap-1 flex-wrap" style={{ alignItems: 'center', marginTop: '1rem' }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={toggleWishlist}
                disabled={wishlistBusy}
                aria-pressed={inWishlist}
              >
                <span style={{ color: inWishlist ? 'var(--color-danger)' : 'inherit' }}>{inWishlist ? '♥' : '♡'}</span>
                {inWishlist ? 'Remove from wishlist' : 'Add to wishlist'}
              </button>
              {inWishlist && <span className="badge badge-success">In your wishlist</span>}
            </div>
          )}

          {user && outOfStock && (
            <div className="flex gap-1 flex-wrap" style={{ alignItems: 'center', marginTop: '0.75rem' }}>
              <button
                type="button"
                className={subscribed ? 'btn-secondary' : undefined}
                onClick={toggleStockAlert}
                disabled={alertBusy}
                aria-pressed={subscribed}
              >
                {subscribed ? 'Cancel stock alert' : 'Notify me when back in stock'}
              </button>
              {subscribed && <span className="badge badge-success">We'll notify you when it's back</span>}
            </div>
          )}

          {!user && (
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: '1rem' }}>
              <Link to="/login" state={{ from: location }}>Log in</Link>
              {outOfStock
                ? ' to save this item or get notified when it is back in stock.'
                : ' to add this item to your cart or wishlist.'}
            </p>
          )}

          {actionError && <p className="error-text">{actionError}</p>}
        </div>
      </div>

      {recommendations.length > 0 && (
        <section style={{ marginTop: '2rem' }}>
          <h2 className="page-title" style={{ fontSize: '1.2rem' }}>You may also like</h2>
          <div className="grid grid-products">
            {recommendations.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}

      <section id="reviews" style={{ marginTop: '2rem' }}>
        <h2 className="page-title" style={{ fontSize: '1.2rem' }}>Customer reviews</h2>

        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', alignItems: 'start' }}>
          <div className="card">
            <div className="flex gap-1" style={{ alignItems: 'baseline', marginBottom: '0.25rem' }}>
              <span style={{ fontSize: '2rem', fontWeight: 700 }}>{distAverage.toFixed(1)}</span>
              <span className="muted">out of 5</span>
            </div>
            <Stars value={distAverage} size="1.2rem" />
            <p className="muted" style={{ fontSize: '0.85rem', margin: '0.3rem 0 1rem' }}>
              {distTotal} review{distTotal === 1 ? '' : 's'}
            </p>
            {[5, 4, 3, 2, 1].map((star) => {
              const count = Number(distribution?.distribution?.[star]) || 0;
              const pct = distTotal > 0 ? (count / distTotal) * 100 : 0;
              return (
                <div key={star} className="flex gap-1" style={{ alignItems: 'center', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
                  <span style={{ width: '2.5rem', whiteSpace: 'nowrap' }}>{star} ★</span>
                  <div style={{ flex: 1, height: 8, background: 'var(--color-border)', borderRadius: 999, overflow: 'hidden' }}>
                    <div style={{ width: `${pct}%`, height: '100%', background: 'var(--color-warning)' }} />
                  </div>
                  <span className="muted" style={{ width: '2rem', textAlign: 'right' }}>{count}</span>
                </div>
              );
            })}

            <div style={{ borderTop: '1px solid var(--color-border)', marginTop: '1rem', paddingTop: '1rem' }}>
              {!user ? (
                <p className="muted" style={{ margin: 0 }}>
                  <Link to="/login" state={{ from: location }}>Log in</Link> to write a review.
                </p>
              ) : reviewSubmitted ? (
                <p style={{ margin: 0, color: 'var(--color-success)' }}>Thanks! Your review has been posted.</p>
              ) : (
                <form onSubmit={submitReview}>
                  <h3 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>Write a review</h3>
                  <div className="form-group">
                    <label>Your rating</label>
                    <div className="flex" role="radiogroup" aria-label="Rating" onMouseLeave={() => setHoverRating(0)}>
                      {[1, 2, 3, 4, 5].map((star) => {
                        const active = star <= (hoverRating || reviewForm.rating);
                        return (
                          <button
                            key={star}
                            type="button"
                            role="radio"
                            aria-checked={reviewForm.rating === star}
                            aria-label={`${star} star${star === 1 ? '' : 's'}`}
                            onMouseEnter={() => setHoverRating(star)}
                            onClick={() => setReviewForm({ ...reviewForm, rating: star })}
                            style={{
                              background: 'transparent', border: 'none', padding: '0 0.15rem',
                              fontSize: '1.6rem', lineHeight: 1,
                              color: active ? 'var(--color-warning)' : 'var(--color-border)',
                            }}
                          >
                            ★
                          </button>
                        );
                      })}
                    </div>
                  </div>
                  <div className="form-group">
                    <label htmlFor="review-comment">Your review</label>
                    <textarea
                      id="review-comment"
                      rows={4}
                      maxLength={2000}
                      placeholder="What did you like or dislike?"
                      value={reviewForm.comment}
                      onChange={(e) => setReviewForm({ ...reviewForm, comment: e.target.value })}
                    />
                  </div>
                  {reviewError && <p className="error-text">{reviewError}</p>}
                  <button type="submit" disabled={submitting}>
                    {submitting ? 'Submitting...' : 'Submit review'}
                  </button>
                </form>
              )}
            </div>
          </div>

          <div className="card">
            {reviewsLoading && !reviews ? (
              <div className="spinner" />
            ) : reviewsError ? (
              <p className="error-text" style={{ margin: 0 }}>{reviewsError}</p>
            ) : reviewList.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>No reviews yet. Be the first to review this product!</p>
            ) : (
              <>
                {reviewList.map((r, idx) => (
                  <div
                    key={r.id}
                    style={{
                      padding: '0.85rem 0',
                      borderTop: idx === 0 ? 'none' : '1px solid var(--color-border)',
                      paddingTop: idx === 0 ? 0 : '0.85rem',
                    }}
                  >
                    <div className="flex-between flex-wrap gap-1">
                      <div className="flex gap-1 flex-wrap" style={{ alignItems: 'center' }}>
                        <strong>{r.userName || 'Customer'}</strong>
                        {r.verifiedPurchase && <span className="badge badge-success">Verified Purchase</span>}
                      </div>
                      <span className="muted" style={{ fontSize: '0.8rem' }}>{formatDate(r.createdAt)}</span>
                    </div>
                    <Stars value={r.rating} />
                    {r.comment && <p style={{ margin: '0.35rem 0 0', whiteSpace: 'pre-line' }}>{r.comment}</p>}
                  </div>
                ))}

                {reviewTotalPages > 1 && (
                  <div className="flex gap-1" style={{ justifyContent: 'center', alignItems: 'center', marginTop: '0.75rem' }}>
                    <button
                      type="button"
                      className="btn-secondary"
                      disabled={reviewCurrentPage <= 0 || reviewsLoading}
                      onClick={() => setReviewPage(reviewCurrentPage - 1)}
                    >
                      Prev
                    </button>
                    <span className="muted" style={{ fontSize: '0.85rem' }}>
                      Page {reviewCurrentPage + 1} of {reviewTotalPages}
                    </span>
                    <button
                      type="button"
                      className="btn-secondary"
                      disabled={reviewCurrentPage >= reviewTotalPages - 1 || reviewsLoading}
                      onClick={() => setReviewPage(reviewCurrentPage + 1)}
                    >
                      Next
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
