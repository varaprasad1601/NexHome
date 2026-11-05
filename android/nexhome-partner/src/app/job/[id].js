import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Linking, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import OfferCard from '../../components/OfferCard';
import Progress from '../../components/Progress';
import { Avatar, Banner, Button, Card, Empty, ErrorBox, Header, Loading, Row, SectionTitle, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { dayLabel, durationLabel, minutesLabel, money, parseApiDate, serviceNameOf, whenLabel } from '../../utils/format';
import { UPCOMING, bookingStatus } from '../../utils/status';

/**
 * Minutes worked so far, worked out from the timer events as the backend does (START / RESUME to PAUSE / STOP, an
 * open stretch up to `now`, rounded up), so it keeps counting while the work runs; the backend's figure otherwise.
 */
function liveMinutes(detail, now) {
  let seconds = 0;
  let since = null;
  for (const log of detail.timeLogs ?? []) {
    const at = parseApiDate(log.eventAt)?.date?.getTime();
    if (at === undefined) return Number(detail.workedMinutes) || 0;
    if (log.eventType === 'START' || log.eventType === 'RESUME') since = at;
    else if ((log.eventType === 'PAUSE' || log.eventType === 'STOP') && since !== null) {
      seconds += (at - since) / 1000;
      since = null;
    }
  }
  if (since !== null) seconds += Math.max(0, (now - since) / 1000);
  return Math.ceil(seconds / 60);
}

/**
 * One job (GET /api/bookings/{id}): status and progress; the customer with Call and Open in Maps (from the booking's
 * address snapshot); what, when, price and payment; actions by state: accept / decline an offer or assignment
 * (PUT /api/bookings/assignments/respond), Start work, Pause, Resume, Finish work (POST /api/bookings/timer), and for
 * a cash job once completed, Cash collected (PUT /api/payments/cash-collections/update, COLLECTED); the time worked;
 * history; Get help.
 */
export default function JobScreen() {
  const { id } = useLocalSearchParams();
  const { authRequest, profile } = useAuth();
  const detail = useApi(`/api/bookings/${id}`);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const running = detail.data?.booking?.bookingStatus === 'IN_PROGRESS';
  useEffect(() => {
    if (!running) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 20000);
    return () => clearInterval(timer);
  }, [running]);

  if (detail.loading) return <Loading />;
  const booking = detail.data?.booking;
  if (!booking) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Job" />
        <Empty icon="alert-circle" title="Job not found" text={detail.error || undefined} />
      </SafeAreaView>
    );
  }

  const status = bookingStatus(booking.bookingStatus);
  const address = booking.address ?? {};
  const mine = (detail.data.assignments ?? []).filter((a) => a.partnerProfileId === profile?.partnerProfileId);
  const answer = mine.find((a) => a.assignmentStatus === 'OFFERED' || (a.assignmentStatus === 'ASSIGNED' && ['ASSIGNED', 'PARTNER_EN_ROUTE'].includes(booking.bookingStatus)));
  const isMine = booking.partnerProfileId === profile?.partnerProfileId;
  const worked = liveMinutes(detail.data, now);
  const items = detail.data.items ?? [];
  const duration = items.find((item) => item.variant?.durationMinutes)?.variant?.durationMinutes ?? booking.pricing?.durationMinutes;
  const fullAddress = [address.addressLine1, address.addressLine2, address.landmark, address.city, address.state, address.postalCode].filter(Boolean).join(', ');

  const run = async (action, message) => {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      await action();
      if (message) setNotice(message);
      await detail.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const timer = (eventType, message) => run(() => authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType } }), message);

  const finish = () =>
    Alert.alert('Finish this job?', booking.paymentMethod === 'CASH' ? `Then collect ${money(booking.totalAmount)} in cash from the customer.` : 'The customer will be asked to rate the work.', [
      { text: 'Not yet', style: 'cancel' },
      { text: 'Finish work', onPress: () => timer('STOP', 'Job completed. Well done!') }
    ]);

  const cashCollected = () =>
    run(async () => {
      const payments = await authRequest(`/api/payments/bookings/${booking.bookingId}`);
      const cash = payments?.cashCollection;
      if (!cash) throw new Error('No cash is due for this job.');
      if (cash.status === 'COLLECTED') return;
      await authRequest('/api/payments/cash-collections/update', { method: 'PUT', body: { cashCollectionId: cash.cashCollectionId, status: 'COLLECTED' } });
    }, 'Cash marked as collected.');

  const openMaps = () => {
    const query = address.latitude && address.longitude ? `${address.latitude},${address.longitude}` : fullAddress;
    Linking.openURL(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`);
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={booking.publicId} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={detail.refreshing} onRefresh={detail.refresh} />}>
        {notice ? <Banner tone="success" text={notice} /> : null}

        <Card style={{ gap: 12, borderRadius: 18 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text weight="extrabold" style={{ fontSize: 17 }}>
                {serviceNameOf(booking.serviceName)}
              </Text>
              <Text style={{ fontSize: 12.5, color: colors.muted }}>{booking.scheduledStartAt ? whenLabel(booking.scheduledStartAt) : 'As soon as possible'}</Text>
            </View>
            <Tag label={status.label} fg={status.fg} bg={status.bg} />
          </View>
          {UPCOMING.includes(booking.bookingStatus) && status.step ? <Progress step={status.step} /> : null}
          <Text style={{ fontSize: 13, color: colors.muted }}>{booking.bookingStatus === 'CANCELLED' && booking.cancellationReason ? `Cancelled: ${booking.cancellationReason}` : status.note}</Text>
          {['IN_PROGRESS', 'PAUSED', 'COMPLETED'].includes(booking.bookingStatus) ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 12, backgroundColor: colors.ground }}>
              <Feather name="clock" size={16} color={colors.blue} />
              <Text weight="bold" style={{ color: colors.navy }}>
                {minutesLabel(worked)} worked
              </Text>
            </View>
          ) : null}
        </Card>

        {answer ? <OfferCard assignment={answer} inJob onDone={() => detail.reload()} /> : null}

        <Card style={{ gap: 12 }}>
          <SectionTitle>Customer</SectionTitle>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
            <Avatar name={address.contactName || booking.customerName} size={44} />
            <View style={{ flex: 1 }}>
              <Text weight="bold">{address.contactName || booking.customerName}</Text>
              {address.mobileNumber ? <Text style={{ fontSize: 12.5, color: colors.muted }}>+91 {address.mobileNumber}</Text> : null}
            </View>
          </View>
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <Feather name="map-pin" size={16} color={colors.green} style={{ marginTop: 2 }} />
            <Text style={{ flex: 1, lineHeight: 20 }}>{fullAddress || '—'}</Text>
          </View>
          {isMine && UPCOMING.includes(booking.bookingStatus) ? (
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Button title="Call" variant="secondary" icon="phone" disabled={!address.mobileNumber} onPress={() => Linking.openURL(`tel:+91${address.mobileNumber}`)} style={{ flex: 1 }} />
              <Button title="Open in Maps" variant="secondary" icon="navigation" disabled={!fullAddress} onPress={openMaps} style={{ flex: 1 }} />
            </View>
          ) : null}
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Job details</SectionTitle>
          {items.map((item) => (
            <Row key={item.bookingItemId} label={item.variant?.name ?? 'Service'} value={item.variant?.durationMinutes ? minutesLabel(item.variant.durationMinutes) : '1 job'} />
          ))}
          <Row label="Booking" value={booking.bookingType === 'INSTANT' ? 'As soon as possible' : 'Scheduled'} />
          {booking.scheduledStartAt ? <Row label="Scheduled" value={whenLabel(booking.scheduledStartAt)} /> : null}
          {duration ? <Row label="Expected time" value={durationLabel(duration)} /> : null}
          {booking.actualStartAt ? <Row label="Started" value={whenLabel(booking.actualStartAt)} /> : null}
          {booking.actualEndAt ? <Row label="Completed" value={whenLabel(booking.actualEndAt)} /> : null}
          {detail.data.categoryName ? <Row label="Category" value={detail.data.categoryName} /> : null}
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Payment</SectionTitle>
          <Row label="Job total" value={money(booking.totalAmount)} strong />
          <Row label="Paid by" value={booking.paymentMethod === 'CASH' ? 'Cash to you' : 'Online'} />
          {booking.paymentMethod === 'CASH' && booking.bookingStatus !== 'CANCELLED' ? (
            <Text style={{ fontSize: 12.5, color: colors.muted }}>
              {booking.paymentStatus === 'PAID' ? 'The cash is recorded as collected.' : `Collect ${money(booking.totalAmount)} from the customer after the work.`}
            </Text>
          ) : null}
          {booking.paymentMethod === 'ONLINE' ? <Text style={{ fontSize: 12.5, color: colors.muted }}>Your share is paid to your bank account after the job. See Earnings.</Text> : null}
        </Card>

        {(detail.data.history ?? []).length ? (
          <Card style={{ gap: 10 }}>
            <SectionTitle>History</SectionTitle>
            {detail.data.history
              .filter((h) => h.oldStatus !== h.newStatus || h.reason)
              .map((h) => (
                <View key={h.historyId} style={{ flexDirection: 'row', gap: 10 }}>
                  <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colors.green, marginTop: 5 }} />
                  <View style={{ flex: 1 }}>
                    <Text weight="semibold" style={{ fontSize: 13 }}>
                      {h.oldStatus === h.newStatus ? h.reason : bookingStatus(h.newStatus).label}
                    </Text>
                    <Text style={{ fontSize: 12, color: colors.muted }}>{whenLabel(h.changedAt)}</Text>
                  </View>
                </View>
              ))}
          </Card>
        ) : null}

        <ErrorBox message={error} />

        {isMine ? (
          <View style={{ gap: 10 }}>
            {booking.bookingStatus === 'PARTNER_EN_ROUTE' ? <Button title="Start work" icon="play" onPress={() => timer('START', 'Work started.')} loading={busy} /> : null}
            {booking.bookingStatus === 'IN_PROGRESS' ? (
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <Button title="Pause" variant="secondary" icon="pause" onPress={() => timer('PAUSE', 'Work paused.')} disabled={busy} style={{ flex: 1 }} />
                <Button title="Finish work" icon="check" onPress={finish} loading={busy} style={{ flex: 1 }} />
              </View>
            ) : null}
            {booking.bookingStatus === 'PAUSED' ? (
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <Button title="Resume" variant="blue" icon="play" onPress={() => timer('RESUME', 'Work resumed.')} loading={busy} style={{ flex: 1 }} />
                <Button title="Finish work" icon="check" onPress={finish} disabled={busy} style={{ flex: 1 }} />
              </View>
            ) : null}
            {booking.bookingStatus === 'COMPLETED' && booking.paymentMethod === 'CASH' && booking.paymentStatus !== 'PAID' ? (
              <Button title={`Cash collected (${money(booking.totalAmount)})`} icon="dollar-sign" onPress={cashCollected} loading={busy} />
            ) : null}
          </View>
        ) : null}
        <Button
          title="Get help with this job"
          variant="secondary"
          icon="life-buoy"
          onPress={() => router.push({ pathname: '/ticket/new', params: { bookingId: booking.bookingId, bookingPublicId: booking.publicId } })}
        />
        {booking.bookingStatus === 'COMPLETED' ? <Text style={{ color: colors.muted, fontSize: 12, textAlign: 'center' }}>Completed on {dayLabel(booking.actualEndAt)}</Text> : null}
      </ScrollView>
    </SafeAreaView>
  );
}
