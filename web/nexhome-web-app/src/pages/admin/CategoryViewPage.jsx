import { CalendarDays, FolderTree, IdCard, ListOrdered, Pencil, Store, Users, Wrench } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Pagination from '../../components/admin/common/Pagination';
import Toast from '../../components/admin/common/Toast';
import { StatTile } from '../../components/admin/profile/ProfileParts';
import { profileTabClass } from '../../components/admin/profile/profileStyles';
import { primaryButtonClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { categoryBody, categoryFields, categoryForm, formatCommission, formatPrice } from '../../data/catalogForms';
import { onboardingBadgeClass } from '../../data/partnerOptions';
import { accountStatusLabel, statusBadgeClass } from '../../data/staffOptions';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber, splitApiDateTime } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };
const LOOKUPS = ['PRICING_TYPE', 'PARTNER_ONBOARDING_STATUS'];
const headClass =
  'whitespace-nowrap px-3 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';
const linkClass = 'font-black text-[var(--blue)] no-underline hover:text-[var(--navy)]';

/** The category image's stand-in: the soft gradient of the service page. */
function ImagePlaceholder() {
  return (
    <span
      aria-hidden="true"
      className="relative block h-full w-full bg-[linear-gradient(135deg,#CFE2F6,#F6E2C3_60%,#E8D3B6)]"
    >
      <span className="absolute bottom-[18%] left-[18%] right-[18%] h-[34%] rounded-t-[0.6rem] rounded-b-[0.3rem] bg-white/50" />
      <span className="absolute right-[14%] top-[16%] aspect-square w-[14%] rounded-full bg-white/55" />
    </span>
  );
}

