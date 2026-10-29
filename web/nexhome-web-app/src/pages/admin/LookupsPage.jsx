import { CircleCheck, Clock, Eye, ListTree, Pencil, Plus, Power, RotateCcw, Search, ShieldCheck, Tags } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import LookupTypeFormModal from '../../components/admin/system/LookupTypeFormModal';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  headClass,
  linkClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { statusBadgeClass } from '../../data/staffOptions';
import useAdminList from '../../hooks/useAdminList';
import useClientPaging from '../../hooks/useClientPaging';
import { formatNumber, splitApiDateTime } from '../../utils/format';

const STAT_TILES = [
  { key: 'total', label: 'Lookup Types', icon: Tags, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'system', label: 'System Types', icon: ShieldCheck, tile: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' }
];

/** A type's kind tag: system types are seeded and relied on by the platform, custom ones were added here. */
function LookupKindTag({ system }) {
  return system ? (
    <span className="whitespace-nowrap rounded-full bg-[#E8EEF6] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--navy)]">System</span>
  ) : (
    <span className="whitespace-nowrap rounded-full bg-[#EDF6FD] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--blue)]">Custom</span>
  );
}

/**
 * System Configuration > Lookups: the lookup types (the dropdown lists of the platform: statuses, address types,
 * languages, ...), with search and kind / status filters. A type's code opens its values (LookupTypePage). With
 * LOOKUP_MANAGE write (super users, V11) types are added, edited, activated and deactivated (not system types);
 * with read only the list is view-only. Types are never deleted. Data from GET /api/lookups/types.
 */
export default function LookupsPage() {
  const { authRequest, can } = useAdminAuth();
  const navigate = useNavigate();
  const canEdit = can('LOOKUP_MANAGE', 'WRITE');
  const { items: types, loading, error, reload } = useAdminList('/api/lookups/types');
  const [search, setSearch] = useState('');
  const [kind, setKind] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState(null); // null, 'new' or the type
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Lookups | NexHome Admin';
  }, []);

  const stats = useMemo(() => {
    const active = types.filter((type) => type.activeFlag).length;
    return {
      total: types.length,
      system: types.filter((type) => type.isSystemManaged).length,
      active,
      inactive: types.length - active
    };
  }, [types]);

  const rows = useMemo(() => {
    const text = search.trim().toLowerCase();
    return [...types]
      .sort((a, b) => a.typeCode.localeCompare(b.typeCode))
      .filter(
        (type) =>
          (!kind || String(type.isSystemManaged) === kind) &&
          (!status || String(type.activeFlag) === status) &&
          (!text || [type.typeCode, type.typeName, type.description].some((value) => value?.toLowerCase().includes(text)))
      );
  }, [types, search, kind, status]);
  const { pageRows, pageData, setPage } = useClientPaging(rows);

  /** Any filter change starts again from the first page. */
  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
  };
  const resetFilters = () => {
    setSearch('');
    setKind('');
    setStatus('');
    setPage(0);
  };

  const handleSaved = (type, edited) => {
    setEditing(null);
    if (edited) {
      showToast(`${type.typeName} was updated.`);
      reload();
    } else {
      // A new type has no values yet: go straight to adding them
      navigate(`/admin/system/lookups/${type.lookupTypeId}`, { state: { toast: { message: `${type.typeName} was added.`, tone: 'success' } } });
    }
  };

  /** Activate / deactivate; the description is resent because the backend always replaces it. */
  const setActive = async (type, activeFlag) => {
    try {
      await authRequest('/api/lookups/types/update', {
        method: 'PUT',
        body: { lookupTypeId: type.lookupTypeId, description: type.description ?? '', activeFlag }
      });
      showToast(`${type.typeName} is now ${activeFlag ? 'active' : 'inactive'}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const actionsOf = (type) => [
    { label: 'View Values', icon: ListTree, onClick: () => navigate(`/admin/system/lookups/${type.lookupTypeId}`) },
    { label: 'Edit', icon: Pencil, onClick: () => setEditing(type) },
    // System types always stay active
    ...(type.isSystemManaged && type.activeFlag
      ? []
      : [{ label: type.activeFlag ? 'Deactivate' : 'Activate', icon: Power, onClick: () => setActive(type, !type.activeFlag) }])
  ];

  const columns = canEdit ? 8 : 7;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'System Configuration' }, { label: 'Lookups' }]}
        title="Lookups"
        description="The lists of values offered across the platform: statuses, types, languages and other options."
        actions={
          canEdit && (
            <button type="button" className={primaryButtonClass} onClick={() => setEditing('new')}>
              <Plus size={17} strokeWidth={2.4} />
              Add Lookup Type
            </button>
          )
        }
      />

      {!canEdit && (
        <div className="mb-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
          <Eye size={17} />
          View only: lookups are changed by super users.
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
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_11rem_10rem_auto]">
          <label className="relative md:col-span-2 xl:col-span-1">
            <span className="sr-only">Search lookup types</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={search}
              onChange={(e) => changeFilter(setSearch)(e.target.value)}
              placeholder="Search by code, name or description..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Kind"
            value={kind}
            onChange={changeFilter(setKind)}
            options={[
              { value: '', label: 'All Kinds' },
              { value: 'true', label: 'System' },
              { value: 'false', label: 'Custom' }
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
                  <th className={headClass}>Type Code</th>
                  <th className={headClass}>Name</th>
                  <th className={`${headClass} w-[34%]`}>Description</th>
                  <th className={headClass}>Kind</th>
                  <th className={headClass}>Status</th>
                  <th className={headClass}>Created On</th>
                  {canEdit && <th className={`${headClass} text-right`}>Actions</th>}
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
                      No lookup types match your search or filters.
                    </td>
                  </tr>
                )}
                {!loading &&
                  pageRows.map((type, i) => (
                    <tr key={type.lookupTypeId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                      <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                      <td className="whitespace-nowrap px-3 py-3">
                        <Link to={`/admin/system/lookups/${type.lookupTypeId}`} className={`${linkClass} font-mono text-[0.8125rem]`}>
                          {type.typeCode}
                        </Link>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 font-bold">{type.typeName}</td>
                      <td className="max-w-0 px-3 py-3">
                        {type.description ? (
                          <span className="block truncate text-[var(--muted)]" title={type.description}>
                            {type.description}
                          </span>
                        ) : (
                          <span className="text-[#A8B6C6]">—</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <LookupKindTag system={type.isSystemManaged} />
                      </td>
                      <td className="px-3 py-3">
                        <span
                          className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(
                            type.activeFlag ? 'ACTIVE' : 'DEACTIVATED'
                          )}`}
                        >
                          {type.activeFlag ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-[var(--muted)]">
                        {splitApiDateTime(type.creationDate)?.date ?? '—'}
                      </td>
                      {canEdit && (
                        <td className="px-3 py-3 text-right">
                          <div className="flex justify-end">
                            <RowActions label={type.typeName} actions={actionsOf(type)} />
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && !error && <Pagination pageData={pageData} noun="lookup types" onPageChange={setPage} />}
      </section>

      {editing && canEdit && (
        <LookupTypeFormModal type={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={handleSaved} />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
