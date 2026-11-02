import { BadgeCheck, ExternalLink, FileWarning, ShieldCheck, ShieldX } from 'lucide-react';
import { useEffect, useState } from 'react';

import { fileUrl } from '../../../api/files';
import { useAdminAuth } from '../../../context/adminAuth';
import Modal from './Modal';

/**
 * Shows a stored file in a dialog: an image whole (scaled to fit), a PDF in the browser's PDF viewer. The file opens
 * through its short-lived link (GET /api/files/download-url/{fileId}; opening a partner's document is audited), with
 * Open in new tab for a larger view. `file` is { fileId, fileName }; `onClose` should be stable (useCallback).
 * `verification` (a partner document: { status, rejectionReason, canVerify, onDecide(status, reason) }) puts its
 * state in the middle of the footer: a green Verified or red Rejected tag once decided, else, with `canVerify`,
 * Verify and Reject (which asks for the reason first); `onDecide` returns a promise whose error is shown here.
 */
export default function FileViewerModal({ file, onClose, verification = null }) {
  const { authRequest } = useAdminAuth();
  const [link, setLink] = useState({ loading: true, data: null, error: '' });
  const [deciding, setDeciding] = useState(false);
  const [decideError, setDecideError] = useState('');
  const [decided, setDecided] = useState(null); // { status, reason } once decided here
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const status = decided?.status ?? verification?.status;
  const rejectionReason = decided?.reason ?? verification?.rejectionReason;

  const decide = async (target) => {
    const why = reason.trim();
    if (target === 'REJECTED' && !why) {
      setDecideError('Enter the reason for rejecting the document.');
      return;
    }
    setDecideError('');
    setDeciding(true);
    try {
      await verification.onDecide(target, target === 'REJECTED' ? why : null);
      setDecided({ status: target, reason: target === 'REJECTED' ? why : null });
      setRejecting(false);
    } catch (err) {
      setDecideError(err.message);
    } finally {
      setDeciding(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/files/download-url/${file.fileId}`)
      .then((data) => !cancelled && setLink({ loading: false, data, error: '' }))
      .catch((err) => !cancelled && setLink({ loading: false, data: null, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, file.fileId]);

  const url = link.data ? fileUrl(link.data.url) : null;
  const type = link.data?.contentType ?? '';
  const name = link.data?.fileName ?? file.fileName ?? 'Document';
  return (
    <Modal title={name} onClose={onClose} width="max-w-[60rem]" fill>
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="flex min-h-0 flex-1 items-center justify-center overflow-hidden rounded-xl border border-[var(--line)] bg-[var(--light)]">
          {link.loading && <div className="h-10 w-10 animate-spin rounded-full border-4 border-[#CFE2F6] border-t-[var(--blue)]" />}
          {link.error && (
            <div role="alert" className="flex flex-col items-center gap-2 p-6 text-center text-sm font-bold text-[#C53030]">
              <FileWarning size={28} />
              {link.error}
            </div>
          )}
          {url && type.startsWith('image/') && (
            <img src={url} alt={name} className="max-h-full max-w-full object-contain" />
          )}
          {url && type === 'application/pdf' && <iframe src={url} title={name} className="h-full w-full bg-white" />}
          {url && !type.startsWith('image/') && type !== 'application/pdf' && (
            <p className="m-0 p-6 text-sm text-[var(--muted)]">This file cannot be shown here; open it in a new tab.</p>
          )}
        </div>
        {rejecting && (
          <div className="mt-3">
            <label htmlFor="reject-reason" className="mb-1.5 block text-sm font-bold text-[var(--ink)]">
              Reason for rejecting
            </label>
            <textarea
              id="reject-reason"
              rows={2}
              maxLength={2000}
              autoFocus
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. The photo is blurred; upload a clear scan."
              className="w-full resize-y rounded-xl border border-[var(--line)] px-3.5 py-2.5 text-sm outline-none focus:border-[var(--blue)] focus:ring-2 focus:ring-[var(--blue)]/15"
            />
          </div>
        )}
        {decideError && (
          <div role="alert" className="mt-3 rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-2.5 text-sm font-bold text-[#C53030]">
            {decideError}
          </div>
        )}
        {/* Footer: the file's kind, the verification in the middle, Open in new tab */}
        <div className="mt-4 grid grid-cols-[1fr_auto_1fr] items-center gap-3 text-[0.8125rem] text-[var(--muted)]">
          <span className="truncate">{type === 'application/pdf' ? 'PDF document' : type.startsWith('image/') ? 'Image' : ''}</span>
          <span className="flex justify-center">
            {verification && status === 'VERIFIED' && (
              <span className="flex items-center gap-1.5 rounded-full bg-[#E9F6EF] px-3.5 py-1.5 text-sm font-bold text-[var(--green-2)]">
                <BadgeCheck size={16} />
                Verified
              </span>
            )}
            {verification && status === 'REJECTED' && (
              <span
                title={rejectionReason ?? undefined}
                className="flex items-center gap-1.5 rounded-full bg-[#FDECEC] px-3.5 py-1.5 text-sm font-bold text-[#C53030]"
              >
                <ShieldX size={16} />
                Rejected
              </span>
            )}
            {verification && status !== 'VERIFIED' && status !== 'REJECTED' && verification.canVerify && (
              <span className="flex items-center gap-2">
                {rejecting ? (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setRejecting(false);
                        setDecideError('');
                      }}
                      disabled={deciding}
                      className="rounded-xl border border-[var(--line)] bg-white px-4 py-2 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={() => decide('REJECTED')}
                      disabled={deciding}
                      className="flex items-center gap-1.5 rounded-xl bg-[#E53E3E] px-5 py-2 text-sm font-bold text-white hover:bg-[#C53030] disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <ShieldX size={16} />
                      {deciding ? 'Rejecting…' : 'Confirm Reject'}
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setDecideError('');
                        setRejecting(true);
                      }}
                      disabled={deciding || !url}
                      className="flex items-center gap-1.5 rounded-xl border border-[#F5C2C2] bg-white px-5 py-2 text-sm font-bold text-[#C53030] hover:bg-[#FDECEC] disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <ShieldX size={16} />
                      Reject
                    </button>
                    <button
                      type="button"
                      onClick={() => decide('VERIFIED')}
                      disabled={deciding || !url}
                      className="flex items-center gap-1.5 rounded-xl bg-[var(--green)] px-5 py-2 text-sm font-bold text-white hover:bg-[var(--green-2)] disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <ShieldCheck size={16} />
                      {deciding ? 'Verifying…' : 'Verify'}
                    </button>
                  </>
                )}
              </span>
            )}
          </span>
          <span className="flex justify-end">
          {url && (
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex flex-shrink-0 items-center gap-1.5 rounded-lg border border-[var(--line)] px-3 py-1.5 text-sm font-bold text-[var(--blue)] no-underline hover:bg-[#EDF6FD]"
            >
              <ExternalLink size={14} />
              Open in new tab
            </a>
          )}
          </span>
        </div>
      </div>
    </Modal>
  );
}
