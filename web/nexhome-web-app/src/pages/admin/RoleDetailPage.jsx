import { Boxes, Eye, Info, KeyRound, Layers, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import Toggle from '../../components/admin/common/Toggle';
import { cardClass, headClass, moduleLabel, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { statusBadgeClass } from '../../data/staffOptions';
import { formatNumber, splitApiDateTime } from '../../utils/format';

const FLAGS = [
  { key: 'canRead', label: 'Read' },
  { key: 'canWrite', label: 'Write' },
  { key: 'canDelete', label: 'Delete' }
];
const NO_ACCESS = { canRead: false, canWrite: false, canDelete: false };


/** What a grant gives: nothing unless it exists and is active. */
function accessOf(grant) {
  return grant?.activeFlag ? { canRead: grant.canRead, canWrite: grant.canWrite, canDelete: grant.canDelete } : NO_ACCESS;
}

/** The access after switching one flag, as the backend stores it: write or delete needs read, no read means nothing. */
function nextAccess(access, key, on) {
  const next = { ...access, [key]: on };
  if (on && key !== 'canRead') next.canRead = true;
  if (!on && key === 'canRead') return NO_ACCESS;
  return next;
}

/**
 * System Configuration > Roles > a role: its summary, then every function grouped by module with Read / Write /
 * Delete switches. Each switch saves at once: the first access adds the role function, a change updates it, and
 * switching everything off removes it (with ROLE_MANAGE delete). Changing them needs ROLE_MANAGE write; without it
 * the switches are read-only. SUPER_USER needs no grants, since it always has every permission. Data from
 * GET /api/roles/{id}, /api/roles/functions/{id} and /api/functions.
 */
export default function RoleDetailPage() {
  const { roleId } = useParams();
  const { authRequest, can, reloadPermissions } = useAdminAuth();
  const [state, setState] = useState({ role: null, functions: [], grants: [], error: '' });
  const [search, setSearch] = useState('');
  const [show, setShow] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showError = useCallback((message) => setToast({ message, tone: 'error', id: Date.now() }), []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      authRequest(`/api/roles/${roleId}`),
      authRequest('/api/functions'),
      authRequest(`/api/roles/functions/${roleId}`)
    ])
      .then(([role, functions, grants]) => {
        if (!cancelled) setState({ role, functions, grants, error: '' });
      })
      .catch((err) => {
        if (!cancelled) setState({ role: null, functions: [], grants: [], error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, roleId]);

  const { role, functions, grants, error } = state;
  const superUserRole = role?.code === 'SUPER_USER';
  // Granting needs ROLE_MANAGE write; removing a grant needs delete (without it, switching all off keeps an empty one).
  // SUPER_USER needs no grants, so its switches stay locked.
  const canEdit = can('ROLE_MANAGE', 'WRITE') && !superUserRole;
  const canDelete = can('ROLE_MANAGE', 'DELETE');

  useEffect(() => {
    document.title = `${role?.name ?? 'Role'} | NexHome Admin`;
  }, [role]);

  const grantOf = useMemo(() => new Map(grants.map((grant) => [grant.functionId, grant])), [grants]);

  /** Modules in name order, each with its functions that pass the search and the "show" filter. */
  const modules = useMemo(() => {
    const text = search.trim().toLowerCase();
    const groups = new Map();
    functions.forEach((fn) => {
      const granted = accessOf(grantOf.get(fn.functionId)).canRead;
      if (show === 'granted' && !granted) return;
      if (show === 'none' && granted) return;
      if (text && ![fn.functionCode, fn.functionName, moduleLabel(fn.functionModule)].some((value) => value.toLowerCase().includes(text))) return;
      if (!groups.has(fn.functionModule)) groups.set(fn.functionModule, []);
      groups.get(fn.functionModule).push(fn);
    });
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [functions, grantOf, search, show]);

  const grantedCount = functions.filter((fn) => accessOf(grantOf.get(fn.functionId)).canRead).length;
  const moduleCount = new Set(functions.map((fn) => fn.functionModule)).size;

  const replaceGrant = (functionId, grant) =>
    setState((current) => ({
      ...current,
      grants: [...current.grants.filter((g) => g.functionId !== functionId), ...(grant ? [grant] : [])]
    }));

  const toggle = async (fn, key, on) => {
    const grant = grantOf.get(fn.functionId);
    const access = nextAccess(accessOf(grant), key, on);
    const none = !access.canRead && !access.canWrite && !access.canDelete;
    setPendingId(fn.functionId);
    try {
      if (none && (!grant || canDelete)) {
        if (grant) await authRequest(`/api/roles/functions/delete/${grant.roleFunctionId}`, { method: 'DELETE' });
        replaceGrant(fn.functionId, null);
      } else if (grant) {
        const updated = await authRequest('/api/roles/functions/update', {
          method: 'PUT',
          body: { roleFunctionId: grant.roleFunctionId, ...access, activeFlag: true }
        });
        replaceGrant(fn.functionId, updated);
      } else {
        const added = await authRequest('/api/roles/functions/add', {
          method: 'POST',
          body: { roleId: role.roleId, functionId: fn.functionId, ...access, activeFlag: true }
        });
        replaceGrant(fn.functionId, added);
      }
      // The change may be to the admin's own role: their access (and so these switches) can change with it
      reloadPermissions();
    } catch (err) {
      showError(err.message);
    } finally {
      setPendingId(null);
    }
  };

  const crumbs = [
    { label: 'System Configuration' },
    { label: 'Roles', to: '/admin/system/roles' },
    { label: role?.name ?? 'Role' }
  ];

  if (error || !role) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} title="Role" />
        {error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {error}
          </div>
        ) : (
          <div className="h-40 animate-pulse rounded-2xl bg-white" />
        )}
      </div>
    );
  }

  const created = splitApiDateTime(role.creationDate);

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} title={role.name} description="The functions this role can read, write and delete." />

      <section className={`flex flex-wrap items-center gap-x-10 gap-y-4 ${cardClass}`}>
        <div className="flex min-w-0 flex-1 items-center gap-4">
          <span className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
            <KeyRound size={24} />
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">{role.code}</span>
              <span
                className={`rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(role.activeFlag ? 'ACTIVE' : 'DEACTIVATED')}`}
              >
                {role.activeFlag ? 'Active' : 'Inactive'}
              </span>
              {created && <span className="text-[0.8125rem] text-[var(--muted)]">Created on {created.date}</span>}
            </div>
            <div className="mt-1.5 text-sm text-[var(--muted)]">{role.description || 'No description.'}</div>
          </div>
        </div>
        {[
          { label: 'Functions Granted', value: superUserRole ? functions.length : grantedCount, icon: Boxes },
          { label: 'Modules', value: moduleCount, icon: Layers }
        ].map((stat) => {
          const Icon = stat.icon;
          return (
            <div key={stat.label} className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--light)] text-[var(--navy)]">
                <Icon size={18} />
              </span>
              <div>
                <div className="text-[0.75rem] font-bold text-[var(--muted)]">{stat.label}</div>
                <div className="text-xl font-black leading-6 text-[var(--ink)]">
                  {formatNumber(stat.value)}
                  {stat.label === 'Functions Granted' && (
                    <span className="text-sm font-bold text-[var(--muted)]"> / {formatNumber(functions.length)}</span>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </section>

      {superUserRole ? (
        <div className="mt-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
          <Info size={17} />
          Super users always have every permission, so this role needs no grants.
        </div>
      ) : (
        !canEdit && (
          <div className="mt-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
            <Eye size={17} />
            View only: you do not have write access to roles.
          </div>
        )
      )}
      {!role.activeFlag && (
        <div className="mt-3 rounded-2xl border border-[#F3D98B] bg-[#FFF7DE] px-5 py-3 text-sm font-bold text-[#8A6508]">
          This role is inactive, so its permissions apply to no one until it is activated.
        </div>
      )}

      <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_13rem]">
        <label className="relative">
          <span className="sr-only">Search functions</span>
          <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by function, code or module..."
            className={searchInputClass}
          />
        </label>
        <Select
          ariaLabel="Show"
          value={show}
          onChange={setShow}
          options={[
            { value: '', label: 'All Functions' },
            { value: 'granted', label: 'Granted' },
            { value: 'none', label: 'Not Granted' }
          ]}
        />
      </div>

      {modules.length === 0 && (
        <div className={`mt-5 py-12 text-center text-sm text-[var(--muted)] ${cardClass}`}>No functions match your search or filter.</div>
      )}

      {modules.map(([module, moduleFunctions]) => {
        const granted = moduleFunctions.filter((fn) => accessOf(grantOf.get(fn.functionId)).canRead).length;
        return (
          <section key={module} className={`mt-5 ${cardClass}`}>
            <div className="mb-3 flex items-center justify-between gap-3">
              <h3 className="mb-0 flex items-center gap-2.5 text-[1.0625rem] font-black text-[var(--navy)]">
                <Layers size={17} className="text-[var(--blue)]" />
                {moduleLabel(module)}
              </h3>
              <span className="rounded-full bg-[var(--light)] px-3 py-1 text-[0.75rem] font-bold text-[var(--muted)]">
                {superUserRole ? moduleFunctions.length : granted} of {moduleFunctions.length} granted
              </span>
            </div>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[40rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={headClass}>Function</th>
                    <th className={`${headClass} w-[11rem]`}>Code</th>
                    {FLAGS.map((flag) => (
                      <th key={flag.key} className={`${headClass} w-24 text-center`}>
                        {flag.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {moduleFunctions.map((fn) => {
                    const access = superUserRole
                      ? { canRead: true, canWrite: true, canDelete: true }
                      : accessOf(grantOf.get(fn.functionId));
                    return (
                      <tr key={fn.functionId} className="border-t border-[var(--line)] text-[var(--ink)]">
                        <td className="px-3 py-3">
                          <span className="flex items-center gap-2 font-bold">
                            {fn.functionName}
                            {!fn.activeFlag && (
                              <span
                                className="rounded-full bg-[#FFF7DE] px-2 py-0.5 text-[0.6875rem] font-bold text-[#8A6508]"
                                title="An inactive function grants nothing, whatever is switched on here"
                              >
                                Inactive
                              </span>
                            )}
                          </span>
                          {fn.description && (
                            <span className="mt-0.5 block max-w-[32rem] truncate text-[0.8125rem] text-[var(--muted)]" title={fn.description}>
                              {fn.description}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          <span className="whitespace-nowrap font-mono text-[0.75rem] font-bold text-[var(--muted)]">{fn.functionCode}</span>
                        </td>
                        {FLAGS.map((flag) => (
                          <td key={flag.key} className="px-3 py-3">
                            <div className="flex justify-center">
                              <Toggle
                                checked={access[flag.key]}
                                onChange={(on) => toggle(fn, flag.key, on)}
                                disabled={!canEdit || pendingId === fn.functionId}
                                label={`${flag.label} ${fn.functionName}`}
                              />
                            </div>
                          </td>
                        ))}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
