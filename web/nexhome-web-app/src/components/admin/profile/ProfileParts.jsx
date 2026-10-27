import { Pencil } from 'lucide-react';

/**
 * The building blocks of the console's profile pages (staff, customers): rows of details in titled sections, each
 * section with its own Edit and, while editing, Cancel / Save at its bottom.
 */

/** One detail: a round icon tile, the label and its value (or, while editing, its inputs); empty values show a dash. */
export function InfoRow({ icon: Icon, label, value, children }) {
  // `{editing && <input />}` gives false while viewing: show the value then
  const shown = children === undefined || children === null || children === false ? value : children;
  return (
    <div className="flex items-start gap-4 py-3.5">
      <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
        <Icon size={18} strokeWidth={2} />
      </span>
      <div className="min-w-0 flex-1 pt-0.5">
        <div className="text-[0.8125rem] text-[var(--muted)]">{label}</div>
        <div className={`mt-0.5 break-words text-[0.9375rem] ${shown ? 'font-bold text-[var(--ink)]' : 'text-[#A8B6C6]'}`}>
          {shown || '—'}
        </div>
      </div>
    </div>
  );
}

/** A titled group of rows, with an Edit link at the top right; `divided` draws lines between the rows. */
export function InfoSection({ title, onEdit, divided = true, children }) {
  return (
    <section>
      <div className="mb-1 flex items-center justify-between gap-3">
        <h3 className="mb-0 text-[1.0625rem] font-black text-[var(--navy)]">{title}</h3>
        {onEdit && (
          <button
            type="button"
            className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
            onClick={onEdit}
          >
            <Pencil size={14} />
            Edit
          </button>
        )}
      </div>
      <div className={divided ? 'divide-y divide-[var(--line)]' : ''}>{children}</div>
    </section>
  );
}

/** Cancel and Save at the bottom of the section being edited. */
export function SectionActions({ onCancel, onSave, saving }) {
  return (
    <div className="mt-4 flex justify-end gap-3 border-t border-[var(--line)] pt-4">
      <button
        type="button"
        className="rounded-xl border border-[var(--line)] bg-white px-5 py-2 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
        onClick={onCancel}
        disabled={saving}
      >
        Cancel
      </button>
      <button
        type="button"
        className="rounded-xl bg-[var(--blue)] px-5 py-2 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:cursor-not-allowed disabled:opacity-60"
        onClick={onSave}
        disabled={saving}
      >
        {saving ? 'Saving…' : 'Save'}
      </button>
    </div>
  );
}

/** The empty state of a tab whose data comes with a later module. */
export function PendingTab({ label, icon: Icon, text }) {
  return (
    <div className="flex flex-col items-center py-12 text-center">
      <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
        <Icon size={22} />
      </span>
      <div className="font-bold text-[var(--ink)]">No {label.toLowerCase()} yet</div>
      <div className="max-w-[28rem] text-sm text-[var(--muted)]">{text}</div>
    </div>
  );
}

/** One statistic: a round icon tile, the label, the figure (a dash while unknown) and a note below. */
export function StatTile({ icon: Icon, tone, label, value, note }) {
  return (
    <div className="flex min-w-[13rem] flex-1 items-start gap-3.5 rounded-xl border border-[var(--line)] bg-white p-4 xl:min-w-0">
      <span className={`flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full ${tone}`}>
        <Icon size={20} strokeWidth={2} />
      </span>
      <div className="min-w-0">
        <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{label}</div>
        <div
          className={`mt-0.5 text-[1.5rem] font-black leading-7 ${value == null ? 'text-[#A8B6C6]' : 'text-[var(--ink)]'}`}
        >
          {value ?? '—'}
        </div>
        {note && (
          <div className="mt-1 truncate text-xs text-[var(--muted)]" title={note}>
            {note}
          </div>
        )}
      </div>
    </div>
  );
}
