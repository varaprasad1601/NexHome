import { CircleCheck, Eye, ListTree, Pencil, Plus, Power, RotateCcw, Search, Star, StarOff, Tags } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import LookupTypeFormModal from '../../components/admin/system/LookupTypeFormModal';
import LookupValueFormModal from '../../components/admin/system/LookupValueFormModal';
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
import useClientPaging from '../../hooks/useClientPaging';
import { formatNumber, splitApiDateTime } from '../../utils/format';

function StatusBadge({ active }) {
  return (
    <span
      className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(active ? 'ACTIVE' : 'DEACTIVATED')}`}
    >
      {active ? 'Active' : 'Inactive'}
    </span>
  );
}

/** A value's metadata in the table: its keys, the whole JSON on hover; a dash when empty. */
function MetadataCell({ metadata }) {
  const keys = Object.keys(metadata ?? {});
  if (!keys.length) return <span className="text-[#A8B6C6]">—</span>;
  return (
    <span
      className="block max-w-[14rem] truncate rounded-md bg-[var(--light)] px-2 py-1 font-mono text-[0.75rem] text-[var(--navy)]"
      title={JSON.stringify(metadata, null, 2)}
    >
      {keys.join(', ')}
    </span>
  );
}

/**
 * System Configuration > Lookups > a lookup type: its summary (code, kind, status, description, value counts) and
 * its values in display order, with search and a status filter. With LOOKUP_MANAGE write the type is edited and
 * values are added, edited, made (or no longer) the default, activated and deactivated (not the values of a system
 * type); with read only it is view-only. Lookup values are never deleted. Data from GET /api/lookups/types (the
 * type) and /api/lookups/types/values/{id} (every value, inactive ones too).
 */
export default function LookupTypePage() {
  const { lookupTypeId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { authRequest, can } = useAdminAuth();
  const canEdit = can('LOOKUP_MANAGE', 'WRITE');
  const [state, setState] = useState({ type: null, values: [], loading: true, error: '' });
  const [reloadKey, setReloadKey] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [editingType, setEditingType] = useState(false);
  const [editingValue, setEditingValue] = useState(null); // null, 'new' or the value
  // A toast handed over by the types list after creating this type
  const [toast, setToast] = useState(() => (location.state?.toast ? { ...location.state.toast, id: 'saved' } : null));
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    if (location.state?.toast) navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([authRequest('/api/lookups/types'), authRequest(`/api/lookups/types/values/${lookupTypeId}`)])
      .then(([types, values]) => {
        if (cancelled) return;
        const type = types.find((item) => String(item.lookupTypeId) === String(lookupTypeId));
        setState(
          type
            ? { type, values, loading: false, error: '' }
            : { type: null, values: [], loading: false, error: 'Lookup type does not exist.' }
        );
      })
      .catch((err) => {
        if (!cancelled) setState({ type: null, values: [], loading: false, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, lookupTypeId, reloadKey]);
  const reload = () => setReloadKey((key) => key + 1);

  const { type, values, loading, error } = state;

  useEffect(() => {
    document.title = `${type?.typeName ?? 'Lookup Type'} | NexHome Admin`;
  }, [type]);

  const rows = useMemo(() => {
    const text = search.trim().toLowerCase();
    return values.filter(
      (value) =>
        (!status || String(value.activeFlag) === status) &&
        (!text || [value.valueCode, value.displayName, value.description].some((field) => field?.toLowerCase().includes(text)))
    );
  }, [values, search, status]);
  const { pageRows, pageData, setPage } = useClientPaging(rows);

  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
  };
  const resetFilters = () => {
    setSearch('');
    setStatus('');
    setPage(0);
  };

  /** A quick change from the row menu; the description and metadata are resent, since the backend replaces them. */
  const updateValue = async (value, changes, message) => {
    try {
      await authRequest('/api/lookups/values/update', {
        method: 'PUT',
        body: { lookupValueId: value.lookupValueId, description: value.description ?? '', ...changes }
      });
      showToast(message);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const actionsOf = (value) => [
    { label: 'Edit', icon: Pencil, onClick: () => setEditingValue(value) },
    ...(value.activeFlag
      ? [
          value.isDefault
            ? {
                label: 'Remove Default',
                icon: StarOff,
                onClick: () => updateValue(value, { isDefault: false }, `${value.displayName} is no longer the default.`)
              }
            : {
                label: 'Make Default',
                icon: Star,
                onClick: () => updateValue(value, { isDefault: true }, `${value.displayName} is now the default.`)
              }
        ]
      : []),
    // Values of a system type always stay active
    ...(type.isSystemManaged && value.activeFlag
      ? []
      : [
          {
            label: value.activeFlag ? 'Deactivate' : 'Activate',
            icon: Power,
            onClick: () =>
              updateValue(
                value,
                // An inactive value cannot be the default
                value.activeFlag ? { activeFlag: false, isDefault: false } : { activeFlag: true },
                `${value.displayName} is now ${value.activeFlag ? 'inactive' : 'active'}.`
              )
          }
        ])
  ];

  const crumbs = [
    { label: 'System Configuration' },
    { label: 'Lookups', to: '/admin/system/lookups' },
    { label: type?.typeCode ?? (loading ? '' : 'Lookup Type') }
  ];

  if (error || !type) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} title={loading ? '' : 'Lookup Type'} />
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

  const created = splitApiDateTime(type.creationDate);
  const activeCount = values.filter((value) => value.activeFlag).length;
  const defaultValue = values.find((value) => value.isDefault);
  const columns = canEdit ? 9 : 8;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={crumbs}
        title={type.typeName}
        description="The values of this lookup, in the order dropdowns offer them."
        actions={
          canEdit && (
            <>
              <button
                type="button"
                className="flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
                onClick={() => setEditingType(true)}
              >
                <Pencil size={15} />
                Edit Type
              </button>
              <button type="button" className={primaryButtonClass} onClick={() => setEditingValue('new')}>
                <Plus size={17} strokeWidth={2.4} />
                Add Value
              </button>
            </>
          )
        }
      />

      {!canEdit && (
        <div className="mb-5 flex items-center gap-3 rounded-2xl border border-[#C7E3FC] bg-[#EDF6FD] px-5 py-3 text-sm font-bold text-[var(--blue)]">
          <Eye size={17} />
          View only: lookups are changed by super users.
        </div>
      )}

      <section className={`flex flex-wrap items-center gap-x-10 gap-y-4 ${cardClass}`}>
        <div className="flex min-w-0 flex-1 items-center gap-4">
          <span className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
            <Tags size={24} />
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">{type.typeCode}</span>
              <span
                className={`rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                  type.isSystemManaged ? 'bg-[#E8EEF6] text-[var(--navy)]' : 'bg-[#EDF6FD] text-[var(--blue)]'
                }`}
              >
                {type.isSystemManaged ? 'System' : 'Custom'}
              </span>
              <StatusBadge active={type.activeFlag} />
              {created && <span className="text-[0.8125rem] text-[var(--muted)]">Created on {created.date}</span>}
            </div>
            <div className="mt-1.5 text-sm text-[var(--muted)]">{type.description || 'No description.'}</div>
          </div>
        </div>
        {[
          { label: 'Values', value: formatNumber(values.length), icon: ListTree },
          { label: 'Active', value: formatNumber(activeCount), icon: CircleCheck },
          { label: 'Default', value: defaultValue?.displayName ?? 'None', icon: Star }
        ].map((stat) => {
          const Icon = stat.icon;
          return (
            <div key={stat.label} className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--light)] text-[var(--navy)]">
                <Icon size={18} />
              </span>
              <div>
                <div className="text-[0.75rem] font-bold text-[var(--muted)]">{stat.label}</div>
                <div className="max-w-[12rem] truncate text-xl font-black leading-6 text-[var(--ink)]" title={stat.value}>
                  {stat.value}
                </div>
              </div>
            </div>
          );
        })}
      </section>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_10rem_auto]">
          <label className="relative">
            <span className="sr-only">Search values</span>
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

        <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
          <table className="w-full min-w-[60rem] text-sm">
            <thead className="bg-[var(--light)]">
              <tr>
                <th className={`${headClass} w-12`}>#</th>
                <th className={headClass}>Order</th>
                <th className={headClass}>Value Code</th>
                <th className={headClass}>Display Name</th>
                <th className={`${headClass} w-[26%]`}>Description</th>
                <th className={headClass}>Metadata</th>
                <th className={headClass}>Default</th>
                <th className={headClass}>Status</th>
                {canEdit && <th className={`${headClass} text-right`}>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr className="border-t border-[var(--line)]">
                  <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                    {values.length ? 'No values match your search or filters.' : 'This lookup type has no values yet.'}
                  </td>
                </tr>
              )}
              {pageRows.map((value, i) => (
                <tr key={value.lookupValueId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                  <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                  <td className="px-3 py-3 text-[var(--muted)]">{value.displayOrder}</td>
                  <td className="px-3 py-3">
                    <span className="whitespace-nowrap rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">
                      {value.valueCode}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-3 py-3 font-bold">{value.displayName}</td>
                  <td className="max-w-0 px-3 py-3">
                    {value.description ? (
                      <span className="block truncate text-[var(--muted)]" title={value.description}>
                        {value.description}
                      </span>
                    ) : (
                      <span className="text-[#A8B6C6]">—</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <MetadataCell metadata={value.metadata} />
                  </td>
                  <td className="px-3 py-3">
                    {value.isDefault ? (
                      <span className="inline-flex items-center gap-1 whitespace-nowrap rounded-full bg-[#FFF7DE] px-2.5 py-1 text-[0.75rem] font-bold text-[#B7860B]">
                        <Star size={12} className="fill-current" />
                        Default
                      </span>
                    ) : (
                      <span className="text-[#A8B6C6]">—</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <StatusBadge active={value.activeFlag} />
                  </td>
                  {canEdit && (
                    <td className="px-3 py-3 text-right">
                      <div className="flex justify-end">
                        <RowActions label={value.displayName} actions={actionsOf(value)} />
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination pageData={pageData} noun="values" onPageChange={setPage} />
      </section>

      {editingType && canEdit && (
        <LookupTypeFormModal
          type={type}
          onClose={() => setEditingType(false)}
          onSaved={(saved) => {
            setEditingType(false);
            showToast(`${saved.typeName} was updated.`);
            reload();
          }}
        />
      )}
      {editingValue && canEdit && (
        <LookupValueFormModal
          type={type}
          value={editingValue === 'new' ? null : editingValue}
          onClose={() => setEditingValue(null)}
          onSaved={(saved, edited) => {
            setEditingValue(null);
            showToast(`${saved.displayName} was ${edited ? 'updated' : 'added'}.`);
            reload();
          }}
        />
      )}

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
