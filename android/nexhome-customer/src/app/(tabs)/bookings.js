import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Linking, Pressable, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import Progress from '../../components/Progress';
import { Button, Empty, ErrorBox, Loading, Tag, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { dayLabel, money, rangeLabel, serviceNameOf, whenLabel } from '../../utils/format';
import { bookingStatus, compareUpcoming, isOpen, isPaymentDue, newestFirstBy } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

const TABS = [
  ['upcoming', 'Upcoming'],
  ['pending', 'Payment pending'],
  ['past', 'Past'],
  ['cancelled', 'Cancelled']
];

const WHO = {
  CUSTOMER: { label: 'You', fg: colors.blue, bg: colors.blueTint },
  PARTNER: { label: 'Partner', fg: colors.greenDark, bg: colors.greenSoft },
  STAFF: { label: 'Admin', fg: colors.navy, bg: '#eef2f6' }
};

/** "+91 98765 43210" -> "tel:+919876543210" */
const telOf = (number) => `tel:${String(number).replace(/[^\d+]/g, '')}`;

/**
 * My bookings (GET /api/bookings/me): Upcoming (in progress, then partner assigned, confirmed, pending; then as soon
 * as possible and the earliest time; then the booking id; the next one with progress, Call Partner and Get help),
 * Payment pending (done but not paid yet: completed, or a cash job whose work is finished; Pay, newest first), Past
 * (completed and paid: the rating given and the amount paid) and Cancelled (who and why). The tabs scroll sideways.
 */
export default function Bookings() {
  const [tab, setTab] = useState('upcoming');
  const bookings = useApi('/api/bookings/me?size=100');
  const reviews = useApi('/api/reviews/me?size=100');
  const contact = useApi('/api/settings/public');
  const me = useApi('/api/profiles/customers/me');

  const all = useMemo(() => bookings.data?.content ?? [], [bookings.data]);
  const reviewOf = useMemo(() => Object.fromEntries((reviews.data?.content ?? []).map((r) => [r.bookingId, r])), [reviews.data]);
  const groups = useMemo(() => {
    return {
      upcoming: all.filter((b) => isOpen(b) && !isPaymentDue(b)).sort(compareUpcoming),
      pending: all.filter(isPaymentDue).sort(newestFirstBy('actualEndAt')),
      past: all.filter((b) => b.bookingStatus === 'COMPLETED' && !isPaymentDue(b)).sort(newestFirstBy('actualEndAt')),
      cancelled: all.filter((b) => b.bookingStatus === 'CANCELLED').sort(newestFirstBy('cancelledAt'))
    };
  }, [all]);
  const list = groups[tab];
  const supportPhone = contact.data?.SUPPORT_PHONE;
  const myName = me.data?.firstName ? [me.data.firstName, me.data.lastName].filter(Boolean).join(' ') : 'You';
  const refresh = () => Promise.all([bookings.refresh(), reviews.refresh()]);
  // Opening the tab shows Upcoming again
  useTabReset(() => setTab('upcoming'), refresh);

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 14 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          My bookings
        </Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} accessibilityRole="tablist" contentContainerStyle={{ gap: 18 }} style={{ marginHorizontal: -20 }}>
          <View style={{ width: 2 }} />
          {TABS.map(([key, label]) => {
            const on = tab === key;
            return (
              <Pressable
                key={key}
                accessibilityRole="tab"
                accessibilityState={{ selected: on }}
                accessibilityLabel={`${label}, ${groups[key].length}`}
                onPress={() => setTab(key)}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 5, paddingBottom: 11, borderBottomWidth: 3, borderBottomColor: on ? colors.blue : 'transparent' }}
              >
                <Text weight="bold" style={{ fontSize: 13, color: on ? colors.blue : colors.muted }}>
                  {label}
                </Text>
                <View style={{ minWidth: 18, height: 18, borderRadius: 9, paddingHorizontal: 5, alignItems: 'center', justifyContent: 'center', backgroundColor: on ? colors.blue : '#e8eef5' }}>
                  <Text weight="extrabold" style={{ fontSize: 10, color: on ? colors.white : colors.muted }}>
                    {groups[key].length}
                  </Text>
                </View>
              </Pressable>
            );
          })}
          <View style={{ width: 2 }} />
        </ScrollView>
      </View>
      {bookings.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={list}
          keyExtractor={(b) => String(b.bookingId)}
          contentContainerStyle={{ padding: 16, gap: 12 }}
          refreshControl={<RefreshControl refreshing={bookings.refreshing} onRefresh={refresh} />}
          ListHeaderComponent={<ErrorBox message={bookings.error} />}
          ListEmptyComponent={
            <Empty
              icon="calendar"
              title={{ upcoming: 'No upcoming bookings', pending: 'No payments pending', past: 'No past bookings', cancelled: 'No cancelled bookings' }[tab]}
              text={tab === 'upcoming' ? 'Book a service and it shows here.' : undefined}
              action={tab === 'upcoming' ? <Button title="Book a service" onPress={() => router.push('/search')} /> : null}
            />
          }
          renderItem={({ item, index }) => {
            if (tab === 'upcoming') {
              return <UpcomingCard booking={item} next={index === 0} later={index === 1} supportPhone={supportPhone} />;
            }
            if (tab === 'past' || tab === 'pending') return <PastCard booking={item} review={reviewOf[item.bookingId]} myName={myName} />;
            return <CancelledCard booking={item} />;
          }}
        />
      )}
    </SafeAreaView>
  );
}

