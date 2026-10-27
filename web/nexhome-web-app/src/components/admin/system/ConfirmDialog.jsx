import { TriangleAlert } from 'lucide-react';
import { useState } from 'react';

import Modal from '../common/Modal';

/**
 * Asks before a destructive action. `onConfirm` returns a promise; while it runs the buttons are off, and if it fails
 * its message is shown in the dialog (e.g. the backend's 409 "... deactivate it instead.").
 */
export default function ConfirmDialog({ title, message, confirmLabel = 'Delete', onConfirm, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const confirm = async () => {
    setBusy(true);
    setError('');
    try {
      await onConfirm();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title={title} onClose={busy ? () => {} : onClose} width="max-w-[28rem]">
      <div className="flex gap-4">
        <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-[#FDECEC] text-[#E53E3E]">
          <TriangleAlert size={20} />
        </span>
        <div className="pt-1 text-sm text-[var(--ink)]">{message}</div>
      </div>
      {error && (
        <div role="alert" className="mt-4 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {error}
        </div>
      )}
      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          className="rounded-xl border border-[var(--line)] bg-white px-6 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:cursor-not-allowed disabled:opacity-60"
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="button"
          className="rounded-xl bg-[#E53E3E] px-6 py-2.5 text-sm font-bold text-white hover:bg-[#C53030] disabled:cursor-not-allowed disabled:opacity-60"
          onClick={confirm}
          disabled={busy}
        >
          {busy ? 'Please wait…' : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
