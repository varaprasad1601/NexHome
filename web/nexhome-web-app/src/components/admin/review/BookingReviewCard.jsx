import { Star } from 'lucide-react';
import { useEffect, useState } from 'react';

import FormModal from '../common/FormModal';
import { When } from '../payment/moneyParts';
import { cardClass } from '../system/systemStyles';
import Stars from './Stars';
import { useAdminAuth } from '../../../context/adminAuth';
import { supportBadgeClass } from '../../../data/supportOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';

const REVIEW_LOOKUP = ['REVIEW_STATUS'];
const RATING_OPTIONS = [5, 4, 3, 2, 1].map((value) => ({ value: String(value), label: `${value} star${value === 1 ? '' : 's'}` }));

/**
 * The booking page's Review card (BOOKING_MANAGE or REVIEW_MANAGE read): the customer's review of a completed booking
 * (GET /api/reviews/bookings/{id}; 404 is "no review"): both ratings as stars, the text, its status and date. A
 * completed booking without one offers Record Review with REVIEW_MANAGE write (POST /api/reviews/create, on the
 * customer's behalf). Shown only once the booking is completed.
 */
export default function BookingReviewCard({ booking, onMessage }) {
  const { authRequest, can } = useAdminAuth();
  const { label } = useLookupLabels(REVIEW_LOOKUP);
  const [state, setState] = useState({ review: null, loaded: false });
  const [reloadKey, setReloadKey] = useState(0);
  const [recording, setRecording] = useState(false);
  const completed = booking.bookingStatus === 'COMPLETED';

  useEffect(() => {
    if (!completed) return undefined;
    let cancelled = false;
    authRequest(`/api/reviews/bookings/${booking.bookingId}`)
      .then((review) => !cancelled && setState({ review, loaded: true }))
      .catch(() => !cancelled && setState({ review: null, loaded: true }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, booking.bookingId, completed, reloadKey]);

  if (!completed) return null;
  const { review, loaded } = state;

  const record = async (values) => {
    await authRequest('/api/reviews/create', {
      method: 'POST',
      body: {
        bookingId: booking.bookingId,
        serviceRating: Number(values.serviceRating),
        partnerRating: Number(values.partnerRating),
        reviewText: values.reviewText.trim() || null
      }
    });
    setRecording(false);
    onMessage('Review recorded.');
    setReloadKey((key) => key + 1);
  };

  return (
    <section className={cardClass}>
      <div className="mb-3 flex items-center gap-2">
        <Star size={17} className="text-[var(--blue)]" />
        <h2 className="mb-0 flex-1 text-[1.0625rem] font-black text-[var(--navy)]">Review</h2>
        {review && (
          <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${supportBadgeClass('REVIEW_STATUS', review.reviewStatus)}`}>
            {label('REVIEW_STATUS', review.reviewStatus)}
          </span>
        )}
      </div>
      {!loaded ? (
        <div className="h-16 animate-pulse rounded-xl bg-[var(--light)]" />
      ) : review ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center justify-between gap-3">
            <span className="text-[var(--muted)]">Partner</span>
            <Stars rating={review.partnerRating} size={15} label="Partner rating" />
          </div>
          <div className="flex items-center justify-between gap-3">
            <span className="text-[var(--muted)]">Service</span>
            <Stars rating={review.serviceRating} size={15} label="Service rating" />
          </div>
          {review.reviewText && (
            <p className="mb-0 rounded-xl bg-[var(--light)] px-3 py-2.5 text-[var(--ink)]">“{review.reviewText}”</p>
          )}
          <div className="text-xs text-[var(--muted)]">
            Reviewed <When value={review.creationDate} />
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="mb-0 text-sm text-[var(--muted)]">The customer has not reviewed this booking yet.</p>
          {can('REVIEW_MANAGE', 'WRITE') && (
            <button
              type="button"
              onClick={() => setRecording(true)}
              className="rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
            >
              + Record Review
            </button>
          )}
        </div>
      )}

      {recording && (
        <FormModal
          title="Record Review"
          fields={[
            { key: 'partnerRating', label: 'Partner Rating', type: 'select', required: true, options: RATING_OPTIONS },
            { key: 'serviceRating', label: 'Service Rating', type: 'select', required: true, options: RATING_OPTIONS },
            {
              key: 'reviewText',
              label: 'Review',
              type: 'textarea',
              rows: 3,
              maxLength: 2000,
              wide: true,
              hint: "The customer's words, if they gave any."
            }
          ]}
          initial={{ partnerRating: '5', serviceRating: '5', reviewText: '' }}
          submitLabel="Record Review"
          onSubmit={record}
          onClose={() => setRecording(false)}
        />
      )}
    </section>
  );
}
