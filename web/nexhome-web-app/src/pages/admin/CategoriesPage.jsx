import { CircleCheck, Clock, FolderTree, Pencil, Plus, Power, RotateCcw, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Pagination from '../../components/admin/common/Pagination';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { categoryBody, categoryFields, categoryForm } from '../../data/catalogForms';
import useAdminList from '../../hooks/useAdminList';
import useClientPaging from '../../hooks/useClientPaging';
import { formatNumber } from '../../utils/format';

/** The table's header cells, as the staff tables'. */
const headClass =
  'whitespace-nowrap px-2.5 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';

/** The name column's avatar tints, as the staff tables'. */
const AVATAR_TINTS = [
  'bg-[#EDF6FD] text-[var(--blue)]',
  'bg-[#E9F6EF] text-[var(--green-2)]',
  'bg-[#E8EEF6] text-[var(--navy)]',
  'bg-[#FFF7DE] text-[#8A6508]'
];

const STAT_TILES = [
  { key: 'total', label: 'Total Categories', icon: FolderTree, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'active', label: 'Active', icon: CircleCheck, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'inactive', label: 'Inactive', icon: Clock, tile: 'bg-[#FFF7DE] text-[#B7860B]' }
];

/** The category's image as a round avatar, or its initials while it has none (or the address fails to load). */
function CategoryAvatar({ category, tint }) {
  const [failed, setFailed] = useState(false);
  const initials = category.name
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
  return category.iconUrl && !failed ? (
    <img
      src={category.iconUrl}
      alt=""
      onError={() => setFailed(true)}
      className="h-8 w-8 flex-shrink-0 rounded-full border border-[var(--line)] bg-white object-cover"
    />
  ) : (
    <span className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-black ${tint}`}>
      {initials}
    </span>
  );
}

/**
 * Service Management > Manage Categories: every category (inactive ones too, with CATALOG_MANAGE read) in display
 * order, counts, search and a status filter, paged in the browser, in the staff tables' design (selection boxes,
 * the id as the link, a round avatar with the name). A category's id opens its page (CategoryViewPage). With
 * CATALOG_MANAGE write
 * the admin adds and edits categories and switches them on or off (no deleting in the console: deactivate instead).
 * Data from GET /api/catalog/categories.
 */
export default function CategoriesPage() {
  const { authRequest, can } = useAdminAuth();
  const canEdit = can('CATALOG_MANAGE', 'WRITE');
  const hasActions = canEdit;
  const { items: categories, loading, error, reload } = useAdminList('/api/catalog/categories?includeInactive=true');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState(null); // null, 'new' or the category
  const [selected, setSelected] = useState(() => new Set());
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Categories | NexHome Admin';
  }, []);

  const stats = useMemo(() => {
    const active = categories.filter((category) => category.isActive).length;
    return { total: categories.length, active, inactive: categories.length - active };
  }, [categories]);

  const rows = useMemo(() => {
    const text = search.trim().toLowerCase();
    return categories.filter(
      (category) =>
        (!status || String(category.isActive) === status) &&
        (!text ||
          [category.name, category.publicId, category.description].some((value) => value?.toLowerCase().includes(text)))
    );
  }, [categories, search, status]);
  const { pageRows, pageData, setPage } = useClientPaging(rows);

  const save = async (values) => {
    const body = categoryBody(values);
    const saved =
      editing === 'new'
        ? await authRequest('/api/catalog/categories/create', { method: 'POST', body })
        : await authRequest('/api/catalog/categories/update', {
            method: 'PUT',
            body: { serviceCategoryId: editing.serviceCategoryId, ...body }
          });
    showToast(`${saved.name} was ${editing === 'new' ? 'added' : 'updated'}.`);
    setEditing(null);
    reload();
  };

  const setActive = async (category, isActive) => {
    try {
      await authRequest('/api/catalog/categories/update', {
        method: 'PUT',
        body: { serviceCategoryId: category.serviceCategoryId, isActive }
      });
      showToast(`${category.name} is now ${isActive ? 'active' : 'inactive'}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const actionsOf = (category) => [
    ...(canEdit
      ? [
          { label: 'Edit', icon: Pencil, onClick: () => setEditing(category) },
          {
            label: category.isActive ? 'Deactivate' : 'Activate',
            icon: Power,
            onClick: () => setActive(category, !category.isActive)
          }
        ]
      : [])
  ];

  const select = (ids, checked) =>
    setSelected((current) => {
      const next = new Set(current);
      ids.forEach((id) => (checked ? next.add(id) : next.delete(id)));
      return next;
    });
  const allSelected = pageRows.length > 0 && pageRows.every((category) => selected.has(category.serviceCategoryId));

  const columns = hasActions ? 9 : 8;

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Service Management' }, { label: 'Categories' }]}
        title="Categories"
        description="The groups services are listed under, in the order customers see them."
        actions={
          canEdit && (
            <button type="button" className={primaryButtonClass} onClick={() => setEditing('new')}>
              <Plus size={17} strokeWidth={2.4} />
              Add Category
            </button>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-3 xl:overflow-visible xl:px-0 xl:pb-0">
        {STAT_TILES.map((tile) => {
          const Icon = tile.icon;
          return (
            <div key={tile.key} className={`flex min-w-[13.125rem] flex-1 items-center gap-4 xl:min-w-0 ${cardClass}`}>
              <span className={`flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full ${tile.tile}`}>
                <Icon size={22} strokeWidth={2} />
              </span>
              <div>
                <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{tile.label}</div>
                <div className="mt-1 h-7 text-[1.625rem] font-black leading-7 text-[var(--ink)]">
                  {loading ? (
                    <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />
                  ) : (
                    formatNumber(stats[tile.key])
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]">
          <label className="relative">
            <span className="sr-only">Search categories</span>
            <Search
              size={16}
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
            />
            <input
              type="search"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Search by name, ID or description..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Status"
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All Status' },
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' }
            ]}
          />
          <button
            type="button"
            className={resetButtonClass}
            onClick={() => {
              setSearch('');
              setStatus('');
              setPage(0);
            }}
          >
            <RotateCcw size={15} />
            Reset
          </button>
        </div>

        {error ? (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {error}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[48rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-10`}>
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-[var(--blue)]"
                        checked={allSelected}
                        onChange={(e) =>
                          select(
                            pageRows.map((category) => category.serviceCategoryId),
                            e.target.checked
                          )
                        }
                        aria-label="Select all on this page"
                      />
                    </th>
                    <th className={headClass}>#</th>
                    <th className={headClass}>Category ID</th>
                    <th className={`${headClass} w-[14rem]`}>Category Name</th>
                    <th className={headClass}>Description</th>
                    <th className={headClass}>Partners</th>
                    <th className={headClass}>Order</th>
                    <th className={headClass}>Status</th>
                    {hasActions && <th className={`${headClass} text-right`}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {loading &&
                    Array.from({ length: 5 }, (_, i) => (
                      <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                        <td colSpan={columns} className="px-3 py-4">
                          <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                        </td>
                      </tr>
                    ))}
                  {!loading && pageRows.length === 0 && (
                    <tr className="border-t border-[var(--line)]">
                      <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                        No categories match your search or filters.
                      </td>
                    </tr>
                  )}
                  {!loading &&
                    pageRows.map((category, i) => (
                      <tr
                        key={category.serviceCategoryId}
                        className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                      >
                        <td className="px-2.5 py-3">
                          <input
                            type="checkbox"
                            className="h-4 w-4 accent-[var(--blue)]"
                            checked={selected.has(category.serviceCategoryId)}
                            onChange={(e) => select([category.serviceCategoryId], e.target.checked)}
                            aria-label={`Select ${category.name}`}
                          />
                        </td>
                        <td className="px-2.5 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                        <td className="whitespace-nowrap px-2.5 py-3">
                          <Link
                            to={`/admin/services/categories/${category.serviceCategoryId}`}
                            className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                            title={`${category.publicId}: view the category`}
                          >
                            {category.publicId}
                          </Link>
                        </td>
                        <td className="px-2.5 py-3">
                          <span className="flex w-[14rem] min-w-0 items-center gap-2 whitespace-nowrap">
                            <CategoryAvatar category={category} tint={AVATAR_TINTS[i % AVATAR_TINTS.length]} />
                            <span className="min-w-0 truncate font-bold" title={category.name}>
                              {category.name}
                            </span>
                          </span>
                        </td>
                        <td className="max-w-0 px-2.5 py-3">
                          {category.description ? (
                            <span
                              className="block max-w-[26rem] truncate text-[var(--muted)]"
                              title={category.description}
                            >
                              {category.description}
                            </span>
                          ) : (
                            <span className="text-[#A8B6C6]">—</span>
                          )}
                        </td>
                        {/* Partners whose main service is this category (partnerCount, staff list only) */}
                        <td className="px-2.5 py-3 font-bold">{category.partnerCount ?? '—'}</td>
                        <td className="px-2.5 py-3 text-[var(--muted)]">{category.displayOrder}</td>
                        <td className="px-2.5 py-3">
                          <span
                            className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                              category.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                            }`}
                          >
                            {category.isActive ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        {hasActions && (
                          <td className="px-2.5 py-3 text-right">
                            <div className="flex justify-end">
                              <RowActions label={category.name} actions={actionsOf(category)} />
                            </div>
                          </td>
                        )}
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
            {!loading && <Pagination pageData={pageData} noun="categories" onPageChange={setPage} />}
          </>
        )}
      </section>

      {editing && canEdit && (
        <FormModal
          title={editing === 'new' ? 'Add New Category' : 'Edit Category'}
          fields={categoryFields(editing !== 'new', editing === 'new' ? null : editing)}
          initial={categoryForm(editing === 'new' ? null : editing)}
          submitLabel={editing === 'new' ? 'Create Category' : 'Save Changes'}
          onSubmit={save}
          onClose={() => setEditing(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
