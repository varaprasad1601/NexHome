import { useLocalSearchParams } from 'expo-router';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { CashOnlineCards, HowWorkedOut, PayHero, WalletJobs } from '../../components/WalletParts';
import { Card, Empty, Header, Loading, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { whenLabel } from '../../utils/format';

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
 * One wallet payout (GET /api/payouts/settlements/{id}): "NexHome paid you" first, then the cash and online cards
 * with each one's commission, how the amount was worked out, the bank account and reference, and its jobs.
 */
export default function Payout() {
  const { id } = useLocalSearchParams();
  const payout = useApi(`/api/payouts/settlements/${id}`);
  const data = payout.data;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Payout" />
      {payout.loading ? <Loading /> : null}
      {!payout.loading && !data ? <Empty icon="alert-circle" title="Payout not found" text={payout.error || undefined} /> : null}
      {data ? (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={payout.refreshing} onRefresh={payout.refresh} />}>
          <PayHero
            owe={data.settlementStatus !== 'PAID'}
            label={data.settlementStatus === 'PAID' ? 'NexHome paid you' : 'This payout failed'}
            amount={data.netAmount}
            note={data.settlementStatus === 'PAID' ? `Paid ${whenLabel(data.paidAt)}` : 'Nothing was sent; the jobs stay in your wallet.'}
            footer={`${data.cashJobs + data.onlineJobs} jobs · ${data.publicId}`}
          />
          <CashOnlineCards data={data} paid />
          <HowWorkedOut data={data} total={data.netAmount} label={data.settlementStatus === 'PAID' ? 'NexHome paid you' : 'To pay'} />
          <Card style={{ gap: 9 }}>
            <Detail label="Bank account" value={data.bankAccountLast4 ? `${data.bankName || 'Bank'} •••• ${data.bankAccountLast4}` : '—'} />
            <Detail label="Reference" value={data.providerTransferId || '—'} />
            <Detail label="Status" value={data.settlementStatus === 'PAID' ? 'Paid' : 'Failed'} />
          </Card>
          <WalletJobs items={data.items} title="Jobs in this payout" />
        </ScrollView>
      ) : null}
    </SafeAreaView>
  );
}
