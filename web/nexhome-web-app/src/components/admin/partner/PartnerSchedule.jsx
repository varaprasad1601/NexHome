import { CalendarX2, Pencil, Plus } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import RowActions from '../system/RowActions';
import { headClass } from '../system/systemStyles';
import FormModal from '../common/FormModal';
import Pagination from '../common/Pagination';
import { InfoSection } from '../profile/ProfileParts';
import { useAdminAuth } from '../../../context/adminAuth';
import useClientPaging from '../../../hooks/useClientPaging';
import { splitApiDateTime } from '../../../utils/format';
import { apiDateTimeParts, TIME_OPTIONS, toInstant, todayInIndia } from '../../../utils/schedule';

/** Time off records per page. */
const PAGE_SIZE = 5;

const when = (value) => {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : '—';
};

/** Where a period stands against `now`, with its tag colours. */
const STAGES = {
  upcoming: { label: 'Upcoming', className: 'bg-[#EDF6FD] text-[var(--blue)]' },
  ongoing: { label: 'Ongoing', className: 'bg-[#E9F6EF] text-[var(--green)]' },
  past: { label: 'Past', className: 'bg-[var(--light)] text-[var(--muted)]' },
  cancelled: { label: 'Cancelled', className: 'bg-[#FDECEC] text-[#C53030]' },
  endedEarly: { label: 'Ended Early', className: 'bg-[#FFF7DE] text-[#8A6508]' }
};

/**
 * Cancelled time off is kept (V44): cancelled before it started it is Cancelled; cancelled while under way it ended
 * then (its end is the cancellation), so it is Ended Early.
 */
function stageOf(item, now) {
  if (item.cancelledAt) return millisOf(item.cancelledAt) > startOf(item) ? 'endedEarly' : 'cancelled';
  if (endOf(item) <= now) return 'past';
  return startOf(item) <= now ? 'ongoing' : 'upcoming';
}

/** Only time off that still stands (upcoming or ongoing) can be edited or cancelled. */
const STANDING = ['upcoming', 'ongoing'];

/** A period's length: "2 days", "1 day 4 hrs", "4 hrs 30 mins", "30 mins". */
function durationOf(item) {
  const minutes = Math.max(0, Math.round((endOf(item) - startOf(item)) / 60000));
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  const parts = [
    days && `${days} day${days === 1 ? '' : 's'}`,
    hours && `${hours} hr${hours === 1 ? '' : 's'}`,
    // Minutes only matter for periods under a day
    !days && mins && `${mins} min${mins === 1 ? '' : 's'}`
  ].filter(Boolean);
  return parts.length ? parts.join(' ') : '—';
}

/** A date over its time, as the table cells show them. */
function DateTimeCell({ value }) {
  const parts = splitApiDateTime(value);
  if (!parts) return <span className="text-[#A8B6C6]">—</span>;
  return (
    <>
      <div className="whitespace-nowrap font-bold text-[var(--ink)]">{parts.date}</div>
      <div className="whitespace-nowrap text-[0.8125rem] text-[var(--muted)]">{parts.time}</div>
    </>
  );
}

/** The time off dialog: start and end (date and time, Indian time) and an optional reason. */
function timeOffDialog(item) {
  const start = apiDateTimeParts(item?.startsAt);
  const end = apiDateTimeParts(item?.endsAt);
  return {
    title: item ? 'Edit Time Off' : 'Add Time Off',
    fields: [
      { key: 'startDate', label: 'From Date', type: 'date', required: true, min: todayInIndia() },
      { key: 'endDate', label: 'To Date', type: 'date', required: true, min: todayInIndia() },
      { key: 'startTime', label: 'From Time', type: 'select', required: true, options: TIME_OPTIONS },
      {
        key: 'endTime',
        label: 'To Time',
        type: 'select',
        required: true,
        options: TIME_OPTIONS,
        validate: (value, values) =>
          toInstant(values.endDate, value) &&
          toInstant(values.startDate, values.startTime) &&
          new Date(toInstant(values.endDate, value)) <= new Date(toInstant(values.startDate, values.startTime))
            ? 'Must be after the start'
            : null
      },
      {
        key: 'reason',
        label: 'Reason',
        type: 'textarea',
        rows: 2,
        maxLength: 200,
        wide: true,
        hint: 'E.g. leave, a festival, training. Visible to staff.'
      }
    ],
    initial: {
      startDate: start?.date ?? '',
      startTime: start?.time ?? '09:00',
      endDate: end?.date ?? '',
      endTime: end?.time ?? '18:00',
      reason: item?.reason ?? ''
    }
  };
}

