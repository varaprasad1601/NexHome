import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorBox, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { money } from '../utils/format';
import { holdPaymentNotices } from '../utils/noticeHold';
import { finishPayment, payOnline } from '../utils/pay';

/**
 * How often the order is checked, the least time Processing shows, how long a SUCCESS not yet captured may settle
 * before showing Pending, and how long a pending payment is watched (it then turns into successful or failed here).
 */
const CHECK_MS = 2500;
const PENDING_CHECK_MS = 4000;
const MIN_PROCESSING_MS = 1500;
const SUCCESS_SETTLE_MS = 6000;
const PENDING_WATCH_MS = 5 * 60 * 1000;

/** Each result: icon, colours, title and text. */
const RESULTS = {
  PAID: {
    icon: 'check',
    fg: colors.green,
    bg: colors.greenSoft,
    title: 'Payment successful',
    text: 'Thank you! Your payment has been received.'
  },
  FAILED: {
    icon: 'x',
    fg: colors.red,
    bg: colors.redSoft,
    title: 'Payment failed',
    text: 'The payment did not go through. If money was taken, your bank returns it. You can try again.'
  },
  PENDING: {
    icon: 'clock',
    fg: colors.amber,
    bg: colors.amberSoft,
    title: 'Payment pending',
    text: 'Your bank is still confirming the payment. This page updates by itself when it goes through, or check your booking later. Please do not pay again yet.'
  },
  OPEN: {
    icon: 'alert-circle',
    fg: colors.muted,
    bg: '#eef2f6',
    title: 'Payment not completed',
    text: 'You left the payment page before paying. You can pay now, or later from your booking.'
  }
};

/**
 * The payment's result (after the `pay` screen): Processing while the backend asks the gateway
 * (POST /api/payments/orders/verify, which records a payment), then Payment successful, failed, pending, or not
 * completed. Only an attempt made after the page opened counts (`since`: the latest attempt then). Successful:
 * Continue opens the rating screen for a completed booking not yet rated, else the booking (`next`); failed / not
 * completed: Try again or Go to booking; pending: Go to booking.
 */
