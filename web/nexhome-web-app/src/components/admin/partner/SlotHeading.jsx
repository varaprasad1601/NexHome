/**
 * A weekly availability column heading: the slot's name, and its time range below in smaller text. AVAILABILITY_SLOT
 * names read "Morning (9 AM - 12 PM)"; a name without a time in brackets shows as it is.
 */
export default function SlotHeading({ slot }) {
  const match = /^(.*?)\s*\((.*)\)\s*$/.exec(slot.displayName ?? '');
  const name = match ? match[1] : slot.displayName;
  const time = match ? match[2].replace(/\s*-\s*/, ' – ') : null;
  return (
    <span className="flex flex-col items-center gap-0.5">
      <span className="text-[0.75rem] font-black normal-case tracking-normal text-[var(--ink)]">{name}</span>
      {time && (
        <span className="whitespace-nowrap text-[0.6875rem] font-normal normal-case tracking-normal text-[var(--muted)]">
          {time}
        </span>
      )}
    </span>
  );
}
