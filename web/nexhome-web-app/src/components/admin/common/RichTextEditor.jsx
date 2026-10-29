import { Bold, Italic, List, ListOrdered, RemoveFormatting, Underline } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { cleanRichText } from '../../../utils/richText';

const TOOLS = [
  { command: 'bold', icon: Bold, label: 'Bold' },
  { command: 'italic', icon: Italic, label: 'Italic' },
  { command: 'underline', icon: Underline, label: 'Underline' },
  { command: 'insertUnorderedList', icon: List, label: 'Bulleted list' },
  { command: 'insertOrderedList', icon: ListOrdered, label: 'Numbered list' },
  { command: 'removeFormat', icon: RemoveFormatting, label: 'Clear formatting' }
];

/**
 * A small formatted-text field: a toolbar (bold, italic, underline, bulleted and numbered lists, clear formatting;
 * also Ctrl+B / I / U) over an editable area. `value` and `onChange` are HTML limited to the tags of utils/richText; pasted content
 * comes in as plain text. Uses the browser's editing commands, so it needs no library.
 */
export default function RichTextEditor({ id, value, onChange, disabled = false, placeholder = '', error = false }) {
  const ref = useRef(null);
  const [active, setActive] = useState({});
  const [focused, setFocused] = useState(false);

  // Only outside changes (loading a service) are written into the editor; typing would otherwise move the caret
  useEffect(() => {
    if (ref.current && ref.current.innerHTML !== value && cleanRichText(ref.current.innerHTML) !== value) {
      ref.current.innerHTML = value;
    }
  }, [value]);

  const emit = () => onChange(cleanRichText(ref.current.innerHTML));
  const refreshActive = () =>
    setActive(
      Object.fromEntries(
        TOOLS.slice(0, 5).map((tool) => [tool.command, document.queryCommandState?.(tool.command) ?? false])
      )
    );
  const run = (command) => {
    ref.current.focus();
    document.execCommand(command, false);
    emit();
    refreshActive();
  };

  return (
    <div
      className={`overflow-hidden rounded-xl border bg-white transition ${
        error
          ? 'border-[#E53E3E]'
          : focused
            ? 'border-[var(--blue)] ring-2 ring-[var(--blue)]/15'
            : 'border-[var(--line)]'
      } ${disabled ? 'bg-[var(--light)]' : ''}`}
    >
      <div
        role="toolbar"
        aria-label="Formatting"
        className="flex flex-wrap items-center gap-0.5 border-b border-[var(--line)] bg-[#FAFCFE] px-2 py-1.5"
      >
        {TOOLS.map((tool, index) => {
          const Icon = tool.icon;
          return (
            <span key={tool.command} className="flex items-center">
              {(index === 3 || index === 5) && <span className="mx-1 h-5 w-px bg-[var(--line)]" aria-hidden="true" />}
              <button
                type="button"
                title={tool.label}
                aria-label={tool.label}
                aria-pressed={index < 5 ? Boolean(active[tool.command]) : undefined}
                disabled={disabled}
                // Keep the selection in the editor
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => run(tool.command)}
                className={`flex h-8 w-8 items-center justify-center rounded-lg transition disabled:cursor-not-allowed disabled:opacity-40 ${
                  active[tool.command]
                    ? 'bg-[#EDF6FD] text-[var(--blue)]'
                    : 'text-[var(--muted)] hover:bg-[var(--light)] hover:text-[var(--ink)]'
                }`}
              >
                <Icon size={16} strokeWidth={2.2} />
              </button>
            </span>
          );
        })}
      </div>
      <div className="relative">
        {!value && !focused && placeholder && (
          <div className="pointer-events-none absolute left-3.5 top-3 text-sm text-[#8A9AAD]">{placeholder}</div>
        )}
        <div
          ref={ref}
          id={id}
          role="textbox"
          aria-multiline="true"
          contentEditable={!disabled}
          suppressContentEditableWarning
          onInput={emit}
          onKeyUp={refreshActive}
          onMouseUp={refreshActive}
          onFocus={() => {
            setFocused(true);
            refreshActive();
          }}
          onBlur={() => setFocused(false)}
          onPaste={(e) => {
            e.preventDefault();
            document.execCommand('insertText', false, e.clipboardData.getData('text/plain'));
          }}
          className={`nh-rich-text max-h-[22rem] min-h-[9.5rem] overflow-y-auto px-3.5 py-3 text-sm leading-relaxed text-[var(--ink)] outline-none ${
            disabled ? 'cursor-not-allowed text-[var(--muted)]' : ''
          }`}
        />
      </div>
    </div>
  );
}
