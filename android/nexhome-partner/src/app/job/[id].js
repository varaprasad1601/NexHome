import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import OfferCard from '../../components/OfferCard';
import Progress from '../../components/Progress';
import StartCodeModal from '../../components/StartCodeModal';
import { CollectScreen, WorkingScreen } from '../../components/WorkScreens';
import { Avatar, Banner, Button, Card, Empty, ErrorBox, Header, Loading, Row, SectionTitle, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { dayLabel, durationLabel, minutesLabel, money, parseApiDate, serviceNameOf, whenLabel } from '../../utils/format';
import { UPCOMING, bookingStatus, isPaid, isPaymentPending, isWorkFinished, jobTag } from '../../utils/status';

/**
 * Minutes worked so far, worked out from the timer events as the backend does (START / RESUME to PAUSE / STOP, an
 * open stretch up to `now`, rounded up), so it keeps counting while the work runs; the backend's figure otherwise.
 */
function liveSeconds(detail, now) {
  let seconds = 0;
  let since = null;
  for (const log of detail.timeLogs ?? []) {
    const at = parseApiDate(log.eventAt)?.date?.getTime();
    if (at === undefined) return (Number(detail.workedMinutes) || 0) * 60;
    if (log.eventType === 'START' || log.eventType === 'RESUME') since = at;
    else if ((log.eventType === 'PAUSE' || log.eventType === 'STOP') && since !== null) {
      seconds += (at - since) / 1000;
      since = null;
    }
  }
  if (since !== null) seconds += Math.max(0, (now - since) / 1000);
  return seconds;
}

/** Minutes worked, rounded up as the backend does. */
function liveMinutes(detail, now) {
  return Math.ceil(liveSeconds(detail, now) / 60);
}

/**
 * One job (GET /api/bookings/{id}): status and progress; the customer with Call and Open in Maps (from the booking's
 * address snapshot); what, when, price and payment; actions by state: accept / decline an offer or assignment
 * (PUT /api/bookings/assignments/respond), Start work, Pause, Resume, Finish work (POST /api/bookings/timer), and for
 * a cash job once completed, Cash collected (PUT /api/payments/cash-collections/update, COLLECTED); the time worked;
 * history; Get help.
 */
/** Who cancelled a job, as a tag: the customer, the partner (You) or NexHome support staff (Admin). */
const CANCELLED_BY = {
  CUSTOMER: { label: 'Customer', fg: colors.blue, bg: colors.blueTint },
  PARTNER: { label: 'You', fg: colors.greenDark, bg: colors.greenSoft },
  STAFF: { label: 'Admin', fg: colors.navy, bg: '#eef2f6' }
};

export default function JobScreen() {
  const { id, collected } = useLocalSearchParams();
  const { authRequest, profile, reloadProfile } = useAuth();
  const detail = useApi(`/api/bookings/${id}`);
  // The partner's jobs: one job at a time, so another one under way blocks Start work here
  const myJobs = useApi('/api/bookings/partner/me?size=50');
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(collected ? 'Cash marked as collected.' : '');
  const [askingCode, setAskingCode] = useState(false);
  // Finish work on a cash job: the Collect payment screen, nothing sent until the cash is collected
  const [finishing, setFinishing] = useState(false);
  const [finalAmount, setFinalAmount] = useState(null);

  const running = detail.data?.booking?.bookingStatus === 'IN_PROGRESS';
  useEffect(() => {
    if (!running) return undefined;
    // Every second: the working screen shows a running clock
    const timer = setInterval(() => setNow(Date.now()), 1000);
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
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  };

  const timer = (eventType, message) => run(() => authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType } }), message);

  // Start work with the customer's code (V59): a wrong code's message stays in the pop-up; success closes it
  const startWithCode = async (code) => {
    await authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType: 'START', otp: code } });
    setAskingCode(false);
    setError('');
    setNotice('Code verified. Work started.');
    // Starting work puts the partner online (backend): Home's card follows
    await Promise.all([detail.reload(), reloadProfile().catch(() => {})]);
  };

  const cashToCollect = booking.paymentMethod === 'CASH' && !isPaid(booking);
  // Another job under way (working, paused, or waiting for its cash)
  const otherJob = (myJobs.data?.content ?? []).find((job) => job.bookingId !== booking.bookingId && ['IN_PROGRESS', 'PAUSED'].includes(job.bookingStatus)) ?? null;
  // Finish work: a cash job only shows the Collect payment screen (nothing changes until the cash is collected); an
  // online job stops the timer and completes
  const finish = async () => {
    if (!cashToCollect) {
      await timer('STOP');
      return;
    }
    setError('');
    setFinalAmount(null);
    setFinishing(true);
    // An hourly job is priced for the time worked so far, as the backend will when the work is finished
    if (booking.pricing?.pricingType === 'HOURLY') {
      const query = new URLSearchParams({ serviceId: String(booking.serviceId), minutes: String(Math.max(1, liveMinutes(detail.data, Date.now()))) });
      if (booking.serviceVariantId) query.set('serviceVariantId', String(booking.serviceVariantId));
      authRequest(`/api/catalog/services/quote?${query}`)
        .then((quote) => setFinalAmount(quote.total))
        .catch(() => {});
    }
  };

  // Cash collected: finish the work (STOP) and record the cash, which completes the booking
  const collectAndComplete = async () => {
    if (!isWorkFinished(booking)) {
      await authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType: 'STOP' } });
    }
    const payments = await authRequest(`/api/payments/bookings/${booking.bookingId}`);
    const cash = payments?.cashCollection;
    if (!cash) throw new Error('No cash is due for this job.');
    if (cash.status !== 'COLLECTED') {
      await authRequest('/api/payments/cash-collections/update', { method: 'PUT', body: { cashCollectionId: cash.cashCollectionId, status: 'COLLECTED' } });
    }
    setFinishing(false);
    setNotice('Cash collected. Job completed.');
    await detail.reload();
  };


  // Collect payment: after Finish work on a cash job (Back returns to the work), or a cash job already finished
  if (isMine && (isWorkFinished(booking) || (finishing && ['IN_PROGRESS', 'PAUSED'].includes(booking.bookingStatus)))) {
    return (
      <CollectScreen
        booking={booking}
        amount={isWorkFinished(booking) ? booking.totalAmount : finalAmount ?? booking.totalAmount}
        onBack={() => {
          if (finishing) setFinishing(false);
          else if (router.canGoBack()) router.back();
          else router.replace('/');
        }}
        onCashCollected={collectAndComplete}
      />
    );
  }
  // While working: only the timer, Pause / Resume and Finish work
  if (isMine && ['IN_PROGRESS', 'PAUSED'].includes(booking.bookingStatus) && !isWorkFinished(booking)) {
    return (
      <WorkingScreen
        booking={booking}
        seconds={liveSeconds(detail.data, now)}
        expectedMinutes={duration}
        busy={busy}
        error={error}
        onPause={() => timer('PAUSE', 'Work paused.')}
        onResume={() => timer('RESUME', 'Work resumed.')}
        onFinish={finish}
      />
    );
  }

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
            <Tag label={jobTag(booking).label} fg={jobTag(booking).fg} bg={jobTag(booking).bg} />
          </View>
          {UPCOMING.includes(booking.bookingStatus) && status.step ? <Progress step={status.step} /> : null}
          {booking.bookingStatus === 'CANCELLED' ? (
            // Who cancelled, when and why (as the customer's booking page shows it)
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
                    {booking.cancelledByRole === 'PARTNER' ? 'You' : booking.cancelledByName ?? '—'}
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
          <Row label="Paid by" value={booking.bookingStatus === 'CANCELLED' ? '–' : booking.paymentMethod === 'CASH' ? 'Cash to you' : 'Online'} />
          {booking.bookingStatus !== 'CANCELLED' ? (
            <Row label="Payment" value={isPaid(booking) ? 'Paid' : 'Pending'} color={isPaid(booking) ? colors.greenDark : colors.amber} />
          ) : null}
          {isPaymentPending(booking) ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 12, backgroundColor: colors.amberSoft }}>
              <Feather name="clock" size={15} color={colors.amber} />
              <Text weight="semibold" style={{ flex: 1, fontSize: 12.5, color: colors.amber }}>
                {booking.paymentMethod === 'CASH'
                  ? `Payment pending: collect ${money(booking.totalAmount)} in cash and tap Cash collected.`
                  : `Payment pending: the customer pays ${money(booking.totalAmount)} online.`}
              </Text>
            </View>
          ) : null}
          {booking.paymentMethod === 'CASH' && booking.bookingStatus !== 'CANCELLED' && !isPaymentPending(booking) ? (
            <Text style={{ fontSize: 12.5, color: colors.muted }}>
              {booking.paymentStatus === 'PAID' ? 'The cash is recorded as collected.' : `Collect ${money(booking.totalAmount)} from the customer after the work.`}
            </Text>
          ) : null}
          {booking.paymentMethod === 'ONLINE' && booking.bookingStatus !== 'CANCELLED' ? <Text style={{ fontSize: 12.5, color: colors.muted }}>Your share is paid to your bank account after the job. See Earnings.</Text> : null}
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
            {booking.bookingStatus === 'PARTNER_EN_ROUTE' ? (
              <View style={{ gap: 8 }}>
                <Button title="Start work" icon="play" onPress={() => setAskingCode(true)} loading={busy} disabled={Boolean(otherJob)} />
                {otherJob ? (
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 12, backgroundColor: colors.amberSoft }}>
                    <Feather name="lock" size={15} color={colors.amber} />
                    <Text weight="semibold" style={{ flex: 1, fontSize: 12.5, color: colors.amber }}>
                      Finish your current job {otherJob.publicId} first; you can start this one after it is completed.
                    </Text>
                  </View>
                ) : null}
              </View>
            ) : null}
            {booking.bookingStatus === 'IN_PROGRESS' && !isWorkFinished(booking) ? (
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
            {isPaymentPending(booking) && booking.paymentMethod === 'CASH' ? (
              <Button title={`Cash collected (${money(booking.totalAmount)})`} icon="dollar-sign" onPress={() => router.push(`/collect/${booking.bookingId}`)} disabled={busy} />
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
      <StartCodeModal visible={askingCode} onSubmit={startWithCode} onClose={() => setAskingCode(false)} />
    </SafeAreaView>
  );
}
