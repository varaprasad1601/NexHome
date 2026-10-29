import { useState } from 'react';

import { useAdminAuth } from '../../../context/adminAuth';
import Modal from '../common/Modal';
import Toggle from '../common/Toggle';
import FormField from './FormField';
import { CODE_PATTERN, fieldClass, toCode } from './systemStyles';

function validate(form) {
  const errors = {};
  if (!form.typeCode) errors.typeCode = 'Type code is required';
  else if (!CODE_PATTERN.test(form.typeCode)) errors.typeCode = 'Start with a letter; use capital letters, digits and underscores';
  if (!form.typeName.trim()) errors.typeName = 'Type name is required';
  return errors;
}

/**
 * Add Lookup Type (POST /api/lookups/types/create) or, with `type`, Edit Lookup Type (PUT /api/lookups/types/update).
 * The code is fixed once created. Types made here are not system-managed; a system-managed type cannot be
 * deactivated, so its switch is locked on. The description is always sent, since the backend replaces it.
 */
export default function LookupTypeFormModal({ type, onClose, onSaved }) {
  const { authRequest } = useAdminAuth();
  const editing = Boolean(type);
  const locked = Boolean(type?.isSystemManaged);
  const [form, setForm] = useState(() => ({
    typeCode: type?.typeCode ?? '',
    typeName: type?.typeName ?? '',
    description: type?.description ?? '',
    activeFlag: type?.activeFlag ?? true
  }));
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;

    const fields = { typeName: form.typeName.trim(), description: form.description.trim(), activeFlag: form.activeFlag };
    setSaving(true);
    try {
      const saved = editing
        ? await authRequest('/api/lookups/types/update', { method: 'PUT', body: { lookupTypeId: type.lookupTypeId, ...fields } })
        : await authRequest('/api/lookups/types/create', { method: 'POST', body: { typeCode: form.typeCode, ...fields } });
      onSaved(saved, editing);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit Lookup Type' : 'Add Lookup Type'} onClose={onClose} width="max-w-[40rem]">
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Type Code"
            htmlFor="lookup-type-code"
            error={errors.typeCode}
            hint={editing ? 'The code cannot be changed.' : 'Unique, e.g. PAYMENT_STATUS'}
          >
            <input
              id="lookup-type-code"
              className={`${fieldClass(errors.typeCode)} font-mono`}
              value={form.typeCode}
              onChange={(e) => set('typeCode', toCode(e.target.value))}
              maxLength={80}
              disabled={editing}
              autoFocus={!editing}
            />
          </FormField>
          <FormField label="Type Name" htmlFor="lookup-type-name" error={errors.typeName}>
            <input
              id="lookup-type-name"
              className={fieldClass(errors.typeName)}
              value={form.typeName}
              onChange={(e) => set('typeName', e.target.value)}
              maxLength={120}
              placeholder="e.g. Payment Status"
              autoFocus={editing}
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Description" htmlFor="lookup-type-description" required={false}>
              <textarea
                id="lookup-type-description"
                className={`${fieldClass()} min-h-[5.5rem] resize-y`}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                placeholder="What the values of this type are used for"
              />
            </FormField>
          </div>
        </div>

        <div className="mt-4 flex items-center gap-4 rounded-xl border border-[var(--line)] p-4">
          <Toggle
            checked={form.activeFlag}
            onChange={(activeFlag) => set('activeFlag', activeFlag)}
            label="Lookup type active"
            disabled={locked}
          />
          <div>
            <div className="text-sm font-bold text-[var(--ink)]">{form.activeFlag ? 'Active' : 'Inactive'}</div>
            <div className="text-[0.8125rem] text-[var(--muted)]">
              {locked
                ? 'A system lookup type is always active: the platform relies on its values.'
                : form.activeFlag
                  ? 'Its active values are offered in dropdowns.'
                  : 'None of its values are offered until it is activated.'}
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
            {saving ? 'Saving…' : editing ? 'Save Changes' : 'Create Lookup Type'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
