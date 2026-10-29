import { BellOff, CheckCheck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import { NotificationItem } from '../../components/admin/NotificationsMenu';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import { MoneyTabs } from '../../components/admin/payment/moneyParts';
import { cardClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { notificationLink } from '../../data/supportOptions';
import useLookupLabels from '../../hooks/useLookupLabels';

const TYPE_LOOKUP = ['NOTIFICATION_TYPE'];
const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/**
 * View all notifications (/admin/notifications, from the bell): the signed-in staff member's own notifications,
 * paged, All / Unread, a type filter, Mark all as read; opening one marks it read and goes to its page; each can be
 * marked read or removed. GET /api/notifications/me.
 */
export default function NotificationsPage() {
  const { authRequest } = useAdminAuth();
  const { values, label } = useLookupLabels(TYPE_LOOKUP);
  const navigate = useNavigate();
  const [tab, setTab] = useState('all');
  const [type, setType] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const reload = () => setReloadKey((key) => key + 1);

  useEffect(() => {
    document.title = 'Notifications | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (tab === 'unread') params.set('unread', 'true');
    if (type) params.set('notificationType', type);
    authRequest(`/api/notifications/me?${params}`)
      .then((data) => !cancelled && setList({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, tab, type, page, reloadKey]);

  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
  };

  const act = async (request, message) => {
    try {
      await request();
      if (message) setToast({ message, tone: 'success', id: Date.now() });
      reload();
    } catch (err) {
      setToast({ message: err.message, tone: 'error', id: Date.now() });
    }
  };
  const markRead = (notification) =>
    act(() =>
      authRequest('/api/notifications/me/read', { method: 'PUT', body: { notificationIds: [notification.notificationId] } })
    );
  const remove = (notification) =>
    act(() => authRequest(`/api/notifications/me/delete/${notification.notificationId}`, { method: 'DELETE' }), 'Notification removed.');
  const markAllRead = () => act(() => authRequest('/api/notifications/me/read-all', { method: 'PUT' }), 'All notifications marked as read.');
  const open = async (notification) => {
    if (!notification.isRead) await markRead(notification);
    const to = notificationLink(notification);
    if (to) navigate(to);
  };

  const rows = list.data.content;
  return (
    <div className="mx-auto max-w-[60rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Notifications' }]}
        title="Notifications"
        description="What happened on bookings, payments, support tickets and partners that concerns you."
        actions={
          <button
            type="button"
            onClick={markAllRead}
            className="flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
          >
            <CheckCheck size={16} />
            Mark all as read
          </button>
        }
      />
      <section className={cardClass}>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <MoneyTabs
            tabs={[
              { key: 'all', label: 'All' },
              { key: 'unread', label: 'Unread' }
            ]}
            active={tab}
            onChange={changeFilter(setTab)}
          />
          <div className="mb-4 w-52">
            <Select
              ariaLabel="Notification type"
              value={type}
              onChange={changeFilter(setType)}
              options={[
                { value: '', label: 'All Types' },
                ...(values.NOTIFICATION_TYPE ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
              ]}
            />
          </div>
        </div>
        {list.error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {list.error}
          </div>
        ) : list.loading ? (
          <div className="space-y-2">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-[var(--light)]" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center py-14 text-center">
            <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
              <BellOff size={22} />
            </span>
            <div className="text-sm font-black text-[var(--navy)]">
              {tab === 'unread' ? "You're all caught up" : 'No notifications yet'}
            </div>
          </div>
        ) : (
          <ul className="m-0 list-none divide-y divide-[var(--line)] p-0">
            {rows.map((notification) => (
              <NotificationItem
                key={notification.notificationId}
                notification={notification}
                typeLabel={label('NOTIFICATION_TYPE', notification.notificationType)}
                onOpen={open}
                onRead={markRead}
                onRemove={remove}
              />
            ))}
          </ul>
        )}
        {!list.loading && !list.error && <Pagination pageData={list.data} noun="notifications" onPageChange={setPage} />}
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
