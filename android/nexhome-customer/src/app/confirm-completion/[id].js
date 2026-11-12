import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Empty, ErrorBox, FormScroll, Header, Loading, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { needsCompletionConfirmation, workBreakdown } from '../../utils/completion';
import { minutesLabel, money, serviceNameOf } from '../../utils/format';
import { payOnline } from '../../utils/pay';

function Line({ label, value, strong, color }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
      <Text weight={strong ? 'extrabold' : 'regular'} style={{ color: strong ? colors.navy : colors.muted, fontSize: strong ? 15 : 14 }}>
        {label}
      </Text>
      <Text weight={strong ? 'extrabold' : 'bold'} style={{ color: color ?? colors.navy, fontSize: strong ? 17 : 14 }}>
        {value}
      </Text>
    </View>
  );
}

/**
 * The professional tapped Finish work on an online job (V70): the customer confirms the work is done.
 * Not yet (PUT /api/bookings/completion/respond, completed false): the professional carries on; back.
 * Yes (completed true): the work stops and is priced for the time worked; then what it is made of (time worked,
 * the package's time, extra time, extra amount, any online fee, total) and Pay now (the usual payment screens,
 * then the booking) or Pay later (the booking).
 */
export default function ConfirmCompletion() {
  const { id } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const detail = useApi(`/api/bookings/${id}`);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState('');

  if (detail.loading) return <Loading />;
  const data = result ?? detail.data;
  const booking = data?.booking;
  if (!booking) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Confirm your job" />
        <Empty icon="alert-circle" title="Booking not found" text={detail.error || undefined} />
      </SafeAreaView>
    );
  }
  const toBooking = () => router.replace({ pathname: '/booking/[id]', params: { id: String(booking.bookingId) } });

  const answer = async (completed) => {
    setError('');
    setBusy(completed ? 'yes' : 'no');
    try {
      const response = await authRequest('/api/bookings/completion/respond', {
        method: 'PUT',
        body: { bookingId: booking.bookingId, completed }
      });
      if (!completed) {
        if (router.canGoBack()) router.back();
        else toBooking();
        return;
      }
      setResult(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(null);
    }
  };

  const pay = async () => {
    setError('');
    setBusy('pay');
    try {
      const opened = await payOnline(authRequest, booking.bookingId, { next: `/booking/${booking.bookingId}`, replace: true });
      if (opened === 'PAID') toBooking();
    } catch (err) {
      setError(err.message);
      setBusy(null);
    }
  };

  // Step 2: confirmed (the work stopped and was priced): what to pay
  if (result || booking.completionConfirmedAt) {
    const parts = workBreakdown(booking, data.items, data.workedMinutes);
    const fee = Number(booking.transactionFeeAmount ?? 0);
    const paid = ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(booking.paymentStatus);
    return (
      <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Work finished" back={false} />
        <FormScroll contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 16 }}>
          <View style={{ alignItems: 'center', gap: 8 }}>
            <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="check-circle" size={30} color={colors.green} />
            </View>
            <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
              {serviceNameOf(booking.serviceName)} is done
            </Text>
            <Text style={{ color: colors.muted, textAlign: 'center' }}>{booking.publicId}</Text>
          </View>
          <Card style={{ gap: 12 }}>
            {parts.worked != null ? <Line label="Total time worked" value={minutesLabel(parts.worked)} /> : null}
            {parts.packageMinutes ? <Line label="Booked package" value={minutesLabel(parts.packageMinutes)} /> : null}
            {parts.extraMinutes ? <Line label="Extra time" value={minutesLabel(parts.extraMinutes)} color={colors.amber} /> : null}
            {parts.bookedAmount != null ? <Line label="Booked price" value={money(parts.bookedAmount)} /> : null}
            {parts.extraAmount > 0 ? <Line label="Extra amount" value={`+ ${money(parts.extraAmount)}`} color={colors.amber} /> : null}
            {fee > 0 ? <Line label="Online payment fee" value={`+ ${money(fee)}`} /> : null}
            <View style={{ height: 1, backgroundColor: colors.line }} />
            <Line label={paid ? 'Paid' : 'Total to pay'} value={money(booking.totalAmount)} strong />
          </Card>
          <ErrorBox message={error} />
          {paid ? (
            <Button title="Go to booking" icon="arrow-right" onPress={toBooking} />
          ) : (
            <View style={{ gap: 10 }}>
              <Button title={`Pay ${money(booking.totalAmount)} now`} icon="credit-card" loading={busy === 'pay'} onPress={pay} />
              <Button title="Pay later" variant="secondary" disabled={Boolean(busy)} onPress={toBooking} />
            </View>
          )}
        </FormScroll>
      </SafeAreaView>
    );
  }

  // Step 1: is the job done?
  const waiting = needsCompletionConfirmation(booking);
  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Confirm your job" />
      <FormScroll contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
        <View style={{ alignItems: 'center', gap: 8 }}>
          <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name="clipboard" size={30} color={colors.blue} />
          </View>
          <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
            Is your {serviceNameOf(booking.serviceName)} done?
          </Text>
          <Text style={{ color: colors.muted, textAlign: 'center', lineHeight: 20 }}>
            {booking.partnerName ? `${booking.partnerName} says` : 'Your professional says'} the work is finished. Confirm only when you are happy with it; the final amount is worked out for the time worked.
          </Text>
        </View>
        <Card style={{ gap: 12 }}>
          <Line label="Booking" value={booking.publicId} />
          {detail.data?.workedMinutes != null ? <Line label="Time worked so far" value={minutesLabel(detail.data.workedMinutes)} /> : null}
        </Card>
        <ErrorBox message={error} />
        {waiting ? (
          <View style={{ gap: 10 }}>
            <Button title="Yes, the job is done" icon="check" loading={busy === 'yes'} disabled={Boolean(busy)} onPress={() => answer(true)} />
            <Button title="Not yet" variant="secondary" loading={busy === 'no'} disabled={Boolean(busy)} onPress={() => answer(false)} />
          </View>
        ) : (
          <Button title="Go to booking" icon="arrow-right" onPress={toBooking} />
        )}
      </FormScroll>
    </SafeAreaView>
  );
}
