import Feather from '@expo/vector-icons/Feather';
import { LinearGradient } from 'expo-linear-gradient';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Linking, Pressable, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import Progress from '../../components/Progress';
import { Button, Card, Empty, ErrorBox, Field, FormScroll, Header, Loading, SectionTitle, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { minutesLabel, money, rangeLabel, serviceNameOf, whenLabel } from '../../utils/format';
import { payOnline } from '../../utils/pay';
import { needsCompletionConfirmation, promptCompletion, workBreakdown } from '../../utils/completion';
import { promptRating } from '../../utils/rating';
import { CANCELLABLE, UPCOMING, bookingStatus, isPaid, isPaymentDue, paymentStatus } from '../../utils/status';

/** Who cancelled, as a tag: the customer (You), the partner, or NexHome support staff (Admin). */
const CANCELLED_BY = {
  CUSTOMER: { label: 'You', fg: colors.blue, bg: colors.blueTint },
  PARTNER: { label: 'Partner', fg: colors.greenDark, bg: colors.greenSoft },
  STAFF: { label: 'Admin', fg: colors.navy, bg: '#eef2f6' }
};

function Row({ label, value, strong, color }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
      <Text style={{ color: colors.muted, fontSize: 13.5 }}>{label}</Text>
      <Text weight={strong ? 'extrabold' : 'semibold'} style={{ fontSize: strong ? 16 : 13.5, color: color ?? (strong ? colors.navy : colors.ink), flexShrink: 1, textAlign: 'right' }}>
        {value}
      </Text>
    </View>
  );
}

function Stars({ value, onChange, label }) {
  return (
    <View style={{ gap: 6 }}>
      <Text weight="bold" style={{ fontSize: 13 }}>
        {label}
      </Text>
      <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', gap: 4 }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable key={n} accessibilityRole="radio" accessibilityLabel={`${n} star${n > 1 ? 's' : ''}`} accessibilityState={{ selected: value === n }} onPress={() => onChange(n)} hitSlop={4}>
            <Feather name="star" size={30} color={n <= value ? colors.star : '#c3cfdb'} style={n <= value ? { textShadowColor: colors.star, textShadowRadius: 1 } : null} />
          </Pressable>
        ))}
      </View>
    </View>
  );
}


/** A history row's dot: payment failed red, pending amber, cancelled grey, anything else green. */
function historyDot(reason = '') {
  if (reason?.startsWith('Payment failed')) return colors.red;
  if (reason?.startsWith('Payment pending')) return '#e0a100';
  if (reason?.startsWith('Payment cancelled')) return '#a8b6c6';
  return colors.green;
}

/** Who made a history change, as the customer sees it (their own: nothing). */
const HISTORY_BY = { PARTNER: 'by Partner', STAFF: 'by Admin', SYSTEM: 'by NexHome' };

/**
 * One booking (GET /api/bookings/{id}): status and progress, what, when, where, the professional, the price,
 * payment (GET /api/payments/bookings/{id}), history; Cancel with a reason until the work starts, Rate once
 * completed (POST /api/reviews/create), Get help (calls NexHome support).
 */
