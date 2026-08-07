import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getToken, setToken as persistToken } from '../lib/api';

export type LoggedUser = {
  publicId: string;
  email: string;
  profileName: string;
};

type LoginResponse = {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: LoggedUser;
};

const USER_STORAGE_KEY = 'restaurante:user';
const UNIT_STORAGE_KEY = 'restaurante:unitId';

type AuthContextValue = {
  token: string | null;
  user: LoggedUser | null;
  unitId: number | null;
  setUnitId: (id: number) => void;
  login: (data: LoginResponse) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken());
  const [user, setUser] = useState<LoggedUser | null>(() => {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as LoggedUser) : null;
  });
  const [unitId, setUnitIdState] = useState<number | null>(() => {
    const raw = localStorage.getItem(UNIT_STORAGE_KEY);
    return raw ? Number(raw) : null;
  });

  useEffect(() => {
    persistToken(token);
  }, [token]);

  function login(data: LoginResponse) {
    setTokenState(data.token);
    setUser(data.user);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(data.user));
  }

  function logout() {
    setTokenState(null);
    setUser(null);
    setUnitIdState(null);
    localStorage.removeItem(USER_STORAGE_KEY);
    localStorage.removeItem(UNIT_STORAGE_KEY);
  }

  function setUnitId(id: number) {
    setUnitIdState(id);
    localStorage.setItem(UNIT_STORAGE_KEY, String(id));
  }

  const value = useMemo(
    () => ({ token, user, unitId, setUnitId, login, logout }),
    [token, user, unitId],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
