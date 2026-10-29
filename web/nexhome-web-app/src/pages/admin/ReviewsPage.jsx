import { MessageSquareText, RotateCcw, Search, Star, ThumbsUp, Wrench } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import { MoneyTile } from '../../components/admin/payment/moneyParts';
import ReviewsTable from '../../components/admin/review/ReviewsTable';
import { cardClass, resetButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { SUPPORT_LOOKUPS } from '../../data/supportOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';

/**
 * Support & Feedback > Reviews (/admin/reviews, REVIEW_MANAGE read): customers' ratings of completed bookings.
 * Tiles (average partner and service ratings of published reviews, total, published; GET /api/reviews/stats), a
 * search (booking, service, names, text), status and rating filters, and the paged list.
 * With REVIEW_MANAGE write each review can be published, hidden or held for review; only published ones count
 * towards partners' ratings.
 */
export default function ReviewsPage() {
  const { authRequest } = useAdminAuth();
  const { values, label } = useLookupLabels(SUPPORT_LOOKUPS);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [status, setStatus] = useState('');
  const [rating, setRating] = useState('');
  const [stats, setStats] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    document.title = 'Reviews | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/reviews/stats')
      .then((data) => !cancelled && setStats(data))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, reloadKey]);

  const setReviewStatus = async (review, reviewStatus) => {
    try {
      await authRequest('/api/reviews/status/update', { method: 'PUT', body: { reviewId: review.reviewId, reviewStatus } });
      setToast({
        message: `The review of ${review.bookingPublicId} is now ${label('REVIEW_STATUS', reviewStatus).toLowerCase()}.`,
        tone: 'success',
        id: Date.now()
      });
      setReloadKey((key) => key + 1);
    } catch (err) {
      setToast({ message: err.message, tone: 'error', id: Date.now() });
    }
  };

  const params = new URLSearchParams();
  if (search) params.set('search', search);
  if (status) params.set('reviewStatus', status);
  if (rating) params.set('rating', rating);
  const query = params.toString() ? `?${params}` : '';

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Support & Feedback' }, { label: 'Reviews' }]}
        title="Reviews & Ratings"
        description="What customers say about completed bookings: the service and the partner, rated 1 to 5."
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:overflow-visible xl:px-0 xl:pb-0">
        <MoneyTile
          icon={Star}
          tone="bg-[#FFF7DE] text-[#B7860B]"
          label="Average Partner Rating"
          value={stats ? (stats.averagePartnerRating != null ? `${Number(stats.averagePartnerRating).toFixed(1)} / 5` : '—') : null}
        />
        <MoneyTile
          icon={Wrench}
          tone="bg-[#E8EEF6] text-[var(--navy)]"
          label="Average Service Rating"
          value={stats ? (stats.averageServiceRating != null ? `${Number(stats.averageServiceRating).toFixed(1)} / 5` : '—') : null}
        />
        <MoneyTile
          icon={MessageSquareText}
          tone="bg-[#EDF6FD] text-[var(--blue)]"
          label="Total Reviews"
          value={stats && formatNumber(stats.total)}
        />
        <MoneyTile icon={ThumbsUp} tone="bg-[#E9F6EF] text-[var(--green)]" label="Published" value={stats && formatNumber(stats.published)} />
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_12rem_10rem_auto]">
          <label className="relative md:col-span-2 xl:col-span-1">
            <span className="sr-only">Search reviews</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search by booking, service, customer, partner or text..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Review status"
            value={status}
            onChange={setStatus}
            options={[
              { value: '', label: 'All Status' },
              ...(values.REVIEW_STATUS ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
            ]}
          />
          <Select
            ariaLabel="Partner rating"
            value={rating}
            onChange={setRating}
            options={[
              { value: '', label: 'All Ratings' },
              ...[5, 4, 3, 2, 1].map((value) => ({ value: String(value), label: `${value} star${value === 1 ? '' : 's'}` }))
            ]}
          />
          <button
            type="button"
            className={resetButtonClass}
            onClick={() => {
              setSearchText('');
              setStatus('');
              setRating('');
            }}
          >
            <RotateCcw size={15} />
            Reset
          </button>
        </div>
        <ReviewsTable query={query} label={label} reloadKey={reloadKey} onSetStatus={setReviewStatus} />
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
