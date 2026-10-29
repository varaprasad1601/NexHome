import { CalendarClock, CalendarPlus, UserPen, UserPlus } from 'lucide-react';

import { InfoRow } from './ProfileParts';
import { splitApiDateTime } from '../../../utils/format';

const when = (at) => {
  const parts = splitApiDateTime(at);
  return parts ? `${parts.date}, ${parts.time}` : null;
};
const who = (entry) =>
  entry?.name ? (
    <>
      {entry.name}
      {entry.role && <span className="font-normal text-[var(--muted)]"> · {entry.role}</span>}
    </>
  ) : null;

/**
 * A record's audit columns above an activity table, as plain icon rows (no card): created on / by and last updated on
 * / by. `created` and `updated` are { at, name, role }; without an update the creation is shown as the last one.
 */
export default function AuditSummary({ created, updated }) {
  const last = updated?.at ? updated : created;
  return (
    <div className="grid grid-cols-1 gap-x-10 sm:grid-cols-2 xl:grid-cols-4">
      <InfoRow icon={CalendarPlus} label="Created On" value={when(created?.at)} />
      <InfoRow icon={UserPlus} label="Created By" value={who(created)} />
      <InfoRow icon={CalendarClock} label="Last Updated On" value={when(last?.at)} />
      <InfoRow icon={UserPen} label="Last Updated By" value={who(last)} />
    </div>
  );
}
