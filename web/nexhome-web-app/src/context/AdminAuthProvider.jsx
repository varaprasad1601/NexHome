import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { logout, refreshTokens } from '../api/auth';
import { ApiError, apiRequest } from '../api/client';
import { clearAdminSession, loadAdminSession, saveAdminSession } from '../utils/adminSession';
import { AdminAuthContext } from './adminAuth';

/**
 * Holds the admin session for the /admin screens, persisted in localStorage, and makes signed-in API calls.
 * An expired access token (15 minutes) is renewed once with the refresh token and the call retried; if that fails
 * the admin is signed out, and AdminRoute sends them to the sign-in.
 *
 * It also loads what the admin may do (GET /api/users/me/permissions) for each signed-in user, and again whenever the
 * tab becomes visible, a request is refused (403) or a screen calls reloadPermissions() (after changing grants), so a
 * changed grant shows without signing out. `can(functionCode, access)` is false until they
 * have loaded; the console hides menus without READ and actions without WRITE / DELETE (the backend checks anyway).
 */
export default function AdminAuthProvider({ children }) {
  const [session, setSession] = useState(loadAdminSession);
  const sessionRef = useRef(session);
  // One refresh at a time: a refresh token works once, and sending it twice ends every session
  const refreshingRef = useRef(null);

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const signIn = useCallback((newSession) => {
    saveAdminSession(newSession);
    sessionRef.current = newSession;
    setSession(newSession);
  }, []);

  const clear = useCallback(() => {
    clearAdminSession();
    sessionRef.current = null;
    setSession(null);
  }, []);

  const signOut = useCallback(async () => {
    const refreshToken = sessionRef.current?.tokens?.refreshToken;
    clear();
    // Ends the session on the server too; nothing to do if that fails
    if (refreshToken) await logout(refreshToken).catch(() => {});
  }, [clear]);

  const renewTokens = useCallback(() => {
    if (!refreshingRef.current) {
      const current = sessionRef.current;
      refreshingRef.current = refreshTokens(current.tokens.refreshToken)
        .then((tokens) => {
          signIn({ ...current, tokens });
          return tokens;
        })
        .finally(() => {
          refreshingRef.current = null;
        });
    }
    return refreshingRef.current;
  }, [signIn]);

  // Bumped to load the permissions again (see below)
  const [permissionsVersion, setPermissionsVersion] = useState(0);
  const reloadPermissions = useCallback(() => setPermissionsVersion((version) => version + 1), []);

  /**
   * apiRequest with the admin's access token, renewing it once when it has expired. A 403 means the console's
   * permissions are out of date (a grant was withdrawn meanwhile), so they are loaded again and the screens follow.
   */
  const authRequest = useCallback(
    async (path, options = {}) => {
      if (!sessionRef.current) throw new ApiError('Please sign in again.', 401);
      const send = (token) =>
        apiRequest(path, { ...options, token }).catch((err) => {
          if (err.status === 403) reloadPermissions();
          throw err;
        });
      try {
        return await send(sessionRef.current.tokens.accessToken);
      } catch (err) {
        if (err.status !== 401) throw err;
      }
      let tokens;
      try {
        tokens = await renewTokens();
      } catch {
        clear();
        throw new ApiError('Your session has ended. Please sign in again.', 401);
      }
      return send(tokens.accessToken);
    },
    [clear, renewTokens, reloadPermissions]
  );

  // { isSuperUser, functions } of the signed-in user, or null while loading (or signed out)
  const [permissions, setPermissions] = useState(null);
  const userId = session?.user?.userId;

  useEffect(() => {
    if (!userId) return undefined;
    let cancelled = false;
    const load = () =>
      authRequest('/api/users/me/permissions')
        .then((loaded) => {
          if (!cancelled) setPermissions({ ...loaded, userId });
        })
        .catch(() => {
          // Keep what was loaded; a failed first load leaves everything hidden (and the backend refusing anyway)
        });
    load();
    const onVisible = () => {
      if (document.visibilityState === 'visible') load();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [authRequest, userId, permissionsVersion]);

  // Permissions of an earlier user (sign out, then in as someone else) never apply
  const current = permissions?.userId === userId ? permissions : null;
  const can = useCallback(
    (functionCode, access = 'READ') => {
      if (!current) return false;
      if (current.isSuperUser) return true;
      const grant = current.functions.find((fn) => fn.functionCode === functionCode);
      return Boolean(grant && { READ: grant.canRead, WRITE: grant.canWrite, DELETE: grant.canDelete }[access]);
    },
    [current]
  );

  const value = useMemo(
    () => ({
      session,
      signIn,
      signOut,
      authRequest,
      permissionsLoaded: Boolean(current),
      reloadPermissions,
      isSuperUser: Boolean(current?.isSuperUser),
      can
    }),
    [session, signIn, signOut, authRequest, current, can, reloadPermissions]
  );
  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}
