/**
 * Catalogue helpers: price, duration and commission formatting, and the category form (common/FormModal fields) with
 * its request body (no slug: the backend makes it from the name on create and keeps it on update). The service form
 * is data/serviceForm.js.
 */

/** ₹1,250 or ₹1,250.50; null for no amount. */
export function formatPrice(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return `₹${number.toLocaleString('en-IN', { minimumFractionDigits: number % 1 ? 2 : 0, maximumFractionDigits: 2 })}`;
}

/** 90 → "1 hr 30 min", 45 → "45 min"; null for none. */
export function formatMinutes(minutes) {
  if (!minutes) return null;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hours && `${hours} hr`, rest && `${rest} min`].filter(Boolean).join(' ');
}

/** "10%" or "₹50" for a commission type and value; null without a value. */
export function formatCommission(type, value) {
  if (value === null || value === undefined) return null;
  return type === 'PERCENTAGE' ? `${Number(value)}%` : formatPrice(value);
}

const text = (value) => (value ?? '').toString();
const optionalNumber = (value) => (String(value).trim() === '' ? null : Number(value));

// ------------------------------------------------------------------ categories

export function categoryFields(editing, category = null) {
  return [
    { key: 'name', label: 'Category Name', type: 'text', required: true, maxLength: 120, placeholder: 'e.g. Plumbing' },
    { key: 'displayOrder', label: 'Display Order', type: 'number', hint: 'Lower numbers show first.' },
    {
      key: 'image',
      label: 'Category Image',
      type: 'image',
      currentUrl: category?.iconUrl ?? null,
      hint: 'Uploading comes once cloud storage is connected; the current image stays until then.'
    },
    { key: 'description', label: 'Description', type: 'textarea', maxLength: 300, rows: 3, counter: true },
    {
      key: 'isActive',
      label: 'Status',
      type: 'toggle',
      onText: 'Active: shown to customers',
      offText: 'Inactive: hidden'
    }
  ];
}

export function categoryForm(category) {
  return {
    name: text(category?.name),
    // The picked image File; not sent until cloud storage exists (the category keeps its iconUrl)
    image: null,
    displayOrder: text(category?.displayOrder ?? 0),
    description: text(category?.description),
    isActive: category?.isActive ?? true
  };
}

export function categoryBody(values) {
  return {
    name: values.name.trim(),
    displayOrder: optionalNumber(values.displayOrder) ?? 0,
    description: values.description.trim(),
    isActive: values.isActive
  };
}
