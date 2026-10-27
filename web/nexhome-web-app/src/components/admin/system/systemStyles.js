/** Shared classes of the System Configuration screens (Roles, Functions) and their dialogs. */

export const cardClass =
  'rounded-2xl border border-[var(--line)] bg-white p-5 shadow-[0_1px_2px_rgba(14,51,89,0.04)]';

export const searchInputClass =
  'w-full rounded-xl border border-[var(--line)] bg-white py-2.5 pl-10 pr-3 text-sm text-[var(--ink)] outline-none placeholder:text-[#8A9AAD] focus:border-[var(--blue)] focus:ring-2 focus:ring-[var(--blue)]/15';

export const resetButtonClass =
  'flex items-center justify-center gap-2 rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]';

export const primaryButtonClass =
  'flex items-center gap-2 rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_16px_rgba(11,67,139,0.25)] hover:bg-[var(--navy)]';

export const headClass =
  'whitespace-nowrap px-3 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';

/** A dialog input; red border while it has an error. Disabled inputs (fixed codes) are greyed. */
export function fieldClass(error) {
  return `w-full rounded-xl border bg-white px-3.5 py-2.5 text-sm text-[var(--ink)] outline-none transition placeholder:text-[#8A9AAD] focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--light)] disabled:text-[var(--muted)] ${
    error
      ? 'border-[#E53E3E] focus:ring-[#E53E3E]/15'
      : 'border-[var(--line)] focus:border-[var(--blue)] focus:ring-[var(--blue)]/15'
  }`;
}

/** Codes of roles, modules and functions: capital letters, digits and underscores, starting with a letter. */
export const CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

/** What a code input keeps of the typed text: upper-cased, spaces become underscores, anything else is dropped. */
export function toCode(text) {
  return text.toUpperCase().replace(/\s+/g, '_').replace(/[^A-Z0-9_]/g, '');
}

/** A module code as the menu names it: SYSTEM_CONFIGURATION is "System Configuration". */
export function moduleLabel(code) {
  return code
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(' ');
}
