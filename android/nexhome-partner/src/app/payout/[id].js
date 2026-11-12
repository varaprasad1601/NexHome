import { useLocalSearchParams } from 'expo-router';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PayHero } from '../../components/WalletParts';
import { Banner, Card, Empty, Header, Loading, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import useLookup from '../../hooks/useLookup';
import { colors } from '../../theme';
import { localDateLabel, whenLabel } from '../../utils/format';

function Detail({ label, value }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
      <Text style={{ color: colors.muted, fontSize: 13 }}>{label}</Text>
      <Text weight="bold" style={{ fontSize: 13, flexShrink: 1, textAlign: 'right' }}>
        {value}
      </Text>
    </View>
  );
}

/**
 * One payment NexHome made to the partner (GET /api/payouts/manual/{id}), recorded by staff: the amount, how it was
 * paid (PhonePe, Google Pay, ...), the transaction id, the day, notes. A cancelled one (recorded by mistake) shows why.
 */
export default function Payout() {
  const { id } = useLocalSearchParams();
  const payout = useApi(`/api/payouts/manual/${id}`);
  const { labelOf } = useLookup('PAYOUT_MODE');
  const data = payout.data;
  const cancelled = data?.payoutStatus === 'CANCELLED';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Payout" />
      {payout.loading ? <Loading /> : null}
      {!payout.loading && !data ? <Empty icon="alert-circle" title="Payout not found" text={payout.error || undefined} /> : null}
      {data ? (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={payout.refreshing} onRefresh={payout.refresh} />}>
          {cancelled ? (
            <Banner tone="danger" icon="x-circle" title="This payout was cancelled" text={data.cancellationReason || 'It was recorded by mistake and does not count as paid.'} />
          ) : null}
          <PayHero
            owe={cancelled}
            label={cancelled ? 'Cancelled payout' : 'NexHome paid you'}
            amount={data.amount}
            note={`${localDateLabel(data.paidOn)} · ${labelOf(data.paymentMode)}`}
            footer={data.publicId}
          />
          <Card style={{ gap: 9 }}>
            <Detail label="Paid through" value={labelOf(data.paymentMode)} />
            <Detail label="Transaction ID" value={data.transactionId || '—'} />
            <Detail label="Date paid" value={localDateLabel(data.paidOn)} />
            <Detail label="Payout ID" value={data.publicId} />
            <Detail label="Recorded on" value={whenLabel(data.creationDate)} />
            {cancelled && data.cancelledAt ? <Detail label="Cancelled on" value={whenLabel(data.cancelledAt)} /> : null}
          </Card>
          {data.notes ? (
            <Card style={{ gap: 4 }}>
              <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14 }}>
                Notes
              </Text>
              <Text style={{ color: colors.ink, fontSize: 13 }}>{data.notes}</Text>
            </Card>
          ) : null}
        </ScrollView>
      ) : null}
    </SafeAreaView>
  );
}
