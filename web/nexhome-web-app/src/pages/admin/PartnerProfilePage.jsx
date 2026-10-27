import {
  BadgeCheck,
  Banknote,
  BriefcaseBusiness,
  CalendarCheck,
  CalendarClock,
  CalendarDays,
  CalendarX,
  CircleCheckBig,
  Clock,
  FileText,
  HeartHandshake,
  IdCard,
  Languages,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  Star,
  UserRound,
  Wallet,
  Wrench
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import CompletionAvatar from '../../components/admin/profile/CompletionAvatar';
import { InfoRow, InfoSection, PendingTab, StatTile } from '../../components/admin/profile/ProfileParts';
import { profileTabClass } from '../../components/admin/profile/profileStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { availabilityDotClass, onboardingBadgeClass, verificationBadgeClass } from '../../data/partnerOptions';
import { accountStatusLabel, statusBadgeClass } from '../../data/staffOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatRupees, splitApiDateTime } from '../../utils/format';

const LOOKUPS = [
  'SKILL_CATEGORY',
  'PREFERRED_CONTACT_LANGUAGE',
  'PREFERRED_ARRANGEMENT',
  'PAYMENT_BASIS',
  'WEEKEND_AVAILABILITY',
  'EMERGENCY_CONTACT_RELATIONSHIP',
  'AVAILABILITY_SLOT',
  'PARTNER_ONBOARDING_STATUS',
  'VERIFICATION_STATUS',
  'PARTNER_AVAILABILITY_STATUS',
  'DOCUMENT_TYPE'
];
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** The details a partner's onboarding needs (the backend's submit checks), in the order the completion lists them. */
const PROFILE_CHECKS = [
  { label: 'Name', done: (profile) => Boolean(profile.firstName && profile.lastName) },
  { label: 'Verified mobile', done: (profile, user) => Boolean(user.accountVerifiedDate) },
  { label: 'Aadhaar number', done: (profile) => Boolean(profile.aadhaarNumber) },
  { label: 'Address', done: (profile) => Boolean(profile.addressLine1 && profile.city && profile.postalCode) },
  { label: 'Skill category', done: (profile) => Boolean(profile.skillCategory) },
  { label: 'Main service', done: (profile) => Boolean(profile.mainServiceCategoryId) },
  { label: 'Experience', done: (profile) => profile.yearsExperience != null },
  { label: 'Service areas', done: (profile) => (profile.serviceAreas ?? []).length > 0 },
  { label: 'Weekly availability', done: (profile) => Object.keys(profile.weeklyAvailability ?? {}).length > 0 }
];

/**
 * The statistics. Jobs, earnings and reviews have no backend yet (bookings, payouts and reviews modules), so each
 * shows a dash with a note rather than made-up figures.
 */
const STAT_CARDS = [
  {
    label: 'Total Jobs',
    icon: CalendarCheck,
    tone: 'bg-[#EDF6FD] text-[var(--blue)]',
    note: 'Available once bookings go live'
  },
  {
    label: 'Completed Jobs',
    icon: CircleCheckBig,
    tone: 'bg-[#E9F6EF] text-[var(--green)]',
    note: 'Available once bookings go live'
  },
  {
    label: 'Cancelled Jobs',
    icon: CalendarX,
    tone: 'bg-[#FDECEC] text-[#E53E3E]',
    note: 'Available once bookings go live'
  },
  {
    label: 'Total Earnings',
    icon: Wallet,
    tone: 'bg-[#E8EEF6] text-[var(--navy)]',
    note: 'Available once payouts go live'
  },
  { label: 'Average Rating', icon: Star, tone: 'bg-[#FFF7DE] text-[#B7860B]', note: 'Available once reviews go live' }
];

/** The tabs whose data comes with later modules: an empty state until then. */
const PENDING_TABS = {
  jobs: {
    label: 'Jobs',
    icon: CalendarCheck,
    count: 0,
    text: "The partner's jobs will show here once bookings go live."
  },
  earnings: {
    label: 'Earnings',
    icon: Wallet,
    text: "The partner's earnings and payouts will show here once payouts go live."
  },
  reviews: {
    label: 'Reviews',
    icon: Star,
    count: 0,
    text: 'Reviews of the partner will show here once reviews go live.'
  }
};

function fullName(profile) {
  return [profile.firstName, profile.middleName, profile.lastName].filter(Boolean).join(' ');
}

function formatDate(value) {
  return splitApiDateTime(value)?.date ?? null;
}

function formatDateTime(value) {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : null;
}

