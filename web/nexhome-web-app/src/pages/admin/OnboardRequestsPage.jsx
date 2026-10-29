import {
  CircleCheckBig,
  CircleX,
  ClipboardCheck,
  MessageSquareWarning,
  Search,
  ShieldCheck,
  UserRound
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Toast from '../../components/admin/common/Toast';
import { cardClass, headClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import useDebounced from '../../hooks/useDebounced';
import { formatNumber } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/** The queues, by onboarding status: what is waiting for staff first. */
const QUEUES = [
  {
    status: 'UNDER_REVIEW',
    label: 'Under Review',
    text: 'Submitted and waiting for a decision',
    icon: ClipboardCheck,
    tone: 'bg-[#EDF6FD] text-[var(--blue)]',
    ring: 'ring-[var(--blue)]'
  },
  {
    status: 'CHANGES_REQUESTED',
    label: 'Changes Requested',
    text: 'Back with the partner',
    icon: MessageSquareWarning,
    tone: 'bg-[#FFF7DE] text-[#B7860B]',
    ring: 'ring-[#E0A800]'
  },
  {
    status: 'APPROVED',
    label: 'Approved',
    text: 'Onboarded and ready for jobs',
    icon: CircleCheckBig,
    tone: 'bg-[#E9F6EF] text-[var(--green)]',
    ring: 'ring-[var(--green)]'
  },
  {
    status: 'REJECTED',
    label: 'Rejected',
    text: 'Not onboarded',
    icon: CircleX,
    tone: 'bg-[#FDECEC] text-[#E53E3E]',
    ring: 'ring-[#E53E3E]'
  }
];

const CHECKS = [
  { key: 'accountVerified', label: 'Profile' },
  { key: 'documentsVerified', label: 'Documents' },
  { key: 'bankAccVerified', label: 'Bank account' }
];

const fullName = (profile) => [profile.firstName, profile.lastName].filter(Boolean).join(' ');
const initials = (profile) =>
  [profile.firstName, profile.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase();

/**
 * User Management > Partners > Onboard Requests (/admin/users/partners/onboarding, from the partner list's button):
 * the onboarding queues as clickable tiles with counts (Under Review first, then Changes Requested, Approved,
 * Rejected), a search, and the paged list of the chosen queue from GET /api/partners/profiles?onboardingStatus=
 * (PARTNER_ONBOARDING_MANAGE read): partner, main service, city, the three verification checks, and Review / Open,
 * which opens the partner's onboarding screen. A decision made there comes back here with its toast.
 */
export default function OnboardRequestsPage() {
  const { authRequest } = useAdminAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [status, setStatus] = useState('UNDER_REVIEW');
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [page, setPage] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [counts, setCounts] = useState({});
  const [categories, setCategories] = useState([]);
  // The onboarding screen hands over the result of a decision
  const [toast, setToast] = useState(() => (location.state?.toast ? { ...location.state.toast, id: 'decided' } : null));
  const closeToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    document.title = 'Onboard Requests | NexHome Admin';
    if (location.state?.toast) navigate(location.pathname, { replace: true, state: null });
    // Once, on arrival
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all(
      QUEUES.map((queue) =>
        authRequest(`/api/partners/profiles?size=1&onboardingStatus=${queue.status}`)
          .then((data) => [queue.status, data.totalElements])
          .catch(() => [queue.status, null])
      )
    ).then((entries) => !cancelled && setCounts(Object.fromEntries(entries)));
    authRequest('/api/catalog/categories?includeInactive=true')
      .then((data) => !cancelled && setCategories(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), onboardingStatus: status });
    if (search) params.set('search', search);
    authRequest(`/api/partners/profiles?${params}`)
      .then((data) => !cancelled && setList({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, status, search, page]);

  const chooseQueue = (next) => {
    // The open queue's first page again changes nothing, so no reload would end the loading state
    if (next === status && page === 0) return;
    setStatus(next);
    setPage(0);
    setList((current) => ({ ...current, loading: true }));
  };
  const categoryName = (id) => categories.find((category) => category.serviceCategoryId === id)?.name;
  const queue = QUEUES.find((item) => item.status === status);
  const rows = list.data.content;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[
          { label: 'User Management' },
          { label: 'Partners', to: '/admin/users/partners' },
          { label: 'Onboard Requests' }
        ]}
        title="Onboard Requests"
        description="Review submitted partner profiles: verify their details, then approve, ask for changes or reject."
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {QUEUES.map((item) => {
          const Icon = item.icon;
          const active = item.status === status;
          return (
            <button
              key={item.status}
              type="button"
              onClick={() => chooseQueue(item.status)}
              aria-pressed={active}
              className={`flex items-center gap-4 text-left transition ${cardClass} ${
                active ? `ring-2 ${item.ring}` : 'hover:border-[#C7E3FC]'
              }`}
            >
              <span className={`flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full ${item.tone}`}>
                <Icon size={22} />
              </span>
              <span className="min-w-0">
                <span className="block text-[0.8125rem] font-bold text-[var(--muted)]">{item.label}</span>
                <span className="mt-0.5 block text-[1.625rem] font-black leading-7 text-[var(--ink)]">
                  {counts[item.status] == null ? '—' : formatNumber(counts[item.status])}
                </span>
                <span className="block truncate text-xs text-[var(--muted)]">{item.text}</span>
              </span>
            </button>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="mb-0 flex items-center gap-2 text-[1.0625rem] font-black text-[var(--navy)]">
            {queue.label}
            <span className="rounded-full bg-[var(--light)] px-2.5 text-[0.8125rem] text-[var(--muted)]">
              {list.loading ? '…' : formatNumber(list.data.totalElements)}
            </span>
          </h2>
          <label className="relative w-full sm:w-80">
            <span className="sr-only">Search partners</span>
            <Search
              size={16}
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
            />
            <input
              type="search"
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value);
                setPage(0);
              }}
              placeholder="Search by name or partner id..."
              className={searchInputClass}
            />
          </label>
        </div>

        {list.error ? (
          <div className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {list.error}
          </div>
        ) : list.loading ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-14 animate-pulse rounded-xl bg-[var(--light)]" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center py-14 text-center">
            <span className={`mb-3 flex h-14 w-14 items-center justify-center rounded-full ${queue.tone}`}>
              <queue.icon size={24} />
            </span>
            <div className="font-bold text-[var(--ink)]">
              {status === 'UNDER_REVIEW' ? 'No requests waiting' : `No ${queue.label.toLowerCase()} partners`}
            </div>
            <div className="text-sm text-[var(--muted)]">
              {status === 'UNDER_REVIEW'
                ? 'Profiles show here once they are submitted for review.'
                : 'Nothing matches your search.'}
            </div>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[56rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-12`}>#</th>
                    <th className={headClass}>Partner ID</th>
                    <th className={headClass}>Partner Name</th>
                    <th className={headClass}>Main Service</th>
                    <th className={headClass}>City</th>
                    <th className={headClass}>Verification</th>
                    {status === 'CHANGES_REQUESTED' || status === 'REJECTED' ? (
                      <th className={headClass}>Reason</th>
                    ) : null}
                    <th className={`${headClass} text-right`}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((profile, i) => {
                    const done = CHECKS.filter((check) => profile[check.key]).length;
                    return (
                      <tr
                        key={profile.partnerProfileId}
                        className="border-t border-[var(--line)] text-[var(--ink)] hover:bg-[#FAFCFE]"
                      >
                        <td className="px-3 py-3 text-[var(--muted)]">{list.data.page * list.data.size + i + 1}</td>
                        <td className="whitespace-nowrap px-3 py-3">
                          <Link
                            to={`/admin/users/partners/${profile.partnerProfileId}`}
                            className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                            title={`${profile.publicId}: view the partner`}
                          >
                            {profile.publicId}
                          </Link>
                        </td>
                        <td className="px-3 py-3">
                          <span className="flex items-center gap-3">
                            <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-xs font-black text-[var(--blue)]">
                              {initials(profile) || <UserRound size={15} />}
                            </span>
                            {fullName(profile) ? (
                              <span className="min-w-0 truncate font-bold">{fullName(profile)}</span>
                            ) : (
                              <span className="text-[var(--muted)]">Not added yet</span>
                            )}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {categoryName(profile.mainServiceCategoryId) ?? '—'}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">{profile.city ?? '—'}</td>
                        <td className="px-3 py-3">
                          <span
                            className="flex items-center gap-2"
                            title={CHECKS.map(
                              (check) => `${check.label}: ${profile[check.key] ? 'verified' : 'pending'}`
                            ).join(', ')}
                          >
                            <span className="flex gap-1">
                              {CHECKS.map((check) => (
                                <span
                                  key={check.key}
                                  className={`h-2 w-6 rounded-full ${profile[check.key] ? 'bg-[var(--green)]' : 'bg-[#DCE4EC]'}`}
                                />
                              ))}
                            </span>
                            <span
                              className={`text-xs font-bold ${done === 3 ? 'text-[var(--green-2)]' : 'text-[var(--muted)]'}`}
                            >
                              {done}/3
                            </span>
                          </span>
                        </td>
                        {status === 'CHANGES_REQUESTED' || status === 'REJECTED' ? (
                          <td className="max-w-0 px-3 py-3">
                            <span className="block truncate text-[var(--muted)]" title={profile.rejectionReason ?? ''}>
                              {profile.rejectionReason ?? '—'}
                            </span>
                          </td>
                        ) : null}
                        <td className="px-3 py-3 text-right">
                          <Link
                            to={`/admin/users/partners/${profile.partnerProfileId}/onboard`}
                            className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3.5 py-1.5 text-[0.8125rem] font-bold no-underline ${
                              status === 'UNDER_REVIEW'
                                ? 'bg-[var(--blue)] text-white hover:bg-[var(--navy)]'
                                : 'border border-[var(--line)] text-[var(--blue)] hover:bg-[#EDF6FD]'
                            }`}
                          >
                            <ShieldCheck size={14} />
                            {status === 'UNDER_REVIEW' ? 'Review' : 'Open'}
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination pageData={list.data} noun="partners" onPageChange={setPage} />
          </>
        )}
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
