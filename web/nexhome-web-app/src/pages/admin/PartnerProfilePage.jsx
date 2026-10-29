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
  History,
  IdCard,
  Languages,
  Mail,
  MapPin,
  Pencil,
  Phone,
  Plus,
  Trash2,
  Send,
  ShieldCheck,
  Star,
  ThumbsDown,
  ThumbsUp,
  Timer,
  UserCheck,
  UserRound,
  Wallet,
  Wrench
} from 'lucide-react';
import { Fragment, useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Toast from '../../components/admin/common/Toast';
import ConfirmDialog from '../../components/admin/system/ConfirmDialog';
import LunchBreak from '../../components/admin/partner/LunchBreak';
import SlotHeading from '../../components/admin/partner/SlotHeading';
import WeeklyAvailabilityModal from '../../components/admin/partner/WeeklyAvailabilityModal';
import ActivityLog from '../../components/admin/profile/ActivityLog';
import FilteredBookings from '../../components/admin/booking/FilteredBookings';
import PartnerEarnings from '../../components/admin/payment/PartnerEarnings';
import PartnerSchedule from '../../components/admin/partner/PartnerSchedule';
import CompletionAvatar from '../../components/admin/profile/CompletionAvatar';
import StatusMenu from '../../components/admin/profile/StatusMenu';
import { InfoRow, InfoSection, PendingTab, StatTile } from '../../components/admin/profile/ProfileParts';
import ProfileReviews from '../../components/admin/review/ProfileReviews';
import { profileTabClass } from '../../components/admin/profile/profileStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { activitySubject, PARTNER_ACTIVITY_SECTIONS } from '../../data/activityEvents';
import { bankDialog, documentDialog, profileDialog, serviceDialog } from '../../data/partnerForms';
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
  'DOCUMENT_TYPE',
  'GOVERNMENT_ID_TYPE'
];
/** Statuses in which a profile can be submitted for review (the backend's EDITABLE_BY_PARTNER). */
const SUBMITTABLE = ['NOT_STARTED', 'DRAFT', 'CHANGES_REQUESTED'];
/** The Documents tab's sections; documents of any other (older) kind show in a third one only when there are some. */
const DOCUMENT_SECTIONS = [
  {
    title: 'Government ID Proof',
    kind: 'GOVERNMENT_ID',
    addLabel: 'Add Government ID',
    icon: IdCard,
    matches: (document) => document.documentType === 'GOVERNMENT_ID',
    empty: 'No government ID added yet. One is needed to complete the profile.'
  },
  {
    title: 'Skill Certificates',
    kind: 'CERTIFICATION',
    addLabel: 'Add Certificate',
    icon: BadgeCheck,
    matches: (document) => document.documentType === 'CERTIFICATION',
    empty: 'No skill certificates added yet.'
  },
  {
    title: 'Other Documents',
    other: true,
    icon: FileText,
    matches: (document) => !['GOVERNMENT_ID', 'CERTIFICATION'].includes(document.documentType),
    empty: ''
  }
];
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** The details a partner's onboarding needs (the backend's submit checks), in the order the completion lists them. */
const PROFILE_CHECKS = [
  { label: 'Name', done: (profile) => Boolean(profile.firstName && profile.lastName) },
  // The mobile number is the partner's login (confirmed by OTP when they sign in), so it always counts as done
  { label: 'Verified mobile', done: () => true },
  { label: 'Aadhaar number', done: (profile) => Boolean(profile.aadhaarNumber) },
  { label: 'Address', done: (profile) => Boolean(profile.addressLine1 && profile.city && profile.postalCode) },
  {
    label: 'Emergency contact',
    done: (profile) =>
      Boolean(profile.emergencyContactName && profile.emergencyContactRelationship && profile.emergencyContactNumber)
  },
  // Every field the Work Details dialog requires
  {
    label: 'Work details',
    done: (profile) =>
      Boolean(
        profile.skillCategory && profile.mainServiceCategoryId && profile.preferredArrangement && profile.paymentBasis
      ) &&
      [
        profile.yearsExperience,
        profile.expectedEarningsInr,
        profile.visitChargeInr,
        profile.minimumJobPaymentInr
      ].every((value) => value != null)
  },
  {
    label: 'Main service added',
    done: (profile, user, documents, banks, services) => services.some((service) => service.isPrimaryService)
  },
  { label: 'Weekly availability', done: (profile) => Object.keys(profile.weeklyAvailability ?? {}).length > 0 },
  // The Coverage card: where the partner works, how far they travel and their weekends
  {
    label: 'Service coverage',
    done: (profile) =>
      (profile.serviceAreas ?? []).length > 0 && profile.maxTravelKm != null && Boolean(profile.weekendAvailability)
  },
  // Submitted, not yet verified: one government ID is enough
  {
    label: 'Government ID added',
    done: (profile, user, documents) => documents.some((document) => document.documentType === 'GOVERNMENT_ID')
  },
  { label: 'Bank account added', done: (profile, user, documents, banks) => banks.length > 0 }
];

