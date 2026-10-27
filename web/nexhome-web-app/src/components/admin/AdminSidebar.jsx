import { ChevronDown } from 'lucide-react';
import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';
import { ADMIN_NAV, canOpen } from '../../data/adminNavigation';

const NAV_PATHS = ADMIN_NAV.flatMap((entry) => (entry.items ? entry.items.map((item) => item.to) : [entry.to]));

/** The sidebar link of a page: the longest link path it is at or below (an admin's profile belongs to Admins). */
const activeLinkOf = (pathname) =>
  NAV_PATHS.filter((to) => pathname === to || pathname.startsWith(`${to}/`)).sort((a, b) => b.length - a.length)[0] ??
  null;

const groupOf = (pathname) => {
  const link = activeLinkOf(pathname);
  return ADMIN_NAV.find((entry) => entry.items?.some((item) => item.to === link))?.label ?? null;
};

/**
 * A link's classes. The active one gets a full-height bar outside its highlight, with a gap between them: at the
 * sidebar edge for top-level links, on the tree line for links inside a group.
 */
const itemClass =
  (nested) =>
  ({ isActive }) =>
  `relative flex items-center gap-3 whitespace-nowrap rounded-lg py-3 pl-3 pr-3 text-[0.9375rem] transition ${
    isActive
      ? 'bg-[#EDF6FD] font-bold text-[var(--blue)] before:absolute before:inset-y-0 before:w-[0.1875rem] before:rounded-full before:bg-[var(--blue)]' +
        (nested ? ' before:-left-[calc(0.625rem+2px)]' : ' before:-left-2.5')
      : 'text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]'
  }`;

/**
 * The admin navigation: a fixed column on desktop, a drawer on smaller screens. One group is open at a time
 * (opening another closes it), starting with the group of the current page. It scrolls without a scrollbar. Links
 * the admin may not open (no READ on their function, see canOpen) are left out, and so are groups without any.
 */
export default function AdminSidebar({ collapsed, drawerOpen, onClose }) {
  const { pathname } = useLocation();
  const { can, isSuperUser } = useAdminAuth();
  const [openGroup, setOpenGroup] = useState(() => groupOf(pathname));
  const activeGroup = groupOf(pathname);
  const activeLink = activeLinkOf(pathname);

  return (
    <>
      {drawerOpen && (
        <button type="button" aria-label="Close menu" className="fixed inset-0 z-30 bg-[var(--navy)]/30 lg:hidden" onClick={onClose} />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-[16.5rem] flex-col border-r border-[var(--line)] bg-white transition-transform duration-200 ${
          drawerOpen ? 'translate-x-0' : '-translate-x-full'
        } ${collapsed ? 'lg:-translate-x-full' : 'lg:translate-x-0'}`}
        aria-label="Admin navigation"
      >
        {/* The brand exactly as in the website header: logo, name and tagline */}
        <Link
          to="/admin/dashboard"
          className="flex h-[4.25rem] flex-shrink-0 items-center gap-2.5 border-b border-[var(--line)] px-4"
          aria-label="NexHome admin dashboard"
        >
          <img className="h-12 w-auto flex-shrink-0 object-contain" src="/assets/NexHome_Logo.png" alt="NexHome Logo" />
          <div className="flex flex-col justify-end pb-0.5">
            <div className="text-[1.5rem] font-normal leading-none tracking-tight" style={{ fontFamily: "'ManropeCustom', sans-serif" }}>
              <span className="font-semibold text-[#0B438B]">Nex</span>
              <span className="font-semibold text-[#168F5B]">Home</span>
            </div>
            <div
              className="mt-[0.125rem] whitespace-nowrap text-[0.6875rem] font-bold leading-none tracking-wider text-gray-500"
              style={{ fontFamily: "'DMSansCustom', sans-serif" }}
            >
              Better Living, Every Day
            </div>
          </div>
        </Link>

        <nav className="nh-scroll-hidden flex-1 overflow-y-auto px-3 py-4">
          <ul className="space-y-1">
            {ADMIN_NAV.map((entry) => {
              const Icon = entry.icon;
              // Only links the admin may open (READ on their function); a group with none is left out
              const items = entry.items?.filter((item) => canOpen(item, can, isSuperUser));
              if (entry.items && items.length === 0) return null;
              if (!entry.items && !canOpen(entry, can, isSuperUser)) return null;
              if (!entry.items) {
                return (
                  <li key={entry.label}>
                    <Link
                      to={entry.to}
                      className={itemClass(false)({ isActive: entry.to === activeLink })}
                      aria-current={entry.to === activeLink ? 'page' : undefined}
                      onClick={() => setOpenGroup(null)}
                    >
                      <Icon size={18} strokeWidth={1.9} className="flex-shrink-0" />
                      {entry.label}
                    </Link>
                  </li>
                );
              }
              const open = openGroup === entry.label;
              const containsActive = activeGroup === entry.label;
              return (
                <li key={entry.label}>
                  <button
                    type="button"
                    className={`flex w-full items-center gap-3 whitespace-nowrap rounded-lg px-3 py-3 text-left text-[0.9375rem] font-bold transition hover:bg-[var(--light)] ${
                      containsActive || open ? 'text-[var(--blue)]' : 'text-[var(--ink)]'
                    }`}
                    aria-expanded={open}
                    onClick={() => setOpenGroup(open ? null : entry.label)}
                  >
                    <Icon size={18} strokeWidth={1.9} className="flex-shrink-0" />
                    <span className="flex-1">{entry.label}</span>
                    <ChevronDown
                      size={14}
                      className={`flex-shrink-0 text-[var(--muted)] transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
                    />
                  </button>
                  {open && (
                    <ul className="mb-1 ml-[1.3125rem] mt-0.5 space-y-0.5 border-l border-[var(--line)] pl-2.5">
                      {items.map((item) => (
                        <li key={item.to}>
                          <Link
                            to={item.to}
                            className={itemClass(true)({ isActive: item.to === activeLink })}
                            aria-current={item.to === activeLink ? 'page' : undefined}
                          >
                            {item.label}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </nav>
      </aside>
    </>
  );
}
