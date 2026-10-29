import {
  ArrowLeft,
  CalendarClock,
  Check,
  CircleX,
  CreditCard,
  History,
  MapPin,
  Pause,
  Play,
  Search,
  Square,
  UserRound,
  UserRoundPlus,
  Wrench
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import BookingPaymentCard from '../../components/admin/payment/BookingPaymentCard';
import PaymentStatusBadge from '../../components/admin/booking/PaymentStatusBadge';
import BookingReviewCard from '../../components/admin/review/BookingReviewCard';
import FormModal from '../../components/admin/common/FormModal';
import DatePicker from '../../components/admin/common/DatePicker';
import Modal from '../../components/admin/common/Modal';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import {
  cardClass,
  fieldClass,
  headClass,
  linkClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import {
  BOOKING_LOOKUPS,
  CANCELLABLE,
  TIMER_ACTIONS,
  assignmentStatusClass,
  bookingStatusClass,
  formatAmount,
  serviceNameOf
} from '../../data/bookingOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { splitApiDateTime } from '../../utils/format';
import { TIME_OPTIONS, apiDateTimeParts, timeLabel, toInstant, todayInIndia } from '../../utils/schedule';

const TIMER_BUTTONS = {
  START: { label: 'Start Work', icon: Play },
  PAUSE: { label: 'Pause', icon: Pause },
  RESUME: { label: 'Resume', icon: Play },
  STOP: { label: 'Finish Work', icon: Square }
};
/** Cancelling asks for a reason. */
const REASON_ACTIONS = {
  CANCEL: { title: 'Cancel Booking', label: 'Why is the booking cancelled?', submit: 'Cancel Booking', required: true }
};
/** The timer steps staff run here: the partner starts the work from the partner app. */
const STAFF_TIMER_ACTIONS = (status) => (TIMER_ACTIONS[status] ?? []).filter((event) => event !== 'START');
/** The history reason the backend writes when staff take the partner off (shown as "Unassigned" alone). */
const PARTNER_UNASSIGNED = 'Partner unassigned';
/** A history row of the partner taken off (unassigned, or declining): back to CONFIRMED from ASSIGNED / PARTNER ACCEPTED. */
const isUnassigned = (row) => row.newStatus === 'CONFIRMED' && ['ASSIGNED', 'PARTNER_EN_ROUTE'].includes(row.oldStatus);
/** The partner can be unassigned until the work starts. */
const UNASSIGNABLE = ['ASSIGNED', 'PARTNER_EN_ROUTE'];

const when = (value) => {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : null;
};
/** A backend date ("Thu, 29 Oct 2026 09:30:00 IST") as an ISO instant, or null. */
const isoOf = (value) => {
  const parts = apiDateTimeParts(value);
  return parts ? toInstant(parts.date, parts.time) : null;
};
const labelClass = 'mb-1.5 block text-sm font-bold text-[var(--ink)]';
const actionButton =
  'flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold transition disabled:cursor-not-allowed disabled:opacity-50';
const smallButton =
  'rounded-lg px-3 py-1.5 text-[0.8125rem] font-bold transition disabled:cursor-not-allowed disabled:opacity-50';

function Badge({ className, children }) {
  return (
    <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${className}`}>{children}</span>
  );
}

function Card({ title, icon: Icon, action, children }) {
  return (
    <section className={cardClass}>
      <div className="mb-3 flex items-center gap-2">
        <Icon size={17} className="text-[var(--blue)]" />
        <h2 className="mb-0 flex-1 text-[1.0625rem] font-black text-[var(--navy)]">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

/** A label over a value, for the Service card's grid. */
function Detail({ label, value, sub }) {
  return (
    <div>
      <div className="text-[0.75rem] font-bold uppercase tracking-[0.05em] text-[var(--muted)]">{label}</div>
      <div className="mt-1 text-[0.9375rem] font-bold text-[var(--ink)]">
        {value ?? <span className="text-[var(--muted)]">—</span>}
      </div>
      {sub && <div className="text-[0.8125rem] text-[var(--muted)]">{sub}</div>}
    </div>
  );
}

/** Why a partner cannot take the booking's time (`busyReason` of GET /api/availability/partners). */
const BUSY_REASONS = {
  OUTSIDE_WEEKLY_HOURS: 'Outside weekly hours',
  TIME_OFF: 'On time off',
  BOOKED: 'Already booked'
};

/** An ISO instant as Indian date and time parts ({ date: yyyy-MM-dd, time: HH:mm }). */
const istParts = (ms) => {
  const text = new Date(ms + 5.5 * 3600 * 1000).toISOString();
  return { date: text.slice(0, 10), time: text.slice(11, 16) };
};
/** The next half hour from now, in Indian time: the slot's default start for a booking without one. */
const nextHalfHour = () => istParts(Math.ceil(Date.now() / 1800000) * 1800000);
const nameOf = (partner) => [partner.firstName, partner.lastName].filter(Boolean).join(' ');

/**
 * Assign Partner, in two steps, the dialog as tall as the screen allows. 1, the slot: start date and time (the
 * booking's when still ahead, else the next half hour) and the end time: worked out from the package (an option with
 * hours, `durationMinutes` of the item's variant snapshot) and locked, otherwise chosen; it must start in the future and
 * end after it starts. 2, the partners offering the service for that slot (GET /api/availability/partners?
 * includeBusy=true), free ones first, with a search (name, partner id, city) and an Assign button each (not for a
 * partner on time off or booked then). Assigning: POST /api/bookings/assignments/offer with `direct`, `startsAt` /
 * `endsAt` (the booking's scheduled time from then on) and `outsideWeeklyHours` (staff may book a partner outside their
 * weekly hours).
 */
function AssignPartnerModal({ booking, packageMinutes, packageName, onClose, onDone }) {
  const { authRequest } = useAdminAuth();
  const [step, setStep] = useState('slot');
  const [slot, setSlot] = useState(() => {
    // The booking's time while it is still ahead, else the next half hour
    const booked = isoOf(booking.scheduledStartAt);
    const start =
      booked && new Date(booked).getTime() > Date.now() ? apiDateTimeParts(booking.scheduledStartAt) : nextHalfHour();
    const bookedEnd = booked === toInstant(start.date, start.time) ? apiDateTimeParts(booking.scheduledEndAt) : null;
    const end =
      bookedEnd ?? istParts(new Date(toInstant(start.date, start.time)).getTime() + (packageMinutes ?? 60) * 60000);
    return { startDate: start.date, startTime: start.time, endTime: end.time };
  });
  const [searchText, setSearchText] = useState('');
  const [partners, setPartners] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(null);

  const startsAt = toInstant(slot.startDate, slot.startTime);
  // A package fixes the end: start + its hours; otherwise the end time staff chose, on the start's day
  const endsAt =
    packageMinutes && startsAt
      ? new Date(new Date(startsAt).getTime() + packageMinutes * 60000).toISOString()
      : toInstant(slot.startDate, slot.endTime);
  const autoEnd = packageMinutes && endsAt ? istParts(new Date(endsAt).getTime()) : null;
  const setSlotPart = (key) => (value) => {
    setError('');
    setSlot((current) => ({ ...current, [key]: value }));
  };

  // The partners for the chosen slot, once it is set
  useEffect(() => {
    if (step !== 'partners') return undefined;
    let cancelled = false;
    const params = new URLSearchParams({ serviceId: String(booking.serviceId), startsAt, endsAt, includeBusy: 'true' });
    authRequest(`/api/availability/partners?${params}`)
      .then((loaded) => !cancelled && setPartners(loaded))
      .catch((err) => {
        if (cancelled) return;
        setError(err.message);
        setPartners([]);
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, booking.serviceId, step, startsAt, endsAt]);

  const findPartners = () => {
    if (!startsAt || !endsAt) {
      setError("Choose the slot's start and end.");
    } else if (new Date(startsAt).getTime() <= Date.now()) {
      setError('The slot must start in the future.');
    } else if (endsAt <= startsAt) {
      setError('The slot must end after it starts.');
    } else {
      setError('');
      setPartners(null);
      setStep('partners');
    }
  };

  const assign = async (partner) => {
    setSaving(partner.partnerProfileId);
    setError('');
    try {
      await authRequest('/api/bookings/assignments/offer', {
        method: 'POST',
        body: {
          bookingId: booking.bookingId,
          partnerProfileId: partner.partnerProfileId,
          direct: true,
          outsideWeeklyHours: true,
          startsAt,
          endsAt
        }
      });
      onDone(`${nameOf(partner) || partner.publicId} is assigned.`);
    } catch (err) {
      setError(err.message);
      setSaving(null);
    }
  };

  const query = searchText.trim().toLowerCase();
  // Partners already booked for the slot (another job then) are not listed
  const available = (partners ?? []).filter((partner) => partner.isFree || partner.busyReason !== 'BOOKED');
  const shown = available.filter(
    (partner) =>
      !query || [nameOf(partner), partner.publicId, partner.city].some((value) => value?.toLowerCase().includes(query))
  );
  const errorBox = error && (
    <div
      role="alert"
      className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
    >
      {error}
    </div>
  );

  if (step === 'slot') {
    return (
      <Modal title="Assign Partner" onClose={onClose} width="max-w-[38rem]">
        <p className="mb-4 text-sm text-[var(--muted)]">Set the slot, then pick a partner free for it.</p>
        {errorBox}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <span className={labelClass}>Start Date</span>
            <DatePicker value={slot.startDate} onChange={setSlotPart('startDate')} min={todayInIndia()} />
          </div>
          <div>
            <span className={labelClass}>Start Time</span>
            <Select value={slot.startTime} onChange={setSlotPart('startTime')} options={TIME_OPTIONS} />
          </div>
          <div>
            <span className={labelClass}>End Time</span>
            {autoEnd ? (
              <div className={`${fieldClass(false)} bg-[var(--light)] text-[var(--muted)]`} aria-disabled="true">
                {timeLabel(autoEnd.time)}
                {autoEnd.date !== slot.startDate && ' (next day)'}
              </div>
            ) : (
              <Select value={slot.endTime} onChange={setSlotPart('endTime')} options={TIME_OPTIONS} />
            )}
          </div>
          {autoEnd && (
            <p className="mb-0 self-end pb-2.5 text-[0.8125rem] text-[var(--muted)]">
              Set by the package: {packageName}, {packageMinutes} minutes.
            </p>
          )}
        </div>
        <div className="mt-6 flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onClose}
            className={`${actionButton} border border-[var(--line)] text-[var(--ink)] hover:bg-[var(--light)]`}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={findPartners}
            className={`${actionButton} bg-[var(--blue)] text-white hover:bg-[var(--navy)]`}
          >
            <Search size={16} />
            Find Partners
          </button>
        </div>
      </Modal>
    );
  }

  const startParts = istParts(new Date(startsAt).getTime());
  const endParts = istParts(new Date(endsAt).getTime());
  return (
    <Modal title="Assign Partner" onClose={onClose} width="max-w-[46rem]" fill>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--light)] px-4 py-3">
        <div className="flex items-center gap-2.5 text-sm">
          <CalendarClock size={17} className="text-[var(--blue)]" />
          <span className="font-bold text-[var(--ink)]">
            {startParts.date.split('-').reverse().join('/')}, {timeLabel(startParts.time)} – {timeLabel(endParts.time)}
            {endParts.date !== startParts.date && ' (next day)'}
          </span>
        </div>
        <button
          type="button"
          onClick={() => {
            setStep('slot');
            setError('');
          }}
          className="flex items-center gap-1.5 text-sm font-bold text-[var(--blue)]"
        >
          <ArrowLeft size={15} />
          Change slot
        </button>
      </div>
      <label className="relative mb-4 block">
        <span className="sr-only">Search partners</span>
        <Search
          size={16}
          className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
        />
        <input
          type="search"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          placeholder="Search by name, partner ID or city..."
          className={searchInputClass}
        />
      </label>
      {errorBox}
      {partners === null ? (
        <div className="h-24 animate-pulse rounded-xl bg-[var(--light)]" />
      ) : shown.length === 0 ? (
        <p className="rounded-xl bg-[var(--light)] px-4 py-8 text-center text-sm text-[var(--muted)]">
          {partners.length === 0
            ? 'No approved partner offers this service.'
            : available.length === 0
              ? 'Every partner offering this service is booked for this slot. Change the slot.'
              : 'No partners match the search.'}
        </p>
      ) : (
        <ul className="m-0 min-h-0 flex-1 list-none space-y-2 overflow-y-auto p-0 pr-1">
          {shown.map((partner) => {
            // Time off and other bookings cannot be overridden; outside weekly hours can
            const blocked = !partner.isFree && partner.busyReason !== 'OUTSIDE_WEEKLY_HOURS';
            return (
              <li
                key={partner.partnerProfileId}
                className="flex flex-wrap items-center gap-3 rounded-xl border border-[var(--line)] px-4 py-3"
              >
                <UserRound size={18} className="text-[var(--blue)]" />
                <div className="min-w-0 flex-1">
                  <div className="font-bold text-[var(--ink)]">
                    {nameOf(partner) || 'Name not added'}
                    {partner.isPrimaryService && (
                      <span className="ml-2 rounded-full bg-[#EDF6FD] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--blue)]">
                        Main service
                      </span>
                    )}
                  </div>
                  <div className="text-sm text-[var(--muted)]">
                    {[
                      partner.publicId,
                      partner.city,
                      partner.experienceYears != null && `${partner.experienceYears} years`
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </div>
                </div>
                {!partner.isFree && (
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-bold ${
                      blocked ? 'bg-[#FDECEC] text-[#C53030]' : 'bg-[#FFF7DE] text-[#8A6508]'
                    }`}
                  >
                    {BUSY_REASONS[partner.busyReason] ?? partner.busyReason}
                  </span>
                )}
                <button
                  type="button"
                  disabled={saving !== null || blocked}
                  onClick={() => assign(partner)}
                  className={`${smallButton} bg-[var(--blue)] text-white hover:bg-[var(--navy)]`}
                >
                  {saving === partner.partnerProfileId ? 'Assigning…' : 'Assign'}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Modal>
  );
}

