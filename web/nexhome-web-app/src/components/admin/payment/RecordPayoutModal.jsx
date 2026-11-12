import { useEffect, useState } from 'react';

import DatePicker from '../common/DatePicker';
import Modal from '../common/Modal';
import Select from '../common/Select';
import FormField from '../system/FormField';
import { fieldClass, primaryButtonClass, resetButtonClass } from '../system/systemStyles';
import { formatAmount } from '../../../data/bookingOptions';
import { todayInIndia } from '../../../utils/schedule';

const AMOUNT = /^\d+(\.\d{1,2})?$/;
const TYPES = [
  { key: 'PAYOUT', label: 'Payout', note: 'Money you paid the partner' },
  { key: 'DEDUCTION', label: 'Deduction', note: 'An amount NexHome keeps' }
];

/**
 * Record a payout or a deduction (V62, V63). First the type, then only its fields:
 * - **Payout**: a payment made to the partner outside the app: amount, paid on (not in the future), paid through
 *   (PAYOUT_MODE), transaction id (not for cash), notes; POST /api/payouts/manual/create.
 * - **Deduction**: an amount NexHome keeps and never pays (a penalty, damage, ...): amount, date, reason
 *   (DEDUCTION_REASON), note (required for Other; the partner sees it); POST /api/payouts/deductions/create.
 * Both: the partner (a searchable list of the partners NexHome owes, GET /api/payouts/dues?dueOnly=true, with what is
 * due; fixed when `partner` is given), at most what is due, and what NexHome owes drops by the amount.
 * `onSaved(message)`.
 */