export default function BookingScreen() {
  const { id, placed, rate } = useLocalSearchParams();
  const { authRequest } = useAuth();
  // While the work is under way it reloads every 15 s (the professional's Finish work asks to confirm); else only
  // when the screen opens
  // Reloaded after every change the professional or NexHome makes (components/LiveWatcher: every 15 s while a
  // professional is assigned or working, and on each new notification)
  const detail = useApi(`/api/bookings/${id}`, { live: 'bookings' });
  const payments = useApi(`/api/payments/bookings/${id}`, { live: 'bookings' });
  const review = useApi(`/api/reviews/bookings/${id}`);
  const contact = useApi('/api/settings/public');
  const supportPhone = contact.data?.SUPPORT_PHONE;
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [rating, setRating] = useState(rate ? { service: 0, partner: 0, text: '' } : null);
  // Completed and not reviewed (the review GET is 404): the rating screen opens (once a session; not if skipped)
  const shown = detail.data?.booking;
  const unrated = shown?.bookingStatus === 'COMPLETED' && isPaid(shown) && Boolean(shown?.partnerProfileId) && !review.loading && !review.data?.reviewId;
  useEffect(() => {
    if (unrated && !rate) promptRating(shown.bookingId);
  }, [unrated, rate, shown?.bookingId]);
  // The professional finished the work: confirm it is done (once per request)
  const toConfirm = needsCompletionConfirmation(shown);
  useEffect(() => {
    if (toConfirm) promptCompletion(shown);
  }, [toConfirm, shown]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // An online payment left waiting for the bank (or paid after the payment screen closed): ask the gateway once
  // per order when the booking opens, which records it if paid, then show the booking again
  const checkedOrders = useRef(new Set());
  const openOrder = (payments.data?.orders ?? []).find(
    (order) => order.provider !== 'MANUAL' && ['CREATED', 'PENDING'].includes(order.paymentStatus)
  );
  const unpaidOnline = detail.data?.booking?.paymentMethod === 'ONLINE' && detail.data?.booking?.paymentStatus === 'PENDING';
  useEffect(() => {
    if (!openOrder || !unpaidOnline || checkedOrders.current.has(openOrder.paymentOrderId)) return;
    checkedOrders.current.add(openOrder.paymentOrderId);
    authRequest('/api/payments/orders/verify', { method: 'POST', body: { paymentOrderId: openOrder.paymentOrderId } })
      .then((order) => {
        if (order.paymentStatus === 'PAID') {
          detail.reload();
          payments.reload();
        }
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openOrder?.paymentOrderId, unpaidOnline]);

  if (detail.loading) return <Loading />;
  const booking = detail.data?.booking;
  if (!booking) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Booking" />
        <Empty icon="alert-circle" title="Booking not found" text={detail.error || undefined} />
      </SafeAreaView>
    );
  }

  // Completed but not paid yet: shown as Payment pending (amber) until it is
  const paymentDue = isPaymentDue(booking);
  const status = paymentDue ? { label: 'Payment pending', fg: colors.amber, bg: colors.amberSoft } : bookingStatus(booking.bookingStatus);
  const pay = paymentStatus(booking.paymentStatus);
  const address = booking.address ?? {};
  const cash = payments.data?.cashCollection;
  const hasReview = Boolean(review.data?.reviewId);

  const run = async (action, after) => {
    setError('');
    setBusy(true);
    try {
      await action();
      after?.();
      detail.reload();
      payments.reload();
      review.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  // Online payment through the gateway's secure page; the backend records it once the gateway says it is paid
  const payNow = () =>
    run(async () => {
      setNotice('');
      // The payment, result and rating screens follow, then back here
      const result = await payOnline(authRequest, booking.bookingId, { next: 'back' });
      if (result === 'PAID') setNotice('Payment received. Thank you!');
    });

  const cancel = () => {
    if (!reason.trim()) {
      setError('Tell us why you are cancelling.');
      return;
    }
    run(() => authRequest('/api/bookings/cancel', { method: 'PUT', body: { bookingId: booking.bookingId, reason: reason.trim() } }), () => setCancelling(false));
  };

  const submitReview = () => {
    if (!rating.service || !rating.partner) {
      setError('Rate both the service and the professional.');
      return;
    }
    run(
      () =>
        authRequest('/api/reviews/create', {
          method: 'POST',
          body: { bookingId: booking.bookingId, serviceRating: rating.service, partnerRating: rating.partner, reviewText: rating.text.trim() || null }
        }),
      () => setRating(null)
    );
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={booking.publicId} />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 12 }}>
        {toConfirm ? (
          <View style={{ backgroundColor: colors.blueTint, borderRadius: 16, padding: 14, gap: 10 }}>
            <View style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
              <Feather name="clipboard" size={22} color={colors.blue} />
              <Text weight="bold" style={{ flex: 1, color: colors.navy }}>
                Your professional says the job is done. Please confirm.
              </Text>
            </View>
            <Button title="Confirm the job" icon="check" onPress={() => router.push({ pathname: '/confirm-completion/[id]', params: { id: String(booking.bookingId) } })} />
          </View>
        ) : null}
        {paymentDue ? (
          <View style={{ backgroundColor: colors.amberSoft, borderRadius: 16, padding: 14, gap: 10 }}>
            <View style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
              <Feather name="clock" size={22} color={colors.amber} />
              <View style={{ flex: 1, gap: 2 }}>
                <Text weight="extrabold" style={{ color: colors.amber, fontSize: 15 }}>
                  Payment pending
                </Text>
                <Text style={{ color: colors.amber, fontSize: 13 }}>
                  {booking.paymentMethod === 'CASH'
                    ? `The job is done. Please pay ${money(booking.totalAmount)} in cash to your professional.`
                    : `The job is done. Please pay ${money(booking.totalAmount)} to complete your booking.`}
                </Text>
              </View>
            </View>
            {booking.paymentMethod === 'ONLINE' ? (
              <Button title={`Pay ${money(booking.totalAmount)} now`} icon="credit-card" loading={busy} onPress={payNow} />
            ) : null}
          </View>
        ) : null}
        {placed ? (
          <View style={{ backgroundColor: colors.greenSoft, borderRadius: 16, padding: 14, flexDirection: 'row', gap: 10, alignItems: 'center' }}>
            <Feather name="check-circle" size={22} color={colors.green} />
            <Text weight="bold" style={{ color: colors.greenDark, flex: 1 }}>
              Booking placed. We will confirm it shortly.
            </Text>
          </View>
        ) : null}

        {booking.startOtp ? (
          // The start code (V59): the professional enters it to start the work
          <LinearGradient colors={[colors.blue, '#1660b8']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ borderRadius: 18, padding: 16, gap: 10 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Feather name="key" size={17} color={colors.white} />
              <Text weight="extrabold" style={{ color: colors.white, fontSize: 15 }}>
                Start code
              </Text>
            </View>
            <View style={{ flexDirection: 'row', gap: 10 }}>
              {booking.startOtp.split('').map((digit, i) => (
                <View key={i} style={{ width: 50, height: 56, borderRadius: 14, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
                  <Text weight="extrabold" style={{ fontSize: 26, color: colors.navy }}>
                    {digit}
                  </Text>
                </View>
              ))}
            </View>
            <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5, lineHeight: 18 }}>
              Share this code with your professional when they arrive. They need it to start the work. Do not share it before they are with you.
            </Text>
          </LinearGradient>
        ) : null}

        <Card style={{ gap: 12, borderRadius: 18 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text weight="extrabold" style={{ fontSize: 17 }}>
                {serviceNameOf(booking.serviceName)}
              </Text>
              {detail.data.categoryName ? (
                <Text weight="bold" style={{ fontSize: 12, color: colors.blue }}>
                  {detail.data.categoryName}
                </Text>
              ) : null}
              <Text style={{ fontSize: 12.5, color: colors.muted }}>{booking.scheduledStartAt ? rangeLabel(booking.scheduledStartAt, booking.scheduledEndAt) : 'As soon as possible'}</Text>
            </View>
            <Tag label={status.label} fg={status.fg} bg={status.bg} />
          </View>
          {UPCOMING.includes(booking.bookingStatus) ? <Progress status={booking.bookingStatus} /> : null}
          {booking.bookingStatus === 'CANCELLED' ? (
            <View style={{ gap: 7, padding: 11, borderRadius: 12, backgroundColor: colors.redSoft }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                <Text style={{ fontSize: 12.5, color: colors.muted }}>Cancelled by</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flexShrink: 1 }}>
                  {CANCELLED_BY[booking.cancelledByRole] ? (
                    <View style={{ backgroundColor: CANCELLED_BY[booking.cancelledByRole].bg, borderRadius: 999, paddingHorizontal: 7, paddingVertical: 2 }}>
                      <Text weight="extrabold" style={{ fontSize: 10, color: CANCELLED_BY[booking.cancelledByRole].fg }}>
                        {CANCELLED_BY[booking.cancelledByRole].label}
                      </Text>
                    </View>
                  ) : null}
                  <Text weight="semibold" style={{ fontSize: 12.5, flexShrink: 1 }} numberOfLines={1}>
                    {booking.cancelledByName ?? '—'}
                  </Text>
                </View>
              </View>
              {booking.cancelledAt ? (
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                  <Text style={{ fontSize: 12.5, color: colors.muted }}>When</Text>
                  <Text weight="semibold" style={{ fontSize: 12.5 }}>
                    {whenLabel(booking.cancelledAt)}
                  </Text>
                </View>
              ) : null}
              {booking.cancellationReason ? (
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                  <Text style={{ fontSize: 12.5, color: colors.muted }}>Reason</Text>
                  <Text weight="semibold" style={{ fontSize: 12.5, color: colors.red, flexShrink: 1, textAlign: 'right' }}>
                    {booking.cancellationReason}
                  </Text>
                </View>
              ) : null}
            </View>
          ) : (
            <Text style={{ fontSize: 13, color: colors.muted }}>{status.note}</Text>
          )}
          {booking.partnerName ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 12, backgroundColor: colors.ground }}>
              <View style={{ width: 38, height: 38, borderRadius: 19, backgroundColor: colors.blue, alignItems: 'center', justifyContent: 'center' }}>
                <Text weight="extrabold" style={{ color: colors.white, fontSize: 13 }}>
                  {booking.partnerName
                    .split(' ')
                    .map((part) => part[0])
                    .join('')
                    .slice(0, 2)
                    .toUpperCase()}
                </Text>
              </View>
              <View>
                <Text weight="bold" style={{ fontSize: 13 }}>
                  {booking.partnerName}
                </Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>Your professional</Text>
              </View>
            </View>
          ) : null}
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Details</SectionTitle>
          {(detail.data.items ?? []).map((item) => (
            <Row key={item.bookingItemId} label={item.variant?.name ?? 'Service'} value={money(item.lineTotal)} />
          ))}
          <Row label="Booking" value={booking.bookingType === 'INSTANT' ? 'As soon as possible' : 'Scheduled'} />
          <Row label="Booked time" value={booking.scheduledStartAt ? rangeLabel(booking.scheduledStartAt, booking.scheduledEndAt) : 'As soon as possible'} />
          {booking.actualStartAt ? (
            <Row
              label={booking.actualEndAt ? 'Actual time' : 'Started'}
              value={booking.actualEndAt ? rangeLabel(booking.actualStartAt, booking.actualEndAt) : whenLabel(booking.actualStartAt)}
              color={colors.greenDark}
            />
          ) : null}
        </Card>

        {/* The full address the service is at (as booked) */}
        <Card style={{ gap: 10 }}>
          <SectionTitle>Service address</SectionTitle>
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <View style={{ width: 34, height: 34, borderRadius: 10, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="map-pin" size={17} color={colors.green} />
            </View>
            <View style={{ flex: 1, gap: 3 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                <Text weight="bold" style={{ fontSize: 14 }}>
                  {address.label || (address.addressType ? address.addressType[0] + address.addressType.slice(1).toLowerCase() : 'Address')}
                </Text>
              </View>
              {address.contactName || address.mobileNumber ? (
                <Text weight="semibold" style={{ fontSize: 13 }}>
                  {[address.contactName, address.mobileNumber].filter(Boolean).join(' · ')}
                </Text>
              ) : null}
              {[address.addressLine1, address.addressLine2].filter(Boolean).map((line) => (
                <Text key={line} style={{ fontSize: 13, color: colors.muted, lineHeight: 19 }}>
                  {line}
                </Text>
              ))}
              {address.landmark ? <Text style={{ fontSize: 13, color: colors.muted, lineHeight: 19 }}>Near {address.landmark}</Text> : null}
              <Text style={{ fontSize: 13, color: colors.muted, lineHeight: 19 }}>
                {[address.city, address.state].filter(Boolean).join(', ')}
                {address.postalCode ? ` – ${address.postalCode}` : ''}
              </Text>
            </View>
          </View>
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Price</SectionTitle>
          {/* Priced for the time worked (the job was confirmed done): what the final amount is made of */}
          {booking.pricing?.workedMinutes != null
            ? (() => {
                const parts = workBreakdown(booking, detail.data.items, detail.data.workedMinutes);
                return (
                  <>
                    <Row label="Total time worked" value={minutesLabel(parts.worked)} />
                    {parts.packageMinutes ? <Row label="Booked package" value={minutesLabel(parts.packageMinutes)} /> : null}
                    {parts.extraMinutes ? <Row label="Extra time" value={minutesLabel(parts.extraMinutes)} color={colors.amber} /> : null}
                    {parts.bookedAmount != null ? <Row label="Booked price" value={money(parts.bookedAmount)} /> : null}
                    {parts.extraAmount > 0 ? <Row label="Extra amount" value={`+ ${money(parts.extraAmount)}`} color={colors.amber} /> : null}
                    <View style={{ height: 1, backgroundColor: colors.line }} />
                  </>
                );
              })()
            : null}
          <Row label="Regular price" value={money(booking.subtotalAmount)} />
          {Number(booking.discountAmount) > 0 ? <Row label="Discount" value={`− ${money(booking.discountAmount)}`} color={colors.greenDark} /> : null}
          {Number(booking.transactionFeeAmount) > 0 ? <Row label="Online payment fee" value={`+ ${money(booking.transactionFeeAmount)}`} /> : null}
          <View style={{ height: 1, backgroundColor: colors.line }} />
          <Row label="Total" value={money(booking.totalAmount)} strong />
          {/* Cashfree's gateway charges the customer paid on top (V69), not part of the booking's price */}
          {Number(booking.gatewayServiceCharge ?? 0) + Number(booking.gatewayServiceTax ?? 0) > 0 ? (
            <>
              <Row label="Gateway charges (Cashfree)" value={`+ ${money(booking.gatewayServiceCharge)}`} />
              <Row label="GST on gateway charges" value={`+ ${money(booking.gatewayServiceTax)}`} />
              <Row label="You paid" value={money(Number(booking.totalAmount) + Number(booking.gatewayServiceCharge) + Number(booking.gatewayServiceTax))} strong />
            </>
          ) : null}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={{ fontSize: 13, color: colors.muted }}>{booking.paymentMethod === 'ONLINE' ? 'Online payment' : 'Cash after service'}</Text>
            {pay ? <Tag label={pay.label} fg={pay.fg} bg={pay.bg} /> : null}
          </View>
          {/* Paid once the work is done: finished (the customer confirmed it) or completed */}
          {booking.paymentMethod === 'ONLINE' && ['PENDING', 'FAILED'].includes(booking.paymentStatus) && paymentDue ? (
            <View style={{ gap: 6 }}>
              <Button title={`Pay ${money(booking.totalAmount)} now`} icon="credit-card" loading={busy} onPress={payNow} />
              <Text style={{ fontSize: 12, color: colors.muted, textAlign: 'center' }}>UPI, card or net banking through Cashfree’s secure page. Cashfree’s gateway charges and GST, if any, are added at payment.</Text>
            </View>
          ) : null}
          {notice ? <Text style={{ fontSize: 13, color: colors.greenDark, fontWeight: '700' }}>{notice}</Text> : null}
          {cash?.status === 'COLLECTED' && !cash.customerAcknowledgedAt ? (
            <Button
              title="I paid the professional"
              variant="secondary"
              loading={busy}
              onPress={() => run(() => authRequest('/api/payments/cash-collections/acknowledge', { method: 'PUT', body: { cashCollectionId: cash.cashCollectionId } }))}
            />
          ) : null}
        </Card>

        {booking.bookingStatus === 'COMPLETED' && hasReview ? (
          <Card style={{ gap: 6 }}>
            <SectionTitle>Your review</SectionTitle>
            <Text style={{ color: colors.muted }}>
              Service {review.data.serviceRating}/5 · Professional {review.data.partnerRating}/5
            </Text>
            {review.data.reviewText ? <Text>{review.data.reviewText}</Text> : null}
          </Card>
        ) : null}

        {booking.bookingStatus === 'COMPLETED' && !hasReview && rating ? (
          <Card style={{ gap: 14 }}>
            <SectionTitle>How was it?</SectionTitle>
            <Stars label="The service" value={rating.service} onChange={(n) => setRating({ ...rating, service: n })} />
            <Stars label="The professional" value={rating.partner} onChange={(n) => setRating({ ...rating, partner: n })} />
            <Field label="Anything to add? (optional)" value={rating.text} onChangeText={(text) => setRating({ ...rating, text })} multiline maxLength={2000} />
            <Button title="Submit review" onPress={submitReview} loading={busy} />
          </Card>
        ) : null}

        {(detail.data.history ?? []).length ? (
          <Card style={{ gap: 10 }}>
            <SectionTitle>History</SectionTitle>
            {detail.data.history
              .filter((h) => h.oldStatus !== h.newStatus || h.reason)
              .map((h) => (
                <View key={h.historyId} style={{ flexDirection: 'row', gap: 10 }}>
                  <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: historyDot(h.reason), marginTop: 5 }} />
                  <View style={{ flex: 1 }}>
                    <Text weight="semibold" style={{ fontSize: 13 }}>
                      {h.oldStatus === h.newStatus ? h.reason : bookingStatus(h.newStatus).label}
                    </Text>
                    {/* A status change a payment brought (Confirmed by "Payment received · ₹…") shows the payment */}
                    {h.oldStatus !== h.newStatus && h.reason?.startsWith('Payment') ? (
                      <Text style={{ fontSize: 12, color: colors.ink }}>{h.reason}</Text>
                    ) : null}
                    <Text style={{ fontSize: 12, color: colors.muted }}>
                      {whenLabel(h.changedAt)}
                      {HISTORY_BY[h.changedByParty] ? ` · ${HISTORY_BY[h.changedByParty]}` : ''}
                    </Text>
                  </View>
                </View>
              ))}
          </Card>
        ) : null}

        {cancelling ? (
          <Card style={{ gap: 12 }}>
            <SectionTitle>Cancel this booking?</SectionTitle>
            <Field label="Reason" value={reason} onChangeText={setReason} placeholder="e.g. Plans changed" maxLength={500} multiline />
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Button title="Keep booking" variant="secondary" onPress={() => setCancelling(false)} style={{ flex: 1 }} />
              <Button title="Cancel booking" variant="danger" onPress={cancel} loading={busy} style={{ flex: 1 }} />
            </View>
          </Card>
        ) : null}
        <ErrorBox message={error} />

        <View style={{ gap: 10 }}>
          {booking.bookingStatus === 'COMPLETED' && !hasReview && !rating ? (
            <Button title="Rate this service" variant="blue" icon="star" onPress={() => setRating({ service: 0, partner: 0, text: '' })} />
          ) : null}
          {CANCELLABLE.includes(booking.bookingStatus) && !cancelling ? (
            <Button title="Cancel booking" variant="danger" onPress={() => setCancelling(true)} />
          ) : null}
          <Button
            title="Get help with this booking"
            variant="secondary"
            icon="headphones"
            // Calls NexHome support (General Settings); without a number set, opens the Support tab
            onPress={() => (supportPhone ? Linking.openURL(`tel:${supportPhone.replace(/[^\d+]/g, '')}`) : router.push('/(tabs)/support'))}
          />
        </View>
      </FormScroll>
    </SafeAreaView>
  );
}
