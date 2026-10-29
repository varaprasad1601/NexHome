import { Bell, BellOff, Check, Settings, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';
import { notificationLink, notificationStyle, timeAgo } from '../../data/supportOptions';
import useLookupLabels from '../../hooks/useLookupLabels';

const TYPE_LOOKUP = ['NOTIFICATION_TYPE'];
/** The panel lists this many of the newest; the rest are on the notifications page. */
const PANEL_SIZE = 30;
/** How often the unread count is refreshed while the console is open. */
const REFRESH_MS = 60000;

/**
 * One notification as a list row (sample 12): icon, title, message, time and type; unread rows have a bold title and
 * a blue dot on the right. Their own actions (mark read, remove) replace the dot on hover or focus.
 */
export function NotificationItem({ notification, typeLabel, onOpen, onRead, onRemove }) {
  const style = notificationStyle(notification.notificationType);
  const Icon = style.icon;
  const unread = !notification.isRead;
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
          <span className={`block text-sm ${unread ? 'font-black text-[var(--navy)]' : 'font-bold text-[var(--ink)]'}`}>
            {notification.title}
          </span>
          <span className="mt-0.5 line-clamp-2 block text-[0.8125rem] leading-snug text-[var(--muted)]">
            {notification.message}
          </span>
          <span className="mt-1.5 flex items-center gap-1.5 text-[0.6875rem] font-bold text-[#8A9AAD]">
            {timeAgo(notification.creationDate)}
            {typeLabel && <span aria-hidden="true">·</span>}
            {typeLabel}
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
 * The top bar's bell and its panel (samples 12, 4 and 11 of Admin Sreens/notification-samples), with the signed-in
 * staff member's own notifications (GET /api/notifications/me and /me/summary, refreshed every minute and on open): a
 * header with Mark all as read and Clear all, All / Unread tabs, type chips (only the types present), list rows
 * (opening one marks it read and goes to its page; each can be marked read or removed on hover), and a footer with
 * View all notifications and settings. Escape or a click outside closes it.
 */
export default function NotificationsMenu() {
  const { authRequest } = useAdminAuth();
  const { label } = useLookupLabels(TYPE_LOOKUP);
  const navigate = useNavigate();
  const ref = useRef(null);
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState('all');
  const [type, setType] = useState('all');
  const [summary, setSummary] = useState({ total: 0, unread: 0 });
  const [notifications, setNotifications] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const loadSummary = useCallback(
    () =>
      authRequest('/api/notifications/me/summary')
        .then(setSummary)
        .catch(() => {}),
    [authRequest]
  );
  const loadList = useCallback(
    () =>
      authRequest(`/api/notifications/me?size=${PANEL_SIZE}`)
        .then((page) => {
          setNotifications(page.content);
          setLoaded(true);
          setNow(Date.now());
        })
        .catch(() => setLoaded(true)),
    [authRequest]
  );

  // The badge stays current while the console is open
  useEffect(() => {
    loadSummary();
    const timer = setInterval(loadSummary, REFRESH_MS);
    return () => clearInterval(timer);
  }, [loadSummary]);

  useEffect(() => {
    if (!open) return undefined;
    loadList();
    loadSummary();
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
  }, [open, loadList, loadSummary]);

  const unread = summary.unread;
  const types = [...new Set(notifications.map((notification) => notification.notificationType))];
  const shown = notifications.filter(
    (notification) => (tab === 'unread' ? !notification.isRead : true) && (type === 'all' || notification.notificationType === type)
  );

  /** Changes on screen at once, then on the server; a failure reloads what the server has. */
  const markRead = (notification) => {
    setNotifications((current) => current.map((item) => (item.notificationId === notification.notificationId ? { ...item, isRead: true } : item)));
    setSummary((current) => ({ ...current, unread: Math.max(0, current.unread - (notification.isRead ? 0 : 1)) }));
    authRequest('/api/notifications/me/read', { method: 'PUT', body: { notificationIds: [notification.notificationId] } }).catch(
      () => loadSummary()
    );
  };
  const markAllRead = () => {
    setNotifications((current) => current.map((item) => ({ ...item, isRead: true })));
    setSummary((current) => ({ ...current, unread: 0 }));
    authRequest('/api/notifications/me/read-all', { method: 'PUT' }).catch(() => loadSummary());
  };
  const remove = (notification) => {
    setNotifications((current) => current.filter((item) => item.notificationId !== notification.notificationId));
    setSummary((current) => ({
      total: Math.max(0, current.total - 1),
      unread: Math.max(0, current.unread - (notification.isRead ? 0 : 1))
    }));
    authRequest(`/api/notifications/me/delete/${notification.notificationId}`, { method: 'DELETE' }).catch(() => {
      loadList();
      loadSummary();
    });
  };
  const clearAll = () => {
    setNotifications([]);
    setSummary({ total: 0, unread: 0 });
    authRequest('/api/notifications/me/clear', { method: 'DELETE' }).catch(() => {
      loadList();
      loadSummary();
    });
  };
  const go = (to) => {
    setOpen(false);
    navigate(to);
  };
  const openNotification = (notification) => {
    if (!notification.isRead) markRead(notification);
    const to = notificationLink(notification);
    if (to) go(to);
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
      type === key
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
            {unread > 99 ? '99+' : unread}
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
            {summary.total > 0 && (
              <div className="flex flex-shrink-0 items-center gap-1 whitespace-nowrap text-xs font-bold">
                <button
                  type="button"
                  onClick={markAllRead}
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
                  onClick={clearAll}
                  className="rounded-md px-1.5 py-1 text-[var(--muted)] hover:bg-[#FDECEC] hover:text-[#C53030]"
                >
                  Clear all
                </button>
              </div>
            )}
          </div>

          {/* All / Unread, then the type chips */}
          <div className="mx-5 flex gap-5 border-b border-[var(--line)]" role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'all'} className={tabClass('all')} onClick={() => setTab('all')}>
              All <span className={countClass('all')}>{summary.total}</span>
            </button>
            <button type="button" role="tab" aria-selected={tab === 'unread'} className={tabClass('unread')} onClick={() => setTab('unread')}>
              Unread <span className={countClass('unread')}>{unread}</span>
            </button>
          </div>
          {types.length > 1 && (
            <div className="nh-scroll-hidden flex gap-1.5 overflow-x-auto px-5 pb-2.5 pt-3" aria-label="Filter by type">
              <button type="button" className={chipClass('all')} aria-pressed={type === 'all'} onClick={() => setType('all')}>
                All
              </button>
              {types.map((code) => (
                <button key={code} type="button" className={chipClass(code)} aria-pressed={type === code} onClick={() => setType(code)}>
                  {label('NOTIFICATION_TYPE', code)}
                </button>
              ))}
            </div>
          )}

          {!loaded ? (
            <div className="space-y-2 px-5 py-4">
              {Array.from({ length: 3 }, (_, i) => (
                <div key={i} className="h-14 animate-pulse rounded-xl bg-[var(--light)]" />
              ))}
            </div>
          ) : shown.length === 0 ? (
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
            <ul className="m-0 max-h-[24rem] list-none space-y-0.5 overflow-y-auto px-2 pb-2 pt-2" key={now}>
              {shown.map((notification) => (
                <NotificationItem
                  key={notification.notificationId}
                  notification={notification}
                  typeLabel={label('NOTIFICATION_TYPE', notification.notificationType)}
                  onOpen={openNotification}
                  onRead={markRead}
                  onRemove={remove}
                />
              ))}
            </ul>
          )}

          {/* Footer: the full list and notification settings */}
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
