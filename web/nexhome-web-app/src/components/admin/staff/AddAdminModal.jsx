import { Lock, Mail, Phone, UserCog } from 'lucide-react';
import { useState } from 'react';

import { useAdminAuth } from '../../../context/adminAuth';
import { ADMIN_ROLE } from '../../../data/staffOptions';
import Modal from '../common/Modal';
import Toggle from '../common/Toggle';

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const EMPTY_FORM = { email: '', mobileNumber: '', isActive: true };

function validate(form) {
  const errors = {};
  // Optional, but checked when given
  if (form.email.trim() && !EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Enter a valid email address';
  if (!form.mobileNumber) errors.mobileNumber = 'Mobile number is required';
  else if (!/^[0-9]{10}$/.test(form.mobileNumber)) errors.mobileNumber = 'Enter the 10-digit mobile number';
  return errors;
}

function Field({ label, error, icon: Icon, htmlFor, required = true, children }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-[var(--ink)]">
        {label} {required ? <span className="text-[#E53E3E]">*</span> : <span className="font-normal text-[var(--muted)]">(optional)</span>}
      </label>
      <div className="relative">
        <Icon size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
        {children}
      </div>
      {error && <div className="mt-1 text-xs font-bold text-[#C53030]">{error}</div>}
    </div>
  );
}

/**
 * Add New Admin, or another account of a fixed `role` (a sub-admin, super admin or customer, with its own `noun` and
 * `note`): the mobile number (+91), optional email and account status, through Create user (POST /api/users/create).
 * Names and the rest of the profile are filled in later by the person (`note`). An inactive account is created and
 * then set to DEACTIVATED.
 */
export default function AddAdminModal({
  onClose,
  onCreated,
  role = ADMIN_ROLE,
  title = 'Add New Admin',
  submitLabel = 'Create Admin',
  noun = 'admin',
  note = 'The admin adds their name and other details under My Profile after signing in.'
}) {
  const { authRequest } = useAdminAuth();
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (field) => (e) => {
    const value = field === 'mobileNumber' ? e.target.value.replace(/\D/g, '').slice(0, 10) : e.target.value;
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;

    setSaving(true);
    let user;
    try {
      user = await authRequest('/api/users/create', {
        method: 'POST',
        body: {
          countryCode: '+91',
          mobileNumber: form.mobileNumber,
          email: form.email.trim() || null,
          roleId: role.id
        }
      });
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
      return;
    }
    if (!form.isActive) {
      try {
        await authRequest('/api/users/status/update', {
          method: 'PUT',
          body: { userId: user.userId, accountStatus: 'DEACTIVATED' }
        });
      } catch (err) {
        // The account exists; report that it is still active rather than losing it
        onCreated(user, `The ${noun} was added but is still active: ${err.message}`);
        return;
      }
    }
    onCreated(user);
  };

  const inputClass = (field) =>
    `w-full rounded-xl border bg-white py-2.5 pl-10 pr-3 text-sm text-[var(--ink)] outline-none transition placeholder:text-[#8A9AAD] focus:ring-2 ${
      errors[field]
        ? 'border-[#E53E3E] focus:ring-[#E53E3E]/15'
        : 'border-[var(--line)] focus:border-[var(--blue)] focus:ring-[var(--blue)]/15'
    }`;
  const sectionTitle = 'rounded-t-xl bg-[var(--light)] px-4 py-2.5 text-[0.9375rem] font-black text-[var(--navy)]';

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        {serverError && (
          <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {serverError}
          </div>
        )}

        <section className="rounded-xl border border-[var(--line)]">
          <div className={sectionTitle}>Basic Information</div>
          <div className="grid grid-cols-1 gap-4 p-4 sm:grid-cols-2">
            <Field label="Mobile Number" icon={Phone} error={errors.mobileNumber} htmlFor="admin-mobile">
              <input
                id="admin-mobile"
                type="tel"
                inputMode="numeric"
                className={inputClass('mobileNumber')}
                placeholder="Enter mobile number"
                value={form.mobileNumber}
                onChange={set('mobileNumber')}
                autoFocus
              />
            </Field>
            <Field label="Email" icon={Mail} required={false} error={errors.email} htmlFor="admin-email">
              <input
                id="admin-email"
                type="email"
                className={inputClass('email')}
                placeholder="Enter email address"
                value={form.email}
                onChange={set('email')}
                maxLength={255}
              />
            </Field>
            <Field label="Role" icon={UserCog} htmlFor="admin-role">
              <div
                id="admin-role"
                className="flex w-full items-center justify-between rounded-xl border border-[var(--line)] bg-[var(--light)] py-2.5 pl-10 pr-3 text-sm font-bold text-[var(--ink)]"
                title="Set by this page"
              >
                {role.label}
                <Lock size={14} className="text-[#A8B6C6]" />
              </div>
            </Field>
          </div>
          <div className="border-t border-[var(--line)] px-4 py-3 text-[0.8125rem] text-[var(--muted)]">
            {note}
          </div>
        </section>

        <section className="mt-4 rounded-xl border border-[var(--line)]">
          <div className={sectionTitle}>Account Status</div>
          <div className="flex items-center gap-4 p-4">
            <Toggle
              checked={form.isActive}
              onChange={(isActive) => setForm((current) => ({ ...current, isActive }))}
              label="Account active"
            />
            <div>
              <div className="text-sm font-bold text-[var(--ink)]">{form.isActive ? 'Active' : 'Inactive'}</div>
              <div className="text-[0.8125rem] text-[var(--muted)]">
                {form.isActive ? 'User can login to the platform' : 'User cannot login until activated'}
              </div>
            </div>
          </div>
        </section>

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
            {saving ? 'Creating…' : submitLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
