import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
const YEARS_PER_PAGE = 12;

/** The trigger's look, as Select's: `field` for forms, `inline` in a profile row while editing. */
const TRIGGERS = {
  field: 'rounded-xl py-2.5 pl-10 pr-3.5 text-sm',
  inline: 'rounded-lg py-2 pl-10 pr-3 text-[0.9375rem] font-normal'
};

const pad = (n) => String(n).padStart(2, '0');
/** A day as the backend's yyyy-MM-dd. */
const toIso = (year, month, day) => `${year}-${pad(month + 1)}-${pad(day)}`;
const parseIso = (iso) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  return match ? { year: Number(match[1]), month: Number(match[2]) - 1, day: Number(match[3]) } : null;
};
/** A date as the console shows it, e.g. 12 Mar 1994. */
const formatIso = (iso) => {
  const date = parseIso(iso);
  return date ? `${pad(date.day)} ${MONTHS[date.month].slice(0, 3)} ${date.year}` : '';
};
const todayIso = () => {
  const now = new Date();
  return toIso(now.getFullYear(), now.getMonth(), now.getDate());
};

/**
 * The console's date field, in place of the browser's <input type="date">: a button showing the date and a calendar
 * panel. The month title switches to a grid of years and then months, so a date of birth decades back is three
 * clicks away. Days outside `min` / `max` (yyyy-MM-dd) are disabled. Escape or a click outside closes it.
 *
 * @param value yyyy-MM-dd, or '' for none
 * @param onChange called with the picked day as yyyy-MM-dd
 */
