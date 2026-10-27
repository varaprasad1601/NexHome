import { Ban, CircleCheck, Clock, Plus, RotateCcw, Search, UserRound, UsersRound } from 'lucide-react';
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
import { CUSTOMER_ROLE, STATUS_FILTERS, accountStatusLabel, statusBadgeClass } from '../../data/staffOptions';
import useDebounced from '../../hooks/useDebounced';
import { formatNumber, splitApiDateTime } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

const STAT_TILES = [
  { key: 'total', label: 'Total Customers', icon: UsersRound, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'suspended', label: 'Suspended', icon: Ban, tile: 'bg-[#FDECEC] text-[#E53E3E]' }
];

/** The status changes of a customer's row menu, by current status (as the staff table's). */
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

/** The profile's name, or null while the customer has not added it. */
function fullName(customer) {
  return [customer.firstName, customer.lastName].filter(Boolean).join(' ') || null;
}

function initials(customer) {
  return [customer.firstName, customer.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/** The name column's avatar tints, as the staff table's. */
const AVATAR_TINTS = [
  'bg-[#EDF6FD] text-[var(--blue)]',
  'bg-[#E9F6EF] text-[var(--green-2)]',
  'bg-[#E8EEF6] text-[var(--navy)]',
  'bg-[#FFF7DE] text-[#8A6508]'
];

/**
 * User Management > Manage Customers: counts by status, search (mobile, email, name, customer id), a status filter
 * and the paged customer list from GET /api/users/customers and /customers/stats. With CUSTOMER_MANAGE write the
 * admin adds customers (Create user with the CUSTOMER role) and changes their status from the row menu; with read
 * only the list is view-only.
 */
export default function CustomersPage() {
  const { authRequest, can } = useAdminAuth();
  const canManage = can('CUSTOMER_MANAGE', 'WRITE');
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [accountStatus, setAccountStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [stats, setStats] = useState(null);
  const [pendingUserId, setPendingUserId] = useState(null);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    document.title = 'Customers | NexHome Admin';
  }, []);

  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const closeToast = useCallback(() => setToast(null), []);
  const reload = () => setReloadKey((key) => key + 1);

  // The page of customers for the current filters
  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (search) params.set('search', search);
    if (accountStatus) params.set('accountStatus', accountStatus);
    authRequest(`/api/users/customers?${params}`)
      .then((data) => {
        if (!cancelled) setList({ data, loading: false, error: '' });
      })
      .catch((err) => {
        if (!cancelled) setList({ data: EMPTY_PAGE, loading: false, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, page, search, accountStatus, reloadKey]);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/users/customers/stats')
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

  const onSearchChange = (e) => {
    setSearchText(e.target.value);
    setPage(0);
  };
  const onStatusFilter = (status) => {
    setAccountStatus(status);
    setPage(0);
    setList((current) => ({ ...current, loading: true }));
  };
  const resetFilters = () => {
    setSearchText('');
    setAccountStatus('');
    setPage(0);
  };

  const changeStatus = async (customer, status) => {
    setPendingUserId(customer.userId);
    try {
      await authRequest('/api/users/status/update', {
        method: 'PUT',
        body: { userId: customer.userId, accountStatus: status }
      });
      showToast(`${fullName(customer) ?? customer.mobileNumber} is now ${accountStatusLabel(status).toLowerCase()}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setPendingUserId(null);
    }
  };

  /** After Add Customer; `warning` when the account was created but setting it inactive failed. */
  const handleCreated = (user, warning) => {
    setAdding(false);
    if (warning) showToast(warning, 'error');
    else showToast(`${user.email ?? user.mobileNumber} was added as a customer.`);
    resetFilters();
    reload();
  };

  const rows = list.data.content;
  const firstRowNumber = list.data.page * list.data.size + 1;
  const columns = canManage ? 10 : 9;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'User Management' }, { label: 'Customers' }]}
        title="Customers"
        description="Everyone who books services on NexHome, with their account status."
        actions={
          canManage && (
            <button type="button" className={primaryButtonClass} onClick={() => setAdding(true)}>
              <Plus size={17} strokeWidth={2.4} />
              Add Customer
            </button>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        {STAT_TILES.map((tile) => {
          const Icon = tile.icon;
          return (
            <div key={tile.key} className={`flex min-w-[13.125rem] flex-1 items-center gap-4 xl:min-w-0 ${cardClass}`}>
              <span className={`flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full ${tile.tile}`}>
                <Icon size={22} strokeWidth={2} />
              </span>
              <div>
                <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{tile.label}</div>
                <div className="mt-1 h-7 text-[1.625rem] font-black leading-7 text-[var(--ink)]">
                  {stats ? formatNumber(stats[tile.key]) : <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]">
          <label className="relative">
            <span className="sr-only">Search customers</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={searchText}
              onChange={onSearchChange}
              placeholder="Search by name, email, customer ID or mobile..."
              className={searchInputClass}
            />
          </label>
          <Select ariaLabel="Status" value={accountStatus} onChange={onStatusFilter} options={[{ value: '', label: 'All Status' }, ...STATUS_FILTERS.map((status) => ({ value: status.code, label: status.label }))]} />
          <button type="button" className={resetButtonClass} onClick={resetFilters}>
            <RotateCcw size={15} />
            Reset
          </button>
        </div>

        {list.error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {list.error}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[56rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-12`}>#</th>
                    <th className={headClass}>Customer ID</th>
                    <th className={`${headClass} w-[11rem]`}>Name</th>
                    <th className={`${headClass} w-[12rem]`}>Email</th>
                    <th className={headClass}>Mobile</th>
                    <th className={headClass}>Status</th>
                    <th className={headClass}>Verified</th>
                    <th className={headClass}>Joined On</th>
                    <th className={headClass}>Last Login</th>
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
                        No customers match your search or filters.
                      </td>
                    </tr>
                  )}
                  {!list.loading &&
                    rows.map((customer, i) => {
                      const name = fullName(customer);
                      const joined = splitApiDateTime(customer.creationDate);
                      const lastLogin = splitApiDateTime(customer.lastLoginAt);
                      return (
                        <tr key={customer.userId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                          <td className="px-3 py-3 text-[var(--muted)]">{firstRowNumber + i}</td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {customer.publicId ? (
                              <Link
                                to={`/admin/users/customers/${customer.customerProfileId}`}
                                className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                                title={`${customer.publicId}: view profile`}
                              >
                                {customer.publicId}
                              </Link>
                            ) : (
                              <span className="text-[var(--muted)]">—</span>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            <span className="flex w-[11rem] min-w-0 items-center gap-2 whitespace-nowrap">
                              {name ? (
                                <span className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-black ${AVATAR_TINTS[i % AVATAR_TINTS.length]}`}>
                                  {initials(customer)}
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
                                <span className="min-w-0 truncate text-[var(--muted)]" title="Added by the customer in the NexHome app">
                                  Not added yet
                                </span>
                              )}
                            </span>
                          </td>
                          <td className="max-w-0 px-3 py-3">
                            {customer.email ? (
                              <span className="block truncate" title={customer.email}>
                                {customer.email}
                              </span>
                            ) : (
                              <span className="text-[#A8B6C6]">—</span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {customer.countryCode} {customer.mobileNumber}
                          </td>
                          <td className="px-3 py-3">
                            <span
                              className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(customer.accountStatus)}`}
                            >
                              {accountStatusLabel(customer.accountStatus)}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {customer.accountVerifiedDate ? (
                              <span className="font-bold text-[var(--green-2)]">Yes</span>
                            ) : (
                              <span className="text-[var(--muted)]">No</span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-3 py-3 text-[var(--muted)]">{joined?.date ?? '—'}</td>
                          <td className="whitespace-nowrap px-3 py-3">
                            {lastLogin ? (
                              <>
                                {lastLogin.date}
                                <span className="ml-2 text-[var(--muted)]">{lastLogin.time}</span>
                              </>
                            ) : (
                              <span className="text-[var(--muted)]">Never</span>
                            )}
                          </td>
                          {canManage && (
                            <td className="px-3 py-3 text-right">
                              <div className="flex justify-end">
                                <RowActions
                                  label={name ?? customer.mobileNumber}
                                  disabled={pendingUserId === customer.userId}
                                  actions={(STATUS_ACTIONS[customer.accountStatus] ?? STATUS_ACTIONS.BLOCKED).map((action) => ({
                                    label: action.label,
                                    danger: action.danger,
                                    onClick: () => changeStatus(customer, action.status)
                                  }))}
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
            {!list.loading && <Pagination pageData={list.data} noun="customers" onPageChange={setPage} />}
          </>
        )}
      </section>

      {adding && canManage && (
        <AddAdminModal
          role={CUSTOMER_ROLE}
          title="Add New Customer"
          submitLabel="Create Customer"
          noun="customer"
          note="The customer adds their name and other details in the NexHome app after signing in."
          onClose={() => setAdding(false)}
          onCreated={handleCreated}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
