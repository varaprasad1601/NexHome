import {
  Bell,
  CalendarCheck,
  CreditCard,
  LifeBuoy,
  Megaphone,
  ShieldCheck,
  Star,
  UserRoundCheck
} from 'lucide-react';

import { apiDateTimeParts, toInstant } from '../utils/schedule';

/**
 * Module 9 in the console: reviews, support tickets, notifications and audit logs. Labels come from the lookups
 * (useLookupLabels with these types); colours and icons are here.
 */
export const SUPPORT_LOOKUPS = [
  'REVIEW_STATUS',
  'SUPPORT_CATEGORY',
  'SUPPORT_PRIORITY',
  'SUPPORT_TICKET_STATUS',
  'NOTIFICATION_TYPE',
  'NOTIFICATION_CHANNEL',
  'DELIVERY_STATUS'
];

const GREEN = 'bg-[#E9F6EF] text-[var(--green-2)]';
const BLUE = 'bg-[#EDF6FD] text-[var(--blue)]';
const AMBER = 'bg-[#FFF7DE] text-[#8A6508]';
const RED = 'bg-[#FDECEC] text-[#C53030]';
const NAVY = 'bg-[#E8EEF6] text-[var(--navy)]';
const GREY = 'bg-[var(--light)] text-[var(--muted)]';

const TONES = {
  REVIEW_STATUS: { PUBLISHED: GREEN, HIDDEN: GREY, UNDER_REVIEW: AMBER },
  SUPPORT_TICKET_STATUS: { OPEN: AMBER, ASSIGNED: BLUE, IN_PROGRESS: NAVY, RESOLVED: GREEN, CLOSED: GREY },
  SUPPORT_PRIORITY: { LOW: GREY, MEDIUM: BLUE, HIGH: AMBER, URGENT: RED },
  DELIVERY_STATUS: { PENDING: AMBER, SENT: BLUE, DELIVERED: GREEN, FAILED: RED }
};

/** The badge colours of a Module 9 status (grey for anything unknown). */
export const supportBadgeClass = (type, code) => TONES[type]?.[code] ?? GREY;

/** Ticket statuses still being worked on. */
export const OPEN_TICKET_STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS'];

/** Each NOTIFICATION_TYPE's icon and tile colour (the bell and the notifications pages). */
export const NOTIFICATION_STYLES = {
  BOOKING: { icon: CalendarCheck, tile: 'bg-[#F3ECFF] text-[#6B46C1]' },
  PAYMENT: { icon: CreditCard, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  REVIEW: { icon: Star, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  SUPPORT: { icon: LifeBuoy, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  ONBOARDING: { icon: UserRoundCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  ACCOUNT: { icon: ShieldCheck, tile: 'bg-[#FDECEC] text-[#C53030]' },
  ANNOUNCEMENT: { icon: Megaphone, tile: 'bg-[#E8EEF6] text-[var(--navy)]' }
};
export const notificationStyle = (type) => NOTIFICATION_STYLES[type] ?? { icon: Bell, tile: GREY };

/** The console page a notification is about (null when it is about nothing in particular). */
export function notificationLink(notification) {
  const id = notification.referenceId;
  switch (notification.referenceType) {
    case 'BOOKING':
      return `/admin/bookings/${id}`;
    case 'SUPPORT_TICKET':
      return `/admin/support/${id}`;
    case 'REVIEW':
      return '/admin/reviews';
    case 'PARTNER_PROFILE':
      return `/admin/users/partners/${id}`;
    default:
      return null;
  }
}

/** A backend date in milliseconds (null if unreadable). */
export function millisOf(value) {
  const parts = apiDateTimeParts(value);
  return parts ? new Date(toInstant(parts.date, parts.time)).getTime() : null;
}

/** "Just now", "6 min ago", "2 hr ago", "Yesterday" or "3 days ago" for a backend date. */
export function timeAgo(value, now = Date.now()) {
  const millis = millisOf(value);
  if (millis == null) return '';
  const minutes = Math.max(0, Math.floor((now - millis) / 60000));
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? 'Yesterday' : `${days} days ago`;
}

/** The audiences staff send notifications to (POST /api/notifications/send). */
export const NOTIFICATION_AUDIENCES = [
  { value: 'CUSTOMERS', label: 'All Customers' },
  { value: 'PARTNERS', label: 'All Partners' },
  { value: 'STAFF', label: 'All Staff' },
  { value: 'USER', label: 'One User (by mobile number)' }
];
