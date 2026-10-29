import { Fragment, useState } from 'react';

import Modal from '../common/Modal';
import LunchBreak from './LunchBreak';
import SlotHeading from './SlotHeading';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/**
 * Edits a partner's weekly availability: a day by slot grid (AVAILABILITY_SLOT) whose cells switch on and off.
 * `onSave(weeklyAvailability)` gets { DAY: [slot codes] } with only the days that have a slot, and returns a promise.
 */
export default function WeeklyAvailabilityModal({ slots, value, onSave, onClose }) {
  const [grid, setGrid] = useState(() => Object.fromEntries(DAYS.map((day) => [day, new Set(value?.[day] ?? [])])));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const toggle = (day, slot) =>
    setGrid((current) => {
      const next = new Set(current[day]);
      if (next.has(slot)) next.delete(slot);
      else next.add(slot);
      return { ...current, [day]: next };
    });
  const setColumn = (slot, on) =>
    setGrid((current) =>
      Object.fromEntries(
        DAYS.map((day) => {
          const next = new Set(current[day]);
          if (on) next.add(slot);
          else next.delete(slot);
          return [day, next];
        })
      )
    );
  const save = async () => {
    setSaving(true);
    setError('');
    try {
      await onSave(
        Object.fromEntries(
          DAYS.filter((day) => grid[day].size > 0).map((day) => [
            day,
            slots.map((slot) => slot.valueCode).filter((code) => grid[day].has(code))
          ])
        )
      );
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title="Edit Weekly Availability" onClose={onClose} width="max-w-[48rem]">
      {error && (
        <div role="alert" className="mb-4 rounded-xl bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {error}
        </div>
      )}
      <p className="mb-3 text-sm text-[var(--muted)]">
        Click a slot to switch it on or off, or use All days to set a whole column.
      </p>
      <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
        <table className="w-full min-w-[34rem] text-sm">
          <thead className="bg-[var(--light)]">
            <tr>
              <th className="px-3 py-2.5 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
                Day
              </th>
              {slots.map((slot) => (
                <Fragment key={slot.valueCode}>
                  <th className="px-2 py-2.5 text-center align-top text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
                    <SlotHeading slot={slot} />
                    {/* Select all: the slot on every day, or off again when it already is */}
                    <label className="mt-2 inline-flex cursor-pointer items-center gap-1.5 text-[0.6875rem] font-bold normal-case tracking-normal text-[var(--blue)]">
                      <input
                        type="checkbox"
                        className="h-3.5 w-3.5 accent-[var(--blue)]"
                        checked={DAYS.every((day) => grid[day].has(slot.valueCode))}
                        onChange={(e) => setColumn(slot.valueCode, e.target.checked)}
                        aria-label={`${slot.displayName} on every day`}
                      />
                      All days
                    </label>
                  </th>
                  <LunchBreak after={slot} header />
                </Fragment>
              ))}
            </tr>
          </thead>
          <tbody>
            {DAYS.map((day) => (
              <tr key={day} className="border-t border-[var(--line)]">
                <td className="px-3 py-2 font-bold text-[var(--ink)]">{day[0] + day.slice(1).toLowerCase()}</td>
                {slots.map((slot) => {
                  const on = grid[day].has(slot.valueCode);
                  return (
                    <Fragment key={slot.valueCode}>
                      <td className="px-2 py-1.5 text-center">
                        <button
                          type="button"
                          aria-pressed={on}
                          aria-label={`${day} ${slot.displayName}`}
                          onClick={() => toggle(day, slot.valueCode)}
                          className={`inline-flex h-8 w-full max-w-[4.5rem] items-center justify-center rounded-lg text-xs font-bold transition ${
                            on
                              ? 'bg-[#E9F6EF] text-[var(--green-2)] ring-1 ring-[var(--green)]/40'
                              : 'bg-[var(--light)] text-[#A8B6C6] hover:bg-[#EDF6FD] hover:text-[var(--blue)]'
                          }`}
                        >
                          {on ? 'Available' : 'Off'}
                        </button>
                      </td>
                      <LunchBreak after={slot} />
                    </Fragment>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-5 flex justify-end gap-3">
        <button
          type="button"
          onClick={onClose}
          className="rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={save}
          disabled={saving}
          className="rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)] disabled:opacity-60"
        >
          {saving ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </Modal>
  );
}
