/**
 * The top bar's notifications. **Sample data**: the backend has no notifications yet, so these show how the panel
 * works (tabs, kind filters, priority, read, mark all as read, clear all). `kind` is one of NOTIFICATION_KINDS;
 * `priority` is `high` or `normal` (high ones are tagged and listed first); `minutesAgo` is relative to when the
 * console was opened; `to` is the page a notification opens.
 */
export const NOTIFICATION_KINDS = [
  { key: 'customer', label: 'Customers' },
  { key: 'partner', label: 'Partners' },
  { key: 'booking', label: 'Bookings' },
  { key: 'payment', label: 'Payments' },
  { key: 'system', label: 'System' }
];

export const SAMPLE_NOTIFICATIONS = [
  {
    id: 1,
    kind: 'customer',
    priority: 'normal',
    title: 'New customer registered',
    text: 'Ravi Kumar signed up with +91 98765 43210.',
    minutesAgo: 6,
    to: '/admin/users/customers',
    read: false
  },
  {
    id: 2,
    kind: 'partner',
    priority: 'high',
    title: 'Partner profile awaiting review',
    text: 'Suresh Babu (Plumbing) submitted their profile for verification.',
    minutesAgo: 84,
    to: '/admin/users/partners',
    read: false
  },
  {
    id: 3,
    kind: 'booking',
    priority: 'normal',
    title: 'Booking cancelled',
    text: 'Booking NHBOOK0042 for AC servicing was cancelled by the customer.',
    minutesAgo: 190,
    to: '/admin/bookings',
    read: false
  },
  {
    id: 4,
    kind: 'payment',
    priority: 'normal',
    title: 'Payout processed',
    text: '₹4,250 was paid out to 3 partners for last week.',
    minutesAgo: 60 * 26,
    to: '/admin/payments/payouts',
    read: true
  },
  {
    id: 5,
    kind: 'system',
    priority: 'high',
    title: 'New sign-in to the admin console',
    text: 'Your account signed in from a new browser on Windows.',
    minutesAgo: 60 * 50,
    to: '/admin/profile',
    read: true
  }
];
