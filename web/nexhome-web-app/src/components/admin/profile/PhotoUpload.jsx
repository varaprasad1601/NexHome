import { Camera, ImageUp, Info } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';

import Modal from '../common/Modal';

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

function formatSize(bytes) {
  return bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * The photo upload of a profile picture: laid over the round photo (its parent must be `relative`), it darkens the
 * photo on hover or focus and shows a camera (no text; "Upload photo" / "Change photo" is its label for screen
 * readers and the dialog title). Clicking picks a JPEG, PNG or
 * WebP image of up to 2 MB and previews it round, as it would show.
 *
 * Saving is not possible yet: the backend has no file storage (a photo is only a profilePhotoUrl), so the preview's
 * Save stays disabled with a note. Hook `onSave(file)` up once storage exists.
 *
 * @param inset the overlay's inset within the parent, to sit exactly on the photo (e.g. inside a progress ring)
 */
export default function PhotoUpload({ hasPhoto, name, inset = '0' }) {
  const inputRef = useRef(null);
  const [picked, setPicked] = useState(null); // { file, url }
  const [error, setError] = useState('');

  const close = useCallback(() => {
    setPicked((current) => {
      if (current) URL.revokeObjectURL(current.url);
      return null;
    });
    setError('');
  }, []);

  // The preview's object URL is released when the page goes away too
  useEffect(() => () => picked && URL.revokeObjectURL(picked.url), [picked]);

  const onFile = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setError('Choose a JPEG, PNG or WebP image.');
      setPicked(null);
      return;
    }
    if (file.size > MAX_BYTES) {
      setError(`The image is ${formatSize(file.size)}; choose one of up to 2 MB.`);
      setPicked(null);
      return;
    }
    setError('');
    setPicked({ file, url: URL.createObjectURL(file) });
  };

  const label = hasPhoto ? 'Change photo' : 'Upload photo';

  return (
    <>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        aria-label={`${label} for ${name || 'this profile'}`}
        style={{ inset }}
        className="group/photo absolute z-[1] flex items-center justify-center rounded-full bg-[rgba(11,49,95,0)] text-white opacity-0 outline-none transition duration-200 hover:bg-[rgba(11,49,95,0.62)] hover:opacity-100 focus-visible:bg-[rgba(11,49,95,0.62)] focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-[var(--blue)]/40"
      >
        <Camera size={24} strokeWidth={2} className="scale-90 transition-transform duration-200 group-hover/photo:scale-100" />
      </button>
      <input ref={inputRef} type="file" accept={ACCEPTED_TYPES.join(',')} className="hidden" onChange={onFile} />

      {(picked || error) && (
        <Modal title={label} onClose={close} width="max-w-[26rem]">
          {error ? (
            <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
              {error}
            </div>
          ) : (
            <div className="flex flex-col items-center text-center">
              <img
                src={picked.url}
                alt="Preview of the new profile photo"
                className="h-40 w-40 rounded-full object-cover shadow-[0_10px_30px_rgba(14,51,89,0.18)] ring-4 ring-white outline outline-1 outline-[var(--line)]"
              />
              <div className="mt-4 max-w-full truncate text-sm font-bold text-[var(--ink)]" title={picked.file.name}>
                {picked.file.name}
              </div>
              <div className="text-xs text-[var(--muted)]">{formatSize(picked.file.size)} · shown round, as on the profile</div>
              <div className="mt-5 flex w-full items-start gap-2.5 rounded-xl border border-[#C7E3FC] bg-[#EDF6FD] px-4 py-3 text-left text-[0.8125rem] text-[var(--navy)]">
                <Info size={16} className="mt-0.5 flex-shrink-0 text-[var(--blue)]" />
                Saving photos comes once file storage is set up. The preview shows how it will look.
              </div>
            </div>
          )}
          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              className="rounded-xl border border-[var(--line)] bg-white px-5 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)]"
              onClick={error ? () => inputRef.current?.click() : close}
            >
              {error ? 'Choose another' : 'Cancel'}
            </button>
            <button
              type="button"
              disabled
              title="Available once file storage is set up"
              className="flex items-center gap-2 rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              <ImageUp size={16} />
              Save photo
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
