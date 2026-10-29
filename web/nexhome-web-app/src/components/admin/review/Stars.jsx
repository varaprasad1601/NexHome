import { Star } from 'lucide-react';

/** A 1-5 rating as five stars, the rating's ones filled gold; `label` names it for screen readers. */
export default function Stars({ rating, size = 14, label = 'Rating' }) {
  return (
    <span className="inline-flex items-center gap-0.5" role="img" aria-label={`${label}: ${rating} out of 5`}>
      {[1, 2, 3, 4, 5].map((value) => (
        <Star
          key={value}
          size={size}
          aria-hidden="true"
          className={value <= rating ? 'fill-[#F2C94C] text-[#D9A21B]' : 'fill-transparent text-[#CBD5E1]'}
        />
      ))}
    </span>
  );
}
