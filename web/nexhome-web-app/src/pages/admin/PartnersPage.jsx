import {
  CircleCheck,
  Hourglass,
  Plus,
  Radio,
  RotateCcw,
  Search,
  UserRound,
  UserRoundPen,
  UsersRound
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import AddAdminModal from '../../components/admin/staff/AddAdminModal';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  headClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { NOT_STARTED, PARTNER_ROLE, availabilityDotClass, onboardingBadgeClass } from '../../data/partnerOptions';
import { STATUS_FILTERS, accountStatusLabel, statusBadgeClass } from '../../data/staffOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };
const LOOKUPS = ['PARTNER_ONBOARDING_STATUS', 'PARTNER_AVAILABILITY_STATUS'];

const STAT_TILES = [
  { key: 'total', label: 'Total Partners', icon: UsersRound, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'approved', label: 'Approved', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'awaitingReview', label: 'Awaiting Review', icon: Hourglass, tile: 'bg-[#F3ECFF] text-[#6B46C1]' },
  { key: 'notSubmitted', label: 'Not Submitted', icon: UserRoundPen, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'online', label: 'Online Now', icon: Radio, tile: 'bg-[#E9F6EF] text-[var(--green-2)]' }
];

/** The account status changes of a partner's row menu, by current status (as the staff and customer tables'). */
const STATUS_ACTIONS = {
  ACTIVE: [
    { status: 'DEACTIVATED', label: 'Deactivate' },
    { status: 'SUSPENDED', label: 'Suspend', danger: true }
  ],
  DEACTIVATED: [
    { status: 'ACTIVE', label: 'Activate' },
    { status: 'SUSPENDED', label: 'Suspend', danger: true }
  ],
  SUSPENDED: [{ status: 'ACTIVE', label: 'Activate' }],
  BLOCKED: [{ status: 'ACTIVE', label: 'Activate' }]
};

const AVATAR_TINTS = [
  'bg-[#EDF6FD] text-[var(--blue)]',
  'bg-[#E9F6EF] text-[var(--green-2)]',
  'bg-[#E8EEF6] text-[var(--navy)]',
  'bg-[#FFF7DE] text-[#8A6508]'
];

function fullName(partner) {
  return [partner.firstName, partner.lastName].filter(Boolean).join(' ') || null;
}

function initials(partner) {
  return [partner.firstName, partner.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/**
 * User Management > Manage Partners: counts (total, approved, awaiting review, not submitted, online), search (mobile,
 * email, name, partner id), onboarding and account status filters, and the paged list from GET /api/users/partners
 * and /partners/stats. A partner id links to the partner's profile; partners who have not started onboarding have
 * none yet. With PARTNER_MANAGE write the admin adds partners (Create user with the PARTNER role) and changes their
 * account status from the row menu; with read only the list is view-only.
 */
export default function PartnersPage() {
  const { authRequest, can } = useAdminAuth();
  const canManage = can('PARTNER_MANAGE', 'WRITE');
  const { values, label } = useLookupLabels(LOOKUPS);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [onboardingStatus, setOnboardingStatus] = useState('');
  const [accountStatus, setAccountStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [stats, setStats] = useState(null);
  const [pendingUserId, setPendingUserId] = useState(null);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    document.title = 'Partners | NexHome Admin';
  }, []);

  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const closeToast = useCallback(() => setToast(null), []);
  const reload = () => setReloadKey((key) => key + 1);

  // The page of partners for the current filters
  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (search) params.set('search', search);
    if (onboardingStatus) params.set('onboardingStatus', onboardingStatus);
    if (accountStatus) params.set('accountStatus', accountStatus);
    authRequest(`/api/users/partners?${params}`)
      .then((data) => {
        if (!cancelled) setList({ data, loading: false, error: '' });
      })
      .catch((err) => {
        if (!cancelled) setList({ data: EMPTY_PAGE, loading: false, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, page, search, onboardingStatus, accountStatus, reloadKey]);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/users/partners/stats')
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch(() => {
        if (!cancelled) setStats(null);
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, reloadKey]);

  /** Any filter change starts again from the first page, with the loading rows. */
  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
    setList((current) => ({ ...current, loading: true }));
  };
  const resetFilters = () => {
    setSearchText('');
    setOnboardingStatus('');
    setAccountStatus('');
    setPage(0);
  };

  const changeStatus = async (partner, status) => {
    setPendingUserId(partner.userId);
    try {
      await authRequest('/api/users/status/update', {
        method: 'PUT',
        body: { userId: partner.userId, accountStatus: status }
      });
      showToast(`${fullName(partner) ?? partner.mobileNumber} is now ${accountStatusLabel(status).toLowerCase()}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setPendingUserId(null);
    }
  };

  const handleCreated = (user, warning) => {
    setAdding(false);
    if (warning) showToast(warning, 'error');
    else showToast(`${user.email ?? user.mobileNumber} was added as a partner.`);
    resetFilters();
    reload();
  };

  const rows = list.data.content;
  const firstRowNumber = list.data.page * list.data.size + 1;
  const columns = canManage ? 10 : 9;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'User Management' }, { label: 'Partners' }]}
        title="Partners"
        description="Service professionals on NexHome, their onboarding and their account status."
        actions={
          canManage && (
            <button type="button" className={primaryButtonClass} onClick={() => setAdding(true)}>
              <Plus size={17} strokeWidth={2.4} />
              Add Partner
            </button>
          )
        }
      />

      {/* Always one row: five columns on wide screens, a sideways-scrolling strip on smaller ones */}
      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-5 xl:overflow-visible xl:px-0 xl:pb-0">
        {STAT_TILES.map((tile) => {
          const Icon = tile.icon;
          return (
            <div key={tile.key} className={`flex min-w-[12.5rem] flex-1 items-center gap-4 xl:min-w-0 ${cardClass}`}>
              <span className={`flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full ${tile.tile}`}>
                <Icon size={22} strokeWidth={2} />
              </span>
              <div>
                <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{tile.label}</div>
                <div className="mt-1 h-7 text-[1.625rem] font-black leading-7 text-[var(--ink)]">
                  {stats ? (
                    formatNumber(stats[tile.key])
                  ) : (
                    <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_13rem_11rem_auto]">
          <label className="relative">
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
              placeholder="Search by name, email, partner ID or mobile..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Onboarding"
            value={onboardingStatus}
            onChange={changeFilter(setOnboardingStatus)}
            options={[
              { value: '', label: 'All Onboarding' },
              { value: NOT_STARTED, label: 'Not started' },
              ...(values.PARTNER_ONBOARDING_STATUS ?? []).map((value) => ({
                value: value.valueCode,
                label: value.displayName
              }))
            ]}
          />
          <Select
            ariaLabel="Account status"
            value={accountStatus}
            onChange={changeFilter(setAccountStatus)}
            options={[
              { value: '', label: 'All Status' },
              ...STATUS_FILTERS.map((status) => ({ value: status.code, label: status.label }))
            ]}
          />
          <button type="button" className={resetButtonClass} onClick={resetFilters}>
            <RotateCcw size={15} />
            Reset
          </button>
        </div>

        {list.error ? (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {list.error}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[60rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-12`}>#</th>
                    <th className={headClass}>Partner ID</th>
                    <th className={`${headClass} w-[11rem]`}>Name</th>
                    <th className={headClass}>Mobile</th>
                    <th className={headClass}>Service</th>
                    <th className={headClass}>City</th>
                    <th className={headClass}>Onboarding</th>
                    <th className={headClass}>Availability</th>
                    <th className={headClass}>Account</th>
                    {canManage && <th className={`${headClass} text-right`}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {list.loading &&
                    Array.from({ length: 5 }, (_, i) => (
                      <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                        <td colSpan={columns} className="px-3 py-4">
                          <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                        </td>
                      </tr>
                    ))}
                  {!list.loading && rows.length === 0 && (
                    <tr className="border-t border-[var(--line)]">
                      <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                        No partners match your search or filters.
                      </td>
                    </tr>
                  )}
                  {!list.loading &&
                    rows.map((partner, i) => {
                      const name = fullName(partner);
                      return (
                        <tr
                          key={partner.userId}
                          className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                        >
                          <td className="px-3 py-3 text-[var(--muted)]">{firstRowNumber + i}</td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {partner.publicId ? (
                              <Link
                                to={`/admin/users/partners/${partner.partnerProfileId}`}
                                className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                                title={`${partner.publicId}: view profile`}
                              >
                                {partner.publicId}
                              </Link>
                            ) : (
                              <span className="text-[var(--muted)]">—</span>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            <span className="flex w-[11rem] min-w-0 items-center gap-2 whitespace-nowrap">
                              {name ? (
                                <span
                                  className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-black ${AVATAR_TINTS[i % AVATAR_TINTS.length]}`}
                                >
                                  {initials(partner)}
                                </span>
                              ) : (
                                <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-[var(--light)] text-[#A8B6C6]">
                                  <UserRound size={15} strokeWidth={2} />
                                </span>
                              )}
                              {name ? (
                                <span className="min-w-0 truncate font-bold" title={name}>
                                  {name}
                                </span>
                              ) : (
                                <span
                                  className="min-w-0 truncate text-[var(--muted)]"
                                  title={
                                    partner.publicId
                                      ? 'Added by the partner during onboarding'
                                      : 'The partner has no onboarding profile yet'
                                  }
                                >
                                  {partner.publicId ? 'Not added yet' : 'Profile not started'}
                                </span>
                              )}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {partner.countryCode} {partner.mobileNumber}
                          </td>
                          <td className="max-w-0 px-3 py-3">
                            {partner.mainServiceCategoryName ? (
                              <span className="block max-w-[9rem] truncate" title={partner.mainServiceCategoryName}>
                                {partner.mainServiceCategoryName}
                              </span>
                            ) : (
                              <span className="text-[#A8B6C6]">—</span>
                            )}
                          </td>
                          <td className="max-w-0 px-3 py-3">
                            {partner.city ? (
                              <span className="block max-w-[7rem] truncate" title={partner.city}>
                                {partner.city}
                              </span>
                            ) : (
                              <span className="text-[#A8B6C6]">—</span>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            {partner.onboardingStatus ? (
                              <span
                                className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${onboardingBadgeClass(partner.onboardingStatus)}`}
                              >
                                {label('PARTNER_ONBOARDING_STATUS', partner.onboardingStatus)}
                              </span>
                            ) : (
                              <span className="whitespace-nowrap rounded-full bg-[var(--light)] px-2.5 py-1 text-[0.75rem] font-bold text-[#8A9AAD]">
                                Not started
                              </span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {partner.onboardingStatus === 'APPROVED' ? (
                              <span className="flex items-center gap-2">
                                <span
                                  className={`h-2 w-2 rounded-full ${availabilityDotClass(partner.availabilityStatus)}`}
                                />
                                {label('PARTNER_AVAILABILITY_STATUS', partner.availabilityStatus)}
                              </span>
                            ) : (
                              <span className="text-[#A8B6C6]">—</span>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            <span
                              className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(partner.accountStatus)}`}
                            >
                              {accountStatusLabel(partner.accountStatus)}
                            </span>
                          </td>
                          {canManage && (
                            <td className="px-3 py-3 text-right">
                              <div className="flex justify-end">
                                <RowActions
                                  label={name ?? partner.mobileNumber}
                                  disabled={pendingUserId === partner.userId}
                                  actions={(STATUS_ACTIONS[partner.accountStatus] ?? STATUS_ACTIONS.BLOCKED).map(
                                    (action) => ({
                                      label: action.label,
                                      danger: action.danger,
                                      onClick: () => changeStatus(partner, action.status)
                                    })
                                  )}
                                />
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
            {!list.loading && <Pagination pageData={list.data} noun="partners" onPageChange={changeFilter(setPage)} />}
          </>
        )}
      </section>

      {adding && canManage && (
        <AddAdminModal
          role={PARTNER_ROLE}
          title="Add New Partner"
          submitLabel="Create Partner"
          noun="partner"
          note="The partner completes their onboarding profile in the NexHome Partner app after signing in."
          onClose={() => setAdding(false)}
          onCreated={handleCreated}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
