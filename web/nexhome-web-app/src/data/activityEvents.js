import { accountStatusLabel } from './staffOptions';

const GREEN = 'bg-[#E9F6EF] text-[var(--green-2)]';
const BLUE = 'bg-[#EDF6FD] text-[var(--blue)]';
const AMBER = 'bg-[#FFF7DE] text-[#8A6508]';

/**
 * The events of the activity logs built from the audit columns (NexHomeAuditEntry.event): the action badge's text and
 * colour. Created and verified are green, added blue, updated amber.
 */
export const ACTIVITY_EVENTS = {
  ACCOUNT_CREATED: { label: 'Account created', tone: GREEN },
  ACCOUNT_UPDATED: { label: 'Account updated', tone: AMBER },
  STATUS_UPDATED: { label: 'Status changed', tone: BLUE },
  PROFILE_CREATED: { label: 'Profile created', tone: GREEN },
  PROFILE_UPDATED: { label: 'Profile updated', tone: AMBER },
  ADDRESS_ADDED: { label: 'Address added', tone: BLUE },
  ADDRESS_UPDATED: { label: 'Address updated', tone: AMBER },
  DOCUMENT_ADDED: { label: 'Document added', tone: BLUE },
  DOCUMENT_VERIFIED: { label: 'Document verified', tone: GREEN },
  BANK_ACCOUNT_ADDED: { label: 'Bank details added', tone: BLUE },
  BANK_ACCOUNT_VERIFIED: { label: 'Bank details verified', tone: GREEN },
  ACCOUNT_VERIFIED: { label: 'Profile verified', tone: GREEN },
  APPROVED: { label: 'Approved', tone: GREEN }
};

/** The "What" column: a status change names the new status; other subjects show as they are. */
export function activitySubject(entry) {
  if (entry.event === 'STATUS_UPDATED') return `Now ${accountStatusLabel(entry.subject)}`;
  return entry.subject;
}

/** The partner's activity log in three sections, as the partner profile is organised. */
export const PARTNER_ACTIVITY_SECTIONS = [
  { title: 'Partner Profile', events: ['PROFILE_CREATED', 'PROFILE_UPDATED', 'ACCOUNT_VERIFIED', 'APPROVED'] },
  { title: 'Documents', events: ['DOCUMENT_ADDED', 'DOCUMENT_VERIFIED'] },
  { title: 'Bank Account', events: ['BANK_ACCOUNT_ADDED', 'BANK_ACCOUNT_VERIFIED'] }
];
