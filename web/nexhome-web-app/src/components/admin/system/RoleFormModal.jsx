import { useState } from 'react';

import { useAdminAuth } from '../../../context/adminAuth';
import Modal from '../common/Modal';
import Toggle from '../common/Toggle';
import FormField from './FormField';
import { CODE_PATTERN, fieldClass, toCode } from './systemStyles';

function validate(form) {
  const errors = {};
  if (!form.code) errors.code = 'Role code is required';
  else if (!CODE_PATTERN.test(form.code)) errors.code = 'Start with a letter; use capital letters, digits and underscores';
  if (!form.name.trim()) errors.name = 'Role name is required';
  return errors;
}

/**
 * Add Role (POST /api/roles/create) or, with `role`, Edit Role (PUT /api/roles/update). The code is fixed once
 * created. The description is always sent, since the backend replaces it (an empty one clears it). `system` roles
 * (the seeded ones the code relies on) cannot be deactivated here.
 */
export default function RoleFormModal({ role, system = false, onClose, onSaved }) {
  const { authRequest } = useAdminAuth();
  const editing = Boolean(role);
  const [form, setForm] = useState(() => ({
    code: role?.code ?? '',
    name: role?.name ?? '',
    description: role?.description ?? '',
    activeFlag: role?.activeFlag ?? true
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

    const fields = { name: form.name.trim(), description: form.description.trim(), activeFlag: form.activeFlag };
    setSaving(true);
    try {
      const saved = editing
        ? await authRequest('/api/roles/update', { method: 'PUT', body: { roleId: role.roleId, ...fields } })
        : await authRequest('/api/roles/create', { method: 'POST', body: { code: form.code, ...fields } });
      onSaved(saved, editing);
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit Role' : 'Add New Role'} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Role Code"
            htmlFor="role-code"
            error={errors.code}
            hint={editing ? 'The code cannot be changed.' : 'e.g. SUPPORT_AGENT'}
          >
            <input
              id="role-code"
              className={`${fieldClass(errors.code)} font-mono`}
              value={form.code}
              onChange={(e) => set('code', toCode(e.target.value))}
              maxLength={30}
              disabled={editing}
              autoFocus={!editing}
            />
          </FormField>
          <FormField label="Role Name" htmlFor="role-name" error={errors.name}>
            <input
              id="role-name"
              className={fieldClass(errors.name)}
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
              maxLength={80}
              placeholder="e.g. Support Agent"
              autoFocus={editing}
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Description" htmlFor="role-description" required={false}>
              <textarea
                id="role-description"
                className={`${fieldClass()} min-h-[5.5rem] resize-y`}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                placeholder="What this role is for"
              />
            </FormField>
          </div>
        </div>

        <div className="mt-4 flex items-center gap-4 rounded-xl border border-[var(--line)] p-4">
          <Toggle
            checked={form.activeFlag}
            onChange={(activeFlag) => set('activeFlag', activeFlag)}
            disabled={system}
            label="Role active"
          />
          <div>
            <div className="text-sm font-bold text-[var(--ink)]">{form.activeFlag ? 'Active' : 'Inactive'}</div>
            <div className="text-[0.8125rem] text-[var(--muted)]">
              {system
                ? 'A system role is always active.'
                : form.activeFlag
                  ? 'Users holding this role get its permissions.'
                  : 'The role grants nothing until it is activated.'}
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
            {saving ? 'Saving…' : editing ? 'Save Changes' : 'Create Role'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
