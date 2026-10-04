import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

export default function Navbar() {
  const { user, logout, isAdmin } = useAuth();
  const { itemCount } = useCart();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);

  const handleSearch = (e) => {
    e.preventDefault();
    navigate(`/products?keyword=${encodeURIComponent(query)}`);
    setMenuOpen(false);
  };

  return (
    <header style={{ background: 'var(--color-surface)', borderBottom: '1px solid var(--color-border)', position: 'sticky', top: 0, zIndex: 10 }}>
      <div className="container flex-between" style={{ padding: '0.75rem 1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <Link to="/" style={{ fontWeight: 700, fontSize: '1.25rem', color: 'var(--color-text)' }}>🛒 Ecom Shop</Link>

        <form onSubmit={handleSearch} style={{ flex: '1 1 260px', maxWidth: 480 }}>
          <input
            placeholder="Search products..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </form>

        <button className="btn-secondary" style={{ display: 'none' }} onClick={() => setMenuOpen((o) => !o)}>Menu</button>

        <nav className="flex gap-2" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Link to="/products">Products</Link>
          {user && <Link to="/wishlist">Wishlist</Link>}
          {user && <Link to="/notifications">Notifications</Link>}
          {user && (
            <Link to="/cart">Cart{itemCount > 0 && <span className="badge" style={{ marginLeft: 4 }}>{itemCount}</span>}</Link>
          )}
          {user && <Link to="/orders">Orders</Link>}
          {isAdmin && <Link to="/admin">Admin</Link>}
          {user ? (
            <>
              <Link to="/profile">{user.firstName || 'Profile'}</Link>
              <button className="btn-secondary" onClick={() => { logout(); navigate('/'); }}>Logout</button>
            </>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register" className="btn">Sign up</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
