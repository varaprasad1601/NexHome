import { CalendarDays, LifeBuoy, Lock, Paperclip, Phone, Send, UserRound } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import Toggle from '../../components/admin/common/Toggle';
import { BookingLink, When } from '../../components/admin/payment/moneyParts';
import { cardClass, fieldClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { SUPPORT_LOOKUPS } from '../../data/supportOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { SupportBadge } from './SupportTicketsPage';

/** A label over its value, as the ticket's side panel lists them. */
function Field({ label, children }) {
  return (
    <div>
      <div className="mb-1 text-[0.75rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">{label}</div>
      <div className="text-sm text-[var(--ink)]">{children}</div>
    </div>
  );
}

/** One message: the requester's on the left, staff replies on the right, internal notes amber with a lock. */
function Message({ message }) {
  const right = message.fromStaff;
  const tone = message.isInternalNote
    ? 'border-[#F3D98B] bg-[#FFF9E6]'
    : right
      ? 'border-[#C7E3FC] bg-[#EDF6FD]'
      : 'border-[var(--line)] bg-white';
  return (
    <li className={`flex ${right ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[85%] rounded-2xl border px-4 py-3 ${tone}`}>
        <div className="mb-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs">
          <span className="font-black text-[var(--navy)]">{message.senderName ?? 'Unknown'}</span>
          {message.senderRole && <span className="text-[var(--muted)]">{message.senderRole}</span>}
          {message.isInternalNote && (
            <span className="inline-flex items-center gap-1 rounded-full bg-[#FFF0C2] px-2 py-px font-bold text-[#8A6508]">
              <Lock size={10} />
              Internal note
            </span>
          )}
          <span className="text-[#8A9AAD]">
            <When value={message.creationDate} />
          </span>
        </div>
        <p className="mb-0 whitespace-pre-wrap text-sm text-[var(--ink)]">{message.message}</p>
      </div>
    </li>
  );
}

/**
 * A support ticket (/admin/support/:ticketId, SUPPORT_MANAGE read; GET /api/support/tickets/{id}): the request, the
 * conversation with internal notes, and a side panel with the ticket's status, priority, category and assignee
 * (SUPPORT_MANAGE write changes them with PUT /api/support/tickets/update; "Assign to me"), the requester, the booking
 * and attachments. With write, a reply box (POST /api/support/tickets/messages/add) with an Internal note switch: the
 * requester never sees notes. Replying moves an open ticket to In Progress; a closed ticket takes no replies.
 */
export default function SupportTicketPage() {
  const { ticketId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { authRequest, can, session } = useAdminAuth();
  const { values, label } = useLookupLabels(SUPPORT_LOOKUPS);
  const canWrite = can('SUPPORT_MANAGE', 'WRITE');
  const [state, setState] = useState({ detail: null, error: '' });
  const [reloadKey, setReloadKey] = useState(0);
  const [assignees, setAssignees] = useState([]);
  const [reply, setReply] = useState('');
  const [internal, setInternal] = useState(false);
  const [sending, setSending] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(() => (location.state?.toast ? { ...location.state.toast, id: 'opened' } : null));
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = (message, tone = 'success') => setToast({ message, tone, id: Date.now() });

  useEffect(() => {
    if (location.state?.toast) navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate]);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/support/tickets/${ticketId}`)
      .then((detail) => !cancelled && setState({ detail, error: '' }))
      .catch((err) => !cancelled && setState({ detail: null, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, ticketId, reloadKey]);

  useEffect(() => {
    if (!canWrite) return undefined;
    let cancelled = false;
    authRequest('/api/support/assignees')
      .then((list) => !cancelled && setAssignees(list))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, canWrite]);

  const { detail, error } = state;
  const ticket = detail?.ticket;

  useEffect(() => {
    document.title = `${ticket?.publicId ?? 'Support Ticket'} | NexHome Admin`;
  }, [ticket]);

  const crumbs = [
    { label: 'Support & Feedback' },
    { label: 'Support Tickets', to: '/admin/support' },
    { label: ticket?.publicId ?? '' }
  ];

  if (error || !ticket) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} title="" />
        {error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {error}
          </div>
        ) : (
          <div className="h-48 animate-pulse rounded-2xl bg-white" />
        )}
      </div>
    );
  }

  const closed = ticket.status === 'CLOSED';
  const update = async (changes, message) => {
    setSaving(true);
    try {
      await authRequest('/api/support/tickets/update', { method: 'PUT', body: { supportTicketId: ticket.supportTicketId, ...changes } });
      showToast(message);
      setReloadKey((key) => key + 1);
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setSaving(false);
    }
  };
  const send = async () => {
    if (!reply.trim()) return;
    setSending(true);
    try {
      await authRequest('/api/support/tickets/messages/add', {
        method: 'POST',
        body: { supportTicketId: ticket.supportTicketId, message: reply.trim(), isInternalNote: internal }
      });
      setReply('');
      showToast(internal ? 'Internal note added.' : 'Reply sent to the requester.');
      setReloadKey((key) => key + 1);
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setSending(false);
    }
  };
  const options = (type) => (values[type] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }));
  const myId = session?.user?.userId;
  const canTakeIt = canWrite && myId && ticket.assignedToUserId !== myId && assignees.some((person) => person.userId === myId);

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} title={ticket.subject} description={`Support ticket ${ticket.publicId}`} />

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-5">
          {/* The request */}
          <section className={cardClass}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
                <LifeBuoy size={18} />
              </span>
              <span className="rounded-md bg-[#E8EEF6] px-2 py-1 font-mono text-[0.75rem] font-bold text-[var(--navy)]">{ticket.publicId}</span>
              <SupportBadge type="SUPPORT_TICKET_STATUS" code={ticket.status} label={label} />
              <SupportBadge type="SUPPORT_PRIORITY" code={ticket.priority} label={label} />
              <span className="rounded-full bg-[var(--light)] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--muted)]">
                {label('SUPPORT_CATEGORY', ticket.category)}
              </span>
            </div>
            <p className="mb-0 mt-4 whitespace-pre-wrap text-sm leading-relaxed text-[var(--ink)]">{ticket.description}</p>
            <div className="mt-4 flex flex-wrap gap-x-6 gap-y-1 border-t border-[var(--line)] pt-3 text-xs text-[var(--muted)]">
              <span>
                Opened <When value={ticket.creationDate} />
                {ticket.createdBy !== ticket.requesterUserId && ticket.createdByName && ` by ${ticket.createdByName}`}
              </span>
              {ticket.resolvedAt && (
                <span>
                  Resolved <When value={ticket.resolvedAt} />
                </span>
              )}
              {ticket.closedAt && (
                <span>
                  Closed <When value={ticket.closedAt} />
                </span>
              )}
            </div>
          </section>

          {/* The conversation */}
          <section className={cardClass}>
            <h3 className="mb-4 text-[1.0625rem] font-black text-[var(--navy)]">Conversation</h3>
            {detail.messages.length === 0 ? (
              <p className="mb-0 rounded-xl bg-[var(--light)] px-4 py-5 text-center text-sm text-[var(--muted)]">
                No replies yet.
              </p>
            ) : (
              <ul className="m-0 list-none space-y-3 p-0">
                {detail.messages.map((message) => (
                  <Message key={message.messageId} message={message} />
                ))}
              </ul>
            )}

            {canWrite && (
              <div className="mt-5 border-t border-[var(--line)] pt-4">
                {closed ? (
                  <p className="mb-0 text-sm text-[var(--muted)]">This ticket is closed. Reopen it to reply.</p>
                ) : (
                  <>
                    <label htmlFor="ticket-reply" className="sr-only">
                      {internal ? 'Internal note' : 'Reply'}
                    </label>
                    <textarea
                      id="ticket-reply"
                      className={`${fieldClass()} min-h-[6rem] resize-y ${internal ? 'border-[#F3D98B] bg-[#FFFDF5]' : ''}`}
                      value={reply}
                      onChange={(e) => setReply(e.target.value)}
                      maxLength={5000}
                      placeholder={internal ? 'A note for staff only; the requester will not see it.' : 'Write a reply to the requester...'}
                    />
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <span className="flex items-center gap-3 text-sm">
                        <Toggle checked={internal} onChange={setInternal} label="Internal note" />
                        <span className="font-bold text-[var(--ink)]">Internal note</span>
                      </span>
                      <button
                        type="button"
                        onClick={send}
                        disabled={sending || !reply.trim()}
                        className="flex items-center gap-2 rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {internal ? <Lock size={15} /> : <Send size={15} />}
                        {sending ? 'Sending…' : internal ? 'Add Note' : 'Send Reply'}
                      </button>
                    </div>
                  </>
                )}
              </div>
            )}
          </section>
        </div>

        {/* Side panel */}
        <div className="space-y-5">
          <section className={`space-y-4 ${cardClass}`}>
            <h3 className="mb-0 text-[1.0625rem] font-black text-[var(--navy)]">Ticket</h3>
            {canWrite ? (
              <>
                <Field label="Status">
                  <Select
                    ariaLabel="Status"
                    value={ticket.status}
                    onChange={(value) => update({ status: value }, `Ticket is now ${label('SUPPORT_TICKET_STATUS', value)}.`)}
                    options={options('SUPPORT_TICKET_STATUS').filter((option) => !closed || ['CLOSED', 'IN_PROGRESS'].includes(option.value))}
                  />
                </Field>
                <Field label="Priority">
                  <Select
                    ariaLabel="Priority"
                    value={ticket.priority}
                    onChange={(value) => update({ priority: value }, `Priority set to ${label('SUPPORT_PRIORITY', value)}.`)}
                    options={options('SUPPORT_PRIORITY')}
                  />
                </Field>
                <Field label="Category">
                  <Select
                    ariaLabel="Category"
                    value={ticket.category}
                    onChange={(value) => update({ category: value }, `Category set to ${label('SUPPORT_CATEGORY', value)}.`)}
                    options={options('SUPPORT_CATEGORY')}
                  />
                </Field>
                <Field label="Assigned To">
                  <Select
                    ariaLabel="Assigned to"
                    value={ticket.assignedToUserId ? String(ticket.assignedToUserId) : ''}
                    onChange={(value) =>
                      value
                        ? update({ assignedToUserId: Number(value) }, 'Ticket assigned.')
                        : update({ unassign: true }, 'Assignee taken off.')
                    }
                    options={[
                      { value: '', label: 'Unassigned' },
                      ...assignees.map((person) => ({
                        value: String(person.userId),
                        label: `${person.name ?? 'Unnamed'}${person.role ? ` · ${person.role}` : ''}`
                      }))
                    ]}
                  />
                  {canTakeIt && (
                    <button
                      type="button"
                      disabled={saving}
                      onClick={() => update({ assignedToUserId: myId }, 'Ticket assigned to you.')}
                      className="mt-2 text-xs font-bold text-[var(--blue)] hover:text-[var(--navy)]"
                    >
                      Assign to me
                    </button>
                  )}
                </Field>
              </>
            ) : (
              <>
                <Field label="Status">
                  <SupportBadge type="SUPPORT_TICKET_STATUS" code={ticket.status} label={label} />
                </Field>
                <Field label="Priority">
                  <SupportBadge type="SUPPORT_PRIORITY" code={ticket.priority} label={label} />
                </Field>
                <Field label="Category">{label('SUPPORT_CATEGORY', ticket.category)}</Field>
                <Field label="Assigned To">{ticket.assignedToName ?? 'Unassigned'}</Field>
              </>
            )}
          </section>

          <section className={`space-y-4 ${cardClass}`}>
            <h3 className="mb-0 text-[1.0625rem] font-black text-[var(--navy)]">Requester</h3>
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2">
                <UserRound size={15} className="text-[var(--blue)]" />
                <span className="font-bold text-[var(--ink)]">{ticket.requesterName ?? 'Name not added'}</span>
                {ticket.requesterRole && <span className="text-xs text-[var(--muted)]">{ticket.requesterRole}</span>}
              </div>
              <div className="flex items-center gap-2 text-[var(--muted)]">
                <Phone size={15} className="text-[var(--blue)]" />
                {ticket.requesterMobile}
              </div>
              <div className="flex items-center gap-2 text-[var(--muted)]">
                <CalendarDays size={15} className="text-[var(--blue)]" />
                Booking: <BookingLink id={ticket.bookingId} publicId={ticket.bookingPublicId} />
              </div>
              {ticket.paymentReference && <div className="text-xs text-[var(--muted)]">Payment reference {ticket.paymentReference}</div>}
            </div>
          </section>

          <section className={cardClass}>
            <h3 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">Attachments</h3>
            {detail.attachments.length === 0 ? (
              <p className="mb-0 text-sm text-[var(--muted)]">No files attached.</p>
            ) : (
              <ul className="m-0 list-none space-y-2 p-0">
                {detail.attachments.map((attachment) => (
                  <li key={attachment.attachmentId} className="flex items-start gap-2 text-sm">
                    <Paperclip size={14} className="mt-0.5 flex-shrink-0 text-[var(--muted)]" />
                    <span className="min-w-0">
                      <span className="block truncate font-bold text-[var(--ink)]" title={attachment.storageKey}>
                        {attachment.storageKey.split('/').pop()}
                      </span>
                      <span className="text-xs text-[var(--muted)]">{attachment.fileType}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