/**
 * The verification checks: the profile details (who verified it: accountVerifiedBy), every document and the primary
 * bank account (from those records, V24).
 */
const VERIFICATION_CHECKS = [
  { key: 'accountVerified', event: 'ACCOUNT_VERIFIED', label: 'Account', icon: UserRound },
  { key: 'documentsVerified', event: 'DOCUMENT_VERIFIED', label: 'Documents', icon: FileText },
  { key: 'bankAccVerified', event: 'BANK_ACCOUNT_VERIFIED', label: 'Bank Account', icon: Banknote }
];

/** A backend date ("Thu, 29 Oct 2026 06:44:54 IST") as milliseconds, for comparing audit lines. */
const apiTime = (value) => Date.parse(String(value).replace(' IST', ' GMT+0530')) || 0;

/** An optional green tag (Verified), then who did it and their role, from an audit trail line. */
function DoneBy({ tag, entry }) {
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
      {tag && <Badge className="bg-[#E9F6EF] text-[var(--green-2)]">{tag}</Badge>}
      {entry?.byName && (
        <span className="text-sm">
          <span className="font-bold text-[var(--ink)]">{entry.byName}</span>
          {entry.byRole && <span className="text-[var(--muted)]"> · {entry.byRole}</span>}
        </span>
      )}
    </span>
  );
}

/**
 * The statistics, from GET /api/bookings/partner-stats/{id} (BOOKING_MANAGE read): jobs the partner holds, then by
 * stage, counted as the customer's are (assigned and not answered; accepted, which includes PARTNER_EN_ROUTE, shown as
 * "Partner Accepted"; in progress, paused included), completed, cancelled, declined (rejected), and earnings (payouts plus cash collected; PAYOUT_MANAGE read, else a dash). The rating is in the
 * header, beside the joined date.
 */
const STAT_CARDS = [
  { key: 'totalJobs', label: 'Total Jobs', icon: CalendarCheck, tone: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'assigned', label: 'Assigned', icon: UserCheck, tone: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { key: 'accepted', label: 'Accepted', icon: ThumbsUp, tone: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'inProgress', label: 'In Progress', icon: Timer, tone: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'completed', label: 'Completed Jobs', icon: CircleCheckBig, tone: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'cancelled', label: 'Cancelled Jobs', icon: CalendarX, tone: 'bg-[#FDECEC] text-[#E53E3E]' },
  { key: 'rejected', label: 'Rejected', icon: ThumbsDown, tone: 'bg-[#FDECEC] text-[#E53E3E]' },
  {
    key: 'totalEarnings',
    label: 'Total Earnings',
    icon: Wallet,
    tone: 'bg-[#E8EEF6] text-[var(--navy)]',
    money: true
  }
];

/** Statuses a partner's job never has (before a partner is assigned): not in its filter. */
const NOT_JOB_STATUSES = ['PENDING', 'CONFIRMED'];

/** The tabs whose data comes with later modules: an empty state until then. */
const PENDING_TABS = {
  // Live (Module 6) for BOOKING_MANAGE readers: the bookings assigned to the partner
  jobs: {
    label: 'Jobs',
    icon: CalendarCheck,
    text: "The partner's jobs show here for staff who can read bookings."
  },
  // Live (Module 7) for PAYOUT_MANAGE readers: the partner's payouts
  earnings: {
    label: 'Earnings',
    icon: Wallet,
    text: "The partner's payouts show here for staff who can read payouts."
  },
  // Live (Module 9) for REVIEW_MANAGE readers: the reviews of the partner's jobs
  reviews: {
    label: 'Reviews',
    icon: Star,
    text: "Reviews of the partner's jobs show here for staff who can read reviews."
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

/** A list item's Edit: a pencil icon (the word is its label for screen readers and the tooltip). */
function EditButton({ onClick, label = 'Edit' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--blue)] hover:bg-[#EDF6FD]"
    >
      <Pencil size={15} />
    </button>
  );
}

/** A list tab's heading: the count and, for editors, the Add button. */
function ListHeader({ title, count, addLabel, onAdd }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h3 className="mb-0 flex items-center gap-2 text-[1.0625rem] font-black text-[var(--navy)]">
        {title}
        <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] font-bold text-[var(--muted)]">
          {count}
        </span>
      </h3>
      {onAdd && (
        <button
          type="button"
          onClick={onAdd}
          className="flex items-center gap-2 rounded-xl bg-[var(--blue)] px-4 py-2 text-sm font-bold text-white hover:bg-[var(--navy)]"
        >
          <Plus size={16} strokeWidth={2.6} />
          {addLabel}
        </button>
      )}
    </div>
  );
}

