import { useState } from 'react';

import { useAdminAuth } from '../../../context/adminAuth';
import Modal from '../common/Modal';
import Select from '../common/Select';
import Toggle from '../common/Toggle';
import FormField from './FormField';
import { CODE_PATTERN, fieldClass, toCode } from './systemStyles';

function validate(form) {
  const errors = {};
  const codeError = (value, name) =>
    !value ? `${name} is required` : CODE_PATTERN.test(value) ? undefined : 'Start with a letter; use capital letters, digits and underscores';
  errors.functionModule = codeError(form.functionModule, 'Module');
  errors.functionCode = codeError(form.functionCode, 'Function code');
  if (!form.functionName.trim()) errors.functionName = 'Function name is required';
  if (!form.functionType) errors.functionType = 'Function type is required';
  Object.keys(errors).forEach((key) => errors[key] === undefined && delete errors[key]);
  return errors;
}

/**
 * Add Function (POST /api/functions/create) or, with `fn`, Edit Function (PUT /api/functions/update). Module and code
 * are fixed once created; `modules` (the existing ones) are offered as suggestions, and `types` are the FUNCTION_TYPE
 * lookup values. The description is always sent, since the backend replaces it.
 */
export default function FunctionFormModal({ fn, modules, types, onClose, onSaved }) {
  const { authRequest } = useAdminAuth();
  const editing = Boolean(fn);
  const [form, setForm] = useState(() => ({
    functionModule: fn?.functionModule ?? '',
    functionCode: fn?.functionCode ?? '',
    functionName: fn?.functionName ?? '',
    functionType: fn?.functionType ?? (types.some((type) => type.valueCode === 'FEATURE') ? 'FEATURE' : ''),
    description: fn?.description ?? '',
    activeFlag: fn?.activeFlag ?? true
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

    const fields = {
      functionName: form.functionName.trim(),
      functionType: form.functionType,
      description: form.description.trim(),
      activeFlag: form.activeFlag
    };
    setSaving(true);
    try {
      const saved = editing
        ? await authRequest('/api/functions/update', { method: 'PUT', body: { functionId: fn.functionId, ...fields } })
        : await authRequest('/api/functions/create', {
            method: 'POST',
            body: { functionModule: form.functionModule, functionCode: form.functionCode, ...fields }
          });
      onSaved(saved, editing);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit Function' : 'Add New Function'} onClose={onClose} width="max-w-[40rem]">
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Module"
            htmlFor="function-module"
            error={errors.functionModule}
            hint={editing ? 'The module cannot be changed.' : 'Pick an existing module or type a new one.'}
          >
            <input
              id="function-module"
              className={`${fieldClass(errors.functionModule)} font-mono`}
              list="function-modules"
              value={form.functionModule}
              onChange={(e) => set('functionModule', toCode(e.target.value))}
              maxLength={50}
              placeholder="e.g. CATALOG"
              disabled={editing}
              autoFocus={!editing}
            />
            <datalist id="function-modules">
              {modules.map((module) => (
                <option key={module} value={module} />
              ))}
            </datalist>
          </FormField>
          <FormField
            label="Function Code"
            htmlFor="function-code"
            error={errors.functionCode}
            hint={editing ? 'The code cannot be changed.' : 'Unique, e.g. BOOKING_MANAGE'}
          >
            <input
              id="function-code"
              className={`${fieldClass(errors.functionCode)} font-mono`}
              value={form.functionCode}
              onChange={(e) => set('functionCode', toCode(e.target.value))}
              maxLength={100}
              disabled={editing}
            />
          </FormField>
          <FormField label="Function Name" htmlFor="function-name" error={errors.functionName}>
            <input
              id="function-name"
              className={fieldClass(errors.functionName)}
              value={form.functionName}
              onChange={(e) => set('functionName', e.target.value)}
              maxLength={150}
              placeholder="e.g. Manage Bookings"
              autoFocus={editing}
            />
          </FormField>
          <FormField label="Function Type" htmlFor="function-type" error={errors.functionType}>
            <Select
              id="function-type"
              value={form.functionType}
              onChange={(value) => set('functionType', value)}
              placeholder="Select a type"
              error={Boolean(errors.functionType)}
              options={types.map((type) => ({ value: type.valueCode, label: type.displayName }))}
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Description" htmlFor="function-description" required={false}>
              <textarea
                id="function-description"
                className={`${fieldClass()} min-h-[5.5rem] resize-y`}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                placeholder="What read, write and delete allow"
              />
            </FormField>
          </div>
        </div>

        <div className="mt-4 flex items-center gap-4 rounded-xl border border-[var(--line)] p-4">
          <Toggle checked={form.activeFlag} onChange={(activeFlag) => set('activeFlag', activeFlag)} label="Function active" />
          <div>
            <div className="text-sm font-bold text-[var(--ink)]">{form.activeFlag ? 'Active' : 'Inactive'}</div>
            <div className="text-[0.8125rem] text-[var(--muted)]">
              {form.activeFlag
                ? 'Roles granted this function get its access.'
                : 'No role gets this access until it is activated (super users always have it).'}
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
            {saving ? 'Saving…' : editing ? 'Save Changes' : 'Create Function'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