function BookingCard({ booking, when, tag, children }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={() => router.push(`/booking/${booking.bookingId}`)}
      style={{ backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 18, padding: 14, gap: 12 }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
        <View style={{ flex: 1, gap: 2 }}>
          <Text weight="extrabold" style={{ fontSize: 15 }} numberOfLines={1}>
            {serviceNameOf(booking.serviceName)}
          </Text>
          <Text style={{ fontSize: 12, color: colors.muted }}>{when}</Text>
        </View>
        {tag}
      </View>
      {children}
    </Pressable>
  );
}

/** The professional: initial, name (or not yet assigned), a line, and the amount. */
function Professional({ booking, line }) {
  const name = booking.partnerName;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 12, backgroundColor: colors.ground }}>
      <View style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
        {name ? (
          <Text weight="extrabold" style={{ color: colors.blue, fontSize: 14 }}>
            {name[0].toUpperCase()}
          </Text>
        ) : (
          <Feather name="user" size={16} color={colors.blue} />
        )}
      </View>
      <View style={{ flex: 1, gap: 1 }}>
        <Text weight="bold" style={{ fontSize: 13 }} numberOfLines={1}>
          {name ?? 'Professional not assigned yet'}
        </Text>
        <Text style={{ fontSize: 11.5, color: colors.muted }} numberOfLines={1}>
          {line}
        </Text>
      </View>
      <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14 }}>
        {money(booking.totalAmount)}
      </Text>
    </View>
  );
}

function SectionLabel({ children }) {
  return (
    <Text weight="extrabold" style={{ fontSize: 11, color: colors.muted, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: -4 }}>
      {children}
    </Text>
  );
}

function UpcomingCard({ booking, next, later, supportPhone }) {
  const status = bookingStatus(booking.bookingStatus);
  const when = booking.scheduledStartAt ? rangeLabel(booking.scheduledStartAt, booking.scheduledEndAt) : 'As soon as possible';
  const tag = <Tag label={status.label} fg={status.fg} bg={status.bg} />;
  const card = (
    <BookingCard booking={booking} when={when} tag={tag}>
      {next ? <Progress status={booking.bookingStatus} /> : null}
      <Professional booking={booking} line={booking.partnerName ? 'Your professional' : status.note} />
      {isPaymentDue(booking) ? (
        <View style={{ gap: 10, padding: 12, borderRadius: 12, backgroundColor: colors.amberSoft }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Feather name={booking.paymentMethod === 'CASH' ? 'dollar-sign' : 'credit-card'} size={16} color={colors.amber} />
            <Text weight="bold" style={{ flex: 1, fontSize: 13, color: colors.amber }}>
              {booking.paymentMethod === 'CASH' ? `Pay ${money(booking.totalAmount)} in cash to your professional` : `Payment due: ${money(booking.totalAmount)}`}
            </Text>
          </View>
          {booking.paymentMethod === 'CASH' ? null : (
            <Button title={`Pay ${money(booking.totalAmount)}`} variant="blue" onPress={() => router.push(`/booking/${booking.bookingId}`)} />
          )}
        </View>
      ) : null}
      {booking.startOtp ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 12, backgroundColor: colors.blueTint }}>
          <Feather name="key" size={15} color={colors.blue} />
          <Text style={{ flex: 1, fontSize: 12.5, color: colors.blue }}>Start code for your professional</Text>
          <Text weight="extrabold" style={{ fontSize: 18, letterSpacing: 4, color: colors.navy }}>
            {booking.startOtp}
          </Text>
        </View>
      ) : null}
      {next ? (
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <ActionButton
            icon="phone"
            label="Call Partner"
            disabled={!booking.partnerMobile}
            onPress={() => Linking.openURL(telOf(booking.partnerMobile))}
          />
          <ActionButton icon="headphones" label="Get help" filled disabled={!supportPhone} onPress={() => Linking.openURL(telOf(supportPhone))} />
        </View>
      ) : null}
    </BookingCard>
  );
  if (next || later) {
    return (
      <View style={{ gap: 12 }}>
        <SectionLabel>{next ? 'Next' : 'Later'}</SectionLabel>
        {card}
      </View>
    );
  }
  return card;
}

