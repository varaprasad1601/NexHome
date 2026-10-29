import { Ban, CircleCheck, Clock, Plus, RotateCcw, Search, UsersRound } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import AddAdminModal from '../../components/admin/staff/AddAdminModal';
import StaffTable from '../../components/admin/staff/StaffTable';
import { useAdminAuth } from '../../context/adminAuth';
import {
  ADMIN_ROLE,
  STATUS_FILTERS,
  SUB_ADMIN_ROLE,
  SUPER_USER_ROLE,
  accountStatusLabel,
  canManageStaffRole
} from '../../data/staffOptions';
import useDebounced from '../../hooks/useDebounced';
import { formatNumber } from '../../utils/format';

const PAGE_SIZE = 10;
/** The staff pages built on this component: each lists one role. */
const STAFF_PAGES = {
  admins: {
    role: ADMIN_ROLE,
    path: '/admin/users/admins',
    title: 'Admins',
    description: 'Manage all admin users and their access to the platform.',
    noun: 'admins',
    totalLabel: 'Total Admins',
    addLabel: 'Add Admin',
    addTitle: 'Add New Admin',
    addSubmit: 'Create Admin',
    added: 'an admin'
  },
  superAdmins: {
    role: SUPER_USER_ROLE,
    path: '/admin/users/super-admins',
    title: 'Super Admins',
    description: 'Manage the super users, who have full access to the platform.',
    noun: 'super admins',
    totalLabel: 'Total Super Admins',
    addLabel: 'Add Super Admin',
    addTitle: 'Add New Super Admin',
    addSubmit: 'Create Super Admin',
    added: 'a super admin',
    superUserOnly: true
  },
  subAdmins: {
    role: SUB_ADMIN_ROLE,
    path: '/admin/users/sub-admins',
    title: 'Sub Admins',
    description: 'Manage the sub admins, who have read-only access to most of the platform.',
    noun: 'sub admins',
    totalLabel: 'Total Sub Admins',
    addLabel: 'Add Sub Admin',
    addTitle: 'Add New Sub Admin',
    addSubmit: 'Create Sub Admin',
    added: 'a sub admin'
  }
};
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

const STAT_TILES = [
  { key: 'total', label: null, icon: UsersRound, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'suspended', label: 'Suspended', icon: Ban, tile: 'bg-[#FDECEC] text-[#E53E3E]' }
];

/**
 * User Management > Admins (ADMIN accounts), with variant "superAdmins" Super Admins (SUPER_USER accounts, for super
 * users only; the backend hides them from everyone else and AdminLayout shows others No access), or with "subAdmins" Sub Admins: counts by status, search,
 * a status filter, the row menu and Add Admin (the last two only with write access, see canManage). Data from GET /api/users/staff and /staff/stats.
 */
