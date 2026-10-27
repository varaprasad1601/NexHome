/** Staff roles, highest first (as the backend's STAFF_ROLES); SUPER_USER is offered to super users only. */
export const STAFF_ROLES = [
  { code: 'SUPER_USER', label: 'Super User', badge: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { code: 'ADMIN', label: 'Admin', badge: 'bg-[#E9F6EF] text-[var(--green-2)]' },
  { code: 'SUB_ADMIN', label: 'Sub Admin', badge: 'bg-[#EDF6FD] text-[var(--blue)]' }
];

/** The roles the Add dialogs give, with the ids seeded by V1 (Create user takes a role id). */
export const ADMIN_ROLE = { id: 2, code: 'ADMIN', label: 'Admin' };
export const SUPER_USER_ROLE = { id: 1, code: 'SUPER_USER', label: 'Super User' };
export const SUB_ADMIN_ROLE = { id: 3, code: 'SUB_ADMIN', label: 'Sub Admin' };
export const CUSTOMER_ROLE = { id: 4, code: 'CUSTOMER', label: 'Customer' };

/** ACCOUNT_STATUS codes as the admin console names them (DEACTIVATED is "Inactive"). */
export const ACCOUNT_STATUSES = [
  { code: 'ACTIVE', label: 'Active' },
  { code: 'DEACTIVATED', label: 'Inactive' },
  { code: 'SUSPENDED', label: 'Suspended' },
  { code: 'BLOCKED', label: 'Blocked' }
];

/** The status filter's choices: staff are no longer blocked from the console, so Blocked is left out. */
export const STATUS_FILTERS = ACCOUNT_STATUSES.filter((status) => status.code !== 'BLOCKED');

/**
 * The function that guards accounts of a staff role (as the backend's NexHomePermissionService.canManageRole);
 * SUPER_USER has none: super users only.
 */
export const STAFF_ROLE_FUNCTIONS = { ADMIN: 'ADMIN_MANAGE', SUB_ADMIN: 'SUB_ADMIN_MANAGE' };

/**
 * Whether the signed-in admin (can, isSuperUser from useAdminAuth) may change accounts of the staff role; null (no
 * staff role) and SUPER_USER only for super users.
 */
export function canManageStaffRole(roleCode, can, isSuperUser, access = 'WRITE') {
  const fn = STAFF_ROLE_FUNCTIONS[roleCode];
  return fn ? can(fn, access) : isSuperUser;
}

export function staffRoleOf(code) {
  return STAFF_ROLES.find((role) => role.code === code);
}

/** Status badge colours: active green, inactive amber (warning), suspended and blocked red. */
const STATUS_BADGES = {
  ACTIVE: 'bg-[#E9F6EF] text-[var(--green-2)]',
  DEACTIVATED: 'bg-[#FFF7DE] text-[#8A6508]',
  SUSPENDED: 'bg-[#FDECEC] text-[#C53030]',
  BLOCKED: 'bg-[#FDECEC] text-[#C53030]'
};

export function statusBadgeClass(code) {
  return STATUS_BADGES[code] ?? STATUS_BADGES.DEACTIVATED;
}

export function accountStatusLabel(code) {
  return ACCOUNT_STATUSES.find((status) => status.code === code)?.label ?? code;
}
