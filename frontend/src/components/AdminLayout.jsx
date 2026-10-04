import { NavLink, Outlet } from 'react-router-dom';

const links = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/products', label: 'Products' },
  { to: '/admin/inventory', label: 'Inventory' },
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/returns', label: 'Returns & Refunds' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/reports', label: 'Reports' },
];

export default function AdminLayout() {
  return (
    <div className="flex gap-2" style={{ alignItems: 'flex-start', flexWrap: 'wrap' }}>
      <aside className="card" style={{ width: 200, flexShrink: 0 }}>
        <nav className="flex" style={{ flexDirection: 'column', gap: '0.35rem' }}>
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.end}
              style={({ isActive }) => ({
                padding: '0.5rem 0.6rem',
                borderRadius: 8,
                background: isActive ? 'var(--color-primary)' : 'transparent',
                color: isActive ? 'white' : 'var(--color-text)',
              })}
            >
              {l.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <section style={{ flex: 1, minWidth: 280 }}>
        <Outlet />
      </section>
    </div>
  );
}
