import { Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';

import Modal from '../common/Modal';
import Select from '../common/Select';
import FormField from '../system/FormField';
import { fieldClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import useDebounced from '../../../hooks/useDebounced';

/** "Ravi Kumar - +91 98765 43210", or the mobile number while the profile has no name. */
const personLabel = (person) => {
  const name = [person.firstName, person.lastName].filter(Boolean).join(' ');
  const mobile = `${person.countryCode} ${person.mobileNumber}`;
  return name ? `${name} - ${mobile}` : mobile;
};

/**
 * New Ticket (SUPPORT_MANAGE write): opens a support ticket on a customer's or partner's behalf (POST
 * /api/support/tickets/create). The requester is found by name, mobile number or id among customers and partners;
 * an optional booking is given by its id (NHBOOK...), found with the bookings search. Category and priority come from
 * the lookups; `lookups` is useLookupLabels' values.
 */
export default function CreateTicketModal({ lookups, onClose, onCreated }) {
  const { authRequest, can } = useAdminAuth();
  const [query, setQuery] = useState('');
  const search = useDebounced(query.trim());
  const [matches, setMatches] = useState([]);
  const [requester, setRequester] = useState(null);
  const [form, setForm] = useState({ bookingPublicId: '', category: '', priority: 'MEDIUM', subject: '', description: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);

  // Customers and partners matching the search (each list only for those who may read it)
  useEffect(() => {
    if (search.length < 2 || requester) return undefined;
    let cancelled = false;
    const lists = [
      can('CUSTOMER_MANAGE', 'READ') &&
        authRequest(`/api/users/customers?size=5&search=${encodeURIComponent(search)}`).then((page) =>
          page.content.map((person) => ({ ...person, kind: 'Customer' }))
        ),
      can('PARTNER_MANAGE', 'READ') &&
        authRequest(`/api/users/partners?size=5&search=${encodeURIComponent(search)}`).then((page) =>
          page.content.map((person) => ({ ...person, kind: 'Partner' }))
        )
    ].filter(Boolean);
    Promise.all(lists.map((list) => list.catch(() => [])))
      .then((results) => !cancelled && setMatches(results.flat()))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, can, search, requester]);

  const set = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = {};
    if (!requester) found.requester = 'Choose the customer or partner the ticket is for';
    if (!form.category) found.category = 'Category is required';
    if (!form.subject.trim()) found.subject = 'Subject is required';
    if (!form.description.trim()) found.description = 'Description is required';
    setErrors(found);
    if (Object.keys(found).length) return;

    setSaving(true);
    try {
      let bookingId = null;
      const bookingPublicId = form.bookingPublicId.trim().toUpperCase();
      if (bookingPublicId) {
        const page = await authRequest(`/api/bookings?size=5&search=${encodeURIComponent(bookingPublicId)}`);
        bookingId = page.content.find((booking) => booking.publicId === bookingPublicId)?.bookingId ?? null;
        if (!bookingId) {
          setErrors({ bookingPublicId: 'No booking has this id' });
          setSaving(false);
          return;
        }
      }
      const ticket = await authRequest('/api/support/tickets/create', {
        method: 'POST',
        body: {
          requesterId: requester.userId,
          bookingId,
          category: form.category,
          priority: form.priority,
          subject: form.subject.trim(),
          description: form.description.trim()
        }
      });
      onCreated(ticket);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  const options = (type) => (lookups[type] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }));

  return (
    <Modal title="New Support Ticket" onClose={onClose} width="max-w-[42rem]">
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <FormField
              label="Customer or Partner"
              htmlFor="ticket-requester"
              error={errors.requester}
              hint="The person the ticket is for: they see it and its replies (not internal notes)."
            >
              {requester ? (
                <div className="flex items-center justify-between gap-3 rounded-xl border border-[var(--line)] bg-[var(--light)] px-3.5 py-2.5 text-sm">
                  <span>
                    <span className="font-bold text-[var(--ink)]">{personLabel(requester)}</span>
                    <span className="ml-2 text-[var(--muted)]">
                      {requester.kind} · {requester.publicId ?? 'no profile id'}
                    </span>
                  </span>
                  <button
                    type="button"
                    onClick={() => {
                      setRequester(null);
                      setQuery('');
                    }}
                    className="rounded-md p-1 text-[var(--muted)] hover:bg-white hover:text-[var(--ink)]"
                    aria-label="Choose someone else"
                  >
                    <X size={15} />
                  </button>
                </div>
              ) : (
                <>
                  <div className="relative">
                    <Search size={15} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
                    <input
                      id="ticket-requester"
                      className={`${fieldClass(errors.requester)} pl-9`}
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      placeholder="Search by name, mobile number or id..."
                      autoComplete="off"
                      autoFocus
                    />
                  </div>
                  {search.length >= 2 && (
                    <ul className="m-0 mt-1.5 max-h-48 list-none overflow-y-auto rounded-xl border border-[var(--line)] p-1">
                      {matches.length === 0 ? (
                        <li className="px-3 py-2 text-sm text-[var(--muted)]">No customers or partners match.</li>
                      ) : (
                        matches.map((person) => (
                          <li key={`${person.kind}-${person.userId}`}>
                            <button
                              type="button"
                              onClick={() => {
                                setRequester(person);
                                setErrors((current) => ({ ...current, requester: undefined }));
                              }}
                              className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-[var(--light)]"
                            >
                              <span className="font-bold text-[var(--ink)]">{personLabel(person)}</span>
                              <span className="text-xs font-bold text-[var(--muted)]">{person.kind}</span>
                            </button>
                          </li>
                        ))
                      )}
                    </ul>
                  )}
                </>
              )}
            </FormField>
          </div>
          <FormField label="Category" htmlFor="ticket-category" error={errors.category}>
            <Select
              id="ticket-category"
              value={form.category}
              onChange={(value) => set('category', value)}
              placeholder="Select a category"
              error={Boolean(errors.category)}
              options={options('SUPPORT_CATEGORY')}
            />
          </FormField>
          <FormField label="Priority" htmlFor="ticket-priority">
            <Select id="ticket-priority" value={form.priority} onChange={(value) => set('priority', value)} options={options('SUPPORT_PRIORITY')} />
          </FormField>
          <div className="sm:col-span-2">
            <FormField
              label="Booking ID"
              htmlFor="ticket-booking"
              required={false}
              error={errors.bookingPublicId}
              hint="The booking the ticket is about, e.g. NHBOOK0031."
            >
              <input
                id="ticket-booking"
                className={`${fieldClass(errors.bookingPublicId)} font-mono uppercase`}
                value={form.bookingPublicId}
                onChange={(e) => set('bookingPublicId', e.target.value)}
                maxLength={24}
                placeholder="NHBOOK0000"
              />
            </FormField>
          </div>
          <div className="sm:col-span-2">
            <FormField label="Subject" htmlFor="ticket-subject" error={errors.subject}>
              <input
                id="ticket-subject"
                className={fieldClass(errors.subject)}
                value={form.subject}
                onChange={(e) => set('subject', e.target.value)}
                maxLength={200}
                placeholder="e.g. Partner did not turn up"
              />
            </FormField>
          </div>
          <div className="sm:col-span-2">
            <FormField label="Description" htmlFor="ticket-description" error={errors.description}>
              <textarea
                id="ticket-description"
                className={`${fieldClass(errors.description)} min-h-[7rem] resize-y`}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                maxLength={5000}
              />
            </FormField>
          </div>
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            className="rounded-xl border border-[var(--line)] bg-white px-6 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving}
            className="rounded-xl bg-[var(--blue)] px-6 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {saving ? 'Creating…' : 'Create Ticket'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
