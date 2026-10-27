/** A labelled dialog field: the label (with * or "(optional)"), the input passed as children, then a hint or error. */
export default function FormField({ label, htmlFor, required = true, hint, error, children }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-[var(--ink)]">
        {label}{' '}
        {required ? <span className="text-[#E53E3E]">*</span> : <span className="font-normal text-[var(--muted)]">(optional)</span>}
      </label>
      {children}
      {error ? (
        <div className="mt-1 text-xs font-bold text-[#C53030]">{error}</div>
      ) : (
        hint && <div className="mt-1 text-xs text-[var(--muted)]">{hint}</div>
      )}
    </div>
  );
}
