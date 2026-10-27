import { Ellipsis, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { accountStatusLabel, staffRoleOf, statusBadgeClass } from '../../../data/staffOptions';
import { splitApiDateTime } from '../../../utils/format';

const AVATAR_TINTS = [
  'bg-[#EDF6FD] text-[var(--blue)]',
  'bg-[#E9F6EF] text-[var(--green-2)]',
  'bg-[#E8EEF6] text-[var(--navy)]',
  'bg-[#FFF7DE] text-[#8A6508]'
];

/** The status actions of a row's menu, besides the Active toggle. */
const ACTIVATE = { status: 'ACTIVE', label: 'Activate' };
const DEACTIVATE = { status: 'DEACTIVATED', label: 'Deactivate' };
const SUSPEND = { status: 'SUSPENDED', label: 'Suspend', danger: true };

/** The menu's actions for each current status; a suspended (or older blocked) account can only be activated. */
const MENU_ACTIONS = {
  ACTIVE: [DEACTIVATE, SUSPEND],
  DEACTIVATED: [ACTIVATE, SUSPEND],
  SUSPENDED: [ACTIVATE],
  BLOCKED: [ACTIVATE]
};

/** Names come from the staff profile (created with the account), which admins fill in under My Profile. */
function hasProfile(staff) {
  return Boolean(staff.firstName || staff.lastName);
}

/** The name, or the mobile number while the profile is missing (for labels and menus). */
function fullName(staff) {
  return [staff.firstName, staff.lastName].filter(Boolean).join(' ') || staff.mobileNumber;
}

function initials(staff) {
  return [staff.firstName, staff.lastName]
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/**
 * The row's "..." menu. It is positioned against the viewport (fixed), so the table's scroll container cannot clip
 * it, and opens upwards when there is no room below; scrolling or resizing closes it.
 */
function RowMenu({ staff, onStatusChange, disabled }) {
  const [position, setPosition] = useState(null);
  const ref = useRef(null);
  const open = position !== null;

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setPosition(null);
    };
    const close = () => setPosition(null);
    document.addEventListener('mousedown', closeOutside);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  const toggle = (e) => {
    if (open) {
      setPosition(null);
      return;
    }
    const rect = e.currentTarget.getBoundingClientRect();
    const upwards = window.innerHeight - rect.bottom < 200;
    setPosition({
      right: window.innerWidth - rect.right,
      ...(upwards ? { bottom: window.innerHeight - rect.top + 4 } : { top: rect.bottom + 4 })
    });
  };

  return (
    <div ref={ref}>
      <button
        type="button"
        className="rounded-md p-1.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)] disabled:cursor-not-allowed disabled:opacity-40"
        aria-label={`Actions for ${fullName(staff)}`}
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        onClick={toggle}
      >
        <Ellipsis size={18} />
      </button>
      {open && (
        <div
          role="menu"
          style={position}
          className="fixed z-30 w-40 rounded-xl border border-[var(--line)] bg-white p-1.5 text-left shadow-[var(--shadow)]"
        >
          {(MENU_ACTIONS[staff.accountStatus] ?? [ACTIVATE]).map((action) => (
            <button
              key={action.status}
              type="button"
              role="menuitem"
              className={`block w-full rounded-lg px-3 py-2 text-left text-sm font-bold ${
                action.danger ? 'text-[#C53030] hover:bg-[#FDECEC]' : 'text-[var(--ink)] hover:bg-[var(--light)]'
              }`}
              onClick={() => {
                setPosition(null);
                onStatusChange(staff, action.status);
              }}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * The staff list. Every text column has a width limit and cuts longer values with "..." (full text on hover), so the
 * table fits the screen. The status is a badge; the row menu changes it (MENU_ACTIONS), and is
 * off for the signed-in admin's own row (nobody changes their own status) and while a change is being saved.
 * `readOnly` (no write access) drops the Actions column. Public ids link to `profilePath`/:adminProfileId, the list's
 * own details page.
 */
export default function StaffTable({
  rows,
  firstRowNumber,
  loading,
  currentUserId,
  profilePath = '/admin/users/admins',
  readOnly = false,
  pendingUserId,
  onStatusChange,
  selected,
  onSelect
}) {
  const allSelected = rows.length > 0 && rows.every((row) => selected.has(row.userId));
  const headClass = 'whitespace-nowrap px-2.5 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';

  return (
    <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
      <table className="w-full min-w-[48rem] text-sm">
        <thead className="bg-[var(--light)]">
          <tr>
            <th className={`${headClass} w-10`}>
              <input
                type="checkbox"
                className="h-4 w-4 accent-[var(--blue)]"
                checked={allSelected}
                onChange={(e) => onSelect(rows.map((row) => row.userId), e.target.checked)}
                aria-label="Select all on this page"
              />
            </th>
            <th className={headClass}>#</th>
            <th className={headClass}>Public ID</th>
            <th className={`${headClass} w-[10rem]`}>Name</th>
            <th className={`${headClass} w-[11rem]`}>Email</th>
            <th className={headClass}>Mobile</th>
            <th className={headClass}>Role</th>
            <th className={headClass}>Status</th>
            <th className={headClass}>Last Login</th>
            {!readOnly && <th className={`${headClass} text-right`}>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {loading &&
            Array.from({ length: 5 }, (_, i) => (
              <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                <td colSpan={readOnly ? 9 : 10} className="px-3 py-4">
                  <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                </td>
              </tr>
            ))}
          {!loading && rows.length === 0 && (
            <tr className="border-t border-[var(--line)]">
              <td colSpan={readOnly ? 9 : 10} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                No admins match your search or filters.
              </td>
            </tr>
          )}
          {!loading &&
            rows.map((staff, i) => {
              const role = staffRoleOf(staff.roleCode);
              const lastLogin = splitApiDateTime(staff.lastLoginAt);
              const own = staff.userId === currentUserId;
              const busy = pendingUserId === staff.userId;
              return (
                <tr key={staff.userId} className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]">
                  <td className="px-2.5 py-3">
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-[var(--blue)]"
                      checked={selected.has(staff.userId)}
                      onChange={(e) => onSelect([staff.userId], e.target.checked)}
                      aria-label={`Select ${fullName(staff)}`}
                    />
                  </td>
                  <td className="px-2.5 py-3 text-[var(--muted)]">{firstRowNumber + i}</td>
                  <td className="whitespace-nowrap px-2.5 py-3">
                    {staff.publicId ? (
                      <Link
                        to={`${profilePath}/${staff.adminProfileId}`}
                        className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                        title={`${staff.publicId}: view profile`}
                      >
                        {staff.publicId}
                      </Link>
                    ) : (
                      <span className="text-[var(--muted)]">—</span>
                    )}
                  </td>
                  <td className="px-2.5 py-3">
                    <span className="flex w-[10rem] min-w-0 items-center gap-2 whitespace-nowrap">
                      {hasProfile(staff) ? (
                        <span className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-black ${AVATAR_TINTS[i % AVATAR_TINTS.length]}`}>
                          {initials(staff)}
                        </span>
                      ) : (
                        <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-[var(--light)] text-[#A8B6C6]">
                          <UserRound size={15} strokeWidth={2} />
                        </span>
                      )}
                      {hasProfile(staff) ? (
                        <span className="min-w-0 truncate font-bold" title={fullName(staff)}>
                          {fullName(staff)}
                        </span>
                      ) : (
                        <span className="min-w-0 truncate text-[var(--muted)]" title="Added by the admin under My Profile">
                          Not added yet
                        </span>
                      )}
                      {own && <span className="flex-shrink-0 rounded-full bg-[#EDF6FD] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--blue)]">You</span>}
                    </span>
                  </td>
                  <td className="px-2.5 py-3">
                    {staff.email ? (
                      <span className="block w-[11rem] truncate" title={staff.email}>
                        {staff.email}
                      </span>
                    ) : (
                      <span className="text-[var(--muted)]">—</span>
                    )}
                  </td>
                  <td className="px-2.5 py-3">
                    <span className="block max-w-[8.75rem] truncate" title={`${staff.countryCode} ${staff.mobileNumber}`}>
                      {staff.countryCode} {staff.mobileNumber}
                    </span>
                  </td>
                  <td className="px-2.5 py-3">
                    <span
                      className={`inline-block max-w-[7rem] truncate rounded-md px-2.5 py-1 align-middle text-xs font-bold ${role?.badge ?? 'bg-[var(--light)] text-[var(--muted)]'}`}
                      title={staff.roleName ?? undefined}
                    >
                      {staff.roleName ?? '—'}
                    </span>
                  </td>
                  <td className="px-2.5 py-3">
                    <span
                      className={`inline-block max-w-[6.5rem] truncate rounded-full px-3 py-1 align-middle text-xs font-bold ${statusBadgeClass(staff.accountStatus)}`}
                      title={accountStatusLabel(staff.accountStatus)}
                    >
                      {accountStatusLabel(staff.accountStatus)}
                    </span>
                  </td>
                  <td className="px-2.5 py-3">
                    {lastLogin ? (
                      <span className="block max-w-[10.5rem] truncate" title={`${lastLogin.date} ${lastLogin.time}`}>
                        {lastLogin.date}
                        <span className="ml-2 text-[var(--muted)]">{lastLogin.time}</span>
                      </span>
                    ) : (
                      <span className="text-[var(--muted)]">Never</span>
                    )}
                  </td>
                  {!readOnly && (
                    <td className="px-2.5 py-3 text-right">
                      <div className="flex justify-end">
                        <RowMenu staff={staff} onStatusChange={onStatusChange} disabled={own || busy} />
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
        </tbody>
      </table>
    </div>
  );
}