/**
 * Bookings > All Bookings > booking id (/admin/bookings/:bookingId): GET /api/bookings/{id}. The header: booking id
 * and status, the customer (name and id), the total and payment status, and the actions BOOKING_MANAGE write allows by
 * status: Confirm and Cancel (PENDING), Assign Partner and Cancel (CONFIRMED), Cancel (ASSIGNED / PARTNER ACCEPTED), and
 * Pause / Resume / Finish while working (the partner starts the work and reports being on the way from the partner
 * app). Left: the service, pricing and the service
 * address; right: the partner (Unassign until the work starts), payment and the booking history.
 */
export default function BookingDetailPage() {
  const { bookingId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('BOOKING_MANAGE', 'WRITE');
  const canFindPartners = can('PARTNER_AVAILABILITY_MANAGE', 'READ');
  const { label } = useLookupLabels(BOOKING_LOOKUPS);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [reasonFor, setReasonFor] = useState(null);
  const [assigning, setAssigning] = useState(false);
  const [toast, setToast] = useState(() => (location.state?.toast ? { ...location.state.toast, id: 'created' } : null));
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (location.state?.toast) navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate]);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/bookings/${bookingId}`)
      .then((loaded) => !cancelled && setDetail(loaded))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [authRequest, bookingId, reloadKey]);

  const booking = detail?.booking;
  useEffect(() => {
    document.title = `${booking?.publicId ?? 'Booking'} | NexHome Admin`;
  }, [booking]);

  const crumbs = [
    { label: 'Bookings' },
    { label: 'All Bookings', to: '/admin/bookings' },
    ...(booking ? [{ label: booking.publicId }] : [])
  ];
  if (error || !detail) {
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

  const run = async (request, message) => {
    setBusy(true);
    try {
      await request();
      showToast(message);
      setReloadKey((key) => key + 1);
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };
  const setStatus = (bookingStatus, reason = null) =>
    authRequest('/api/bookings/status/update', {
      method: 'PUT',
      body: { bookingId: booking.bookingId, bookingStatus, reason }
    });
  const timer = (eventType) =>
    run(
      () => authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType } }),
      `${TIMER_BUTTONS[eventType].label} recorded.`
    );
  const unassign = (assignment) =>
    run(
      () =>
        authRequest('/api/bookings/assignments/cancel', {
          method: 'PUT',
          body: { assignmentId: assignment.assignmentId }
        }),
      'Partner unassigned.'
    );
  const submitReason = async (formValues) => {
    const reason = formValues.reason.trim() || null;
    await authRequest('/api/bookings/cancel', { method: 'PUT', body: { bookingId: booking.bookingId, reason } });
    showToast('Booking cancelled.');
    setReasonFor(null);
    setReloadKey((key) => key + 1);
  };

  const status = booking.bookingStatus;
  const address = booking.address ?? {};
  const pricing = booking.pricing ?? {};
  const item = detail.items[0];
  // The option as booked (the item's variant snapshot; older bookings: the price snapshot's name)
  const variant = item?.variant ?? null;
  const optionName = variant?.name ?? pricing.variantName ?? null;
  // The booking's name is "Service - Option"; the option has its own field
  const serviceName = serviceNameOf(booking, optionName);
  const packageMinutes = variant?.durationMinutes ?? null;
  // The partner's assignment: Assigned (by staff), Accepted (by the partner), Started Working, Completed
  const holder = detail.assignments.find((assignment) =>
    ['ASSIGNED', 'ACCEPTED', 'STARTED', 'COMPLETED'].includes(assignment.assignmentStatus)
  );
  // The Partner card's audit line: "Assigned by Name (Role) on date at time"
  const assignedAt = holder && splitApiDateTime(holder.offeredAt);
  const start = splitApiDateTime(booking.scheduledStartAt);
  const end = splitApiDateTime(booking.scheduledEndAt);
  const startIso = isoOf(booking.scheduledStartAt);
  const endIso = isoOf(booking.scheduledEndAt);
  const durationMinutes =
    startIso && endIso ? Math.round((new Date(endIso) - new Date(startIso)) / 60000) : packageMinutes;
  const secondaryButton = `${actionButton} border border-[var(--line)] text-[var(--ink)] hover:bg-[var(--light)]`;
  const primaryButton = `${actionButton} bg-[var(--blue)] text-white hover:bg-[var(--navy)]`;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      {/* Header: booking, customer, total and payment, then the actions */}
      <section className={`mb-5 ${cardClass}`}>
        <div className="flex flex-wrap items-center gap-5">
          <span className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-2xl bg-[#EDF6FD] text-[var(--blue)]">
            <CalendarClock size={26} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="mb-0 text-[1.375rem] font-black text-[var(--navy)]">{booking.publicId}</h1>
              <Badge className={bookingStatusClass(status)}>{label('BOOKING_STATUS', status)}</Badge>
            </div>
            <div className="mt-1.5 text-[1.0625rem] text-[var(--muted)]">
              <Link to={`/admin/users/customers/${booking.customerProfileId}`} className={linkClass}>
                {booking.customerName ?? 'Name not added'}
              </Link>
              {' · '}
              <Link to={`/admin/users/customers/${booking.customerProfileId}`} className={linkClass}>
                {booking.customerPublicId}
              </Link>
            </div>
            <div className="mt-1 text-[0.8125rem] text-[var(--muted)]">Booked on {when(booking.creationDate)}</div>
          </div>
          <div className="text-right">
            <div className="text-[0.75rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">Total Amount</div>
            <div className="my-0.5 text-[1.75rem] font-black text-[var(--navy)]">
              {formatAmount(booking.totalAmount)}
            </div>
            <PaymentStatusBadge
              status={booking.paymentStatus}
              statusLabel={label('BOOKING_PAYMENT_STATUS', booking.paymentStatus)}
              method={booking.paymentMethod}
              methodLabel={label('PAYMENT_METHOD', booking.paymentMethod)}
              size="text-xs"
            />
          </div>
        </div>

        {canWrite && (CANCELLABLE.includes(status) || STAFF_TIMER_ACTIONS(status).length > 0) && (
          <div className="mt-5 flex flex-wrap gap-2.5 border-t border-[var(--line)] pt-4">
            {status === 'PENDING' && (
              <button
                type="button"
                disabled={busy}
                onClick={() => run(() => setStatus('CONFIRMED'), 'Booking confirmed.')}
                className={primaryButton}
              >
                <Check size={16} />
                Confirm Booking
              </button>
            )}
            {status === 'CONFIRMED' && canFindPartners && (
              <button type="button" disabled={busy} onClick={() => setAssigning(true)} className={primaryButton}>
                <UserRoundPlus size={16} />
                Assign Partner
              </button>
            )}
            {STAFF_TIMER_ACTIONS(status).map((event) => {
              const button = TIMER_BUTTONS[event];
              const Icon = button.icon;
              return (
                <button
                  key={event}
                  type="button"
                  disabled={busy}
                  onClick={() => timer(event)}
                  className={
                    event === 'STOP'
                      ? `${actionButton} bg-[var(--green)] text-white hover:bg-[var(--green-2)]`
                      : secondaryButton
                  }
                >
                  <Icon size={16} />
                  {button.label}
                </button>
              );
            })}
            {CANCELLABLE.includes(status) && (
              <button
                type="button"
                disabled={busy}
                onClick={() => setReasonFor('CANCEL')}
                className={`${actionButton} border border-[#F5C2C2] text-[#C53030] hover:bg-[#FDECEC]`}
              >
                <CircleX size={16} />
                Cancel Booking
              </button>
            )}
          </div>
        )}
        {booking.cancellationReason && (
          <p className="mb-0 mt-4 rounded-xl bg-[#FDECEC] px-4 py-3 text-sm text-[#C53030]">
            Cancelled: {booking.cancellationReason}
          </p>
        )}
      </section>

      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]">
        <div className="space-y-5">
          <Card title="Services" icon={Wrench}>
            <Link
              to={`/admin/services/${booking.serviceId}`}
              className={`mb-3 block border-b border-[var(--line)] pb-2.5 text-[0.9375rem] ${linkClass}`}
            >
              {serviceName}
            </Link>
            <div className="grid grid-cols-2 gap-x-5 gap-y-4 md:grid-cols-3">
              <Detail label="Category" value={detail.categoryName} />
              <Detail label="Option" value={optionName} />
              <Detail label="Option Type" value={pricing.pricingType && label('PRICING_TYPE', pricing.pricingType)} />
              <Detail label="Duration" value={durationMinutes ? `${durationMinutes} min` : null} />
              <Detail label="Booking Type" value={label('BOOKING_TYPE', booking.bookingType)} />
              <Detail
                label="Scheduled"
                value={start ? start.date : 'As soon as possible'}
                sub={start ? `${start.time}${end ? ` – ${end.time}` : ''}` : null}
              />
              <Detail label="Actual Start" value={when(booking.actualStartAt)} />
              <Detail label="Actual End" value={when(booking.actualEndAt)} />
              <Detail label="Payment Method" value={label('PAYMENT_METHOD', booking.paymentMethod)} />
            </div>
          </Card>

          <Card title="Pricing" icon={CreditCard}>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--line)]">
                    <th className={headClass}>Item</th>
                    <th className={`${headClass} text-right`}>Regular</th>
                    <th className={`${headClass} text-right`}>Price</th>
                    <th className={`${headClass} text-right`}>Line Total</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.items.map((line) => (
                    <tr key={line.bookingItemId} className="border-b border-[var(--line)]">
                      <td className="px-3 py-3 font-bold text-[var(--ink)]">
                        {line.variant?.name ?? optionName ?? serviceName}
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-[var(--muted)]">
                        {Number(pricing.regularPrice) > Number(line.unitPrice) ? (
                          <s>{formatAmount(pricing.regularPrice)}</s>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-right">{formatAmount(line.unitPrice)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right font-bold">
                        {formatAmount(line.lineTotal)}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  {[
                    // Regular amount (the subtotal before the discount), the discount off it, then the total
                    ['Regular Amount', formatAmount(booking.subtotalAmount), ''],
                    ['Discount', `− ${formatAmount(booking.discountAmount)}`, 'text-[var(--green-2)]'],
                    // The online payment fee (V35), added to the total (₹0 for cash bookings)
                    ['Gateway Charges', `+ ${formatAmount(booking.transactionFeeAmount ?? 0)}`, '']
                  ].map(([name, amount, tone], index) => (
                    <tr key={name}>
                      <td
                        colSpan={3}
                        className={`px-3 pb-1 text-right text-[var(--muted)] ${index === 0 ? 'pt-3' : 'pt-1'}`}
                      >
                        {name}
                      </td>
                      <td className={`whitespace-nowrap px-3 pb-1 text-right ${index === 0 ? 'pt-3' : 'pt-1'} ${tone}`}>
                        {amount}
                      </td>
                    </tr>
                  ))}
                  <tr className="text-base font-black text-[var(--navy)]">
                    <td colSpan={3} className="px-3 pt-3 text-right">
                      Total
                    </td>
                    <td className="whitespace-nowrap border-t border-[var(--line)] px-3 pt-3 text-right">
                      {formatAmount(booking.totalAmount)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
            {pricing.pricingType === 'HOURLY' && (
              <p className="mb-0 mt-3 text-xs text-[var(--muted)]">
                {pricing.workedMinutes != null
                  ? `Priced for ${pricing.workedMinutes} minutes worked (booked at ${formatAmount(pricing.bookedTotal)}).`
                  : `An estimate for ${pricing.billableMinutes ?? '—'} minutes; priced again for the time worked.`}
              </p>
            )}
          </Card>

          <Card title="Service Address" icon={MapPin}>
            <div className="text-sm leading-relaxed text-[var(--ink)]">
              <div className="mb-1 flex flex-wrap items-center gap-2">
                <span className="font-bold">{address.label ?? label('ADDRESS_TYPE', address.addressType)}</span>
                {address.label && address.addressType && (
                  <Badge className="bg-[var(--light)] text-[var(--muted)]">
                    {label('ADDRESS_TYPE', address.addressType)}
                  </Badge>
                )}
              </div>
              <div>{[address.addressLine1, address.addressLine2].filter(Boolean).join(', ')}</div>
              {address.landmark && <div className="text-[var(--muted)]">Near {address.landmark}</div>}
              <div>
                {[address.city, address.state].filter(Boolean).join(', ')}
                {address.postalCode && ` - ${address.postalCode}`}
              </div>
              {(address.contactName || address.mobileNumber) && (
                <div className="mt-1.5 text-[var(--muted)]">
                  {[address.contactName, address.mobileNumber].filter(Boolean).join(' · ')}
                </div>
              )}
            </div>
          </Card>
        </div>

        <div className="space-y-5">
          <Card
            title="Partner"
            icon={UserRound}
            action={
              holder && (
                <Badge className={assignmentStatusClass(holder.assignmentStatus)}>
                  {label('ASSIGNMENT_STATUS', holder.assignmentStatus)}
                </Badge>
              )
            }
          >
            {booking.partnerProfileId ? (
              <div className="flex flex-wrap items-center gap-3">
                <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-[var(--blue)] text-sm font-black text-white ring-2 ring-[#CFE2F7] ring-offset-2">
                  {(booking.partnerName ?? booking.partnerPublicId)
                    .split(' ')
                    .map((part) => part[0])
                    .slice(0, 2)
                    .join('')
                    .toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <Link to={`/admin/users/partners/${booking.partnerProfileId}`} className={`block ${linkClass}`}>
                    {booking.partnerName ?? 'Name not added'}
                  </Link>
                  <Link to={`/admin/users/partners/${booking.partnerProfileId}`} className={`text-sm ${linkClass}`}>
                    {booking.partnerPublicId}
                  </Link>
                </div>
                {canWrite && holder && UNASSIGNABLE.includes(status) && (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => unassign(holder)}
                    className={`${smallButton} border border-[#F5C2C2] text-[#C53030] hover:bg-[#FDECEC]`}
                  >
                    Unassign
                  </button>
                )}
                {holder && (
                  <p className="mb-0 mt-2 w-full border-t border-dashed border-[var(--line)] pt-3 text-sm text-[var(--muted)]">
                    Assigned by <span className="font-bold text-[var(--ink)]">{holder.assignedByName ?? 'unknown'}</span>
                    {holder.assignedByRole && ` (${holder.assignedByRole})`}
                    {assignedAt && (
                      <>
                        {' '}on <span className="font-bold text-[var(--ink)]">{assignedAt.date}</span> at{' '}
                        <span className="font-bold text-[var(--ink)]">{assignedAt.time}</span>
                      </>
                    )}
                    .
                  </p>
                )}
              </div>
            ) : (
              <p className="mb-0 rounded-xl bg-[var(--light)] px-4 py-4 text-sm text-[var(--muted)]">
                {status === 'PENDING' ? 'Confirm the booking before assigning a partner.' : 'No partner assigned yet.'}
              </p>
            )}
          </Card>

          {(can('PAYMENT_MANAGE', 'READ') || can('BOOKING_MANAGE', 'READ')) && (
            <BookingPaymentCard booking={booking} onChanged={reload} onMessage={showToast} />
          )}

          {(can('REVIEW_MANAGE', 'READ') || can('BOOKING_MANAGE', 'READ')) && (
            <BookingReviewCard booking={booking} onMessage={showToast} />
          )}

          <Card title="Booking History" icon={History}>
            <ol className="m-0 list-none space-y-3 p-0">
              {detail.history.map((row) => {
                // A row whose status stays the same is a payment event ("Payment received", "Cash collected")
                const paymentEvent = row.oldStatus === row.newStatus && row.reason;
                return (
                <li key={row.historyId} className="flex gap-3 text-sm">
                  <span className="mt-1 h-2.5 w-2.5 flex-shrink-0 rounded-full bg-[var(--green)]" />
                  <div>
                    <div className="font-bold text-[var(--ink)]">
                      {paymentEvent ? row.reason : isUnassigned(row) ? 'Unassigned' : label('BOOKING_STATUS', row.newStatus)}
                      {!paymentEvent && row.reason && row.reason !== PARTNER_UNASSIGNED && (
                        <span className="font-normal text-[var(--muted)]"> · {row.reason}</span>
                      )}
                    </div>
                    <div className="text-[0.8125rem] text-[var(--muted)]">
                      {when(row.changedAt)}
                      {row.changedByName && (
                        <>
                          {' '}
                          by <span className="font-bold text-[var(--ink)]">{row.changedByName}</span>
                          {row.changedByRole && ` · ${row.changedByRole}`}
                        </>
                      )}
                    </div>
                  </div>
                </li>
                );
              })}
            </ol>
          </Card>
        </div>
      </div>

      {reasonFor && (
        <FormModal
          title={REASON_ACTIONS[reasonFor].title}
          fields={[
            {
              key: 'reason',
              label: REASON_ACTIONS[reasonFor].label,
              type: 'textarea',
              required: REASON_ACTIONS[reasonFor].required,
              maxLength: 2000,
              rows: 3
            }
          ]}
          initial={{ reason: '' }}
          submitLabel={REASON_ACTIONS[reasonFor].submit}
          onSubmit={submitReason}
          onClose={() => setReasonFor(null)}
        />
      )}
      {assigning && (
        <AssignPartnerModal
          booking={booking}
          packageMinutes={packageMinutes}
          packageName={optionName}
          onClose={() => setAssigning(false)}
          onDone={(message) => {
            setAssigning(false);
            showToast(message);
            setReloadKey((key) => key + 1);
          }}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
