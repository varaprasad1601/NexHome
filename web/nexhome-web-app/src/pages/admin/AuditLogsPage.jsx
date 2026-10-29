import { RotateCcw, Search } from 'lucide-react';
import { useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import DatePicker from '../../components/admin/common/DatePicker';
import Select from '../../components/admin/common/Select';
import PagedTable from '../../components/admin/payment/PagedTable';
import { When } from '../../components/admin/payment/moneyParts';
import { cardClass, moduleLabel, resetButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import useDebounced from '../../hooks/useDebounced';
import { dayBounds, todayInIndia } from '../../utils/schedule';

/** A value as the change list shows it: null as a dash, objects as JSON. */
function shown(value) {
  if (value == null || value === '') return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/**
 * What an entry changed, one line per field: "field: old → new" (only the new value when there was none before,
 * e.g. a creation; only the old one for a removal). A dash when the entry records no values.
 */
function Changes({ oldValues, newValues }) {
  const keys = [...new Set([...Object.keys(oldValues ?? {}), ...Object.keys(newValues ?? {})])];
  if (!keys.length) return <span className="text-[#A8B6C6]">—</span>;
  return (
    <ul className="m-0 list-none space-y-0.5 p-0 text-[0.8125rem]">
      {keys.map((key) => {
        const before = oldValues && key in oldValues ? shown(oldValues[key]) : null;
        const after = newValues && key in newValues ? shown(newValues[key]) : null;
        return (
          <li key={key} className="truncate" title={`${key}: ${before ?? ''}${before && after ? ' → ' : ''}${after ?? ''}`}>
            <span className="font-bold text-[var(--muted)]">{key}:</span>{' '}
            {before != null && <span className={after != null ? 'text-[#C53030] line-through decoration-1' : ''}>{before}</span>}
            {before != null && after != null && <span className="mx-1 text-[var(--muted)]">→</span>}
            {after != null && <span className="font-bold text-[var(--ink)]">{after}</span>}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * System Configuration > Audit Logs (/admin/system/audit-logs, AUDIT_LOG_VIEW read): the security and business
 * history, newest first: when, who (name and role), the action, the record (type and id), what changed and the client
 * address. Filters: record type and action (GET /api/audit-logs/filters lists those logged so far), a search (record
 * id, action) and a date range (IST days). Never shows OTPs, tokens or bank and document numbers: they are not kept.
 */
export default function AuditLogsPage() {
  const { authRequest } = useAdminAuth();
  const [filters, setFilters] = useState({ entityTypes: [], actionsByEntityType: {} });
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  useEffect(() => {
    document.title = 'Audit Logs | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/audit-logs/filters')
      .then((data) => !cancelled && setFilters(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  const actions = entityType
    ? filters.actionsByEntityType[entityType] ?? []
    : [...new Set(Object.values(filters.actionsByEntityType).flat())].sort();

  const params = new URLSearchParams();
  if (entityType) params.set('entityType', entityType);
  if (action) params.set('action', action);
  if (search) params.set('search', search);
  if (fromDate) params.set('from', dayBounds(fromDate).from);
  if (toDate) params.set('to', dayBounds(toDate).to);
  const query = params.toString() ? `?${params}` : '';

  const columns = [
    { header: 'When', cell: (row) => <When value={row.creationDate} /> },
    {
      header: 'Who',
      cell: (row) =>
        row.actorUserId ? (
          <span className="block max-w-[12rem] truncate" title={row.actorName}>
            <span className="font-bold">{row.actorName ?? `User ${row.actorUserId}`}</span>
            {row.actorRole && <span className="block text-xs text-[var(--muted)]">{row.actorRole}</span>}
          </span>
        ) : (
          <span className="text-[var(--muted)]">System</span>
        )
    },
    {
      header: 'Action',
      cell: (row) => (
        <span className="whitespace-nowrap rounded-full bg-[#EDF6FD] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--blue)]">
          {moduleLabel(row.action)}
        </span>
      )
    },
    {
      header: 'Record',
      cell: (row) => (
        <span className="whitespace-nowrap">
          <span className="text-[0.8125rem] font-bold text-[var(--muted)]">{moduleLabel(row.entityType)}</span>{' '}
          <span className="rounded-md bg-[#E8EEF6] px-1.5 py-0.5 font-mono text-[0.75rem] font-bold text-[var(--navy)]">{row.entityId}</span>
        </span>
      )
    },
    {
      header: 'Changes',
      className: 'max-w-0 w-[34%]',
      cell: (row) => <Changes oldValues={row.oldValues} newValues={row.newValues} />
    },
    { header: 'IP Address', cell: (row) => <span className="font-mono text-[0.75rem] text-[var(--muted)]">{row.ipAddress ?? '—'}</span> }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'System Configuration' }, { label: 'Audit Logs' }]}
        title="Audit Logs"
        description="Who changed what, and when: sign-ins, accounts, roles and permissions, lookups, partners, bookings and money."
      />
      <section className={cardClass}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_11rem_12rem_10rem_10rem_auto]">
          <label className="relative md:col-span-2 xl:col-span-1">
            <span className="sr-only">Search audit logs</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search by record id or action..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Record type"
            value={entityType}
            onChange={(value) => {
              setEntityType(value);
              setAction('');
            }}
            options={[{ value: '', label: 'All Records' }, ...filters.entityTypes.map((type) => ({ value: type, label: moduleLabel(type) }))]}
          />
          <Select
            ariaLabel="Action"
            value={action}
            onChange={setAction}
            options={[{ value: '', label: 'All Actions' }, ...actions.map((code) => ({ value: code, label: moduleLabel(code) }))]}
          />
          <DatePicker ariaLabel="From date" value={fromDate} onChange={setFromDate} max={toDate || todayInIndia()} placeholder="From" />
          <DatePicker ariaLabel="To date" value={toDate} onChange={setToDate} min={fromDate || undefined} max={todayInIndia()} placeholder="To" />
          <button
            type="button"
            className={resetButtonClass}
            onClick={() => {
              setEntityType('');
              setAction('');
              setSearchText('');
              setFromDate('');
              setToDate('');
            }}
          >
            <RotateCcw size={15} />
            Reset
          </button>
        </div>
        <PagedTable
          path={`/api/audit-logs${query}`}
          columns={columns}
          rowKey={(row) => row.auditLogId}
          emptyText="Nothing logged matches."
          minWidth="72rem"
        />
      </section>
    </div>
  );
}
