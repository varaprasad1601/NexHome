/**
 * The onboarding steps, in order, and when each is complete. The checks follow the backend's requireReadyForReview
 * (the fields a submit needs) plus what the admin console's completion ring asks before Submit for Review: Aadhaar,
 * the work charges, a primary service, a government ID and a bank account.
 */
export const STEPS = [
  { key: 'personal', title: 'Personal details', icon: 'user', hint: 'Your name as on your ID, Aadhaar and how we reach you.' },
  { key: 'address', title: 'Your address', icon: 'home', hint: 'Where you live. Customers never see it.' },
  { key: 'work', title: 'Work details', icon: 'briefcase', hint: 'Your skills, experience and what you expect to earn.' },
  { key: 'services', title: 'Services you offer', icon: 'tool', hint: 'Pick the services you do, and mark your main one.' },
  { key: 'coverage', title: 'Areas & working hours', icon: 'map', hint: 'Where you travel to and when you can take jobs.' },
  { key: 'emergency', title: 'Emergency contact', icon: 'phone-call', hint: 'Someone we can call if something happens on a job.' },
  { key: 'documents', title: 'Documents', icon: 'file-text', hint: 'A government ID is required; skill certificates help.' },
  { key: 'bank', title: 'Bank account', icon: 'credit-card', hint: 'Where your online earnings are paid.' }
];

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const DAY_SHORT = { MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun' };

const filled = (value) => value !== null && value !== undefined && value !== '';

/** Whether the weekly grid agrees with the weekend availability (the backend's submit rule), else the problem. */
export function weekendProblem(weekend, weekly) {
  const saturday = (weekly?.SATURDAY ?? []).length > 0;
  const sunday = (weekly?.SUNDAY ?? []).length > 0;
  if (weekend === 'SATURDAY' && !(saturday && !sunday)) return 'You chose Saturday only: tick Saturday slots and no Sunday slots.';
  if (weekend === 'SUNDAY' && !(sunday && !saturday)) return 'You chose Sunday only: tick Sunday slots and no Saturday slots.';
  if (weekend === 'NEITHER' && (saturday || sunday)) return 'You chose no weekends: untick the Saturday and Sunday slots.';
  return '';
}

/** Which steps are complete, from the profile and its records. */
export function stepsDone({ profile, services, documents, banks }) {
  const p = profile ?? {};
  const weekly = p.weeklyAvailability ?? {};
  return {
    personal: filled(p.firstName) && filled(p.lastName) && p.isAdult === true && filled(p.preferredContactLanguage) && filled(p.aadhaarNumber),
    address: [p.addressLine1, p.locality, p.city, p.state, p.postalCode].every(filled),
    work: [p.skillCategory, p.yearsExperience, p.mainServiceCategoryId, p.preferredArrangement, p.paymentBasis, p.expectedEarningsInr, p.visitChargeInr, p.minimumJobPaymentInr].every(filled),
    services: (services ?? []).some((s) => s.isPrimaryService && s.isActive),
    coverage:
      (p.serviceAreas ?? []).length > 0 &&
      filled(p.maxTravelKm) &&
      filled(p.weekendAvailability) &&
      Object.values(weekly).some((slots) => (slots ?? []).length > 0) &&
      !weekendProblem(p.weekendAvailability, weekly),
    emergency: [p.emergencyContactName, p.emergencyContactRelationship, p.emergencyContactNumber].every(filled),
    documents: (documents ?? []).some((d) => d.documentType === 'GOVERNMENT_ID'),
    bank: (banks ?? []).length > 0
  };
}

/** Saves fields of the signed-in partner's profile (PUT /api/partners/profiles/update; null keeps a value). */
export async function saveProfile(authRequest, profile, fields) {
  return authRequest('/api/partners/profiles/update', { method: 'PUT', body: { partnerProfileId: profile.partnerProfileId, ...fields } });
}

/** The backend's field names in its "Complete these fields first" message, in words. */
const FIELD_WORDS = {
  firstName: 'first name',
  lastName: 'last name',
  isAdult: 'age confirmation',
  preferredContactLanguage: 'preferred language',
  addressLine1: 'address',
  locality: 'locality',
  city: 'city',
  state: 'state',
  postalCode: 'PIN code',
  skillCategory: 'skill category',
  yearsExperience: 'years of experience',
  mainServiceCategoryId: 'main service',
  preferredArrangement: 'work arrangement',
  paymentBasis: 'payment basis',
  expectedEarningsInr: 'expected earnings',
  maxTravelKm: 'travel distance',
  weekendAvailability: 'weekend availability',
  serviceAreas: 'service areas',
  weeklyAvailability: 'weekly working hours'
};

/** "Complete these fields first: firstName, city." with readable names. */
export function readableSubmitError(message) {
  return (message ?? '').replace(/(Complete these fields first: )(.*)\./, (_, start, list) =>
    `${start}${list
      .split(', ')
      .map((name) => FIELD_WORDS[name] ?? name)
      .join(', ')}.`
  );
}
