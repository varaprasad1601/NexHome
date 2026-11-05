import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Empty, ErrorBox, Field, FormScroll, Header, Loading, Pick, SectionTitle, Tag, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import { colors } from '../theme';
import { nextDays, parseApiDate, toInstant, whenLabel } from '../utils/format';
import { timeOffStatus } from '../utils/status';

const HOURS = Array.from({ length: 17 }, (_, i) => `${String(i + 6).padStart(2, '0')}:00`);
const hourLabel = (hhmm) => {
  const h = Number(hhmm.slice(0, 2));
  return `${h % 12 === 0 ? 12 : h % 12} ${h >= 12 ? 'PM' : 'AM'}`;
};

/** Upcoming, on leave now, past or cancelled. */
function stateOf(item, now) {
  if (item.cancelledAt) return 'CANCELLED';
  const starts = parseApiDate(item.startsAt)?.date?.getTime() ?? 0;
  const ends = parseApiDate(item.endsAt)?.date?.getTime() ?? 0;
  if (ends <= now) return 'PAST';
  if (starts <= now) return 'ONGOING';
  return 'UPCOMING';
}

function DayAndHour({ label, day, hour, onDay, onHour }) {
  const days = nextDays(30);
  return (
    <View style={{ gap: 8 }}>
      <Text weight="bold" style={{ fontSize: 13 }}>
        {label}
      </Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
        {days.map((d) => (
          <Pick key={d.iso} selected={day === d.iso} onPress={() => onDay(d.iso)} style={{ width: 58, alignItems: 'center', paddingVertical: 8 }}>
            <Text style={{ fontSize: 11.5, color: colors.muted }}>{d.dow}</Text>
            <Text weight="extrabold" style={{ fontSize: 16, color: day === d.iso ? colors.blue : colors.ink }}>
              {d.day}
            </Text>
          </Pick>
        ))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
        {HOURS.map((h) => (
          <Pick key={h} selected={hour === h} onPress={() => onHour(h)} style={{ paddingVertical: 10, paddingHorizontal: 12 }}>
            <Text weight="bold" style={{ fontSize: 13, color: hour === h ? colors.blue : colors.ink }}>
              {hourLabel(h)}
            </Text>
          </Pick>
        ))}
      </ScrollView>
    </View>
  );
}

/**
 * Time off (GET /api/availability/time-off/{id}): leave, newest first, with Upcoming / On leave now / Past /
 * Cancelled; Add (POST /time-off/add: from and to, a reason) and Cancel leave (PUT /time-off/cancel, an optional
 * reason; leave under way ends now). Time off is never deleted.
 */
export default function TimeOff() {
  const { authRequest, profile } = useAuth();
  const list = useApi(profile ? `/api/availability/time-off/${profile.partnerProfileId}` : null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ fromDay: null, fromHour: '09:00', toDay: null, toHour: '18:00', reason: '' });
  const [cancelling, setCancelling] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));
  const [now] = useState(() => Date.now());

  const run = async (action, after) => {
    setError('');
    setBusy(true);
    try {
      await action();
      after();
      await list.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const add = () => {
    if (!form.fromDay || !form.toDay) {
      setError('Choose the day your leave starts and the day it ends.');
      return;
    }
    const startsAt = toInstant(form.fromDay, form.fromHour);
    const endsAt = toInstant(form.toDay, form.toHour);
    if (new Date(endsAt) <= new Date(startsAt)) {
      setError('The leave must end after it starts.');
      return;
    }
    run(
      () =>
        authRequest('/api/availability/time-off/add', {
          method: 'POST',
          body: { partnerProfileId: profile.partnerProfileId, startsAt, endsAt, reason: form.reason.trim() || null }
        }),
      () => {
        setAdding(false);
        setForm({ fromDay: null, fromHour: '09:00', toDay: null, toHour: '18:00', reason: '' });
      }
    );
  };

  const cancel = (item) =>
    run(
      () => authRequest('/api/availability/time-off/cancel', { method: 'PUT', body: { timeOffId: item.timeOffId, reason: cancelReason.trim() || null } }),
      () => {
        setCancelling(null);
        setCancelReason('');
      }
    );

  const items = list.data ?? [];
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Time off" />
      {list.loading ? (
        <Loading />
      ) : (
        <FormScroll contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={list.refreshing} onRefresh={list.refresh} />}>
          <Text style={{ color: colors.muted, fontSize: 13 }}>NexHome does not send you jobs while you are on leave. Leave cannot cover jobs you already have.</Text>
          {adding ? (
            <Card style={{ gap: 14 }}>
              <SectionTitle>Add time off</SectionTitle>
              <DayAndHour label="From" day={form.fromDay} hour={form.fromHour} onDay={set('fromDay')} onHour={set('fromHour')} />
              <DayAndHour label="To" day={form.toDay} hour={form.toHour} onDay={set('toDay')} onHour={set('toHour')} />
              <Field label="Reason (optional)" value={form.reason} onChangeText={set('reason')} maxLength={200} placeholder="e.g. Family function" />
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <Button title="Cancel" variant="secondary" onPress={() => setAdding(false)} style={{ flex: 1 }} />
                <Button title="Save" onPress={add} loading={busy} style={{ flex: 1 }} />
              </View>
            </Card>
          ) : (
            <Button title="Add time off" variant="blue" icon="plus" onPress={() => setAdding(true)} />
          )}
          <ErrorBox message={error || list.error} />
          {!items.length ? <Empty icon="sun" title="No time off yet" /> : null}
          {items.map((item) => {
            const state = stateOf(item, now);
            const tag = timeOffStatus(state);
            const open = state === 'UPCOMING' || state === 'ONGOING';
            return (
              <Card key={item.timeOffId} style={{ gap: 8 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                  <View style={{ flex: 1, gap: 2 }}>
                    <Text weight="bold">{whenLabel(item.startsAt)}</Text>
                    <Text style={{ fontSize: 12.5, color: colors.muted }}>to {whenLabel(item.endsAt)}</Text>
                  </View>
                  <Tag label={tag.label} fg={tag.fg} bg={tag.bg} />
                </View>
                {item.reason ? <Text style={{ fontSize: 13 }}>{item.reason}</Text> : null}
                {item.cancelledAt ? (
                  <Text style={{ fontSize: 12, color: colors.muted }}>
                    Cancelled {whenLabel(item.cancelledAt)}
                    {item.cancellationReason ? `: ${item.cancellationReason}` : ''}
                  </Text>
                ) : null}
                {open && cancelling === item.timeOffId ? (
                  <View style={{ gap: 10 }}>
                    <Field label="Why? (optional)" value={cancelReason} onChangeText={setCancelReason} maxLength={200} />
                    <View style={{ flexDirection: 'row', gap: 8 }}>
                      <Button title="Keep leave" variant="secondary" onPress={() => setCancelling(null)} style={{ flex: 1 }} />
                      <Button title={state === 'ONGOING' ? 'End leave now' : 'Cancel leave'} variant="danger" onPress={() => cancel(item)} loading={busy} style={{ flex: 1 }} />
                    </View>
                  </View>
                ) : open ? (
                  <Text weight="bold" style={{ color: colors.red, paddingVertical: 8 }} accessibilityRole="button" onPress={() => setCancelling(item.timeOffId)}>
                    {state === 'ONGOING' ? 'End leave now' : 'Cancel leave'}
                  </Text>
                ) : null}
              </Card>
            );
          })}
        </FormScroll>
      )}
    </SafeAreaView>
  );
}
