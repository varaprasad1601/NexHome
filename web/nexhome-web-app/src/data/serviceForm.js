/**
 * The service form (pages/admin/ServiceEditorPage): its state, checks and request body for
 * POST /api/catalog/services/create and PUT /api/catalog/services/update. The service target (SERVICE_TARGET lookup,
 * "Service For") decides the pricing types offered, from its metadata.pricingTypes; the backend checks the same.
 */

/** The pricing types priced by variants (hourly packages, home sizes); a FIXED service has one price and none. */
export const VARIANT_PRICED = ['HOURLY', 'AREA'];

import { richTextLength, toRichText } from '../utils/richText';

/** The detailed description's limit, in visible characters (it is stored as formatted HTML). */
export const DESCRIPTION_LIMIT = 4000;

/** Per pricing type: what the base price means and how the variants are named (FIXED has no variants). */
export const PRICING_COPY = {
  FIXED: {
    basePriceHint: 'Charged once per booking.'
  },
  HOURLY: {
    basePriceHint: 'Per hour, for jobs without a package and time beyond one.',
    variantsTitle: 'Hourly Packages',
    variantsHint: 'Packages of hours at a set price, e.g. 2 Hours for ₹499.',
    addLabel: 'Add Hourly Package',
    namePlaceholder: 'e.g. 2 Hours'
  },
  AREA: {
    basePriceHint: 'The starting price, when no home size is chosen.',
    variantsTitle: 'Home Sizes',
    variantsHint: 'Prices by the size of the home, e.g. 1 BHK, 2 BHK.',
    addLabel: 'Add Home Size',
    namePlaceholder: 'e.g. 2 BHK'
  }
};

/**
 * The billing rules of hourly services (at least an hour, then 30-minute steps). The form does not show them: a service
 * keeps the ones it has, and one becoming hourly gets these (the backend needs both for HOURLY).
 */
const HOURLY_DEFAULTS = { minimumBillableMinutes: '60', billingIncrementMinutes: '30' };

const text = (value) => (value ?? '').toString();
const optionalNumber = (value) => (String(value).trim() === '' ? null : Number(value));
const isAmount = (value) => String(value).trim() !== '' && Number.isFinite(Number(value)) && Number(value) >= 0;
let nextRowKey = 0;
const rowKey = () => `row-${(nextRowKey += 1)}`;

/** The pricing types a SERVICE_TARGET value allows (all of `pricingTypes` when its metadata names none). */
export function allowedPricingTypes(target, pricingTypes) {
  const allowed = target?.metadata?.pricingTypes;
  return Array.isArray(allowed) ? pricingTypes.filter((type) => allowed.includes(type.valueCode)) : pricingTypes;
}

/**
 * The form with another pricing type: the prices and variants entered for the old type are
 * cleared, since they mean something else under the new one.
 */
export function withPricingType(form, pricingType) {
  if (pricingType === form.pricingType) return form;
  return { ...form, pricingType, basePrice: '', discountPrice: '', ...HOURLY_DEFAULTS, variants: [] };
}

export function emptyVariant() {
  return {
    key: rowKey(),
    serviceVariantId: null,
    name: '',
    hours: '',
    price: '',
    discountPrice: '',
    description: '',
    isActive: true,
    durationMinutes: null
  };
}

/** The form's values: empty for a new service, else from GET /api/catalog/services/{id}. */
export function serviceFormOf(detail) {
  const service = detail?.service;
  const chosen = (side) =>
    (detail?.requirements ?? [])
      .filter((requirement) => requirement.requirementFor === side && requirement.requirementCode)
      .map((requirement) => requirement.requirementCode);
  return {
    categoryId: service ? String(service.categoryId) : '',
    name: text(service?.name),
    shortDescription: text(service?.shortDescription),
    description: toRichText(service?.description),
    serviceTarget: text(service?.serviceTarget),
    pricingType: text(service?.pricingType),
    basePrice: text(service?.basePrice),
    discountPrice: text(service?.discountPrice),
    minimumBillableMinutes: text(service?.minimumBillableMinutes ?? HOURLY_DEFAULTS.minimumBillableMinutes),
    billingIncrementMinutes: text(service?.billingIncrementMinutes ?? HOURLY_DEFAULTS.billingIncrementMinutes),
    platformCommissionType: text(service?.platformCommissionType ?? 'PERCENTAGE'),
    platformCommissionValue: text(service?.platformCommissionValue),
    isInstantBookingAllowed: service?.isInstantBookingAllowed ?? false,
    isScheduledBookingAllowed: service?.isScheduledBookingAllowed ?? true,
    isActive: service?.isActive ?? true,
    variants: (detail?.variants ?? []).map((variant) => ({
      key: rowKey(),
      serviceVariantId: variant.serviceVariantId,
      name: variant.name,
      hours: variant.durationMinutes ? String(+(variant.durationMinutes / 60).toFixed(2)) : '',
      price: text(variant.priceOverride),
      discountPrice: text(variant.discountPrice),
      description: text(variant.description),
      isActive: variant.isActive,
      durationMinutes: variant.durationMinutes
    })),
    customerRequirements: chosen('CUSTOMER'),
    partnerRequirements: chosen('PARTNER')
  };
}

