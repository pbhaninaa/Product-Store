import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { configureApiClient } from '../api/client';
import {
  getLastGuestMerchantSlug,
  getSessionUser,
  getStoredToken,
  isSupportOrPlatformOnlyUser,
  loginWithEmailPassword,
  logout as apiLogout,
  registerMerchant,
  requestPasswordReset,
  resetPassword,
  setLastGuestMerchantSlug,
  type SessionUser,
} from '../api/authApi';
import { CONFIG } from '../config';

type AuthContextValue = {
  ready: boolean;
  user: SessionUser | null;
  merchantSlug: string;
  setMerchantSlug: (slug: string) => void;
  refreshSession: () => Promise<SessionUser | null>;
  login: (email: string, password: string) => Promise<any>;
  signup: (params: {
    merchantName: string;
    ownerEmail: string;
    ownerPassword: string;
  }) => Promise<any>;
  forgotPassword: (email: string) => Promise<void>;
  resetPassword: (token: string, newPassword: string) => Promise<void>;
  logout: () => Promise<void>;
  isSupportOnly: boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<SessionUser | null>(null);
  const [merchantSlug, setMerchantSlugState] = useState(CONFIG.DEFAULT_MERCHANT_SLUG);

  const refreshSession = useCallback(async () => {
    const u = await getSessionUser();
    setUser(u);
    if (u?.tenant) {
      setMerchantSlugState(u.tenant);
      await setLastGuestMerchantSlug(u.tenant);
    }
    return u;
  }, []);

  useEffect(() => {
    configureApiClient({
      getToken: getStoredToken,
      onUnauthorized: async () => {
        await apiLogout();
        setUser(null);
      },
    });

    (async () => {
      const slug = await getLastGuestMerchantSlug(CONFIG.DEFAULT_MERCHANT_SLUG);
      setMerchantSlugState(slug);
      await refreshSession();
      setReady(true);
    })();

    const tick = setInterval(() => {
      void refreshSession();
    }, 60_000);
    return () => clearInterval(tick);
  }, [refreshSession]);

  const setMerchantSlug = useCallback((slug: string) => {
    const s = String(slug || '').trim() || CONFIG.DEFAULT_MERCHANT_SLUG;
    setMerchantSlugState(s);
    void setLastGuestMerchantSlug(s);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await loginWithEmailPassword(email, password);
      await refreshSession();
      return res;
    },
    [refreshSession],
  );

  const signup = useCallback(
    async (params: {
      merchantName: string;
      ownerEmail: string;
      ownerPassword: string;
    }) => {
      const res = await registerMerchant(params);
      await refreshSession();
      return res;
    },
    [refreshSession],
  );

  const forgotPassword = useCallback(async (email: string) => {
    await requestPasswordReset(email);
  }, []);

  const doResetPassword = useCallback(async (token: string, newPassword: string) => {
    await resetPassword(token, newPassword);
  }, []);

  const logout = useCallback(async () => {
    await apiLogout();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ready,
      user,
      merchantSlug,
      setMerchantSlug,
      refreshSession,
      login,
      signup,
      forgotPassword,
      resetPassword: doResetPassword,
      logout,
      isSupportOnly: isSupportOrPlatformOnlyUser(user),
    }),
    [
      ready,
      user,
      merchantSlug,
      setMerchantSlug,
      refreshSession,
      login,
      signup,
      forgotPassword,
      doResetPassword,
      logout,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
