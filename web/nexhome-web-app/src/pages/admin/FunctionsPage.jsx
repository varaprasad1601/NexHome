import { Boxes, CircleCheck, Clock, Eye, Layers, Pencil, Plus, Power, RotateCcw, Search, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import ConfirmDialog from '../../components/admin/system/ConfirmDialog';
import FunctionFormModal from '../../components/admin/system/FunctionFormModal';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  headClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass,
  moduleLabel
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { statusBadgeClass } from '../../data/staffOptions';
import useAdminList from '../../hooks/useAdminList';
import useClientPaging from '../../hooks/useClientPaging';
import { formatNumber } from '../../utils/format';

const STAT_TILES = [
  { key: 'total', label: 'Total Functions', icon: Boxes, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'modules', label: 'Modules', icon: Layers, tile: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' }
];

/**
 * System Configuration > Functions: the features permissions are granted on (role functions), by module and code,
 * with search and module / type / status filters. Admins add, edit, activate, deactivate and delete them (only
 * one no role is granted; the backend answers 409 otherwise) as FUNCTION_MANAGE write / delete allow; with read only
 * the list is view-only. Data from
 * GET /api/functions; types from the FUNCTION_TYPE lookup.
 */
export default function FunctionsPage() {
  const { authRequest, can } = useAdminAuth();
  // Add, edit and (de)activate need FUNCTION_MANAGE write, delete needs delete; with neither the list is read-only
  const canEdit = can('FUNCTION_MANAGE', 'WRITE');
  const canDelete = can('FUNCTION_MANAGE', 'DELETE');
  const hasActions = canEdit || canDelete;
  const { items: functions, loading, error, reload } = useAdminList('/api/functions');
  const [types, setTypes] = useState([]);
  const [search, setSearch] = useState('');
  const [module, setModule] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState(null); // null, 'new' or the function
  const [deleting, setDeleting] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Functions | NexHome Admin';
  }, []);

  // FUNCTION_TYPE options (a public lookup); without them the type column shows the raw codes
  useEffect(() => {
    let cancelled = false;
    authRequest('/api/lookups/values/FUNCTION_TYPE')
      .then((values) => {
        if (!cancelled) setTypes(values);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  const modules = useMemo(() => [...new Set(functions.map((fn) => fn.functionModule))].sort(), [functions]);
  const typeLabel = (code) => types.find((value) => value.valueCode === code)?.displayName ?? code;

  const stats = useMemo(() => {
    const active = functions.filter((fn) => fn.activeFlag).length;
    return { total: functions.length, modules: modules.length, active, inactive: functions.length - active };
  }, [functions, modules]);

  const rows = useMemo(() => {
    const text = search.trim().toLowerCase();
    return functions.filter(
      (fn) =>
        (!module || fn.functionModule === module) &&
        (!type || fn.functionType === type) &&
        (!status || String(fn.activeFlag) === status) &&
        (!text ||
          [fn.functionCode, fn.functionName, moduleLabel(fn.functionModule), fn.description].some((value) =>
            value?.toLowerCase().includes(text)
          ))
    );
  }, [functions, search, module, type, status]);
  const { pageRows, pageData, setPage } = useClientPaging(rows);

  /** Any filter change starts again from the first page. */
  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
  };
  const resetFilters = () => {
    setSearch('');
    setModule('');
    setType('');
    setStatus('');
    setPage(0);
  };

  const handleSaved = (fn, edited) => {
    setEditing(null);
    showToast(`${fn.functionName} was ${edited ? 'updated' : 'added'}.`);
    reload();
  };

  /** Activate / deactivate; the description is resent because the backend always replaces it. */
  const setActive = async (fn, activeFlag) => {
    try {
      await authRequest('/api/functions/update', {
        method: 'PUT',
        body: { functionId: fn.functionId, description: fn.description ?? '', activeFlag }
      });
      showToast(`${fn.functionName} is now ${activeFlag ? 'active' : 'inactive'}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const deleteFunction = async () => {
    await authRequest(`/api/functions/delete/${deleting.functionId}`, { method: 'DELETE' });
    showToast(`${deleting.functionName} was deleted.`);
    setDeleting(null);
    reload();
  };

  const actionsOf = (fn) => [
    ...(canEdit
      ? [
          { label: 'Edit', icon: Pencil, onClick: () => setEditing(fn) },
          { label: fn.activeFlag ? 'Deactivate' : 'Activate', icon: Power, onClick: () => setActive(fn, !fn.activeFlag) }
        ]
      : []),
    ...(canDelete ? [{ label: 'Delete', icon: Trash2, danger: true, onClick: () => setDeleting(fn) }] : [])
  ];

  const columns = hasActions ? 8 : 7;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'System Configuration' }, { label: 'Functions' }]}
        title="Functions"
        description="The features of the platform that roles are given read, write and delete access to."
        actions={
          canEdit && (
            <button type="button" className={primaryButtonClass} onClick={() => setEditing('new')}>
              <Plus size={17} strokeWidth={2.4} />
              Add Function
            </button>
          )
        }
      />

      {!hasActions && (
        <div className="mb-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
          <Eye size={17} />
          View only: functions are changed by super users.
        </div>
      )}

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
                  {loading ? <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" /> : formatNumber(stats[tile.key])}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_11rem_11rem_10rem_auto]">
          <label className="relative md:col-span-2 xl:col-span-1">
            <span className="sr-only">Search functions</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={search}
              onChange={(e) => changeFilter(setSearch)(e.target.value)}
              placeholder="Search by code, name, module or description..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Module"
            value={module}
            onChange={changeFilter(setModule)}
            options={[{ value: '', label: 'All Modules' }, ...modules.map((name) => ({ value: name, label: moduleLabel(name) }))]}
          />
          <Select
            ariaLabel="Type"
            value={type}
            onChange={changeFilter(setType)}
            options={[
              { value: '', label: 'All Types' },
              ...types.map((value) => ({ value: value.valueCode, label: value.displayName }))
            ]}
          />
          <Select
            ariaLabel="Status"
            value={status}
            onChange={changeFilter(setStatus)}
            options={[
              { value: '', label: 'All Status' },
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' }
            ]}
          />
          <button type="button" className={resetButtonClass} onClick={resetFilters}>
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
            <table className="w-full min-w-[56rem] text-sm">
              <thead className="bg-[var(--light)]">
                <tr>
                  <th className={`${headClass} w-12`}>#</th>
                  <th className={headClass}>Module</th>
                  <th className={headClass}>Code</th>
                  <th className={headClass}>Name</th>
                  <th className={headClass}>Type</th>
                  <th className={`${headClass} w-[30%]`}>Description</th>
                  <th className={headClass}>Status</th>
                  {hasActions && <th className={`${headClass} text-right`}>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {loading &&
                  Array.from({ length: 6 }, (_, i) => (
                    <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                      <td colSpan={columns} className="px-3 py-4">
                        <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                      </td>
                    </tr>
                  ))}
                {!loading && rows.length === 0 && (
                  <tr className="border-t border-[var(--line)]">
                    <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                      No functions match your search or filters.
                    </td>
                  </tr>
                )}
                {!loading &&
                  pageRows.map((fn, i) => (
                    <tr key={fn.functionId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                      <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-[0.8125rem] font-bold text-[var(--muted)]">{moduleLabel(fn.functionModule)}</td>
                      <td className="px-3 py-3">
                        <span className="whitespace-nowrap rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">
                          {fn.functionCode}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 font-bold">{fn.functionName}</td>
                      <td className="px-3 py-3">
                        <span className="whitespace-nowrap rounded-full bg-[#EDF6FD] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--blue)]">
                          {typeLabel(fn.functionType)}
                        </span>
                      </td>
                      <td className="max-w-0 px-3 py-3">
                        {fn.description ? (
                          <span className="block truncate text-[var(--muted)]" title={fn.description}>
                            {fn.description}
                          </span>
                        ) : (
                          <span className="text-[#A8B6C6]">—</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <span
                          className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(
                            fn.activeFlag ? 'ACTIVE' : 'DEACTIVATED'
                          )}`}
                        >
                          {fn.activeFlag ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      {hasActions && (
                        <td className="px-3 py-3 text-right">
                          <div className="flex justify-end">
                            <RowActions label={fn.functionName} actions={actionsOf(fn)} />
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && !error && <Pagination pageData={pageData} noun="functions" onPageChange={setPage} />}
      </section>

      {editing && canEdit && (
        <FunctionFormModal
          fn={editing === 'new' ? null : editing}
          modules={modules}
          types={types}
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
      {deleting && canDelete && (
        <ConfirmDialog
          title="Delete Function"
          message={
            <>
              Delete the function <strong>{deleting.functionName}</strong> ({deleting.functionCode})? Only a function
              that no role is granted can be deleted; otherwise deactivate it.
            </>
          }
          onConfirm={deleteFunction}
          onClose={() => setDeleting(null)}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