/** A yyyy-MM-dd date as 12 Mar 2026. */
function formatLocalDate(value) {
  if (!value) return null;
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  });
}

const rupees = (value) => (value == null ? null : formatRupees(Number(value)));

function Badge({ className, children }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${className}`}>{children}</span>
  );
}

function Chips({ items }) {
  return items?.length ? (
    <span className="flex flex-wrap gap-1.5">
      {items.map((item) => (
        <span
          key={item}
          className="rounded-full bg-[#EDF6FD] px-2.5 py-0.5 text-[0.8125rem] font-bold text-[var(--blue)]"
        >
          {item}
        </span>
      ))}
    </span>
  ) : null;
}

/** A row of a list tab (services, documents, bank accounts): an icon tile, the main line, details and a badge. */
function ListRow({ icon: Icon, title, details, badge }) {
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-xl border border-[var(--line)] p-4">
      <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
        <Icon size={18} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="font-black text-[var(--navy)]">{title}</div>
        <div className="mt-0.5 text-sm text-[var(--muted)]">{details}</div>
      </div>
      {badge}
    </div>
  );
}

function EmptyList({ icon: Icon, title, text }) {
  return (
    <div className="flex flex-col items-center py-10 text-center">
      <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
        <Icon size={22} />
      </span>
      <div className="font-bold text-[var(--ink)]">{title}</div>
      <div className="text-sm text-[var(--muted)]">{text}</div>
    </div>
  );
}

/**
 * User Management > Partners > partner id: the partner's onboarding profile, read only. The summary (photo with the
 * onboarding completion, name, account and onboarding status, partner id, joined on, availability), the statistics
 * (jobs, earnings and rating: dashes until those modules exist), then Overview (personal, work, contact, address,
 * emergency contact, onboarding), Services, Documents, Bank Accounts and Availability, and Jobs, Earnings and Reviews
 * to come. Data from GET /api/partners/profiles/{id} (PARTNER_ONBOARDING_MANAGE read), its services, documents and
 * bank accounts, /api/users/{userId}, the catalogue categories (main service name) and the lookups. Reviewing,
 * verifying and editing are not here yet.
 */
export default function PartnerProfilePage() {
  const { partnerProfileId } = useParams();
  const { authRequest, can } = useAdminAuth();
  const { values, label } = useLookupLabels(LOOKUPS);
  const [state, setState] = useState({ profile: null, user: null, services: [], documents: [], banks: [], error: '' });
  const [categories, setCategories] = useState([]);
  const [tab, setTab] = useState('overview');

  useEffect(() => {
    let cancelled = false;
    const list = (path) => authRequest(path).catch(() => []);
    authRequest(`/api/partners/profiles/${partnerProfileId}`)
      .then((profile) =>
        Promise.all([
          authRequest(`/api/users/${profile.userId}`),
          list(`/api/partners/profiles/services/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/documents/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/bank-accounts/${profile.partnerProfileId}`)
        ]).then(([user, services, documents, banks]) => {
          if (!cancelled) setState({ profile, user, services, documents, banks, error: '' });
        })
      )
      .catch((err) => {
        if (!cancelled)
          setState({ profile: null, user: null, services: [], documents: [], banks: [], error: err.message });
      });
    authRequest('/api/catalog/categories')
      .then((list) => !cancelled && setCategories(list))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, partnerProfileId]);

  const { profile, user, services, documents, banks, error } = state;
  const name = profile ? fullName(profile) : '';

  useEffect(() => {
    document.title = `${name || profile?.publicId || 'Partner'} | NexHome Admin`;
  }, [name, profile]);

  // The partner id once loaded; nothing in its place meanwhile
  const crumbs = [
    { label: 'User Management' },
    { label: 'Partners', to: '/admin/users/partners' },
    ...(profile ? [{ label: profile.publicId }] : [])
  ];

  if (error || !profile) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} />
        {error ? (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {error}
          </div>
        ) : (
          <div className="h-40 animate-pulse rounded-2xl bg-white" />
        )}
      </div>
    );
  }

  const initials = [profile.firstName, profile.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
  const mainService = categories.find((category) => category.serviceCategoryId === profile.mainServiceCategoryId)?.name;
  const approved = profile.onboardingStatus === 'APPROVED';
  const slots = values.AVAILABILITY_SLOT ?? [];
  const address = [
    [profile.addressLine1, profile.addressLine2].filter(Boolean).join(', '),
    profile.landmark && `Near ${profile.landmark}`,
    [profile.locality, profile.city, profile.state].filter(Boolean).join(', ') +
      (profile.postalCode ? ` - ${profile.postalCode}` : '')
  ].filter(Boolean);

  const tabs = [
    { key: 'overview', label: 'Overview', icon: UserRound },
    { key: 'services', label: 'Services', icon: Wrench, count: services.length },
    { key: 'documents', label: 'Documents', icon: FileText, count: documents.length },
    { key: 'banks', label: 'Bank Accounts', icon: Banknote, count: banks.length },
    { key: 'availability', label: 'Availability', icon: CalendarClock },
    ...Object.entries(PENDING_TABS).map(([key, pending]) => ({ key, ...pending }))
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-center gap-6 px-6 pb-6 pt-7 sm:px-8">
          <CompletionAvatar
            checks={PROFILE_CHECKS.map((check) => ({ label: check.label, done: check.done(profile, user) }))}
            photoUrl={user.profilePhotoUrl}
            name={name}
            initials={initials}
            canEdit={can('PARTNER_MANAGE', 'WRITE')}
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className={`mb-0 text-2xl font-black leading-tight ${name ? 'text-[var(--navy)]' : 'text-[var(--muted)]'}`}>
                {name || 'Name not added yet'}
              </h1>
              <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadgeClass(user.accountStatus)}`}>
                {accountStatusLabel(user.accountStatus)}
              </span>
              <span
                className={`rounded-full px-3 py-1 text-xs font-bold ${onboardingBadgeClass(profile.onboardingStatus)}`}
              >
                {label('PARTNER_ONBOARDING_STATUS', profile.onboardingStatus)}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <IdCard size={15} className="text-[var(--blue)]" />
                Partner ID: <span className="font-bold text-[var(--ink)]">{profile.publicId}</span>
              </span>
              {mainService && (
                <span className="flex items-center gap-2">
                  <Wrench size={15} className="text-[var(--blue)]" />
                  <span className="font-bold text-[var(--ink)]">{mainService}</span>
                </span>
              )}
            </div>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <CalendarDays size={15} className="text-[var(--blue)]" />
                Joined on <span className="font-bold text-[var(--ink)]">{formatDate(user.creationDate) ?? '—'}</span>
              </span>
              {approved && (
                <span className="flex items-center gap-2">
                  <span className={`h-2 w-2 rounded-full ${availabilityDotClass(profile.availabilityStatus)}`} />
                  <span className="font-bold text-[var(--ink)]">
                    {label('PARTNER_AVAILABILITY_STATUS', profile.availabilityStatus)}
                  </span>
                </span>
              )}
            </div>
          </div>
        </div>

        <div
          className="nh-scroll-hidden mx-6 flex gap-8 overflow-x-auto whitespace-nowrap border-b border-[var(--line)] sm:mx-8"
          role="tablist"
        >
          {tabs.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={tab === item.key}
                className={profileTabClass(tab === item.key)}
                onClick={() => setTab(item.key)}
              >
                <Icon size={16} />
                {item.label}
                {item.count !== undefined && (
                  <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] text-[var(--muted)]">
                    {item.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="px-6 py-7 sm:px-8" role="tabpanel">
          {tab === 'overview' && (
            <>
              <section className="mb-8">
                <h3 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">Partner Statistics</h3>
                <div className="nh-scroll-hidden -mx-6 flex gap-4 overflow-x-auto px-6 pb-1 sm:-mx-8 sm:px-8 xl:mx-0 xl:grid xl:grid-cols-5 xl:overflow-visible xl:px-0 xl:pb-0">
                  {STAT_CARDS.map((card) => (
                    <StatTile key={card.label} {...card} value={null} />
                  ))}
                </div>
              </section>

              <div className="grid grid-cols-1 gap-10 lg:grid-cols-2 lg:gap-0 lg:divide-x lg:divide-[var(--line)]">
                <div className="space-y-8 lg:pr-10">
                  <InfoSection title="Personal Information">
                    <InfoRow icon={UserRound} label="Full Name" value={name} />
                    <InfoRow icon={IdCard} label="Aadhaar Number" value={profile.aadhaarNumber} />
                    <InfoRow
                      icon={Languages}
                      label="Preferred Language"
                      value={label('PREFERRED_CONTACT_LANGUAGE', profile.preferredContactLanguage)}
                    />
                    <InfoRow
                      icon={BadgeCheck}
                      label="18 Years or Older"
                      value={profile.isAdult == null ? null : profile.isAdult ? 'Yes' : 'No'}
                    />
                    <InfoRow
                      icon={CalendarDays}
                      label="Registered On"
                      value={formatLocalDate(profile.registrationDate)}
                    />
                  </InfoSection>
                  <InfoSection title="Work Details">
                    <InfoRow icon={Wrench} label="Main Service" value={mainService} />
                    <InfoRow
                      icon={BriefcaseBusiness}
                      label="Skill Category"
                      value={label('SKILL_CATEGORY', profile.skillCategory)}
                    />
                    <InfoRow
                      icon={CalendarClock}
                      label="Experience"
                      value={
                        profile.yearsExperience == null
                          ? null
                          : `${profile.yearsExperience} year${profile.yearsExperience === 1 ? '' : 's'}`
                      }
                    />
                    <InfoRow
                      icon={HeartHandshake}
                      label="Preferred Arrangement"
                      value={label('PREFERRED_ARRANGEMENT', profile.preferredArrangement)}
                    />
                    <InfoRow icon={Wallet} label="Payment Basis" value={label('PAYMENT_BASIS', profile.paymentBasis)} />
                    <InfoRow
                      icon={Banknote}
                      label="Expected Earnings · Visit Charge · Minimum Job"
                      value={[
                        rupees(profile.expectedEarningsInr),
                        rupees(profile.visitChargeInr),
                        rupees(profile.minimumJobPaymentInr)
                      ]
                        .map((value) => value ?? '—')
                        .join('  ·  ')}
                    />
                    <InfoRow icon={Star} label="Additional Skills" value={<Chips items={profile.additionalSkills} />} />
                    <InfoRow icon={FileText} label="Previous Work" value={profile.previousWorkDetails} />
                  </InfoSection>
                </div>
                <div className="space-y-8 lg:pl-10">
                  <InfoSection title="Contact & Account">
                    <InfoRow icon={Phone} label="Mobile Number" value={`${user.countryCode} ${user.mobileNumber}`} />
                    <InfoRow icon={Phone} label="Alternate Number" value={profile.alternateContactNumber} />
                    <InfoRow icon={Mail} label="Email Address" value={user.email} />
                    <InfoRow icon={Clock} label="Last Login" value={formatDateTime(user.lastLoginAt) ?? 'Never'} />
                    <InfoRow
                      icon={BadgeCheck}
                      label="Mobile Verified On"
                      value={formatDate(user.accountVerifiedDate)}
                    />
                  </InfoSection>
                  <InfoSection title="Address">
                    <InfoRow
                      icon={MapPin}
                      label="Address"
                      value={
                        address.length
                          ? address.map((line) => (
                              <span key={line} className="block">
                                {line}
                              </span>
                            ))
                          : null
                      }
                    />
                  </InfoSection>
                  <InfoSection title="Emergency Contact">
                    {profile.emergencyContactName ? (
                      <>
                        <InfoRow icon={UserRound} label="Name" value={profile.emergencyContactName} />
                        <InfoRow
                          icon={HeartHandshake}
                          label="Relationship"
                          value={label('EMERGENCY_CONTACT_RELATIONSHIP', profile.emergencyContactRelationship)}
                        />
                        <InfoRow icon={Phone} label="Number" value={profile.emergencyContactNumber} />
                      </>
                    ) : (
                      <InfoRow
                        icon={ShieldCheck}
                        label="Shared with NexHome"
                        value={profile.emergencyContactPermission ? null : 'Hidden until the partner allows sharing'}
                      />
                    )}
                  </InfoSection>
                  <InfoSection title="Onboarding">
                    <InfoRow
                      icon={CircleCheckBig}
                      label="Onboarding Status"
                      value={
                        <Badge className={onboardingBadgeClass(profile.onboardingStatus)}>
                          {label('PARTNER_ONBOARDING_STATUS', profile.onboardingStatus)}
                        </Badge>
                      }
                    />
                    <InfoRow
                      icon={ShieldCheck}
                      label="Verification"
                      value={
                        <Badge className={verificationBadgeClass(profile.verificationStatus)}>
                          {label('VERIFICATION_STATUS', profile.verificationStatus)}
                        </Badge>
                      }
                    />
                    <InfoRow icon={CalendarCheck} label="Approved On" value={formatDateTime(profile.approvedAt)} />
                    {profile.rejectionReason && (
                      <InfoRow icon={FileText} label="Reason" value={profile.rejectionReason} />
                    )}
                  </InfoSection>
                </div>
              </div>
            </>
          )}

          {tab === 'services' &&
            (services.length ? (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {services.map((service) => (
                  <ListRow
                    key={service.partnerServiceId}
                    icon={Wrench}
                    title={service.serviceName}
                    details={`${service.experienceYears ?? 0} year${service.experienceYears === 1 ? '' : 's'} of experience`}
                    badge={
                      <span className="flex gap-1.5">
                        {service.isPrimaryService && <Badge className="bg-[#EDF6FD] text-[var(--blue)]">Primary</Badge>}
                        <Badge
                          className={
                            service.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                          }
                        >
                          {service.isActive ? 'Active' : 'Inactive'}
                        </Badge>
                      </span>
                    }
                  />
                ))}
              </div>
            ) : (
              <EmptyList
                icon={Wrench}
                title="No services added yet"
                text="The partner chooses the services they offer during onboarding."
              />
            ))}

          {tab === 'documents' &&
            (documents.length ? (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {documents.map((document) => (
                  <ListRow
                    key={document.partnerDocumentId}
                    icon={FileText}
                    title={label('DOCUMENT_TYPE', document.documentType)}
                    details={[
                      document.documentNumber,
                      document.expiryDate && `Expires ${formatLocalDate(document.expiryDate)}`,
                      document.rejectionReason
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                    badge={
                      <Badge className={verificationBadgeClass(document.verificationStatus)}>
                        {label('VERIFICATION_STATUS', document.verificationStatus)}
                      </Badge>
                    }
                  />
                ))}
              </div>
            ) : (
              <EmptyList
                icon={FileText}
                title="No documents uploaded yet"
                text="ID, address proof and other documents show here once added."
              />
            ))}

          {tab === 'banks' &&
            (banks.length ? (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {banks.map((bank) => (
                  <ListRow
                    key={bank.partnerBankAccountId}
                    icon={Banknote}
                    title={`${bank.bankName ?? 'Bank account'} · XXXX${bank.accountNumberLast4}`}
                    details={`${bank.accountHolderName} · IFSC ${bank.ifscCode}`}
                    badge={
                      <span className="flex gap-1.5">
                        {bank.isPrimary && <Badge className="bg-[#EDF6FD] text-[var(--blue)]">Primary</Badge>}
                        <Badge className={verificationBadgeClass(bank.verificationStatus)}>
                          {label('VERIFICATION_STATUS', bank.verificationStatus)}
                        </Badge>
                      </span>
                    }
                  />
                ))}
              </div>
            ) : (
              <EmptyList
                icon={Banknote}
                title="No bank accounts added yet"
                text="Payouts go to the partner's primary bank account."
              />
            ))}

          {tab === 'availability' && (
            <div className="grid grid-cols-1 gap-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
              <InfoSection title="Weekly Availability" divided={false}>
                <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
                  <table className="w-full min-w-[34rem] text-sm">
                    <thead className="bg-[var(--light)]">
                      <tr>
                        <th className="px-3 py-2.5 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
                          Day
                        </th>
                        {slots.map((slot) => (
                          <th
                            key={slot.valueCode}
                            className="px-2 py-2.5 text-center text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]"
                          >
                            {slot.displayName}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {DAYS.map((day) => (
                        <tr key={day} className="border-t border-[var(--line)]">
                          <td className="px-3 py-2.5 font-bold text-[var(--ink)]">
                            {day[0] + day.slice(1).toLowerCase()}
                          </td>
                          {slots.map((slot) => {
                            const on = (profile.weeklyAvailability?.[day] ?? []).includes(slot.valueCode);
                            return (
                              <td key={slot.valueCode} className="px-2 py-2 text-center">
                                <span
                                  className={`inline-flex h-7 w-full max-w-[4.5rem] items-center justify-center rounded-lg text-xs font-bold ${
                                    on ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[var(--light)] text-[#C3CEDA]'
                                  }`}
                                >
                                  {on ? 'Available' : '—'}
                                </span>
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </InfoSection>
              <InfoSection title="Coverage">
                <InfoRow icon={MapPin} label="Service Areas" value={<Chips items={profile.serviceAreas} />} />
                <InfoRow
                  icon={MapPin}
                  label="Travels Up To"
                  value={profile.maxTravelKm == null ? null : `${Number(profile.maxTravelKm)} km`}
                />
                <InfoRow
                  icon={CalendarDays}
                  label="Weekends"
                  value={label('WEEKEND_AVAILABILITY', profile.weekendAvailability)}
                />
                <InfoRow
                  icon={Clock}
                  label="Right Now"
                  value={
                    approved ? label('PARTNER_AVAILABILITY_STATUS', profile.availabilityStatus) : 'Not approved yet'
                  }
                />
              </InfoSection>
            </div>
          )}

          {PENDING_TABS[tab] && <PendingTab {...PENDING_TABS[tab]} />}
        </div>
      </section>
    </div>
  );
}
