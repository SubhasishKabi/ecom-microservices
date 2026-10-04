import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', phone: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await register(form);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card" style={{ maxWidth: 420, margin: '2rem auto' }}>
      <h2 className="page-title" style={{ marginTop: 0 }}>Create an account</h2>
      <form onSubmit={handleSubmit}>
        <div className="flex gap-2">
          <div className="form-group" style={{ flex: 1 }}>
            <label>First name</label>
            <input required value={form.firstName} onChange={update('firstName')} />
          </div>
          <div className="form-group" style={{ flex: 1 }}>
            <label>Last name</label>
            <input value={form.lastName} onChange={update('lastName')} />
          </div>
        </div>
        <div className="form-group">
          <label>Email</label>
          <input type="email" required value={form.email} onChange={update('email')} />
        </div>
        <div className="form-group">
          <label>Phone</label>
          <input value={form.phone} onChange={update('phone')} />
        </div>
        <div className="form-group">
          <label>Password</label>
          <input type="password" required minLength={6} value={form.password} onChange={update('password')} />
        </div>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" disabled={submitting} style={{ width: '100%' }}>
          {submitting ? 'Creating account...' : 'Sign up'}
        </button>
      </form>
      <p>Already have an account? <Link to="/login">Log in</Link></p>
    </div>
  );
}