/**
 * The form's problems: { field: message }, variant problems as `variants.<key>.<field>`, and `form` for rules across
 * fields. Empty when it can be saved.
 */
export function validateServiceForm(form, allowedTypes) {
  const errors = {};
  const require = (field, message) => {
    if (String(form[field]).trim() === '') errors[field] = message;
  };
  require('name', 'Enter the service name');
  require('categoryId', 'Choose a category');
  require('shortDescription', 'Enter a short description');
  if (richTextLength(form.description) > DESCRIPTION_LIMIT) {
    errors.description = `Keep the description within ${DESCRIPTION_LIMIT} characters`;
  }
  require('serviceTarget', 'Choose what the service is for');
  if (!form.pricingType) errors.pricingType = 'Choose a pricing type';
  else if (form.serviceTarget && !allowedTypes.includes(form.pricingType)) {
    errors.pricingType = 'This pricing type does not fit what the service is for';
  }
  if (!isAmount(form.basePrice)) errors.basePrice = 'Enter the base price';
  if (form.discountPrice !== '') {
    if (!isAmount(form.discountPrice)) errors.discountPrice = 'Enter a valid amount';
    else if (isAmount(form.basePrice) && Number(form.discountPrice) > Number(form.basePrice)) {
      errors.discountPrice = 'Cannot be more than the base price';
    }
  }
  if (!form.platformCommissionType) errors.platformCommissionType = 'Choose a commission type';
  if (!isAmount(form.platformCommissionValue)) errors.platformCommissionValue = 'Enter the commission';
  else if (form.platformCommissionType === 'PERCENTAGE' && Number(form.platformCommissionValue) > 100) {
    errors.platformCommissionValue = 'A percentage can be at most 100';
  }
  if (!form.isInstantBookingAllowed && !form.isScheduledBookingAllowed) {
    errors.booking = 'Allow at least one way of booking';
  }
  (VARIANT_PRICED.includes(form.pricingType) ? form.variants : []).forEach((variant) => {
    const at = (field) => `variants.${variant.key}.${field}`;
    if (!variant.name.trim()) errors[at('name')] = 'Name it';
    if (!isAmount(variant.price)) errors[at('price')] = 'Enter a price';
    if (variant.discountPrice !== '') {
      if (!isAmount(variant.discountPrice)) errors[at('discountPrice')] = 'Invalid';
      else if (isAmount(variant.price) && Number(variant.discountPrice) > Number(variant.price)) {
        errors[at('discountPrice')] = 'Above the price';
      }
    }
    if (form.pricingType === 'HOURLY' && !(Number(variant.hours) > 0)) errors[at('hours')] = 'Hours';
  });
  return errors;
}

/** The create / update body; on update the lists replace the service's variants and chosen requirements. */
export function serviceRequest(form) {
  const hourly = form.pricingType === 'HOURLY';
  return {
    categoryId: Number(form.categoryId),
    name: form.name.trim(),
    shortDescription: form.shortDescription.trim(),
    // Formatted HTML, already limited to the editor's tags; the backend cleans it again
    description: form.description,
    serviceTarget: form.serviceTarget,
    pricingType: form.pricingType,
    basePrice: Number(form.basePrice),
    discountPrice: optionalNumber(form.discountPrice),
    minimumBillableMinutes: hourly ? Number(form.minimumBillableMinutes) : null,
    billingIncrementMinutes: hourly ? Number(form.billingIncrementMinutes) : null,
    platformCommissionType: form.platformCommissionType,
    platformCommissionValue: Number(form.platformCommissionValue),
    isInstantBookingAllowed: form.isInstantBookingAllowed,
    isScheduledBookingAllowed: form.isScheduledBookingAllowed,
    isActive: form.isActive,
    // A FIXED service has none: sending [] removes any it had
    variants: (VARIANT_PRICED.includes(form.pricingType) ? form.variants : []).map((variant) => ({
      serviceVariantId: variant.serviceVariantId,
      name: variant.name.trim(),
      description: variant.description.trim() || null,
      price: Number(variant.price),
      discountPrice: optionalNumber(variant.discountPrice),
      // Hourly packages cover their hours; home sizes keep any duration they had
      durationMinutes: hourly ? Math.round(Number(variant.hours) * 60) : variant.durationMinutes,
      isActive: variant.isActive
    })),
    customerRequirements: form.customerRequirements,
    partnerRequirements: form.partnerRequirements
  };
}
