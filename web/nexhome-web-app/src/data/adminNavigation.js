import {
  CalendarDays,
  ChartColumn,
  LayoutDashboard,
  LifeBuoy,
  Megaphone,
  Settings,
  SlidersHorizontal,
  UserRound,
  Wallet,
  Wrench
} from 'lucide-react';

/**
 * The admin sidebar: single links and groups of links; the top level has icons.
 *
 * A link with `fn` needs READ on that function (or on any of them, for a list) to be shown and opened; WRITE and
 * DELETE decide the screen's actions. `superUserOnly` links are for super users. Links without either are for every
 * staff member: the features behind them (offers, banners, reports, general settings) have no functions yet. A group without a visible link is hidden.
 */
export const ADMIN_NAV = [
  { label: 'Dashboard', to: '/admin/dashboard', icon: LayoutDashboard },
  {
    label: 'User Management',
    icon: UserRound,
    items: [
      { label: 'Manage Super Admins', to: '/admin/users/super-admins', superUserOnly: true },
      { label: 'Manage Admins', to: '/admin/users/admins', fn: 'ADMIN_MANAGE' },
      { label: 'Manage Sub Admins', to: '/admin/users/sub-admins', fn: 'SUB_ADMIN_MANAGE' },
      { label: 'Manage Customers', to: '/admin/users/customers', fn: 'CUSTOMER_MANAGE' },
      { label: 'Manage Partners', to: '/admin/users/partners', fn: 'PARTNER_MANAGE' }
    ]
  },
  {
    label: 'Service Management',
    icon: Wrench,
    items: [
      { label: 'Manage Categories', to: '/admin/services/categories', fn: 'CATALOG_MANAGE' },
      { label: 'Manage Services', to: '/admin/services', fn: 'CATALOG_MANAGE' }
    ]
  },
  {
    label: 'Bookings',
    icon: CalendarDays,
    items: [
      { label: 'All Bookings', to: '/admin/bookings', fn: 'BOOKING_MANAGE' },
      { label: 'Partner Availability', to: '/admin/bookings/availability', fn: 'PARTNER_AVAILABILITY_MANAGE' }
    ]
  },
  {
    label: 'Payments & Payouts',
    icon: Wallet,
    items: [
      { label: 'Customer Payments', to: '/admin/payments/customers', fn: 'PAYMENT_MANAGE' },
      { label: 'Partner Payouts', to: '/admin/payments/payouts', fn: 'PAYOUT_MANAGE' },
      { label: 'Transactions', to: '/admin/payments/transactions', fn: ['PAYMENT_MANAGE', 'PAYOUT_MANAGE'] },
      { label: 'Refunds', to: '/admin/payments/refunds', fn: 'PAYMENT_MANAGE' }
    ]
  },
  {
    label: 'Support & Feedback',
    icon: LifeBuoy,
    items: [
      { label: 'Reviews & Ratings', to: '/admin/reviews', fn: 'REVIEW_MANAGE' },
      { label: 'Support Tickets', to: '/admin/support', fn: 'SUPPORT_MANAGE' }
    ]
  },
  {
    label: 'Marketing',
    icon: Megaphone,
    items: [
      { label: 'Offers & Discounts', to: '/admin/marketing/offers' },
      { label: 'Notifications', to: '/admin/marketing/notifications', fn: 'NOTIFICATION_MANAGE' },
      { label: 'Banners & Content', to: '/admin/marketing/banners' }
    ]
  },
  {
    label: 'Reports & Analytics',
    icon: ChartColumn,
    items: [
      { label: 'Overview', to: '/admin/reports' },
      { label: 'Bookings Report', to: '/admin/reports/bookings' },
      { label: 'Revenue Report', to: '/admin/reports/revenue' },
      { label: 'Partners Report', to: '/admin/reports/partners' },
      { label: 'Customer Report', to: '/admin/reports/customers' }
    ]
  },
  {
    label: 'System Configuration',
    icon: SlidersHorizontal,
    items: [
      { label: 'Manage Roles', to: '/admin/system/roles', fn: 'ROLE_MANAGE' },
      { label: 'Manage Functions', to: '/admin/system/functions', fn: 'FUNCTION_MANAGE' },
      { label: 'Manage Lookups', to: '/admin/system/lookups', fn: 'LOOKUP_MANAGE' },
      { label: 'Audit Logs', to: '/admin/system/audit-logs', fn: 'AUDIT_LOG_VIEW' }
    ]
  },
  {
    label: 'Settings',
    icon: Settings,
    items: [
      { label: 'General', to: '/admin/settings' },
      { label: 'Policies & FAQs', to: '/admin/settings/policies', fn: ['POLICY_MANAGE', 'FAQ_MANAGE'] }
    ]
  }
];

/**
 * The breadcrumb of a path: its sidebar group (no page of its own) and link, e.g. [{ label: 'User Management' },
 * { label: 'Admins', to: '/admin/users/admins' }]; empty for paths that are not in the sidebar.
 */
export function findNavTrail(pathname) {
  for (const entry of ADMIN_NAV) {
    if (entry.to === pathname) return [{ label: entry.label, to: entry.to }];
    const item = entry.items?.find((child) => child.to === pathname);
    if (item) return [{ label: entry.label }, { label: item.label, to: item.to }];
  }
  return [];
}

/** Whether the signed-in admin may see and open a sidebar link (`can` and `isSuperUser` from useAdminAuth). */
export function canOpen(item, can, isSuperUser) {
  if (item.superUserOnly) return isSuperUser;
  if (!item.fn) return true;
  return (Array.isArray(item.fn) ? item.fn : [item.fn]).some((code) => can(code, 'READ'));
}

/**
 * The sidebar link a path belongs to: the longest link the path is at or below (so a role's page belongs to Roles),
 * or null for paths outside the sidebar (e.g. My Profile).
 */
export function findNavItem(pathname) {
  let found = null;
  ADMIN_NAV.flatMap((entry) => entry.items ?? [entry]).forEach((item) => {
    if ((pathname === item.to || pathname.startsWith(`${item.to}/`)) && (!found || item.to.length > found.to.length)) {
      found = item;
    }
  });
  return found;
}
