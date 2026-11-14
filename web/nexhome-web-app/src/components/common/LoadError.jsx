import Icon from './Icon';

/**
 * Shown where the site's services or FAQs would be when the server cannot be reached (no built-in content is shown
 * instead): "We could not load <what> right now" with Try again.
 */
export default function LoadError({ what = 'this', onRetry, className = '' }) {
  return (
    <div className={`rounded-2xl border border-gray-200 bg-white p-6 text-center ${className}`} role="alert">
      <p className="text-sm font-extrabold text-[#0B438B] mb-1">We could not load {what} right now.</p>
      <p className="text-xs text-gray-500 mb-4">Please check your connection and try again in a moment.</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="border border-[#168F5B] text-[#168F5B] hover:bg-green-50 text-xs font-bold px-5 py-2 rounded-full inline-flex items-center gap-1.5 transition"
        >
          <Icon name="refresh" size={13} /> Try again
        </button>
      )}
    </div>
  );
}
