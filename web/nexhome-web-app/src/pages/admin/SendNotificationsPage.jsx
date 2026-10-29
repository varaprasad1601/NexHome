import { Bell, BellRing, CheckCheck, Send, Truck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import PagedTable from '../../components/admin/payment/PagedTable';
import { MoneyTabs, MoneyTile, When } from '../../components/admin/payment/moneyParts';
import FormField from '../../components/admin/system/FormField';
import { cardClass, fieldClass, primaryButtonClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { NOTIFICATION_AUDIENCES, SUPPORT_LOOKUPS, notificationStyle, supportBadgeClass } from '../../data/supportOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';

const EMPTY_FORM = { audience: 'CUSTOMERS', mobile: '', notificationType: 'ANNOUNCEMENT', title: '', message: '' };

/**
 * The send form (NOTIFICATION_MANAGE write): to every customer, partner or staff member, or one user found by mobile
 * number (among customers and partners); a type, title and message. POST /api/notifications/send.
 */
function SendForm({ lookups, onSent }) {
  const { authRequest, can } = useAdminAuth();
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [sending, setSending] = useState(false);

  const set = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  /** The user with this mobile number among customers and partners (null if none). */
  const findUser = async (mobile) => {
    const searches = [
      can('CUSTOMER_MANAGE', 'READ') && authRequest(`/api/users/customers?size=5&search=${mobile}`),
      can('PARTNER_MANAGE', 'READ') && authRequest(`/api/users/partners?size=5&search=${mobile}`)
    ].filter(Boolean);
    const pages = await Promise.all(searches.map((search) => search.catch(() => ({ content: [] }))));
    return pages.flatMap((page) => page.content).find((user) => user.mobileNumber === mobile) ?? null;
  };

  const submit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = {};
    const mobile = form.mobile.replace(/\D/g, '');
    if (form.audience === 'USER' && !/^\d{6,15}$/.test(mobile)) found.mobile = 'Enter the mobile number (6 to 15 digits)';
    if (!form.title.trim()) found.title = 'Title is required';
    if (!form.message.trim()) found.message = 'Message is required';
    setErrors(found);
    if (Object.keys(found).length) return;

    setSending(true);
    try {
      let userId = null;
      if (form.audience === 'USER') {
        const user = await findUser(mobile);
        if (!user) {
          setErrors({ mobile: 'No customer or partner has this mobile number' });
          setSending(false);
          return;
        }
        userId = user.userId;
      }
      const result = await authRequest('/api/notifications/send', {
        method: 'POST',
        body: {
          audience: form.audience,
          userId,
          notificationType: form.notificationType,
          title: form.title.trim(),
          message: form.message.trim()
        }
      });
      setForm(EMPTY_FORM);
      onSent(result);
    } catch (err) {
      setServerError(err.message);
    } finally {
      setSending(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className={cardClass}>
      <h3 className="mb-4 text-[1.0625rem] font-black text-[var(--navy)]">Send a Notification</h3>
      {serverError && (
        <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {serverError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <FormField label="Send To" htmlFor="notify-audience" hint="Every active account holding the role, or one user.">
          <Select id="notify-audience" value={form.audience} onChange={(value) => set('audience', value)} options={NOTIFICATION_AUDIENCES} />
        </FormField>
        <FormField label="Type" htmlFor="notify-type">
          <Select
            id="notify-type"
            value={form.notificationType}
            onChange={(value) => set('notificationType', value)}
            options={(lookups.NOTIFICATION_TYPE ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))}
          />
        </FormField>
        {form.audience === 'USER' && (
          <div className="md:col-span-2">
            <FormField label="Mobile Number" htmlFor="notify-mobile" error={errors.mobile} hint="A customer's or partner's number, without +91.">
              <input
                id="notify-mobile"
                className={fieldClass(errors.mobile)}
                inputMode="numeric"
                value={form.mobile}
                onChange={(e) => set('mobile', e.target.value)}
                maxLength={15}
                placeholder="9876543210"
              />
            </FormField>
          </div>
        )}
        <div className="md:col-span-2">
          <FormField label="Title" htmlFor="notify-title" error={errors.title}>
            <input
              id="notify-title"
              className={fieldClass(errors.title)}
              value={form.title}
              onChange={(e) => set('title', e.target.value)}
              maxLength={200}
              placeholder="e.g. Diwali offer: 20% off deep cleaning"
            />
          </FormField>
        </div>
        <div className="md:col-span-2">
          <FormField label="Message" htmlFor="notify-message" error={errors.message}>
            <textarea
              id="notify-message"
              className={`${fieldClass(errors.message)} min-h-[6rem] resize-y`}
              value={form.message}
              onChange={(e) => set('message', e.target.value)}
              maxLength={2000}
            />
          </FormField>
        </div>
      </div>
      <div className="mt-4 flex justify-end">
        <button type="submit" disabled={sending} className={`${primaryButtonClass} disabled:cursor-not-allowed disabled:opacity-60`}>
          <Send size={16} />
          {sending ? 'Sending…' : 'Send Notification'}
        </button>
      </div>
    </form>
  );
}

/**
 * Marketing > Notifications (/admin/marketing/notifications, NOTIFICATION_MANAGE read): tiles (sent, unread, by
 * channel delivery), the send form with write, and tabs Sent Notifications (every notification, who got it, read or
 * not; type and read filters) and Delivery Logs (IN_APP for now: no push, SMS or email provider yet).
 */
export default function SendNotificationsPage() {
  const { authRequest, can } = useAdminAuth();
  const { values, label } = useLookupLabels(SUPPORT_LOOKUPS);
  const canWrite = can('NOTIFICATION_MANAGE', 'WRITE');
  const [tab, setTab] = useState('sent');
  const [type, setType] = useState('');
  const [read, setRead] = useState('');
  const [stats, setStats] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    document.title = 'Notifications | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/notifications/stats')
      .then((data) => !cancelled && setStats(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, reloadKey]);

  const sentQuery = new URLSearchParams();
  if (type) sentQuery.set('notificationType', type);
  if (read) sentQuery.set('unread', read === 'unread' ? 'true' : 'false');

  const sentColumns = [
    {
      header: 'Notification',
      className: 'max-w-0 w-[38%]',
      cell: (row) => {
        const style = notificationStyle(row.notificationType);
        const Icon = style.icon;
        return (
          <span className="flex items-start gap-2.5">
            <span className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full ${style.tile}`}>
              <Icon size={15} />
            </span>
            <span className="min-w-0">
              <span className="block truncate font-bold" title={row.title}>
                {row.title}
              </span>
              <span className="block truncate text-[0.8125rem] text-[var(--muted)]" title={row.message}>
                {row.message}
              </span>
            </span>
          </span>
        );
      }
    },
    { header: 'Type', cell: (row) => label('NOTIFICATION_TYPE', row.notificationType) },
    {
      header: 'Recipient',
      cell: (row) => (
        <span className="block max-w-[12rem] truncate">
          {row.userName ?? `User ${row.userId}`}
          {row.userRole && <span className="ml-1.5 text-xs text-[var(--muted)]">{row.userRole}</span>}
        </span>
      )
    },
    {
      header: 'Read',
      cell: (row) =>
        row.isRead ? (
          <span className="whitespace-nowrap rounded-full bg-[#E9F6EF] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--green-2)]">Read</span>
        ) : (
          <span className="whitespace-nowrap rounded-full bg-[#FFF7DE] px-2.5 py-1 text-[0.75rem] font-bold text-[#8A6508]">Unread</span>
        )
    },
    { header: 'Sent', cell: (row) => <When value={row.creationDate} /> }
  ];
  const deliveryColumns = [
    {
      header: 'Notification',
      className: 'max-w-0 w-[30%]',
      cell: (row) =>
        row.notificationTitle ? (
          <span className="block truncate font-bold" title={row.notificationTitle}>
            {row.notificationTitle}
          </span>
        ) : (
          <span className="text-[var(--muted)]">Deleted by the user</span>
        )
    },
    { header: 'Recipient', cell: (row) => row.userName ?? `User ${row.userId}` },
    { header: 'Channel', cell: (row) => label('NOTIFICATION_CHANNEL', row.channel) },
    {
      header: 'Status',
      cell: (row) => (
        <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${supportBadgeClass('DELIVERY_STATUS', row.deliveryStatus)}`}>
          {label('DELIVERY_STATUS', row.deliveryStatus)}
        </span>
      )
    },
    { header: 'Sent', cell: (row) => <When value={row.sentAt} /> },
    { header: 'Delivered', cell: (row) => <When value={row.deliveredAt} /> }
  ];

  const delivered = stats?.deliveriesByStatus?.DELIVERED ?? 0;
  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Marketing' }, { label: 'Notifications' }]}
        title="Notifications"
        description="Send announcements to customers, partners or staff, and see every notification the platform sent."
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        <MoneyTile icon={Bell} tone="bg-[#EDF6FD] text-[var(--blue)]" label="Notifications Sent" value={stats && formatNumber(stats.total)} />
        <MoneyTile icon={BellRing} tone="bg-[#FFF7DE] text-[#B7860B]" label="Unread" value={stats && formatNumber(stats.unread)} />
        <MoneyTile
          icon={CheckCheck}
          tone="bg-[#E9F6EF] text-[var(--green)]"
          label="Read"
          value={stats && formatNumber(stats.total - stats.unread)}
        />
        <MoneyTile
          icon={Truck}
          tone="bg-[#E8EEF6] text-[var(--navy)]"
          label="Delivered"
          value={stats && formatNumber(delivered)}
          note="In app; push, SMS and email to come"
        />
      </div>

      {canWrite && (
        <div className="mt-5">
          <SendForm
            lookups={values}
            onSent={(result) => {
              setToast({
                message: `Notification sent to ${formatNumber(result.recipients)} user${result.recipients === 1 ? '' : 's'}.`,
                tone: result.recipients ? 'success' : 'error',
                id: Date.now()
              });
              setReloadKey((key) => key + 1);
            }}
          />
        </div>
      )}

      <section className={`mt-5 ${cardClass}`}>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <MoneyTabs
            tabs={[
              { key: 'sent', label: 'Sent Notifications' },
              { key: 'deliveries', label: 'Delivery Logs' }
            ]}
            active={tab}
            onChange={setTab}
          />
          {tab === 'sent' && (
            <div className="mb-4 flex gap-3">
              <div className="w-48">
                <Select
                  ariaLabel="Notification type"
                  value={type}
                  onChange={setType}
                  options={[
                    { value: '', label: 'All Types' },
                    ...(values.NOTIFICATION_TYPE ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
                  ]}
                />
              </div>
              <div className="w-40">
                <Select
                  ariaLabel="Read"
                  value={read}
                  onChange={setRead}
                  options={[
                    { value: '', label: 'Read or Unread' },
                    { value: 'unread', label: 'Unread' },
                    { value: 'read', label: 'Read' }
                  ]}
                />
              </div>
            </div>
          )}
        </div>
        {tab === 'sent' ? (
          <PagedTable
            path={`/api/notifications${sentQuery.toString() ? `?${sentQuery}` : ''}`}
            columns={sentColumns}
            rowKey={(row) => row.notificationId}
            reloadKey={reloadKey}
            emptyText="No notifications sent yet."
            minWidth="60rem"
          />
        ) : (
          <PagedTable
            path="/api/notifications/delivery-logs"
            columns={deliveryColumns}
            rowKey={(row) => row.deliveryLogId}
            reloadKey={reloadKey}
            emptyText="No deliveries yet."
            minWidth="60rem"
          />
        )}
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
