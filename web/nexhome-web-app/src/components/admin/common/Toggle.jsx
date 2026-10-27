/** An on/off switch in the NexHome green; `label` names it for screen readers. */
export default function Toggle({ checked, onChange, disabled = false, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--green)]/30 disabled:cursor-not-allowed disabled:opacity-50 ${
        checked ? 'bg-[var(--green)]' : 'bg-[#CBD5E1]'
      }`}
    >
      <span
        className={`inline-block h-[1.125rem] w-[1.125rem] rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-[1.375rem]' : 'translate-x-[0.1875rem]'
        }`}
      />
    </button>
  );
}
