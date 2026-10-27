/**
 * The admin dashboard's stat cards. Cards with a `source` show live figures from GET /api/users/stats (the
 * dashboard fills in value, change, note and trend); the others are sample figures, because bookings, payments and
 * ratings have no backend yet (Modules 6-8).
 */

export const STAT_CARDS = [
  { key: 'customers', label: 'Total Customers', source: 'customers', fn: 'CUSTOMER_MANAGE', tone: 'blue' },
  { key: 'partners', label: 'Total Partners', source: 'partners', fn: 'PARTNER_MANAGE', tone: 'green' },
  { key: 'bookings', label: 'Total Bookings', value: 4320, change: 18.2, note: '+664 this month', tone: 'navy',
    trend: [3, 3.4, 3.2, 3.8, 3.5, 4.2, 4.1, 4.8, 5.2, 6.1] },
  { key: 'revenue', label: 'Total Revenue', value: 1875000, currency: true, change: 22.4,
    note: '+₹3,42,000 this month', tone: 'teal', trend: [4, 4.4, 4.2, 4.8, 5, 4.9, 5.6, 5.8, 6.1, 6.6] },
  { key: 'rating', label: 'Average Rating', value: 4.6, decimals: 1, change: 2.1, note: 'Based on 1,240 reviews',
    tone: 'amber', trend: [5, 5.3, 5.1, 5.6, 5.4, 5.8, 5.7, 6, 6.2, 6.4] }
];
