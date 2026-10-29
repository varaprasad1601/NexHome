import {
  BadgeCheck,
  Cake,
  CalendarCheck,
  CalendarDays,
  CalendarX,
  CircleCheckBig,
  Clock,
  CreditCard,
  History,
  Home,
  IdCard,
  Languages,
  Mail,
  MapPin,
  Megaphone,
  Pencil,
  Phone,
  Plus,
  Star,
  ThumbsDown,
  ThumbsUp,
  Timer,
  UserCheck,
  UserRound,
  Wallet
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import DatePicker from '../../components/admin/common/DatePicker';
import FormModal from '../../components/admin/common/FormModal';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import Toggle from '../../components/admin/common/Toggle';
import ActivityLog from '../../components/admin/profile/ActivityLog';
import FilteredBookings from '../../components/admin/booking/FilteredBookings';
import CustomerPayments from '../../components/admin/payment/CustomerPayments';
import CreateBookingModal from '../../components/admin/booking/CreateBookingModal';
import CompletionAvatar from '../../components/admin/profile/CompletionAvatar';
import StatusMenu from '../../components/admin/profile/StatusMenu';
import {
  InfoRow,
  InfoSection,
  PendingTab,
  SectionActions,
  StatTile
} from '../../components/admin/profile/ProfileParts';
import ProfileReviews from '../../components/admin/review/ProfileReviews';
import { inlineInputClass, profileTabClass } from '../../components/admin/profile/profileStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { addressBody, addressFields } from '../../data/addressForm';
import useActivity from '../../hooks/useActivity';
import { formatAmount } from '../../data/bookingOptions';
import { accountStatusLabel, statusBadgeClass } from '../../data/staffOptions';
import { formatNumber, splitApiDateTime } from '../../utils/format';

const NAME_PATTERN = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function fullName(profile) {
  return [profile.firstName, profile.lastName].filter(Boolean).join(' ');
}

function formatDate(value) {
  return splitApiDateTime(value)?.date ?? null;
}

function formatDateTime(value) {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : null;
}

/** A date of birth (yyyy-MM-dd) as the console shows dates, e.g. 12 Mar 1994. */
function formatBirthDate(value) {
  if (!value) return null;
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  });
}

