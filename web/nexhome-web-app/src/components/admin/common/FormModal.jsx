import { useEffect, useRef, useState } from 'react';

import FormField from '../system/FormField';
import DatePicker from './DatePicker';
import ImageUploadField from './ImageUploadField';
import { fieldClass } from '../system/systemStyles';
import Modal from './Modal';
import MultiSelect from './MultiSelect';
import Select from './Select';
import Toggle from './Toggle';

/**
 * A dialog form built from a list of fields, for the console's simpler records (catalogue categories, addresses, FAQs,
 * partner details). Each field:
 *
 * - `key`, `label`, `type`: text, textarea (`rows`, `counter`), number, money (₹ prefix), code (upper-cased, A-Z 0-9
 *   _), image (a File, with `currentUrl`; see ImageUploadField), select (`options` [{ value, label }]), multiselect
 *   (an array of option values, chips below the box), date
 *   (yyyy-MM-dd with the console's DatePicker, `min` / `max`) or toggle (`onText` / `offText` beside it)
 * - `required`, `hint`, `placeholder`, `maxLength`, `wide` (both columns), `disabled` (e.g. fixed after create)
 * - `show(values)` to show it only sometimes (e.g. HOURLY billing), `validate(value, values)` returning an error
 * - `options` may also be a function of the values, for choices that depend on another field
 * - `derive(values)` for a field filled from another until edited (e.g. the slug from the name)
 *
 * `lookup` ({ watch, run(value) }) fills untouched fields from what `run` resolves to when the watched field changes.
 *
 * `onSubmit(values)` returns a promise; its error's message is shown above the form.
 */
