import { ArrowLeft, CalendarClock, Eye, Plus, Power, Trash2, Zap } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { API_BASE_URL } from '../../api/client';
import AdminPageHeader from '../../components/admin/AdminPageHeader';
import ServiceImagesField from '../../components/admin/catalog/ServiceImagesField';
import InfoTip from '../../components/admin/common/InfoTip';
import MultiSelect from '../../components/admin/common/MultiSelect';
import Pagination from '../../components/admin/common/Pagination';
import RichTextEditor from '../../components/admin/common/RichTextEditor';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import Toggle from '../../components/admin/common/Toggle';
import {
  cardClass,
  fieldClass,
  primaryButtonClass,
  resetButtonClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import {
  allowedPricingTypes,
  DESCRIPTION_LIMIT,
  emptyVariant,
  PRICING_COPY,
  serviceFormOf,
  serviceRequest,
  validateServiceForm,
  VARIANT_PRICED,
  withPricingType
} from '../../data/serviceForm';
import useClientPaging from '../../hooks/useClientPaging';
import useLookupLabels from '../../hooks/useLookupLabels';
import { richTextLength } from '../../utils/richText';

const LOOKUPS = ['SERVICE_TARGET', 'PRICING_TYPE', 'COMMISSION_TYPE', 'CUSTOMER_REQUIREMENT', 'PARTNER_REQUIREMENT'];
const SHORT_LIMIT = 300;

const VARIANTS_PER_PAGE = 10;

const lookupOptions = (values) => (values ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }));

/** A numbered card of the form, e.g. "1. Basic Information". */
function Section({ number, title, description, children }) {
  return (
    <section className={cardClass}>
      <div className="mb-5">
        <h2 className="mb-0.5 text-[1.0625rem] font-black text-[var(--ink)]">
          <span className="mr-2 text-[var(--blue)]">{number}.</span>
          {title}
        </h2>
        <div className="text-[0.8125rem] text-[var(--muted)]">{description}</div>
      </div>
      {children}
    </section>
  );
}

/**
 * A labelled field: the explanation (`hint`) sits behind an info icon beside the label; below the control only an error
 * and an optional character counter show.
 */
function Field({ label, htmlFor, required, hint, error, counter, children }) {
  return (
    <div>
      <div className="mb-1.5 flex items-center gap-1.5">
        <label htmlFor={htmlFor} className="text-[0.8125rem] font-bold text-[var(--ink)]">
          {label}
          {required && <span className="text-[#E53E3E]"> *</span>}
        </label>
        {hint && <InfoTip text={hint} />}
      </div>
      {children}
      {(error || counter) && (
        <div className="mt-1 flex justify-between gap-3 text-xs">
          <span className="font-bold text-[#C53030]">{error}</span>
          {counter && <span className="flex-shrink-0 text-[var(--muted)]">{counter}</span>}
        </div>
      )}
    </div>
  );
}

/** An amount input with ₹ in front (or % behind for a percentage). */
function AmountInput({ id, value, onChange, error, disabled, percent = false, placeholder = '0', small = false }) {
  return (
    <div className="relative">
      {!percent && (
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm font-bold text-[var(--muted)]">
          ₹
        </span>
      )}
      <input
        id={id}
        type="number"
        min="0"
        step="0.01"
        inputMode="decimal"
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className={`${fieldClass(error)} ${percent ? 'pr-8' : 'pl-7'} ${small ? '!py-2' : ''}`}
      />
      {percent && (
        <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm font-bold text-[var(--muted)]">
          %
        </span>
      )}
    </div>
  );
}

/** One of the booking switches: an icon, what it does, and the switch. */
function ToggleCard({ icon: Icon, title, text, checked, onChange, disabled }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-[var(--line)] bg-[#FAFCFE] p-4">
      <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
        <Icon size={17} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-bold text-[var(--ink)]">{title}</div>
        <div className="mt-0.5 text-xs text-[var(--muted)]">{text}</div>
      </div>
      <Toggle checked={checked} onChange={onChange} disabled={disabled} label={title} />
    </div>
  );
}

const variantHeadClass =
  'whitespace-nowrap px-2 py-2.5 text-left text-[0.6875rem] font-bold uppercase tracking-[0.06em] text-[var(--muted)]';
