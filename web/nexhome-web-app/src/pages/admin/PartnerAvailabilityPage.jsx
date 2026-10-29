import { CalendarSearch, Clock, Search, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import DatePicker from '../../components/admin/common/DatePicker';
import Select from '../../components/admin/common/Select';
import { cardClass, headClass, linkClass, primaryButtonClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import useLookupLabels from '../../hooks/useLookupLabels';
import { TIME_OPTIONS, timeLabel, toInstant, todayInIndia } from '../../utils/schedule';

const LOOKUPS = ['PARTNER_AVAILABILITY_STATUS'];
const crumbs = [{ label: 'Bookings' }, { label: 'Partner Availability' }];

/**
 * Bookings > Partner Availability (/admin/bookings/availability, PARTNER_AVAILABILITY_MANAGE read): which partners
 * are free for a service at a time (GET /api/availability/partners): approved, offering it, working then by their
 * weekly slots, not on time off and without another booking then. (Holding a slot is not used for now: partners
 * are assigned from the booking page.)
 */
export default function PartnerAvailabilityPage() {
  const { authRequest } = useAdminAuth();
  const { label } = useLookupLabels(LOOKUPS);
  const [services, setServices] = useState([]);
  const [query, setQuery] = useState({ serviceId: '', date: todayInIndia(), start: '10:00', end: '12:00' });
  const [result, setResult] = useState(null); // { partners, query } after a search
  const [error, setError] = useState('');
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    document.title = 'Partner Availability | NexHome Admin';
    authRequest('/api/catalog/services?size=100')
      .then((page) => setServices(page.content))
      .catch(() => setServices([]));
  }, [authRequest]);

  const set = (key) => (value) => setQuery((current) => ({ ...current, [key]: value }));
  const startsAt = toInstant(query.date, query.start);
  const endsAt = toInstant(query.date, query.end);
  const ready = query.serviceId && startsAt && endsAt && new Date(endsAt) > new Date(startsAt);

  const search = async (current = query) => {
    setSearching(true);
    setError('');
    try {
      const params = new URLSearchParams({
        serviceId: current.serviceId,
        startsAt: toInstant(current.date, current.start),
        endsAt: toInstant(current.date, current.end)
      });
      const partners = await authRequest(`/api/availability/partners?${params}`);
      setResult({ partners, query: current });
    } catch (err) {
      setError(err.message);
      setResult(null);
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={crumbs}
        title="Partner Availability"
        description="Find the partners free for a service at a time."
      />

      <section className={`mb-5 ${cardClass}`}>
        <div className="grid grid-cols-1 items-end gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold text-[var(--ink)]">Service</span>
            <Select
              value={query.serviceId}
              onChange={set('serviceId')}
              options={services.map((service) => ({ value: String(service.serviceId), label: service.name }))}
              placeholder="Select a service"
              searchable
              searchPlaceholder="Search services…"
            />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold text-[var(--ink)]">Date</span>
            <DatePicker value={query.date} onChange={set('date')} min={todayInIndia()} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold text-[var(--ink)]">From</span>
            <Select value={query.start} onChange={set('start')} options={TIME_OPTIONS} searchable searchPlaceholder="e.g. 10:30" />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold text-[var(--ink)]">To</span>
            <Select value={query.end} onChange={set('end')} options={TIME_OPTIONS} searchable searchPlaceholder="e.g. 6:00" />
          </label>
          <button
            type="button"
            onClick={() => search()}
            disabled={!ready || searching}
            className={`${primaryButtonClass} justify-center disabled:cursor-not-allowed disabled:opacity-45`}
          >
            <Search size={16} />
            {searching ? 'Searching…' : 'Find Partners'}
          </button>
        </div>
        {query.serviceId && startsAt && endsAt && !ready && (
          <p className="mb-0 mt-3 text-sm text-[#C53030]">The end time must be after the start time.</p>
        )}
      </section>

      {error && (
        <div className="mb-5 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {error}
        </div>
      )}

      {result ? (
        <section className={cardClass}>
          <h2 className="mb-1 text-[1.0625rem] font-black text-[var(--navy)]">
            {result.partners.length} {result.partners.length === 1 ? 'partner' : 'partners'} free
          </h2>
          <p className="mb-4 flex items-center gap-1.5 text-sm text-[var(--muted)]">
            <Clock size={14} />
            {services.find((service) => String(service.serviceId) === result.query.serviceId)?.name} ·{' '}
            {result.query.date} · {timeLabel(result.query.start)} – {timeLabel(result.query.end)}
          </p>
          {result.partners.length === 0 ? (
            <p className="rounded-xl bg-[var(--light)] px-4 py-8 text-center text-sm text-[var(--muted)]">
              Nobody offering this service is free then. Try another time.
            </p>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[48rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-12`}>#</th>
                    <th className={headClass}>Partner ID</th>
                    <th className={headClass}>Partner Name</th>
                    <th className={headClass}>City</th>
                    <th className={headClass}>Experience</th>
                    <th className={headClass}>Right Now</th>
                  </tr>
                </thead>
                <tbody>
                  {result.partners.map((partner, index) => {
                    const name = [partner.firstName, partner.lastName].filter(Boolean).join(' ');
                    return (
                      <tr key={partner.partnerProfileId} className="border-t border-[var(--line)] text-[var(--ink)]">
                        <td className="px-3 py-3 text-[var(--muted)]">{index + 1}</td>
                        <td className="whitespace-nowrap px-3 py-3">
                          <Link to={`/admin/users/partners/${partner.partnerProfileId}`} className={linkClass}>
                            {partner.publicId}
                          </Link>
                        </td>
                        <td className="px-3 py-3">
                          <span className="flex items-center gap-2">
                            <UserRound size={15} className="text-[var(--blue)]" />
                            <span className="font-bold">{name || 'Not added yet'}</span>
                            {partner.isPrimaryService && (
                              <span className="rounded-full bg-[#EDF6FD] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--blue)]">
                                Main service
                              </span>
                            )}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">{partner.city ?? '—'}</td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {partner.experienceYears == null ? '—' : `${partner.experienceYears} years`}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {label('PARTNER_AVAILABILITY_STATUS', partner.availabilityStatus) ?? '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : (
        !error && (
          <section className={`${cardClass} flex flex-col items-center py-14 text-center`}>
            <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
              <CalendarSearch size={24} />
            </span>
            <p className="mb-0 text-sm text-[var(--muted)]">Choose a service, a date and a time to see who is free.</p>
          </section>
        )
      )}
    </div>
  );
}