/**
 * The partner's time off (Module 5), below the weekly grid on the partner profile's Availability tab: a table (#, from,
 * to, duration, reason, Upcoming / Ongoing / Past / Cancelled / Ended Early tag) paged 5 at a time, upcoming and
 * ongoing periods first. Time off is never deleted: with PARTNER_AVAILABILITY_MANAGE write, Add and the row menu's Edit
 * and Cancel Leave (an optional reason; PUT /api/availability/time-off/cancel) are offered for time off that still
 * stands; leave under way ends at once. (Slot reservations are not used for now.)
 */
export default function PartnerSchedule({ partnerProfileId, onMessage }) {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PARTNER_AVAILABILITY_MANAGE', 'WRITE');
  const [data, setData] = useState({ timeOff: [], loaded: false, loadedAt: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const [dialog, setDialog] = useState(null); // { item } while the time off dialog is open
  const [cancelling, setCancelling] = useState(null);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/availability/time-off/${partnerProfileId}`)
      .catch(() => [])
      .then((timeOff) => !cancelled && setData({ timeOff, loaded: true, loadedAt: Date.now() }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, partnerProfileId, reloadKey]);

  const saveTimeOff = async (values) => {
    const body = {
      startsAt: toInstant(values.startDate, values.startTime),
      endsAt: toInstant(values.endDate, values.endTime),
      reason: values.reason.trim()
    };
    if (dialog.item) {
      await authRequest('/api/availability/time-off/update', {
        method: 'PUT',
        body: { timeOffId: dialog.item.timeOffId, ...body }
      });
    } else {
      await authRequest('/api/availability/time-off/add', {
        method: 'POST',
        body: { partnerProfileId, ...body, reason: body.reason || null }
      });
    }
    setDialog(null);
    onMessage(dialog.item ? 'Time off updated.' : 'Time off added.');
    reload();
  };

  const cancelTimeOff = async (values) => {
    const underWay = stageOf(cancelling, Date.now()) === 'ongoing';
    await authRequest('/api/availability/time-off/cancel', {
      method: 'PUT',
      body: { timeOffId: cancelling.timeOffId, reason: values.reason.trim() || null }
    });
    setCancelling(null);
    onMessage(underWay ? 'Leave ended now.' : 'Leave cancelled.');
    reload();
  };

  // Upcoming and ongoing first, then the rest, as of when the list was loaded
  const current = data.timeOff.filter((item) => STANDING.includes(stageOf(item, data.loadedAt)));
  const rows = [...current, ...data.timeOff.filter((item) => !current.includes(item))];
  const { pageRows, pageData, setPage } = useClientPaging(rows, PAGE_SIZE);
  const hasActions = canWrite;

  const actionsOf = (item, stage) =>
    canWrite && STANDING.includes(stage)
      ? [
          { label: 'Edit', icon: Pencil, onClick: () => setDialog({ item }) },
          { label: 'Cancel Leave', icon: CalendarX2, danger: true, onClick: () => setCancelling(item) }
        ]
      : [];

  return (
    <div className="mt-10">
      <InfoSection title="Time Off" divided={false}>
        <div className="mb-3 flex justify-end">
          {canWrite && (
            <button
              type="button"
              onClick={() => setDialog({ item: null })}
              className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
            >
              <Plus size={15} />
              Add Time Off
            </button>
          )}
        </div>
        {!data.loaded ? (
          <div className="h-24 animate-pulse rounded-xl bg-[var(--light)]" />
        ) : data.timeOff.length === 0 ? (
          <p className="rounded-xl bg-[var(--light)] px-4 py-5 text-center text-sm text-[var(--muted)]">
            No time off planned.
          </p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[44rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-12`}>#</th>
                    <th className={headClass}>From</th>
                    <th className={headClass}>To</th>
                    <th className={headClass}>Duration</th>
                    <th className={`${headClass} w-[30%]`}>Reason</th>
                    <th className={headClass}>Status</th>
                    {hasActions && <th className={`${headClass} w-12`}><span className="sr-only">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((item, i) => {
                    const stage = stageOf(item, data.loadedAt);
                    const actions = actionsOf(item, stage);
                    return (
                      <tr
                        key={item.timeOffId}
                        className="border-t border-[var(--line)] align-top text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                      >
                        <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                        <td className="px-3 py-3">
                          <DateTimeCell value={item.startsAt} />
                        </td>
                        <td className="px-3 py-3">
                          <DateTimeCell value={item.endsAt} />
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">{durationOf(item)}</td>
                        <td className="max-w-0 px-3 py-3">
                          {item.reason ? (
                            <span className="block truncate" title={item.reason}>
                              {item.reason}
                            </span>
                          ) : (
                            <span className="text-[#A8B6C6]">—</span>
                          )}
                          {item.cancelledAt && (
                            <span
                              className="mt-0.5 block truncate text-xs text-[var(--muted)]"
                              title={item.cancellationReason ?? undefined}
                            >
                              {stage === 'endedEarly' ? 'Ended' : 'Cancelled'} {when(item.cancelledAt)}
                              {item.cancellationReason && `: ${item.cancellationReason}`}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          <span
                            className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${STAGES[stage].className}`}
                          >
                            {STAGES[stage].label}
                          </span>
                        </td>
                        {hasActions && (
                          <td className="px-3 py-3 text-right">
                            {actions.length > 0 && (
                              <div className="flex justify-end">
                                <RowActions label="time off" actions={actions} />
                              </div>
                            )}
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination pageData={pageData} noun="time off" onPageChange={setPage} />
          </>
        )}
      </InfoSection>

      {dialog && (
        <FormModal
          {...timeOffDialog(dialog.item)}
          submitLabel={dialog.item ? 'Save Changes' : 'Add Time Off'}
          onSubmit={saveTimeOff}
          onClose={() => setDialog(null)}
        />
      )}
      {cancelling && (
        <FormModal
          title="Cancel Leave"
          fields={[
            {
              key: 'reason',
              label: 'Reason',
              type: 'textarea',
              rows: 2,
              maxLength: 200,
              wide: true,
              hint:
                stageOf(cancelling, data.loadedAt) === 'ongoing'
                  ? 'This leave has started: cancelling ends it now and the partner is available again.'
                  : `The leave from ${when(cancelling.startsAt)} to ${when(cancelling.endsAt)} is kept as cancelled and the partner is available then.`
            }
          ]}
          initial={{ reason: '' }}
          submitLabel="Cancel Leave"
          onSubmit={cancelTimeOff}
          onClose={() => setCancelling(null)}
        />
      )}
    </div>
  );
}

/** A backend date in milliseconds (0 if unreadable). */
function millisOf(value) {
  const parts = apiDateTimeParts(value);
  return parts ? new Date(toInstant(parts.date, parts.time)).getTime() : 0;
}

/** When a period of time off starts and ends, in milliseconds. */
const startOf = (item) => millisOf(item.startsAt);
const endOf = (item) => millisOf(item.endsAt);
