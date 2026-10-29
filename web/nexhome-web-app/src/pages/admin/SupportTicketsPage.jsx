import { CircleAlert, CircleCheck, Inbox, LifeBuoy, Plus, RotateCcw, Search, UserX } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import PagedTable from '../../components/admin/payment/PagedTable';
import { BookingLink, MoneyTile, When } from '../../components/admin/payment/moneyParts';
import CreateTicketModal from '../../components/admin/support/CreateTicketModal';
import {
  cardClass,
  linkClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { SUPPORT_LOOKUPS, supportBadgeClass } from '../../data/supportOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';

/** A status or priority badge, labelled from its lookup. */
export function SupportBadge({ type, code, label }) {
  return (
    <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${supportBadgeClass(type, code)}`}>
      {label(type, code)}
    </span>
  );
}

/**
 * Support & Feedback > Support Tickets (/admin/support, SUPPORT_MANAGE read): tiles (open, urgent / high, unassigned,
 * resolved, total; GET /api/support/tickets/stats), a search (ticket id, subject), status, priority and category
 * filters and "Assigned to me", and the paged list (ticket id link, subject, requester, category, priority, status,
 * assignee, booking, opened). With SUPPORT_MANAGE write, New Ticket opens one for a customer or partner.
 */
export default function SupportTicketsPage() {
  const { authRequest, can, session } = useAdminAuth();
  const navigate = useNavigate();
  const { values, label } = useLookupLabels(SUPPORT_LOOKUPS);
  const canWrite = can('SUPPORT_MANAGE', 'WRITE');
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [category, setCategory] = useState('');
  const [mine, setMine] = useState(false);
  const [stats, setStats] = useState(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    document.title = 'Support Tickets | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/support/tickets/stats')
      .then((data) => !cancelled && setStats(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  const params = new URLSearchParams();
  if (search) params.set('search', search);
  if (status) params.set('status', status);
  if (priority) params.set('priority', priority);
  if (category) params.set('category', category);
  if (mine && session?.user?.userId) params.set('assignedTo', session.user.userId);
  const query = params.toString() ? `?${params}` : '';
  const options = (type, all) => [
    { value: '', label: all },
    ...(values[type] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
  ];

  const columns = [
    {
      header: 'Ticket ID',
      cell: (row) => (
        <Link to={`/admin/support/${row.supportTicketId}`} className={linkClass}>
          {row.publicId}
        </Link>
      )
    },
    {
      header: 'Subject',
      className: 'max-w-0 w-[24%]',
      cell: (row) => (
        <span className="block truncate font-bold" title={row.subject}>
          {row.subject}
        </span>
      )
    },
    {
      header: 'Requester',
      cell: (row) => (
        <span className="block max-w-[12rem] truncate" title={row.requesterName ?? row.requesterMobile}>
          {row.requesterName ?? row.requesterMobile}
          {row.requesterRole && <span className="ml-1.5 text-xs text-[var(--muted)]">{row.requesterRole}</span>}
        </span>
      )
    },
    { header: 'Category', cell: (row) => label('SUPPORT_CATEGORY', row.category) },
    { header: 'Priority', cell: (row) => <SupportBadge type="SUPPORT_PRIORITY" code={row.priority} label={label} /> },
    { header: 'Status', cell: (row) => <SupportBadge type="SUPPORT_TICKET_STATUS" code={row.status} label={label} /> },
    {
      header: 'Assigned To',
      cell: (row) =>
        row.assignedToName ? (
          <span className="block max-w-[10rem] truncate" title={row.assignedToName}>
            {row.assignedToName}
          </span>
        ) : (
          <span className="text-[var(--muted)]">Unassigned</span>
        )
    },
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    { header: 'Opened', cell: (row) => <When value={row.creationDate} /> }
  ];

  const resolved = stats ? (stats.byStatus.RESOLVED ?? 0) + (stats.byStatus.CLOSED ?? 0) : null;
  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Support & Feedback' }, { label: 'Support Tickets' }]}
        title="Support Tickets"
        description="Questions and problems from customers and partners: reply, assign and see them through."
        actions={
          canWrite && (
            <button type="button" className={primaryButtonClass} onClick={() => setCreating(true)}>
              <Plus size={17} strokeWidth={2.4} />
              New Ticket
            </button>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:overflow-visible xl:px-0 xl:pb-0">
        <MoneyTile icon={Inbox} tone="bg-[#FFF7DE] text-[#B7860B]" label="Open" value={stats && formatNumber(stats.open)} />
        <MoneyTile
          icon={CircleAlert}
          tone="bg-[#FDECEC] text-[#C53030]"
          label="Urgent / High"
          value={stats && formatNumber(stats.highPriorityOpen)}
          note="Open tickets"
        />
        <MoneyTile icon={UserX} tone="bg-[#E8EEF6] text-[var(--navy)]" label="Unassigned" value={stats && formatNumber(stats.unassignedOpen)} />
        <MoneyTile icon={CircleCheck} tone="bg-[#E9F6EF] text-[var(--green)]" label="Resolved / Closed" value={resolved != null && formatNumber(resolved)} />
        <MoneyTile icon={LifeBuoy} tone="bg-[#EDF6FD] text-[var(--blue)]" label="Total Tickets" value={stats && formatNumber(stats.total)} />
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_10rem_10rem_11rem_auto_auto]">
          <label className="relative md:col-span-2 xl:col-span-1">
            <span className="sr-only">Search tickets</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search by ticket id or subject..."
              className={searchInputClass}
            />
          </label>
          <Select ariaLabel="Status" value={status} onChange={setStatus} options={options('SUPPORT_TICKET_STATUS', 'All Status')} />
          <Select ariaLabel="Priority" value={priority} onChange={setPriority} options={options('SUPPORT_PRIORITY', 'All Priorities')} />
          <Select ariaLabel="Category" value={category} onChange={setCategory} options={options('SUPPORT_CATEGORY', 'All Categories')} />
          <button
            type="button"
            aria-pressed={mine}
            onClick={() => setMine((value) => !value)}
            className={`whitespace-nowrap rounded-xl border px-4 py-2.5 text-sm font-bold transition ${
              mine ? 'border-[var(--blue)] bg-[#EDF6FD] text-[var(--blue)]' : 'border-[var(--line)] bg-white text-[var(--ink)] hover:bg-[var(--light)]'
            }`}
          >
            Assigned to me
          </button>
          <button
            type="button"
            className={resetButtonClass}
            onClick={() => {
              setSearchText('');
              setStatus('');
              setPriority('');
              setCategory('');
              setMine(false);
            }}
          >
            <RotateCcw size={15} />
            Reset
          </button>
        </div>
        <PagedTable
          path={`/api/support/tickets${query}`}
          columns={columns}
          rowKey={(row) => row.supportTicketId}
          emptyText="No support tickets match."
          minWidth="68rem"
        />
      </section>

      {creating && (
        <CreateTicketModal
          lookups={values}
          onClose={() => setCreating(false)}
          onCreated={(ticket) =>
            navigate(`/admin/support/${ticket.supportTicketId}`, {
              state: { toast: { message: `Ticket ${ticket.publicId} was opened.`, tone: 'success' } }
            })
          }
        />
      )}
    </div>
  );
}
