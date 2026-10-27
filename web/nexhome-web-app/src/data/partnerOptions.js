/** The PARTNER role as Create user takes it (seeded id 5, V1). */
export const PARTNER_ROLE = { id: 5, code: 'PARTNER', label: 'Partner' };

/** The partner list's onboarding filter for partners who have not started their onboarding profile. */
export const NOT_STARTED = 'NOT_STARTED';

/** Onboarding status badge colours (PARTNER_ONBOARDING_STATUS): waiting on the partner, on staff, done, stopped. */
const ONBOARDING_BADGES = {
  DRAFT: 'bg-[var(--light)] text-[var(--muted)]',
  PROFILE_INCOMPLETE: 'bg-[#FFF7DE] text-[#8A6508]',
  UNDER_REVIEW: 'bg-[#EDF6FD] text-[var(--blue)]',
  TRAINING_REQUIRED: 'bg-[#F3ECFF] text-[#6B46C1]',
  APPROVED: 'bg-[#E9F6EF] text-[var(--green-2)]',
  REJECTED: 'bg-[#FDECEC] text-[#C53030]',
  SUSPENDED: 'bg-[#FDECEC] text-[#C53030]'
};

export function onboardingBadgeClass(code) {
  return ONBOARDING_BADGES[code] ?? ONBOARDING_BADGES.DRAFT;
}

/** Verification status badge colours (VERIFICATION_STATUS), for profiles, documents and bank accounts. */
const VERIFICATION_BADGES = {
  PENDING: 'bg-[var(--light)] text-[var(--muted)]',
  IN_REVIEW: 'bg-[#EDF6FD] text-[var(--blue)]',
  VERIFIED: 'bg-[#E9F6EF] text-[var(--green-2)]',
  REJECTED: 'bg-[#FDECEC] text-[#C53030]',
  EXPIRED: 'bg-[#FFF7DE] text-[#8A6508]'
};

export function verificationBadgeClass(code) {
  return VERIFICATION_BADGES[code] ?? VERIFICATION_BADGES.PENDING;
}

/** The availability dot colour (PARTNER_AVAILABILITY_STATUS). */
const AVAILABILITY_DOTS = { ONLINE: 'bg-[var(--green)]', BUSY: 'bg-[#E0A800]', OFFLINE: 'bg-[#A8B6C6]' };

export function availabilityDotClass(code) {
  return AVAILABILITY_DOTS[code] ?? AVAILABILITY_DOTS.OFFLINE;
}
