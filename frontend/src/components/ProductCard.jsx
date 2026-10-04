import { Link } from 'react-router-dom';

export default function ProductCard({ product }) {
  if (!product) return null;
  const hasDiscount = Number(product.discount) > 0;
  const outOfStock = !product.stockQuantity || product.stockQuantity <= 0;

  return (
    <Link to={`/products/${product.id}`} className="card" style={{ display: 'block', color: 'inherit', textDecoration: 'none' }}>
      <div style={{
        width: '100%', aspectRatio: '1 / 1', borderRadius: 8, overflow: 'hidden',
        background: '#f1f2f6', marginBottom: '0.75rem',
      }}>
        {product.imageUrl && (
          <img src={product.imageUrl} alt={product.name}
               style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
        )}
      </div>
      <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>{product.brand || product.category}</div>
      <div style={{ fontWeight: 600, margin: '0.2rem 0', minHeight: '2.6em', overflow: 'hidden' }}>{product.name}</div>
      <div className="flex gap-1" style={{ alignItems: 'baseline' }}>
        <span style={{ fontWeight: 700 }}>${Number(product.effectivePrice ?? product.price).toFixed(2)}</span>
        {hasDiscount && (
          <span className="muted" style={{ textDecoration: 'line-through', fontSize: '0.85rem' }}>
            ${Number(product.price).toFixed(2)}
          </span>
        )}
      </div>
      <div className="flex gap-1" style={{ marginTop: '0.4rem', alignItems: 'center' }}>
        {product.rating > 0 && <span className="badge">★ {product.rating.toFixed(1)}</span>}
        {outOfStock && <span className="badge badge-danger">Out of stock</span>}
      </div>
    </Link>
  );
}
