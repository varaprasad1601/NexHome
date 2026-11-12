import Feather from '@expo/vector-icons/Feather';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '../theme';
import { durationLabel, money, serviceNameOf } from '../utils/format';
import PaymentHelp from './PaymentHelp';
import { Button, ErrorBox, Header, Text } from './ui';

/** 3725 seconds as "01:02:05". */
function clock(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds));
  const two = (n) => String(n).padStart(2, '0');
  return `${two(Math.floor(s / 3600))}:${two(Math.floor((s % 3600) / 60))}:${two(s % 60)}`;
}

/**
 * While the work runs (IN_PROGRESS or PAUSED) the job page is only this: the job, a large live timer of the time
 * worked, and Finish work. No Pause or Resume: only NexHome staff pause and resume, and the job page, which reloads
 * every 5 s, follows them (a paused job shows a note and its timer stopped).
 */
export function WorkingScreen({ booking, seconds, expectedMinutes, busy, error, onFinish }) {
  const paused = booking.bookingStatus === 'PAUSED';
  const customer = booking.address?.contactName || booking.customerName;
  const area = [booking.address?.addressLine2, booking.address?.city].filter(Boolean).join(', ');
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={paused ? 'Paused' : 'Working'} />
      <ScrollView contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
        <View style={{ alignItems: 'center', gap: 6 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 5, borderRadius: 999, backgroundColor: paused ? colors.amberSoft : colors.greenSoft }}>
            <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: paused ? colors.amber : colors.green }} />
            <Text weight="extrabold" style={{ fontSize: 12, color: paused ? colors.amber : colors.greenDark }}>
              {paused ? 'Paused' : 'Working'}
            </Text>
          </View>
          <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
            {serviceNameOf(booking.serviceName)}
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center' }}>{[customer, area].filter(Boolean).join(' · ')}</Text>
        </View>

        <View style={{ alignItems: 'center', gap: 6, paddingVertical: 28, borderRadius: 24, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}>
          <Text style={{ fontSize: 13, color: colors.muted }}>Time worked</Text>
          <Text weight="extrabold" style={{ fontSize: 54, color: paused ? colors.muted : colors.navy, letterSpacing: 1, fontVariant: ['tabular-nums'] }}>
            {clock(seconds)}
          </Text>
          {expectedMinutes ? <Text style={{ fontSize: 12.5, color: colors.muted }}>Expected: {durationLabel(expectedMinutes)}</Text> : null}
        </View>

        <ErrorBox message={error} />

        {paused ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12, borderRadius: 12, backgroundColor: colors.amberSoft }}>
            <Feather name="pause-circle" size={16} color={colors.amber} />
            <Text weight="semibold" style={{ flex: 1, fontSize: 13, color: colors.amber }}>
              NexHome paused this job. The timer starts again by itself when NexHome resumes it.
            </Text>
          </View>
        ) : null}

        <View style={{ gap: 10 }}>
          <Button title="Finish work" icon="check" onPress={onFinish} disabled={busy} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * After Finish work on an unpaid online job (V70): waiting for the customer to confirm the work is done in their
 * app, with the time worked so far (the timer keeps running), Back to the timer and Help. The job page moves on by
 * itself: Payment processing once the customer confirms, the timer again (with their answer) if they say not yet.
 */
export function WaitingConfirmScreen({ booking, workedMinutes, onBack }) {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Waiting for the customer" onBack={onBack} />
      <ScrollView contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
        <View style={{ alignItems: 'center', gap: 8 }}>
          <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name="user-check" size={30} color={colors.blue} />
          </View>
          <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
            Waiting for the customer to confirm
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center', lineHeight: 19 }}>
            Ask {booking.address?.contactName || 'the customer'} to confirm in the NexHome app that {serviceNameOf(booking.serviceName)} is done. The final amount is worked out when they confirm.
          </Text>
        </View>

        <View style={{ alignItems: 'center', gap: 6, paddingVertical: 24, borderRadius: 24, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}>
          <Text style={{ fontSize: 13, color: colors.muted }}>Time worked so far</Text>
          <Text weight="extrabold" style={{ fontSize: 34, color: colors.navy }}>
            {durationLabel(Math.max(1, workedMinutes ?? 0))}
          </Text>
          <Text style={{ fontSize: 12.5, color: colors.muted, textAlign: 'center', paddingHorizontal: 16 }}>
            The timer keeps running until the customer confirms.
          </Text>
        </View>

        <View style={{ gap: 10 }}>
          <Button title="Back to the timer" variant="secondary" icon="arrow-left" onPress={onBack} />
        </View>
        <PaymentHelp publicId={booking.publicId} />
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * After Finish work on a cash job: the amount to collect, large. Cash: Cash collected (marks the cash collection
 * COLLECTED) and Back; online: the customer pays in the app (or it is paid), and Back.
 */
export function CollectScreen({ booking, amount, onCashCollected, onBack }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const cash = booking.paymentMethod === 'CASH';
  const paid = ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(booking.paymentStatus);

  const collected = async () => {
    setBusy(true);
    setError('');
    try {
      await onCashCollected();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Work completed" />
      <ScrollView contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
        <View style={{ alignItems: 'center', gap: 8 }}>
          <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name="check-circle" size={32} color={colors.green} />
          </View>
          <Text weight="extrabold" style={{ fontSize: 18, color: colors.navy }}>
            {serviceNameOf(booking.serviceName)} is done
          </Text>
        </View>

        <View style={{ alignItems: 'center', gap: 6, paddingVertical: 30, paddingHorizontal: 16, borderRadius: 24, backgroundColor: cash ? colors.amberSoft : colors.blueTint }}>
          <Text weight="bold" style={{ fontSize: 14, color: cash ? colors.amber : colors.blue }}>
            {cash ? 'Collect in cash' : paid ? 'Paid online' : 'The customer pays online'}
          </Text>
          <Text weight="extrabold" style={{ fontSize: 56, color: colors.navy }}>
            {money(amount ?? booking.totalAmount)}
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center', lineHeight: 19 }}>
            {cash
              ? 'Take the full amount from the customer, then tap Cash collected.'
              : paid
                ? 'Nothing to collect. Your share is paid to your bank account.'
                : 'Nothing to collect in cash: the customer pays in the NexHome app.'}
          </Text>
        </View>

        <ErrorBox message={error} />

        <View style={{ gap: 10 }}>
          {cash ? <Button title="Cash collected" icon="dollar-sign" onPress={collected} loading={busy} /> : null}
          <Button title="Back" variant="secondary" icon="arrow-left" onPress={onBack} disabled={busy} />
        </View>
        <PaymentHelp publicId={booking.publicId} />
      </ScrollView>
    </SafeAreaView>
  );
}
