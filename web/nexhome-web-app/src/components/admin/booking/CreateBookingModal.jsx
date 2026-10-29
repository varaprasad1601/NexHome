import { Search, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import DatePicker from '../common/DatePicker';
import FormModal from '../common/FormModal';
import Modal from '../common/Modal';
import Select from '../common/Select';
import { primaryButtonClass, searchInputClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import { addressBody, addressFields, newAddressValues } from '../../../data/addressForm';
import { BOOKING_LOOKUPS, formatAmount } from '../../../data/bookingOptions';
import useDebounced from '../../../hooks/useDebounced';
import useLookupLabels from '../../../hooks/useLookupLabels';
import { TIME_OPTIONS, toInstant, todayInIndia } from '../../../utils/schedule';

const labelClass = 'mb-1.5 block text-sm font-bold text-[var(--ink)]';
const ADDRESS_LOOKUPS = ['ADDRESS_TYPE'];

/**
 * Create Booking (BOOKING_MANAGE write): a staff booking for a customer, e.g. by phone. Pick the customer (search),
 * one of their addresses, the service and its option, instant or a date and time, and cash or online, then
 * POST /api/bookings/create (priced on the server). `customer` ({ customerProfileId, userId, name }) fixes the
 * customer when opened from their profile. `onCreated(booking)` gets the new booking.
 */
export default function CreateBookingModal({ customer: fixedCustomer = null, onClose, onCreated }) {
  const { authRequest } = useAdminAuth();
  const { values } = useLookupLabels(BOOKING_LOOKUPS);
  const addressTypes = useLookupLabels(ADDRESS_LOOKUPS).values.ADDRESS_TYPE ?? [];
  // While the Add Address dialog is open it replaces this one (both close on Escape)
  const [addingAddress, setAddingAddress] = useState(false);
  const [addressKey, setAddressKey] = useState(0);
  const [customer, setCustomer] = useState(fixedCustomer);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [matches, setMatches] = useState([]);
  // The matches float below the search box, fixed to the viewport like Select's panel (the dialog cannot clip it)
  const searchRef = useRef(null);
  const panelRef = useRef(null);
  const [panel, setPanel] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [services, setServices] = useState([]);
  const [detail, setDetail] = useState(null);
  const [quote, setQuote] = useState(null);
  const [form, setForm] = useState({
    addressId: '',
    serviceId: '',
    variantId: '',
    // Chosen by staff: nothing by default, and the date and time only for SCHEDULED
    bookingType: '',
    date: todayInIndia(),
    time: '10:00',
    paymentMethod: 'CASH'
  });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  useEffect(() => {
    authRequest('/api/catalog/services?size=100')
      .then((page) => setServices(page.content))
      .catch(() => setServices([]));
  }, [authRequest]);

  useEffect(() => {
    if (!panel) return undefined;
    const closeOutside = (e) => {
      if (!searchRef.current?.contains(e.target) && !panelRef.current?.contains(e.target)) setPanel(null);
    };
    // A scroll anywhere but in the panel itself would leave the fixed panel behind
    const closeOnScroll = (e) => {
      if (!panelRef.current?.contains(e.target)) setPanel(null);
    };
    const close = () => setPanel(null);
    document.addEventListener('mousedown', closeOutside);
    window.addEventListener('scroll', closeOnScroll, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      window.removeEventListener('scroll', closeOnScroll, true);
      window.removeEventListener('resize', close);
    };
  }, [panel]);

  const openPanel = () => {
    const rect = searchRef.current.getBoundingClientRect();
    setPanel({ left: rect.left, top: rect.bottom + 6, width: rect.width });
  };

  // Customers matching the search (the customer list's search: name, mobile, email, customer id)
  useEffect(() => {
    if (customer || !search) return undefined;
    let cancelled = false;
    authRequest(`/api/users/customers?size=6&search=${encodeURIComponent(search)}`)
      .then((page) => !cancelled && setMatches(page.content.filter((row) => row.customerProfileId)))
      .catch(() => !cancelled && setMatches([]));
    return () => {
      cancelled = true;
    };
  }, [authRequest, customer, search]);

  // The chosen customer's addresses, the default first
  useEffect(() => {
    if (!customer) return undefined;
    let cancelled = false;
    authRequest(`/api/addresses/users/${customer.userId}`)
      .then((list) => {
        if (cancelled) return;
        setAddresses(list);
        // The newest is first after the default; a just-added address is chosen
        setForm((current) => ({
          ...current,
          addressId: list.some((address) => String(address.addressId) === current.addressId)
            ? current.addressId
            : list[0]
              ? String(list[0].addressId)
              : ''
        }));
      })
      .catch(() => !cancelled && setAddresses([]));
    return () => {
      cancelled = true;
    };
  }, [authRequest, customer, addressKey]);

  // The chosen service's options (variants)
  useEffect(() => {
    if (!form.serviceId) return undefined;
    let cancelled = false;
    authRequest(`/api/catalog/services/${form.serviceId}`)
      .then((loaded) => !cancelled && setDetail(loaded))
      .catch(() => !cancelled && setDetail(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, form.serviceId]);

  // The price for the choice, as the backend will charge it
  useEffect(() => {
    if (!form.serviceId) return undefined;
    let cancelled = false;
    const params = new URLSearchParams({ serviceId: form.serviceId });
    if (form.variantId) params.set('serviceVariantId', form.variantId);
    authRequest(`/api/catalog/services/quote?${params}`)
      .then((loaded) => !cancelled && setQuote(loaded))
      .catch(() => !cancelled && setQuote(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, form.serviceId, form.variantId]);

  const service = detail?.service?.serviceId === Number(form.serviceId) ? detail.service : null;
  const variants = service ? detail.variants.filter((variant) => variant.isActive) : [];
  const typeOptions = (values.BOOKING_TYPE ?? [])
    .filter((type) =>
      !service
        ? true
        : type.valueCode === 'INSTANT'
          ? service.isInstantBookingAllowed
          : service.isScheduledBookingAllowed
    )
    .map((type) => ({ value: type.valueCode, label: type.displayName }));
  const scheduled = form.bookingType === 'SCHEDULED';
  const ready =
    customer &&
    form.addressId &&
    form.serviceId &&
    form.bookingType &&
    form.paymentMethod &&
    (!scheduled || (form.date && form.time));

  const chooseService = (serviceId) => setForm((current) => ({ ...current, serviceId, variantId: '' }));

  const saveAddress = async (addressValues) => {
    const created = await authRequest('/api/addresses/create', {
      method: 'POST',
      body: {
        userId: customer.userId,
        ...addressBody(addressValues, false),
        countryCode: 'IN',
        isDefault: addressValues.isDefault || undefined
      }
    });
    setForm((current) => ({ ...current, addressId: String(created.addressId) }));
    setAddressKey((key) => key + 1);
    setAddingAddress(false);
  };

  if (addingAddress) {
    return (
      <FormModal
        title="Add Address"
        fields={addressFields(addressTypes)}
        initial={newAddressValues(customer.contactName, customer.mobileNumber, addressTypes)}
        submitLabel="Add Address"
        onSubmit={saveAddress}
        onClose={() => setAddingAddress(false)}
      />
    );
  }

  const submit = async () => {
    setSaving(true);
    setError('');
    try {
      const booking = await authRequest('/api/bookings/create', {
        method: 'POST',
        body: {
          customerProfileId: customer.customerProfileId,
          serviceId: Number(form.serviceId),
          serviceVariantId: form.variantId ? Number(form.variantId) : null,
          addressId: Number(form.addressId),
          bookingType: form.bookingType,
          scheduledStartAt: scheduled ? toInstant(form.date, form.time) : null,
          paymentMethod: form.paymentMethod
        }
      });
      onCreated(booking);
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title="Create Booking" onClose={onClose} width="max-w-[44rem]">
      <div className="max-h-[70vh] space-y-4 overflow-y-auto px-1 pb-1">
        {error && (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {error}
          </div>
        )}

        <div>
          <span className={labelClass}>Customer</span>
          {customer ? (
            <div className="flex items-center gap-3 rounded-xl border border-[var(--line)] px-3.5 py-2.5">
              <UserRound size={16} className="text-[var(--blue)]" />
              <span className="flex-1 font-bold text-[var(--ink)]">{customer.name}</span>
              {!fixedCustomer && (
                <button
                  type="button"
                  onClick={() => {
                    setCustomer(null);
                    setAddresses([]);
                  }}
                  className="text-sm font-bold text-[var(--blue)]"
                >
                  Change
                </button>
              )}
            </div>
          ) : (
            <div>
              <label ref={searchRef} className="relative block">
                <span className="sr-only">Search customers</span>
                <Search
                  size={16}
                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
                />
                <input
                  type="search"
                  value={searchText}
                  onChange={(e) => {
                    setSearchText(e.target.value);
                    openPanel();
                  }}
                  onFocus={openPanel}
                  onKeyDown={(e) => e.key === 'Escape' && panel && (e.stopPropagation(), setPanel(null))}
                  placeholder="Search by name, mobile or customer ID..."
                  className={searchInputClass}
                />
              </label>
              {search && panel && (
                <ul
                  ref={panelRef}
                  style={panel}
                  className="fixed z-[60] m-0 max-h-64 list-none overflow-y-auto rounded-xl border border-[var(--line)] bg-white p-1 shadow-[0_12px_32px_rgba(15,23,42,0.16)]"
                >
                  {matches.length === 0 ? (
                    <li className="px-3.5 py-3 text-sm text-[var(--muted)]">No customers found.</li>
                  ) : (
                    matches.map((row) => {
                      const name = [row.firstName, row.lastName].filter(Boolean).join(' ') || 'Name not added';
                      return (
                        <li key={row.userId}>
                          <button
                            type="button"
                            onClick={() => {
                              setPanel(null);
                              setCustomer({
                                customerProfileId: row.customerProfileId,
                                userId: row.userId,
                                name: `${name} - ${row.mobileNumber}`,
                                contactName: [row.firstName, row.lastName].filter(Boolean).join(' '),
                                mobileNumber: row.mobileNumber
                              });
                            }}
                            className="w-full rounded-lg px-3 py-2.5 text-left text-sm text-[var(--ink)] hover:bg-[var(--light)]"
                          >
                            {name} - {row.mobileNumber}
                          </button>
                        </li>
                      );
                    })
                  )}
                </ul>
              )}
            </div>
          )}
        </div>

        {customer && (
          <div>
            <span className={labelClass}>Address</span>
            {addresses.length === 0 ? (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--light)] px-4 py-3 text-sm">
                <span className="text-[var(--muted)]">The customer has no address yet.</span>
                <button
                  type="button"
                  onClick={() => setAddingAddress(true)}
                  className="rounded-lg bg-[var(--blue)] px-3 py-1.5 text-[0.8125rem] font-bold text-white hover:bg-[var(--navy)]"
                >
                  + Add Address
                </button>
              </div>
            ) : (
              <Select
                value={form.addressId}
                onChange={set('addressId')}
                options={addresses.map((address) => ({
                  value: String(address.addressId),
                  label: `${address.label ?? address.addressType}: ${address.addressLine1}, ${address.city}`
                }))}
              />
            )}
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <span className={labelClass}>Service</span>
            <Select
              value={form.serviceId}
              onChange={chooseService}
              options={services.map((item) => ({ value: String(item.serviceId), label: item.name }))}
              placeholder="Select a service"
            />
          </div>
          <div>
            <span className={labelClass}>Option</span>
            <Select
              value={form.variantId}
              onChange={set('variantId')}
              options={[
                { value: '', label: variants.length ? 'No option' : 'No options for this service' },
                ...variants.map((variant) => ({ value: String(variant.serviceVariantId), label: variant.name }))
              ]}
              disabled={!variants.length}
            />
          </div>
          <div>
            <span className={labelClass}>Booking Type</span>
            <Select
              value={form.bookingType}
              onChange={set('bookingType')}
              options={typeOptions}
              placeholder="Select booking type"
            />
          </div>
          <div>
            <span className={labelClass}>Payment Method</span>
            <Select
              value={form.paymentMethod}
              onChange={set('paymentMethod')}
              options={(values.PAYMENT_METHOD ?? []).map((method) => ({
                value: method.valueCode,
                label: method.displayName
              }))}
            />
          </div>
          {scheduled && (
            <>
              <div>
                <span className={labelClass}>Date</span>
                <DatePicker value={form.date} onChange={set('date')} min={todayInIndia()} />
              </div>
              <div>
                <span className={labelClass}>Time</span>
                <Select value={form.time} onChange={set('time')} options={TIME_OPTIONS} />
              </div>
            </>
          )}
        </div>

        {quote && (
          <div className="flex items-center justify-between rounded-xl bg-[var(--light)] px-4 py-3 text-sm">
            <span className="text-[var(--muted)]">Price (worked out again when booking)</span>
            <span className="text-base font-black text-[var(--ink)]">{formatAmount(quote.total)}</span>
          </div>
        )}
      </div>

      <div className="mt-5 flex justify-end gap-3 border-t border-[var(--line)] pt-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-xl border border-[var(--line)] px-4 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={submit}
          disabled={!ready || saving}
          className={`${primaryButtonClass} disabled:cursor-not-allowed disabled:opacity-45`}
        >
          {saving ? 'Booking…' : 'Create Booking'}
        </button>
      </div>
    </Modal>
  );
}
