import { PHONE_NUMBER } from '../data/siteData';

/** A wa.me link that opens a chat with NexHome with the message filled in. */
export function buildWhatsAppUrl(message) {
  return `https://wa.me/${PHONE_NUMBER}?text=${encodeURIComponent(message)}`;
}
