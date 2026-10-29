import { EyeOff, FolderTree, Pencil, Plus, Power, RotateCcw, Search, Store, Wrench } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
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
import { formatCommission, formatPrice } from '../../data/catalogForms';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };
const LOOKUPS = ['PRICING_TYPE'];

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

/** A service's initials for its avatar, e.g. "AC Servicing" → "AS". */
const initials = (name) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

const STAT_TILES = [
  { key: 'total', label: 'Total Services', icon: Wrench, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'visible', label: 'Visible to Customers', icon: Store, tile: 'bg-[#E9F6EF] text-[var(--green)]' },
  { key: 'hidden', label: 'Hidden', icon: EyeOff, tile: 'bg-[#FFF7DE] text-[#B7860B]' },
  { key: 'categories', label: 'Categories', icon: FolderTree, tile: 'bg-[#E8EEF6] text-[var(--navy)]' }
];

/**
 * Service Management > Manage Services: counts (all services, those customers can see (active, in an active
 * category), hidden ones, categories), search, category and status filters (?categoryId= from the categories
 * page), and the paged list from GET /api/catalog/services?includeInactive=true in the staff tables' design
 * (selection boxes, the id as the link, a round avatar with the name). A service id opens the service's page
 * (ServiceEditorPage), where it is edited. With CATALOG_MANAGE write the admin adds services (Add Service opens
 * /admin/services/new) and switches them on or off (no deleting in the console: deactivate instead).
 */
