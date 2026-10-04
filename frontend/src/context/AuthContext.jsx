import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import api, { setTokens, clearTokens } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(true);

  const persistUser = (u) => {
    setUser(u);
    if (u) localStorage.setItem('user', JSON.stringify(u));
    else localStorage.removeItem('user');
  };

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      setLoading(false);
      return;
    }
    api.get('/api/v1/auth/me')
      .then((res) => persistUser(res.data))
      .catch(() => { clearTokens(); persistUser(null); })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email, password) => {
    const { data } = await api.post('/api/v1/auth/login', { email, password });
    setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    persistUser(data.user);
    return data.user;
  }, []);

  const register = useCallback(async (payload) => {
    const { data } = await api.post('/api/v1/auth/register', payload);
    setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    persistUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    persistUser(null);
  }, []);

  const refreshProfile = useCallback(async () => {
    const { data } = await api.get('/api/v1/auth/me');
    persistUser(data);
    return data;
  }, []);

  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshProfile, isAdmin }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
