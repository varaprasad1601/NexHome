import { Ellipsis } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

/**
 * A row's "..." menu of `actions` ({ label, icon, onClick, danger }). Like the staff table's menu it is positioned
 * against the viewport (fixed), so the table's scroll container cannot clip it, opens upwards when there is no room
 * below, and closes on scroll or resize. `variant="button"` shows a bordered "Actions" button, `variant="plain"` the icon without a hover background,
 * instead of the bare icon
 * (detail pages' headers); `buttonText={null}` leaves the word out.
 */
export default function RowActions({ label, actions, disabled = false, variant = 'icon', buttonText = 'Actions' }) {
  const [position, setPosition] = useState(null);
  const ref = useRef(null);
  const open = position !== null;

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (e) => {
      if (!ref.current?.contains(e.target)) setPosition(null);
    };
    const close = () => setPosition(null);
    document.addEventListener('mousedown', closeOutside);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  const toggle = (e) => {
    if (open) {
      setPosition(null);
      return;
    }
    const rect = e.currentTarget.getBoundingClientRect();
    const upwards = window.innerHeight - rect.bottom < 200;
    setPosition({
      right: window.innerWidth - rect.right,
      ...(upwards ? { bottom: window.innerHeight - rect.top + 4 } : { top: rect.bottom + 4 })
    });
  };

  return (
    <div ref={ref}>
      <button
        type="button"
        className={
          variant === 'button'
            ? 'flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:cursor-not-allowed disabled:opacity-40'
            : variant === 'plain'
              ? 'p-1 text-[var(--muted)] hover:text-[var(--ink)] disabled:cursor-not-allowed disabled:opacity-40'
              : 'rounded-md p-1.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)] disabled:cursor-not-allowed disabled:opacity-40'
        }
        aria-label={`Actions for ${label}`}
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        onClick={toggle}
      >
        {variant === 'button' && buttonText}
        <Ellipsis size={18} />
      </button>
      {open && (
        <div
          role="menu"
          style={position}
          className="fixed z-30 w-44 rounded-xl border border-[var(--line)] bg-white p-1.5 text-left shadow-[var(--shadow)]"
        >
          {actions.map((action) => {
            const Icon = action.icon;
            return (
              <button
                key={action.label}
                type="button"
                role="menuitem"
                className={`flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-bold ${
                  action.danger ? 'text-[#C53030] hover:bg-[#FDECEC]' : 'text-[var(--ink)] hover:bg-[var(--light)]'
                }`}
                onClick={() => {
                  setPosition(null);
                  action.onClick();
                }}
              >
                {Icon && <Icon size={15} />}
                {action.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