/** Today in yyyy-MM-dd, the latest date a date of birth may not reach. */
function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** Yesterday in yyyy-MM-dd: the latest date of birth (it must be in the past). */
function yesterday() {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function validate(form) {
  const errors = {};
  ['firstName', 'lastName'].forEach((field) => {
    if (field in form && form[field].trim() && !NAME_PATTERN.test(form[field])) {
      errors[field] = 'Use letters, spaces, dots, apostrophes and hyphens';
    }
  });
  if ('dateOfBirth' in form && form.dateOfBirth && form.dateOfBirth >= today()) {
    errors.dateOfBirth = 'The date of birth must be in the past';
  }
  if ('email' in form && form.email.trim() && !EMAIL_PATTERN.test(form.email.trim())) {
    errors.email = 'Enter a valid email address';
  }
  return errors;
}

/**
 * The customer statistics, from GET /api/bookings/customer-stats/{id} (BOOKING_MANAGE read): bookings in all, then by
 * stage (pending: every new booking until it is confirmed; confirmed and waiting for a partner; partner assigned and
 * not answered; partner accepted, which includes PARTNER_EN_ROUTE, shown as "Partner Accepted"; in progress, paused
 * included), successful (completed), cancelled, the times partners declined the customer's jobs, and spend (online
 * payments captured plus cash collected, less refunds). The rating is in the header, below the joined date.
 */
const STAT_CARDS = [
  { key: 'totalBookings', label: 'Total Bookings', icon: CalendarCheck, tone: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'pending', label: 'Pending', icon: Clock, tone: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'confirmed', label: 'Confirmed', icon: BadgeCheck, tone: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { key: 'partnerAssigned', label: 'Partner Assigned', icon: UserCheck, tone: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'accepted', label: 'Partner Accepted', icon: ThumbsUp, tone: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inProgress', label: 'In Progress', icon: Timer, tone: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'completed', label: 'Successful Bookings', icon: CircleCheckBig, tone: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'cancelled', label: 'Cancelled Bookings', icon: CalendarX, tone: 'bg-[#FDECEC] text-[#E53E3E]' },
  { key: 'partnerRejected', label: 'Partner Rejected', icon: ThumbsDown, tone: 'bg-[#FDECEC] text-[#E53E3E]' },
  {
    key: 'totalSpend',
    label: 'Total Spend',
    icon: Wallet,
    tone: 'bg-[#E8EEF6] text-[var(--navy)]',
    money: true
  }
];

/**
 * The tabs after Overview and Addresses, whose data comes with later modules: an empty state until then. `count` is the
 * tab's badge (always 0 until those records exist; replace it with the real count then). Activity Logs follows them.
 */
const PENDING_TABS = {
  // Live (Module 6) for BOOKING_MANAGE readers: the customer's bookings
  bookings: {
    label: 'Bookings',
    icon: CalendarCheck,
    text: "The customer's bookings show here for staff who can read bookings."
  },
  // Live (Module 7) for PAYMENT_MANAGE readers: the customer's payments
  payments: {
    label: 'Payments',
    icon: CreditCard,
    text: "The customer's payments show here for staff who can read payments."
  },
  // Live (Module 9) for REVIEW_MANAGE readers: the reviews the customer wrote
  reviews: {
    label: 'Reviews Written',
    icon: Star,
    text: 'Reviews the customer wrote show here for staff who can read reviews.'
  }
};

/** The details that make a customer's profile complete, in the order the completion lists them. */
const PROFILE_CHECKS = [
  { label: 'Name', done: (profile) => Boolean(profile.firstName && profile.lastName) },
  { label: 'Date of birth', done: (profile) => Boolean(profile.dateOfBirth) },
  { label: 'Language', done: (profile) => Boolean(profile.preferredLanguage) },
  { label: 'Email', done: (profile, user) => Boolean(user.email) },
  { label: 'Verified mobile', done: (profile, user) => Boolean(user.accountVerifiedDate) },
  { label: 'Address', done: (profile, user, addresses) => addresses.length > 0 }
];

/** One saved address as a card: label and type, the default badge, contact and the address lines. */
function AddressCard({ address, typeLabel, onEdit }) {
  return (
    <div className="rounded-xl border border-[var(--line)] p-5">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
          <Home size={16} />
        </span>
        <span className="font-black text-[var(--navy)]">{address.label || typeLabel}</span>
        {address.label && (
          <span className="rounded-full bg-[var(--light)] px-2.5 py-0.5 text-[0.75rem] font-bold text-[var(--muted)]">
            {typeLabel}
          </span>
        )}
        {address.isDefault && (
          <span className="rounded-full bg-[#E9F6EF] px-2.5 py-0.5 text-[0.75rem] font-bold text-[var(--green-2)]">
            Default
          </span>
        )}
        {onEdit && (
          <button
            type="button"
            onClick={onEdit}
            className="ml-auto flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
          >
            <Pencil size={14} />
            Edit
          </button>
        )}
      </div>
      <div className="text-sm font-bold text-[var(--ink)]">
        {address.contactName} · {address.mobileNumber}
      </div>
      <div className="mt-1 text-sm text-[var(--muted)]">
        {[address.addressLine1, address.addressLine2].filter(Boolean).join(', ')}
        {address.landmark ? ` (near ${address.landmark})` : ''}
      </div>
      <div className="text-sm text-[var(--muted)]">
        {[address.city, address.state].filter(Boolean).join(', ')}
        {address.postalCode ? ` - ${address.postalCode}` : ''}
      </div>
    </div>
  );
}

/**
 * User Management > Customers > customer id: the summary (photo, name, status, customer id, joined on), then the
 * Overview tab (Personal Information: the customer profile; Contact & Account: the account) and the Addresses tab (the
 * customer's saved addresses; with CUSTOMER_MANAGE write Add Address and each card's Edit). With CUSTOMER_MANAGE write each Overview section has its own Edit, saving
 * only its changed fields: PUT /api/profiles/customers/update and, for the email, PUT /api/users/update. The mobile
 * number and status are managed elsewhere. Data from GET /api/profiles/customers/{id}, /api/users/{userId} and
 * /api/addresses/users/{userId}; language and address type names from their lookups.
 */
export default function CustomerProfilePage() {
  const { customerProfileId } = useParams();
  const { authRequest, can, session } = useAdminAuth();
  const canEdit = can('CUSTOMER_MANAGE', 'WRITE');
  const [state, setState] = useState({
    profile: null,
    user: null,
    addresses: [],
    error: ''
  });
  const [languages, setLanguages] = useState([]);
  const [addressTypes, setAddressTypes] = useState([]);
  const [tab, setTab] = useState('overview');
  const [booking, setBooking] = useState(false);
  const navigate = useNavigate();
  // The Activity Logs tab: account, profile and address changes from the audit columns
  const activity = useActivity(tab === 'activity' ? `/api/profiles/customers/activity/${customerProfileId}` : null);
  // The section being edited ('personal' or 'contact') and its form
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  // The address dialog: null, 'new' or the address being edited
  const [addressDialog, setAddressDialog] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/profiles/customers/${customerProfileId}`)
      .then((profile) =>
        Promise.all([
          authRequest(`/api/users/${profile.userId}`),
          authRequest(`/api/addresses/users/${profile.userId}`).catch(() => [])
        ]).then(([user, addresses]) => {
          if (!cancelled) setState({ profile, user, addresses, error: '' });
        })
      )
      .catch((err) => {
        if (!cancelled)
          setState({
            profile: null,
            user: null,
            addresses: [],
            error: err.message
          });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, customerProfileId]);

  // The statistics and the Bookings tab's count, for BOOKING_MANAGE readers
  const canReadBookings = can('BOOKING_MANAGE', 'READ');
  const [bookingStats, setBookingStats] = useState(null);
  const bookingCount = bookingStats?.totalBookings ?? null;

  // The Reviews tab's count (one row asked for, the total read), for REVIEW_MANAGE readers
  const canReadReviews = can('REVIEW_MANAGE', 'READ');
  const [reviewCount, setReviewCount] = useState(null);
  useEffect(() => {
    if (!canReadReviews) return undefined;
    let cancelled = false;
    authRequest(`/api/reviews?size=1&customerProfileId=${customerProfileId}`)
      .then((page) => !cancelled && setReviewCount(page.totalElements))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, customerProfileId, canReadReviews]);
  useEffect(() => {
    if (!canReadBookings) return undefined;
    let cancelled = false;
    authRequest(`/api/bookings/customer-stats/${customerProfileId}`)
      .then((stats) => !cancelled && setBookingStats(stats))
      .catch(() => !cancelled && setBookingStats(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, customerProfileId, canReadBookings]);

  // Display names of the lookup codes (public lookups; without them the codes show)
  useEffect(() => {
    let cancelled = false;
    authRequest('/api/lookups/values/PREFERRED_CONTACT_LANGUAGE')
      .then((values) => !cancelled && setLanguages(values))
      .catch(() => {});
    authRequest('/api/lookups/values/ADDRESS_TYPE')
      .then((values) => !cancelled && setAddressTypes(values))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  const { profile, user, addresses, error } = state;
  const name = profile ? fullName(profile) : '';

  useEffect(() => {
    document.title = `${name || profile?.publicId || 'Customer'} | NexHome Admin`;
  }, [name, profile]);

  // The customer id once loaded; nothing in its place meanwhile
  const crumbs = [
    { label: 'User Management' },
    { label: 'Customers', to: '/admin/users/customers' },
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
  const languageLabel = (code) => languages.find((value) => value.valueCode === code)?.displayName ?? code;
  const saveAddress = async (values) => {
    const editing = addressDialog !== 'new';
    // On update "" clears an optional field; on create it is left out
    const fields = addressBody(values, editing);
    if (editing) {
      await authRequest('/api/addresses/update', {
        method: 'PUT',
        body: { addressId: addressDialog.addressId, ...fields, isDefault: values.isDefault }
      });
    } else {
      await authRequest('/api/addresses/create', {
        method: 'POST',
        // The first address is the default anyway
        body: { userId: profile.userId, ...fields, countryCode: 'IN', isDefault: values.isDefault || undefined }
      });
    }
    const saved = await authRequest(`/api/addresses/users/${profile.userId}`);
    setState((current) => ({ ...current, addresses: saved }));
    setAddressDialog(null);
    showToast(editing ? 'Address updated.' : 'Address added.');
  };

  const typeLabel = (code) => addressTypes.find((value) => value.valueCode === code)?.displayName ?? code;

  const startEditing = (section) => () => {
    setEditing(section);
    setErrors({});
    setForm(
      section === 'personal'
        ? {
            firstName: profile.firstName ?? '',
            lastName: profile.lastName ?? '',
            dateOfBirth: profile.dateOfBirth ?? '',
            preferredLanguage: profile.preferredLanguage ?? '',
            marketingConsent: profile.marketingConsent
          }
        : { email: user.email ?? '' }
    );
  };
  const cancelEditing = () => {
    setEditing(null);
    setForm(null);
  };
  /** Only one section is edited at a time, and only with CUSTOMER_MANAGE write. */
  const editLink = (section) => (editing || !canEdit ? null : startEditing(section));
  const set = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const save = async () => {
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    try {
      if (editing === 'contact') {
        // The email is on the account; "" clears it
        const email = form.email.trim();
        if (email !== (user.email ?? '')) {
          const updated = await authRequest('/api/users/update', {
            method: 'PUT',
            body: { userId: user.userId, email }
          });
          setState((current) => ({ ...current, user: updated }));
        }
      } else {
        // Only the changed fields. Names cannot be blanked (left out); "" clears the language; a date of birth
        // cannot be cleared once set
        const body = { customerProfileId: profile.customerProfileId };
        ['firstName', 'lastName'].forEach((field) => {
          const text = form[field].trim();
          if (text && text !== (profile[field] ?? '')) body[field] = text;
        });
        if (form.dateOfBirth && form.dateOfBirth !== (profile.dateOfBirth ?? '')) body.dateOfBirth = form.dateOfBirth;
        if (form.preferredLanguage !== (profile.preferredLanguage ?? ''))
          body.preferredLanguage = form.preferredLanguage;
        if (form.marketingConsent !== profile.marketingConsent) body.marketingConsent = form.marketingConsent;
        const updated = await authRequest('/api/profiles/customers/update', {
          method: 'PUT',
          body
        });
        setState((current) => ({ ...current, profile: updated }));
      }
      showToast(`${editing === 'contact' ? 'Contact details' : 'Personal information'} saved.`);
      cancelEditing();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const fieldError = (field) =>
    errors[field] && <div className="mt-1 text-xs font-bold text-[#C53030]">{errors[field]}</div>;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      {/* One card: the summary, the tabs and their details (as the staff profile) */}
      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-center gap-6 px-6 pb-6 pt-7 sm:px-8">
          <CompletionAvatar
            checks={PROFILE_CHECKS.map((check) => ({ label: check.label, done: check.done(profile, user, addresses) }))}
            photoUrl={user.profilePhotoUrl}
            name={name}
            initials={initials}
            canEdit={canEdit}
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1
                className={`mb-0 text-2xl font-black leading-tight ${name ? 'text-[var(--navy)]' : 'text-[var(--muted)]'}`}
              >
                {name || 'Name not added yet'}
              </h1>
              <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadgeClass(user.accountStatus)}`}>
                {accountStatusLabel(user.accountStatus)}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <IdCard size={15} className="text-[var(--blue)]" />
                Customer ID: <span className="font-bold text-[var(--ink)]">{profile.publicId}</span>
              </span>
            </div>
            <div className="mt-1.5 flex items-center gap-2 text-sm text-[var(--muted)]">
              <CalendarDays size={15} className="text-[var(--blue)]" />
              Joined on <span className="font-bold text-[var(--ink)]">{formatDate(user.creationDate) ?? '—'}</span>
            </div>
          </div>
          {/* Account status, as the customer list's row menu (CUSTOMER_MANAGE write; not one's own account) */}
          {canEdit && user.userId !== session?.user?.userId && (
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
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'overview'}
            className={profileTabClass(tab === 'overview')}
            onClick={() => setTab('overview')}
          >
            <UserRound size={16} />
            Overview
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'addresses'}
            className={profileTabClass(tab === 'addresses')}
            onClick={() => setTab('addresses')}
          >
            <MapPin size={16} />
            Addresses
            <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] text-[var(--muted)]">
              {addresses.length}
            </span>
          </button>
          {Object.entries(PENDING_TABS).map(([key, pending]) => {
            const Icon = pending.icon;
            return (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                className={profileTabClass(tab === key)}
                onClick={() => setTab(key)}
              >
                <Icon size={16} />
                {pending.label}
                {(key === 'bookings' ? bookingCount : key === 'reviews' ? reviewCount : pending.count) != null && (
                  <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] text-[var(--muted)]">
                    {key === 'bookings' ? bookingCount : key === 'reviews' ? reviewCount : pending.count}
                  </span>
                )}
              </button>
            );
          })}
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'activity'}
            className={profileTabClass(tab === 'activity')}
            onClick={() => setTab('activity')}
          >
            <History size={16} />
            Activity Logs
          </button>
        </div>

        <div className="px-6 py-7 sm:px-8" role="tabpanel">
          {tab === 'overview' ? (
            <>
              {/* Customer statistics (see STAT_CARDS) */}
              <section className="mb-8">
                <h3 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">Customer Statistics</h3>
                {/* Five to a row on wide screens, a sideways-scrolling strip on smaller ones */}
                <div className="nh-scroll-hidden -mx-6 flex gap-4 overflow-x-auto px-6 pb-1 sm:-mx-8 sm:px-8 xl:mx-0 xl:grid xl:grid-cols-5 xl:overflow-visible xl:px-0 xl:pb-0">
                  {STAT_CARDS.map((card) => (
                    <StatTile
                      key={card.key}
                      icon={card.icon}
                      tone={card.tone}
                      label={card.label}
                      note={card.note}
                      value={
                        bookingStats?.[card.key] == null
                          ? null
                          : card.money
                            ? formatAmount(bookingStats[card.key])
                            : formatNumber(bookingStats[card.key])
                      }
                    />
                  ))}
                </div>
              </section>
              <div className="grid grid-cols-1 gap-10 lg:grid-cols-2 lg:gap-0 lg:divide-x lg:divide-[var(--line)]">
                <div className="lg:pr-10">
                  <InfoSection title="Personal Information" onEdit={editLink('personal')}>
                    <InfoRow icon={UserRound} label="Full Name" value={name}>
                      {editing === 'personal' && (
                        <div className="flex flex-col gap-2 sm:flex-row">
                          <div className="min-w-0 flex-1">
                            <input
                              aria-label="First name"
                              placeholder="First name"
                              className={inlineInputClass(errors.firstName)}
                              value={form.firstName}
                              onChange={(e) => set('firstName', e.target.value)}
                              maxLength={100}
                            />
                            {fieldError('firstName')}
                          </div>
                          <div className="min-w-0 flex-1">
                            <input
                              aria-label="Last name"
                              placeholder="Last name"
                              className={inlineInputClass(errors.lastName)}
                              value={form.lastName}
                              onChange={(e) => set('lastName', e.target.value)}
                              maxLength={100}
                            />
                            {fieldError('lastName')}
                          </div>
                        </div>
                      )}
                    </InfoRow>
                    <InfoRow icon={Cake} label="Date of Birth" value={formatBirthDate(profile.dateOfBirth)}>
                      {editing === 'personal' && (
                        <div>
                          <DatePicker
                            variant="inline"
                            ariaLabel="Date of birth"
                            value={form.dateOfBirth}
                            max={yesterday()}
                            error={Boolean(errors.dateOfBirth)}
                            onChange={(value) => set('dateOfBirth', value)}
                          />
                          {fieldError('dateOfBirth')}
                        </div>
                      )}
                    </InfoRow>
                    <InfoRow
                      icon={Languages}
                      label="Preferred Language"
                      value={profile.preferredLanguage && languageLabel(profile.preferredLanguage)}
                    >
                      {editing === 'personal' && (
                        <Select
                          variant="inline"
                          ariaLabel="Preferred language"
                          value={form.preferredLanguage}
                          onChange={(value) => set('preferredLanguage', value)}
                          options={[
                            { value: '', label: 'Not set' },
                            ...languages.map((value) => ({ value: value.valueCode, label: value.displayName }))
                          ]}
                        />
                      )}
                    </InfoRow>
                    <InfoRow
                      icon={Megaphone}
                      label="Marketing Messages"
                      value={profile.marketingConsent ? 'Agreed' : 'Not agreed'}
                    >
                      {editing === 'personal' && (
                        <div className="flex items-center gap-3 font-normal">
                          <Toggle
                            checked={form.marketingConsent}
                            onChange={(on) => set('marketingConsent', on)}
                            label="Marketing messages"
                          />
                          {form.marketingConsent ? 'Agreed' : 'Not agreed'}
                        </div>
                      )}
                    </InfoRow>
                  </InfoSection>
                  {editing === 'personal' && <SectionActions onCancel={cancelEditing} onSave={save} saving={saving} />}
                </div>
                <div className="lg:pl-10">
                  <InfoSection title="Contact & Account" onEdit={editLink('contact')}>
                    <InfoRow icon={Mail} label="Email Address" value={user.email}>
                      {editing === 'contact' && (
                        <div>
                          <input
                            type="email"
                            aria-label="Email address"
                            placeholder="Email address"
                            className={inlineInputClass(errors.email)}
                            value={form.email}
                            onChange={(e) => set('email', e.target.value)}
                            maxLength={255}
                          />
                          {fieldError('email')}
                        </div>
                      )}
                    </InfoRow>
                    <InfoRow icon={Phone} label="Mobile Number" value={`${user.countryCode} ${user.mobileNumber}`} />
                    <InfoRow icon={Clock} label="Last Login" value={formatDateTime(user.lastLoginAt) ?? 'Never'} />
                    <InfoRow
                      icon={BadgeCheck}
                      label="Mobile Verified On"
                      value={formatDate(user.accountVerifiedDate)}
                    />
                  </InfoSection>
                  {editing === 'contact' && <SectionActions onCancel={cancelEditing} onSave={save} saving={saving} />}
                </div>
              </div>
            </>
          ) : tab === 'bookings' && can('BOOKING_MANAGE', 'READ') ? (
            <>
              {can('BOOKING_MANAGE', 'WRITE') && (
                <div className="mb-4 flex justify-end">
                  <button
                    type="button"
                    onClick={() => setBooking(true)}
                    className="rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
                  >
                    + Create Booking
                  </button>
                </div>
              )}
              <FilteredBookings
                params={{ customerProfileId: profile.customerProfileId }}
                hide={['customer']}
                searchPlaceholder="Search by booking ID, service or partner..."
                emptyText="This customer has no bookings yet."
              />
            </>
          ) : tab === 'reviews' && can('REVIEW_MANAGE', 'READ') ? (
            <ProfileReviews of="customer" profileId={profile.customerProfileId} />
          ) : tab === 'payments' && can('PAYMENT_MANAGE', 'READ') ? (
            <CustomerPayments customerProfileId={profile.customerProfileId} />
          ) : tab === 'activity' ? (
            <ActivityLog entries={activity.entries} loading={activity.loading} />
          ) : PENDING_TABS[tab] ? (
            <PendingTab {...PENDING_TABS[tab]} />
          ) : addresses.length ? (
            <InfoSection
              title={
                <span className="flex w-full items-center justify-between gap-3">
                  Saved Addresses
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => setAddressDialog('new')}
                      className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
                    >
                      <Plus size={15} strokeWidth={2.6} />
                      Add Address
                    </button>
                  )}
                </span>
              }
              divided={false}
            >
              <div className="grid grid-cols-1 gap-4 pt-3 lg:grid-cols-2">
                {addresses.map((address) => (
                  <AddressCard
                    key={address.addressId}
                    address={address}
                    typeLabel={typeLabel(address.addressType)}
                    onEdit={canEdit ? () => setAddressDialog(address) : undefined}
                  />
                ))}
              </div>
            </InfoSection>
          ) : (
            <div className="flex flex-col items-center py-10 text-center">
              <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
                <MapPin size={22} />
              </span>
              <div className="font-bold text-[var(--ink)]">No addresses saved yet</div>
              <div className="text-sm text-[var(--muted)]">
                The customer adds their service addresses in the NexHome app{canEdit ? ', or add one here.' : '.'}
              </div>
              {canEdit && (
                <button
                  type="button"
                  onClick={() => setAddressDialog('new')}
                  className="mt-4 flex items-center gap-2 rounded-xl bg-[var(--blue)] px-4 py-2 text-sm font-bold text-white hover:bg-[var(--navy)]"
                >
                  <Plus size={16} strokeWidth={2.6} />
                  Add Address
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      {addressDialog && canEdit && (
        <FormModal
          title={addressDialog === 'new' ? 'Add Address' : 'Edit Address'}
          fields={addressFields(addressTypes)}
          initial={
            addressDialog === 'new'
              ? {
                  contactName: name ?? '',
                  mobileNumber: user.mobileNumber ?? '',
                  addressType: addressTypes[0]?.valueCode ?? '',
                  label: '',
                  addressLine1: '',
                  addressLine2: '',
                  landmark: '',
                  city: '',
                  state: '',
                  postalCode: '',
                  isDefault: addresses.length === 0
                }
              : {
                  contactName: addressDialog.contactName ?? '',
                  mobileNumber: addressDialog.mobileNumber ?? '',
                  addressType: addressDialog.addressType ?? '',
                  label: addressDialog.label ?? '',
                  addressLine1: addressDialog.addressLine1 ?? '',
                  addressLine2: addressDialog.addressLine2 ?? '',
                  landmark: addressDialog.landmark ?? '',
                  city: addressDialog.city ?? '',
                  state: addressDialog.state ?? '',
                  postalCode: addressDialog.postalCode ?? '',
                  isDefault: addressDialog.isDefault
                }
          }
          submitLabel={addressDialog === 'new' ? 'Add Address' : 'Save Changes'}
          onSubmit={saveAddress}
          onClose={() => setAddressDialog(null)}
          width="max-w-[44rem]"
        />
      )}
      {booking && (
        <CreateBookingModal
          customer={{
            customerProfileId: profile.customerProfileId,
            userId: profile.userId,
            name: `${name || 'Name not added'} · ${profile.publicId}`,
            contactName: name,
            mobileNumber: user.mobileNumber
          }}
          onClose={() => setBooking(false)}
          onCreated={(created) =>
            navigate(`/admin/bookings/${created.bookingId}`, {
              state: { toast: { message: `Booking ${created.publicId} created.`, tone: 'success' } }
            })
          }
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