/** One page of a list from the backend, reloaded when `path` changes. */
function usePagedList(path) {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ data: EMPTY_PAGE, loading: true, error: '' });
  useEffect(() => {
    if (!path) return undefined;
    let cancelled = false;
    authRequest(path)
      .then((data) => !cancelled && setState({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setState({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, path]);
  return state;
}

/** A tab's list: the table (or a message) and paging. */
function TabList({ list, noun, empty, onPageChange, children }) {
  if (list.error) {
    return (
      <div className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
        {list.error}
      </div>
    );
  }
  if (list.loading) return <div className="h-32 animate-pulse rounded-xl bg-[var(--light)]" />;
  if (list.data.totalElements === 0) {
    return <p className="rounded-xl bg-[var(--light)] px-4 py-6 text-center text-sm text-[var(--muted)]">{empty}</p>;
  }
  return (
    <>
      <div className="overflow-x-auto rounded-xl border border-[var(--line)]">{children}</div>
      <Pagination pageData={list.data} noun={noun} onPageChange={onPageChange} />
    </>
  );
}

/**
 * Service Management > Categories > a category (/admin/services/categories/:categoryId), read only: one card with the
 * image (a gradient stand-in without one), name, status, category id, display order, added on and description, with
 * Edit Category (CATALOG_MANAGE write, the categories page's dialog); figures (services, those customers see,
 * partners); then tabs: the category's services (GET /api/catalog/services?categoryId=) and, with PARTNER_MANAGE read, the
 * partners whose main service category it is (GET /api/users/partners?mainServiceCategoryId=), both paged.
 */
export default function CategoryViewPage() {
  const { categoryId } = useParams();
  const { authRequest, can } = useAdminAuth();
  const canEdit = can('CATALOG_MANAGE', 'WRITE');
  const canSeePartners = can('PARTNER_MANAGE', 'READ');
  const { label } = useLookupLabels(LOOKUPS);
  const [category, setCategory] = useState(null);
  const [error, setError] = useState('');
  const [visibleCount, setVisibleCount] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState(false);
  const [servicePage, setServicePage] = useState(0);
  const [partnerPage, setPartnerPage] = useState(0);
  const [tab, setTab] = useState('services');
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  // There is no single-category endpoint: the full list is small
  useEffect(() => {
    let cancelled = false;
    authRequest('/api/catalog/categories?includeInactive=true')
      .then((list) => {
        if (cancelled) return;
        const found = list.find((item) => String(item.serviceCategoryId) === categoryId);
        if (found) setCategory(found);
        else setError('This category does not exist.');
      })
      .catch((err) => !cancelled && setError(err.message));
    // Services customers see: active ones of this category while it is active
    authRequest(`/api/catalog/services?size=1&categoryId=${categoryId}`)
      .then((page) => !cancelled && setVisibleCount(page.totalElements))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, categoryId, reloadKey]);

  const services = usePagedList(
    `/api/catalog/services?includeInactive=true&categoryId=${categoryId}&page=${servicePage}&size=${PAGE_SIZE}`
  );
  const partners = usePagedList(
    canSeePartners
      ? `/api/users/partners?mainServiceCategoryId=${categoryId}&page=${partnerPage}&size=${PAGE_SIZE}`
      : null
  );

  useEffect(() => {
    document.title = `${category?.name ?? 'Category'} | NexHome Admin`;
  }, [category]);

  const save = async (values) => {
    const saved = await authRequest('/api/catalog/categories/update', {
      method: 'PUT',
      body: { serviceCategoryId: category.serviceCategoryId, ...categoryBody(values) }
    });
    showToast(`${saved.name} was updated.`);
    setEditing(false);
    setReloadKey((key) => key + 1);
  };

  const crumbs = [
    { label: 'Service Management' },
    { label: 'Categories', to: '/admin/services/categories' },
    ...(category ? [{ label: category.publicId }] : [])
  ];

  if (error || !category) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} />
        {error ? (
          <div
            role="alert"
            className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
          >
            {error}
          </div>
        ) : (
          <div className="h-72 animate-pulse rounded-2xl border border-[var(--line)] bg-white" />
        )}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[92.5rem] space-y-5">
      <AdminPageHeader crumbs={crumbs} />

      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-start gap-6 px-6 pb-6 pt-7 sm:px-8">
          <span className="relative flex h-40 w-60 flex-shrink-0 overflow-hidden rounded-2xl border border-[var(--line)]">
            {category.iconUrl ? (
              <img src={category.iconUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <ImagePlaceholder />
            )}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="mb-0 text-2xl font-black leading-tight text-[var(--navy)]">{category.name}</h1>
              <span
                className={`rounded-full px-3 py-1 text-xs font-bold ${
                  category.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                }`}
              >
                {category.isActive ? 'Active' : 'Inactive'}
              </span>
            </div>
            {category.description && (
              <div className="mt-1.5 max-w-[52rem] text-[0.9375rem] leading-relaxed text-[var(--muted)]">
                {category.description}
              </div>
            )}
            <div className="mt-2.5 flex flex-wrap items-center gap-x-6 gap-y-1.5 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <IdCard size={15} className="text-[var(--blue)]" />
                Category ID: <span className="font-bold text-[var(--ink)]">{category.publicId}</span>
              </span>
              <span className="flex items-center gap-2">
                <ListOrdered size={15} className="text-[var(--blue)]" />
                Display order <span className="font-bold text-[var(--ink)]">{category.displayOrder}</span>
              </span>
              <span className="flex items-center gap-2">
                <CalendarDays size={15} className="text-[var(--blue)]" />
                Added on{' '}
                <span className="font-bold text-[var(--ink)]">
                  {splitApiDateTime(category.creationDate)?.date ?? '—'}
                </span>
              </span>
            </div>
          </div>
          {canEdit && (
            <button type="button" className={`${primaryButtonClass} self-start`} onClick={() => setEditing(true)}>
              <Pencil size={16} />
              Edit Category
            </button>
          )}
        </div>

        <div className="nh-scroll-hidden flex gap-4 overflow-x-auto px-6 pb-6 sm:px-8">
          <StatTile
            icon={Wrench}
            tone="bg-[#EDF6FD] text-[var(--blue)]"
            label="Services"
            value={services.loading ? null : formatNumber(services.data.totalElements)}
            note="Active and inactive"
          />
          <StatTile
            icon={Store}
            tone="bg-[#E9F6EF] text-[var(--green)]"
            label="Visible to Customers"
            value={visibleCount === null ? null : formatNumber(visibleCount)}
            note={category.isActive ? 'Active services' : 'None while the category is inactive'}
          />
          {canSeePartners && (
            <StatTile
              icon={Users}
              tone="bg-[#E8EEF6] text-[var(--navy)]"
              label="Partners"
              value={partners.loading ? null : formatNumber(partners.data.totalElements)}
              note="With this as their main service"
            />
          )}
        </div>

        <div
          className="nh-scroll-hidden mx-6 flex gap-8 overflow-x-auto whitespace-nowrap border-b border-[var(--line)] sm:mx-8"
          role="tablist"
        >
          {[
            { key: 'services', label: 'Services', icon: FolderTree, list: services },
            ...(canSeePartners ? [{ key: 'partners', label: 'Partners', icon: Users, list: partners }] : [])
          ].map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={tab === item.key}
                className={profileTabClass(tab === item.key)}
                onClick={() => setTab(item.key)}
              >
                <Icon size={16} />
                {item.label}
                <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] text-[var(--muted)]">
                  {item.list.loading ? '…' : formatNumber(item.list.data.totalElements)}
                </span>
              </button>
            );
          })}
        </div>

        <div className="px-6 py-7 sm:px-8">
          {tab === 'services' && (
            <TabList
              list={services}
              noun="services"
              empty="No services in this category yet."
              onPageChange={setServicePage}
            >
              <table className="w-full min-w-[44rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={headClass}>#</th>
                    <th className={headClass}>Service ID</th>
                    <th className={headClass}>Service Name</th>
                    <th className={headClass}>Pricing Type</th>
                    <th className={headClass}>Price</th>
                    <th className={headClass}>Commission</th>
                    <th className={headClass}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {services.data.content.map((service, index) => (
                    <tr key={service.serviceId} className="border-t border-[var(--line)] text-[var(--ink)]">
                      <td className="px-3 py-3 text-[var(--muted)]">
                        {services.data.page * services.data.size + index + 1}
                      </td>
                      <td className="whitespace-nowrap px-3 py-3">
                        <Link to={`/admin/services/${service.serviceId}`} className={linkClass}>
                          {service.publicId}
                        </Link>
                      </td>
                      <td className="px-3 py-3 font-bold">{service.name}</td>
                      <td className="whitespace-nowrap px-3 py-3">{label('PRICING_TYPE', service.pricingType)}</td>
                      <td className="whitespace-nowrap px-3 py-3">
                        <span className="font-bold">{formatPrice(service.discountPrice ?? service.basePrice)}</span>
                        {service.discountPrice !== null && (
                          <span className="ml-1.5 text-[0.75rem] text-[var(--muted)] line-through">
                            {formatPrice(service.basePrice)}
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-[var(--muted)]">
                        {formatCommission(service.platformCommissionType, service.platformCommissionValue) ?? '—'}
                      </td>
                      <td className="px-3 py-3">
                        <span
                          className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                            service.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                          }`}
                        >
                          {service.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TabList>
          )}

          {tab === 'partners' && canSeePartners && (
            <TabList
              list={partners}
              noun="partners"
              empty="No partner has this as their main service category yet."
              onPageChange={setPartnerPage}
            >
              <table className="w-full min-w-[52rem] text-sm">
                <thead className="bg-[var(--light)]">
                  <tr>
                    <th className={headClass}>#</th>
                    <th className={headClass}>Partner ID</th>
                    <th className={headClass}>Name</th>
                    <th className={headClass}>Mobile</th>
                    <th className={headClass}>City</th>
                    <th className={headClass}>Onboarding</th>
                    <th className={headClass}>Account</th>
                  </tr>
                </thead>
                <tbody>
                  {partners.data.content.map((partner, index) => {
                    const name = [partner.firstName, partner.lastName].filter(Boolean).join(' ');
                    return (
                      <tr key={partner.userId} className="border-t border-[var(--line)] text-[var(--ink)]">
                        <td className="px-3 py-3 text-[var(--muted)]">
                          {partners.data.page * partners.data.size + index + 1}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {partner.partnerProfileId ? (
                            <Link to={`/admin/users/partners/${partner.partnerProfileId}`} className={linkClass}>
                              {partner.publicId}
                            </Link>
                          ) : (
                            <span className="text-[var(--muted)]">—</span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          {name ? (
                            <span className="font-bold">{name}</span>
                          ) : (
                            <span className="text-[var(--muted)]">Not added yet</span>
                          )}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {partner.countryCode} {partner.mobileNumber}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">{partner.city ?? '—'}</td>
                        <td className="px-3 py-3">
                          <span
                            className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${onboardingBadgeClass(partner.onboardingStatus)}`}
                          >
                            {label('PARTNER_ONBOARDING_STATUS', partner.onboardingStatus)}
                          </span>
                        </td>
                        <td className="px-3 py-3">
                          <span
                            className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${statusBadgeClass(partner.accountStatus)}`}
                          >
                            {accountStatusLabel(partner.accountStatus)}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </TabList>
          )}
        </div>
      </section>

      {editing && canEdit && (
        <FormModal
          title="Edit Category"
          fields={categoryFields(true, category)}
          initial={categoryForm(category)}
          submitLabel="Save Changes"
          onSubmit={save}
          onClose={() => setEditing(false)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
