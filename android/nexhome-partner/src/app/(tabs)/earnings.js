import { router } from 'expo-router';
import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Card, Empty, ErrorBox, Loading, Pick, SectionTitle, Tabs, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { dayLabel, money, serviceNameOf } from '../../utils/format';
import { payoutStatus, settlementStatus } from '../../utils/status';

const PERIODS = [
  ['TODAY', 'Today'],
  ['WEEK', 'This week'],
  ['MONTH', 'This month'],
  ['ALL', 'Overall']
];

function Figure({ label, value }) {
  return (
    <View style={{ flex: 1, gap: 2 }}>
      <Text style={{ fontSize: 11.5, color: colors.white, opacity: 0.8 }}>{label}</Text>
      <Text weight="extrabold" style={{ fontSize: 15, color: colors.white }}>
        {value}
      </Text>
    </View>
  );
}

/**
 * Earnings: the period switcher (Today, This week, This month, Overall) over GET /api/payouts/earnings/{id}?period=
 * with the jobs, total, commission and earnings and each job's record; and the payouts to the bank account
 * (GET /api/payouts/me).
 */
export default function Earnings() {
  const { profile } = useAuth();
  const [tab, setTab] = useState('earnings');
  const [period, setPeriod] = useState('TODAY');
  const earnings = useApi(profile ? `/api/payouts/earnings/${profile.partnerProfileId}?period=${period}&size=100` : null);
  const payouts = useApi('/api/payouts/me?size=50');
  const data = earnings.data;
  const records = data?.records?.content ?? [];
  const refresh = () => Promise.all([earnings.refresh(), payouts.refresh()]);

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          Earnings
        </Text>
        <Tabs
          tabs={[
            ['earnings', 'My earnings'],
            ['payouts', 'Payouts']
          ]}
          value={tab}
          onChange={setTab}
        />
      </View>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={earnings.refreshing || payouts.refreshing} onRefresh={refresh} />}>
        {tab === 'earnings' ? (
          <>
            <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', gap: 6 }}>
              {PERIODS.map(([key, label]) => (
                <Pick key={key} selected={period === key} onPress={() => setPeriod(key)} style={{ flex: 1, alignItems: 'center', paddingHorizontal: 4, minHeight: 44, justifyContent: 'center' }}>
                  <Text weight="bold" style={{ fontSize: 12, color: period === key ? colors.blue : colors.ink }} numberOfLines={1}>
                    {label}
                  </Text>
                </Pick>
              ))}
            </View>
            <View style={{ borderRadius: 20, padding: 18, backgroundColor: colors.blue, gap: 14 }}>
              <View>
                <Text style={{ color: colors.white, opacity: 0.85, fontSize: 12.5 }}>Your earnings · {PERIODS.find((p) => p[0] === period)[1]}</Text>
                <Text weight="extrabold" style={{ color: colors.white, fontSize: 30 }}>
                  {data ? money(data.earnings) : '—'}
                </Text>
              </View>
              <View style={{ flexDirection: 'row', gap: 10 }}>
                <Figure label="Jobs" value={data ? data.jobs : '—'} />
                <Figure label="Job totals" value={data ? money(data.totalAmount) : '—'} />
                <Figure label="Commission" value={data ? money(data.commission) : '—'} />
              </View>
            </View>
            <ErrorBox message={earnings.error} />
            <SectionTitle>Jobs</SectionTitle>
            {earnings.loading ? <Loading /> : null}
            {!earnings.loading && !records.length ? <Empty icon="trending-up" title="No completed jobs in this period" /> : null}
            {records.map((r) => {
              const settled = settlementStatus(r.settlement, r.settlementStatus);
              return (
                <Card key={r.bookingId} onPress={() => router.push(`/job/${r.bookingId}`)} style={{ gap: 8 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                    <View style={{ flex: 1 }}>
                      <Text weight="bold">{serviceNameOf(r.serviceName)}</Text>
                      <Text style={{ fontSize: 12, color: colors.muted }}>
                        {r.bookingPublicId} · {dayLabel(r.completedAt)} · {r.paymentMethod === 'CASH' ? 'Cash' : 'Online'}
                      </Text>
                    </View>
                    <Text weight="extrabold" style={{ color: colors.greenDark, fontSize: 15 }}>
                      {money(r.partnerEarnings)}
                    </Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Text style={{ fontSize: 12, color: colors.muted }}>
                      {money(r.totalAmount)} − {money(r.commission)} commission
                    </Text>
                    <Tag label={settled.label} fg={settled.fg} bg={settled.bg} />
                  </View>
                </Card>
              );
            })}
          </>
        ) : (
          <>
            <Text style={{ color: colors.muted, fontSize: 13 }}>Online jobs are paid to your bank account. Cash jobs are paid by the customer directly.</Text>
            <ErrorBox message={payouts.error} />
            {payouts.loading ? <Loading /> : null}
            {!payouts.loading && !(payouts.data?.content ?? []).length ? <Empty icon="credit-card" title="No payouts yet" /> : null}
            {(payouts.data?.content ?? []).map((p) => {
              const status = payoutStatus(p.payoutStatus);
              return (
                <Card key={p.payoutId} onPress={() => router.push(`/job/${p.bookingId}`)} style={{ gap: 8 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                    <View style={{ flex: 1 }}>
                      <Text weight="bold">{p.publicId}</Text>
                      <Text style={{ fontSize: 12, color: colors.muted }}>
                        For {p.bookingPublicId} · {p.paidAt ? `Paid ${dayLabel(p.paidAt)}` : dayLabel(p.creationDate)}
                      </Text>
                    </View>
                    <Text weight="extrabold" style={{ color: colors.navy, fontSize: 15 }}>
                      {money(p.netAmount)}
                    </Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Text style={{ fontSize: 12, color: colors.muted }}>
                      {money(p.grossAmount)} − {money(p.platformCommission)}
                      {Number(p.adjustmentsAmount) ? ` ${Number(p.adjustmentsAmount) > 0 ? '+' : '−'} ${money(Math.abs(Number(p.adjustmentsAmount)))}` : ''}
                    </Text>
                    <Tag label={status.label} fg={status.fg} bg={status.bg} />
                  </View>
                </Card>
              );
            })}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
