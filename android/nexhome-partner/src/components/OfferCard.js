import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { money, serviceNameOf, whenLabel } from '../utils/format';
import { Button, Card, ErrorBox, Field, Tag, Text } from './ui';

/**
 * A job offered or assigned to the partner, with Accept / Decline (a reason): PUT /api/bookings/assignments/respond.
 * The offer itself carries only the booking id, so the card loads the booking (GET /api/bookings/{id}) for the
 * service, time, area and amount. `onDone` runs after an answer; `inJob` when shown on the job's own page.
 */
export default function OfferCard({ assignment, onDone, inJob }) {
  const { authRequest } = useAuth();
  const [booking, setBooking] = useState(null);
  const [declining, setDeclining] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    authRequest(`/api/bookings/${assignment.bookingId}`)
      .then((detail) => setBooking(detail.booking))
      .catch(() => {});
  }, [assignment.bookingId, authRequest]);

  const respond = async (accept) => {
    if (!accept && !reason.trim()) {
      setError('Tell NexHome why you cannot take it.');
      return;
    }
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/bookings/assignments/respond', {
        method: 'PUT',
        body: { assignmentId: assignment.assignmentId, accept, rejectionReason: accept ? null : reason.trim() }
      });
      onDone?.(accept);
      if (accept && !inJob) router.push(`/job/${assignment.bookingId}`);
    } catch (err) {
      setError(err.message);
      onDone?.(null);
    } finally {
      setBusy(false);
    }
  };

  const area = [booking?.address?.addressLine2, booking?.address?.city].filter(Boolean).join(', ');
  return (
    <Card style={{ gap: 12, borderRadius: 18, borderColor: '#cfe2f6', borderWidth: 1.5 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
        <View style={{ flex: 1, gap: 2 }}>
          <Text weight="extrabold" style={{ fontSize: 15 }}>
            {booking ? serviceNameOf(booking.serviceName) : assignment.bookingPublicId}
          </Text>
          <Text style={{ fontSize: 12, color: colors.muted }}>
            {assignment.bookingPublicId}
            {booking ? ` · ${booking.scheduledStartAt ? whenLabel(booking.scheduledStartAt) : 'As soon as possible'}` : ''}
          </Text>
        </View>
        <Tag label={assignment.assignmentStatus === 'ASSIGNED' ? 'Assigned' : 'New offer'} fg={colors.blue} bg={colors.blueTint} />
      </View>
      {booking ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flex: 1 }}>
            <Feather name="map-pin" size={14} color={colors.muted} />
            <Text style={{ fontSize: 12.5, color: colors.muted, flex: 1 }} numberOfLines={1}>
              {area || '—'}
            </Text>
          </View>
          <Text weight="bold" style={{ color: colors.navy }}>
            {money(booking.totalAmount)} · {booking.paymentMethod === 'CASH' ? 'Cash' : 'Online'}
          </Text>
        </View>
      ) : null}
      {assignment.expiresAt ? <Text style={{ fontSize: 12, color: colors.amber }}>Answer by {whenLabel(assignment.expiresAt)}</Text> : null}
      {declining ? (
        <Field label="Why can’t you take it?" value={reason} onChangeText={setReason} placeholder="e.g. Too far, already busy" maxLength={500} multiline />
      ) : null}
      <ErrorBox message={error} />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {declining ? (
          <>
            <Button title="Back" variant="secondary" onPress={() => setDeclining(false)} style={{ flex: 1 }} />
            <Button title="Decline" variant="danger" onPress={() => respond(false)} loading={busy} style={{ flex: 1 }} />
          </>
        ) : (
          <>
            <Button title="Decline" variant="danger" onPress={() => setDeclining(true)} disabled={busy} style={{ flex: 1 }} />
            <Button title="Accept" icon="check" onPress={() => respond(true)} loading={busy} style={{ flex: 1 }} />
          </>
        )}
      </View>
      {inJob ? null : (
        <Text weight="bold" style={{ color: colors.blue, textAlign: 'center', paddingVertical: 4 }} accessibilityRole="button" onPress={() => router.push(`/job/${assignment.bookingId}`)}>
          See details
        </Text>
      )}
    </Card>
  );
}
