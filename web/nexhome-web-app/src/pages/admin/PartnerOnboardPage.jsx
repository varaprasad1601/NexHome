import {
  Banknote,
  BriefcaseBusiness,
  CalendarClock,
  Check,
  CircleCheckBig,
  CircleX,
  ExternalLink,
  FileText,
  History,
  IdCard,
  MapPin,
  MessageSquareWarning,
  RotateCcw,
  ShieldCheck,
  UserRound,
  Wrench
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Toast from '../../components/admin/common/Toast';
import { InfoRow } from '../../components/admin/profile/ProfileParts';
import { cardClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { onboardingBadgeClass } from '../../data/partnerOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatRupees, splitApiDateTime } from '../../utils/format';

const LOOKUPS = [
  'SKILL_CATEGORY',
  'PREFERRED_CONTACT_LANGUAGE',
  'PREFERRED_ARRANGEMENT',
  'PAYMENT_BASIS',
  'WEEKEND_AVAILABILITY',
  'EMERGENCY_CONTACT_RELATIONSHIP',
  'PARTNER_ONBOARDING_STATUS',
  'VERIFICATION_STATUS',
  'DOCUMENT_TYPE',
  'GOVERNMENT_ID_TYPE'
];
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/**
 * The three checks before approval: the profile details (sections 1-3, one Mark as Verified), every document and the
 * primary bank account (V24: worked out by the backend from those records).
 */
const CHECKS = [
  { key: 'accountVerified', label: 'Profile details', icon: UserRound },
  { key: 'documentsVerified', label: 'Documents', icon: FileText },
  { key: 'bankAccVerified', label: 'Bank account', icon: Banknote }
];

/** The audit trail's lines (GET /api/partners/profiles/audit/{id}), in the order the backend sends them. */
const AUDIT_EVENTS = {
  PROFILE_CREATED: 'Partner created',
  PROFILE_UPDATED: 'Profile updated',
  DOCUMENT_ADDED: 'Document added',
  BANK_ACCOUNT_ADDED: 'Bank details added',
  ACCOUNT_VERIFIED: 'Profile verified',
  DOCUMENT_VERIFIED: 'Document verified',
  BANK_ACCOUNT_VERIFIED: 'Bank details verified',
  APPROVED: 'Approved'
};

const REASON_DIALOGS = {
  CHANGES_REQUESTED: {
    title: 'Request Changes',
    label: 'What should the partner add or fix?',
    submit: 'Send Back to Partner',
    toast: 'sent back for changes'
  },
  REJECTED: {
    title: 'Reject Partner',
    label: 'Why is the partner rejected?',
    submit: 'Reject Partner',
    toast: 'rejected'
  }
};

const rupees = (value) => (value == null ? null : formatRupees(value));

const verifiedBadge = (verified) => (
  <span
    className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold ${
      verified ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
    }`}
  >
    {verified && <Check size={12} strokeWidth={3} />}
    {verified ? 'Verified' : 'Needs verification'}
  </span>
);

/** A review section: its number, title, optionally whether its check is verified, and its details. */
function ReviewSection({ number, icon: Icon, title, verified, children }) {
  return (
    <section className={cardClass}>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
          <Icon size={17} />
        </span>
        <h2 className="mb-0 flex-1 text-[1.0625rem] font-black text-[var(--navy)]">
          <span className="mr-1.5 text-[var(--blue)]">{number}.</span>
          {title}
        </h2>
        {verified != null && verifiedBadge(verified)}
      </div>
      <div className="divide-y divide-[var(--line)]">{children}</div>
    </section>
  );
}

const undoButtonClass =
  'flex items-center gap-1 rounded-lg border border-[var(--line)] px-3 py-1.5 text-[0.8125rem] font-bold text-[var(--muted)] transition hover:bg-[var(--light)] disabled:opacity-50';
const verifyButtonClass =
  'rounded-lg bg-[var(--green)] px-3 py-1.5 text-[0.8125rem] font-bold text-white transition hover:bg-[var(--green-2)] disabled:opacity-50';
const rejectButtonClass =
  'rounded-lg border border-[#F5C2C2] px-3 py-1.5 text-[0.8125rem] font-bold text-[#C53030] transition hover:bg-[#FDECEC] disabled:opacity-50';

/**
 * A document's or bank account's decision buttons (PARTNER_VERIFY write): Verify and Reject until verified, then Undo
 * (back to In Review), which is gone once the partner is approved.
 */
function RecordActions({ status, approved, busy, onDecide }) {
  if (status === 'VERIFIED') {
    return approved ? null : (
      <button type="button" onClick={() => onDecide('IN_REVIEW')} disabled={busy} className={undoButtonClass}>
        <RotateCcw size={13} />
        Undo
      </button>
    );
  }
  return (
    <span className="flex gap-2">
      <button type="button" onClick={() => onDecide('VERIFIED')} disabled={busy} className={verifyButtonClass}>
        Verify
      </button>
      {status !== 'REJECTED' && (
        <button type="button" onClick={() => onDecide('REJECTED')} disabled={busy} className={rejectButtonClass}>
          Reject
        </button>
      )}
    </span>
  );
}

/** The audit trail card: who created, changed, verified and approved what, and when; "Not yet" for what is still to come. */
function AuditTrail({ entries, label }) {
  return (
    <section className={cardClass}>
      <h2 className="mb-3 flex items-center gap-2 text-[1.0625rem] font-black text-[var(--navy)]">
        <History size={17} className="text-[var(--blue)]" />
        Audit Trail
      </h2>
      {entries.length === 0 ? (
        <p className="text-sm text-[var(--muted)]">No audit details.</p>
      ) : (
        <ol className="m-0 list-none space-y-3 p-0">
          {entries.map((entry, index) => {
            const when = splitApiDateTime(entry.at);
            const subject = entry.subject && (label('GOVERNMENT_ID_TYPE', entry.subject) ?? entry.subject);
            return (
              <li key={`${entry.event}-${index}`} className="flex gap-3 text-sm">
                <span
                  className={`mt-1 h-2.5 w-2.5 flex-shrink-0 rounded-full ${
                    when ? 'bg-[var(--green)]' : 'border-2 border-[#C9D4E1] bg-white'
                  }`}
                />
                <div className="min-w-0">
                  <div className="font-bold text-[var(--ink)]">
                    {AUDIT_EVENTS[entry.event] ?? entry.event}
                    {subject && <span className="font-normal text-[var(--muted)]"> · {subject}</span>}
                  </div>
                  <div className="text-[0.8125rem] text-[var(--muted)]">
                    {when ? (
                      <>
                        {when.date}, {when.time}
                        {entry.byName && (
                          <>
                            {' '}
                            by <span className="font-bold text-[var(--ink)]">{entry.byName}</span>
                          </>
                        )}
                      </>
                    ) : (
                      'Not yet'
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

/**
 * User Management > Partners > Onboard Requests > partner id (/admin/users/partners/:id/onboard): the reviewer's
 * screen. A summary with the verification progress, then the sections to check: 1. account & identity, 2. documents,
 * 3. bank account (each with Mark as Verified, PUT /api/partners/profiles/verifications/update), 4. work & services and
 * 5. availability for reference. The decision panel (PARTNER_ONBOARDING_MANAGE write, never on your own profile)
 * approves once the three checks are verified (the backend requires them too), or requests changes / rejects with a
 * reason (PUT /profiles/review); the decision returns to the request list. Other statuses show the outcome.
 */
export default function PartnerOnboardPage() {
  const { partnerProfileId } = useParams();
  const navigate = useNavigate();
  const { authRequest, can, session } = useAdminAuth();
  const { label } = useLookupLabels(LOOKUPS);
  const canVerifyFn = can('PARTNER_VERIFY', 'WRITE');
  const canApproveFn = can('PARTNER_APPROVE', 'WRITE');
  const [state, setState] = useState({
    profile: null,
    user: null,
    services: [],
    documents: [],
    banks: [],
    audit: [],
    error: ''
  });
  const [categories, setCategories] = useState([]);
  const [reloadKey, setReloadKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [reasonFor, setReasonFor] = useState(null); // CHANGES_REQUESTED or REJECTED while its dialog is open
  // The document or bank account whose rejection reason is asked for: { kind: 'document' | 'bank', item }
  const [rejecting, setRejecting] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    let cancelled = false;
    const list = (path) => authRequest(path).catch(() => []);
    authRequest(`/api/partners/profiles/${partnerProfileId}`)
      .then((profile) =>
        Promise.all([
          authRequest(`/api/users/${profile.userId}`).catch(() => null),
          list(`/api/partners/profiles/services/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/documents/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/bank-accounts/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/audit/${profile.partnerProfileId}`)
        ]).then(([user, services, documents, banks, audit]) => {
          if (!cancelled) setState({ profile, user, services, documents, banks, audit, error: '' });
        })
      )
      .catch((err) => !cancelled && setState((current) => ({ ...current, error: err.message })));
    authRequest('/api/catalog/categories?includeInactive=true')
      .then((data) => !cancelled && setCategories(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, partnerProfileId, reloadKey]);

  const { profile, user, services, documents, banks, audit, error } = state;
  const name = profile ? [profile.firstName, profile.middleName, profile.lastName].filter(Boolean).join(' ') : '';

  useEffect(() => {
    document.title = `Onboard ${name || profile?.publicId || 'Partner'} | NexHome Admin`;
  }, [name, profile]);

  const crumbs = [
    { label: 'User Management' },
    { label: 'Partners', to: '/admin/users/partners' },
    { label: 'Onboard Requests', to: '/admin/users/partners/onboarding' },
    ...(profile ? [{ label: profile.publicId }] : [])
  ];

  if (error || !profile) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} />
        {error ? (
          <div className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {error}
          </div>
        ) : (
          <div className="h-60 animate-pulse rounded-2xl border border-[var(--line)] bg-white" />
        )}
      </div>
    );
  }

  const ownProfile = profile.userId === session?.user?.userId;
  const approved = profile.onboardingStatus === 'APPROVED';
  // Verifying and deciding are admin actions (PARTNER_VERIFY, PARTNER_APPROVE; V25), never on your own profile
  const canDecide = canApproveFn && !ownProfile && profile.onboardingStatus === 'UNDER_REVIEW';
  const canVerify = canVerifyFn && !ownProfile;
  const verifiedCount = CHECKS.filter((check) => profile[check.key]).length;
  const allVerified = verifiedCount === CHECKS.length;
  const mainService = categories.find((category) => category.serviceCategoryId === profile.mainServiceCategoryId)?.name;
  const initials = [profile.firstName, profile.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
  const address = [
    [profile.addressLine1, profile.addressLine2].filter(Boolean).join(', '),
    profile.landmark && `Near ${profile.landmark}`,
    [profile.locality, profile.city, profile.state].filter(Boolean).join(', ') +
      (profile.postalCode ? ` - ${profile.postalCode}` : '')
  ].filter(Boolean);
  const documentTitle = (document) =>
    document.documentType === 'GOVERNMENT_ID'
      ? (label('GOVERNMENT_ID_TYPE', document.idType) ?? label('DOCUMENT_TYPE', document.documentType))
      : (document.documentName ?? label('DOCUMENT_TYPE', document.documentType));

  const run = async (request, message) => {
    setBusy(true);
    try {
      await request();
      setReloadKey((current) => current + 1);
      showToast(message);
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };
  const verifyProfile = (on) =>
    run(
      () =>
        authRequest('/api/partners/profiles/verifications/update', {
          method: 'PUT',
          body: { partnerProfileId: profile.partnerProfileId, accountVerified: on }
        }),
      on ? 'Profile details marked as verified.' : 'Verification undone.'
    );
  const DECISION_TOASTS = { VERIFIED: 'verified', REJECTED: 'rejected', IN_REVIEW: 'back in review' };
  // PUT /documents/verify or /bank-accounts/verify; a rejection carries its reason
  const RECORDS = {
    document: {
      path: '/api/partners/documents/verify',
      id: (item) => ({ partnerDocumentId: item.partnerDocumentId }),
      title: (item) => documentTitle(item)
    },
    bank: {
      path: '/api/partners/bank-accounts/verify',
      id: (item) => ({ partnerBankAccountId: item.partnerBankAccountId }),
      title: () => 'Bank account'
    }
  };
  const decideRecord = (kind, item, verificationStatus, rejectionReason = null) =>
    authRequest(RECORDS[kind].path, {
      method: 'PUT',
      body: { ...RECORDS[kind].id(item), verificationStatus, rejectionReason }
    });
  const onRecordDecision = (kind, item) => (verificationStatus) =>
    verificationStatus === 'REJECTED'
      ? setRejecting({ kind, item })
      : run(
          () => decideRecord(kind, item, verificationStatus),
          `${RECORDS[kind].title(item)} ${DECISION_TOASTS[verificationStatus]}.`
        );
  const rejectRecord = async (reason) => {
    const { kind, item } = rejecting;
    await decideRecord(kind, item, 'REJECTED', reason);
    setReloadKey((current) => current + 1);
    showToast(`${RECORDS[kind].title(item)} rejected.`);
  };
  const decide = async (onboardingStatus, rejectionReason = null) => {
    await authRequest('/api/partners/profiles/review', {
      method: 'PUT',
      body: { partnerProfileId: profile.partnerProfileId, onboardingStatus, rejectionReason }
    });
    const who = name || profile.publicId;
    const message =
      onboardingStatus === 'APPROVED'
        ? `${who} is approved and onboarded.`
        : `${who} was ${REASON_DIALOGS[onboardingStatus].toast}.`;
    navigate('/admin/users/partners/onboarding', { state: { toast: { message, tone: 'success' } } });
  };
  const approve = async () => {
    setBusy(true);
    try {
      await decide('APPROVED');
    } catch (err) {
      showToast(err.message, 'error');
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={crumbs}
        actions={
          <Link
            to={`/admin/users/partners/${profile.partnerProfileId}`}
            className="flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm font-bold text-[var(--ink)] no-underline hover:bg-[var(--light)]"
          >
            <ExternalLink size={15} />
            View Profile
          </Link>
        }
      />

      {/* Summary with the verification progress */}
      <section className={`mb-5 flex flex-wrap items-center gap-6 ${cardClass}`}>
        <span className="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-full bg-[var(--blue)] text-xl font-black text-white">
          {initials || <UserRound size={26} />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="mb-0 text-2xl font-black text-[var(--navy)]">{name || 'Name not added yet'}</h1>
            <span
              className={`rounded-full px-3 py-1 text-xs font-bold ${onboardingBadgeClass(profile.onboardingStatus)}`}
            >
              {label('PARTNER_ONBOARDING_STATUS', profile.onboardingStatus)}
            </span>
          </div>
          <div className="mt-1.5 flex flex-wrap gap-x-5 gap-y-1 text-sm text-[var(--muted)]">
            <span className="flex items-center gap-1.5">
              <IdCard size={15} className="text-[var(--blue)]" />
              <span className="font-bold text-[var(--ink)]">{profile.publicId}</span>
            </span>
            {mainService && (
              <span className="flex items-center gap-1.5">
                <Wrench size={15} className="text-[var(--blue)]" />
                {mainService}
              </span>
            )}
            {profile.city && (
              <span className="flex items-center gap-1.5">
                <MapPin size={15} className="text-[var(--blue)]" />
                {profile.city}
              </span>
            )}
          </div>
        </div>
        <div className="w-full sm:w-72">
          <div className="mb-1.5 flex justify-between text-sm">
            <span className="font-bold text-[var(--ink)]">Verification</span>
            <span className={`font-black ${allVerified ? 'text-[var(--green-2)]' : 'text-[var(--muted)]'}`}>
              {verifiedCount} of {CHECKS.length}
            </span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-[var(--light)]">
            <div
              className={`h-full rounded-full transition-all ${allVerified ? 'bg-[var(--green)]' : 'bg-[var(--blue)]'}`}
              style={{ width: `${(verifiedCount / CHECKS.length) * 100}%` }}
            />
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 items-start gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-5">
          <ReviewSection number={1} icon={UserRound} title="Account & Identity">
            <div className="grid grid-cols-1 gap-x-10 md:grid-cols-2">
              <InfoRow icon={UserRound} label="Full Name" value={name} />
              <InfoRow icon={IdCard} label="Aadhaar Number" value={profile.aadhaarNumber} />
              <InfoRow
                icon={CircleCheckBig}
                label="Age 18 or Above"
                value={profile.isAdult == null ? null : profile.isAdult ? 'Yes' : 'No'}
              />
              <InfoRow
                icon={UserRound}
                label="Preferred Language"
                value={label('PREFERRED_CONTACT_LANGUAGE', profile.preferredContactLanguage)}
              />
              <InfoRow
                icon={UserRound}
                label="Mobile · Alternate"
                value={[user ? `${user.countryCode} ${user.mobileNumber}` : null, profile.alternateContactNumber]
                  .filter(Boolean)
                  .join('  ·  ')}
              />
              <InfoRow icon={UserRound} label="Email" value={user?.email} />
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
              <InfoRow
                icon={UserRound}
                label="Emergency Contact"
                value={
                  profile.emergencyContactName
                    ? `${profile.emergencyContactName} (${label('EMERGENCY_CONTACT_RELATIONSHIP', profile.emergencyContactRelationship)}) · ${profile.emergencyContactNumber ?? ''}`
                    : null
                }
              />
            </div>
          </ReviewSection>

          <ReviewSection number={2} icon={BriefcaseBusiness} title="Work & Services">
            <div className="grid grid-cols-1 gap-x-10 md:grid-cols-2">
              <InfoRow
                icon={BriefcaseBusiness}
                label="Partner Skill Category"
                value={label('SKILL_CATEGORY', profile.skillCategory)}
              />
              <InfoRow icon={Wrench} label="Main Service" value={mainService} />
              <InfoRow
                icon={CalendarClock}
                label="Experience"
                value={profile.yearsExperience == null ? null : `${profile.yearsExperience} years`}
              />
              <InfoRow
                icon={BriefcaseBusiness}
                label="Arrangement · Payment"
                value={[
                  label('PREFERRED_ARRANGEMENT', profile.preferredArrangement),
                  label('PAYMENT_BASIS', profile.paymentBasis)
                ]
                  .filter(Boolean)
                  .join(' · ')}
              />
              <InfoRow
                icon={Banknote}
                label="Expected · Visit · Minimum Job"
                value={[
                  rupees(profile.expectedEarningsInr),
                  rupees(profile.visitChargeInr),
                  rupees(profile.minimumJobPaymentInr)
                ]
                  .map((value) => value ?? '—')
                  .join('  ·  ')}
              />
              <InfoRow
                icon={Wrench}
                label="Services Offered"
                value={
                  services.length
                    ? services
                        .map((service) => `${service.serviceName}${service.isPrimaryService ? ' (primary)' : ''}`)
                        .join(', ')
                    : null
                }
              />
            </div>
          </ReviewSection>

          <ReviewSection number={3} icon={CalendarClock} title="Availability">
            <div className="grid grid-cols-1 gap-x-10 md:grid-cols-2">
              <InfoRow icon={MapPin} label="Service Areas" value={(profile.serviceAreas ?? []).join(', ') || null} />
              <InfoRow
                icon={MapPin}
                label="Travels Up To"
                value={profile.maxTravelKm == null ? null : `${Number(profile.maxTravelKm)} km`}
              />
              <InfoRow
                icon={CalendarClock}
                label="Available On"
                value={
                  DAYS.filter((day) => (profile.weeklyAvailability?.[day] ?? []).length)
                    .map((day) => day[0] + day.slice(1, 3).toLowerCase())
                    .join(', ') || null
                }
              />
              <InfoRow
                icon={CalendarClock}
                label="Weekends"
                value={label('WEEKEND_AVAILABILITY', profile.weekendAvailability)}
              />
            </div>
          </ReviewSection>

          {/* One verification for sections 1-3 */}
          <section
            className={`flex flex-wrap items-center gap-3 rounded-2xl border px-5 py-4 ${
              profile.accountVerified ? 'border-[#BFE5CF] bg-[#F3FBF6]' : 'border-[#F2D58A] bg-[#FFFBEB]'
            }`}
          >
            <ShieldCheck size={20} className={profile.accountVerified ? 'text-[var(--green-2)]' : 'text-[#8A6508]'} />
            <div className="min-w-0 flex-1">
              <div className="font-black text-[var(--navy)]">Profile Details</div>
              <div className="text-sm text-[var(--muted)]">
                Account & identity, work & services and availability (1-3)
              </div>
            </div>
            {verifiedBadge(profile.accountVerified)}
            {canVerify &&
              (profile.accountVerified ? (
                !approved && (
                  <button
                    type="button"
                    onClick={() => verifyProfile(false)}
                    disabled={busy}
                    className={undoButtonClass}
                  >
                    <RotateCcw size={13} />
                    Undo
                  </button>
                )
              ) : (
                <button type="button" onClick={() => verifyProfile(true)} disabled={busy} className={verifyButtonClass}>
                  Mark as Verified
                </button>
              ))}
          </section>

          <ReviewSection number={4} icon={FileText} title="Documents" verified={profile.documentsVerified}>
            {documents.length === 0 ? (
              <p className="py-3 text-sm text-[var(--muted)]">No documents added yet.</p>
            ) : (
              documents.map((document) => (
                <div key={document.partnerDocumentId} className="flex flex-wrap items-center gap-3 py-3">
                  <FileText size={18} className="text-[var(--blue)]" />
                  <div className="min-w-0 flex-1">
                    <div className="font-bold text-[var(--ink)]">{documentTitle(document)}</div>
                    <div className="text-sm text-[var(--muted)]">
                      {[
                        label('DOCUMENT_TYPE', document.documentType),
                        document.documentNumber,
                        document.expiryDate && `Expires ${document.expiryDate}`
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </div>
                    {document.verificationStatus === 'REJECTED' && document.rejectionReason && (
                      <div className="text-sm text-[#C53030]">{document.rejectionReason}</div>
                    )}
                  </div>
                  {canVerify && (
                    <RecordActions
                      status={document.verificationStatus}
                      approved={approved}
                      busy={busy}
                      onDecide={onRecordDecision('document', document)}
                    />
                  )}
                </div>
              ))
            )}
          </ReviewSection>

          <ReviewSection number={5} icon={Banknote} title="Bank Account" verified={profile.bankAccVerified}>
            {banks.length === 0 ? (
              <p className="py-3 text-sm text-[var(--muted)]">No bank account added yet.</p>
            ) : (
              banks.map((bank) => (
                <div key={bank.partnerBankAccountId} className="flex flex-wrap items-center gap-3 py-3">
                  <Banknote size={18} className="text-[var(--blue)]" />
                  <div className="min-w-0 flex-1">
                    <div className="font-bold text-[var(--ink)]">
                      {bank.bankName ?? 'Bank account'} · XXXX{bank.accountNumberLast4}
                    </div>
                    <div className="text-sm text-[var(--muted)]">
                      {bank.accountHolderName} · IFSC {bank.ifscCode}
                      {bank.branchName ? ` · ${bank.branchName}` : ''}
                      {bank.isPrimary ? ' · Primary' : ''}
                    </div>
                    {bank.verificationStatus === 'REJECTED' && bank.rejectionReason && (
                      <div className="text-sm text-[#C53030]">{bank.rejectionReason}</div>
                    )}
                  </div>
                  {canVerify && (
                    <RecordActions
                      status={bank.verificationStatus}
                      approved={approved}
                      busy={busy}
                      onDecide={onRecordDecision('bank', bank)}
                    />
                  )}
                </div>
              ))
            )}
          </ReviewSection>
        </div>

        {/* The decision */}
        <div className="space-y-5">
          <aside className={cardClass}>
            <h2 className="mb-1 text-[1.0625rem] font-black text-[var(--navy)]">Decision</h2>
            <p className="mb-4 text-sm text-[var(--muted)]">
              {profile.onboardingStatus === 'UNDER_REVIEW'
                ? 'Verify the profile details, every document and the primary bank account, then approve the partner or send the profile back.'
                : 'This profile is not waiting for a decision.'}
            </p>
            <ul className="mb-5 list-none space-y-2.5 p-0">
              {CHECKS.map((check) => {
                const Icon = check.icon;
                const done = profile[check.key];
                return (
                  <li key={check.key} className="flex items-center gap-3 text-sm">
                    <span
                      className={`flex h-7 w-7 items-center justify-center rounded-full ${
                        done ? 'bg-[var(--green)] text-white' : 'bg-[var(--light)] text-[#A8B6C6]'
                      }`}
                    >
                      {done ? <Check size={14} strokeWidth={3} /> : <Icon size={14} />}
                    </span>
                    <span className={done ? 'font-bold text-[var(--ink)]' : 'text-[var(--muted)]'}>{check.label}</span>
                  </li>
                );
              })}
            </ul>

            {canDecide ? (
              <div className="space-y-2.5">
                <button
                  type="button"
                  onClick={approve}
                  disabled={!allVerified || busy}
                  title={allVerified ? undefined : 'Verify all three sections first'}
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-[var(--green)] px-4 py-3 text-sm font-black text-white transition hover:bg-[var(--green-2)] disabled:cursor-not-allowed disabled:opacity-45"
                >
                  <ShieldCheck size={17} />
                  Approve & Onboard
                </button>
                {!allVerified && (
                  <p className="text-center text-xs text-[var(--muted)]">
                    Approval unlocks once all three are verified.
                  </p>
                )}
                <button
                  type="button"
                  onClick={() => setReasonFor('CHANGES_REQUESTED')}
                  className="flex w-full items-center justify-center gap-2 rounded-xl border border-[#F2D58A] bg-[#FFFBEB] px-4 py-2.5 text-sm font-bold text-[#8A6508] hover:bg-[#FFF7DE]"
                >
                  <MessageSquareWarning size={16} />
                  Request Changes
                </button>
                <button
                  type="button"
                  onClick={() => setReasonFor('REJECTED')}
                  className="flex w-full items-center justify-center gap-2 rounded-xl border border-[#F5C2C2] bg-white px-4 py-2.5 text-sm font-bold text-[#C53030] hover:bg-[#FDECEC]"
                >
                  <CircleX size={16} />
                  Reject
                </button>
              </div>
            ) : (
              <div className="rounded-xl bg-[var(--light)] px-4 py-3 text-sm">
                <div className="font-bold text-[var(--ink)]">
                  {label('PARTNER_ONBOARDING_STATUS', profile.onboardingStatus)}
                </div>
                {profile.rejectionReason && <div className="mt-1 text-[var(--muted)]">{profile.rejectionReason}</div>}
                {ownProfile && <div className="mt-1 text-[var(--muted)]">You cannot review your own profile.</div>}
                {!ownProfile && !canApproveFn && profile.onboardingStatus === 'UNDER_REVIEW' && (
                  <div className="mt-1 text-[var(--muted)]">Only admins can approve partners.</div>
                )}
              </div>
            )}
          </aside>
          <AuditTrail entries={audit} label={label} />
        </div>
      </div>

      {rejecting && (
        <FormModal
          title={rejecting.kind === 'bank' ? 'Reject Bank Account' : 'Reject Document'}
          fields={[
            {
              key: 'reason',
              label: `Why is ${RECORDS[rejecting.kind].title(rejecting.item).toLowerCase()} rejected?`,
              type: 'textarea',
              required: true,
              maxLength: 500,
              rows: 3
            }
          ]}
          initial={{ reason: '' }}
          submitLabel="Reject"
          onSubmit={(formValues) => rejectRecord(formValues.reason.trim())}
          onClose={() => setRejecting(null)}
        />
      )}
      {reasonFor && (
        <FormModal
          title={REASON_DIALOGS[reasonFor].title}
          fields={[
            {
              key: 'reason',
              label: REASON_DIALOGS[reasonFor].label,
              type: 'textarea',
              required: true,
              maxLength: 2000,
              rows: 4
            }
          ]}
          initial={{ reason: '' }}
          submitLabel={REASON_DIALOGS[reasonFor].submit}
          onSubmit={(formValues) => decide(reasonFor, formValues.reason.trim())}
          onClose={() => setReasonFor(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
