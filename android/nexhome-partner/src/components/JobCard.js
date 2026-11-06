import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { View } from 'react-native';

import { colors } from '../theme';
import { dayLabel, money, serviceNameOf, whenLabel } from '../utils/format';
import { UPCOMING, bookingStatus, isPaymentPending, jobTag } from '../utils/status';
import Progress from './Progress';
import { Card, Tag, Text } from './ui';

/** One job in a list: service, id and time, status tag, progress (while open), area, the customer and the total. */
export default function JobCard({ job }) {
  const status = bookingStatus(job.bookingStatus);
  const tag = jobTag(job);
  const when = job.scheduledStartAt ? whenLabel(job.scheduledStartAt) : 'As soon as possible';
  const area = [job.address?.addressLine2, job.address?.city].filter(Boolean).join(', ');
  const open = UPCOMING.includes(job.bookingStatus);
  return (
    <Card onPress={() => router.push(`/job/${job.bookingId}`)} style={{ gap: 12, borderRadius: 18 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
        <View style={{ flex: 1, gap: 2 }}>
          <Text weight="extrabold" style={{ fontSize: 15 }}>
            {serviceNameOf(job.serviceName)}
          </Text>
          <Text style={{ fontSize: 12, color: colors.muted }}>
            {job.publicId} · {job.bookingStatus === 'COMPLETED' ? `Completed ${dayLabel(job.actualEndAt)}` : when}
          </Text>
        </View>
        <Tag label={tag.label} fg={tag.fg} bg={tag.bg} />
      </View>
      {open && status.step ? <Progress step={status.step} /> : null}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flex: 1 }}>
          <Feather name="map-pin" size={14} color={colors.muted} />
          <Text style={{ fontSize: 12.5, color: colors.muted, flex: 1 }} numberOfLines={1}>
            {[job.customerName, area].filter(Boolean).join(' · ') || '—'}
          </Text>
        </View>
        <Text weight="bold" style={{ color: isPaymentPending(job) ? colors.amber : colors.navy }}>
          {money(job.totalAmount)}
          {isPaymentPending(job) ? (job.paymentMethod === 'CASH' ? ' · cash' : ' · online') : ''}
        </Text>
      </View>
    </Card>
  );
}
