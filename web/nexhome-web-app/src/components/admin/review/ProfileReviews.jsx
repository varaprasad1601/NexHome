import { BadgeCheck, MessageSquareText, Star } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import Pagination from '../common/Pagination';
import { When } from '../payment/moneyParts';
import { linkClass } from '../system/systemStyles';
import Stars from './Stars';
import { useAdminAuth } from '../../../context/adminAuth';
import { supportBadgeClass } from '../../../data/supportOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';
import { formatNumber } from '../../../utils/format';

const REVIEW_LOOKUP = ['REVIEW_STATUS'];
const PAGE_SIZE = 5;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/** Review-site colours by rating: green for 3 to 5, amber for 2, red for 1. */
const ratingColour = (rating) => (rating >= 3 ? '#26A541' : rating === 2 ? '#FF9F00' : '#FF6161');

/**
 * The rating summary, centred as on Flipkart and Amazon: the average in large type with a star and "N ratings &
 * reviews", then one bar per rating, 5 down to 1, in its rating's colour, with its count.
 */
function RatingSummary({ summary }) {
  const total = summary?.total ?? 0;
  const average = summary?.averageRating != null ? Number(summary.averageRating) : null;
  return (
    <div className="mx-auto flex max-w-[52rem] flex-col items-center gap-6 px-6 py-4 sm:flex-row sm:justify-center sm:gap-10">
      <div className="flex flex-col items-center text-center sm:w-44">
        <div className="flex items-center gap-1.5 text-[2.5rem] font-black leading-none text-[var(--ink)]">
          {average != null ? average.toFixed(1) : '—'}
          <Star size={26} className="fill-[#F2C94C] text-[#D9A21B]" aria-hidden="true" />
        </div>
        <div className="mt-2 text-[0.8125rem] font-bold text-[var(--muted)]">
          {formatNumber(total)} rating{total === 1 ? '' : 's'} &amp; review{total === 1 ? '' : 's'}
        </div>
      </div>
      <div className="w-full max-w-[24rem] space-y-1.5 sm:border-l sm:border-[var(--line)] sm:pl-10">
        {[5, 4, 3, 2, 1].map((rating) => {
          const count = summary?.ratingCounts?.[rating] ?? 0;
          const share = total ? (count / total) * 100 : 0;
          return (
            <div key={rating} className="flex items-center gap-3 text-[0.8125rem]">
              <span className="flex w-7 flex-shrink-0 items-center gap-0.5 font-bold text-[var(--ink)]">
                {rating}
                <Star size={11} className="fill-[#F2C94C] text-[#D9A21B]" aria-hidden="true" />
              </span>
              <span
                className="h-[0.4375rem] flex-1 overflow-hidden rounded-full bg-[#F0F0F0]"
                role="img"
                aria-label={`${rating} stars: ${count} review${count === 1 ? '' : 's'}`}
              >
                <span
                  className="block h-full rounded-full transition-[width]"
                  style={{ width: `${share}%`, backgroundColor: ratingColour(rating) }}
                />
              </span>
              <span className="w-10 flex-shrink-0 text-right text-[var(--muted)]">{formatNumber(count)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * A profile's Reviews tab (REVIEW_MANAGE read), as review sites show them, without card borders: the centred rating summary
 * (GET /api/reviews/summary), then the reviews across the full width, 5 to a page (GET /api/reviews): the other
 * person (the customer on a partner's profile, the partner on a customer's) with a Booking tag, below it the booking id
 * and the date, then the partner rating in gold stars and the text; a review that is not published is tagged. `of` is 'partner' or
 * 'customer'; `profileId` that profile's id.
 */
export default function ProfileReviews({ of, profileId }) {
  const { authRequest } = useAdminAuth();
  const { label } = useLookupLabels(REVIEW_LOOKUP);
  const param = of === 'partner' ? 'partnerProfileId' : 'customerProfileId';
  const [summary, setSummary] = useState(null);
  const [page, setPage] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });

  useEffect(() => {
    if (of !== 'partner') return undefined;
    let cancelled = false;
    authRequest(`/api/reviews/summary?${param}=${profileId}`)
      .then((data) => !cancelled && setSummary(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, of, param, profileId]);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/reviews?${param}=${profileId}&page=${page}&size=${PAGE_SIZE}`)
      .then((data) => !cancelled && setList({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, param, profileId, page]);

  const rows = list.data.content;
  return (
    <div className="w-full space-y-5">
      {/* A partner's profile shows the rating summary and a heading; a customer's tab is the list alone */}
      {of === 'partner' && <RatingSummary summary={summary} />}

      <section className="w-full">
        {of === 'partner' && <h3 className="mb-0 py-3 text-[1.0625rem] font-black text-[var(--navy)]">Customer Reviews</h3>}
        {list.error ? (
          <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
            {list.error}
          </div>
        ) : list.loading ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-[var(--light)]" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center py-12 text-center">
            <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#FFF7DE] text-[#B7860B]">
              <MessageSquareText size={22} />
            </span>
            <div className="text-sm font-black text-[var(--navy)]">No reviews yet</div>
            <div className="mt-0.5 text-xs text-[var(--muted)]">
              {of === 'partner' ? 'Customers review a booking once it is completed.' : 'Reviews this customer writes show here.'}
            </div>
          </div>
        ) : (
          <ul className="m-0 list-none divide-y divide-[var(--line)] p-0">
            {rows.map((review) => {
              const other =
                of === 'partner'
                  ? { to: `/admin/users/customers/${review.customerProfileId}`, name: review.customerName ?? review.customerPublicId }
                  : { to: `/admin/users/partners/${review.partnerProfileId}`, name: review.partnerName ?? review.partnerPublicId };
              return (
                <li key={review.reviewId} className="py-5">
                  {/* The person and the booking tag; the booking id and date; then the stars and the review */}
                  <div className="flex flex-wrap items-center gap-2">
                    <Link to={other.to} className={`text-sm ${linkClass}`}>
                      {other.name}
                    </Link>
                    <span className="inline-flex items-center gap-1 rounded-full bg-[#E9F6EF] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--green-2)]">
                      <BadgeCheck size={12} aria-hidden="true" />
                      Booking
                    </span>
                    {review.reviewStatus !== 'PUBLISHED' && (
                      <span className={`rounded-full px-2 py-0.5 text-[0.6875rem] font-bold ${supportBadgeClass('REVIEW_STATUS', review.reviewStatus)}`}>
                        {label('REVIEW_STATUS', review.reviewStatus)}
                      </span>
                    )}
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-[var(--muted)]">
                    <Link to={`/admin/bookings/${review.bookingId}`} className={linkClass}>
                      {review.bookingPublicId}
                    </Link>
                    <span aria-hidden="true">·</span>
                    <When value={review.creationDate} />
                  </div>
                  <div className="mt-2.5">
                    <Stars rating={review.partnerRating} size={15} label="Partner rating" />
                  </div>
                  <p className={`mb-0 mt-1.5 text-sm leading-relaxed ${review.reviewText ? 'text-[var(--ink)]' : 'italic text-[var(--muted)]'}`}>
                    {review.reviewText ?? 'No written review.'}
                  </p>
                </li>
              );
            })}
          </ul>
        )}
        {!list.loading && !list.error && list.data.totalElements > PAGE_SIZE && (
          <div>
            <Pagination pageData={list.data} noun="reviews" onPageChange={setPage} />
          </div>
        )}
      </section>
    </div>
  );
}
