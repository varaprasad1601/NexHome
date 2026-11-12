import { CalendarDays, CornerDownLeft, CreditCard, LayoutGrid, Search, UserRound, Wrench, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';
import { ADMIN_NAV, canOpen } from '../../data/adminNavigation';
import { formatAmount, serviceNameOf } from '../../data/bookingOptions';
import useDebounced from '../../hooks/useDebounced';

/** What the search looks in: each list the backend already searches, only with read on its function, 5 each. */
const SOURCES = [
  {
    key: 'bookings',
    title: 'Bookings',
    icon: CalendarDays,
    fn: 'BOOKING_MANAGE',
    path: (q) => `/api/bookings?search=${q}&size=5`,
    toItem: (b) => ({
      id: b.bookingId,
      label: b.publicId,
      detail: [serviceNameOf(b), b.customerName].filter(Boolean).join(' · '),
      to: `/admin/bookings/${b.bookingId}`
    })
  },
  {
    key: 'customers',
    title: 'Customers',
    icon: UserRound,
    fn: 'CUSTOMER_MANAGE',
    path: (q) => `/api/users/customers?search=${q}&size=5`,
    toItem: (c) => ({
      id: c.userId,
      label: [c.firstName, c.lastName].filter(Boolean).join(' ') || 'Name not added',
      detail: [c.publicId, c.mobileNumber && `${c.countryCode} ${c.mobileNumber}`].filter(Boolean).join(' · '),
      // An account without a customer profile has no page of its own: the list, searched
      to: c.customerProfileId ? `/admin/users/customers/${c.customerProfileId}` : '/admin/users/customers'
    })
  },
  {
    key: 'partners',
    title: 'Partners',
    icon: UserRound,
    fn: 'PARTNER_MANAGE',
    path: (q) => `/api/users/partners?search=${q}&size=5`,
    toItem: (p) => ({
      id: p.userId,
      label: [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Name not added',
      detail: [p.publicId, p.mainServiceCategoryName, p.mobileNumber && `${p.countryCode} ${p.mobileNumber}`]
        .filter(Boolean)
        .join(' · '),
      to: p.partnerProfileId ? `/admin/users/partners/${p.partnerProfileId}` : '/admin/users/partners'
    })
  },
  {
    key: 'services',
    title: 'Services',
    icon: Wrench,
    fn: 'CATALOG_MANAGE',
    path: (q) => `/api/catalog/services?includeInactive=true&search=${q}&size=5`,
    toItem: (s) => ({
      id: s.serviceId,
      label: s.name,
      detail: [s.publicId, s.categoryName].filter(Boolean).join(' · '),
      to: `/admin/services/${s.serviceId}`
    })
  },
  {
    key: 'orders',
    title: 'Payment Orders',
    icon: CreditCard,
    fn: 'PAYMENT_MANAGE',
    path: (q) => `/api/payments/orders?search=${q}&size=5`,
    toItem: (o) => ({
      id: o.paymentOrderId,
      label: o.publicId,
      detail: [o.customerName, formatAmount(o.amount), o.paymentStatus].filter(Boolean).join(' · '),
      to: `/admin/payments/customers/orders/${o.paymentOrderId}`
    })
  }
];

/**
 * The top bar's search (Ctrl+K or / to focus): pages of the console by name, and, from 2 characters, bookings,
 * customers, partners, services and payment orders through their own list endpoints (`search`, 5 each), only those
 * the signed-in staff member may read. Typing pauses 300 ms before searching; a newer search replaces an older one's
 * results. Arrow keys move, Enter opens, Escape closes.
 */
export default function GlobalSearch() {
  const { authRequest, can, isSuperUser } = useAdminAuth();
  const navigate = useNavigate();
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  // The records found, with the search they belong to (an older search's results are never shown for a newer one)
  const [found, setFound] = useState({ query: '', data: {} });
  const [active, setActive] = useState(0);
  const inputRef = useRef(null);
  const boxRef = useRef(null);
  const searchId = useRef(0);
  const query = useDebounced(text.trim(), 300);

  // Pages: every console page the admin may open whose name matches
  const pages = useMemo(() => {
    const needle = text.trim().toLowerCase();
    if (!needle) return [];
    return ADMIN_NAV.flatMap((entry) =>
      (entry.items ?? [entry]).map((item) => ({ ...item, group: entry.items ? entry.label : null }))
    )
      .filter((item) => canOpen(item, can, isSuperUser))
      .filter((item) => `${item.group ?? ''} ${item.label}`.toLowerCase().includes(needle))
      .slice(0, 5)
      .map((item) => ({ id: item.to, label: item.label, detail: item.group, to: item.to }));
  }, [text, can, isSuperUser]);

  const sources = useMemo(() => SOURCES.filter((source) => can(source.fn, 'READ')), [can]);

  // The records: one request per source, in parallel, once typing pauses
  useEffect(() => {
    if (query.length < 2) return undefined;
    const id = ++searchId.current;
    const q = encodeURIComponent(query);
    sources.forEach((source) => {
      authRequest(source.path(q))
        .then((page) => (page?.content ?? []).map(source.toItem))
        .catch(() => [])
        .then((items) => {
          if (id !== searchId.current) return;
          setFound((current) => ({
            query,
            data: { ...(current.query === query ? current.data : {}), [source.key]: items }
          }));
        });
    });
    return undefined;
  }, [query, sources, authRequest]);

  // Ctrl+K or "/" (outside a field) focuses the search
  useEffect(() => {
    const onKey = (event) => {
      const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName) || document.activeElement?.isContentEditable;
      if ((event.key === 'k' && (event.ctrlKey || event.metaKey)) || (event.key === '/' && !typing)) {
        event.preventDefault();
        inputRef.current?.focus();
        setOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // A click outside closes it
  useEffect(() => {
    if (!open) return undefined;
    const close = (event) => {
      if (!boxRef.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const searching = query.length >= 2 && query === text.trim();
  const sections = [
    ...(pages.length ? [{ key: 'pages', title: 'Pages', icon: LayoutGrid, items: pages }] : []),
    ...(searching
      ? sources.map((source) => ({
          key: source.key,
          title: source.title,
          icon: source.icon,
          items: found.query === query ? (found.data[source.key] ?? null) : null // null: still loading
        }))
      : [])
  ];
  const flat = sections.flatMap((section) => section.items ?? []);
  const loading = searching && sections.some((section) => section.items === null);
  const nothing = searching && !loading && flat.length === 0;
  const activeIndex = Math.min(active, Math.max(flat.length - 1, 0));

  const go = (item) => {
    if (!item) return;
    setOpen(false);
    setText('');
    inputRef.current?.blur();
    navigate(item.to);
  };

  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      setOpen(false);
      inputRef.current?.blur();
    }
    else if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((index) => Math.min(index + 1, flat.length - 1));
    }
    else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    }
    else if (event.key === 'Enter') {
      event.preventDefault();
      go(flat[activeIndex]);
    }
  };

  let position = -1;
  const showPanel = open && text.trim().length > 0;

  return (
    <div ref={boxRef} className="relative ml-1 hidden max-w-md flex-1 md:block">
      <label className="relative block">
        <span className="sr-only">Search</span>
        <Search size={15} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={showPanel}
          aria-controls="admin-search-results"
          autoComplete="off"
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="Search customers, bookings, partners, services…"
          className="w-full rounded-full border border-[var(--line)] bg-[var(--light)] py-2.5 pl-10 pr-10 text-sm text-[var(--ink)] outline-none transition placeholder:text-[#8A9AAD] focus:border-[var(--blue)] focus:bg-white focus:ring-2 focus:ring-[var(--blue)]/15"
        />
        {/* Ctrl+K and / still focus the field; no hint is shown */}
        {text ? (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => {
              setText('');
              inputRef.current?.focus();
            }}
            className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full p-1 text-[var(--muted)] hover:bg-[var(--line)]"
          >
            <X size={14} />
          </button>
        ) : null}
      </label>

      {showPanel && (
        <div
          id="admin-search-results"
          role="listbox"
          className="absolute left-0 right-0 top-full z-40 mt-2 max-h-[70vh] overflow-y-auto rounded-2xl border border-[var(--line)] bg-white p-2 shadow-[0_18px_40px_-12px_rgba(11,49,95,0.28)]"
        >
          {text.trim().length < 2 && !pages.length && (
            <p className="px-3 py-3 text-[0.8125rem] text-[var(--muted)]">Type at least 2 characters to search records.</p>
          )}
          {sections.map((section) => {
            const Icon = section.icon;
            if (section.items && !section.items.length) return null;
            return (
              <div key={section.key} className="py-1">
                <p className="px-3 pb-1 pt-1.5 text-[0.6875rem] font-black uppercase tracking-wide text-[var(--muted)]">
                  {section.title}
                </p>
                {section.items === null ? (
                  <div className="mx-3 my-1 h-8 animate-pulse rounded-lg bg-[var(--light)]" />
                ) : (
                  section.items.map((item) => {
                    position += 1;
                    const index = position;
                    const on = index === activeIndex;
                    return (
                      <button
                        key={`${section.key}-${item.id}`}
                        type="button"
                        role="option"
                        aria-selected={on}
                        onMouseEnter={() => setActive(index)}
                        onClick={() => go(item)}
                        className={`flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left ${on ? 'bg-[#EDF6FD]' : ''}`}
                      >
                        <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-[var(--light)] text-[var(--blue)]">
                          <Icon size={15} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-bold text-[var(--ink)]">{item.label}</span>
                          {item.detail && <span className="block truncate text-xs text-[var(--muted)]">{item.detail}</span>}
                        </span>
                        {on && <CornerDownLeft size={14} className="text-[var(--muted)]" />}
                      </button>
                    );
                  })
                )}
              </div>
            );
          })}
          {nothing && <p className="px-3 py-3 text-[0.8125rem] text-[var(--muted)]">Nothing matches “{text.trim()}”.</p>}
        </div>
      )}
    </div>
  );
}
