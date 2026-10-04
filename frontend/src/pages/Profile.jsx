import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';

const emptyAddress = { type: 'HOME', street: '', city: '', state: '', zipcode: '', country: '', isDefault: false };

export default function Profile() {
  const { user, refreshProfile } = useAuth();
  const [profileForm, setProfileForm] = useState({ firstName: '', lastName: '', phone: '' });
  const [profileMsg, setProfileMsg] = useState('');
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '' });
  const [pwMsg, setPwMsg] = useState('');
  const [addresses, setAddresses] = useState([]);
  const [addressForm, setAddressForm] = useState(emptyAddress);
  const [editingId, setEditingId] = useState(null);
  const [addrMsg, setAddrMsg] = useState('');

  useEffect(() => {
    if (user) {
      setProfileForm({ firstName: user.firstName || '', lastName: user.lastName || '', phone: user.phone || '' });
      setAddresses(user.addresses || []);
    }
  }, [user]);

  const saveProfile = async (e) => {
    e.preventDefault();
    setProfileMsg('');
    try {
      await api.put(`/api/users/${user.id}`, profileForm);
      await refreshProfile();
      setProfileMsg('Profile updated.');
    } catch (err) {
      setProfileMsg(err.response?.data?.message || 'Update failed');
    }
  };

  const changePassword = async (e) => {
    e.preventDefault();
    setPwMsg('');
    try {
      await api.post('/api/v1/auth/change-password', pwForm);
      setPwMsg('Password changed successfully.');
      setPwForm({ currentPassword: '', newPassword: '' });
    } catch (err) {
      setPwMsg(err.response?.data?.message || 'Could not change password');
    }
  };

  const submitAddress = async (e) => {
    e.preventDefault();
    setAddrMsg('');
    try {
      if (editingId) {
        await api.put(`/api/users/${user.id}/addresses/${editingId}`, addressForm);
      } else {
        await api.post(`/api/users/${user.id}/addresses`, addressForm);
      }
      setAddressForm(emptyAddress);
      setEditingId(null);
      await refreshProfile();
    } catch (err) {
      setAddrMsg(err.response?.data?.message || 'Could not save address');
    }
  };

  const editAddress = (addr) => {
    setEditingId(addr.id);
    setAddressForm({ ...addr });
  };

  const deleteAddress = async (id) => {
    await api.delete(`/api/users/${user.id}/addresses/${id}`);
    await refreshProfile();
  };

  const makeDefault = async (id) => {
    await api.put(`/api/users/${user.id}/addresses/${id}/default`);
    await refreshProfile();
  };

  if (!user) return null;

  return (
    <div>
      <h2 className="page-title">My Profile</h2>
      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
        <div className="card">
          <h3>Profile details</h3>
          <form onSubmit={saveProfile}>
            <div className="form-group">
              <label>Email</label>
              <input value={user.email} disabled />
            </div>
            <div className="form-group">
              <label>First name</label>
              <input value={profileForm.firstName} onChange={(e) => setProfileForm({ ...profileForm, firstName: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Last name</label>
              <input value={profileForm.lastName} onChange={(e) => setProfileForm({ ...profileForm, lastName: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Phone</label>
              <input value={profileForm.phone} onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} />
            </div>
            {profileMsg && <p className="muted">{profileMsg}</p>}
            <button type="submit">Save changes</button>
          </form>
        </div>

        <div className="card">
          <h3>Change password</h3>
          <form onSubmit={changePassword}>
            <div className="form-group">
              <label>Current password</label>
              <input type="password" required value={pwForm.currentPassword}
                     onChange={(e) => setPwForm({ ...pwForm, currentPassword: e.target.value })} />
            </div>
            <div className="form-group">
              <label>New password</label>
              <input type="password" required minLength={6} value={pwForm.newPassword}
                     onChange={(e) => setPwForm({ ...pwForm, newPassword: e.target.value })} />
            </div>
            {pwMsg && <p className="muted">{pwMsg}</p>}
            <button type="submit">Update password</button>
          </form>
        </div>
      </div>

      <div className="card" style={{ marginTop: '1.25rem' }}>
        <h3>Addresses</h3>
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', marginBottom: '1rem' }}>
          {addresses.map((addr) => (
            <div key={addr.id} className="card" style={{ background: 'var(--color-bg)' }}>
              <div className="flex-between">
                <strong>{addr.type}</strong>
                {addr.isDefault && <span className="badge badge-success">Default</span>}
              </div>
              <p className="muted" style={{ margin: '0.4rem 0' }}>
                {addr.street}, {addr.city}, {addr.state} {addr.zipcode}, {addr.country}
              </p>
              <div className="flex gap-1">
                <button className="btn-secondary" onClick={() => editAddress(addr)}>Edit</button>
                {!addr.isDefault && <button className="btn-secondary" onClick={() => makeDefault(addr.id)}>Make default</button>}
                <button className="btn-danger" onClick={() => deleteAddress(addr.id)}>Delete</button>
              </div>
            </div>
          ))}
          {addresses.length === 0 && <p className="muted">No addresses yet.</p>}
        </div>

        <h4>{editingId ? 'Edit address' : 'Add a new address'}</h4>
        <form onSubmit={submitAddress}>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '0.75rem' }}>
            <div className="form-group">
              <label>Type</label>
              <select value={addressForm.type} onChange={(e) => setAddressForm({ ...addressForm, type: e.target.value })}>
                <option value="HOME">Home</option>
                <option value="WORK">Work</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div className="form-group">
              <label>Street</label>
              <input required value={addressForm.street} onChange={(e) => setAddressForm({ ...addressForm, street: e.target.value })} />
            </div>
            <div className="form-group">
              <label>City</label>
              <input required value={addressForm.city} onChange={(e) => setAddressForm({ ...addressForm, city: e.target.value })} />
            </div>
            <div className="form-group">
              <label>State</label>
              <input value={addressForm.state} onChange={(e) => setAddressForm({ ...addressForm, state: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Zipcode</label>
              <input required value={addressForm.zipcode} onChange={(e) => setAddressForm({ ...addressForm, zipcode: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Country</label>
              <input required value={addressForm.country} onChange={(e) => setAddressForm({ ...addressForm, country: e.target.value })} />
            </div>
          </div>
          <label className="flex gap-1" style={{ alignItems: 'center', fontWeight: 400, marginBottom: '0.75rem' }}>
            <input type="checkbox" style={{ width: 'auto' }} checked={!!addressForm.isDefault}
                   onChange={(e) => setAddressForm({ ...addressForm, isDefault: e.target.checked })} />
            Set as default
          </label>
          {addrMsg && <p className="error-text">{addrMsg}</p>}
          <div className="flex gap-1">
            <button type="submit">{editingId ? 'Update address' : 'Add address'}</button>
            {editingId && <button type="button" className="btn-secondary" onClick={() => { setEditingId(null); setAddressForm(emptyAddress); }}>Cancel</button>}
          </div>
        </form>
      </div>
    </div>
  );
}
