import { useState } from 'react';

import { useAdminAuth } from '../../../context/adminAuth';
import Modal from '../common/Modal';
import Toggle from '../common/Toggle';
import FormField from './FormField';
import { fieldClass } from './systemStyles';

/** Value codes: mostly capitals (IN_PROGRESS), but language codes such as "te" are lower case. */
const VALUE_CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.-]*$/;

/** The metadata text as an object: {} when empty, null when it is not a JSON object. */
function parseMetadata(text) {
  if (!text.trim()) return {};
  try {
    const value = JSON.parse(text);
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

function validate(form) {
  const errors = {};
  if (!form.valueCode) errors.valueCode = 'Value code is required';
  else if (!VALUE_CODE_PATTERN.test(form.valueCode)) {
    errors.valueCode = 'Start with a letter or digit; use letters, digits, underscores, dots and hyphens';
  }
  if (!form.displayName.trim()) errors.displayName = 'Display name is required';
  if (form.displayOrder !== '' && !/^-?\d+$/.test(form.displayOrder)) errors.displayOrder = 'Display order must be a whole number';
  if (parseMetadata(form.metadata) === null) errors.metadata = 'Metadata must be a JSON object, e.g. {"key": "value"}';
  return errors;
}

/**
 * Add Value (POST /api/lookups/values/add) or, with `value`, Edit Value (PUT /api/lookups/values/update) of the lookup
 * `type`. The code is fixed once created. Values of a system-managed type cannot be deactivated, and an inactive value
 * cannot be the default, so switching it off also clears the default. The description and metadata are always sent
 * (the backend replaces the description; an empty metadata box saves {}).
 */
export default function LookupValueFormModal({ type, value, onClose, onSaved }) {
  const { authRequest } = useAdminAuth();
  const editing = Boolean(value);
  // A value of a system type stays active once it is (the backend refuses to deactivate it)
  const activeLocked = type.isSystemManaged && (value?.activeFlag ?? true);
  const [form, setForm] = useState(() => ({
    valueCode: value?.valueCode ?? '',
    displayName: value?.displayName ?? '',
    displayOrder: String(value?.displayOrder ?? ''),
    description: value?.description ?? '',
    metadata: value?.metadata && Object.keys(value.metadata).length ? JSON.stringify(value.metadata, null, 2) : '',
    activeFlag: value?.activeFlag ?? true,
    isDefault: value?.isDefault ?? false
  }));
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (field, fieldValue) => {
    setForm((current) => ({
      ...current,
      [field]: fieldValue,
      // An inactive value cannot be the default
      ...(field === 'activeFlag' && !fieldValue ? { isDefault: false } : {})
    }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;

    const fields = {
      displayName: form.displayName.trim(),
      description: form.description.trim(),
      displayOrder: form.displayOrder === '' ? 0 : Number(form.displayOrder),
      activeFlag: form.activeFlag,
      isDefault: form.isDefault,
      metadata: parseMetadata(form.metadata)
    };
    setSaving(true);
    try {
      const saved = editing
        ? await authRequest('/api/lookups/values/update', {
            method: 'PUT',
            body: { lookupValueId: value.lookupValueId, ...fields }
          })
        : await authRequest('/api/lookups/values/add', {
            method: 'POST',
            body: { lookupTypeId: type.lookupTypeId, valueCode: form.valueCode, ...fields }
          });
      onSaved(saved, editing);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit Value' : 'Add Value'} onClose={onClose} width="max-w-[40rem]">
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Lookup Type" htmlFor="lookup-value-type" marker={false}>
            <input id="lookup-value-type" className={`${fieldClass()} font-mono`} value={type.typeCode} readOnly disabled />
          </FormField>
          <FormField
            label="Value Code"
            htmlFor="lookup-value-code"
            error={errors.valueCode}
            hint={editing ? 'The code cannot be changed.' : 'Unique within the type; stored in records, e.g. IN_PROGRESS'}
          >
            <input
              id="lookup-value-code"
              className={`${fieldClass(errors.valueCode)} font-mono`}
              value={form.valueCode}
              onChange={(e) => set('valueCode', e.target.value.replace(/\s+/g, '_').replace(/[^A-Za-z0-9_.-]/g, ''))}
              maxLength={80}
              disabled={editing}
              autoFocus={!editing}
            />
          </FormField>
          <FormField label="Display Name" htmlFor="lookup-value-name" error={errors.displayName}>
            <input
              id="lookup-value-name"
              className={fieldClass(errors.displayName)}
              value={form.displayName}
              onChange={(e) => set('displayName', e.target.value)}
              maxLength={150}
              placeholder="e.g. In Progress"
              autoFocus={editing}
            />
          </FormField>
          <FormField
            label="Display Order"
            htmlFor="lookup-value-order"
            required={false}
            error={errors.displayOrder}
            hint="Dropdowns list the values from the lowest order up; 0 when left empty."
          >
            <input
              id="lookup-value-order"
              className={fieldClass(errors.displayOrder)}
              inputMode="numeric"
              value={form.displayOrder}
              onChange={(e) => set('displayOrder', e.target.value.replace(/[^\d-]/g, ''))}
              maxLength={6}
              placeholder="0"
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Description" htmlFor="lookup-value-description" required={false}>
              <textarea
                id="lookup-value-description"
                className={`${fieldClass()} min-h-[4.5rem] resize-y`}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
              />
            </FormField>
          </div>
          <div className="sm:col-span-2">
            <FormField
              label="Metadata"
              htmlFor="lookup-value-metadata"
              required={false}
              error={errors.metadata}
              hint="Extra settings the platform reads from this value, as a JSON object (e.g. a percentage or slot times). Change it only if you know what reads it."
            >
              <textarea
                id="lookup-value-metadata"
                className={`${fieldClass(errors.metadata)} min-h-[6rem] resize-y font-mono text-[0.8125rem]`}
                value={form.metadata}
                onChange={(e) => set('metadata', e.target.value)}
                placeholder="{}"
                spellCheck={false}
              />
            </FormField>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="flex items-center gap-4 rounded-xl border border-[var(--line)] p-4">
            <Toggle
              checked={form.activeFlag}
              onChange={(activeFlag) => set('activeFlag', activeFlag)}
              label="Value active"
              disabled={activeLocked}
            />
            <div>
              <div className="text-sm font-bold text-[var(--ink)]">{form.activeFlag ? 'Active' : 'Inactive'}</div>
              <div className="text-[0.8125rem] text-[var(--muted)]">
                {activeLocked ? 'Values of a system type stay active.' : form.activeFlag ? 'Offered in dropdowns.' : 'Not offered.'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-4 rounded-xl border border-[var(--line)] p-4">
            <Toggle
              checked={form.isDefault}
              onChange={(isDefault) => set('isDefault', isDefault)}
              label="Default value"
              disabled={!form.activeFlag}
            />
            <div>
              <div className="text-sm font-bold text-[var(--ink)]">{form.isDefault ? 'Default' : 'Not default'}</div>
              <div className="text-[0.8125rem] text-[var(--muted)]">
                {form.activeFlag ? 'One per type; replaces the current default.' : 'An inactive value cannot be the default.'}
              </div>
            </div>
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
            className="rounded-xl bg-[var(--blue)] px-6 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-60"
            disabled={saving}
          >
            {saving ? 'Saving…' : editing ? 'Save Changes' : 'Add Value'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
