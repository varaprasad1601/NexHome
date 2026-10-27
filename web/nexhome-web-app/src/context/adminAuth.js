import { createContext, useContext } from 'react';

/**
 * The signed-in admin: { session, signIn(session), signOut(), authRequest(path, options), permissionsLoaded,
 * isSuperUser, can(functionCode, 'READ' | 'WRITE' | 'DELETE'), reloadPermissions() }; session is { user, tokens } or null. authRequest
 * calls the backend with the admin's token; can() answers from GET /api/users/me/permissions (see AdminAuthProvider).
 */
export const AdminAuthContext = createContext(null);

export function useAdminAuth() {
  const value = useContext(AdminAuthContext);
  if (!value) throw new Error('useAdminAuth must be used inside AdminAuthProvider');
  return value;
}
