/**
 * The admin dashboard's stat cards. Cards with a `source` show live figures (the dashboard fills in value, change,
 * note and trend from its `SOURCES`) and are shown only with read access to their `fn`.
 */

export const STAT_CARDS = [
  { key: 'customers', label: 'Total Customers', source: 'customers', fn: 'CUSTOMER_MANAGE', tone: 'blue' },
  { key: 'partners', label: 'Total Partners', source: 'partners', fn: 'PARTNER_MANAGE', tone: 'green' },
  { key: 'bookings', label: 'Total Bookings', source: 'bookings', fn: 'BOOKING_MANAGE', tone: 'navy' },
  { key: 'revenue', label: 'Total Revenue', source: 'revenue', fn: 'PAYMENT_MANAGE', currency: true, tone: 'teal' },
  { key: 'rating', label: 'Average Rating', source: 'rating', fn: 'REVIEW_MANAGE', decimals: 1, tone: 'amber' }
];
