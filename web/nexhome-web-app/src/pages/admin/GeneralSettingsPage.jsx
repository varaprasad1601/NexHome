import { Bell, Building2, CalendarClock, RotateCcw, Save } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import InfoTip from '../../components/admin/common/InfoTip';
import Toast from '../../components/admin/common/Toast';
import { MoneyTabs } from '../../components/admin/payment/moneyParts';
import { cardClass, fieldClass, primaryButtonClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';

const TABS = [
  { key: 'BUSINESS', label: 'Business & Contact', icon: Building2, text: 'Shown on the public website: its header, footer, contact page and WhatsApp links.' },
  { key: 'BOOKING', label: 'Booking Rules', icon: CalendarClock, text: 'Limits and charges the app applies to bookings and carts.' },
  { key: 'STAFF_ALERTS', label: 'Staff Alerts', icon: Bell, text: 'Which events notify staff, and which come as High priority (red tag, three-note chime).' }
];

const LEVELS = [
  { value: 'OFF', label: 'Off', on: 'bg-[#F1F3F6] text-[var(--ink)]' },
  { value: 'NORMAL', label: 'Normal', on: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { value: 'HIGH', label: 'High', on: 'bg-[#FDECEC] text-[#C53030]' }
];
const levelLabel = (value) => LEVELS.find((level) => level.value === value)?.label ?? value;

/** "Changed by Name on date", or "Default" while the default applies. */
function Changed({ setting }) {
  if (!setting.updatedDate) return <span className="text-xs text-[var(--muted)]">Default</span>;
  return (
    <span className="text-xs text-[var(--muted)]">
      Changed{setting.updatedByName ? ` by ${setting.updatedByName}` : ''} · {setting.updatedDate}
    </span>
  );
}

/** One text or number setting: label with its help, the input, default and who changed it. */
function SettingField({ setting, value, error, disabled, onChange }) {
  const numeric = setting.valueType === 'NUMBER' || setting.valueType === 'DECIMAL';
  const id = `setting-${setting.settingKey}`;
  const defaultText = setting.valueType === 'ALERT' ? levelLabel(setting.defaultValue) : setting.defaultValue;
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 flex items-center gap-1.5 text-sm font-bold text-[var(--ink)]">
        {setting.label}
        <InfoTip text={setting.description} />
      </label>
      <div className="flex gap-2">
        <input
          id={id}
          className={fieldClass(error)}
          value={value}
          disabled={disabled}
          inputMode={numeric ? 'decimal' : setting.valueType === 'DIGITS' ? 'numeric' : undefined}
          maxLength={setting.maxLength ?? undefined}
          onChange={(e) => onChange(e.target.value)}
        />
        {!disabled && value !== setting.defaultValue && (
          <button
            type="button"
            title={`Back to the default: ${defaultText}`}
            aria-label={`Reset ${setting.label} to its default`}
            onClick={() => onChange(setting.defaultValue)}
            className="flex-shrink-0 rounded-lg border border-[var(--line)] px-2.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
          >
            <RotateCcw size={15} />
          </button>
        )}
      </div>
      {error ? (
        <div className="mt-1 text-xs font-bold text-[#C53030]">{error}</div>
      ) : (
        <div className="mt-1 flex flex-wrap justify-between gap-x-3">
          <span className="text-xs text-[var(--muted)]">
            {numeric && setting.min != null ? `${setting.min} to ${setting.max} · ` : ''}Default: {defaultText}
          </span>
          <Changed setting={setting} />
        </div>
      )}
    </div>
  );
}

/** A staff alert as a row: what happens, then Off / Normal / High. */
function AlertRow({ setting, value, disabled, onChange }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--line)] py-3 last:border-b-0">
      <div className="min-w-0 flex-1">
        <div className="text-sm font-bold text-[var(--ink)]">{setting.label}</div>
        <div className="text-[0.8125rem] text-[var(--muted)]">{setting.description}</div>
        <Changed setting={setting} />
      </div>
      <div role="radiogroup" aria-label={setting.label} className="flex flex-shrink-0 rounded-xl border border-[var(--line)] p-1">
        {LEVELS.map((level) => {
          const active = value === level.value;
          return (
            <button
              key={level.value}
              type="button"
              role="radio"
              aria-checked={active}
              disabled={disabled}
              onClick={() => onChange(level.value)}
              className={`rounded-lg px-3.5 py-1.5 text-[0.8125rem] font-bold disabled:cursor-not-allowed ${
                active ? level.on : 'text-[var(--muted)] hover:text-[var(--ink)]'
              }`}
            >
              {level.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Settings > General (/admin/settings, SETTINGS_MANAGE): GET /api/settings, tabs Business & Contact (public: the
 * website reads them from GET /api/settings/public), Booking Rules (online payment fee, offer expiry, no-partner
 * warning, book-ahead and cart limits) and Staff Alerts (each event Off / Normal / High). Super users save the tab's changed
 * settings are saved together (PUT /api/settings/update, all or none); without it the page is read only.
 */
export default function GeneralSettingsPage() {
  const { authRequest, isSuperUser } = useAdminAuth();
  // Only super users change settings (V51); admins and sub-admins with SETTINGS_MANAGE read see them
  const canWrite = isSuperUser;
  const [tab, setTab] = useState('BUSINESS');
  const [settings, setSettings] = useState(null);
  const [values, setValues] = useState({});
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message) => setToast({ message, tone: 'success', id: Date.now() }), []);

  const show = (list) => {
    setSettings(list);
    setValues(Object.fromEntries(list.map((setting) => [setting.settingKey, setting.value])));
  };

  useEffect(() => {
    document.title = 'General Settings | NexHome Admin';
    let cancelled = false;
    authRequest('/api/settings')
      .then((list) => {
        if (cancelled) return;
        setSettings(list);
        setValues(Object.fromEntries(list.map((setting) => [setting.settingKey, setting.value])));
      })
      .catch((err) => !cancelled && setLoadError(err.message));
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  const inTab = (settings ?? []).filter((setting) => setting.settingGroup === tab);
  const changed = inTab.filter((setting) => (values[setting.settingKey] ?? '').trim() !== setting.value);

  const save = async () => {
    setSaveError('');
    setSaving(true);
    try {
      const list = await authRequest('/api/settings/update', {
        method: 'PUT',
        body: { settings: changed.map((setting) => ({ settingKey: setting.settingKey, value: values[setting.settingKey] })) }
      });
      show(list);
      showToast('Settings saved.');
    } catch (err) {
      setSaveError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const discard = () => {
    setSaveError('');
    setValues((current) => ({
      ...current,
      ...Object.fromEntries(inTab.map((setting) => [setting.settingKey, setting.value]))
    }));
  };

  const setValue = (key, value) => setValues((current) => ({ ...current, [key]: value }));
  const current = TABS.find((entry) => entry.key === tab);
  const Icon = current.icon;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Settings' }, { label: 'General' }]}
        title="General Settings"
        description="Business and contact details, booking rules and which events alert staff."
      />

      {loadError && (
        <div role="alert" className="mb-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {loadError}
        </div>
      )}
      {!canWrite && settings && (
        <div className="mb-4 rounded-xl border border-[var(--line)] bg-[var(--light)] px-4 py-3 text-sm font-bold text-[var(--muted)]">
          View only: only super users can change the general settings.
        </div>
      )}

      <section className={cardClass}>
        <MoneyTabs
          tabs={TABS.map((entry) => ({ key: entry.key, label: entry.label }))}
          active={tab}
          onChange={(key) => {
            setSaveError('');
            setTab(key);
          }}
        />
        <div className="mb-5 flex items-start gap-3">
          <span className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
            <Icon size={18} />
          </span>
          <p className="m-0 pt-2 text-sm text-[var(--muted)]">{current.text}</p>
        </div>

        {!settings && !loadError && (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            {Array.from({ length: 4 }, (_, index) => (
              <div key={index} className="h-16 animate-pulse rounded-xl bg-[var(--light)]" />
            ))}
          </div>
        )}

        {settings &&
          (tab === 'STAFF_ALERTS' ? (
            <div>
              {inTab.map((setting) => (
                <AlertRow
                  key={setting.settingKey}
                  setting={setting}
                  value={values[setting.settingKey]}
                  disabled={!canWrite}
                  onChange={(value) => setValue(setting.settingKey, value)}
                />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-x-6 gap-y-5 md:grid-cols-2">
              {inTab.map((setting) => (
                <SettingField
                  key={setting.settingKey}
                  setting={setting}
                  value={values[setting.settingKey] ?? ''}
                  disabled={!canWrite}
                  onChange={(value) => setValue(setting.settingKey, value)}
                />
              ))}
            </div>
          ))}

        {settings && canWrite && (
          <div className="mt-6 flex flex-wrap items-center justify-end gap-3 border-t border-[var(--line)] pt-4">
            {saveError && (
              <div role="alert" className="mr-auto text-sm font-bold text-[#C53030]">
                {saveError}
              </div>
            )}
            {changed.length > 0 && (
              <span className="text-[0.8125rem] text-[var(--muted)]">
                {changed.length} unsaved change{changed.length === 1 ? '' : 's'}
              </span>
            )}
            <button
              type="button"
              onClick={discard}
              disabled={!changed.length || saving}
              className="rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              Discard
            </button>
            <button
              type="button"
              onClick={save}
              disabled={!changed.length || saving}
              className={`${primaryButtonClass} disabled:cursor-not-allowed disabled:opacity-60`}
            >
              <Save size={16} />
              {saving ? 'Saving…' : 'Save Changes'}
            </button>
          </div>
        )}
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
