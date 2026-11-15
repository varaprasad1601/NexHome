import { router, useLocalSearchParams, useNavigation } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Alert, AppState, Linking, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';

import { API_BASE_URL } from '../api/client';
import { Header, Loading } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { holdPaymentNotices } from '../utils/noticeHold';
import { ATTEMPT_DONE, PAYMENT_RETURN } from '../utils/pay';

/**
 * How often the order is checked (each check also asks the gateway): rarely while the customer is still on the
 * payment page, often once the payment has left it (a bank's page, a UPI app), never while the app is in the
 * background (checked once on return).
 */
const IDLE_CHECK_MS = 15000;
const ACTIVE_CHECK_MS = 4000;
/**
 * How long an attempt may wait for the bank (PENDING) while the customer is on this screen before it closes: the result
 * screen then shows Payment pending at once and keeps watching until the bank answers. Time in the UPI app does not
 * count (no checks while the app is in the background), so approving there is never cut short.
 */
const PENDING_GRACE_MS = 8000;

/** Addresses the page itself may open; anything else (upi:, intent:, tez:, phonepe:, ...) is a payment app. */
const WEB = /^(https?:|about:|data:|blob:|javascript:)/i;

/** Android's intent://...#Intent;scheme=upi;...;end as the app's own address (upi://...), plus its fallback page. */
function fromIntent(url) {
  const match = /^intent:\/\/([^#]*)#Intent;(.*)end;?$/i.exec(url);
  if (!match) return { target: url };
  const extras = Object.fromEntries(
    match[2].split(';').filter(Boolean).map((part) => {
      const at = part.indexOf('=');
      return at < 0 ? [part, ''] : [part.slice(0, at), decodeURIComponent(part.slice(at + 1))];
    })
  );
  return {
    target: extras.scheme ? `${extras.scheme}://${match[1]}` : url,
    fallback: extras['S.browser_fallback_url']
  };
}

/**
 * The secure payment page inside the app (opened by utils/pay.payOnline): the backend's checkout page
 * (GET /api/payments/checkout, Cashfree's checkout) in a WebView. UPI apps it asks for open on the phone; when the
 * page is done or closed (it goes to nexhome://payment-result), or Back is pressed, it gives way to `payment-status`
 * (Processing, then the result). It also checks the order every few seconds (POST /api/payments/orders/verify) and
 * goes on by itself once a new attempt is paid, failed or waiting for the bank, since the page may end blank.
 */
export default function Pay() {
  const { session, orderId, bookingId, next } = useLocalSearchParams();
  const navigation = useNavigation();
  const { authRequest } = useAuth();
  const [loading, setLoading] = useState(true);
  const closed = useRef(false);
  const pendingSince = useRef(null);
  // The latest attempt when the page opened (e.g. an earlier failed try): only a newer one closes the screen
  const before = useRef(undefined);
  // Payment notifications wait while paying; the result screen shows them after the outcome (utils/noticeHold)
  useEffect(() => holdPaymentNotices(), []);

  // On to the result: Processing, then successful / failed / pending (only attempts after `since` count)
  const close = () => {
    if (closed.current) return;
    closed.current = true;
    router.replace({
      pathname: '/payment-status',
      params: { orderId, bookingId, next: next ?? 'back', since: before.current ?? '' }
    });
  };

  // Back (the arrow or the phone's) also goes to the result, which says if nothing was paid
  useEffect(
    () =>
      navigation.addListener('beforeRemove', (event) => {
        if (closed.current) return;
        event.preventDefault();
        close();
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [navigation]
  );

  // Whether the payment has left the checkout page (a bank's page or a UPI app): then the order is checked often
  const leftPage = useRef(false);
  const checkNow = useRef(null);

  // The gateway's page may end on a blank page instead of coming back: check the order with the backend now and
  // then (which records the payment) and close once it is paid, failed or waiting for the bank. One check at a time,
  // none while the app is in the background, one at once when it returns (e.g. from the UPI app)
  useEffect(() => {
    if (!orderId) return undefined;
    let stopped = false;
    let timer;
    let running = false;
    const schedule = () => {
      clearTimeout(timer);
      if (!stopped && AppState.currentState === 'active') {
        timer = setTimeout(check, leftPage.current ? ACTIVE_CHECK_MS : IDLE_CHECK_MS);
      }
    };
    const check = async () => {
      if (running || stopped) return;
      running = true;
      try {
        const checked = await authRequest('/api/payments/orders/verify', { method: 'POST', body: { paymentOrderId: Number(orderId) } });
        if (before.current === undefined) before.current = checked.lastAttemptId ?? null;
        const isNew = checked.lastAttemptId != null && checked.lastAttemptId !== before.current;
        const order = isNew ? checked : { ...checked, lastAttemptStatus: null };
        const done = order.paymentStatus !== 'CREATED' && order.paymentStatus !== 'PENDING';
        // Waiting for the bank: the customer may still be on the bank's or UPI app's step, so give it a while first
        const waiting = order.lastAttemptStatus === 'PENDING';
        if (!waiting) pendingSince.current = null;
        else pendingSince.current ??= Date.now();
        const settled = waiting ? Date.now() - pendingSince.current >= PENDING_GRACE_MS : ATTEMPT_DONE.includes(order.lastAttemptStatus);
        if (!stopped && (done || settled)) {
          stopped = true;
          close();
          return;
        }
      } catch {
        // a missed check: try again next time
      } finally {
        running = false;
      }
      schedule();
    };
    checkNow.current = () => {
      clearTimeout(timer);
      check();
    };
    const watch = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        leftPage.current = true; // back from a payment app
        checkNow.current?.();
      } else {
        clearTimeout(timer);
      }
    });
    check(); // at once: notes the attempt already there
    return () => {
      stopped = true;
      clearTimeout(timer);
      watch.remove();
      checkNow.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  const url = `${API_BASE_URL}/api/payments/checkout?session=${encodeURIComponent(session ?? '')}&return=${encodeURIComponent(PAYMENT_RETURN)}`;

  const openApp = (address) => {
    leftPage.current = true;
    const { target, fallback } = fromIntent(address);
    Linking.openURL(target).catch(() => {
      if (fallback) Linking.openURL(fallback).catch(() => {});
      else Alert.alert('App not found', 'That payment app is not installed on this phone. Choose another way to pay.');
    });
  };

  const onRequest = (request) => {
    const address = request.url ?? '';
    if (address.startsWith(PAYMENT_RETURN)) {
      close();
      return false;
    }
    if (WEB.test(address)) {
      // The page moving on from the checkout (a bank's page): check often from now on
      // (only the page itself: the gateway's own frames inside it load as it opens)
      const topFrame = request.isTopFrame !== false;
      if (topFrame && /^https?:/i.test(address) && !address.startsWith(`${API_BASE_URL}/api/payments/checkout`) && !leftPage.current) {
        leftPage.current = true;
        checkNow.current?.();
      }
      return true;
    }
    openApp(address);
    return false;
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Secure payment" />
      <View style={{ flex: 1 }}>
        <WebView
          source={{ uri: url }}
          originWhitelist={['*']}
          onShouldStartLoadWithRequest={onRequest}
          setSupportMultipleWindows={false}
          javaScriptEnabled
          domStorageEnabled
          onLoadEnd={() => setLoading(false)}
          style={{ flex: 1, backgroundColor: colors.ground }}
        />
        {loading && (
          <View style={{ position: 'absolute', inset: 0, backgroundColor: colors.ground }}>
            <Loading />
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}
