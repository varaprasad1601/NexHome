import { ChevronLeft, ChevronRight } from 'lucide-react';

/** Up to five page numbers around the current one. */
function visiblePages(page, totalPages) {
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  return Array.from({ length: Math.min(5, totalPages) }, (_, i) => start + i);
}

/**
 * "Showing 1 - 10 of 12 admins" and the page buttons for a NexHomePageResponse.
 *
 * @param noun the plural shown in the summary, e.g. "admins"
 */
export default function Pagination({ pageData, noun, onPageChange }) {
  const { page, size, totalElements, totalPages } = pageData;
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  const buttonClass =
    'flex h-9 min-w-9 items-center justify-center rounded-lg border px-2 text-sm font-bold transition disabled:cursor-not-allowed disabled:opacity-40';

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 pt-4">
      <div className="text-sm text-[var(--muted)]">
        Showing {from} - {to} of {totalElements} {noun}
      </div>
      {totalPages > 1 && (
        <nav className="flex items-center gap-1.5" aria-label="Pages">
          <button
            type="button"
            className={`${buttonClass} border-[var(--line)] bg-white text-[var(--ink)] hover:border-[#C7E3FC]`}
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            aria-label="Previous page"
          >
            <ChevronLeft size={16} />
          </button>
          {visiblePages(page, totalPages).map((number) => (
            <button
              key={number}
              type="button"
              className={`${buttonClass} ${
                number === page
                  ? 'border-[var(--blue)] bg-[var(--blue)] text-white'
                  : 'border-[var(--line)] bg-white text-[var(--ink)] hover:border-[#C7E3FC]'
              }`}
              onClick={() => onPageChange(number)}
              aria-current={number === page ? 'page' : undefined}
            >
              {number + 1}
            </button>
          ))}
          <button
            type="button"
            className={`${buttonClass} border-[var(--line)] bg-white text-[var(--ink)] hover:border-[#C7E3FC]`}
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            aria-label="Next page"
          >
            <ChevronRight size={16} />
          </button>
        </nav>
      )}
    </div>
  );
}
