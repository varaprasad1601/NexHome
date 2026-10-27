import { ChevronDown, CircleHelp, LogOut, Search, ShieldCheck, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';
import { STAFF_ROLE_CODES } from '../../utils/adminSession';
import NotificationsMenu from './NotificationsMenu';

/** The signed-in admin's highest staff role (SUPER_USER before ADMIN before SUB_ADMIN). */
function primaryRole(user) {
  const active = user.roles.filter((role) => role.isActive);
  return STAFF_ROLE_CODES.map((code) => active.find((role) => role.roleCode === code)).find(Boolean) ?? active[0];
}

function initials(text) {
  return text
    .split(/\s+/)
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/** The menu button: three bars, long, short, long. */
function MenuButton({ onClick }) {
  return (
    <button
      type="button"
      className="group flex h-10 w-10 flex-col items-start justify-center gap-[0.3125rem] rounded-lg pl-2.5 hover:bg-[var(--light)]"
      aria-label="Toggle menu"
      onClick={onClick}
    >
      <span className="h-[0.1562rem] w-5 rounded-full bg-[var(--ink)]" />
      <span className="h-[0.1562rem] w-3 rounded-full bg-[var(--ink)] transition-all group-hover:w-5" />
      <span className="h-[0.1562rem] w-5 rounded-full bg-[var(--ink)]" />
    </button>
  );
}

/** The signed-in admin's initials: a solid blue circle with a white ring and border, and a green "online" dot. */
function Avatar({ text }) {
  return (
    <span className="relative flex-shrink-0">
      <span className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-white bg-[var(--blue-2)] text-sm font-black text-white shadow-[0_0_0_2px_#C7E3FC]">
        {text}
      </span>
      <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-white bg-[#22C55E]" aria-hidden="true" />
    </span>
  );
}

/** A row of the account menu: an icon and the label. */
function MenuItem({ icon: Icon, label, onClick, danger = false }) {
  return (
    <button
      type="button"
      role="menuitem"
      className={`flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-bold transition ${
        danger ? 'text-[#C53030] hover:bg-[#FDECEC]' : 'text-[var(--ink)] hover:bg-[var(--light)]'
      }`}
      onClick={onClick}
    >
      <Icon size={15} strokeWidth={2.2} />
      {label}
    </button>
  );
}

/** Menu button, search, notifications, help and the account menu. */
export default function AdminTopbar({ onMenuClick }) {
  const { session, signOut } = useAdminAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  const user = session.user;
  const roleName = primaryRole(user)?.roleName ?? 'Admin';

  useEffect(() => {
    if (!menuOpen) return undefined;
    const close = (e) => {
      if (!menuRef.current?.contains(e.target)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [menuOpen]);

  const openMyProfile = () => {
    setMenuOpen(false);
    navigate('/admin/profile');
  };

  const handleSignOut = async () => {
    setMenuOpen(false);
    await signOut();
    navigate('/admin', { replace: true });
  };

  return (
    <header className="sticky top-0 z-20 flex h-[4.25rem] items-center gap-3 border-b border-[var(--line)] bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8">
      <MenuButton onClick={onMenuClick} />

      <label className="relative ml-1 hidden max-w-md flex-1 md:block">
        <span className="sr-only">Search</span>
        <Search size={15} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
        <input
          type="search"
          placeholder="Search customers, bookings, partners, services…"
          className="w-full rounded-full border border-[var(--line)] bg-[var(--light)] py-2.5 pl-10 pr-4 text-sm text-[var(--ink)] outline-none transition placeholder:text-[#8A9AAD] focus:border-[var(--blue)] focus:bg-white focus:ring-2 focus:ring-[var(--blue)]/15"
        />
      </label>

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        <NotificationsMenu />
        <button type="button" className="rounded-full p-2.5 text-[var(--ink)] hover:bg-[var(--light)]" aria-label="Help">
          <CircleHelp size={18} />
        </button>
        <span className="mx-1 hidden h-8 w-px bg-[var(--line)] sm:block" />

        <div className="relative" ref={menuRef}>
          <button
            type="button"
            className={`flex items-center gap-3 rounded-xl px-2 py-1.5 transition ${
              menuOpen ? 'bg-[#EDF6FD]' : 'hover:bg-[var(--light)]'
            }`}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span className="hidden text-right leading-tight sm:block">
              <span className="block text-sm font-black text-[var(--ink)]">
                {user.countryCode} {user.mobileNumber}
              </span>
              <span className="mt-0.5 flex items-center justify-end gap-1 text-xs font-bold text-[var(--muted)]">
                <ShieldCheck size={12} strokeWidth={2.4} className="text-[var(--green)]" />
                {roleName}
              </span>
            </span>
            <Avatar text={initials(roleName)} />
            <ChevronDown
              size={15}
              className={`text-[var(--muted)] transition-transform duration-200 ${menuOpen ? 'rotate-180 text-[var(--blue)]' : ''}`}
            />
          </button>

          {menuOpen && (
            <div
              role="menu"
              className="absolute right-0 mt-2.5 w-48 rounded-2xl border border-[var(--line)] bg-white p-1.5 shadow-[0_18px_44px_rgba(14,51,89,0.16)]"
            >
              <MenuItem icon={UserRound} label="My Profile" onClick={openMyProfile} />
              <MenuItem icon={LogOut} label="Sign out" onClick={handleSignOut} danger />
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
