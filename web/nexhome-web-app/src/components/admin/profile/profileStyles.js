/** Shared classes of the console's profile pages (see ProfileParts). */

/** The class of an input shown in place of a row's value: red border while it has an error. */
export function inlineInputClass(error) {
  return `w-full rounded-lg border bg-white px-3 py-2 text-[0.9375rem] font-normal text-[var(--ink)] outline-none transition placeholder:text-[#A8B6C6] focus:ring-2 ${
    error ? 'border-[#E53E3E] focus:ring-[#E53E3E]/15' : 'border-[var(--line)] focus:border-[var(--blue)] focus:ring-[var(--blue)]/15'
  }`;
}

/** The summary tab bar's tab classes; the selected tab is blue and underlined. */
export function profileTabClass(selected) {
  return `relative flex items-center gap-2 px-1 pb-3 text-sm font-bold transition ${
    selected
      ? 'text-[var(--blue)] after:absolute after:inset-x-0 after:-bottom-px after:h-[0.1875rem] after:rounded-full after:bg-[var(--blue)]'
      : 'text-[var(--muted)] hover:text-[var(--ink)]'
  }`;
}