export default function ServicesPage() {
  const { authRequest, can } = useAdminAuth();
  const navigate = useNavigate();
  const canEdit = can('CATALOG_MANAGE', 'WRITE');
  const hasActions = canEdit;
  const { label } = useLookupLabels(LOOKUPS);
  const [searchParams, setSearchParams] = useSearchParams();
  const categoryId = searchParams.get('categoryId') ?? '';
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  const [stats, setStats] = useState(null);
  const [categories, setCategories] = useState([]);
  const [selected, setSelected] = useState(() => new Set());
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const reload = () => setReloadKey((key) => key + 1);

  useEffect(() => {
    document.title = 'Services | NexHome Admin';
  }, []);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), includeInactive: 'true' });
    if (search) params.set('search', search);
    if (categoryId) params.set('categoryId', categoryId);
    if (status) params.set('isActive', status === 'ACTIVE' ? 'true' : 'false');
    authRequest(`/api/catalog/services?${params}`)
      .then((data) => !cancelled && setList({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, page, search, categoryId, status, reloadKey]);

  // The counts: every service, and those the public sees (active services of active categories)
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      authRequest('/api/catalog/services?size=1&includeInactive=true'),
      authRequest('/api/catalog/services?size=1'),
      authRequest('/api/catalog/categories?includeInactive=true')
    ])
      .then(([all, visible, allCategories]) => {
        if (cancelled) return;
        setCategories(allCategories);
        setStats({
          total: all.totalElements,
          visible: visible.totalElements,
          hidden: all.totalElements - visible.totalElements,
          categories: allCategories.length
        });
      })
      .catch(() => !cancelled && setStats(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, reloadKey]);

  /** Another page of the list, with the loading rows (the same page changes nothing, so it is not reloaded). */
  const goToPage = (next) => {
    if (next === page) return;
    setPage(next);
    setList((current) => ({ ...current, loading: true }));
  };
  const changeFilter = (setter) => (value) => {
    setter(value);
    setPage(0);
    setList((current) => ({ ...current, loading: true }));
  };
  const setCategory = (value) =>
    changeFilter((next) => setSearchParams(next ? { categoryId: next } : {}, { replace: true }))(value);
  const resetFilters = () => {
    setSearchText('');
    setStatus('');
    setSearchParams({}, { replace: true });
    setPage(0);
  };

  const setActive = async (service, isActive) => {
    try {
      await authRequest('/api/catalog/services/update', {
        method: 'PUT',
        // An update always replaces the discount price: resend it so switching keeps the discount
        body: { serviceId: service.serviceId, isActive, discountPrice: service.discountPrice }
      });
      showToast(`${service.name} is now ${isActive ? 'active' : 'inactive'}.`);
      reload();
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  const actionsOf = (service) => [
    ...(canEdit
      ? [
          { label: 'Edit', icon: Pencil, onClick: () => navigate(`/admin/services/${service.serviceId}/edit`) },
          {
            label: service.isActive ? 'Deactivate' : 'Activate',
            icon: Power,
            onClick: () => setActive(service, !service.isActive)
          }
        ]
      : [])
  ];

  const rows = list.data.content;
  const firstRowNumber = list.data.page * list.data.size + 1;
  const columns = hasActions ? 10 : 9;
  const select = (ids, checked) =>
    setSelected((current) => {
      const next = new Set(current);
      ids.forEach((id) => (checked ? next.add(id) : next.delete(id)));
      return next;
    });
  const allSelected = rows.length > 0 && rows.every((service) => selected.has(service.serviceId));

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Service Management' }, { label: 'Services' }]}
        title="Services"
        description="Everything customers can book, with its pricing and commission."
        actions={
          canEdit && (
            <Link to="/admin/services/new" className={`${primaryButtonClass} no-underline`}>
              <Plus size={17} strokeWidth={2.4} />
              Add Service
            </Link>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
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
                  {stats ? (
                    formatNumber(stats[tile.key])
                  ) : (
                    <span className="block h-full w-12 animate-pulse rounded-md bg-[var(--light)]" />
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_13rem_11rem_auto]">
          <label className="relative">
            <span className="sr-only">Search services</span>
            <Search
              size={16}
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
            />
            <input
              type="search"
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value);
                setPage(0);
              }}
              placeholder="Search services by name..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Category"
            value={categoryId}
            onChange={setCategory}
            options={[
              { value: '', label: 'All Categories' },
              ...categories.map((category) => ({ value: String(category.serviceCategoryId), label: category.name }))
            ]}
          />
          <Select
            ariaLabel="Status"
            value={status}
            onChange={changeFilter(setStatus)}
            options={[
              { value: '', label: 'All Statuses' },
              { value: 'ACTIVE', label: 'Active' },
              { value: 'INACTIVE', label: 'Inactive' }
            ]}
          />
          <button type="button" className={resetButtonClass} onClick={resetFilters}>
            <RotateCcw size={15} />
            Reset
          </button>
        </div>

        {list.error ? (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {list.error}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
              <table className="w-full min-w-[56rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={`${headClass} w-10`}>
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-[var(--blue)]"
                        checked={allSelected}
                        onChange={(e) =>
                          select(
                            rows.map((service) => service.serviceId),
                            e.target.checked
                          )
                        }
                        aria-label="Select all on this page"
                      />
                    </th>
                    <th className={headClass}>#</th>
                    <th className={headClass}>Service ID</th>
                    <th className={`${headClass} w-[16rem]`}>Service Name</th>
                    <th className={headClass}>Category</th>
                    <th className={headClass}>Pricing Type</th>
                    <th className={headClass}>Price</th>
                    <th className={headClass}>Commission</th>
                    <th className={headClass}>Status</th>
                    {hasActions && <th className={`${headClass} text-right`}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {list.loading &&
                    Array.from({ length: 5 }, (_, i) => (
                      <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                        <td colSpan={columns} className="px-3 py-4">
                          <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                        </td>
                      </tr>
                    ))}
                  {!list.loading && rows.length === 0 && (
                    <tr className="border-t border-[var(--line)]">
                      <td colSpan={columns} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                        No services match your search or filters.
                      </td>
                    </tr>
                  )}
                  {!list.loading &&
                    rows.map((service, i) => {
                      const actions = actionsOf(service);
                      return (
                        <tr
                          key={service.serviceId}
                          className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                        >
                          <td className="px-2.5 py-3">
                            <input
                              type="checkbox"
                              className="h-4 w-4 accent-[var(--blue)]"
                              checked={selected.has(service.serviceId)}
                              onChange={(e) => select([service.serviceId], e.target.checked)}
                              aria-label={`Select ${service.name}`}
                            />
                          </td>
                          <td className="px-2.5 py-3 text-[var(--muted)]">{firstRowNumber + i}</td>
                          <td className="whitespace-nowrap px-2.5 py-3">
                            <Link
                              to={`/admin/services/${service.serviceId}`}
                              className="block max-w-[7rem] truncate font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]"
                              title={`${service.publicId}: view service`}
                            >
                              {service.publicId}
                            </Link>
                          </td>
                          <td className="px-2.5 py-3">
                            <span className="flex w-[16rem] min-w-0 items-center gap-2">
                              <span
                                className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-black ${AVATAR_TINTS[i % AVATAR_TINTS.length]}`}
                              >
                                {initials(service.name)}
                              </span>
                              <span className="min-w-0 truncate font-bold" title={service.name}>
                                {service.name}
                              </span>
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-2.5 py-3">{service.categoryName}</td>
                          <td className="whitespace-nowrap px-2.5 py-3">
                            {label('PRICING_TYPE', service.pricingType)}
                          </td>
                          <td className="whitespace-nowrap px-2.5 py-3">
                            <span className="font-bold">{formatPrice(service.discountPrice ?? service.basePrice)}</span>
                            {service.discountPrice !== null && (
                              <span className="ml-1.5 text-[0.75rem] text-[var(--muted)] line-through">
                                {formatPrice(service.basePrice)}
                              </span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-2.5 py-3 text-[var(--muted)]">
                            {formatCommission(service.platformCommissionType, service.platformCommissionValue) ?? '—'}
                          </td>
                          <td className="px-2.5 py-3">
                            <span
                              className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                                service.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                              }`}
                            >
                              {service.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                          {hasActions && (
                            <td className="px-2.5 py-3 text-right">
                              <div className="flex justify-end">
                                {actions.length > 0 && <RowActions label={service.name} actions={actions} />}
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
            {!list.loading && <Pagination pageData={list.data} noun="services" onPageChange={goToPage} />}
          </>
        )}
      </section>

      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
