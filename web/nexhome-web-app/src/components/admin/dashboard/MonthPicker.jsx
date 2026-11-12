import { Calendar, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { monthLabel, monthRangeLabel } from '../../../hooks/useStats';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * The dashboard's month picker: the button shows the chosen month's dates; its panel has a year with its twelve
 * months (only `min` to `max`, the months the statistics cover, can be chosen), arrows for the year and This month.
 * Escape or a click outside closes it.
 */
export default function MonthPicker({ value, min, max, onChange }) {
  const [open, setOpen] = useState(false);
  const [year, setYear] = useState(() => Number(value.slice(0, 4)));
  const boxRef = useRef(null);
  const minYear = Number(min.slice(0, 4));
  const maxYear = Number(max.slice(0, 4));

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (event) => {
      if (!boxRef.current?.contains(event.target)) setOpen(false);
    };
    const onKey = (event) => event.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const choose = (month) => {
    onChange(month);
    setOpen(false);
  };

  return (
    <div ref={boxRef} className="relative">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => {
          setYear(Number(value.slice(0, 4)));
          setOpen((current) => !current);
        }}
        className="flex items-center gap-3 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm font-bold text-[var(--ink)] shadow-[0_1px_2px_rgba(14,51,89,0.04)] hover:border-[#C7E3FC]"
      >
        <Calendar size={15} className="text-[var(--blue)]" />
        {monthRangeLabel(value)}
        <ChevronDown size={14} className={`text-[var(--muted)] transition ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Choose a month"
          className="absolute right-0 top-full z-30 mt-2 w-[17rem] rounded-2xl border border-[var(--line)] bg-white p-3 shadow-[0_18px_40px_-12px_rgba(11,49,95,0.28)]"
        >
          <div className="mb-2 flex items-center justify-between">
            <button
              type="button"
              aria-label="Previous year"
              disabled={year <= minYear}
              onClick={() => setYear((current) => current - 1)}
              className="rounded-lg p-1.5 text-[var(--ink)] hover:bg-[var(--light)] disabled:opacity-30"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="text-sm font-black text-[var(--navy)]">{year}</span>
            <button
              type="button"
              aria-label="Next year"
              disabled={year >= maxYear}
              onClick={() => setYear((current) => current + 1)}
              className="rounded-lg p-1.5 text-[var(--ink)] hover:bg-[var(--light)] disabled:opacity-30"
            >
              <ChevronRight size={16} />
            </button>
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            {MONTHS.map((name, index) => {
              const month = `${year}-${String(index + 1).padStart(2, '0')}`;
              const disabled = month < min || month > max;
              const selected = month === value;
              return (
                <button
                  key={month}
                  type="button"
                  disabled={disabled}
                  onClick={() => choose(month)}
                  className={`rounded-lg py-2 text-[0.8125rem] font-bold transition ${
                    selected
                      ? 'bg-[var(--blue)] text-white'
                      : disabled
                        ? 'cursor-not-allowed text-[#B8C4D1]'
                        : 'text-[var(--ink)] hover:bg-[#EDF6FD]'
                  }`}
                >
                  {name}
                </button>
              );
            })}
          </div>
          <div className="mt-3 flex items-center justify-between border-t border-[var(--line)] pt-2.5">
            <span className="text-[0.75rem] text-[var(--muted)]">
              {monthLabel(min)} to {monthLabel(max)}
            </span>
            <button
              type="button"
              onClick={() => choose(max)}
              className="rounded-lg px-2.5 py-1 text-[0.8125rem] font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
            >
              This month
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
