import { useState } from 'react';

/**
 * Pages a list held in the browser (e.g. the filtered roles) like a NexHomePageResponse, for `common/Pagination`:
 * { pageRows, pageData, setPage }. A page past the end (after filtering or a delete) falls back to the last one.
 */
export default function useClientPaging(rows, size = 10) {
  const [page, setPage] = useState(0);
  const totalPages = Math.ceil(rows.length / size);
  const current = Math.min(page, Math.max(0, totalPages - 1));
  return {
    pageRows: rows.slice(current * size, (current + 1) * size),
    pageData: { page: current, size, totalElements: rows.length, totalPages },
    setPage
  };
}
