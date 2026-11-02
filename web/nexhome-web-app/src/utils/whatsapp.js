import { siteSettings } from '../hooks/useSiteSettings';

/** A wa.me link that opens a chat with NexHome with the message filled in. */
export function buildWhatsAppUrl(message) {
  return `https://wa.me/${siteSettings().whatsapp}?text=${encodeURIComponent(message)}`;
}
