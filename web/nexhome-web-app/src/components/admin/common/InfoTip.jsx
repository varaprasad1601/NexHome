import { Info } from 'lucide-react';
import { useRef, useState } from 'react';

/**
 * An info icon after a field's label; hovering or focusing it shows `text` in a small tooltip above it. The tooltip
 * is fixed to the window, so a scrolling dialog cannot clip it.
 */
export default function InfoTip({ text }) {
  const ref = useRef(null);
  const [position, setPosition] = useState(null);
  const show = () => {
    const rect = ref.current.getBoundingClientRect();
    setPosition({ left: rect.left + rect.width / 2, top: rect.top - 8 });
  };
  const hide = () => setPosition(null);

  return (
    <span className="inline-flex align-middle">
      <button
        ref={ref}
        type="button"
        aria-label={text}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        className="flex h-4 w-4 items-center justify-center rounded-full text-[#8A9AAD] outline-none hover:text-[var(--blue)] focus-visible:text-[var(--blue)]"
      >
        <Info size={14} strokeWidth={2.2} />
      </button>
      {position && (
        <span
          role="tooltip"
          style={{ left: position.left, top: position.top }}
          className="pointer-events-none fixed z-[70] w-max max-w-[16rem] -translate-x-1/2 -translate-y-full rounded-lg bg-[var(--navy)] px-2.5 py-1.5 text-xs font-normal leading-snug text-white shadow-lg"
        >
          {text}
          <span className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-[var(--navy)]" />
        </span>
      )}
    </span>
  );
}
