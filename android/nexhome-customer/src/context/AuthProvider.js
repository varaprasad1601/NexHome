import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

import { ApiError, apiRequest } from '../api/client';

const TOKENS_KEY = 'nexhome.tokens';
const USER_KEY = 'nexhome.user';

const AuthContext = createContext(null);

/** True when the account holds an active CUSTOMER role (only customers use this app). */
export function isCustomer(user) {
  return Boolean(user?.roles?.some((role) => role.roleCode === 'CUSTOMER' && role.isActive));
}

/**
 * The signed-in customer: tokens (secure storage), the user, and the customer profile. `authRequest(path, options)`
 * sends the access token and, on a 401, renews the tokens once (one renewal at a time: a refresh token works once)
 * and retries; if renewal fails the customer is signed out.
 */
export function AuthProvider({ children }) {
  const [ready, setReady] = useState(false);
  const [tokens, setTokens] = useState(null);
  const [user, setUser] = useState(null);
  const [profile, setProfile] = useState(null);
  const tokensRef = useRef(null);
  const refreshingRef = useRef(null);

  const store = useCallback(async (nextTokens, nextUser) => {
    tokensRef.current = nextTokens;
    setTokens(nextTokens);
    setUser(nextUser);
    try {
      if (nextTokens) {
        await SecureStore.setItemAsync(TOKENS_KEY, JSON.stringify(nextTokens));
        await SecureStore.setItemAsync(USER_KEY, JSON.stringify(nextUser));
      } else {
        await SecureStore.deleteItemAsync(TOKENS_KEY);
        await SecureStore.deleteItemAsync(USER_KEY);
      }
    } catch {
      // Storage unavailable: the session lasts until the app closes
    }
  }, []);

  const signOut = useCallback(async () => {
    const refreshToken = tokensRef.current?.refreshToken;
    setProfile(null);
    await store(null, null);
    if (refreshToken) {
      apiRequest('/api/auth/logout', { method: 'POST', body: { refreshToken } }).catch(() => {});
    }
  }, [store]);

  const refresh = useCallback(() => {
    if (!refreshingRef.current) {
      const refreshToken = tokensRef.current?.refreshToken;
      refreshingRef.current = apiRequest('/api/auth/token/refresh', { method: 'POST', body: { refreshToken } })
        .then(async (next) => {
          tokensRef.current = next;
          setTokens(next);
          try {
            await SecureStore.setItemAsync(TOKENS_KEY, JSON.stringify(next));
          } catch {
            // Kept in memory
          }
          return next;
        })
        .finally(() => {
          refreshingRef.current = null;
        });
    }
    return refreshingRef.current;
  }, []);

  const authRequest = useCallback(
    async (path, options = {}) => {
      try {
        return await apiRequest(path, { ...options, token: tokensRef.current?.accessToken });
      } catch (err) {
        if (!(err instanceof ApiError) || err.status !== 401 || !tokensRef.current) throw err;
        let next;
        try {
          next = await refresh();
        } catch {
          await signOut();
          throw new ApiError('Your session has ended. Please log in again.', 401);
        }
        return apiRequest(path, { ...options, token: next.accessToken });
      }
    },
    [refresh, signOut]
  );

  const reloadProfile = useCallback(async () => {
    const [me, customer] = await Promise.all([
      authRequest('/api/users/me'),
      authRequest('/api/profiles/customers/me').catch(() => null)
    ]);
    setUser(me);
    setProfile(customer);
    try {
      await SecureStore.setItemAsync(USER_KEY, JSON.stringify(me));
    } catch {
      // Kept in memory
    }
    return { user: me, profile: customer };
  }, [authRequest]);

  /** After a verified OTP: keep the tokens and the user, then load the customer profile. */
  const signIn = useCallback(
    async (otpResponse) => {
      await store(otpResponse.tokens, otpResponse.user);
      return reloadProfile();
    },
    [store, reloadProfile]
  );

  // Restore a saved session at start
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const savedTokens = JSON.parse((await SecureStore.getItemAsync(TOKENS_KEY)) ?? 'null');
        const savedUser = JSON.parse((await SecureStore.getItemAsync(USER_KEY)) ?? 'null');
        if (savedTokens && savedUser && !cancelled) {
          tokensRef.current = savedTokens;
          setTokens(savedTokens);
          setUser(savedUser);
          await reloadProfile().catch(() => {});
        }
      } catch {
        // Nothing saved
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value = useMemo(
    () => ({ ready, signedIn: Boolean(tokens), user, profile, signIn, signOut, authRequest, reloadProfile }),
    [ready, tokens, user, profile, signIn, signOut, authRequest, reloadProfile]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
