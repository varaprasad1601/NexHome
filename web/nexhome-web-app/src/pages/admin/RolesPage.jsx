import { CircleCheck, Clock, Eye, Pencil, Plus, Power, RotateCcw, Search, ShieldCheck, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import ConfirmDialog from '../../components/admin/system/ConfirmDialog';
import RoleFormModal from '../../components/admin/system/RoleFormModal';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  headClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { statusBadgeClass } from '../../data/staffOptions';
import useAdminList from '../../hooks/useAdminList';
import useClientPaging from '../../hooks/useClientPaging';
import { formatNumber, splitApiDateTime } from '../../utils/format';

/** The seeded roles the code relies on (role codes in NexHomeRole): never deactivated or deleted here. */
const SYSTEM_ROLE_CODES = ['SUPER_USER', 'ADMIN', 'SUB_ADMIN', 'CUSTOMER', 'PARTNER'];

const STAT_TILES = [
  { key: 'total', label: 'Total Roles', icon: ShieldCheck, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' }
];

/**
 * System Configuration > Roles: every role with its status, search and a status filter; a role's name opens its
 * permissions (RoleDetailPage). ROLE_MANAGE write adds, edits, activates and deactivates roles, delete deletes an
 * unused one (the backend answers 409 otherwise); with read only the list is view-only. Data from GET /api/roles.
 */
export default function RolesPage() {
  const { authRequest, can, isSuperUser } = useAdminAuth();
  // ROLE_MANAGE write adds, edits and (de)activates roles, delete deletes them; the SUPER_USER role is changed by super
  // users only (the backend refuses everyone else)
  const canEdit = can('ROLE_MANAGE', 'WRITE');
  const canDelete = can('ROLE_MANAGE', 'DELETE');
  const hasActions = canEdit || canDelete;
  const { items: roles, loading, error, reload } = useAdminList('/api/roles');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState(null); // null, 'new' or the role
  const [deleting, setDeleting] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Roles | NexHome Admin';
  }, []);

  const stats = useMemo(() => {
    const active = roles.filter((role) => role.activeFlag).length;
    return { total: roles.length, active, inactive: roles.length - active };
  }, [roles]);

  const rows = useMemo(() => {
    const text = search.trim().toLowerCase();
    return roles.filter(
      (role) =>
        (!status || String(role.activeFlag) === status) &&
        (!text || [role.code, role.name, role.description].some((value) => value?.toLowerCase().includes(text)))
    );
  }, [roles, search, status]);
  const { pageRows, pageData, setPage } = useClientPaging(rows);

  const handleSaved = (role, edited) => {
    setEditing(null);
    showToast(`${role.name} was ${edited ? 'updated' : 'added'}.`);
    reload();
  };

  /** Activate / deactivate; the description is resent because the backend always replaces it. */
  const setActive = async (role, activeFlag) => {
    try {
      await authRequest('/api/roles/update', {
        method: 'PUT',
        body: { roleId: role.roleId, description: role.description ?? '', activeFlag }
      });
      showToast(`${role.name} is now ${activeFlag ? 'active' : 'inactive'}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const deleteRole = async () => {
    await authRequest(`/api/roles/delete/${deleting.roleId}`, { method: 'DELETE' });
    showToast(`${deleting.name} was deleted.`);
    setDeleting(null);
    reload();
  };

  /** The row menu: what the admin may do with this role (none for SUPER_USER unless they are a super user). */
  const actionsOf = (role) => {
    const system = SYSTEM_ROLE_CODES.includes(role.code);
    if (role.code === 'SUPER_USER' && !isSuperUser) return [];
    const actions = [];
    if (canEdit) actions.push({ label: 'Edit', icon: Pencil, onClick: () => setEditing(role) });
    if (canEdit && !system) {
      actions.push({
        label: role.activeFlag ? 'Deactivate' : 'Activate',
        icon: Power,
        onClick: () => setActive(role, !role.activeFlag)
      });
    }
    if (canDelete && !system) {
      actions.push({ label: 'Delete', icon: Trash2, danger: true, onClick: () => setDeleting(role) });
    }
    return actions;
  };

  const columns = hasActions ? 7 : 6;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'System Configuration' }, { label: 'Roles' }]}
        title="Roles"
        description="The roles users hold. Their permissions are granted per function under Permissions."
        actions={
          canEdit && (
            <button type="button" className={primaryButtonClass} onClick={() => setEditing('new')}>
              <Plus size={17} strokeWidth={2.4} />
              Add Role
            </button>
          )
        }
      />

      {!hasActions && (
        <div className="mb-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
          <Eye size={17} />
          View only: you do not have write access to roles.
        </div>
      )}

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-3 xl:overflow-visible xl:px-0 xl:pb-0">
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
                  {loading ? <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" /> : formatNumber(stats[tile.key])}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]">
          <label className="relative">
            <span className="sr-only">Search roles</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Search by code, name or description..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Status"
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All Status' },
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' }
            ]}
          />
          <button
            type="button"
            className={resetButtonClass}
            onClick={() => {
              setSearch('');
              setStatus('');
              setPage(0);
            }}
          >
            <RotateCcw size={15} />
            Reset
          </button>
        </div>

        {error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {error}
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
            <table className="w-full min-w-[48rem] text-sm">
              <thead className="bg-[var(--light)]">
                <tr>
                  <th className={`${headClass} w-12`}>#</th>
                  <th className={headClass}>Code</th>
                  <th className={headClass}>Name</th>
                  <th className={`${headClass} w-[40%]`}>Description</th>
                  <th className={headClass}>Status</th>
                  <th className={headClass}>Created On</th>
                  {hasActions && <th className={`${headClass} text-right`}>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {loading &&
                  Array.from({ length: 5 }, (_, i) => (
                    <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                      <td colSpan={columns} className="px-3 py-4">
                        <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                      </td>
                    </tr>
                  ))}
                {!loading && rows.length === 0 && (
                  <tr className="border-t border-[var(--line)]">
                    <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                      No roles match your search or filters.
                    </td>
                  </tr>
                )}
                {!loading &&
                  pageRows.map((role, i) => {
                    const created = splitApiDateTime(role.creationDate);
                    return (
                      <tr key={role.roleId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                        <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                        <td className="px-3 py-3">
                          <span className="whitespace-nowrap rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">
                            {role.code}
                          </span>
                        </td>
                        <td className="px-3 py-3">
                          <span className="flex items-center gap-2 whitespace-nowrap font-bold">
                            <Link
                              to={`/admin/system/roles/${role.roleId}`}
                              className="font-bold text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                              title="Open the role's permissions"
                            >
                              {role.name}
                            </Link>
                            {SYSTEM_ROLE_CODES.includes(role.code) && (
                              <span
                                className="rounded-full bg-[var(--light)] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--muted)]"
                                title="A seeded role the platform relies on: it cannot be deactivated or deleted"
                              >
                                System
                              </span>
                            )}
                          </span>
                        </td>
                        <td className="max-w-0 px-3 py-3">
                          {role.description ? (
                            <span className="block truncate text-[var(--muted)]" title={role.description}>
                              {role.description}
                            </span>
                          ) : (
                            <span className="text-[#A8B6C6]">—</span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          <span
                            className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(
                              role.activeFlag ? 'ACTIVE' : 'DEACTIVATED'
                            )}`}
                          >
                            {role.activeFlag ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-3 py-3 text-[var(--muted)]">{created?.date ?? '—'}</td>
                        {hasActions && (
                          <td className="px-3 py-3 text-right">
                            <div className="flex justify-end">
                              {actionsOf(role).length > 0 && <RowActions label={role.name} actions={actionsOf(role)} />}
                            </div>
                          </td>
                        )}
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </div>
        )}
        {!loading && !error && <Pagination pageData={pageData} noun="roles" onPageChange={setPage} />}
      </section>

      {editing && canEdit && (
        <RoleFormModal
          role={editing === 'new' ? null : editing}
          system={editing !== 'new' && SYSTEM_ROLE_CODES.includes(editing.code)}
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
      {deleting && canDelete && (
        <ConfirmDialog
          title="Delete Role"
          message={
            <>
              Delete the role <strong>{deleting.name}</strong> ({deleting.code})? Only a role that no user holds and no
              function is granted to can be deleted; otherwise deactivate it.
            </>
          }
          onConfirm={deleteRole}
          onClose={() => setDeleting(null)}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