function PastCard({ booking, review, myName }) {
  const paid = booking.paymentStatus === 'PAID' || booking.paymentStatus === 'PARTIALLY_REFUNDED' || booking.paymentStatus === 'REFUNDED';
  const partnerFirst = booking.partnerName?.split(' ')[0] ?? 'your professional';
  const when = `${booking.bookingStatus === 'COMPLETED' ? 'Completed' : 'Work finished'} ${dayLabel(booking.actualEndAt)}`;
  const booked = booking.scheduledStartAt ? rangeLabel(booking.scheduledStartAt, booking.scheduledEndAt) : 'As soon as possible';
  const actual = booking.actualStartAt ? rangeLabel(booking.actualStartAt, booking.actualEndAt) : null;
  const tag = paid ? <Tag label="Paid" fg={colors.greenDark} bg={colors.greenSoft} /> : <Tag label="Payment pending" fg={colors.amber} bg={colors.amberSoft} />;
  return (
    <BookingCard booking={booking} when={when} tag={tag}>
      <View style={{ gap: 6, padding: 10, borderRadius: 10, backgroundColor: colors.ground }}>
        <TimeRow icon="calendar" label="Booked" value={booked} />
        {actual ? <TimeRow icon="clock" label="Actual" value={actual} strong /> : null}
      </View>
      <Professional booking={booking} line={booking.partnerName ? 'Your professional' : ''} />
      {!paid ? (
        <>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 10, backgroundColor: colors.ground }}>
            <Feather name={booking.paymentMethod === 'CASH' ? 'dollar-sign' : 'credit-card'} size={14} color={colors.muted} />
            <Text style={{ flex: 1, fontSize: 12, color: colors.muted }}>{booking.paymentMethod === 'CASH' ? 'Cash · not paid yet' : 'Online · not paid yet'}</Text>
            <Text weight="extrabold" style={{ color: colors.navy }}>
              {money(booking.totalAmount)}
            </Text>
          </View>
          <Button title={`Pay ${money(booking.totalAmount)}`} variant="blue" onPress={() => router.push(`/booking/${booking.bookingId}`)} />
        </>
      ) : (
        <>
          <View style={{ flexDirection: 'row', alignItems: 'center', padding: 10, borderRadius: 10, backgroundColor: colors.ground }}>
            <Pressable
              accessibilityRole="button"
              disabled={Boolean(review)}
              onPress={() => router.push(`/booking/${booking.bookingId}?rate=1`)}
              style={{ flex: 1, gap: 2 }}
            >
              <Text style={{ fontSize: 11.5, color: colors.muted }}>{review ? `${myName} rated` : `Rate ${partnerFirst}`}</Text>
              <Text style={{ fontSize: 17, letterSpacing: 1.5, color: review ? colors.star : '#cfd8e3' }}>
                {review ? '★'.repeat(review.partnerRating) + '☆'.repeat(5 - review.partnerRating) : '☆☆☆☆☆'}
              </Text>
            </Pressable>
            <View style={{ alignItems: 'flex-end', gap: 2 }}>
              <Text style={{ fontSize: 11.5, color: colors.muted }}>Paid</Text>
              <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14 }}>
                {money(booking.totalAmount)}
              </Text>
            </View>
          </View>
          {review?.reviewText ? (
            <Text style={{ fontSize: 12, fontStyle: 'italic', backgroundColor: '#fffaf0', borderLeftWidth: 3, borderLeftColor: colors.star, paddingVertical: 6, paddingHorizontal: 9, borderRadius: 6 }}>
              “{review.reviewText}”
            </Text>
          ) : null}
        </>
      )}
    </BookingCard>
  );
}

