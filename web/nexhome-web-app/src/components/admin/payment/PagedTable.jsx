import { useEffect, useState } from 'react';

import Pagination from '../common/Pagination';
import { headClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/**
 * A server-paged table (a NexHomePageResponse endpoint) for the payments screens: `path` with its filters (page and
 * size are added), `columns` ([{ header, cell(row, index), className, headClassName }]), `rowKey(row)`. A change of
 * `path` starts again at the first page; `reloadKey` reloads.
 */
export default function PagedTable({
  path,
  columns,
  rowKey,
  emptyText = 'Nothing here yet.',
  reloadKey = 0,
  minWidth = '56rem'
}) {
  const { authRequest } = useAdminAuth();
  const [page, setPage] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [lastPath, setLastPath] = useState(path);
  if (lastPath !== path) {
    setLastPath(path);
    setPage(0);
  }

  useEffect(() => {
    let cancelled = false;
    const separator = path.includes('?') ? '&' : '?';
    authRequest(`${path}${separator}page=${page}&size=${PAGE_SIZE}`)
      .then((data) => !cancelled && setList({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, path, page, reloadKey]);

  if (list.error) {
    return (
      <div
        role="alert"
        className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
      >
        {list.error}
      </div>
    );
  }
  const rows = list.data.content;
  return (
    <>
      <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
        <table className="w-full text-sm" style={{ minWidth }}>
          <thead className="bg-[var(--light)]">
            <tr>
              <th className={`${headClass} w-12`}>#</th>
              {columns.map((column) => (
                <th key={column.header} className={`${headClass} ${column.headClassName ?? ''}`}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {list.loading &&
              Array.from({ length: 4 }, (_, i) => (
                <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                  <td colSpan={columns.length + 1} className="px-3 py-4">
                    <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                  </td>
                </tr>
              ))}
            {!list.loading && rows.length === 0 && (
              <tr className="border-t border-[var(--line)]">
                <td colSpan={columns.length + 1} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                  {emptyText}
                </td>
              </tr>
            )}
            {!list.loading &&
              rows.map((row, i) => (
                <tr
                  key={rowKey(row)}
                  className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                >
                  <td className="px-3 py-3 text-[var(--muted)]">{list.data.page * list.data.size + i + 1}</td>
                  {columns.map((column) => (
                    <td key={column.header} className={`px-3 py-3 ${column.className ?? ''}`}>
                      {column.cell(row, i)}
                    </td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {!list.loading && <Pagination pageData={list.data} noun="rows" onPageChange={setPage} />}
    </>
  );
}
