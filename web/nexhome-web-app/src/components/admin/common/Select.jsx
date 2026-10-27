import { Check, ChevronDown } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';

/** The trigger's look: `field` for filters and dialogs (as the inputs there), `inline` in a profile row while editing. */
const TRIGGERS = {
  field: 'rounded-xl py-2.5 pl-3.5 pr-10 text-sm',
  inline: 'rounded-lg py-2 pl-3 pr-10 text-[0.9375rem] font-normal'
};

/**
 * The console's dropdown, in place of the browser's <select>: a button showing the chosen option, and a panel of
 * options with the chosen one ticked. Keyboard: Enter, Space or the arrows open it, the arrows move, Enter picks,
 * Escape or Tab close. It opens upwards when there is no room below.
 *
 * @param options [{ value, label }]; `value` is a string ('' for "all" / "not set" choices)
 * @param onChange called with the picked option's value
 */
export default function Select({
  value,
  onChange,
  options,
  ariaLabel,
  id,
  placeholder = 'Select…',
  variant = 'field',
  error = false,
  disabled = false,
  className = ''
}) {
  const listId = useId();
  const ref = useRef(null);
  const listRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [upwards, setUpwards] = useState(false);
  const [active, setActive] = useState(0);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = options[selectedIndex];

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', closeOutside);
    return () => document.removeEventListener('mousedown', closeOutside);
  }, [open]);

  // Keep the highlighted option in view while moving through a long list
  useEffect(() => {
    if (open) listRef.current?.children[active]?.scrollIntoView({ block: 'nearest' });
  }, [open, active]);

  const openList = () => {
    const rect = ref.current.getBoundingClientRect();
    setUpwards(window.innerHeight - rect.bottom < 280 && rect.top > window.innerHeight - rect.bottom);
    setActive(Math.max(selectedIndex, 0));
    setOpen(true);
  };
  const pick = (option) => {
    setOpen(false);
    if (option.value !== value) onChange(option.value);
  };

  const onKeyDown = (e) => {
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
        e.preventDefault();
        openList();
      }
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((index) => Math.min(index + 1, options.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault();
      setActive(e.key === 'Home' ? 0 : options.length - 1);
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (options[active]) pick(options[active]);
    } else if (e.key === 'Escape' || e.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div ref={ref} className={`relative ${className}`}>
      <button
        type="button"
        id={id}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        disabled={disabled}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={onKeyDown}
        className={`relative w-full truncate border bg-white text-left outline-none transition focus-visible:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--light)] disabled:font-bold disabled:text-[var(--muted)] ${
          TRIGGERS[variant]
        } ${
          error
            ? 'border-[#E53E3E] focus-visible:ring-[#E53E3E]/15'
            : open
              ? 'border-[var(--blue)] ring-2 ring-[var(--blue)]/15'
              : 'border-[var(--line)] hover:border-[#C7E3FC] focus-visible:border-[var(--blue)] focus-visible:ring-[var(--blue)]/15'
        } ${selected ? 'text-[var(--ink)]' : 'text-[#8A9AAD]'}`}
      >
        {selected ? selected.label : placeholder}
        <ChevronDown
          size={16}
          className={`pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--muted)] transition-transform duration-200 ${
            open ? 'rotate-180 text-[var(--blue)]' : ''
          }`}
        />
      </button>

      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-label={ariaLabel}
          className={`absolute left-0 z-40 m-0 max-h-64 w-full min-w-[11rem] list-none overflow-y-auto rounded-xl border border-[var(--line)] bg-white p-1.5 shadow-[0_14px_44px_rgba(14,51,89,0.14)] ${
            upwards ? 'bottom-[calc(100%+0.375rem)]' : 'top-[calc(100%+0.375rem)]'
          }`}
        >
          {options.map((option, index) => {
            const isSelected = option.value === value;
            return (
              <li
                key={option.value}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setActive(index)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(option)}
                className={`flex cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                  index === active ? 'bg-[#EDF6FD] text-[var(--blue)]' : 'text-[var(--ink)]'
                } ${isSelected ? 'font-bold' : ''}`}
              >
                <span className="truncate">{option.label}</span>
                {isSelected && <Check size={15} strokeWidth={2.6} className="flex-shrink-0 text-[var(--blue)]" />}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
