import Feather from '@expo/vector-icons/Feather';
import { useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, ErrorBox, Field, FormScroll, Header, Loading, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { money, serviceNameOf } from '../../utils/format';
import { finishPayment } from '../../utils/pay';
import { markRatingShown, skipRating } from '../../utils/rating';

function Stars({ value, onChange, label }) {
  return (
    <View style={{ gap: 8, alignItems: 'center' }}>
      <Text weight="bold" style={{ fontSize: 14 }}>
        {label}
      </Text>
      <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', gap: 8 }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable
            key={n}
            accessibilityRole="radio"
            accessibilityLabel={`${n} star${n > 1 ? 's' : ''}`}
            accessibilityState={{ selected: value === n }}
            onPress={() => onChange(n)}
            hitSlop={4}
          >
            <Feather name="star" size={34} color={n <= value ? colors.star : '#c3cfdb'} />
          </Pressable>
        ))}
      </View>
    </View>
  );
}

/**
 * Rate a completed booking (after paying for it: payment-status's Continue): the service and the professional
 * (1-5 stars) and an optional note, POST /api/reviews/create; Skip goes straight on. Then the booking (`next`, see
 * utils/pay.finishPayment).
 */
export default function RateBooking() {
  const { id, next, paid } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const detail = useApi(`/api/bookings/${id}`);
  const payments = useApi(`/api/payments/bookings/${id}`);
  const [rating, setRating] = useState({ service: 0, partner: 0, text: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // The payment step first (cash: the customer confirms paying; online: what was paid, Continue), then the rating
  const [paymentAnswered, setPaymentAnswered] = useState(false);

  useEffect(() => {
    markRatingShown(id);
  }, [id]);

  if (detail.loading || payments.loading) return <Loading />;
  const booking = detail.data?.booking;
  const cash = payments.data?.cashCollection;
  const confirmCash =
    !paymentAnswered && booking?.paymentMethod === 'CASH' && cash && ['COLLECTED', 'DUE'].includes(cash.status) && !cash.customerAcknowledgedAt;
  // Online and paid, unless the payment screens just showed it (`paid`): the same step, no confirmation
  const paidOnline =
    !paymentAnswered && !paid && booking?.paymentMethod === 'ONLINE' && ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(booking?.paymentStatus);
  const onlineAmount = booking
    ? Number(booking.totalAmount ?? 0) + Number(booking.gatewayServiceCharge ?? 0) + Number(booking.gatewayServiceTax ?? 0)
    : 0;

  const confirmPaid = async () => {
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/payments/cash-collections/acknowledge', { method: 'PUT', body: { cashCollectionId: cash.cashCollectionId } });
      setPaymentAnswered(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  // Step 1 for an online job: what was paid, then Continue to the rating
  if (paidOnline) {
    return (
      <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Payment received" back={false} />
        <FormScroll contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
          <View style={{ alignItems: 'center', gap: 8 }}>
            <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="check-circle" size={30} color={colors.green} />
            </View>
            <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
              {booking ? serviceNameOf(booking.serviceName) : 'Your service'} is done
            </Text>
          </View>
          <Card style={{ alignItems: 'center', gap: 6, paddingVertical: 26 }}>
            <Text weight="bold" style={{ fontSize: 14, color: colors.muted }}>
              Paid online
            </Text>
            <Text weight="extrabold" style={{ fontSize: 44, color: colors.navy }}>
              {money(onlineAmount)}
            </Text>
            <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center' }}>
              Your payment for this booking is received. Thank you!
            </Text>
          </Card>
          <Button title="Continue" icon="arrow-right" onPress={() => setPaymentAnswered(true)} />
        </FormScroll>
      </SafeAreaView>
    );
  }

  // Step 1 for a cash job: did you pay the professional?
  if (confirmCash) {
    return (
      <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Confirm your payment" back={false} />
        <FormScroll contentContainerStyle={{ flexGrow: 1, padding: 20, gap: 18, justifyContent: 'center' }}>
          <View style={{ alignItems: 'center', gap: 8 }}>
            <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="dollar-sign" size={30} color={colors.green} />
            </View>
            <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
              {booking ? serviceNameOf(booking.serviceName) : 'Your service'} is done
            </Text>
          </View>
          <Card style={{ alignItems: 'center', gap: 6, paddingVertical: 26 }}>
            <Text weight="bold" style={{ fontSize: 14, color: colors.muted }}>
              Paid in cash
            </Text>
            <Text weight="extrabold" style={{ fontSize: 44, color: colors.navy }}>
              {money(cash.amount)}
            </Text>
            <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center' }}>
              {booking?.partnerName
                ? `Please confirm you paid this amount in cash to ${booking.partnerName}.`
                : 'Please confirm you paid this amount in cash to your professional.'}
            </Text>
          </Card>
          <ErrorBox message={error} />
          <View style={{ gap: 10 }}>
            <Button title={`Yes, I paid ${money(cash.amount)}`} icon="check" loading={busy} onPress={confirmPaid} />
            <Button title="Not now" variant="secondary" disabled={busy} onPress={() => setPaymentAnswered(true)} />
          </View>
        </FormScroll>
      </SafeAreaView>
    );
  }

  const submit = async () => {
    if (!rating.service || !rating.partner) {
      setError('Rate both the service and the professional.');
      return;
    }
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/reviews/create', {
        method: 'POST',
        body: { bookingId: Number(id), serviceRating: rating.service, partnerRating: rating.partner, reviewText: rating.text.trim() || null }
      });
      finishPayment(next);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Rate your service" back={false} right={<Button title="Skip" variant="secondary" onPress={() => {
            skipRating(id);
            finishPayment(next);
          }} style={{ paddingVertical: 8, paddingHorizontal: 14 }} />} />
      <FormScroll contentContainerStyle={{ padding: 20, gap: 16 }}>
        <View style={{ alignItems: 'center', gap: 6 }}>
          <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy, textAlign: 'center' }}>
            How was your {booking ? serviceNameOf(booking.serviceName) : 'service'}?
          </Text>
          <Text style={{ color: colors.muted, textAlign: 'center' }}>
            {booking?.partnerName ? `Your feedback helps ${booking.partnerName} and other customers.` : 'Your feedback helps us and other customers.'}
          </Text>
        </View>
        <Card style={{ gap: 20, paddingVertical: 22 }}>
          <Stars label="The service" value={rating.service} onChange={(n) => setRating({ ...rating, service: n })} />
          <Stars label="The professional" value={rating.partner} onChange={(n) => setRating({ ...rating, partner: n })} />
        </Card>
        <Field label="Anything to add? (optional)" value={rating.text} onChangeText={(text) => setRating({ ...rating, text })} multiline maxLength={2000} />
        <ErrorBox message={error} />
        <Button title="Submit rating" loading={busy} onPress={submit} />
      </FormScroll>
    </SafeAreaView>
  );
}
