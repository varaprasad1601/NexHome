import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Empty, ErrorBox, GradientCover, Header, Loading, Pick, SectionTitle, Text } from '../components/ui';
import { apiRequest } from '../api/client';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import { colors } from '../theme';
import { money, nextDays, timeLabel, toInstant } from '../utils/format';

/** Half-hour start times, 9:00 AM to 5:00 PM (the working hours). */
const TIMES = Array.from({ length: 17 }, (_, i) => ({ hours: 9 + Math.floor(i / 2), minutes: i % 2 ? 30 : 0 }));

/** A line of the price details: label left, amount right. */
function PriceRow({ label, value, strong, color }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
      <Text style={{ flex: 1, color: strong ? colors.ink : colors.muted, fontSize: strong ? 15 : 13 }} numberOfLines={2}>
        {label}
      </Text>
      <Text weight={strong ? 'extrabold' : 'semibold'} style={{ fontSize: strong ? 16 : 13, color: color ?? (strong ? colors.navy : colors.ink) }}>
        {value}
      </Text>
    </View>
  );
}

/** Which field a message from the server is about, so it shows there. */
function fieldOf(message = '') {
  const text = message.toLowerCase();
  if (text.includes('address')) return 'address';
  if (text.includes('scheduled') || text.includes('days ahead') || text.includes('future') || text.includes('time')) return 'time';
  return 'page';
}

/** A field's error in red, right where it happened. */
function FieldError({ message }) {
  if (!message) return null;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
      <Feather name="alert-circle" size={14} color={colors.red} />
      <Text weight="semibold" style={{ flex: 1, fontSize: 12.5, color: colors.red }}>
        {message}
      </Text>
    </View>
  );
}

/** The times in three groups: [label, from minute, until minute]. */
const PARTS = [
  ['Morning', 0, 12 * 60],
  ['Afternoon', 12 * 60, 16 * 60],
  ['Evening', 16 * 60, 24 * 60]
];

/**
 * Design A checkout, one page: the cart's services (remove), the service address (the default chosen; change or
 * add), when (as soon as possible, or a day and a half-hour slot), and cash or online; Confirm books every service
 * (POST /api/cart/checkout, one booking each). An online booking gets its payment order right away
 * (POST /api/payments/orders/create).
 */
