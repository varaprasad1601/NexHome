import { ImageUp, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';

const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

/**
 * An image upload field: a dashed box with the picked image's preview (or the current image while none is picked),
 * Choose image / Change and Remove. It takes PNG, JPG or WebP up to 2 MB and hands the File to `onChange`.
 *
 * Nothing is uploaded yet: there is no cloud storage, so forms keep the File out of their requests (the record keeps
 * its current image). Connect it to the upload endpoint once storage exists.
 */
export default function ImageUploadField({ id, value, onChange, currentUrl, error, disabled = false }) {
  const inputRef = useRef(null);
  const [typeError, setTypeError] = useState('');
  const pickedUrl = useMemo(() => (value instanceof File ? URL.createObjectURL(value) : null), [value]);
  useEffect(() => () => pickedUrl && URL.revokeObjectURL(pickedUrl), [pickedUrl]);
  const preview = pickedUrl ?? currentUrl ?? null;

  const pick = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setTypeError('Choose a PNG, JPG or WebP image.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setTypeError('Choose an image of up to 2 MB.');
      return;
    }
    setTypeError('');
    onChange(file);
  };

  return (
    <div>
      <div
        className={`flex items-center gap-4 rounded-xl border border-dashed p-3 ${
          error || typeError ? 'border-[#E53E3E] bg-[#FFF8F8]' : 'border-[#C7D6E6] bg-[#FAFCFE]'
        }`}
      >
        <span className="flex h-16 w-16 flex-shrink-0 items-center justify-center overflow-hidden rounded-xl border border-[var(--line)] bg-white">
          {preview ? (
            <img src={preview} alt="" className="h-full w-full object-contain p-1" />
          ) : (
            <ImageUp size={22} className="text-[#A8B6C6]" />
          )}
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-bold text-[var(--ink)]">
            {value instanceof File ? value.name : currentUrl ? 'Current image' : 'No image yet'}
          </div>
          <div className="text-xs text-[var(--muted)]">PNG, JPG or WebP, up to 2 MB</div>
        </div>
        <div className="flex flex-shrink-0 items-center gap-1.5">
          {value instanceof File && (
            <button
              type="button"
              onClick={() => onChange(null)}
              aria-label="Remove the chosen image"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--muted)] hover:bg-[#FDECEC] hover:text-[#C53030]"
            >
              <X size={15} />
            </button>
          )}
          <button
            type="button"
            id={id}
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
            className="whitespace-nowrap rounded-lg border border-[var(--line)] bg-white px-3 py-1.5 text-xs font-bold text-[var(--blue)] hover:bg-[#EDF6FD] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {preview ? 'Change' : 'Choose image'}
          </button>
        </div>
        <input ref={inputRef} type="file" accept={ACCEPTED_TYPES.join(',')} className="hidden" onChange={pick} />
      </div>
      {typeError && <div className="mt-1 text-xs font-bold text-[#C53030]">{typeError}</div>}
    </div>
  );
}
