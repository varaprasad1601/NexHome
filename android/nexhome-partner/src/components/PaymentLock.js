import { router, usePathname } from 'expo-router';
import { useEffect } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { isLockedJob, setPaymentLock, usePaymentLock } from '../utils/paymentLock';

/**
 * Keeps the partner on the Payment processing screen of a job the customer confirmed done but has not paid yet
 * (rendered once in _layout, nothing on screen). It looks for such a job once when the app opens (or is signed in)
 * and when it comes back to the front (GET /api/bookings/partner/me, one request, no polling); while one is locked,
 * any screen but its `await-payment/[id]` and Help & support (`/support`) goes back to it.
 */
export default function PaymentLock() {
  const { signedIn, profile, authRequest } = useAuth();
  const locked = usePaymentLock();
  const pathname = usePathname();

  useEffect(() => {
    if (!signedIn || !profile?.partnerProfileId) {
      setPaymentLock(null);
      return undefined;
    }
    let cancelled = false;
    const look = async () => {
      try {
        const page = await authRequest('/api/bookings/partner/me?size=20');
        const job = (page?.content ?? []).find(isLockedJob);
        if (!cancelled) setPaymentLock(job ? job.bookingId : null);
      } catch {
        // Next time the app comes to the front
      }
    };
    look();
    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active') look();
    });
    return () => {
      cancelled = true;
      subscription.remove();
    };
  }, [signedIn, profile?.partnerProfileId, authRequest]);

  useEffect(() => {
    if (!locked) return;
    const allowed = pathname === `/await-payment/${locked}` || pathname === '/support';
    if (!allowed) router.replace({ pathname: '/await-payment/[id]', params: { id: String(locked) } });
  }, [locked, pathname]);

  return null;
}
