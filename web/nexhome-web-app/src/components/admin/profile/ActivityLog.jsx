import AuditSummary from './AuditSummary';
import Pagination from '../common/Pagination';
import { ACTIVITY_EVENTS, activitySubject } from '../../../data/activityEvents';
import useClientPaging from '../../../hooks/useClientPaging';
import { splitApiDateTime } from '../../../utils/format';

const cellHead =
  'whitespace-nowrap px-3 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';

/** A backend date ("Thu, 29 Oct 2026 06:44:54 IST") as milliseconds, for sorting. */
const apiTime = (value) => Date.parse(String(value).replace(' IST', ' GMT+0530')) || 0;

/** One paged table of audit entries: date and time, action, what, and by whom with their role. */
function ActivityTable({ entries, formatSubject }) {
  const paging = useClientPaging(entries);
  if (entries.length === 0) return <p className="text-sm text-[var(--muted)]">No activity yet.</p>;
  return (
    <>
      <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
        <table className="w-full min-w-[44rem] text-sm">
          <thead className="bg-[var(--light)]">
            <tr>
              <th className={cellHead}>Date &amp; Time</th>
              <th className={cellHead}>Action</th>
              <th className={cellHead}>What</th>
              <th className={cellHead}>By</th>
            </tr>
          </thead>
          <tbody>
            {paging.pageRows.map((entry, index) => {
              const when = splitApiDateTime(entry.at);
              const action = ACTIVITY_EVENTS[entry.event] ?? { label: entry.event, tone: 'bg-[var(--light)]' };
              const subject = formatSubject ? formatSubject(entry) : activitySubject(entry);
              return (
                <tr
                  key={`${entry.event}-${entry.at}-${index}`}
                  className="border-t border-[var(--line)] text-[var(--ink)]"
                >
                  <td className="whitespace-nowrap px-3 py-3">
                    <span className="font-bold">{when?.date ?? '—'}</span>
                    <span className="ml-2 text-[var(--muted)]">{when?.time}</span>
                  </td>
                  <td className="px-3 py-3">
                    <span
                      className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${action.tone}`}
                    >
                      {action.label}
                    </span>
                  </td>
                  <td className="px-3 py-3">{subject || <span className="text-[var(--muted)]">—</span>}</td>
                  <td className="whitespace-nowrap px-3 py-3">
                    {entry.byName ? (
                      <>
                        <span className="font-bold">{entry.byName}</span>
                        {entry.byRole && <span className="text-[var(--muted)]"> · {entry.byRole}</span>}
                      </>
                    ) : (
                      <span className="text-[var(--muted)]">Unknown</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <Pagination pageData={paging.pageData} noun="entries" onPageChange={paging.setPage} />
    </>
  );
}

/**
 * The Activity Logs tab of the profile pages, from the audit columns (NexHomeAuditEntry): one paged table, newest
 * first, or with `sections` ([{ title, events }]) one table per section. Entries that have not happened (no `at`) are
 * left out. `formatSubject(entry)` overrides the What column. Above the tables, the profile's own audit columns
 * (`AuditSummary`, from its PROFILE_CREATED / PROFILE_UPDATED lines).
 */
export default function ActivityLog({ entries, sections, formatSubject, loading }) {
  if (loading) return <div className="h-40 animate-pulse rounded-xl bg-[var(--light)]" />;
  const happened = (entries ?? []).filter((entry) => entry.at).sort((a, b) => apiTime(b.at) - apiTime(a.at));
  const audit = (event) => {
    const entry = happened.find((line) => line.event === event);
    return entry && { at: entry.at, name: entry.byName, role: entry.byRole };
  };
  return (
    <div className="space-y-8">
      <AuditSummary created={audit('PROFILE_CREATED')} updated={audit('PROFILE_UPDATED')} />
      {sections ? (
        sections.map((section) => (
          <section key={section.title}>
            <h3 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">{section.title}</h3>
            <ActivityTable
              entries={happened.filter((entry) => section.events.includes(entry.event))}
              formatSubject={formatSubject}
            />
          </section>
        ))
      ) : (
        <ActivityTable entries={happened} formatSubject={formatSubject} />
      )}
      <p className="text-xs text-[var(--muted)]">
        From when each record was created and last changed; earlier edits of the same record are not kept.
      </p>
    </div>
  );
}
