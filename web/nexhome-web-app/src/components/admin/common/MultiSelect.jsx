import { Check, ChevronDown, X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';

/**
 * The console's multiple choice dropdown, as `Select` but several options at once: the box shows the placeholder or how
 * many are chosen, the chosen ones show as chips below it (each with a remove button), and the panel lists every option
 * with a check box. Keyboard: Enter, Space or the arrows open
 * it, the arrows move, Enter or Space toggles, Escape or Tab close. The panel is fixed to the viewport and closes when
 * a container around it scrolls, like `Select`'s.
 *
 * @param value the chosen option values, in the order they were chosen
 * @param options [{ value, label }]
 * @param onChange called with the new list of values
 */
export default function MultiSelect({
  value,
  onChange,
  options,
  ariaLabel,
  id,
  placeholder = 'Select…',
  disabled = false
}) {
  const listId = useId();
  const ref = useRef(null);
  const triggerRef = useRef(null);
  const listRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const [active, setActive] = useState(0);
  const labelOf = (optionValue) => options.find((option) => option.value === optionValue)?.label ?? optionValue;

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false);
    };
    const closeOnScroll = (e) => {
      if (!listRef.current?.contains(e.target)) setOpen(false);
    };
    const close = () => setOpen(false);
    document.addEventListener('mousedown', closeOutside);
    window.addEventListener('scroll', closeOnScroll, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      window.removeEventListener('scroll', closeOnScroll, true);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  useEffect(() => {
    const list = listRef.current;
    const item = list?.children[active];
    if (!open || !item) return;
    if (item.offsetTop < list.scrollTop) list.scrollTop = item.offsetTop;
    else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
    }
  }, [open, active]);

  const openList = () => {
    // Measured on the box itself: the chips below it are part of `ref` too
    const rect = triggerRef.current.getBoundingClientRect();
    const upwards = window.innerHeight - rect.bottom < 300 && rect.top > window.innerHeight - rect.bottom;
    setPosition({
      left: rect.left,
      width: rect.width,
      ...(upwards ? { bottom: window.innerHeight - rect.top + 6 } : { top: rect.bottom + 6 })
    });
    setActive(0);
    setOpen(true);
  };
  const toggle = (optionValue) =>
    onChange(value.includes(optionValue) ? value.filter((v) => v !== optionValue) : [...value, optionValue]);

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
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (options[active]) toggle(options[active].value);
    } else if (e.key === 'Escape' || e.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div ref={ref} className="relative">
      <div
        ref={triggerRef}
        id={id}
        role="combobox"
        tabIndex={disabled ? -1 : 0}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-disabled={disabled}
        onClick={() => !disabled && (open ? setOpen(false) : openList())}
        onKeyDown={disabled ? undefined : onKeyDown}
        className={`relative flex w-full items-center rounded-xl border py-2.5 pl-3.5 pr-10 text-sm outline-none transition ${
          disabled
            ? 'cursor-not-allowed bg-[var(--light)]'
            : `cursor-pointer bg-white ${
                open
                  ? 'border-[var(--blue)] ring-2 ring-[var(--blue)]/15'
                  : 'border-[var(--line)] hover:border-[#C7E3FC] focus-visible:border-[var(--blue)] focus-visible:ring-2 focus-visible:ring-[var(--blue)]/15'
              }`
        } ${disabled ? 'border-[var(--line)]' : ''}`}
      >
        <span className={`truncate ${value.length ? 'font-bold text-[var(--ink)]' : 'text-[#8A9AAD]'}`}>
          {value.length ? `${value.length} selected` : placeholder}
        </span>
        <ChevronDown
          size={16}
          className={`pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--muted)] transition-transform duration-200 ${
            open ? 'rotate-180 text-[var(--blue)]' : ''
          }`}
        />
      </div>

      {value.length > 0 && (
        <div className="mt-2.5 flex flex-wrap gap-1.5">
          {value.map((chosen) => (
            <span
              key={chosen}
              className="flex max-w-full items-center gap-1 rounded-lg bg-[#EDF6FD] py-1 pl-2.5 pr-1.5 text-[0.8125rem] font-bold text-[var(--blue)]"
            >
              <span className="truncate">{labelOf(chosen)}</span>
              {!disabled && (
                <button
                  type="button"
                  aria-label={`Remove ${labelOf(chosen)}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggle(chosen);
                  }}
                  className="flex h-4 w-4 flex-shrink-0 items-center justify-center rounded text-[var(--blue)] hover:bg-[var(--blue)] hover:text-white"
                >
                  <X size={11} strokeWidth={2.8} />
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      {open && position && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-multiselectable="true"
          aria-label={ariaLabel}
          style={{ ...position, minWidth: Math.max(position.width, 176) }}
          className="fixed z-[60] m-0 max-h-72 list-none overflow-y-auto rounded-xl border border-[var(--line)] bg-white p-1.5 shadow-[0_14px_44px_rgba(14,51,89,0.14)]"
        >
          {options.length === 0 && <li className="px-3 py-2 text-sm text-[var(--muted)]">No options yet.</li>}
          {options.map((option, index) => {
            const checked = value.includes(option.value);
            return (
              <li
                key={option.value}
                role="option"
                aria-selected={checked}
                onMouseEnter={() => setActive(index)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => toggle(option.value)}
                className={`flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors ${
                  index === active ? 'bg-[#EDF6FD] text-[var(--blue)]' : 'text-[var(--ink)]'
                } ${checked ? 'font-bold' : ''}`}
              >
                <span
                  className={`flex h-4 w-4 flex-shrink-0 items-center justify-center rounded border ${
                    checked ? 'border-[var(--blue)] bg-[var(--blue)] text-white' : 'border-[#B9C7D6] bg-white'
                  }`}
                >
                  {checked && <Check size={11} strokeWidth={3} />}
                </span>
                <span className="truncate">{option.label}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