/** A row of a list tab (services, documents, bank accounts): an icon tile, the main line, details and a badge. */
function ListRow({ icon: Icon, title, details, badge, onEdit, onDelete }) {
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
      {onEdit && <EditButton onClick={onEdit} />}
      {onDelete && (
        <button
          type="button"
          onClick={onDelete}
          aria-label={`Remove ${title}`}
          title="Remove"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-[#E53E3E] hover:bg-[#FDECEC]"
        >
          <Trash2 size={15} />
        </button>
      )}
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
 * bank accounts, /api/users/{userId}, the catalogue categories (main service name) and the lookups. With
 * PARTNER_ONBOARDING_MANAGE write every card has an Edit (data/partnerForms dialogs; the onboarding card reviews the
 * profile when a decision is possible, the weekly grid has its own dialog) and Services, Documents and Bank Accounts
 * have Add buttons and an Edit per item; the email needs PARTNER_MANAGE write. Verifying is not here yet.
 */
export default function PartnerProfilePage() {
  const { partnerProfileId } = useParams();
  const { authRequest, can, session } = useAdminAuth();
  const { values, label } = useLookupLabels(LOOKUPS);
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
  const [tab, setTab] = useState('overview');
  const canManage = can('PARTNER_ONBOARDING_MANAGE', 'WRITE');
  const canEditEmail = can('PARTNER_MANAGE', 'WRITE');
  const canManageDelete = can('PARTNER_ONBOARDING_MANAGE', 'DELETE');
  // The service being removed, while its confirmation is open
  const [removing, setRemoving] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  // The open dialog: { kind, item } (kind: personal, work, contact, address, emergency, review, coverage, weekly,
  // service, document, bank; item: the service, document or bank account edited, or null to add one)
  const [dialog, setDialog] = useState(null);
  const [catalogue, setCatalogue] = useState([]);
  const [toast, setToast] = useState(null);
  const [jobStats, setJobStats] = useState(null);

  // The Reviews tab's count (one row asked for, the total read), for REVIEW_MANAGE readers
  const canReadReviews = can('REVIEW_MANAGE', 'READ');
  const [reviewCount, setReviewCount] = useState(null);
  useEffect(() => {
    if (!canReadReviews) return undefined;
    let cancelled = false;
    authRequest(`/api/reviews?size=1&partnerProfileId=${partnerProfileId}`)
      .then((page) => !cancelled && setReviewCount(page.totalElements))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, partnerProfileId, canReadReviews]);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    let cancelled = false;
    const list = (path) => authRequest(path).catch(() => []);
    authRequest(`/api/partners/profiles/${partnerProfileId}`)
      .then((profile) =>
        Promise.all([
          authRequest(`/api/users/${profile.userId}`),
          list(`/api/partners/profiles/services/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/documents/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/bank-accounts/${profile.partnerProfileId}`),
          list(`/api/partners/profiles/audit/${profile.partnerProfileId}`)
        ]).then(([user, services, documents, banks, audit]) => {
          if (!cancelled) setState({ profile, user, services, documents, banks, audit, error: '' });
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
  }, [authRequest, partnerProfileId, reloadKey]);

  // Jobs and earnings, for those who can read bookings
  const canReadBookings = can('BOOKING_MANAGE', 'READ');
  useEffect(() => {
    if (!canReadBookings) return undefined;
    let cancelled = false;
    authRequest(`/api/bookings/partner-stats/${partnerProfileId}`)
      .then((stats) => !cancelled && setJobStats(stats))
      .catch(() => !cancelled && setJobStats(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, partnerProfileId, canReadBookings, reloadKey]);

  const { profile, user, services, documents, banks, error } = state;

  // The services a partner can add, when the Services dialog first opens
  useEffect(() => {
    if (dialog?.kind !== 'service' || catalogue.length) return undefined;
    let cancelled = false;
    authRequest('/api/catalog/services?size=100')
      .then((page) => !cancelled && setCatalogue(page.content))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, dialog, catalogue.length]);
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

  const ctx = { values, categories, canEditEmail, user };
  const open =
    (kind, item = null) =>
    () =>
      setDialog({ kind, item });
  const PROFILE_KINDS = ['personal', 'work', 'contact', 'address', 'emergency', 'coverage'];
  const config = !dialog
    ? null
    : PROFILE_KINDS.includes(dialog.kind)
      ? profileDialog(dialog.kind, profile, ctx)
      : dialog.kind === 'service'
        ? serviceDialog(dialog.item, catalogue, services)
        : dialog.kind === 'document'
          ? documentDialog(dialog.item, values, dialog.documentKind ?? dialog.item?.documentType)
          : dialog.kind === 'bank'
            ? bankDialog(dialog.item)
            : null;
  // Until approval PARTNER_ONBOARDING_MANAGE (sub-admins too) may change the partner; afterwards only those with
  // PARTNER_APPROVED_EDIT (admins, V25), as the backend checks
  const lockedApproved = approved && !can('PARTNER_APPROVED_EDIT', 'WRITE');
  const canEdit = canManage && !lockedApproved;
  const canDelete = canManageDelete && !lockedApproved;
  const completion = PROFILE_CHECKS.map((check) => ({
    label: check.label,
    done: check.done(profile, user, documents, banks, services)
  }));
  const completionPercent = Math.round((completion.filter((check) => check.done).length / completion.length) * 100);
  const complete = completionPercent === 100;
  // Submit for Review is offered to editors of a profile not yet submitted (the status menu then gives way to it)
  const showSubmit = canEdit && SUBMITTABLE.includes(profile.onboardingStatus);
  const submitForReview = async () => {
    setSubmitting(true);
    try {
      await authRequest('/api/partners/profiles/submit', {
        method: 'PUT',
        body: { partnerProfileId: profile.partnerProfileId }
      });
      saved('Submitted for review: the profile is now Under Review.');
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setSubmitting(false);
    }
  };
  // Staff never verify or review their own partner profile
  // Who verified each check and who approved: the latest such line of the audit trail (name and role)
  const latestBy = (event) =>
    (state.audit ?? [])
      .filter((entry) => entry.event === event && entry.at)
      .reduce((latest, entry) => (latest && apiTime(latest.at) >= apiTime(entry.at) ? latest : entry), null);

  const saved = (message) => {
    setDialog(null);
    showToast(message);
    setReloadKey((key) => key + 1);
  };
  const updateProfile = (body) =>
    authRequest('/api/partners/profiles/update', {
      method: 'PUT',
      body: { partnerProfileId: profile.partnerProfileId, ...body }
    });
  const saveDialog = async (formValues) => {
    const { kind, item } = dialog;
    const body = config.body(formValues);
    if (PROFILE_KINDS.includes(kind)) {
      await updateProfile(body);
      if (kind === 'contact' && canEditEmail && formValues.email.trim() !== (user.email ?? '')) {
        await authRequest('/api/users/update', {
          method: 'PUT',
          body: { userId: user.userId, email: formValues.email.trim() }
        });
      }
      saved(`${config.title.replace('Edit ', '')} saved.`);
    } else if (kind === 'service') {
      await (item
        ? authRequest('/api/partners/services/update', {
            method: 'PUT',
            body: { partnerServiceId: item.partnerServiceId, ...body }
          })
        : authRequest('/api/partners/services/add', {
            method: 'POST',
            body: { partnerProfileId: profile.partnerProfileId, ...body }
          }));
      saved(item ? 'Service updated.' : 'Service added.');
    } else if (kind === 'document') {
      await (item
        ? authRequest('/api/partners/documents/update', {
            method: 'PUT',
            body: { partnerDocumentId: item.partnerDocumentId, ...body }
          })
        : authRequest('/api/partners/documents/add', {
            method: 'POST',
            body: { partnerProfileId: profile.partnerProfileId, ...body }
          }));
      saved(item ? 'Document updated; it goes back for verification.' : 'Document added.');
    } else if (kind === 'bank') {
      await (item
        ? authRequest('/api/partners/bank-accounts/update', {
            method: 'PUT',
            body: { partnerBankAccountId: item.partnerBankAccountId, ...body }
          })
        : authRequest('/api/partners/bank-accounts/add', {
            method: 'POST',
            body: { partnerProfileId: profile.partnerProfileId, ...body }
          }));
      saved(item ? 'Bank account updated.' : 'Bank account added.');
    }
  };
  const documentTitle = (document) =>
    document.documentType === 'GOVERNMENT_ID'
      ? (label('GOVERNMENT_ID_TYPE', document.idType) ?? label('DOCUMENT_TYPE', document.documentType))
      : (document.documentName ?? label('DOCUMENT_TYPE', document.documentType));

  const tabs = [
    { key: 'overview', label: 'Overview', icon: UserRound },
    { key: 'services', label: 'Services', icon: Wrench, count: services.length },
    { key: 'documents', label: 'Documents', icon: FileText, count: documents.length },
    { key: 'banks', label: 'Bank Accounts', icon: Banknote, count: banks.length },
    { key: 'availability', label: 'Availability', icon: CalendarClock },
    // The Jobs count: the bookings the partner holds (the job figures' total), for BOOKING_MANAGE readers
    ...Object.entries(PENDING_TABS).map(([key, pending]) => ({
      key,
      ...pending,
      ...(key === 'jobs' && jobStats ? { count: jobStats.totalJobs } : {}),
      ...(key === 'reviews' && reviewCount != null ? { count: reviewCount } : {})
    })),
    { key: 'activity', label: 'Activity Logs', icon: History }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-center gap-6 px-6 pb-6 pt-7 sm:px-8">
          <CompletionAvatar
            checks={completion}
            photoUrl={user.profilePhotoUrl}
            name={name}
            initials={initials}
            canEdit={can('PARTNER_MANAGE', 'WRITE')}
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1
                className={`mb-0 text-2xl font-black leading-tight ${name ? 'text-[var(--navy)]' : 'text-[var(--muted)]'}`}
              >
                {name || 'Name not added yet'}
              </h1>
              {/* An approved partner gets a green shield in place of the onboarding tag */}
              {approved ? (
                <span
                  title="Approved partner"
                  aria-label="Approved partner"
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-[#E9F6EF] text-[var(--green-2)]"
                >
                  <ShieldCheck size={19} strokeWidth={2.4} />
                </span>
              ) : (
                <span
                  className={`rounded-full px-3 py-1 text-xs font-bold ${onboardingBadgeClass(profile.onboardingStatus)}`}
                >
                  {label('PARTNER_ONBOARDING_STATUS', profile.onboardingStatus)}
                </span>
              )}
              <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadgeClass(user.accountStatus)}`}>
                {accountStatusLabel(user.accountStatus)}
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
            {/* The partner's rating (reviews come with Module 9: until then "No reviews yet") */}
            <div className="mt-1.5 flex items-center gap-2 text-sm text-[var(--muted)]">
              <Star size={15} className="fill-[#F2C94C] text-[#D9A21B]" />
              Rating{' '}
              {jobStats?.averageRating != null ? (
                <span className="font-bold text-[var(--ink)]">{Number(jobStats.averageRating).toFixed(1)} / 5</span>
              ) : (
                <span className="font-bold text-[var(--ink)]">No reviews yet</span>
              )}
            </div>
          </div>
          {/* Onboarding: submit a complete profile for review, then review it on the onboarding screen */}
          {showSubmit && (
            <span className="flex flex-col items-end gap-1 self-start">
              <button
                type="button"
                onClick={submitForReview}
                disabled={!complete || submitting}
                title={complete ? undefined : 'Complete the profile first'}
                className="flex items-center gap-2 rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_16px_rgba(11,67,139,0.25)] hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-45 disabled:shadow-none"
              >
                <Send size={16} />
                {submitting ? 'Submitting…' : 'Submit for Review'}
              </button>
              {!complete && <span className="text-xs text-[var(--muted)]">Profile {completionPercent}% complete</span>}
            </span>
          )}
          {can('PARTNER_ONBOARDING_MANAGE', 'READ') && profile.onboardingStatus === 'UNDER_REVIEW' && (
            <Link
              to={`/admin/users/partners/${profile.partnerProfileId}/onboard`}
              className="flex items-center gap-2 self-start rounded-xl bg-[var(--green)] px-5 py-2.5 text-sm font-bold text-white no-underline shadow-[0_6px_16px_rgba(22,143,91,0.25)] hover:bg-[var(--green-2)]"
            >
              <ShieldCheck size={16} />
              Onboard
            </Link>
          )}
          {/* Account status, as the partner list's row menu (PARTNER_MANAGE write; not one's own account); hidden
              while Submit for Review is offered */}
          {canEditEmail && user.userId !== session?.user?.userId && !showSubmit && (
            <div className="-mt-4 self-start">
              <StatusMenu
                user={user}
                name={name}
                onChanged={(status) =>
                  setState((current) => ({ ...current, user: { ...current.user, accountStatus: status } }))
                }
                onMessage={showToast}
              />
            </div>
          )}
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
                <div className="nh-scroll-hidden -mx-6 flex gap-4 overflow-x-auto px-6 pb-1 sm:-mx-8 sm:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
                  {STAT_CARDS.map((card) => (
                    <StatTile
                      key={card.key}
                      icon={card.icon}
                      tone={card.tone}
                      label={card.label}
                      note={card.note}
                      value={
                        jobStats?.[card.key] == null
                          ? null
                          : card.money
                            ? formatAmount(jobStats[card.key])
                            : jobStats[card.key]
                      }
                    />
                  ))}
                </div>
              </section>

              <div className="grid grid-cols-1 gap-10 lg:grid-cols-2 lg:gap-0 lg:divide-x lg:divide-[var(--line)]">
                <div className="space-y-8 lg:pr-10">
                  <InfoSection title="Personal Information" onEdit={canEdit ? open('personal') : undefined}>
                    <InfoRow icon={UserRound} label="Full Name" value={name} />
                    <InfoRow icon={IdCard} label="Aadhaar Number" value={profile.aadhaarNumber} />
                    <InfoRow
                      icon={Languages}
                      label="Preferred Language"
                      value={label('PREFERRED_CONTACT_LANGUAGE', profile.preferredContactLanguage)}
                    />
                    <InfoRow
                      icon={BadgeCheck}
                      label="Age 18 or Above"
                      value={profile.isAdult == null ? null : profile.isAdult ? 'Yes' : 'No'}
                    />
                    <InfoRow
                      icon={CalendarDays}
                      label="Registered On"
                      value={formatLocalDate(profile.registrationDate)}
                    />
                  </InfoSection>
                  <InfoSection title="Address" onEdit={canEdit ? open('address') : undefined}>
                    <InfoRow
                      icon={MapPin}
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
                  <InfoSection title="Work Details" onEdit={canEdit ? open('work') : undefined}>
                    <InfoRow
                      icon={BriefcaseBusiness}
                      label="Partner Skill Category"
                      value={label('SKILL_CATEGORY', profile.skillCategory)}
                    />
                    <InfoRow icon={Wrench} label="Main Service" value={mainService} />
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
                    <InfoRow
                      icon={Star}
                      label="Additional Service Categories"
                      value={<Chips items={profile.additionalSkills} />}
                    />
                    <InfoRow icon={FileText} label="Previous Work" value={profile.previousWorkDetails} />
                  </InfoSection>
                </div>
                <div className="space-y-8 lg:pl-10">
                  <InfoSection title="Contact & Account" onEdit={canEdit ? open('contact') : undefined}>
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
                  <InfoSection title="Emergency Contact" onEdit={canEdit ? open('emergency') : undefined}>
                    {/* One row, as the address: the name, then relationship and number */}
                    <InfoRow
                      icon={HeartHandshake}
                      value={
                        profile.emergencyContactName ? (
                          <>
                            <span className="block">{profile.emergencyContactName}</span>
                            <span className="block font-normal text-[var(--muted)]">
                              {[
                                label('EMERGENCY_CONTACT_RELATIONSHIP', profile.emergencyContactRelationship),
                                profile.emergencyContactNumber
                              ]
                                .filter(Boolean)
                                .join(' · ')}
                            </span>
                          </>
                        ) : null
                      }
                    />
                  </InfoSection>
                  <InfoSection title="Verification Checks">
                    {VERIFICATION_CHECKS.map((check) => (
                      <InfoRow
                        key={check.key}
                        icon={check.icon}
                        label={check.label}
                        value={
                          profile[check.key] ? (
                            <DoneBy tag="Verified" entry={latestBy(check.event)} />
                          ) : (
                            <Badge className="bg-[var(--light)] text-[var(--muted)]">Not verified yet</Badge>
                          )
                        }
                      />
                    ))}
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
                      label="Approved By"
                      value={approved && latestBy('APPROVED')?.byName ? <DoneBy entry={latestBy('APPROVED')} /> : null}
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

          {tab === 'services' && (
            <ListHeader
              title="Services Offered"
              count={services.length}
              addLabel="Add Service"
              onAdd={canEdit ? open('service') : undefined}
            />
          )}
          {tab === 'services' &&
            (services.length ? (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {services.map((service) => (
                  <ListRow
                    key={service.partnerServiceId}
                    icon={Wrench}
                    onEdit={canEdit ? open('service', service) : undefined}
                    onDelete={canDelete ? () => setRemoving(service) : undefined}
                    title={service.serviceName}
                    details={[
                      service.categoryName,
                      `${service.experienceYears ?? 0} year${service.experienceYears === 1 ? '' : 's'} of experience`
                    ]
                      .filter(Boolean)
                      .join(' · ')}
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
                text={
                  canEdit
                    ? 'Add the services the partner offers, or let them choose during onboarding.'
                    : 'The partner chooses the services they offer during onboarding.'
                }
              />
            ))}

          {tab === 'documents' && (
            <div className="space-y-6">
              {DOCUMENT_SECTIONS.filter(
                (section) => !section.other || documents.some((document) => section.matches(document))
              ).map((section) => {
                const items = documents.filter((document) => section.matches(document));
                return (
                  <section key={section.title} className="rounded-2xl border border-[var(--line)] p-5">
                    <ListHeader
                      title={section.title}
                      count={items.length}
                      addLabel={section.addLabel}
                      onAdd={
                        canEdit && section.kind
                          ? () => setDialog({ kind: 'document', item: null, documentKind: section.kind })
                          : undefined
                      }
                    />
                    {items.length ? (
                      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                        {items.map((document) => (
                          <ListRow
                            key={document.partnerDocumentId}
                            icon={section.icon}
                            title={documentTitle(document)}
                            onEdit={canEdit ? open('document', document) : undefined}
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
                      <p className="rounded-xl bg-[var(--light)] px-4 py-5 text-center text-sm text-[var(--muted)]">
                        {section.empty}
                      </p>
                    )}
                  </section>
                );
              })}
            </div>
          )}

          {tab === 'banks' && (
            <ListHeader
              title="Bank Accounts"
              count={banks.length}
              addLabel="Add Bank Account"
              onAdd={canEdit ? open('bank') : undefined}
            />
          )}
          {tab === 'banks' &&
            (banks.length ? (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {banks.map((bank) => (
                  <ListRow
                    key={bank.partnerBankAccountId}
                    icon={Banknote}
                    onEdit={canEdit ? open('bank', bank) : undefined}
                    title={`${bank.bankName ?? 'Bank account'} · XXXX${bank.accountNumberLast4}`}
                    details={[
                      bank.accountHolderName,
                      `IFSC ${bank.ifscCode}`,
                      bank.branchName,
                      bank.verificationStatus === 'REJECTED' && bank.rejectionReason
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                    badge={
                      <Badge className={verificationBadgeClass(bank.verificationStatus)}>
                        {label('VERIFICATION_STATUS', bank.verificationStatus)}
                      </Badge>
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
              <InfoSection title="Weekly Availability" divided={false} onEdit={canEdit ? open('weekly') : undefined}>
                <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
                  <table className="w-full min-w-[34rem] text-sm">
                    <thead className="bg-[var(--light)]">
                      <tr>
                        <th className="px-3 py-2.5 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
                          Day
                        </th>
                        {slots.map((slot) => (
                          <Fragment key={slot.valueCode}>
                            <th className="px-2 py-2.5 text-center text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
                              <SlotHeading slot={slot} />
                            </th>
                            <LunchBreak after={slot} header />
                          </Fragment>
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
                              <Fragment key={slot.valueCode}>
                                <td className="px-2 py-2 text-center">
                                  <span
                                    className={`inline-flex h-7 w-full max-w-[4.5rem] items-center justify-center rounded-lg text-xs font-bold ${
                                      on ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[var(--light)] text-[#C3CEDA]'
                                    }`}
                                  >
                                    {on ? 'Available' : '—'}
                                  </span>
                                </td>
                                <LunchBreak after={slot} />
                              </Fragment>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </InfoSection>
              <InfoSection title="Coverage" onEdit={canEdit ? open('coverage') : undefined}>
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
          {/* Time off (Module 5), with PARTNER_AVAILABILITY_MANAGE read */}
          {tab === 'availability' && can('PARTNER_AVAILABILITY_MANAGE', 'READ') && (
            <PartnerSchedule partnerProfileId={profile.partnerProfileId} onMessage={showToast} />
          )}

          {tab === 'earnings' && can('PAYOUT_MANAGE', 'READ') ? (
            <PartnerEarnings partnerProfileId={profile.partnerProfileId} />
          ) : tab === 'reviews' && can('REVIEW_MANAGE', 'READ') ? (
            <ProfileReviews of="partner" profileId={profile.partnerProfileId} />
          ) : tab === 'jobs' && can('BOOKING_MANAGE', 'READ') ? (
            <FilteredBookings
              params={{ partnerProfileId: profile.partnerProfileId }}
              hide={['partner']}
              hiddenStatuses={NOT_JOB_STATUSES}
              searchPlaceholder="Search by booking ID, service or customer..."
              emptyText="No bookings assigned to this partner yet."
            />
          ) : (
            PENDING_TABS[tab] && <PendingTab {...PENDING_TABS[tab]} />
          )}
          {tab === 'activity' && (
            <ActivityLog
              entries={state.audit}
              sections={PARTNER_ACTIVITY_SECTIONS}
              formatSubject={(entry) =>
                entry.event.startsWith('DOCUMENT_')
                  ? (label('GOVERNMENT_ID_TYPE', entry.subject) ?? entry.subject)
                  : activitySubject(entry)
              }
            />
          )}
        </div>
      </section>

      {config && canEdit && (
        <FormModal
          key={`${dialog.kind}-${dialog.item ? 'edit' : 'new'}-${catalogue.length}`}
          title={config.title}
          fields={config.fields}
          initial={config.initial}
          lookup={config.lookup}
          submitLabel={dialog.item || PROFILE_KINDS.includes(dialog.kind) ? 'Save Changes' : 'Add'}
          onSubmit={saveDialog}
          onClose={() => setDialog(null)}
          width="max-w-[44rem]"
        />
      )}
      {dialog?.kind === 'weekly' && canEdit && (
        <WeeklyAvailabilityModal
          slots={slots}
          value={profile.weeklyAvailability}
          onSave={async (weeklyAvailability) => {
            await updateProfile({ weeklyAvailability });
            saved('Weekly availability saved.');
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {removing && (
        <ConfirmDialog
          title="Remove Service"
          message={`Remove ${removing.serviceName} from the services this partner offers?`}
          confirmLabel="Remove"
          onConfirm={async () => {
            await authRequest(`/api/partners/services/delete/${removing.partnerServiceId}`, { method: 'DELETE' });
            setRemoving(null);
            saved('Service removed.');
          }}
          onClose={() => setRemoving(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