export default function Cart() {
  const { authRequest } = useAuth();
  const cart = useApi('/api/cart/me');
  const addresses = useApi('/api/addresses/me');
  // Today's slots start at least an hour from now (Indian time)
  const [minuteNow] = useState(() => {
    const ist = new Date(Date.now() + 5.5 * 3600 * 1000);
    return ist.getUTCHours() * 60 + ist.getUTCMinutes();
  });
  const today = useMemo(() => nextDays(1)[0].iso, []);
  const slotsOf = useCallback((iso) => TIMES.filter((t) => iso !== today || t.hours * 60 + t.minutes >= minuteNow + 60), [today, minuteNow]);
  // The next 7 days that still have a slot (today drops out once its last slot has passed)
  const days = useMemo(() => nextDays(8).filter((d) => slotsOf(d.iso).length).slice(0, 7), [slotsOf]);
  const [addressId, setAddressId] = useState(null);
  const [instant, setInstant] = useState(false);
  const [day, setDay] = useState(null);
  const [time, setTime] = useState(null);
  const [payment, setPayment] = useState('CASH');
  const [busy, setBusy] = useState(false);
  // An error shows at its field: address, date, time, or (anything else) at the top of the page
  const [error, setErrorState] = useState(null);
  const scrollRef = useRef(null);
  const positions = useRef({});
  const setError = useCallback((message, field = 'page') => {
    if (!message) {
      setErrorState(null);
      return;
    }
    setErrorState({ field, message });
    const y = field === 'page' ? 0 : (positions.current[field === 'address' ? 'address' : 'when'] ?? 0) - 12;
    setTimeout(() => scrollRef.current?.scrollTo({ y: Math.max(0, y), animated: true }), 50);
  }, []);
  const errorFor = (field) => (error?.field === field ? error.message : null);

  const items = useMemo(() => cart.data?.items ?? [], [cart.data]);
  const settings = useApi('/api/settings/public');

  // Each item's regular price (before its discount) from the public quote, for the price details
  const [regulars, setRegulars] = useState({});
  const itemsKey = items.map((i) => `${i.cartItemId}:${i.serviceId}:${i.serviceVariantId ?? ''}`).join(',');
  useEffect(() => {
    let cancelled = false;
    Promise.all(
      items.map((item) => {
        const query = new URLSearchParams({ serviceId: String(item.serviceId) });
        if (item.serviceVariantId) query.set('serviceVariantId', String(item.serviceVariantId));
        return apiRequest(`/api/catalog/services/quote?${query}`)
          .then((quote) => [item.cartItemId, Number(quote.regularSubtotal ?? quote.total)])
          .catch(() => [item.cartItemId, null]);
      })
    ).then((pairs) => {
      if (!cancelled) setRegulars(Object.fromEntries(pairs));
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itemsKey]);

  const prices = useMemo(() => {
    const charged = items.reduce((sum, i) => sum + Number(i.estimatedUnitPrice ?? 0), 0);
    const regular = items.reduce((sum, i) => sum + Math.max(Number(regulars[i.cartItemId] ?? i.estimatedUnitPrice ?? 0), Number(i.estimatedUnitPrice ?? 0)), 0);
    const feeRate = Number(settings.data?.TRANSACTION_FEE_PERCENTAGE ?? 0);
    // The backend rounds the fee half-up to the paisa (NexHomeBookingCreator.feeOf)
    const fee = payment === 'ONLINE' ? Math.round(charged * feeRate) / 100 : 0;
    return { regular, discount: Math.max(0, regular - charged), feeRate, fee, total: charged + fee };
  }, [items, regulars, settings.data, payment]);
  const list = addresses.data ?? [];
  const address = list.find((a) => a.addressId === addressId) ?? list.find((a) => a.isDefault) ?? list[0];

  const slots = day ? slotsOf(day) : [];
  const chosenDay = days.find((d) => d.iso === day);

  const remove = async (cartItemId) => {
    try {
      await authRequest(`/api/cart/items/delete/${cartItemId}`, { method: 'DELETE' });
      cart.reload();
    } catch (err) {
      setError(err.message);
    }
  };

  const confirm = async () => {
    setError(null);
    if (!address) {
      setError('Add the address where the service is needed.', 'address');
      return;
    }
    if (!instant && !day) {
      setError('Choose a date for the service.', 'date');
      return;
    }
    if (!instant && !time) {
      setError('Choose a time for the service.', 'time');
      return;
    }
    setBusy(true);
    try {
      const hhmm = time ? `${String(time.hours).padStart(2, '0')}:${String(time.minutes).padStart(2, '0')}` : null;
      const result = await authRequest('/api/cart/checkout', {
        method: 'POST',
        body: {
          addressId: address.addressId,
          bookingType: instant ? 'INSTANT' : 'SCHEDULED',
          scheduledStartAt: instant ? null : toInstant(day, hhmm),
          paymentMethod: payment
        }
      });
      const first = result.bookings[0];
      if (payment === 'ONLINE' && first) {
        await authRequest('/api/payments/orders/create', { method: 'POST', body: { bookingId: first.bookingId } }).catch(() => {});
      }
      if (result.bookings.length === 1) router.replace(`/booking/${first.bookingId}?placed=1`);
      else router.replace('/(tabs)/bookings');
    } catch (err) {
      setError(err.message, fieldOf(err.message));
    } finally {
      setBusy(false);
    }
  };

  if (cart.loading) return <Loading />;
  if (!items.length) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Checkout" />
        <Empty icon="shopping-cart" title="Your cart is empty" text="Add a service to book it." action={<Button title="Browse services" onPress={() => router.replace('/(tabs)')} />} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Checkout" />
      <ScrollView ref={scrollRef} contentContainerStyle={{ padding: 16, gap: 12 }}>
        <ErrorBox message={errorFor('page')} />
        {items.map((item) => (
          <Card key={item.cartItemId} style={{ flexDirection: 'row', gap: 12, alignItems: 'center', paddingVertical: 14 }}>
            <GradientCover style={{ width: 52, height: 52, borderRadius: 12 }} />
            <View style={{ flex: 1, gap: 2 }}>
              <Text weight="bold">{item.serviceName}</Text>
              {item.variantName ? <Text style={{ fontSize: 12, color: colors.muted }}>{item.variantName}</Text> : null}
            </View>
            <View style={{ alignItems: 'flex-end', gap: 4 }}>
              <Text weight="extrabold" style={{ color: colors.navy }}>
                {money(item.estimatedUnitPrice)}
              </Text>
              <Pressable accessibilityRole="button" accessibilityLabel={`Remove ${item.serviceName}`} onPress={() => remove(item.cartItemId)} hitSlop={8}>
                <Text weight="bold" style={{ fontSize: 12, color: colors.red }}>
                  Remove
                </Text>
              </Pressable>
            </View>
          </Card>
        ))}

        <Card style={{ gap: 10 }} onLayout={(e) => (positions.current.address = e.nativeEvent.layout.y)}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <SectionTitle>Service address</SectionTitle>
            <Text weight="bold" style={{ color: colors.blue, fontSize: 13 }} onPress={() => router.push('/addresses?pick=1')} accessibilityRole="button">
              {address ? 'Change' : 'Add'}
            </Text>
          </View>
          {address ? (
            <View style={{ flexDirection: 'row', gap: 10 }}>
              <Feather name="map-pin" size={18} color={colors.green} style={{ marginTop: 2 }} />
              <View style={{ flex: 1 }}>
                <Text weight="bold">{address.label || address.addressType}</Text>
                <Text style={{ fontSize: 13, color: colors.muted, lineHeight: 19 }}>
                  {[address.addressLine1, address.addressLine2, address.landmark, address.city, address.postalCode].filter(Boolean).join(', ')}
                </Text>
              </View>
            </View>
          ) : (
            <Text style={{ color: colors.muted }}>No address yet. Add the place where the service is needed.</Text>
          )}
          {list.length > 1 ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {list.map((a) => (
                <Pick key={a.addressId} selected={a.addressId === address?.addressId} onPress={() => setAddressId(a.addressId)} style={{ paddingVertical: 8, paddingHorizontal: 12 }}>
                  <Text weight="bold" style={{ fontSize: 12.5, color: a.addressId === address?.addressId ? colors.blue : colors.ink }}>
                    {a.label || a.addressType}
                  </Text>
                </Pick>
              ))}
            </ScrollView>
          ) : null}
          <FieldError message={errorFor('address')} />
        </Card>

        <Card style={{ gap: 10 }} onLayout={(e) => (positions.current.when = e.nativeEvent.layout.y)}>
          <SectionTitle>When</SectionTitle>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Pick selected={instant} onPress={() => setInstant(true)} style={{ flex: 1, alignItems: 'center' }}>
              <Text weight="bold" style={{ fontSize: 13, color: instant ? colors.blue : colors.ink }}>
                As soon as possible
              </Text>
            </Pick>
            <Pick selected={!instant} onPress={() => setInstant(false)} style={{ flex: 1, alignItems: 'center' }}>
              <Text weight="bold" style={{ fontSize: 13, color: !instant ? colors.blue : colors.ink }}>
                Schedule
              </Text>
            </Pick>
          </View>
          {instant ? (
            <Text style={{ fontSize: 12.5, color: colors.muted }}>A professional is assigned within working hours (9 AM to 5 PM).</Text>
          ) : (
            <>
              <Text weight="bold" style={{ fontSize: 13 }}>
                Choose a date
              </Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                {days.map((d) => {
                  const on = d.iso === day;
                  return (
                    <Pressable
                      key={d.iso}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: on }}
                      onPress={() => {
                        setDay(d.iso);
                        setTime(null);
                        setError(null);
                      }}
                      style={{ width: 58, paddingVertical: 8, borderRadius: 12, alignItems: 'center', backgroundColor: on ? colors.blue : colors.white, borderWidth: on ? 0 : 1, borderColor: colors.line }}
                    >
                      <Text style={{ fontSize: 11, color: on ? colors.white : colors.muted }}>{d.dow}</Text>
                      <Text weight="extrabold" style={{ fontSize: 16, color: on ? colors.white : colors.ink }}>
                        {d.day}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
              <FieldError message={errorFor('date')} />
              {day ? (
                <>
                  <Text weight="bold" style={{ fontSize: 13, marginTop: 4 }}>
                    Choose a time
                  </Text>
                  {PARTS.map(([label, from, to]) => {
                    const part = slots.filter((t) => t.hours * 60 + t.minutes >= from && t.hours * 60 + t.minutes < to);
                    if (!part.length) return null;
                    return (
                      <View key={label} style={{ gap: 6 }}>
                        <Text style={{ fontSize: 11.5, color: colors.muted }}>{label}</Text>
                        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                          {part.map((t) => {
                            const on = Boolean(time) && time.hours === t.hours && time.minutes === t.minutes;
                            return (
                              <Pick
                                key={`${t.hours}:${t.minutes}`}
                                selected={on}
                                onPress={() => {
                                  setTime(t);
                                  setError(null);
                                }}
                                style={{ paddingVertical: 8, paddingHorizontal: 12, borderRadius: 10 }}
                              >
                                <Text weight="bold" style={{ fontSize: 12.5, color: on ? colors.blue : colors.ink }}>
                                  {timeLabel(t.hours, t.minutes)}
                                </Text>
                              </Pick>
                            );
                          })}
                        </View>
                      </View>
                    );
                  })}
                  <FieldError message={errorFor('time')} />
                  {time && chosenDay ? (
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 10, backgroundColor: colors.greenSoft }}>
                      <Feather name="check-circle" size={16} color={colors.green} />
                      <Text weight="bold" style={{ color: colors.greenDark, fontSize: 13 }}>
                        {chosenDay.dow === 'Today' ? 'Today' : `${chosenDay.dow}, ${chosenDay.day}`} at {timeLabel(time.hours, time.minutes)}
                      </Text>
                    </View>
                  ) : null}
                </>
              ) : (
                <Text style={{ fontSize: 12.5, color: colors.muted }}>Pick a date to see the times.</Text>
              )}
            </>
          )}
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Pay with</SectionTitle>
          {[
            { code: 'CASH', label: 'Cash after service', note: 'Pay the professional when the job is done' },
            { code: 'ONLINE', label: 'Pay online', note: 'UPI, card or net banking; a small gateway fee is added' }
          ].map((option) => {
            const on = payment === option.code;
            return (
              <Pick key={option.code} selected={on} onPress={() => setPayment(option.code)} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <View style={{ flex: 1 }}>
                  <Text weight="bold">{option.label}</Text>
                  <Text style={{ fontSize: 12, color: colors.muted }}>{option.note}</Text>
                </View>
                <View style={{ width: 18, height: 18, borderRadius: 9, borderWidth: on ? 6 : 2, borderColor: on ? colors.blue : '#c3cfdb' }} />
              </Pick>
            );
          })}
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionTitle>Price details</SectionTitle>
          {items.map((item) => (
            <PriceRow key={item.cartItemId} label={item.variantName ? `${item.serviceName} · ${item.variantName}` : item.serviceName} value={money(item.estimatedUnitPrice)} />
          ))}
          <View style={{ height: 1, backgroundColor: colors.line }} />
          <PriceRow label={`Item total${items.length > 1 ? ` (${items.length} services)` : ''}`} value={money(prices.regular)} />
          {prices.discount > 0 ? <PriceRow label="Discount" value={`− ${money(prices.discount)}`} color={colors.greenDark} /> : null}
          {payment === 'ONLINE' && prices.feeRate > 0 ? (
            <PriceRow label={`Online payment fee (${prices.feeRate}%)`} value={`+ ${money(prices.fee)}`} />
          ) : null}
          <View style={{ height: 1, backgroundColor: colors.line }} />
          <PriceRow label="To pay" value={money(prices.total)} strong />
          {prices.discount > 0 ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, padding: 9, borderRadius: 10, backgroundColor: colors.greenSoft }}>
              <Feather name="tag" size={14} color={colors.green} />
              <Text weight="bold" style={{ fontSize: 12.5, color: colors.greenDark }}>
                You save {money(prices.discount)} on this booking
              </Text>
            </View>
          ) : null}
          <Text style={{ fontSize: 11.5, color: colors.muted }}>
            {payment === 'CASH' ? 'Pay the professional in cash when the job is done.' : 'The online payment fee is not refunded if you cancel.'} Hourly services are priced for one hour; the final amount follows the time worked.
          </Text>
        </Card>
      </ScrollView>

      <View style={{ backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line, padding: 16, gap: 10 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <View>
            <Text style={{ color: colors.muted }}>To pay</Text>
            {prices.discount > 0 ? (
              <Text style={{ fontSize: 12, color: '#8a9aad', textDecorationLine: 'line-through' }}>{money(prices.regular + prices.fee)}</Text>
            ) : null}
          </View>
          <Text weight="extrabold" style={{ fontSize: 18, color: colors.navy }}>
            {money(prices.total)}
          </Text>
        </View>
        <Button title="Confirm booking" onPress={confirm} loading={busy} />
      </View>
    </SafeAreaView>
  );
}
