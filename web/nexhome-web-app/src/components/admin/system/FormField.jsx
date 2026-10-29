import InfoTip from '../common/InfoTip';

/**
 * A labelled dialog field: the label (with * or "(optional)", or neither with `marker` false, e.g. for a switch) and the
 * field's description (`hint`) behind an info icon after it, then the input passed as children, then an error.
 */
export default function FormField({ label, htmlFor, required = true, marker = true, hint, error, children }) {
  return (
    <div>
      <div className="mb-1.5 flex items-center gap-1.5">
        <label htmlFor={htmlFor} className="text-sm font-bold text-[var(--ink)]">
          {label}{' '}
          {marker &&
            (required ? (
              <span className="text-[#E53E3E]">*</span>
            ) : (
              <span className="font-normal text-[var(--muted)]">(optional)</span>
            ))}
        </label>
        {hint && <InfoTip text={hint} />}
      </div>
      {children}
      {error && <div className="mt-1 text-xs font-bold text-[#C53030]">{error}</div>}
    </div>
  );
}