export default function FormModal({
  title,
  fields,
  initial,
  submitLabel = 'Save',
  onSubmit,
  onClose,
  width = 'max-w-[40rem]',
  lookup = null
}) {
  const [values, setValues] = useState(initial);
  const [touched, setTouched] = useState({});
  const touchedRef = useRef(touched);
  useEffect(() => {
    touchedRef.current = touched;
  }, [touched]);

  // `lookup`: when its `watch` field changes, `run(value)` may resolve to { key: value } for other fields, which fill
  // those the user has not typed in (e.g. the bank and branch of an IFSC code)
  const watched = lookup ? values[lookup.watch] : undefined;
  useEffect(() => {
    if (!lookup) return undefined;
    let cancelled = false;
    Promise.resolve(lookup.run(watched))
      .then((found) => {
        if (cancelled || !found) return;
        setValues((current) => {
          const next = { ...current };
          Object.entries(found).forEach(([key, value]) => {
            if (value && !touchedRef.current[key]) next[key] = value;
          });
          return next;
        });
        setErrors((current) => {
          const next = { ...current };
          Object.keys(found).forEach((key) => delete next[key]);
          return next;
        });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
    // Only when the watched value changes (the lookup object is rebuilt with every render of the page)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watched]);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);

  const visible = fields.filter((field) => !field.show || field.show(values));

  const set = (key, value) => {
    setTouched((current) => ({ ...current, [key]: true }));
    setErrors((current) => ({ ...current, [key]: undefined }));
    setValues((current) => {
      const next = { ...current, [key]: value };
      // Derived fields follow their source until edited themselves
      fields.forEach((field) => {
        if (field.derive && !touched[field.key] && field.key !== key) next[field.key] = field.derive(next);
      });
      return next;
    });
  };

  const validate = () => {
    const found = {};
    visible.forEach((field) => {
      const value = values[field.key];
      const empty = value === undefined || value === null || String(value).trim() === '';
      if (field.required && empty) found[field.key] = `${field.label} is required`;
      else if (!empty && (field.type === 'number' || field.type === 'money') && Number.isNaN(Number(value))) {
        found[field.key] = `${field.label} must be a number`;
      } else if (!empty && (field.type === 'number' || field.type === 'money') && Number(value) < (field.min ?? 0)) {
        found[field.key] = `${field.label} must be at least ${field.min ?? 0}`;
      } else if (field.validate) {
        const message = field.validate(value, values);
        if (message) found[field.key] = message;
      }
    });
    return found;
  };

  const submit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    try {
      await onSubmit(values);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  // `options` may depend on the other fields, e.g. leaving out what another field already chose
  const optionsOf = (field) => (typeof field.options === 'function' ? field.options(values) : field.options);
  const input = (field) => {
    const id = `form-${field.key}`;
    const value = values[field.key] ?? '';
    const error = Boolean(errors[field.key]);
    switch (field.type) {
      case 'textarea':
        return (
          <>
            <textarea
              id={id}
              rows={field.rows}
              className={`${fieldClass(error)} ${field.rows ? '' : 'min-h-[5.5rem]'} resize-y`}
              value={value}
              maxLength={field.maxLength}
              placeholder={field.placeholder}
              disabled={field.disabled}
              onChange={(e) => set(field.key, e.target.value)}
            />
            {field.counter && field.maxLength && (
              <div className="mt-1 text-right text-xs text-[var(--muted)]">
                {value.length}/{field.maxLength}
              </div>
            )}
          </>
        );
      case 'select':
        return (
          <Select
            id={id}
            value={value}
            onChange={(next) => set(field.key, next)}
            options={optionsOf(field)}
            placeholder={field.placeholder ?? 'Select…'}
            error={error}
            disabled={field.disabled}
          />
        );
      case 'multiselect':
        return (
          <MultiSelect
            id={id}
            value={value}
            onChange={(next) => set(field.key, next)}
            options={optionsOf(field)}
            placeholder={field.placeholder ?? 'Select…'}
            disabled={field.disabled}
          />
        );
      case 'date':
        return (
          <DatePicker
            id={id}
            value={value}
            onChange={(next) => set(field.key, next)}
            min={field.min}
            max={field.max}
            placeholder={field.placeholder ?? 'Select a date'}
            error={error}
            disabled={field.disabled}
          />
        );
      case 'toggle':
        return (
          <div className="flex items-center gap-3 rounded-xl border border-[var(--line)] px-3.5 py-2.5">
            <Toggle
              checked={Boolean(values[field.key])}
              onChange={(on) => set(field.key, on)}
              label={field.label}
              disabled={field.disabled}
            />
            <span className="text-sm text-[var(--ink)]">
              {values[field.key] ? (field.onText ?? 'Yes') : (field.offText ?? 'No')}
            </span>
          </div>
        );
      case 'image':
        return (
          <ImageUploadField
            id={id}
            value={values[field.key] ?? null}
            onChange={(file) => set(field.key, file)}
            currentUrl={field.currentUrl}
            error={error}
            disabled={field.disabled}
          />
        );
      case 'money':
        return (
          <div className="relative">
            <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm font-bold text-[var(--muted)]">
              ₹
            </span>
            <input
              id={id}
              inputMode="decimal"
              className={`${fieldClass(error)} pl-8`}
              value={value}
              placeholder={field.placeholder ?? '0.00'}
              disabled={field.disabled}
              onChange={(e) => set(field.key, e.target.value.replace(/[^0-9.]/g, ''))}
            />
          </div>
        );
      default:
        return (
          <input
            id={id}
            inputMode={field.type === 'number' ? 'numeric' : undefined}
            className={`${fieldClass(error)} ${field.type === 'code' ? 'font-mono' : ''}`}
            value={value}
            maxLength={field.maxLength}
            placeholder={field.placeholder}
            disabled={field.disabled}
            onChange={(e) => {
              const raw = e.target.value;
              const next =
                field.type === 'number'
                  ? raw.replace(/[^0-9]/g, '')
                  : field.type === 'code'
                    ? raw
                        .toUpperCase()
                        .replace(/\s+/g, '_')
                        .replace(/[^A-Z0-9_]/g, '')
                    : raw;
              set(field.key, next);
            }}
          />
        );
    }
  };

  return (
    <Modal title={title} onClose={onClose} width={width}>
      <form onSubmit={submit} noValidate>
        {serverError && (
          <div
            role="alert"
            className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {serverError}
          </div>
        )}
        {/* The fields scroll inside a fixed height (the category form's), so long forms keep their title and buttons in
            view; the padding leaves room for the focus rings and the scrollbar */}
        <div className="-mx-1.5 max-h-[min(30rem,calc(100vh-14rem))] overflow-y-auto px-1.5 py-1">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {visible.map((field) => (
              <div
                key={field.key}
                className={field.wide || field.type === 'textarea' || field.type === 'image' ? 'sm:col-span-2' : ''}
              >
                <FormField
                  label={field.label}
                  htmlFor={`form-${field.key}`}
                  required={Boolean(field.required)}
                  marker={field.type !== 'toggle'}
                  hint={field.hint}
                  error={errors[field.key]}
                >
                  {input(field)}
                </FormField>
              </div>
            ))}
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-3 border-t border-[var(--line)] pt-4">
          <button
            type="button"
            className="rounded-xl border border-[var(--line)] bg-white px-6 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="rounded-xl bg-[var(--blue)] px-6 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-60"
            disabled={saving}
          >
            {saving ? 'Saving…' : submitLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
