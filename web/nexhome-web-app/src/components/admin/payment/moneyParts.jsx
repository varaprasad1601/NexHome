import { Link } from 'react-router-dom';

import { cardClass, linkClass } from '../system/systemStyles';
import { moneyStatusClass } from '../../../data/paymentOptions';
import { splitApiDateTime } from '../../../utils/format';

/** A status badge of any Module 7 status, labelled from its lookup. */
export function MoneyBadge({ status, label }) {
  if (!status) return <span className="text-[var(--muted)]">—</span>;
  return (
    <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${moneyStatusClass(status)}`}>
      {label ?? status}
    </span>
  );
}

/** A backend date as "08 Oct 2026, 02:29 PM" (a dash when missing). */
export function When({ value }) {
  const parts = splitApiDateTime(value);
  if (!parts) return <span className="text-[var(--muted)]">—</span>;
  return (
    <span className="whitespace-nowrap">
      <span className="font-bold">{parts.date}</span>
      <span className="ml-1.5 text-[var(--muted)]">{parts.time}</span>
    </span>
  );
}

// Links look the same on every screen (systemStyles.linkClass)

export function BookingLink({ id, publicId }) {
  return id ? (
    <Link to={`/admin/bookings/${id}`} className={linkClass}>
      {publicId}
    </Link>
  ) : (
    <span className="text-[var(--muted)]">—</span>
  );
}

export function PartnerLink({ id, name }) {
  return id ? (
    <Link to={`/admin/users/partners/${id}`} className={linkClass}>
      {name ?? 'Name not added'}
    </Link>
  ) : (
    <span className="text-[var(--muted)]">—</span>
  );
}

export function CustomerLink({ id, name }) {
  return id ? (
    <Link to={`/admin/users/customers/${id}`} className={linkClass}>
      {name ?? 'Name not added'}
    </Link>
  ) : (
    <span className="text-[var(--muted)]">—</span>
  );
}

/** A count tile, as the lists' tiles: icon, label, value and an optional note. */
export function MoneyTile({ icon: Icon, tone, label, value, note }) {
  return (
    <div className={`flex min-w-[13rem] flex-1 items-center gap-3 xl:min-w-0 ${cardClass}`}>
      <span className={`flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full ${tone}`}>
        <Icon size={20} strokeWidth={2} />
      </span>
      <div className="min-w-0">
        <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{label}</div>
        <div className="mt-1 h-7 text-[1.375rem] font-black leading-7 text-[var(--ink)]">
          {value ?? <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />}
        </div>
        {note && <div className="text-xs text-[var(--muted)]">{note}</div>}
      </div>
    </div>
  );
}

/** Tabs of a payments screen. */
export function MoneyTabs({ tabs, active, onChange }) {
  return (
    <div className="mb-4 flex gap-6 border-b border-[var(--line)]" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          aria-selected={active === tab.key}
          onClick={() => onChange(tab.key)}
          className={`relative pb-3 text-sm font-bold transition ${
            active === tab.key
              ? 'text-[var(--blue)] after:absolute after:inset-x-0 after:-bottom-px after:h-[0.1875rem] after:rounded-full after:bg-[var(--blue)]'
              : 'text-[var(--muted)] hover:text-[var(--ink)]'
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
