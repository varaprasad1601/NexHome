import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';

/**
 * Asking the customer to rate a completed booking they have not reviewed: the rating screen (for a cash job first
 * "Confirm your payment") opens by itself from any screen (`components/LiveWatcher`, also Home and the booking's page), once per booking each time the app runs, and never again for one they skipped
 * (kept on the phone, the last 100).
 */
const SKIPPED_KEY = 'nexhome.skippedRatings';
let skipped = null;
const prompted = new Set();

async function skippedSet() {
  if (skipped) return skipped;
  try {
    skipped = new Set(JSON.parse((await SecureStore.getItemAsync(SKIPPED_KEY)) ?? '[]').map(Number));
  } catch {
    skipped = new Set();
  }
  return skipped;
}

/** Whether the rating screen was already opened for this booking this session. */
export function wasRatingShown(bookingId) {
  return prompted.has(Number(bookingId));
}

/** The rating screen is open for this booking (it is not opened again this session). */
export function markRatingShown(bookingId) {
  prompted.add(Number(bookingId));
}

/** The customer skipped rating this booking: it is not asked again. */
export async function skipRating(bookingId) {
  const set = await skippedSet();
  set.add(Number(bookingId));
  try {
    await SecureStore.setItemAsync(SKIPPED_KEY, JSON.stringify([...set].slice(-100)));
  } catch {
    // Only a convenience: at worst they are asked again
  }
}

/** Opens the rating screen for a completed, unreviewed booking unless it was shown this session or skipped. */
export async function promptRating(bookingId) {
  const id = Number(bookingId);
  if (!id || prompted.has(id)) return false;
  prompted.add(id);
  if ((await skippedSet()).has(id)) return false;
  router.push({ pathname: '/rate/[id]', params: { id: String(id), next: 'back' } });
  return true;
}
