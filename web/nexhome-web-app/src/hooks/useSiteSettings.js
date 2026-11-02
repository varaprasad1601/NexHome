import { useSyncExternalStore } from 'react';

import { apiRequest } from '../api/client';
import { BUSINESS_HOURS, EMAIL_ADDRESS, FORMATTED_PHONE, LOCATION_TEXT, PHONE_NUMBER } from '../data/siteData';

/** The phone's tel: address: its digits, with India's +91 in front of a 10-digit number. */
function telOf(phone) {
  const digits = phone.replace(/\D/g, '');
  return `tel:+${digits.length === 10 ? `91${digits}` : digits}`;
}

function settingsOf(phone, whatsapp, email, area, hours) {
  return { phone, telHref: telOf(phone), whatsapp, email, area, hours };
}

/** The built-in details, shown until the server's arrive (and when it cannot be reached). */
let current = settingsOf(FORMATTED_PHONE, PHONE_NUMBER, EMAIL_ADDRESS, LOCATION_TEXT, BUSINESS_HOURS);
let request = null;
const listeners = new Set();

function load() {
  request ??= apiRequest('/api/settings/public')
    .then((data) => {
      current = settingsOf(
        data.SUPPORT_PHONE || current.phone,
        data.WHATSAPP_NUMBER || current.whatsapp,
        data.SUPPORT_EMAIL || current.email,
        data.SERVICE_AREA || current.area,
        data.WORKING_HOURS || current.hours
      );
      listeners.forEach((listener) => listener());
    })
    .catch(() => {});
}

function subscribe(listener) {
  listeners.add(listener);
  load();
  return () => listeners.delete(listener);
}

/** The current business and contact details, outside React (e.g. building a WhatsApp link). */
export function siteSettings() {
  return current;
}

/**
 * The business and contact details from General Settings (GET /api/settings/public, public, loaded once per visit):
 * { phone, telHref, whatsapp, email, area, hours }. Components re-render when they arrive.
 */
export function useSiteSettings() {
  return useSyncExternalStore(subscribe, siteSettings);
}
