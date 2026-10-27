import { Check, UserRound, X } from 'lucide-react';

import PhotoUpload from './PhotoUpload';

/** The progress ring around the photo: drawn on a 116-unit square, 6 units wide, starting at the top. */
const RING_SIZE = 116;
const RING_STROKE = 6;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

/** The ring and tag colours: amber below half, brand blue up to 99%, green when complete. */
function completionTone(percent) {
  if (percent === 100) return { ring: 'var(--green)', tag: 'border-[var(--green)] text-[var(--green-2)]' };
  if (percent < 50) return { ring: '#E0A800', tag: 'border-[#E0A800] text-[#8A6508]' };
  return { ring: 'var(--blue)', tag: 'border-[var(--blue)] text-[var(--blue)]' };
}

/**
 * A profile summary's photo (or initials) with the profile completion on it (staff and customer profiles): the border
 * is a ring filled to the share of `checks` done, and a "67% complete" tag sits across its bottom. Hovering or
 * focusing the tag lists every detail as added or missing; with `canEdit`, hovering the photo offers a new one
 * (PhotoUpload).
 *
 * @param checks [{ label, done }] in the order the list shows them
 */
export default function CompletionAvatar({ checks, photoUrl, name, initials, canEdit }) {
  const done = checks.filter((check) => check.done).length;
  const percent = Math.round((done / checks.length) * 100);
  const tone = completionTone(percent);
  return (
    <div className="relative h-[7.25rem] w-[7.25rem] flex-shrink-0">
      <svg
        viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`}
        className="absolute inset-0 h-full w-full -rotate-90"
        aria-hidden="true"
      >
        <circle
          cx={RING_SIZE / 2}
          cy={RING_SIZE / 2}
          r={RING_RADIUS}
          fill="none"
          stroke="#E6EEF6"
          strokeWidth={RING_STROKE}
        />
        <circle
          cx={RING_SIZE / 2}
          cy={RING_SIZE / 2}
          r={RING_RADIUS}
          fill="none"
          stroke={tone.ring}
          strokeWidth={RING_STROKE}
          strokeLinecap="round"
          strokeDasharray={RING_LENGTH}
          strokeDashoffset={RING_LENGTH * (1 - percent / 100)}
          className="transition-[stroke-dashoffset] duration-500"
        />
      </svg>
      {photoUrl ? (
        <img
          src={photoUrl}
          alt={name || 'Profile photo'}
          className="absolute inset-[0.625rem] h-[6rem] w-[6rem] rounded-full object-cover"
        />
      ) : (
        <span className="absolute inset-[0.625rem] flex items-center justify-center rounded-full bg-[#EDF6FD] text-3xl font-black text-[var(--navy)]">
          {initials || <UserRound size={38} strokeWidth={1.8} className="text-[var(--blue)]" />}
        </span>
      )}

      {canEdit && <PhotoUpload hasPhoto={Boolean(photoUrl)} name={name} inset="0.625rem" />}

      {/* The tag across the bottom of the ring, with the details on hover or focus */}
      <div className="group absolute -bottom-1.5 left-1/2 z-10 -translate-x-1/2">
        <span
          tabIndex={0}
          role="button"
          aria-label={`Profile ${percent}% complete: ${done} of ${checks.length} details added`}
          className={`block cursor-default whitespace-nowrap rounded-full border bg-white px-2.5 py-0.5 text-[0.6875rem] font-black shadow-[0_4px_10px_rgba(14,51,89,0.1)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue)]/30 ${tone.tag}`}
        >
          {percent}% <span className="font-bold text-[var(--muted)]">complete</span>
        </span>
        <div
          role="tooltip"
          className="invisible absolute left-1/2 top-[calc(100%+0.5rem)] w-[15rem] -translate-x-1/2 rounded-xl border border-[var(--line)] bg-white p-4 text-left opacity-0 shadow-[var(--shadow)] transition-opacity group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100"
        >
          <div className="mb-1 text-[0.8125rem] font-black text-[var(--navy)]">Profile {percent}% complete</div>
          <div className="mb-2.5 text-xs text-[var(--muted)]">
            {done} of {checks.length} details added
          </div>
          <ul className="m-0 grid list-none gap-1.5 p-0 text-[0.8125rem]">
            {checks.map((check) => (
              <li key={check.label} className="flex items-center gap-2">
                <span
                  className={`flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-full ${
                    check.done ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FDECEC] text-[#C53030]'
                  }`}
                >
                  {check.done ? <Check size={10} strokeWidth={3.5} /> : <X size={10} strokeWidth={3.5} />}
                </span>
                <span className={check.done ? 'font-bold text-[var(--ink)]' : 'text-[var(--muted)]'}>
                  {check.label}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