export default function PaymentStatus() {
  const { orderId, bookingId, next, since } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const [result, setResult] = useState(null); // { state, order, message }
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  // Payment notifications (received / failed / pending) wait until this screen shows the outcome, then pop up
  const [release] = useState(() => holdPaymentNotices());
  useEffect(() => {
    if (result) release();
  }, [result, release]);
  useEffect(() => () => release(0), [release]);

  useEffect(() => {
    let stopped = false;
    let timer;
    let showTimer;
    let pendingShown = false;
    const started = Date.now();
    const show = (value) => {
      const wait = Math.max(0, MIN_PROCESSING_MS - (Date.now() - started));
      clearTimeout(showTimer);
      showTimer = setTimeout(() => !stopped && setResult(value), wait);
    };
    const check = async () => {
      let order = null;
      try {
        order = await authRequest('/api/payments/orders/verify', { method: 'POST', body: { paymentOrderId: Number(orderId) } });
      } catch {
        // checked again below, until the wait is over
      }
      if (stopped) return;
      if (order) {
        const fresh = order.lastAttemptId != null && order.lastAttemptId !== (since || null);
        const attempt = fresh ? order.lastAttemptStatus : null;
        // The gateway's payment id of this attempt (Cashfree's cf_payment_id): what support and the bank go by
        const reference = fresh || order.paymentStatus === 'PAID' ? order.lastAttemptId : null;
        if (order.paymentStatus === 'PAID') return show({ state: 'PAID', order, reference });
        if (order.paymentStatus !== 'CREATED' && order.paymentStatus !== 'PENDING') {
          return show({ state: 'FAILED', order });
        }
        if (attempt && !['PENDING', 'NOT_ATTEMPTED', 'SUCCESS'].includes(attempt)) {
          return show({ state: 'FAILED', order, reference, message: order.lastAttemptMessage });
        }
        if (attempt !== 'PENDING' && attempt !== 'SUCCESS') return show({ state: 'OPEN', order });
      }
      // The bank is still confirming: Payment pending at once (a SUCCESS being captured gets a few seconds first), and
      // this screen keeps checking, turning into successful or failed when the bank answers
      const settling = order?.lastAttemptStatus === 'SUCCESS' && Date.now() - started < SUCCESS_SETTLE_MS;
      if (!pendingShown && !settling && (order || Date.now() - started >= SUCCESS_SETTLE_MS)) {
        pendingShown = true;
        const pendingRef = order && order.lastAttemptId !== (since || null) ? order.lastAttemptId : null;
        show({ state: 'PENDING', order, reference: pendingRef });
      }
      if (Date.now() - started < PENDING_WATCH_MS) timer = setTimeout(check, pendingShown ? PENDING_CHECK_MS : CHECK_MS);
    };
    check();
    return () => {
      stopped = true;
      clearTimeout(timer);
      clearTimeout(showTimer);
    };
  }, [authRequest, orderId, since]);

  // Successful: rate a completed booking not yet rated, then the booking
  const goOn = async () => {
    setBusy(true);
    try {
      const detail = await authRequest(`/api/bookings/${bookingId}`);
      if (detail.booking?.bookingStatus === 'COMPLETED') {
        const rated = await authRequest(`/api/reviews/bookings/${bookingId}`).then(
          () => true,
          (err) => err.status !== 404
        );
        if (!rated) {
          // `paid`: this screen already showed the payment, so the rating screen skips its payment step
          router.replace({ pathname: '/rate/[id]', params: { id: String(bookingId), next: next ?? 'back', paid: '1' } });
          return;
        }
      }
    } catch {
      // the booking is shown anyway
    }
    finishPayment(next);
  };

  const tryAgain = async () => {
    setError('');
    setBusy(true);
    try {
      const opened = await payOnline(authRequest, Number(bookingId), { next: next ?? 'back', replace: true });
      if (opened === 'PAID') setResult((current) => ({ ...current, state: 'PAID' }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const look = result ? RESULTS[result.state] : null;
  const amount = result?.order?.amount;

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28, gap: 14 }}>
        {!look ? (
          <>
            <View style={{ width: 96, height: 96, borderRadius: 48, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
              <ActivityIndicator size="large" color={colors.blue} />
            </View>
            <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
              Processing payment
            </Text>
            <Text style={{ color: colors.muted, textAlign: 'center' }}>Please wait while we confirm your payment. Do not close the app.</Text>
          </>
        ) : (
          <>
            <View style={{ width: 96, height: 96, borderRadius: 48, backgroundColor: look.bg, alignItems: 'center', justifyContent: 'center' }}>
              <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: look.fg, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name={look.icon} size={34} color={colors.white} />
              </View>
            </View>
            <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
              {look.title}
            </Text>
            {amount != null && (
              <Text weight="extrabold" style={{ fontSize: 28, color: look.fg }}>
                {money(amount)}
              </Text>
            )}
            <Text style={{ color: colors.muted, textAlign: 'center', lineHeight: 20 }}>{look.text}</Text>
            {result.state === 'FAILED' && result.message ? (
              <Text style={{ color: colors.red, textAlign: 'center', fontSize: 13 }}>{result.message}</Text>
            ) : null}
            {result.reference || result.order?.publicId ? (
              <View style={{ alignSelf: 'stretch', backgroundColor: colors.white, borderColor: colors.line, borderWidth: 1, borderRadius: 14, padding: 14, gap: 8, marginTop: 6 }}>
                {result.reference ? (
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
                    <Text style={{ color: colors.muted, fontSize: 13 }}>Payment reference ID</Text>
                    <Text weight="bold" selectable style={{ fontSize: 13, flexShrink: 1, textAlign: 'right' }}>
                      {result.reference}
                    </Text>
                  </View>
                ) : null}
                {result.order?.publicId ? (
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
                    <Text style={{ color: colors.muted, fontSize: 13 }}>Order ID</Text>
                    <Text weight="bold" selectable style={{ fontSize: 13 }}>
                      {result.order.publicId}
                    </Text>
                  </View>
                ) : null}
              </View>
            ) : null}
            <ErrorBox message={error} />
          </>
        )}
      </View>
      {look && (
        <View style={{ padding: 20, gap: 10 }}>
          {result.state === 'PAID' && <Button title="Continue" loading={busy} onPress={goOn} />}
          {(result.state === 'FAILED' || result.state === 'OPEN') && (
            <>
              <Button title="Try again" icon="refresh-cw" loading={busy} onPress={tryAgain} />
              <Button title="Go to booking" variant="secondary" disabled={busy} onPress={() => finishPayment(next)} />
            </>
          )}
          {result.state === 'PENDING' && <Button title="Go to booking" onPress={() => finishPayment(next)} />}
        </View>
      )}
    </SafeAreaView>
  );
}