export default function DatePicker({
  value,
  onChange,
  min,
  max,
  ariaLabel,
  id,
  placeholder = 'Select a date',
  variant = 'field',
  error = false,
  disabled = false
}) {
  const ref = useRef(null);
  const [open, setOpen] = useState(false);
  const [upwards, setUpwards] = useState(false);
  const [view, setView] = useState('days'); // days, years or months
  const [shown, setShown] = useState({ year: 0, month: 0 });
  const today = todayIso();

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false);
    };
    const closeOnEscape = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  /** Opens on the chosen day's month, else the latest allowed month, else this month. */
  const openPanel = () => {
    const start = parseIso(value) ?? parseIso(max && max < today ? max : today);
    setShown({ year: start.year, month: start.month });
    setView('days');
    const rect = ref.current.getBoundingClientRect();
    setUpwards(window.innerHeight - rect.bottom < 380 && rect.top > window.innerHeight - rect.bottom);
    setOpen(true);
  };

  const outOfRange = (iso) => (min && iso < min) || (max && iso > max);
  const yearOutOfRange = (year) => (min && year < Number(min.slice(0, 4))) || (max && year > Number(max.slice(0, 4)));
  const monthOutOfRange = (year, month) =>
    (min && toIso(year, month, 31) < min) || (max && toIso(year, month, 1) > max);

  const move = (step) => {
    if (view === 'days') {
      const month = shown.month + step;
      setShown({ year: shown.year + Math.floor(month / 12), month: (month + 12) % 12 });
    } else if (view === 'months') {
      setShown({ ...shown, year: shown.year + step });
    } else {
      setShown({ ...shown, year: shown.year + step * YEARS_PER_PAGE });
    }
  };

  const pickDay = (day) => {
    onChange(toIso(shown.year, shown.month, day));
    setOpen(false);
  };

  // The days grid: blanks before the 1st (weeks start on Monday), then the month's days
  const firstWeekday = (new Date(shown.year, shown.month, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(shown.year, shown.month + 1, 0).getDate();
  const firstYear = shown.year - (((shown.year % YEARS_PER_PAGE) + YEARS_PER_PAGE) % YEARS_PER_PAGE);
  const title =
    view === 'days'
      ? `${MONTHS[shown.month]} ${shown.year}`
      : view === 'months'
        ? String(shown.year)
        : `${firstYear} – ${firstYear + YEARS_PER_PAGE - 1}`;

  const cellClass = (selected, disabledCell, current) =>
    `flex items-center justify-center rounded-lg text-sm transition-colors ${
      selected
        ? 'bg-[var(--blue)] font-bold text-white shadow-[0_4px_10px_rgba(11,67,139,0.25)]'
        : disabledCell
          ? 'cursor-not-allowed text-[#C3CEDA]'
          : `text-[var(--ink)] hover:bg-[#EDF6FD] hover:text-[var(--blue)] ${current ? 'font-black text-[var(--blue)] ring-1 ring-inset ring-[#C7E3FC]' : ''}`
    }`;

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        id={id}
        aria-label={ariaLabel}
        aria-haspopup="dialog"
        aria-expanded={open}
        disabled={disabled}
        onClick={() => (open ? setOpen(false) : openPanel())}
        className={`relative w-full border bg-white text-left outline-none transition focus-visible:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--light)] ${
          TRIGGERS[variant]
        } ${
          error
            ? 'border-[#E53E3E] focus-visible:ring-[#E53E3E]/15'
            : open
              ? 'border-[var(--blue)] ring-2 ring-[var(--blue)]/15'
              : 'border-[var(--line)] hover:border-[#C7E3FC] focus-visible:border-[var(--blue)] focus-visible:ring-[var(--blue)]/15'
        } ${value ? 'text-[var(--ink)]' : 'text-[#8A9AAD]'}`}
      >
        <CalendarDays
          size={16}
          className={`pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 ${open ? 'text-[var(--blue)]' : 'text-[var(--muted)]'}`}
        />
        {formatIso(value) || placeholder}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label={ariaLabel ? `Choose ${ariaLabel.toLowerCase()}` : 'Choose a date'}
          className={`absolute left-0 z-40 w-[18.5rem] rounded-2xl border border-[var(--line)] bg-white p-4 shadow-[0_14px_44px_rgba(14,51,89,0.14)] ${
            upwards ? 'bottom-[calc(100%+0.375rem)]' : 'top-[calc(100%+0.375rem)]'
          }`}
        >
          <div className="mb-3 flex items-center justify-between gap-2">
            <button
              type="button"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
              onClick={() => move(-1)}
              aria-label="Previous"
            >
              <ChevronLeft size={17} />
            </button>
            <button
              type="button"
              className="rounded-lg px-3 py-1.5 text-sm font-black text-[var(--navy)] hover:bg-[#EDF6FD] hover:text-[var(--blue)]"
              onClick={() => setView(view === 'days' ? 'years' : view === 'months' ? 'years' : 'days')}
              title={view === 'days' ? 'Choose the year and month' : 'Back to the days'}
            >
              {title}
            </button>
            <button
              type="button"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
              onClick={() => move(1)}
              aria-label="Next"
            >
              <ChevronRight size={17} />
            </button>
          </div>

          {view === 'days' && (
            <>
              <div className="mb-1 grid grid-cols-7 gap-1">
                {WEEKDAYS.map((weekday) => (
                  <div key={weekday} className="py-1 text-center text-[0.6875rem] font-bold uppercase tracking-wide text-[var(--muted)]">
                    {weekday}
                  </div>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-1">
                {Array.from({ length: firstWeekday }, (_, i) => (
                  <span key={`blank-${i}`} />
                ))}
                {Array.from({ length: daysInMonth }, (_, i) => {
                  const day = i + 1;
                  const iso = toIso(shown.year, shown.month, day);
                  const unavailable = outOfRange(iso);
                  return (
                    <button
                      key={day}
                      type="button"
                      disabled={unavailable}
                      onClick={() => pickDay(day)}
                      aria-label={formatIso(iso)}
                      aria-pressed={iso === value}
                      className={`h-9 ${cellClass(iso === value, unavailable, iso === today)}`}
                    >
                      {day}
                    </button>
                  );
                })}
              </div>
            </>
          )}

          {view === 'years' && (
            <div className="grid grid-cols-3 gap-2">
              {Array.from({ length: YEARS_PER_PAGE }, (_, i) => {
                const year = firstYear + i;
                const unavailable = yearOutOfRange(year);
                return (
                  <button
                    key={year}
                    type="button"
                    disabled={unavailable}
                    onClick={() => {
                      setShown({ ...shown, year });
                      setView('months');
                    }}
                    className={`h-11 ${cellClass(year === parseIso(value)?.year, unavailable, String(year) === today.slice(0, 4))}`}
                  >
                    {year}
                  </button>
                );
              })}
            </div>
          )}

          {view === 'months' && (
            <div className="grid grid-cols-3 gap-2">
              {MONTHS.map((name, month) => {
                const unavailable = monthOutOfRange(shown.year, month);
                const chosen = parseIso(value);
                return (
                  <button
                    key={name}
                    type="button"
                    disabled={unavailable}
                    onClick={() => {
                      setShown({ ...shown, month });
                      setView('days');
                    }}
                    className={`h-11 ${cellClass(chosen?.year === shown.year && chosen?.month === month, unavailable, false)}`}
                  >
                    {name.slice(0, 3)}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