function CancelledCard({ booking }) {
  const who = WHO[booking.cancelledByRole];
  const when = booking.cancelledAt ? `Cancelled ${whenLabel(booking.cancelledAt)}` : 'Cancelled';
  return (
    <BookingCard booking={booking} when={when} tag={<Tag label="Cancelled" fg={colors.red} bg={colors.redSoft} />}>
      <View style={{ gap: 7, padding: 10, borderRadius: 10, backgroundColor: colors.redSoft }}>
        <Row label="Cancelled by">
          {who ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
              <View style={{ backgroundColor: who.bg, borderRadius: 999, paddingHorizontal: 7, paddingVertical: 2 }}>
                <Text weight="extrabold" style={{ fontSize: 10, color: who.fg }}>
                  {who.label}
                </Text>
              </View>
              <Text weight="semibold" style={{ fontSize: 12 }}>
                {booking.cancelledByName}
              </Text>
            </View>
          ) : (
            <Text weight="semibold" style={{ fontSize: 12 }}>
              —
            </Text>
          )}
        </Row>
        <Row label="Reason">
          <Text weight="semibold" style={{ fontSize: 12, color: colors.red, flexShrink: 1, textAlign: 'right' }}>
            {booking.cancellationReason}
          </Text>
        </Row>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 10, backgroundColor: colors.ground }}>
        <Feather name={booking.paymentMethod === 'CASH' ? 'dollar-sign' : 'rotate-ccw'} size={14} color={colors.muted} />
        <Text style={{ flex: 1, fontSize: 12, color: colors.muted }}>
          {booking.paymentMethod === 'CASH' ? 'Cash · nothing to pay' : 'Online · anything paid is refunded'}
        </Text>
        <Text weight="extrabold" style={{ color: colors.navy }}>
          {money(booking.totalAmount)}
        </Text>
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <ActionButton label="Details" onPress={() => router.push(`/booking/${booking.bookingId}`)} />
        <ActionButton label="Book again" filled blue onPress={() => router.push(`/service/${booking.serviceId}`)} />
      </View>
    </BookingCard>
  );
}

/** "Booked  Thu, 6 Nov · 10:30 AM – 11:30 AM" */
function TimeRow({ icon, label, value, strong }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
      <Feather name={icon} size={13} color={strong ? colors.green : colors.muted} style={{ marginTop: 2 }} />
      <Text style={{ width: 50, fontSize: 12, color: colors.muted }}>{label}</Text>
      <Text weight={strong ? 'bold' : 'semibold'} style={{ flex: 1, fontSize: 12, color: strong ? colors.greenDark : colors.ink }}>
        {value}
      </Text>
    </View>
  );
}

function Row({ label, children }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <Text style={{ fontSize: 12, color: colors.muted }}>{label}</Text>
      {children}
    </View>
  );
}

/** Two equal buttons in a row; text on one line. `filled` green (or blue with `blue`), else outlined. */
function ActionButton({ icon, label, onPress, filled, blue, disabled }) {
  const bg = filled ? (blue ? colors.blue : colors.green) : colors.white;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => ({
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 6,
        height: 42,
        borderRadius: 12,
        backgroundColor: bg,
        borderWidth: filled ? 0 : 1.5,
        borderColor: colors.line,
        opacity: disabled ? 0.5 : pressed ? 0.85 : 1
      })}
    >
      {icon ? <Feather name={icon} size={15} color={filled ? colors.white : colors.navy} /> : null}
      <Text weight="bold" style={{ fontSize: 13, color: filled ? colors.white : colors.navy }} numberOfLines={1}>
        {label}
      </Text>
    </Pressable>
  );
}
