import {
  BadgeCheck,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  Clock,
  Globe,
  History,
  IdCard,
  Landmark,
  Mail,
  MapPin,
  Pencil,
  Phone,
  UserRound
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import ActivityLog from '../../components/admin/profile/ActivityLog';
import CompletionAvatar from '../../components/admin/profile/CompletionAvatar';
import StatusMenu from '../../components/admin/profile/StatusMenu';
import { InfoRow, InfoSection, SectionActions } from '../../components/admin/profile/ProfileParts';
import Toast from '../../components/admin/common/Toast';
import { useAdminAuth } from '../../context/adminAuth';
import { accountStatusLabel, canManageStaffRole, statusBadgeClass } from '../../data/staffOptions';
import useActivity from '../../hooks/useActivity';
import { splitApiDateTime } from '../../utils/format';

/**
 * The staff lists, highest role first. A profile opens under the list it was opened from (`list`, e.g.
 * /admin/users/sub-admins/:id), so the sidebar, the page's access check and the breadcrumb follow that list.
 */
const STAFF_LISTS = [
  { key: 'superAdmins', roleCode: 'SUPER_USER', label: 'Super Admins', to: '/admin/users/super-admins' },
  { key: 'admins', roleCode: 'ADMIN', label: 'Admins', to: '/admin/users/admins' },
  { key: 'subAdmins', roleCode: 'SUB_ADMIN', label: 'Sub Admins', to: '/admin/users/sub-admins' }
];

const NAME_PATTERN = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;


/** The profile fields the Edit form changes (PUT /api/profiles/admins/update), per tab. */
const OVERVIEW_FIELDS = ['firstName', 'lastName', 'employeeCode', 'department', 'jobTitle'];
const ADDRESS_FIELDS = ['addressLine1', 'addressLine2', 'landmark', 'city', 'state', 'postalCode'];
/** Required fields cannot be sent blank (400), so a blank one is left out; optional ones are cleared with "". */
const REQUIRED_FIELDS = ['firstName', 'lastName', 'addressLine1', 'city', 'state', 'postalCode'];

const LABELS = {
  firstName: 'First Name',
  lastName: 'Last Name',
  employeeCode: 'Employee Code',
  department: 'Department',
  jobTitle: 'Job Title',
  addressLine1: 'Address Line 1',
  addressLine2: 'Address Line 2',
  landmark: 'Landmark',
  city: 'City',
  state: 'State',
  postalCode: 'PIN Code',
  email: 'Email Address'
};

/** The details that make a staff profile complete, in the order the completion lists them. */
const PROFILE_CHECKS = [
  { label: 'Name', done: (profile) => Boolean(profile.firstName && profile.lastName) },
  { label: 'Email', done: (profile, user) => Boolean(user.email) },
  { label: 'Verified mobile', done: (profile, user) => Boolean(user.accountVerifiedDate) },
  { label: 'Employee code', done: (profile) => Boolean(profile.employeeCode) },
  { label: 'Department', done: (profile) => Boolean(profile.department) },
  { label: 'Job title', done: (profile) => Boolean(profile.jobTitle) },
  { label: 'Registered address', done: (profile) => Boolean(profile.addressLine1) }
];

function fullName(profile) {
  return [profile.firstName, profile.lastName].filter(Boolean).join(' ');
}

function formatDate(value) {
  return splitApiDateTime(value)?.date ?? null;
}

function formatDateTime(value) {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : null;
}

/** The fields each section edits; Contact & Account edits the account's email (PUT /api/users/update). */
const SECTION_FIELDS = { personal: OVERVIEW_FIELDS, contact: ['email'], address: ADDRESS_FIELDS };

/** The form of one section, from the current values. */
function toForm(section, profile, user) {
  return Object.fromEntries(
    SECTION_FIELDS[section].map((field) => [field, (field === 'email' ? user.email : profile[field]) ?? ''])
  );
}

/** Checks the fields the form has (one section's). */
function validate(form) {
  const errors = {};
  const text = (field) => (form[field] ?? '').trim();
  ['firstName', 'lastName'].forEach((field) => {
    if (text(field) && !NAME_PATTERN.test(form[field])) {
      errors[field] = 'Use letters, spaces, dots, apostrophes and hyphens';
    }
  });
  const address = ['addressLine1', 'city', 'state', 'postalCode'].filter((field) => text(field));
  if ('addressLine1' in form && address.length && address.length < 4) {
    ['addressLine1', 'city', 'state', 'postalCode']
      .filter((field) => !text(field))
      .forEach((field) => {
        errors[field] = `${LABELS[field]} is needed with the rest of the address`;
      });
  }
  if (text('email') && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(text('email'))) {
    errors.email = 'Enter a valid email address';
  }
  if (text('postalCode') && !/^[1-9][0-9]{5}$/.test(text('postalCode'))) {
    errors.postalCode = 'Enter a 6-digit PIN code';
  }
  return errors;
}

/** An input in place of a row's value while editing, with its error below. */
function InlineInput({ field, form, errors, onChange, placeholder }) {
  return (
    <div className="min-w-0 flex-1">
      <input
        id={`profile-${field}`}
        aria-label={LABELS[field]}
        placeholder={placeholder ?? LABELS[field]}
        value={form[field]}
        onChange={onChange(field)}
        maxLength={field === 'postalCode' ? 6 : 255}
        type={field === 'email' ? 'email' : 'text'}
        inputMode={field === 'postalCode' ? 'numeric' : undefined}
        className={`w-full rounded-lg border bg-white px-3 py-2 text-[0.9375rem] font-normal text-[var(--ink)] outline-none transition placeholder:text-[#A8B6C6] focus:ring-2 ${
          errors[field]
            ? 'border-[#E53E3E] focus:ring-[#E53E3E]/15'
            : 'border-[var(--line)] focus:border-[var(--blue)] focus:ring-[var(--blue)]/15'
        }`}
      />
      {errors[field] && <div className="mt-1 text-xs font-bold text-[#C53030]">{errors[field]}</div>}
    </div>
  );
}

/** A labelled input of the Address edit form (the grid shown while editing the address). */
function EditField({ field, form, errors, onChange }) {
  return (
    <div>
      <label htmlFor={`profile-${field}`} className="text-[0.75rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
        {LABELS[field]}
      </label>
      <input
        id={`profile-${field}`}
        value={form[field]}
        onChange={onChange(field)}
        maxLength={field === 'postalCode' ? 6 : 255}
        inputMode={field === 'postalCode' ? 'numeric' : undefined}
        className={`mt-1 w-full rounded-xl border bg-white px-3 py-2.5 text-sm text-[var(--ink)] outline-none transition focus:ring-2 ${
          errors[field]
            ? 'border-[#E53E3E] focus:ring-[#E53E3E]/15'
            : 'border-[var(--line)] focus:border-[var(--blue)] focus:ring-[var(--blue)]/15'
        }`}
      />
      {errors[field] && <div className="mt-1 text-xs font-bold text-[#C53030]">{errors[field]}</div>}
    </div>
  );
}

/**
 * An admin's profile (User Management > Admins > public id), or with `self` the signed-in admin's own (My Profile, from
 * the account menu): photo, name, public id, joined date and status, then Overview and Address tabs. Each section's
 * Edit changes the staff profile or the email; the mobile number, role and status are managed elsewhere. Data from
 * GET /api/profiles/admins/{id} and GET /api/users/{userId}, or /admins/me and /users/me.
 */
export default function AdminProfilePage({ self = false, list = 'admins' }) {
  const { adminProfileId } = useParams();
  const { authRequest, can, isSuperUser, session } = useAdminAuth();
  const [state, setState] = useState({ profile: null, user: null, error: '' });
  // Everyone edits their own profile (My Profile); someone else's needs write on the function of their highest staff
  // role (ADMIN_MANAGE, SUB_ADMIN_MANAGE; a super user's: super users), for the profile and the email alike.
  // Without it the sections have no Edit.
  const ownerRole = STAFF_LISTS.find((list) =>
    state.user?.roles?.some((role) => role.isActive && role.roleCode === list.roleCode)
  )?.roleCode;
  const canEdit = self || (Boolean(ownerRole) && canManageStaffRole(ownerRole, can, isSuperUser));
  const canEditContact = canEdit;
  const [tab, setTab] = useState('overview');
  // The section being edited ('personal', 'contact' or 'address') and its form
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  // The Activity Logs tab: account and profile changes from the audit columns
  const activity = useActivity(
    tab === 'activity' && state.profile ? `/api/profiles/admins/activity/${state.profile.adminProfileId}` : null
  );

  useEffect(() => {
    let cancelled = false;
    authRequest(self ? '/api/profiles/admins/me' : `/api/profiles/admins/${adminProfileId}`)
      .then((profile) =>
        authRequest(self ? '/api/users/me' : `/api/users/${profile.userId}`).then((user) => {
          if (!cancelled) setState({ profile, user, error: '' });
        })
      )
      .catch((err) => {
        if (!cancelled) setState({ profile: null, user: null, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, adminProfileId, self]);

  const { profile, user, error } = state;
  const name = profile ? fullName(profile) : '';

  useEffect(() => {
    document.title = `${self ? 'My Profile' : name || profile?.publicId || 'Admin'} | NexHome Admin`;
  }, [name, profile, self]);

  // Back to the list the profile was opened from (named as on that list's page), then the profile's public id
  const listLink = STAFF_LISTS.find((entry) => entry.key === list) ?? STAFF_LISTS[1];
  const crumbs = self
    ? [{ label: 'My Profile' }]
    : [
        { label: 'User Management' },
        { label: listLink.label, to: listLink.to },
        // The public id once loaded; nothing in its place meanwhile
        ...(profile ? [{ label: profile.publicId }] : [])
      ];

  if (error) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} />
        <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {error}
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} />
        <div className="h-40 animate-pulse rounded-2xl bg-white" />
      </div>
    );
  }

  const initials = [profile.firstName, profile.lastName].filter(Boolean).map((part) => part[0]).join('').toUpperCase();
  const address = [profile.addressLine1, profile.addressLine2, profile.landmark, profile.city, profile.state, profile.postalCode];

  const startEditing = (section) => () => {
    setEditing(section);
    setForm(toForm(section, profile, user));
    setErrors({});
  };
  const cancelEditing = () => {
    setEditing(null);
    setForm(null);
  };
  /** Only one section is edited at a time: the others hide their Edit. */
  const editLink = (section) =>
    editing || !(section === 'contact' ? canEditContact : canEdit) ? null : startEditing(section);
  const onChange = (field) => (e) => {
    const value = field === 'postalCode' ? e.target.value.replace(/\D/g, '') : e.target.value;
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const inputProps = { form, errors, onChange };

  const save = async () => {
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    try {
      if (editing === 'contact') {
        // The email is on the account; "" clears it
        const email = form.email.trim();
        if (email !== (user.email ?? '')) {
          const updatedUser = await authRequest('/api/users/update', {
            method: 'PUT',
            body: { userId: user.userId, email }
          });
          setState((current) => ({ ...current, user: updatedUser }));
        }
      } else {
        // Only the changed fields: employee details need write on the owner's staff role even on one's own profile
        const body = { adminProfileId: profile.adminProfileId };
        Object.entries(form).forEach(([field, value]) => {
          const text = value.trim();
          if (text === (profile[field] ?? '')) return;
          if (text) body[field] = text;
          else if (!REQUIRED_FIELDS.includes(field)) body[field] = '';
        });
        const updated = await authRequest('/api/profiles/admins/update', { method: 'PUT', body });
        setState((current) => ({ ...current, profile: updated }));
      }
      const saved = { personal: 'Personal information', contact: 'Contact details', address: 'Address' }[editing];
      cancelEditing();
      setToast({ message: `${saved} saved.`, tone: 'success', id: Date.now() });
    } catch (err) {
      setToast({ message: err.message, tone: 'error', id: Date.now() });
    } finally {
      setSaving(false);
    }
  };

  const tabClass = (key) =>
    `relative flex items-center gap-2 px-1 pb-3 text-sm font-bold transition ${
      tab === key
        ? 'text-[var(--blue)] after:absolute after:inset-x-0 after:-bottom-px after:h-[0.1875rem] after:rounded-full after:bg-[var(--blue)]'
        : 'text-[var(--muted)] hover:text-[var(--ink)]'
    }`;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      {/* One card: the summary (photo, name and status; admin id; joined on), the tabs and
          their details */}
      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-center gap-6 px-6 pb-6 pt-7 sm:px-8">
          <CompletionAvatar
            checks={PROFILE_CHECKS.map((check) => ({ label: check.label, done: check.done(profile, user) }))}
            photoUrl={user.profilePhotoUrl}
            name={name}
            initials={initials}
            canEdit={canEdit}
          />

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className={`mb-0 text-2xl font-black leading-tight ${name ? 'text-[var(--navy)]' : 'text-[var(--muted)]'}`}>
                {name || 'Name not added yet'}
              </h1>
              <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadgeClass(user.accountStatus)}`}>
                {accountStatusLabel(user.accountStatus)}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <IdCard size={15} className="text-[var(--blue)]" />
                Admin ID: <span className="font-bold text-[var(--ink)]">{profile.publicId}</span>
              </span>
            </div>
            <div className="mt-1.5 flex items-center gap-2 text-sm text-[var(--muted)]">
              <CalendarDays size={15} className="text-[var(--blue)]" />
              Joined on <span className="font-bold text-[var(--ink)]">{formatDate(user.creationDate) ?? '—'}</span>
            </div>
          </div>

          {/* Account status, as the staff lists' row menu: not on one's own account */}
          {!self && canEdit && user.userId !== session?.user?.userId && (
            <div className="-mt-4 self-start">
              <StatusMenu
                user={user}
                name={name}
                onChanged={(status) => setState((current) => ({ ...current, user: { ...current.user, accountStatus: status } }))}
                onMessage={(message, tone = 'success') => setToast({ message, tone, id: Date.now() })}
              />
            </div>
          )}
        </div>

        <div className="mx-6 flex gap-8 border-b border-[var(--line)] sm:mx-8" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'overview'} className={tabClass('overview')} onClick={() => setTab('overview')}>
            <UserRound size={16} />
            Overview
          </button>
          <button type="button" role="tab" aria-selected={tab === 'address'} className={tabClass('address')} onClick={() => setTab('address')}>
            <MapPin size={16} />
            Address
          </button>
          <button type="button" role="tab" aria-selected={tab === 'activity'} className={tabClass('activity')} onClick={() => setTab('activity')}>
            <History size={16} />
            Activity Logs
          </button>
        </div>

        <div className="px-6 py-7 sm:px-8" role="tabpanel">
          {tab === 'activity' ? (
            <ActivityLog entries={activity.entries} loading={activity.loading} />
          ) : tab === 'overview' ? (
            <div className="grid grid-cols-1 gap-10 lg:grid-cols-2 lg:gap-0 lg:divide-x lg:divide-[var(--line)]">
              <div className="lg:pr-10">
                <InfoSection title="Personal Information" onEdit={editLink('personal')}>
                  <InfoRow icon={UserRound} label="Full Name" value={name}>
                    {editing === 'personal' && (
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <InlineInput field="firstName" placeholder="First name" {...inputProps} />
                        <InlineInput field="lastName" placeholder="Last name" {...inputProps} />
                      </div>
                    )}
                  </InfoRow>
                  <InfoRow icon={IdCard} label="Employee Code" value={profile.employeeCode}>
                    {editing === 'personal' && <InlineInput field="employeeCode" {...inputProps} />}
                  </InfoRow>
                  <InfoRow icon={Building2} label="Department" value={profile.department}>
                    {editing === 'personal' && <InlineInput field="department" {...inputProps} />}
                  </InfoRow>
                  <InfoRow icon={BriefcaseBusiness} label="Job Title" value={profile.jobTitle}>
                    {editing === 'personal' && <InlineInput field="jobTitle" {...inputProps} />}
                  </InfoRow>
                </InfoSection>
                {editing === 'personal' && <SectionActions onCancel={cancelEditing} onSave={save} saving={saving} />}
              </div>
              <div className="lg:pl-10">
                <InfoSection title="Contact & Account" onEdit={editLink('contact')}>
                  <InfoRow icon={Mail} label="Email Address" value={user.email}>
                    {editing === 'contact' && <InlineInput field="email" placeholder="Email address" {...inputProps} />}
                  </InfoRow>
                  <InfoRow icon={Phone} label="Mobile Number" value={`${user.countryCode} ${user.mobileNumber}`} />
                  <InfoRow icon={Clock} label="Last Login" value={formatDateTime(user.lastLoginAt) ?? 'Never'} />
                  <InfoRow icon={BadgeCheck} label="Mobile Verified On" value={formatDate(user.accountVerifiedDate)} />
                </InfoSection>
                {editing === 'contact' && <SectionActions onCancel={cancelEditing} onSave={save} saving={saving} />}
              </div>
            </div>
          ) : editing === 'address' ? (
            <InfoSection title="Registered Address" divided={false}>
              <div className="grid grid-cols-1 gap-x-8 gap-y-6 pt-3 sm:grid-cols-2 xl:grid-cols-3">
                {ADDRESS_FIELDS.map((field) => (
                  <EditField key={field} field={field} {...inputProps} />
                ))}
              </div>
              <SectionActions onCancel={cancelEditing} onSave={save} saving={saving} />
            </InfoSection>
          ) : address.some(Boolean) ? (
            <div>
              <InfoSection title="Registered Address" onEdit={editLink('address')} divided={false}>
                <InfoRow icon={MapPin} label="Address">
                  <span className="block">{[profile.addressLine1, profile.addressLine2].filter(Boolean).join(', ')}</span>
                  <span className="block">
                    {[profile.city, profile.state].filter(Boolean).join(', ')}
                    {profile.postalCode ? ` - ${profile.postalCode}` : ''}
                  </span>
                </InfoRow>
                <InfoRow icon={Landmark} label="Landmark" value={profile.landmark} />
                <InfoRow icon={Globe} label="Country" value={profile.countryCode === 'IN' ? 'India' : profile.countryCode} />
              </InfoSection>
            </div>
          ) : (
            <div className="flex flex-col items-center py-10 text-center">
              <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
                <MapPin size={22} />
              </span>
              <div className="font-bold text-[var(--ink)]">No address added yet</div>
              {canEdit && <div className="mb-4 text-sm text-[var(--muted)]">Add the registered address.</div>}
              {canEdit && (
                <button
                  type="button"
                  className="flex items-center gap-2 rounded-xl border border-[#C7E3FC] bg-[#EDF6FD] px-4 py-2 text-sm font-bold text-[var(--blue)] hover:bg-[var(--blue)] hover:text-white"
                  onClick={startEditing('address')}
                  disabled={Boolean(editing)}
                >
                  <Pencil size={14} />
                  Add Address
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
