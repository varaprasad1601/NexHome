import Feather from '@expo/vector-icons/Feather';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, AppState, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { money } from '../utils/format';
import PaymentHelp from './PaymentHelp';
import { Button, ErrorBox, Header, Loading, Text } from './ui';

/** How long the partner waits for the online payment before Cash collected is offered. */
const WAIT_SECONDS = 120;
/** How often the payment is checked (the backend asks the gateway each time). */
const CHECK_MS = 5000;
/** How long the green tick shows before going back to the job. */
const DONE_MS = 2500;

const PAID = ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'];

/**
 * Payment processing for an online job, as the Collect payment screen is for a cash one: the amount, "Payment
 * processing..." while the customer pays in the app (POST /api/payments/bookings/check every 5 s, which asks the
 * gateway), the customer's last failed try, and a 2-minute countdown after which the partner may collect cash
 * instead (POST /api/payments/bookings/collect-cash: the booking becomes CASH with its cash collected). Once paid:
 * with `stopWork` the work is stopped then (POST /api/bookings/timer STOP: the job completes), a green tick, and
 * `onDone` after 2.5 s. `onBack` (optional) shows a Back that returns to the work; without it there is no Back.
 */
export default function OnlinePaymentScreen({ bookingId, stopWork, completed, onBack, onDone }) {
  const id = bookingId;
  const { authRequest } = useAuth();
  const [state, setState] = useState(null);
  const [error, setError] = useState('');
  const [left, setLeft] = useState(WAIT_SECONDS);
  const [done, setDone] = useState(null); // 'online' | 'cash'
  const [busy, setBusy] = useState(false);
  const checking = useRef(false);
  const finishing = useRef(false);
  // The latest onDone, so a new function from the parent never restarts the polling
  const doneRef = useRef(onDone);
  useEffect(() => {
    doneRef.current = onDone;
  }, [onDone]);

  // Paid: the work stops now (it completes the job), then the green tick and onDone
  const finishWith = useCallback(async (how) => {
    if (finishing.current) return;
    finishing.current = true;
    if (stopWork) {
      try {
        await authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: Number(id), eventType: 'STOP' } });
      } catch (err) {
        finishing.current = false;
        setError(err.message);
        return;
      }
    }
    setDone(how);
    setTimeout(() => doneRef.current?.(how), DONE_MS);
  }, [authRequest, id, stopWork]);

  // Checks the payment; never two at once, none once it is settled
  const check = useCallback(async () => {
    if (checking.current || done) return;
    checking.current = true;
    try {
      const result = await authRequest('/api/payments/bookings/check', { method: 'POST', body: { bookingId: Number(id) } });
      setState(result);
      setError('');
      if (PAID.includes(result.paymentStatus)) finishWith(result.paymentMethod === 'CASH' ? 'cash' : 'online');
      // NexHome (staff) finished the job while the payment is still pending: show it, then the job
      else if (!completed && result.bookingStatus === 'COMPLETED' && !finishing.current) {
        finishing.current = true;
        setDone('pending');
        setTimeout(() => doneRef.current?.('pending'), DONE_MS);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      checking.current = false;
    }
  }, [authRequest, id, done, finishWith, completed]);

  useEffect(() => {
    if (done) return undefined;
    const first = setTimeout(check, 0);
    let timer = setInterval(check, CHECK_MS);
    // Not while the app is in the background; once more on return
    const sub = AppState.addEventListener('change', (next) => {
      clearInterval(timer);
      if (next === 'active') {
        check();
        timer = setInterval(check, CHECK_MS);
      }
    });
    return () => {
      clearTimeout(first);
      clearInterval(timer);
      sub.remove();
    };
  }, [check, done]);

  // The 2-minute countdown
  useEffect(() => {
    if (done || left <= 0) return undefined;
    const tick = setTimeout(() => setLeft((value) => value - 1), 1000);
    return () => clearTimeout(tick);
  }, [left, done]);

  const collectCash = () => {
    const amount = money(state?.totalAmount);
    Alert.alert('Collect cash instead?', `Take ${amount} in cash from the customer. The online payment is cancelled and the job is marked paid in cash.`, [
      { text: 'Not yet', style: 'cancel' },
      {
        text: 'Cash collected',
        onPress: async () => {
          setBusy(true);
          setError('');
          try {
            const result = await authRequest('/api/payments/bookings/collect-cash', {
              method: 'POST',
              body: { bookingId: Number(id), notes: 'Online payment did not go through; paid in cash' }
            });
            setState(result);
            // A payment that came in meanwhile wins: then it is paid online
            finishWith(result.paymentMethod === 'CASH' ? 'cash' : 'online');
          } catch (err) {
            setError(err.message);
          } finally {
            setBusy(false);
          }
        }
      }
    ]);
  };

  if (!state && !error) return <Loading />;

  if (done === 'pending') {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 14 }}>
        <View style={{ width: 110, height: 110, borderRadius: 55, backgroundColor: colors.amberSoft, alignItems: 'center', justifyContent: 'center' }}>
          <Feather name="clock" size={58} color={colors.amber} />
        </View>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.amber }}>
          Payment pending
        </Text>
        <Text weight="extrabold" style={{ fontSize: 40, color: colors.navy }}>
          {money(state?.totalAmount)}
        </Text>
        <Text style={{ color: colors.muted, textAlign: 'center' }}>
          NexHome completed this job. The customer will pay online; you can carry on with your next job.
        </Text>
      </SafeAreaView>
    );
  }

  if (done) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 14 }}>
        <View style={{ width: 110, height: 110, borderRadius: 55, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' }}>
          <Feather name="check" size={64} color={colors.white} />
        </View>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.greenDark }}>
          {done === 'cash' ? 'Cash collected' : 'Payment done'}
        </Text>
        <Text weight="extrabold" style={{ fontSize: 40, color: colors.navy }}>
          {money(state?.totalAmount)}
        </Text>
        <Text style={{ color: colors.muted, textAlign: 'center' }}>
          {done === 'cash' ? 'The job is marked paid in cash.' : 'The customer paid online. Your share comes with your payout.'}
        </Text>
      </SafeAreaView>
    );
  }

  const minutes = Math.floor(left / 60);
  const seconds = String(left % 60).padStart(2, '0');
  const failed = ['FAILED', 'USER_DROPPED', 'CANCELLED'].includes(state?.lastAttemptStatus);

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Payment processing" back={Boolean(onBack)} onBack={onBack} />
      <ScrollView contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
        <View style={{ alignItems: 'center', gap: 6, paddingVertical: 30, paddingHorizontal: 16, borderRadius: 24, backgroundColor: colors.blueTint }}>
          <Text weight="bold" style={{ fontSize: 14, color: colors.blue }}>
            Amount to be paid
          </Text>
          <Text weight="extrabold" style={{ fontSize: 52, color: colors.navy }}>
            {money(state?.totalAmount)}
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center' }}>
            The customer pays in the NexHome app. Ask them to open the booking and tap Pay.
          </Text>
        </View>

        <View style={{ alignItems: 'center', gap: 10 }}>
          <ActivityIndicator size="large" color={colors.blue} />
          <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
            Payment processing…
          </Text>
          {failed ? (
            <Text weight="semibold" style={{ fontSize: 13, color: colors.red, textAlign: 'center' }}>
              The customer’s last try did not go through{state.lastAttemptMessage ? `: ${state.lastAttemptMessage}` : '.'} They can try again.
            </Text>
          ) : null}
          {left > 0 ? (
            <View style={{ alignItems: 'center', gap: 2 }}>
              <Text weight="extrabold" style={{ fontSize: 30, color: colors.blue, fontVariant: ['tabular-nums'] }}>
                {minutes}:{seconds}
              </Text>
              <Text style={{ fontSize: 12.5, color: colors.muted, textAlign: 'center' }}>
                If the online payment does not go through, you can collect cash when the timer ends.
              </Text>
            </View>
          ) : (
            <Text style={{ fontSize: 12.5, color: colors.muted, textAlign: 'center' }}>
              Still waiting. If the online payment keeps failing, collect the amount in cash.
            </Text>
          )}
        </View>

        <ErrorBox message={error} />

        <View style={{ gap: 10 }}>
          {left <= 0 ? (
            <Button title={`Cash collected (${money(state?.totalAmount)})`} icon="dollar-sign" onPress={collectCash} loading={busy} />
          ) : null}
          {onBack ? <Button title="Back" variant="secondary" icon="arrow-left" onPress={onBack} disabled={busy} /> : null}
        </View>
        <PaymentHelp publicId={state?.bookingPublicId} />
      </ScrollView>
    </SafeAreaView>
  );
}
