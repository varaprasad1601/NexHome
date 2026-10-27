import { CircleAlert, CircleCheck, X } from 'lucide-react';
import { useEffect } from 'react';

/**
 * A floating message in the top-right corner, below the top bar; it closes itself after a few seconds.
 *
 * @param toast { message, tone: 'success' | 'error', id } or null; a new id restarts the timer
 */
export default function Toast({ toast, onClose, durationMs = 4000 }) {
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(onClose, durationMs);
    return () => clearTimeout(timer);
  }, [toast, onClose, durationMs]);

  if (!toast) return null;
  const error = toast.tone === 'error';
  const Icon = error ? CircleAlert : CircleCheck;

  return (
    <div
      key={toast.id}
      role={error ? 'alert' : 'status'}
      className={`fixed right-6 top-20 z-50 flex w-[min(24rem,calc(100vw-3rem))] items-start gap-3 rounded-xl border bg-white px-4 py-3 shadow-[var(--shadow)] ${
        error ? 'border-[#F5C2C2]' : 'border-[#BFE5CF]'
      }`}
    >
      <Icon size={18} className={`mt-0.5 flex-shrink-0 ${error ? 'text-[#C53030]' : 'text-[var(--green)]'}`} />
      <div className="flex-1 text-sm font-bold text-[var(--ink)]">{toast.message}</div>
      <button
        type="button"
        className="rounded-md p-0.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
        onClick={onClose}
        aria-label="Dismiss"
      >
        <X size={15} />
      </button>
    </div>
  );
}
