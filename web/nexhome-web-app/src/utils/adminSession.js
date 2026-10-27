const STORAGE_KEY = 'nexhome.adminSession';

/** Roles that may sign in to the admin area. */
export const STAFF_ROLE_CODES = ['SUPER_USER', 'ADMIN', 'SUB_ADMIN'];

/** True when the user holds an active staff role. */
export function isStaffUser(user) {
  return Boolean(user?.roles?.some((role) => role.isActive && STAFF_ROLE_CODES.includes(role.roleCode)));
}

/** The stored { user, tokens } of the signed-in admin, or null. */
export function loadAdminSession() {
  try {
    const session = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return session?.user && session?.tokens ? session : null;
  } catch {
    return null;
  }
}

export function saveAdminSession(session) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable (private mode): the session lasts until the page reloads
  }
}

export function clearAdminSession() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing stored
  }
}
