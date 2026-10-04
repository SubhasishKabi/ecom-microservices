import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import ProductCard from '../components/ProductCard';

export default function Home() {
  const [featured, setFeatured] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api.get('/api/products/featured'),
      api.get('/api/products/categories'),
    ])
      .then(([featuredRes, categoriesRes]) => {
        setFeatured(featuredRes.data || []);
        setCategories(categoriesRes.data || []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <section className="card" style={{ textAlign: 'center', padding: '2.5rem 1rem', marginBottom: '1.5rem' }}>
        <h1 style={{ margin: 0 }}>Everything you need, delivered fast.</h1>
        <p className="muted">Browse our catalog of electronics, fashion, home & kitchen, and sports gear.</p>
        <Link to="/products" className="btn">Shop now</Link>
      </section>

      {categories.length > 0 && (
        <section style={{ marginBottom: '1.5rem' }}>
          <h2 className="page-title" style={{ fontSize: '1.2rem' }}>Shop by category</h2>
          <div className="flex gap-1 flex-wrap">
            {categories.map((c) => (
              <Link key={c} to={`/products?category=${encodeURIComponent(c)}`} className="badge" style={{ padding: '0.4rem 0.9rem', fontSize: '0.85rem' }}>
                {c}
              </Link>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="page-title" style={{ fontSize: '1.2rem' }}>Featured products</h2>
        {loading ? (
          <div className="spinner" />
        ) : (
          <div className="grid grid-products">
            {featured.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        )}
      </section>
    </div>
  );
}