const smallInput = (error) => `${fieldClass(error)} !py-2`;

/**
 * Service Management > Services > Add Service (/admin/services/new) and > a service > Edit
 * (/admin/services/:serviceId/edit, from Edit Service on ServiceViewPage), one form in numbered sections: 1. basic
 * information (name, category, short and detailed description in 60% of the width, the images (stored in the database, at most 5) in the other 40%);
 * 2. pricing: what the service is for (SERVICE_TARGET: an appliance allows fixed or hourly pricing, a home also area
 * based), the pricing type, base and discount price, and the variants (hourly packages or home
 * sizes; a fixed price has none; changing the type clears its prices and variants); 3. commission and booking
 * (instant, scheduled, status) side by side; 4. requirements for the customer and the partner (multi-selects of the CUSTOMER_REQUIREMENT / PARTNER_REQUIREMENT lookups).
 *
 * Saving sends one create / update request (the variants and requirements included), uploads new images, deletes
 * removed ones and opens the service's page (ServiceViewPage), which shows the result. Without CATALOG_MANAGE write
 * the page is view only; removing a stored image needs delete.
 */
export default function ServiceEditorPage() {
  const { serviceId } = useParams();
  const isNew = !serviceId;
  const navigate = useNavigate();
  const { authRequest, can } = useAdminAuth();
  const canEdit = can('CATALOG_MANAGE', 'WRITE');
  const canDelete = can('CATALOG_MANAGE', 'DELETE');
  const { values: lookups } = useLookupLabels(LOOKUPS);
  const [detail, setDetail] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(() => serviceFormOf(null));
  // The variant table, 10 rows a page like every table
  const variantPaging = useClientPaging(form.variants, VARIANTS_PER_PAGE);
  const [images, setImages] = useState([]);
  const [removedImageIds, setRemovedImageIds] = useState([]);
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);
  const readOnly = !canEdit;

  useEffect(() => {
    document.title = `${isNew ? 'Add Service' : (detail?.service.name ?? 'Service')} | NexHome Admin`;
  }, [isNew, detail]);

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/catalog/categories?includeInactive=true')
      .then((list) => !cancelled && setCategories(list))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  useEffect(() => {
    if (isNew) return undefined;
    let cancelled = false;
    authRequest(`/api/catalog/services/${serviceId}`)
      .then((loaded) => {
        if (cancelled) return;
        setDetail(loaded);
        setForm(serviceFormOf(loaded));
        setImages(
          loaded.images.map((image) => ({
            key: `stored-${image.serviceImageId}`,
            serviceImageId: image.serviceImageId,
            url: `${API_BASE_URL}${image.imageUrl}`,
            name: image.fileName,
            isNew: false
          }))
        );
        setRemovedImageIds([]);
        setSubmitted(false);
        setLoadError('');
      })
      .catch((err) => !cancelled && setLoadError(err.message));
    return () => {
      cancelled = true;
    };
  }, [authRequest, isNew, serviceId]);

  // Previews of picked files are object URLs: free them when they leave the list or the page closes
  useEffect(
    () => () => images.forEach((image) => image.isNew && URL.revokeObjectURL(image.url)),
    // Only on unmount; removals free their own URL
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  const targets = lookups.SERVICE_TARGET ?? [];
  const target = targets.find((value) => value.valueCode === form.serviceTarget);
  const pricingOptions = form.serviceTarget ? allowedPricingTypes(target, lookups.PRICING_TYPE ?? []) : [];
  const allowedCodes = pricingOptions.map((option) => option.valueCode);
  const errors = useMemo(
    () => (submitted ? validateServiceForm(form, allowedCodes) : {}),
    // allowedCodes follows form.serviceTarget and the lookups
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [submitted, form, lookups]
  );
  const copy = PRICING_COPY[form.pricingType] ?? PRICING_COPY.FIXED;
  const hourly = form.pricingType === 'HOURLY';

  const set = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));
  const setTarget = (value) =>
    setForm((current) => {
      const allowed = allowedPricingTypes(
        targets.find((option) => option.valueCode === value),
        lookups.PRICING_TYPE ?? []
      ).map((option) => option.valueCode);
      // A pricing type the new target does not allow is cleared, with what was entered for it
      return {
        ...withPricingType(current, allowed.includes(current.pricingType) ? current.pricingType : ''),
        serviceTarget: value
      };
    });
  const setPricingType = (value) => setForm((current) => withPricingType(current, value));
  const setVariant = (key, field, value) =>
    setForm((current) => ({
      ...current,
      variants: current.variants.map((variant) => (variant.key === key ? { ...variant, [field]: value } : variant))
    }));
  const addVariant = () => {
    setForm((current) => ({ ...current, variants: [...current.variants, emptyVariant()] }));
    // Show the page the new row lands on
    variantPaging.setPage(Math.floor(form.variants.length / VARIANTS_PER_PAGE));
  };
  const removeVariant = (key) =>
    setForm((current) => ({ ...current, variants: current.variants.filter((variant) => variant.key !== key) }));

  const addImages = (files) =>
    setImages((current) => [
      ...current,
      ...files.map((file, index) => ({
        key: `new-${Date.now()}-${index}`,
        file,
        url: URL.createObjectURL(file),
        name: file.name,
        isNew: true
      }))
    ]);
  const removeImage = (key) => {
    const image = images.find((item) => item.key === key);
    if (!image) return;
    if (image.isNew) URL.revokeObjectURL(image.url);
    else setRemovedImageIds((ids) => [...ids, image.serviceImageId]);
    setImages((current) => current.filter((item) => item.key !== key));
  };

  // The cover is the first image: move the chosen one to the front (saved as the images' order)
  const setCover = (key) =>
    setImages((current) => [
      ...current.filter((item) => item.key === key),
      ...current.filter((item) => item.key !== key)
    ]);

  const categoryOptions = categories
    .filter((category) => category.isActive || String(category.serviceCategoryId) === form.categoryId)
    .map((category) => ({
      value: String(category.serviceCategoryId),
      label: category.isActive ? category.name : `${category.name} (inactive)`
    }));
  const requirementOptions = (type, chosen) => {
    const options = lookupOptions(lookups[type]);
    // A chosen option that was switched off since still shows, by its code
    chosen.forEach((code) => {
      if (!options.some((option) => option.value === code)) options.push({ value: code, label: code });
    });
    return options;
  };

  const save = async () => {
    setSubmitted(true);
    const problems = validateServiceForm(form, allowedCodes);
    if (Object.keys(problems).length > 0) {
      showToast('Please fix the highlighted fields.', 'error');
      // A variant error may be on another page of the table: show the first one
      const firstBad = form.variants.findIndex((variant) =>
        Object.keys(problems).some((key) => key.startsWith(`variants.${variant.key}.`))
      );
      if (firstBad >= 0) variantPaging.setPage(Math.floor(firstBad / VARIANTS_PER_PAGE));
      return;
    }
    setSaving(true);
    try {
      const body = serviceRequest(form);
      const saved = isNew
        ? await authRequest('/api/catalog/services/create', { method: 'POST', body })
        : await authRequest('/api/catalog/services/update', {
            method: 'PUT',
            body: { serviceId: Number(serviceId), ...body }
          });

      // Then the images: removals first, so the new ones fit within the limit
      const imageProblems = [];
      for (const id of removedImageIds) {
        try {
          await authRequest(`/api/catalog/services/images/delete/${id}`, { method: 'DELETE' });
        } catch (err) {
          imageProblems.push(err.message);
        }
      }
      // Uploads in the page's order, collecting every image's id for the order (the first is the cover)
      const order = [];
      for (const image of images) {
        if (!image.isNew) {
          order.push(image.serviceImageId);
          continue;
        }
        const data = new FormData();
        data.append('serviceId', String(saved.serviceId));
        data.append('file', image.file);
        try {
          const uploaded = await authRequest('/api/catalog/services/images/add', { method: 'POST', body: data });
          order.push(uploaded.serviceImageId);
        } catch (err) {
          imageProblems.push(`${image.name}: ${err.message}`);
        }
      }
      if (order.length > 1) {
        try {
          await authRequest('/api/catalog/services/images/order', {
            method: 'PUT',
            body: { serviceId: saved.serviceId, serviceImageIds: order }
          });
        } catch (err) {
          imageProblems.push(`the cover image: ${err.message}`);
        }
      }
      const result = imageProblems.length
        ? { message: `${saved.name} was saved, but not every image: ${imageProblems[0]}`, tone: 'error' }
        : { message: `${saved.name} was ${isNew ? 'created' : 'updated'}.`, tone: 'success' };

      // To the saved service's page, which shows the result
      images.forEach((image) => image.isNew && URL.revokeObjectURL(image.url));
      navigate(`/admin/services/${saved.serviceId}`, { state: { toast: result } });
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const service = detail?.service;
  const loading = !isNew && !detail && !loadError;
  const crumbs = [
    { label: 'Service Management' },
    { label: 'Services', to: '/admin/services' },
    ...(isNew
      ? [{ label: 'Add Service' }]
      : service
        ? [{ label: service.publicId, to: `/admin/services/${serviceId}` }, { label: 'Edit' }]
        : [])
  ];
  // Editing returns to the service's page; adding to the list
  const backTo = isNew ? '/admin/services' : `/admin/services/${serviceId}`;
  const backButton = (
    <Link to={backTo} className={`${resetButtonClass} no-underline`}>
      <ArrowLeft size={16} />
      Back
    </Link>
  );

  if (isNew && !canEdit) {
    return (
      <div className="mx-auto max-w-[92.5rem]">
        <AdminPageHeader crumbs={crumbs} title="Add New Service" actions={backButton} />
        <div className={`${cardClass} text-sm text-[var(--muted)]`}>
          You can view services, but adding them needs write access to the service catalogue.
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={crumbs}
        title={isNew ? 'Add New Service' : service?.name}
        description={
          isNew
            ? 'Create a service with its pricing, variants and the details customers and partners need.'
            : service && `${service.categoryName} · added ${service.creationDate.split(' ').slice(1, 4).join(' ')}`
        }
        actions={
          <div className="flex items-center gap-3">
            {service && (
              <span
                className={`rounded-full px-3 py-1 text-[0.8125rem] font-bold ${
                  service.isActive ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                }`}
              >
                {service.isActive ? 'Active' : 'Inactive'}
              </span>
            )}
            {backButton}
          </div>
        }
      />

      {loadError && (
        <div
          role="alert"
          className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
        >
          {loadError}
        </div>
      )}
      {loading && (
        <div className="space-y-5">
          {[0, 1, 2].map((i) => (
            <div key={i} className={`${cardClass} h-48 animate-pulse`} />
          ))}
        </div>
      )}

      {!loading && !loadError && (
        <div className="space-y-5">
          {readOnly && (
            <div className="flex items-center gap-2 rounded-xl border border-[#C7E3FC] bg-[#EDF6FD] px-4 py-3 text-sm font-bold text-[var(--blue)]">
              <Eye size={16} />
              View only: changing services needs write access to the service catalogue.
            </div>
          )}

          <Section number={1} title="Basic Information" description="What the service is, as customers will see it.">
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
              <div className="space-y-4 lg:col-span-3">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Field label="Service Name" htmlFor="service-name" required error={errors.name}>
                    <input
                      id="service-name"
                      value={form.name}
                      maxLength={150}
                      disabled={readOnly}
                      placeholder="e.g. Home Cleaning"
                      onChange={(e) => set('name')(e.target.value)}
                      className={fieldClass(errors.name)}
                    />
                  </Field>
                  <Field label="Category" htmlFor="service-category" required error={errors.categoryId}>
                    <Select
                      id="service-category"
                      ariaLabel="Category"
                      value={form.categoryId}
                      onChange={set('categoryId')}
                      options={categoryOptions}
                      placeholder="Select a category"
                      error={Boolean(errors.categoryId)}
                      disabled={readOnly}
                    />
                  </Field>
                </div>
                <Field
                  label="Short Description"
                  htmlFor="service-short"
                  required
                  error={errors.shortDescription}
                  hint="One or two lines for lists and cards."
                  counter={`${form.shortDescription.length}/${SHORT_LIMIT}`}
                >
                  <textarea
                    id="service-short"
                    rows={3}
                    maxLength={SHORT_LIMIT}
                    value={form.shortDescription}
                    disabled={readOnly}
                    placeholder="e.g. Professional home cleaning with trained staff and quality products."
                    onChange={(e) => set('shortDescription')(e.target.value)}
                    className={`${fieldClass(errors.shortDescription)} resize-y`}
                  />
                </Field>
                <Field
                  label="Detailed Description"
                  htmlFor="service-description"
                  error={errors.description}
                  hint="What is included, how it is done and anything customers should know."
                  counter={`${richTextLength(form.description)}/${DESCRIPTION_LIMIT}`}
                >
                  <RichTextEditor
                    id="service-description"
                    value={form.description}
                    onChange={set('description')}
                    disabled={readOnly}
                    error={Boolean(errors.description)}
                    placeholder="e.g. Complete home cleaning including dusting, mopping, bathroom and kitchen cleaning."
                  />
                </Field>
              </div>
              <div className="lg:col-span-2">
                <div className="mb-1.5 text-[0.8125rem] font-bold text-[var(--ink)]">Service Images</div>
                <ServiceImagesField
                  images={images}
                  onAdd={addImages}
                  onRemove={removeImage}
                  onSetCover={setCover}
                  canRemoveStored={canDelete}
                  disabled={readOnly}
                />
              </div>
            </div>
          </Section>

          <Section
            number={2}
            title="Pricing & Variants"
            description="What the service is for decides how it can be priced."
          >
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
              <Field
                label="Service For"
                htmlFor="service-target"
                required
                error={errors.serviceTarget}
                hint="An appliance or device, or a home or property. It decides the pricing types you can choose."
              >
                <Select
                  id="service-target"
                  ariaLabel="Service for"
                  value={form.serviceTarget}
                  onChange={setTarget}
                  options={lookupOptions(targets)}
                  placeholder="What is serviced?"
                  error={Boolean(errors.serviceTarget)}
                  disabled={readOnly}
                />
              </Field>
              <Field
                label="Pricing Type"
                htmlFor="service-pricing"
                required
                error={errors.pricingType}
                hint="Choose Service For first. An appliance can be priced fixed or hourly, a home also by area."
              >
                <Select
                  id="service-pricing"
                  ariaLabel="Pricing type"
                  value={form.pricingType}
                  onChange={setPricingType}
                  options={lookupOptions(pricingOptions)}
                  placeholder={form.serviceTarget ? 'Select pricing' : 'Choose Service For first'}
                  error={Boolean(errors.pricingType)}
                  disabled={readOnly || !form.serviceTarget}
                />
              </Field>
              <Field
                label="Base Price"
                htmlFor="service-base-price"
                required
                error={errors.basePrice}
                hint={form.pricingType ? copy.basePriceHint : 'The price customers pay.'}
              >
                <AmountInput
                  id="service-base-price"
                  value={form.basePrice}
                  onChange={set('basePrice')}
                  error={errors.basePrice}
                  disabled={readOnly}
                />
              </Field>
              <Field
                label="Discount Price"
                htmlFor="service-discount"
                error={errors.discountPrice}
                hint="Optional: what customers pay instead."
              >
                <AmountInput
                  id="service-discount"
                  value={form.discountPrice}
                  onChange={set('discountPrice')}
                  error={errors.discountPrice}
                  disabled={readOnly}
                  placeholder="No discount"
                />
              </Field>
            </div>

            {VARIANT_PRICED.includes(form.pricingType) && (
              <div className="mt-6 border-t border-[var(--line)] pt-5">
                <div className="mb-3">
                  <div className="text-sm font-black text-[var(--ink)]">{copy.variantsTitle}</div>
                  <div className="text-xs text-[var(--muted)]">{copy.variantsHint}</div>
                </div>
                {form.variants.length > 0 && (
                  <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
                    <table className="w-full min-w-[52rem] text-sm">
                      <thead className="bg-[var(--light)]">
                        <tr>
                          <th className={`${variantHeadClass} w-10 pl-3`}>#</th>
                          <th className={variantHeadClass}>Variant Name</th>
                          {hourly && <th className={`${variantHeadClass} w-[6.5rem]`}>Hours</th>}
                          <th className={`${variantHeadClass} w-[8.5rem]`}>Price (₹)</th>
                          <th className={`${variantHeadClass} w-[8.5rem]`}>Discount Price (₹)</th>
                          <th className={variantHeadClass}>Description</th>
                          <th className={`${variantHeadClass} w-16`}>Status</th>
                          {!readOnly && <th className={`${variantHeadClass} w-12 pr-3 text-right`}>Remove</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {variantPaging.pageRows.map((variant, pageIndex) => {
                          const index = variantPaging.pageData.page * VARIANTS_PER_PAGE + pageIndex;
                          const at = (field) => errors[`variants.${variant.key}.${field}`];
                          return (
                            <tr key={variant.key} className="border-t border-[var(--line)] align-top">
                              <td className="px-2 py-2.5 pl-3 text-[var(--muted)]">{index + 1}</td>
                              <td className="px-2 py-1.5">
                                <input
                                  aria-label={`Variant ${index + 1} name`}
                                  value={variant.name}
                                  maxLength={150}
                                  disabled={readOnly}
                                  placeholder={copy.namePlaceholder}
                                  onChange={(e) => setVariant(variant.key, 'name', e.target.value)}
                                  className={smallInput(at('name'))}
                                />
                              </td>
                              {hourly && (
                                <td className="px-2 py-1.5">
                                  <input
                                    aria-label={`Variant ${index + 1} hours`}
                                    type="number"
                                    min="0.5"
                                    step="0.5"
                                    value={variant.hours}
                                    disabled={readOnly}
                                    placeholder="2"
                                    onChange={(e) => setVariant(variant.key, 'hours', e.target.value)}
                                    className={smallInput(at('hours'))}
                                  />
                                </td>
                              )}
                              <td className="px-2 py-1.5">
                                <AmountInput
                                  value={variant.price}
                                  onChange={(value) => setVariant(variant.key, 'price', value)}
                                  error={at('price')}
                                  disabled={readOnly}
                                  small
                                />
                              </td>
                              <td className="px-2 py-1.5">
                                <AmountInput
                                  value={variant.discountPrice}
                                  onChange={(value) => setVariant(variant.key, 'discountPrice', value)}
                                  error={at('discountPrice')}
                                  disabled={readOnly}
                                  placeholder="—"
                                  small
                                />
                                {at('discountPrice') && (
                                  <div className="mt-0.5 text-[0.6875rem] font-bold text-[#C53030]">
                                    {at('discountPrice')}
                                  </div>
                                )}
                              </td>
                              <td className="px-2 py-1.5">
                                <input
                                  aria-label={`Variant ${index + 1} description`}
                                  value={variant.description}
                                  maxLength={500}
                                  disabled={readOnly}
                                  placeholder="e.g. Ideal for small homes"
                                  onChange={(e) => setVariant(variant.key, 'description', e.target.value)}
                                  className={smallInput(false)}
                                />
                              </td>
                              <td className="px-2 py-3">
                                <Toggle
                                  checked={variant.isActive}
                                  onChange={(value) => setVariant(variant.key, 'isActive', value)}
                                  disabled={readOnly}
                                  label={`Variant ${index + 1} active`}
                                />
                              </td>
                              {!readOnly && (
                                <td className="px-2 py-2 pr-3 text-right">
                                  <button
                                    type="button"
                                    onClick={() => removeVariant(variant.key)}
                                    aria-label={`Remove variant ${index + 1}`}
                                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-[#E53E3E] hover:bg-[#FDECEC]"
                                  >
                                    <Trash2 size={15} />
                                  </button>
                                </td>
                              )}
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
                {form.variants.length > 0 && (
                  <Pagination pageData={variantPaging.pageData} noun="variants" onPageChange={variantPaging.setPage} />
                )}
                {form.variants.length === 0 && readOnly && <p className="text-sm text-[var(--muted)]">No variants.</p>}
                {!readOnly && (
                  <button
                    type="button"
                    onClick={addVariant}
                    className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-[#EDF6FD] px-3.5 py-2 text-[0.8125rem] font-bold text-[var(--blue)] hover:bg-[#DCEEFC]"
                  >
                    <Plus size={15} strokeWidth={2.6} />
                    {copy.addLabel}
                  </button>
                )}
              </div>
            )}
          </Section>

          <Section
            number={3}
            title="Commission & Booking"
            description="What NexHome keeps from each booking, and how customers can book the service."
          >
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2 lg:gap-0">
              <div className="lg:pr-6">
                <div className="mb-3 text-sm font-black text-[var(--ink)]">Commission</div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <Field
                    label="Commission Type"
                    htmlFor="service-commission-type"
                    required
                    error={errors.platformCommissionType}
                  >
                    <Select
                      id="service-commission-type"
                      ariaLabel="Commission type"
                      value={form.platformCommissionType}
                      onChange={set('platformCommissionType')}
                      options={lookupOptions(lookups.COMMISSION_TYPE)}
                      error={Boolean(errors.platformCommissionType)}
                      disabled={readOnly}
                    />
                  </Field>
                  <Field
                    label={
                      form.platformCommissionType === 'PERCENTAGE' ? 'Commission (%)' : 'Commission (₹ per booking)'
                    }
                    htmlFor="service-commission-value"
                    required
                    error={errors.platformCommissionValue}
                    hint={
                      form.platformCommissionType === 'PERCENTAGE'
                        ? 'A share of the booking amount, up to 100%.'
                        : 'A fixed amount NexHome keeps from each booking.'
                    }
                  >
                    <AmountInput
                      id="service-commission-value"
                      value={form.platformCommissionValue}
                      onChange={set('platformCommissionValue')}
                      error={errors.platformCommissionValue}
                      disabled={readOnly}
                      percent={form.platformCommissionType === 'PERCENTAGE'}
                    />
                  </Field>
                </div>
              </div>
              <div className="border-t border-[var(--line)] pt-6 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
                <div className="mb-3 text-sm font-black text-[var(--ink)]">Booking</div>
                <div className="space-y-3">
                  <ToggleCard
                    icon={Zap}
                    title="Instant Booking"
                    text="Customers can book for right now."
                    checked={form.isInstantBookingAllowed}
                    onChange={set('isInstantBookingAllowed')}
                    disabled={readOnly}
                  />
                  <ToggleCard
                    icon={CalendarClock}
                    title="Scheduled Booking"
                    text="Customers pick a date and time."
                    checked={form.isScheduledBookingAllowed}
                    onChange={set('isScheduledBookingAllowed')}
                    disabled={readOnly}
                  />
                  <ToggleCard
                    icon={Power}
                    title="Active"
                    text={form.isActive ? 'Shown to customers and bookable.' : 'Hidden from customers.'}
                    checked={form.isActive}
                    onChange={set('isActive')}
                    disabled={readOnly}
                  />
                </div>
                {errors.booking && <div className="mt-2 text-xs font-bold text-[#C53030]">{errors.booking}</div>}
              </div>
            </div>
          </Section>

          <Section
            number={4}
            title="Service Requirements"
            description="What has to be ready before the job: arranged by the customer, or brought by the partner."
          >
            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
              <div className="rounded-xl border border-[var(--line)] p-4">
                <Field
                  label="Customer Requirements"
                  htmlFor="service-customer-requirements"
                  hint="Shown to the customer when booking."
                >
                  <MultiSelect
                    id="service-customer-requirements"
                    ariaLabel="Customer requirements"
                    value={form.customerRequirements}
                    onChange={set('customerRequirements')}
                    options={requirementOptions('CUSTOMER_REQUIREMENT', form.customerRequirements)}
                    placeholder="Choose what the customer arranges"
                    disabled={readOnly}
                  />
                </Field>
              </div>
              <div className="rounded-xl border border-[var(--line)] p-4">
                <Field
                  label="Partner Requirements"
                  htmlFor="service-partner-requirements"
                  hint="Shown to the partner with the job."
                >
                  <MultiSelect
                    id="service-partner-requirements"
                    ariaLabel="Partner requirements"
                    value={form.partnerRequirements}
                    onChange={set('partnerRequirements')}
                    options={requirementOptions('PARTNER_REQUIREMENT', form.partnerRequirements)}
                    placeholder="Choose what the partner brings"
                    disabled={readOnly}
                  />
                </Field>
              </div>
            </div>
          </Section>

          {!readOnly && (
            <div className="flex justify-end gap-3 pb-2">
              <Link to={backTo} className={`${resetButtonClass} no-underline`}>
                Cancel
              </Link>
              <button
                type="button"
                onClick={save}
                disabled={saving}
                className={`${primaryButtonClass} disabled:cursor-not-allowed disabled:opacity-60`}
              >
                {saving ? (isNew ? 'Creating…' : 'Saving…') : isNew ? 'Save Service' : 'Save Changes'}
              </button>
            </div>
          )}
        </div>
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
