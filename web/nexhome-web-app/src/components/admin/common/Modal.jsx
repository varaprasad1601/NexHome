import { X } from 'lucide-react';
import { useEffect } from 'react';

/** A centred dialog over a dimmed page; Escape or the backdrop closes it, and the page behind does not scroll. */
export default function Modal({ title, onClose, children, width = 'max-w-[35rem]' }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto p-4">
      <button type="button" aria-label="Close" className="fixed inset-0 bg-[var(--navy)]/40" onClick={onClose} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className={`relative w-full ${width} rounded-2xl bg-white p-6 shadow-[var(--shadow)]`}
      >
        <div className="mb-5 flex items-center justify-between gap-4">
          <h2 id="modal-title" className="mb-0 text-xl font-black text-[var(--navy)]">
            {title}
          </h2>
          <button
            type="button"
            className="rounded-lg p-1.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
            onClick={onClose}
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