export default function AdminsPage({ variant = 'admins' }) {
  const config = STAFF_PAGES[variant];
  const roleCode = config.role.code;
  const { session, authRequest, can, isSuperUser } = useAdminAuth();
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [accountStatus, setAccountStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [stats, setStats] = useState(null);
  const [selected, setSelected] = useState(() => new Set());
  const [pendingUserId, setPendingUserId] = useState(null);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState(null);


  useEffect(() => {
    document.title = `${config.title} | NexHome Admin`;
  }, [config.title]);

  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  const closeToast = useCallback(() => setToast(null), []);

  // The page of staff for the current filters
  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), roleCode });
    if (search) params.set('search', search);
    if (accountStatus) params.set('accountStatus', accountStatus);
    authRequest(`/api/users/staff?${params}`)
      .then((data) => {
        if (!cancelled) setList({ data, loading: false, error: '' });
      })
      .catch((err) => {
        if (!cancelled) setList({ data: EMPTY_PAGE, loading: false, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, roleCode, page, search, accountStatus, reloadKey]);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/users/staff/stats?roleCode=${roleCode}`)
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch(() => {
        if (!cancelled) setStats(null);
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, roleCode, reloadKey]);

  /** Another page of the list, with the loading rows (the same page changes nothing, so it is not reloaded). */
  const goToPage = (next) => {
    if (next === page) return;
    setPage(next);
    setList((current) => ({ ...current, loading: true }));
  };
  /** Any filter change starts again from the first page, with the loading rows. */
  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
    setList((current) => ({ ...current, loading: true }));
  };
  const onSearchChange = (e) => {
    setSearchText(e.target.value);
    setPage(0);
  };
  const resetFilters = () => {
    setSearchText('');
    setAccountStatus('');
    setPage(0);
  };
  const reload = () => setReloadKey((key) => key + 1);

  const handleStatusChange = async (staff, status) => {
    setPendingUserId(staff.userId);
    try {
      await authRequest('/api/users/status/update', {
        method: 'PUT',
        body: { userId: staff.userId, accountStatus: status }
      });
      const name = [staff.firstName, staff.lastName].filter(Boolean).join(' ') || staff.mobileNumber;
      showToast(`${name} is now ${accountStatusLabel(status).toLowerCase()}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setPendingUserId(null);
    }
  };

  const handleSelect = (userIds, checked) => {
    setSelected((current) => {
      const next = new Set(current);
      userIds.forEach((id) => (checked ? next.add(id) : next.delete(id)));
      return next;
    });
  };

  /** After Add Admin; `warning` when the account was created but a follow-up step failed. */
  const handleCreated = (user, warning) => {
    setAdding(false);
    if (warning) showToast(warning, 'error');
    else showToast(`${user.email ?? user.mobileNumber} was added as ${config.added}.`);
    resetFilters();
    reload();
  };


  // Who reads the list is decided by the sidebar link (ADMIN_MANAGE / SUB_ADMIN_MANAGE read; Super Admins: super
  // users only). Adding accounts and changing their status take write on the same function (super users for Super
  // Admins); without it the list is read-only: no Add button and no row menu.
  const canManage = canManageStaffRole(roleCode, can, isSuperUser);

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'User Management' }, { label: config.title }]}
        title={config.title}
        description={config.description}
        actions={
          canManage && (
            <button
              type="button"
              className="flex items-center gap-2 rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_16px_rgba(11,67,139,0.25)] hover:bg-[var(--navy)]"
              onClick={() => setAdding(true)}
            >
              <Plus size={17} strokeWidth={2.4} />
              {config.addLabel}
            </button>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        {STAT_TILES.map((tile) => {
          const Icon = tile.icon;
          return (
            <div
              key={tile.key}
              className="flex min-w-[13.125rem] flex-1 items-center gap-4 rounded-2xl border border-[var(--line)] bg-white p-5 shadow-[0_1px_2px_rgba(14,51,89,0.04)] xl:min-w-0"
            >
              <span className={`flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full ${tile.tile}`}>
                <Icon size={22} strokeWidth={2} />
              </span>
              <div>
                <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{tile.label ?? config.totalLabel}</div>
                <div className="mt-1 h-7 text-[1.625rem] font-black leading-7 text-[var(--ink)]">
                  {stats ? formatNumber(stats[tile.key]) : <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className="mt-5 rounded-2xl border border-[var(--line)] bg-white p-5 shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]">
          <label className="relative">
            <span className="sr-only">Search {config.noun}</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={searchText}
              onChange={onSearchChange}
              placeholder="Search by name, email, ID or mobile..."
              className="w-full rounded-xl border border-[var(--line)] bg-white py-2.5 pl-10 pr-3 text-sm text-[var(--ink)] outline-none placeholder:text-[#8A9AAD] focus:border-[var(--blue)] focus:ring-2 focus:ring-[var(--blue)]/15"
            />
          </label>
          <Select
            ariaLabel="Status"
            value={accountStatus}
            onChange={changeFilter(setAccountStatus)}
            options={[{ value: '', label: 'All Status' }, ...STATUS_FILTERS.map((status) => ({ value: status.code, label: status.label }))]}
          />
          <button
            type="button"
            className="flex items-center justify-center gap-2 rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
            onClick={resetFilters}
          >
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
            <StaffTable
              rows={list.data.content}
              firstRowNumber={list.data.page * list.data.size + 1}
              loading={list.loading}
              currentUserId={session.user.userId}
              profilePath={config.path}
              readOnly={!canManage}
              pendingUserId={pendingUserId}
              onStatusChange={handleStatusChange}
              selected={selected}
              onSelect={handleSelect}
            />
            {!list.loading && <Pagination pageData={list.data} noun={config.noun} onPageChange={goToPage} />}
          </>
        )}
      </section>

      {adding && canManage && (
        <AddAdminModal
          role={config.role}
          title={config.addTitle}
          submitLabel={config.addSubmit}
          onClose={() => setAdding(false)}
          onCreated={handleCreated}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
