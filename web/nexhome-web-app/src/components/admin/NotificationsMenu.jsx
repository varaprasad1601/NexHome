import { Bell, BellOff, CalendarCheck, Check, CreditCard, Settings, ShieldCheck, UserRoundPlus, Wrench, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { NOTIFICATION_KINDS, SAMPLE_NOTIFICATIONS } from '../../data/adminNotifications';

/** The icon tile of each kind of notification. */
const KIND_STYLES = {
  customer: { icon: UserRoundPlus, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  partner: { icon: Wrench, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  booking: { icon: CalendarCheck, tile: 'bg-[#F3ECFF] text-[#6B46C1]' },
  payment: { icon: CreditCard, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  system: { icon: ShieldCheck, tile: 'bg-[#FDECEC] text-[#C53030]' }
};

/** "Just now", "6 min ago", "2 hr ago", "Yesterday" or "3 days ago". */
function timeAgo(minutes) {
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? 'Yesterday' : `${days} days ago`;
}

/** High priority first, then newest first. */
const byPriority = (a, b) => (b.priority === 'high') - (a.priority === 'high') || a.minutesAgo - b.minutesAgo;

/**
 * One notification as a list row (sample 12): icon, title with the High tag, text, time and kind; unread rows have a
 * bold title and a blue dot on the right. Their own actions (mark read, remove) replace the dot on hover or focus.
 */
function NotificationItem({ notification, onOpen, onRead, onRemove }) {
  const style = KIND_STYLES[notification.kind] ?? KIND_STYLES.system;
  const Icon = style.icon;
  const unread = !notification.read;
  const kindLabel = NOTIFICATION_KINDS.find((kind) => kind.key === notification.kind)?.label;
  return (
    <li className="group relative">
      <button
        type="button"
        onClick={() => onOpen(notification)}
        className="flex w-full items-start gap-3 rounded-xl py-3 pl-4 pr-[4.5rem] text-left transition-colors hover:bg-[var(--light)]"
      >
        <span className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full ${style.tile}`}>
          <Icon size={18} strokeWidth={2} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-1.5">
            <span className={`text-sm ${unread ? 'font-black text-[var(--navy)]' : 'font-bold text-[var(--ink)]'}`}>
              {notification.title}
            </span>
            {notification.priority === 'high' && (
              <span className="rounded-full bg-[#FDECEC] px-2 py-px text-[0.625rem] font-black uppercase tracking-wide text-[#C53030]">
                High
              </span>
            )}
          </span>
          <span className="mt-0.5 line-clamp-2 block text-[0.8125rem] leading-snug text-[var(--muted)]">{notification.text}</span>
          <span className="mt-1.5 flex items-center gap-1.5 text-[0.6875rem] font-bold text-[#8A9AAD]">
            {timeAgo(notification.minutesAgo)}
            {kindLabel && <span aria-hidden="true">·</span>}
            {kindLabel}
          </span>
        </span>
      </button>
      {/* The unread dot sits where the actions appear on hover */}
      {unread && (
        <span
          className="pointer-events-none absolute right-5 top-[1.1rem] h-2 w-2 rounded-full bg-[var(--blue)] transition-opacity group-focus-within:opacity-0 group-hover:opacity-0"
          aria-label="Unread"
        />
      )}
      <div className="absolute right-3 top-3 flex gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
        {unread && (
          <button
            type="button"
            onClick={() => onRead(notification)}
            className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--line)] bg-white text-[var(--blue)] shadow-sm hover:bg-[#EDF6FD]"
            aria-label={`Mark "${notification.title}" as read`}
            title="Mark as read"
          >
            <Check size={14} strokeWidth={2.6} />
          </button>
        )}
        <button
          type="button"
          onClick={() => onRemove(notification)}
          className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--line)] bg-white text-[var(--muted)] shadow-sm hover:bg-[#FDECEC] hover:text-[#C53030]"
          aria-label={`Remove "${notification.title}"`}
          title="Remove"
        >
          <X size={14} strokeWidth={2.4} />
        </button>
      </div>
    </li>
  );
}

/**
 * The top bar's bell and its panel (samples 12, 4 and 11 of Admin Sreens/notification-samples): a header with Mark
 * all as read and Clear all (one line), All / Unread tabs, kind chips (Customers, Partners, Bookings, Payments,
 * System), list rows with High-priority ones first (opening one marks it read and goes to its page; each
 * can be marked read or removed on hover), and a footer with View all notifications and settings. Escape or a click
 * outside closes it. The notifications are sample data until the backend has them (data/adminNotifications).
 */
export default function NotificationsMenu() {
  const navigate = useNavigate();
  const ref = useRef(null);
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState('all');
  const [kind, setKind] = useState('all');
  const [notifications, setNotifications] = useState(SAMPLE_NOTIFICATIONS);
  const unread = notifications.filter((notification) => !notification.read).length;
  const shown = notifications
    .filter((notification) => (tab === 'unread' ? !notification.read : true) && (kind === 'all' || notification.kind === kind))
    .sort(byPriority);

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false);
    };
    const closeOnEscape = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  const markRead = (id) =>
    setNotifications((current) => current.map((item) => (id === undefined || item.id === id ? { ...item, read: true } : item)));
  const remove = (id) => setNotifications((current) => current.filter((item) => item.id !== id));
  const go = (to) => {
    setOpen(false);
    navigate(to);
  };
  const openNotification = (notification) => {
    markRead(notification.id);
    if (notification.to) go(notification.to);
    else setOpen(false);
  };

  const tabClass = (key) =>
    `relative flex items-center gap-1.5 whitespace-nowrap pb-2.5 text-[0.8125rem] font-bold transition ${
      tab === key
        ? 'text-[var(--blue)] after:absolute after:inset-x-0 after:-bottom-px after:h-[0.125rem] after:rounded-full after:bg-[var(--blue)]'
        : 'text-[var(--muted)] hover:text-[var(--ink)]'
    }`;
  const countClass = (key) =>
    `rounded-full px-1.5 text-[0.6875rem] ${tab === key ? 'bg-[#EDF6FD] text-[var(--blue)]' : 'bg-[var(--light)] text-[var(--muted)]'}`;
  const chipClass = (key) =>
    `whitespace-nowrap rounded-full border px-3 py-1 text-xs font-bold transition ${
      kind === key
        ? 'border-[var(--blue)] bg-[var(--blue)] text-white'
        : 'border-[var(--line)] bg-white text-[var(--muted)] hover:border-[#C7E3FC] hover:text-[var(--ink)]'
    }`;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        className={`relative rounded-full p-2.5 transition hover:bg-[var(--light)] ${open ? 'bg-[var(--light)] text-[var(--blue)]' : 'text-[var(--ink)]'}`}
        aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#E53E3E] px-1 text-[0.625rem] font-bold text-white ring-2 ring-white">
            {unread}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 top-[calc(100%+0.75rem)] z-40 w-[32rem] max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-[var(--line)] bg-white shadow-[0_24px_60px_rgba(14,51,89,0.18)]"
        >
          {/* Header: the title; the actions on one line */}
          <div className="flex items-center justify-between gap-4 px-5 pb-3 pt-4">
            <h2 className="mb-0 text-base font-black text-[var(--navy)]">Notifications</h2>
            {notifications.length > 0 && (
              <div className="flex flex-shrink-0 items-center gap-1 whitespace-nowrap text-xs font-bold">
                <button
                  type="button"
                  onClick={() => markRead()}
                  disabled={!unread}
                  className="rounded-md px-1.5 py-1 text-[var(--blue)] hover:bg-[#EDF6FD] disabled:cursor-not-allowed disabled:text-[#A8B6C6] disabled:hover:bg-transparent"
                >
                  Mark all as read
                </button>
                <span className="text-[#C3CEDA]" aria-hidden="true">
                  ·
                </span>
                <button
                  type="button"
                  onClick={() => setNotifications([])}
                  className="rounded-md px-1.5 py-1 text-[var(--muted)] hover:bg-[#FDECEC] hover:text-[#C53030]"
                >
                  Clear all
                </button>
              </div>
            )}
          </div>

          {/* All / Unread, then the kind chips */}
          <div className="mx-5 flex gap-5 border-b border-[var(--line)]" role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'all'} className={tabClass('all')} onClick={() => setTab('all')}>
              All <span className={countClass('all')}>{notifications.length}</span>
            </button>
            <button type="button" role="tab" aria-selected={tab === 'unread'} className={tabClass('unread')} onClick={() => setTab('unread')}>
              Unread <span className={countClass('unread')}>{unread}</span>
            </button>
          </div>
          <div className="nh-scroll-hidden flex gap-1.5 overflow-x-auto px-5 pb-2.5 pt-3" aria-label="Filter by kind">
            <button type="button" className={chipClass('all')} aria-pressed={kind === 'all'} onClick={() => setKind('all')}>
              All
            </button>
            {NOTIFICATION_KINDS.map((item) => (
              <button
                key={item.key}
                type="button"
                className={chipClass(item.key)}
                aria-pressed={kind === item.key}
                onClick={() => setKind(item.key)}
              >
                {item.label}
              </button>
            ))}
          </div>

          {shown.length === 0 ? (
            <div className="flex flex-col items-center px-6 py-10 text-center">
              <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
                <BellOff size={22} />
              </span>
              <div className="text-sm font-black text-[var(--navy)]">
                {notifications.length ? 'Nothing here' : "You're all caught up"}
              </div>
              <div className="mt-0.5 text-xs text-[var(--muted)]">
                {notifications.length ? 'No notifications match this view.' : 'New notifications will show here.'}
              </div>
            </div>
          ) : (
            <ul className="m-0 max-h-[24rem] list-none space-y-0.5 overflow-y-auto px-2 pb-2 pt-2">
              {shown.map((notification) => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  onOpen={openNotification}
                  onRead={(item) => markRead(item.id)}
                  onRemove={(item) => remove(item.id)}
                />
              ))}
            </ul>
          )}

          {/* Footer: the full list (to come) and notification settings */}
          <div className="flex items-center justify-between border-t border-[var(--line)] px-5 py-2.5">
            <button
              type="button"
              onClick={() => go('/admin/notifications')}
              className="rounded-md py-1 text-xs font-bold text-[var(--blue)] hover:text-[var(--navy)]"
            >
              View all notifications →
            </button>
            <button
              type="button"
              onClick={() => go('/admin/settings')}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--line)] text-[var(--muted)] hover:bg-[#EDF6FD] hover:text-[var(--blue)]"
              aria-label="Notification settings"
              title="Notification settings"
            >
              <Settings size={15} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
