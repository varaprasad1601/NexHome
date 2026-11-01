import { FileText, Paperclip, X } from 'lucide-react';
import { useRef, useState } from 'react';

import { FILE_RULES, fileProblem, formatFileSize } from '../../../api/files';

/**
 * A file field (FormModal's `file` type): a dashed box with the picked file's name and size, or the current file's
 * name while none is picked, and Choose file / Change / Remove. The File goes to `onChange`; the form uploads it
 * when it saves (api/files uploadFile). Type and size are checked for `purpose` (FILE_RULES), as the backend does.
 */
export default function FileUploadField({ id, value, onChange, purpose, currentName, error, disabled = false }) {
  const inputRef = useRef(null);
  const [problem, setProblem] = useState('');
  const rule = FILE_RULES[purpose];

  const pick = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const found = fileProblem(file, purpose);
    setProblem(found);
    if (!found) onChange(file);
  };

  const picked = value instanceof File ? value : null;
  return (
    <div>
      <div
        className={`flex items-center gap-3 rounded-xl border border-dashed p-3 ${
          error || problem ? 'border-[#E53E3E]' : 'border-[var(--line)]'
        } ${disabled ? 'opacity-60' : ''}`}
      >
        <span className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-[#EDF6FD] text-[var(--blue)]">
          <FileText size={18} />
        </span>
        <div className="min-w-0 flex-1 text-sm">
          {picked ? (
            <>
              <div className="truncate font-bold text-[var(--ink)]" title={picked.name}>
                {picked.name}
              </div>
              <div className="text-xs text-[var(--muted)]">{formatFileSize(picked.size)} · uploaded when you save</div>
            </>
          ) : currentName ? (
            <>
              <div className="truncate font-bold text-[var(--ink)]" title={currentName}>
                {currentName}
              </div>
              <div className="text-xs text-[var(--muted)]">Current file; choose another to replace it</div>
            </>
          ) : (
            <div className="text-[var(--muted)]">
              No file chosen · {rule?.accept.replaceAll('.', '').toUpperCase().replaceAll(',', ', ')} up to {rule?.maxMb} MB
            </div>
          )}
        </div>
        {!disabled && (
          <>
            <button
              type="button"
              id={id}
              onClick={() => inputRef.current?.click()}
              className="flex items-center gap-1.5 rounded-lg border border-[var(--line)] bg-white px-3 py-1.5 text-xs font-bold text-[var(--ink)] hover:bg-[var(--light)]"
            >
              <Paperclip size={13} />
              {picked || currentName ? 'Change' : 'Choose file'}
            </button>
            {picked && (
              <button
                type="button"
                onClick={() => onChange(null)}
                aria-label="Remove the chosen file"
                className="rounded-lg p-1.5 text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]"
              >
                <X size={15} />
              </button>
            )}
          </>
        )}
        <input ref={inputRef} type="file" accept={rule?.accept} className="hidden" onChange={pick} />
      </div>
      {problem && <p className="mb-0 mt-1.5 text-xs font-bold text-[#C53030]">{problem}</p>}
    </div>
  );
}
