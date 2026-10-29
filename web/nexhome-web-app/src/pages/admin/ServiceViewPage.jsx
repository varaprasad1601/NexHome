import {
  CalendarClock,
  CalendarDays,
  Check,
  FileText,
  FolderTree,
  Home,
  IdCard,
  Images,
  History,
  Layers,
  ListChecks,
  MessageCircleQuestion,
  Pencil,
  Percent,
  Plug,
  Power,
  Star,
  Tag,
  UserRound,
  Wallet,
  Wrench,
  Zap
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';

import { API_BASE_URL } from '../../api/client';
import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import ServiceFaqs from '../../components/admin/catalog/ServiceFaqs';
import Toast from '../../components/admin/common/Toast';
import AuditSummary from '../../components/admin/profile/AuditSummary';
import { InfoRow, InfoSection, StatTile } from '../../components/admin/profile/ProfileParts';
import { profileTabClass } from '../../components/admin/profile/profileStyles';
import { primaryButtonClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatCommission, formatMinutes, formatPrice } from '../../data/catalogForms';
import useClientPaging from '../../hooks/useClientPaging';
import useLookupLabels from '../../hooks/useLookupLabels';
import { splitApiDateTime } from '../../utils/format';
import { cleanRichText, toRichText } from '../../utils/richText';

const LOOKUPS = ['SERVICE_TARGET', 'PRICING_TYPE', 'COMMISSION_TYPE'];
const VARIANTS_TITLE = { HOURLY: 'Hourly Packages', AREA: 'Home Sizes' };
const cellHead =
  'whitespace-nowrap px-3 py-3 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';

const yesNo = (allowed) => (allowed ? 'Allowed' : 'Not allowed');
const NO_ROWS = [];
/** What an activity entry says happened. */
const ACTIONS = {
  CREATED: { label: 'Created', tone: 'bg-[#E9F6EF] text-[var(--green-2)]' },
  ADDED: { label: 'Added', tone: 'bg-[#EDF6FD] text-[var(--blue)]' },
  UPDATED: { label: 'Updated', tone: 'bg-[#FFF7DE] text-[#8A6508]' }
};

/**
 * The service's own audit columns above its activity table: created on / by and last updated on / by (the service's
 * CREATED and UPDATED lines of the log; an update within seconds of the creation counts as part of it).
 */
function ServiceAudit({ activity }) {
  const created = activity.find((entry) => entry.subject === 'Service' && entry.action === 'CREATED');
  const updated = activity.find((entry) => entry.subject === 'Service' && entry.action === 'UPDATED') ?? created;
  const of = (entry) => entry && { at: entry.at, name: entry.userName };
  return (
    <div className="mb-5">
      <AuditSummary created={of(created)} updated={of(updated)} />
    </div>
  );
}

/** A price with the regular one struck through when there is a discount. */
function Price({ price, discount }) {
  if (price === null || price === undefined) return '—';
  return (
    <span className="whitespace-nowrap">
      <span className="font-black">{formatPrice(discount ?? price)}</span>
      {discount !== null && discount !== undefined && (
        <span className="ml-1.5 text-[0.8125rem] font-normal text-[var(--muted)] line-through">
          {formatPrice(price)}
        </span>
      )}
    </span>
  );
}

/** The cover's stand-in until a service has an image: a soft gradient with a simple room shape. */
function CoverPlaceholder() {
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

/**
 * Service Management > Services > a service (/admin/services/:serviceId): the read-only page, in the profile pages'
 * design. One card: the summary (a large cover image, name, status, service id, category, added on) with Edit
 * Service in its top right (CATALOG_MANAGE write, opening the form at /admin/services/:serviceId/edit), key figures
 * (price, pricing type, variants, commission), and tabs Overview (details with the commission, booking, the formatted
 * description), Pricing & Variants, Images, Requirements, FAQs (customer and partner) and, for CATALOG_MANAGE readers,
 * Activity Logs (paged, as the variants). Data from GET /api/catalog/services/{id} and the FAQ endpoints.
 */
export default function ServiceViewPage() {
  const { serviceId } = useParams();
  const { authRequest, can } = useAdminAuth();
  const canEdit = can('CATALOG_MANAGE', 'WRITE');
  const { label } = useLookupLabels(LOOKUPS);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('overview');
  const location = useLocation();
  const navigate = useNavigate();
  // The form hands over the result of a save
  const [toast, setToast] = useState(() => (location.state?.toast ? { ...location.state.toast, id: 'saved' } : null));
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const canReadFaqs = can('FAQ_MANAGE', 'READ');
  const [faqs, setFaqs] = useState([]);
  const [faqsKey, setFaqsKey] = useState(0);

  // Drop the handed-over toast from the history entry, so a reload does not show it again
  useEffect(() => {
    if (location.state?.toast) navigate(location.pathname, { replace: true, state: null });
    // Once, on arrival
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/catalog/services/${serviceId}`)
      .then((loaded) => !cancelled && setDetail(loaded))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [authRequest, serviceId]);

  // The service's FAQs: every one for FAQ_MANAGE readers, else the published ones; in display order
  useEffect(() => {
    let cancelled = false;
    const path = canReadFaqs ? `/api/faqs/all?serviceId=${serviceId}&size=100` : `/api/faqs?serviceId=${serviceId}`;
    authRequest(path)
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : data.content;
        setFaqs([...list].sort((a, b) => a.displayOrder - b.displayOrder || a.question.localeCompare(b.question)));
      })
      .catch(() => !cancelled && setFaqs([]));
    return () => {
      cancelled = true;
    };
  }, [authRequest, serviceId, canReadFaqs, faqsKey]);

  useEffect(() => {
    document.title = `${detail?.service.name ?? 'Service'} | NexHome Admin`;
  }, [detail]);

  const service = detail?.service;
  const crumbs = [
    { label: 'Service Management' },
    { label: 'Services', to: '/admin/services' },
    ...(service ? [{ label: service.publicId }] : [])
  ];
  // Paged in the browser (hooks run before the early returns below)
  const variantPaging = useClientPaging(detail?.variants ?? NO_ROWS);
  const activityPaging = useClientPaging(detail?.activity ?? NO_ROWS);

  if (error || !detail) {
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
          <div className="h-80 animate-pulse rounded-2xl border border-[var(--line)] bg-white" />
        )}
        <Toast toast={toast} onClose={closeToast} />
      </div>
    );
  }

  const { images, variants, requirements } = detail;
  const cover = images[0];
  const hourly = service.pricingType === 'HOURLY';
  const variantPriced = Boolean(VARIANTS_TITLE[service.pricingType]);
  const customerRequirements = requirements.filter((requirement) => requirement.requirementFor === 'CUSTOMER');
  const partnerRequirements = requirements.filter((requirement) => requirement.requirementFor === 'PARTNER');
  const description = cleanRichText(toRichText(service.description));
  const TargetIcon = service.serviceTarget === 'APPLIANCE' ? Plug : Home;
  const tabs = [
    { key: 'overview', label: 'Overview', icon: FileText },
    { key: 'pricing', label: 'Pricing & Variants', icon: Wallet, count: variantPriced ? variants.length : undefined },
    { key: 'images', label: 'Images', icon: Images, count: images.length },
    { key: 'requirements', label: 'Requirements', icon: ListChecks, count: requirements.length },
    { key: 'faqs', label: 'FAQs', icon: MessageCircleQuestion, count: faqs.length },
    ...(detail.activity
      ? [{ key: 'activity', label: 'Activity Logs', icon: History, count: detail.activity.length }]
      : [])
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={crumbs} />

      <section className="rounded-2xl border border-[var(--line)] bg-white shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
        <div className="flex flex-wrap items-start gap-6 px-6 pb-6 pt-7 sm:px-8">
          <span className="relative flex h-48 w-72 flex-shrink-0 overflow-hidden rounded-2xl border border-[var(--line)]">
            {cover ? (
              <img src={`${API_BASE_URL}${cover.imageUrl}`} alt="" className="h-full w-full object-cover" />
            ) : (
              <CoverPlaceholder />
            )}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="mb-0 text-2xl font-black leading-tight text-[var(--navy)]">{service.name}</h1>
              <span
                className={`rounded-full px-3 py-1 text-xs font-bold ${
                  service.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                }`}
              >
                {service.isActive ? 'Active' : 'Inactive'}
              </span>
            </div>
            {service.shortDescription && (
              <div className="mt-1.5 max-w-[48rem] text-[0.9375rem] text-[var(--muted)]">
                {service.shortDescription}
              </div>
            )}
            <div className="mt-2.5 flex flex-wrap items-center gap-x-6 gap-y-1.5 text-sm text-[var(--muted)]">
              <span className="flex items-center gap-2">
                <IdCard size={15} className="text-[var(--blue)]" />
                Service ID: <span className="font-bold text-[var(--ink)]">{service.publicId}</span>
              </span>
              <span className="flex items-center gap-2">
                <FolderTree size={15} className="text-[var(--blue)]" />
                {service.categoryName}
              </span>
              <span className="flex items-center gap-2">
                <CalendarDays size={15} className="text-[var(--blue)]" />
                Added on{' '}
                <span className="font-bold text-[var(--ink)]">
                  {splitApiDateTime(service.creationDate)?.date ?? '—'}
                </span>
              </span>
            </div>
          </div>
          {canEdit && (
            <Link to={`/admin/services/${serviceId}/edit`} className={`${primaryButtonClass} self-start no-underline`}>
              <Pencil size={16} />
              Edit Service
            </Link>
          )}
        </div>

        {/* Key figures, always one row (scrolling sideways on small screens) */}
        <div className="nh-scroll-hidden flex gap-4 overflow-x-auto px-6 pb-6 sm:px-8">
          <StatTile
            icon={Wallet}
            tone="bg-[#EDF6FD] text-[var(--blue)]"
            label="Base Price"
            value={formatPrice(service.discountPrice ?? service.basePrice)}
            note={service.discountPrice !== null ? `Discounted from ${formatPrice(service.basePrice)}` : 'No discount'}
          />
          <StatTile
            icon={Tag}
            tone="bg-[#E8EEF6] text-[var(--navy)]"
            label="Pricing Type"
            value={label('PRICING_TYPE', service.pricingType)}
            note={label('SERVICE_TARGET', service.serviceTarget)}
          />
          <StatTile
            icon={Layers}
            tone="bg-[#FFF7DE] text-[#B7860B]"
            label={VARIANTS_TITLE[service.pricingType] ?? 'Variants'}
            value={variantPriced ? variants.length : null}
            note={variantPriced ? `${variants.filter((variant) => variant.isActive).length} active` : 'Fixed price'}
          />
          <StatTile
            icon={Percent}
            tone="bg-[#E9F6EF] text-[var(--green)]"
            label="Commission"
            value={formatCommission(service.platformCommissionType, service.platformCommissionValue)}
            note={label('COMMISSION_TYPE', service.platformCommissionType)}
          />
        </div>

        <div
          className="nh-scroll-hidden mx-6 flex gap-8 overflow-x-auto whitespace-nowrap border-b border-[var(--line)] sm:mx-8"
          role="tablist"
        >
          {tabs.map((item) => {
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
                {item.count !== undefined && (
                  <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] text-[var(--muted)]">
                    {item.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="px-6 py-7 sm:px-8">
          {tab === 'overview' && (
            <div className="space-y-8">
              <div className="grid grid-cols-1 gap-x-12 gap-y-8 lg:grid-cols-2">
                <InfoSection title="Service Details">
                  <InfoRow icon={FolderTree} label="Category" value={service.categoryName} />
                  <InfoRow
                    icon={TargetIcon}
                    label="Service For"
                    value={label('SERVICE_TARGET', service.serviceTarget)}
                  />
                  <InfoRow icon={Tag} label="Pricing Type" value={label('PRICING_TYPE', service.pricingType)} />
                  <InfoRow
                    icon={Percent}
                    label="Platform Commission"
                    value={formatCommission(service.platformCommissionType, service.platformCommissionValue)}
                  />
                </InfoSection>
                <InfoSection title="Booking">
                  <InfoRow icon={Zap} label="Instant Booking" value={yesNo(service.isInstantBookingAllowed)} />
                  <InfoRow
                    icon={CalendarClock}
                    label="Scheduled Booking"
                    value={yesNo(service.isScheduledBookingAllowed)}
                  />
                  <InfoRow
                    icon={Power}
                    label="Status"
                    value={service.isActive ? 'Active: shown to customers' : 'Inactive: hidden from customers'}
                  />
                </InfoSection>
              </div>
              <InfoSection title="Detailed Description" divided={false}>
                {description ? (
                  <div
                    className="nh-rich-text mt-2 rounded-xl border border-[var(--line)] bg-[#FAFCFE] px-5 py-4 text-[0.9375rem] leading-relaxed text-[var(--ink)]"
                    // Cleaned to the editor's tags here and by the backend
                    dangerouslySetInnerHTML={{ __html: description }}
                  />
                ) : (
                  <p className="mt-2 text-sm text-[var(--muted)]">No detailed description yet.</p>
                )}
              </InfoSection>
            </div>
          )}

          {tab === 'pricing' && (
            <div className="space-y-8">
              <InfoSection title="Pricing">
                <div className="grid grid-cols-1 gap-x-12 sm:grid-cols-2">
                  <InfoRow icon={Wallet} label="Base Price" value={formatPrice(service.basePrice)} />
                  <InfoRow
                    icon={Tag}
                    label="Discount Price"
                    value={service.discountPrice !== null ? formatPrice(service.discountPrice) : 'No discount'}
                  />
                  <InfoRow icon={Wallet} label="Minimum Charge" value={formatPrice(service.minimumCharge)} />
                </div>
              </InfoSection>
              {variantPriced && (
                <InfoSection title={VARIANTS_TITLE[service.pricingType]} divided={false}>
                  {variants.length === 0 ? (
                    <p className="mt-2 text-sm text-[var(--muted)]">None added yet.</p>
                  ) : (
                    <div className="mt-2 overflow-x-auto rounded-xl border border-[var(--line)]">
                      <table className="w-full min-w-[40rem] text-sm">
                        <thead className="bg-[var(--light)]">
                          <tr>
                            <th className={cellHead}>#</th>
                            <th className={cellHead}>Name</th>
                            {hourly && <th className={cellHead}>Hours</th>}
                            <th className={cellHead}>Price</th>
                            <th className={cellHead}>Description</th>
                            <th className={cellHead}>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {variantPaging.pageRows.map((variant, index) => (
                            <tr
                              key={variant.serviceVariantId}
                              className="border-t border-[var(--line)] text-[var(--ink)]"
                            >
                              <td className="px-3 py-3 text-[var(--muted)]">
                                {variantPaging.pageData.page * variantPaging.pageData.size + index + 1}
                              </td>
                              <td className="px-3 py-3 font-bold">{variant.name}</td>
                              {hourly && <td className="px-3 py-3">{formatMinutes(variant.durationMinutes) ?? '—'}</td>}
                              <td className="px-3 py-3">
                                <Price price={variant.priceOverride} discount={variant.discountPrice} />
                              </td>
                              <td className="px-3 py-3 text-[var(--muted)]">{variant.description ?? '—'}</td>
                              <td className="px-3 py-3">
                                <span
                                  className={`rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                                    variant.isActive
                                      ? 'bg-[#E9F6EF] text-[var(--green-2)]'
                                      : 'bg-[#FFF7DE] text-[#8A6508]'
                                  }`}
                                >
                                  {variant.isActive ? 'Active' : 'Inactive'}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                  {variants.length > 0 && (
                    <Pagination
                      pageData={variantPaging.pageData}
                      noun="variants"
                      onPageChange={variantPaging.setPage}
                    />
                  )}
                </InfoSection>
              )}
            </div>
          )}

          {tab === 'images' &&
            (images.length === 0 ? (
              <p className="text-sm text-[var(--muted)]">No images yet.</p>
            ) : (
              <ul className="grid list-none grid-cols-2 gap-4 p-0 sm:grid-cols-3 lg:grid-cols-5">
                {images.map((image, index) => (
                  <li
                    key={image.serviceImageId}
                    className="relative aspect-square overflow-hidden rounded-xl border border-[var(--line)] bg-[var(--light)]"
                  >
                    <a
                      href={`${API_BASE_URL}${image.imageUrl}`}
                      target="_blank"
                      rel="noreferrer"
                      title={image.fileName}
                    >
                      <img
                        src={`${API_BASE_URL}${image.imageUrl}`}
                        alt={image.fileName}
                        className="h-full w-full object-cover"
                      />
                    </a>
                    {index === 0 && (
                      <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-[var(--navy)]/85 px-2 py-0.5 text-[0.6875rem] font-bold text-white">
                        <Star size={10} fill="currentColor" strokeWidth={0} />
                        Cover
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            ))}

          {tab === 'activity' && <ServiceAudit activity={detail.activity} />}
          {tab === 'activity' &&
            (detail.activity.length === 0 ? (
              <p className="text-sm text-[var(--muted)]">No activity yet.</p>
            ) : (
              <>
                <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
                  <table className="w-full min-w-[44rem] text-sm">
                    <thead className="bg-[var(--light)]">
                      <tr>
                        <th className={cellHead}>Date &amp; Time</th>
                        <th className={cellHead}>Action</th>
                        <th className={cellHead}>What</th>
                        <th className={cellHead}>By</th>
                      </tr>
                    </thead>
                    <tbody>
                      {activityPaging.pageRows.map((entry, index) => {
                        const when = splitApiDateTime(entry.at);
                        const action = ACTIONS[entry.action] ?? { label: entry.action, tone: 'bg-[var(--light)]' };
                        return (
                          <tr
                            key={`${entry.at}-${entry.subject}-${index}`}
                            className="border-t border-[var(--line)] text-[var(--ink)]"
                          >
                            <td className="whitespace-nowrap px-3 py-3">
                              <span className="font-bold">{when?.date ?? '—'}</span>
                              <span className="ml-2 text-[var(--muted)]">{when?.time}</span>
                            </td>
                            <td className="px-3 py-3">
                              <span className={`rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${action.tone}`}>
                                {action.label}
                              </span>
                            </td>
                            <td className="px-3 py-3">{entry.subject}</td>
                            <td className="whitespace-nowrap px-3 py-3">
                              {entry.userName ?? <span className="text-[var(--muted)]">Unknown</span>}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <Pagination pageData={activityPaging.pageData} noun="entries" onPageChange={activityPaging.setPage} />
                <p className="mt-3 text-xs text-[var(--muted)]">
                  From when each record was created and last changed; earlier edits of the same record are not kept.
                </p>
              </>
            ))}

          {tab === 'faqs' && (
            <ServiceFaqs
              serviceId={service.serviceId}
              faqs={faqs}
              canEdit={can('FAQ_MANAGE', 'WRITE')}
              canDelete={can('FAQ_MANAGE', 'DELETE')}
              authRequest={authRequest}
              onSaved={(message) => {
                showToast(message);
                setFaqsKey((key) => key + 1);
              }}
            />
          )}

          {tab === 'requirements' && (
            <div className="grid grid-cols-1 gap-x-12 gap-y-8 lg:grid-cols-2">
              {[
                { title: 'Customer Requirements', icon: UserRound, list: customerRequirements, partner: false },
                { title: 'Partner Requirements', icon: Wrench, list: partnerRequirements, partner: true }
              ].map((side) => {
                const Icon = side.icon;
                return (
                  <section key={side.title}>
                    <h3 className="mb-4 flex items-center gap-2.5 text-[1.0625rem] font-black text-[var(--navy)]">
                      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
                        <Icon size={17} />
                      </span>
                      {side.title}
                    </h3>
                    {side.list.length === 0 ? (
                      <p className="text-sm text-[var(--muted)]">None.</p>
                    ) : (
                      <ul className="m-0 grid list-none gap-3 p-0">
                        {side.list.map((requirement) => (
                          <li
                            key={requirement.serviceRequirementId}
                            className="flex items-center gap-2.5 text-[0.9375rem] text-[var(--ink)]"
                          >
                            <span
                              className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full ${
                                side.partner ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#EDF6FD] text-[var(--blue)]'
                              }`}
                            >
                              <Check size={12} strokeWidth={3} />
                            </span>
                            {requirement.description}
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                );
              })}
            </div>
          )}
        </div>
      </section>
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