export default function RecordPayoutModal({ authRequest, partner, onClose, onSaved }) {
  const [dues, setDues] = useState(partner ? [partner] : []);
  const [modes, setModes] = useState([]);
  const [reasons, setReasons] = useState([]);
  const [type, setType] = useState('PAYOUT');
  const [values, setValues] = useState({
    partnerProfileId: partner?.partnerProfileId ?? '',
    amount: partner && Number(partner.balance) > 0 ? String(partner.balance) : '',
    date: todayInIndia(),
    paymentMode: '',
    transactionId: '',
    notes: '',
    deductionReason: ''
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/lookups/values/PAYOUT_MODE')
      .then((list) => {
        if (cancelled) return;
        setModes(list);
        const preferred = list.find((value) => value.isDefault) ?? list[0];
        if (preferred) setValues((current) => (current.paymentMode ? current : { ...current, paymentMode: preferred.valueCode }));
      })
      .catch(() => {});
    authRequest('/api/lookups/values/DEDUCTION_REASON')
      .then((list) => !cancelled && setReasons(list))
      .catch(() => {});
    if (!partner) {
      authRequest('/api/payouts/dues?dueOnly=true&size=100')
        .then((page) => !cancelled && setDues(page.content))
        .catch((err) => !cancelled && setError(err.message));
    }
    return () => {
      cancelled = true;
    };
  }, [authRequest, partner]);

  const payout = type === 'PAYOUT';
  const chosen = dues.find((due) => String(due.partnerProfileId) === String(values.partnerProfileId)) ?? null;
  const cash = values.paymentMode === 'CASH';

  const set = (key, value) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  const choosePartner = (id) => {
    const item = dues.find((entry) => String(entry.partnerProfileId) === String(id));
    setValues((current) => ({
      ...current,
      partnerProfileId: id,
      amount: payout && item && Number(item.balance) > 0 ? String(item.balance) : current.amount
    }));
    setErrors((current) => ({ ...current, partnerProfileId: undefined, amount: undefined }));
  };
  // A payout starts filled with what is due; a deduction starts empty (only part of it is usually kept)
  const chooseType = (key) => {
    setType(key);
    setErrors({});
    setError('');
    setValues((current) => ({
      ...current,
      amount: key === 'PAYOUT' && chosen && Number(chosen.balance) > 0 ? String(chosen.balance) : '',
      notes: ''
    }));
  };

  const save = async () => {
    const found = {};
    if (!values.partnerProfileId) found.partnerProfileId = 'Choose the partner';
    const amount = Number(values.amount);
    if (!AMOUNT.test(values.amount.trim()) || amount <= 0) found.amount = 'Enter an amount, e.g. 1500 or 1499.50';
    else if (chosen && amount > Number(chosen.balance)) found.amount = `At most ${formatAmount(chosen.balance)} is due`;
    if (!values.date) found.date = 'Choose the day';
    if (payout) {
      if (!values.paymentMode) found.paymentMode = 'Choose how the partner was paid';
      if (!cash && !values.transactionId.trim()) found.transactionId = 'Enter the transaction ID';
    } else {
      if (!values.deductionReason) found.deductionReason = 'Choose why it is deducted';
      if (values.deductionReason === 'OTHER' && !values.notes.trim()) found.notes = 'Say what the deduction is for';
    }
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    setError('');
    const name = chosen?.partnerName ?? chosen?.publicId ?? 'the partner';
    try {
      if (payout) {
        await authRequest('/api/payouts/manual/create', {
          method: 'POST',
          body: {
            partnerProfileId: Number(values.partnerProfileId),
            amount: values.amount.trim(),
            paidOn: values.date,
            paymentMode: values.paymentMode,
            transactionId: values.transactionId.trim() || null,
            notes: values.notes.trim() || null
          }
        });
        onSaved(`${formatAmount(amount)} paid to ${name} recorded.`);
      } else {
        await authRequest('/api/payouts/deductions/create', {
          method: 'POST',
          body: {
            partnerProfileId: Number(values.partnerProfileId),
            amount: values.amount.trim(),
            deductedOn: values.date,
            deductionReason: values.deductionReason,
            notes: values.notes.trim() || null
          }
        });
        onSaved(`${formatAmount(amount)} deducted from ${name}.`);
      }
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const partnerLabel = (item) => `${item.partnerName ?? 'No name yet'} · ${item.publicId} · ${formatAmount(item.balance)} due`;

  return (
    <Modal title="Record Payout / Deduction" onClose={onClose} width="max-w-[38rem]">
      <div className="space-y-4">
        {/* The type first; the fields below follow it */}
        <FormField label="Type" marker={false}>
          <div className="grid grid-cols-2 gap-3" role="radiogroup" aria-label="Type">
            {TYPES.map((item) => {
              const active = type === item.key;
              const tone = item.key === 'PAYOUT' ? 'border-[var(--green)] bg-[#E9F6EF]' : 'border-[#E53E3E] bg-[#FDECEC]';
              return (
                <button
                  key={item.key}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => chooseType(item.key)}
                  className={`rounded-xl border-2 px-4 py-3 text-left transition ${active ? tone : 'border-[var(--line)] bg-white hover:bg-[var(--light)]'}`}
                >
                  <div className={`text-sm font-black ${active ? (item.key === 'PAYOUT' ? 'text-[var(--green-2)]' : 'text-[#C53030]') : 'text-[var(--ink)]'}`}>
                    {item.label}
                  </div>
                  <div className="text-xs text-[var(--muted)]">{item.note}</div>
                </button>
              );
            })}
          </div>
        </FormField>

        {error && (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {error}
          </div>
        )}
        <FormField label="Partner" error={errors.partnerProfileId}>
          {partner ? (
            <div className={`${fieldClass(false)} flex items-center bg-[var(--light)]`}>{partnerLabel(partner)}</div>
          ) : (
            <Select
              ariaLabel="Partner"
              value={values.partnerProfileId}
              onChange={choosePartner}
              searchable
              searchPlaceholder="Search name, partner ID or mobile…"
              placeholder={dues.length ? 'Choose a partner…' : 'No partner is owed anything'}
              error={Boolean(errors.partnerProfileId)}
              options={dues.map((item) => ({ value: item.partnerProfileId, label: partnerLabel(item) }))}
            />
          )}
        </FormField>
        {chosen && (
          <div className="grid grid-cols-3 gap-3 rounded-xl bg-[var(--light)] px-4 py-3 text-[0.8125rem]">
            <div>
              <div className="font-bold text-[var(--muted)]">Earned</div>
              <div className="font-black text-[var(--ink)]">{formatAmount(chosen.totalEarned)}</div>
            </div>
            <div>
              <div className="font-bold text-[var(--muted)]">Paid / deducted</div>
              <div className="font-black text-[var(--ink)]">{formatAmount(Number(chosen.paidTotal) + Number(chosen.deductedTotal ?? 0))}</div>
            </div>
            <div>
              <div className="font-bold text-[var(--muted)]">Due now</div>
              <div className="font-black text-[var(--green-2)]">{formatAmount(chosen.balance)}</div>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label={payout ? 'Amount Paid (₹)' : 'Amount Deducted (₹)'} htmlFor="record-amount" error={errors.amount}>
            <input
              id="record-amount"
              inputMode="decimal"
              value={values.amount}
              onChange={(e) => set('amount', e.target.value.replace(/[^\d.]/g, ''))}
              className={fieldClass(errors.amount)}
            />
          </FormField>
          <FormField label={payout ? 'Paid On' : 'Deducted On'} error={errors.date}>
            <DatePicker value={values.date} onChange={(value) => set('date', value)} max={todayInIndia()} />
          </FormField>
          {payout ? (
            <>
              <FormField label="Paid Through" error={errors.paymentMode}>
                <Select
                  ariaLabel="Paid through"
                  value={values.paymentMode}
                  onChange={(value) => set('paymentMode', value)}
                  error={Boolean(errors.paymentMode)}
                  options={modes.map((mode) => ({ value: mode.valueCode, label: mode.displayName }))}
                />
              </FormField>
              <FormField
                label="Transaction ID"
                htmlFor="record-transaction"
                required={!cash}
                hint="The UPI reference or bank UTR shown in the payment app."
                error={errors.transactionId}
              >
                <input
                  id="record-transaction"
                  value={values.transactionId}
                  maxLength={100}
                  onChange={(e) => set('transactionId', e.target.value)}
                  placeholder={cash ? 'Not needed for cash' : 'e.g. 432112345678'}
                  className={fieldClass(errors.transactionId)}
                />
              </FormField>
            </>
          ) : (
            <div className="sm:col-span-2">
              <FormField label="Reason" error={errors.deductionReason}>
                <Select
                  ariaLabel="Deduction reason"
                  value={values.deductionReason}
                  onChange={(value) => set('deductionReason', value)}
                  placeholder="Choose a reason…"
                  error={Boolean(errors.deductionReason)}
                  options={reasons.map((reason) => ({ value: reason.valueCode, label: reason.displayName }))}
                />
              </FormField>
            </div>
          )}
        </div>
        <FormField
          label={payout ? 'Notes' : 'Note'}
          htmlFor="record-notes"
          required={!payout && values.deductionReason === 'OTHER'}
          hint={payout ? undefined : 'The partner sees this note, e.g. Late for booking NHBOOK0239.'}
          error={errors.notes}
        >
          <textarea
            id="record-notes"
            rows={2}
            maxLength={500}
            value={values.notes}
            onChange={(e) => set('notes', e.target.value)}
            className={`${fieldClass(errors.notes)} h-auto py-2`}
          />
        </FormField>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className={resetButtonClass} disabled={busy}>
            Cancel
          </button>
          <button type="button" onClick={save} className={primaryButtonClass} disabled={busy}>
            {busy ? 'Saving…' : payout ? 'Record Payout' : 'Record Deduction'}
          </button>
        </div>
      </div>
    </Modal>
  );
}
