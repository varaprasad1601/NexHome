import { router } from 'expo-router';
import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';

import { Banner, Card, Empty, ErrorBox, Loading, Pick, SectionTitle, Tabs, Tag, Text } from '../../components/ui';
import { CashOnlineCards, HowWorkedOut, PayHero, WalletJobs } from '../../components/WalletParts';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { dayLabel, money, serviceNameOf } from '../../utils/format';
import { settlementStatus } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

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
 * Earnings, three tabs. Wallet (GET /api/payouts/wallet/{id}): what is not paid out yet, with no period: "NexHome
 * pays you" (online earnings less the commission on cash jobs; back to 0 after each payout), the cash and online
 * cards, how it is worked out and the jobs. My earnings: the period switcher (Today, This week, This month, Overall) over GET /api/payouts/earnings/{id}?period=
 * with the jobs, total, commission and earnings and each job's record; and the payouts to the bank account
 * (unchanged). Payouts: what was earned since joining and the wallet payouts (GET /api/payouts/settlements), each
 * opening its page (payout/[id]).
 */
export default function Earnings() {
  const { profile } = useAuth();
  const [tab, setTab] = useState('wallet');
  const [period, setPeriod] = useState('TODAY');
  const earnings = useApi(profile ? `/api/payouts/earnings/${profile.partnerProfileId}?period=${period}&size=100` : null);
  const wallet = useApi(profile ? `/api/payouts/wallet/${profile.partnerProfileId}` : null);
  const settlements = useApi(profile ? `/api/payouts/settlements?partnerProfileId=${profile.partnerProfileId}&size=50` : null);
  const data = earnings.data;
  const records = data?.records?.content ?? [];
  const refresh = () => Promise.all([earnings.refresh(), wallet.refresh(), settlements.refresh()]);
  const refreshing = earnings.refreshing || wallet.refreshing || settlements.refreshing;
  // Opening the tab shows the Wallet (and Today under My earnings) again
  useTabReset(() => {
    setTab('wallet');
    setPeriod('TODAY');
  }, refresh);

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          Earnings
        </Text>
        <Tabs
          tabs={[
            ['wallet', 'Wallet'],
            ['earnings', 'My earnings'],
            ['payouts', 'Payouts']
          ]}
          value={tab}
          onChange={setTab}
        />
      </View>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {tab === 'wallet' ? (
          <WalletTab wallet={wallet} />
        ) : tab === 'earnings' ? (
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
            <LinearGradient colors={[colors.blue, '#1660b8']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ borderRadius: 20, padding: 18, gap: 14 }}>
              <View>
                <Text style={{ color: colors.white, opacity: 0.85, fontSize: 12.5 }}>Received · {PERIODS.find((p) => p[0] === period)[1]}</Text>
                <Text weight="extrabold" style={{ color: colors.white, fontSize: 30 }}>
                  {data ? money(data.receivedEarnings ?? data.earnings) : '—'}
                </Text>
                {data && Number(data.pendingEarnings) > 0 ? (
                  <Text style={{ color: colors.white, opacity: 0.85, fontSize: 12.5 }}>
                    {`+ ${money(data.pendingEarnings)} pending · ${data.pendingJobs} ${data.pendingJobs === 1 ? 'job' : 'jobs'} not paid by the customer yet`}
                  </Text>
                ) : null}
              </View>
              <View style={{ flexDirection: 'row', gap: 10 }}>
                <Figure label="Jobs" value={data ? data.jobs : '—'} />
                <Figure label="Job totals" value={data ? money(data.totalAmount) : '—'} />
                <Figure label="Commission" value={data ? money(data.commission) : '—'} />
              </View>
            </LinearGradient>
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
          <PayoutsTab settlements={settlements} wallet={wallet} />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

/** Wallet: what NexHome pays at the next payout (or what the partner owes), with no period. */
function WalletTab({ wallet }) {
  const data = wallet.data;
  if (wallet.loading) return <Loading />;
  if (!data) return <ErrorBox message={wallet.error} />;
  const balance = Number(data.balance);
  const owe = balance < 0;
  const empty = !data.items?.length;
  const last = data.lastSettlement;
  let footer = 'No payouts yet';
  if (data.since) footer = `${empty ? 'Last payout' : 'Since your last payout'}${last ? ` of ${money(last.netAmount)}` : ''} on ${dayLabel(data.since)}`;
  let note = 'Not paid yet · it goes to your bank account';
  if (owe) note = 'The commission on your cash jobs is more than your online earnings; it comes off your next online jobs.';
  else if (empty) note = 'All settled · nothing waiting';
  return (
    <>
      <ErrorBox message={wallet.error} />
      <PayHero owe={owe} label={owe ? 'You owe NexHome' : 'NexHome pays you'} amount={Math.abs(balance)} note={note} footer={footer} />
      {data.onHoldJobs ? (
        <Banner
          tone="warning"
          icon="pause-circle"
          title={`${money(data.onHoldAmount)} on hold`}
          text={`${data.onHoldJobs} online ${data.onHoldJobs === 1 ? 'job is' : 'jobs are'} on hold and not in this amount. Ask NexHome support why.`}
        />
      ) : null}
      {empty ? (
        <Card style={{ alignItems: 'center', gap: 6, paddingVertical: 22 }}>
          <Feather name="check-circle" size={28} color={colors.green} />
          <Text weight="extrabold" style={{ color: colors.navy, fontSize: 15 }}>
            You are all settled
          </Text>
          <Text style={{ color: colors.muted, fontSize: 12.5, textAlign: 'center' }}>New jobs show here until your next payout.</Text>
        </Card>
      ) : (
        <>
          <CashOnlineCards data={data} />
          <HowWorkedOut data={data} total={balance} label={owe ? 'You owe NexHome' : 'NexHome pays you'} />
          <WalletJobs items={data.items} title="Jobs in this amount" />
        </>
      )}
    </>
  );
}

/**
 * Payouts: only money the partner has in hand: the cash they collected plus what NexHome has paid them (a payout adds
 * to it once it is paid; jobs not paid yet are left out), then the same two figures and what is waiting to be paid
 * out (the wallet balance), then every wallet payout.
 */
function PayoutsTab({ settlements, wallet }) {
  const list = settlements.data?.content ?? [];
  const received = wallet.data ? Number(wallet.data.cashKeptTotal) + Number(wallet.data.paidTotal) : null;
  return (
    <>
      <LinearGradient colors={[colors.green, '#1ea36a']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ borderRadius: 20, padding: 18, gap: 2 }}>
        <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5 }}>You earned since joining</Text>
        <Text weight="extrabold" style={{ color: colors.white, fontSize: 30 }}>
          {received === null ? '—' : money(received)}
        </Text>
        <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5 }}>Cash you collected + paid by NexHome</Text>
      </LinearGradient>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {[
          ['Cash collected', wallet.data?.cashKeptTotal],
          ['Paid by NexHome', wallet.data?.paidTotal],
          ['Waiting', wallet.data ? Math.max(0, Number(wallet.data.balance)) : null]
        ].map(([label, value]) => (
          <Card key={label} style={{ flex: 1, gap: 2, padding: 11 }}>
            <Text weight="extrabold" style={{ fontSize: 14, color: colors.navy }}>
              {money(value)}
            </Text>
            <Text style={{ fontSize: 11.5, color: colors.muted }}>{label}</Text>
          </Card>
        ))}
      </View>
      <SectionTitle>Payouts</SectionTitle>
      <ErrorBox message={settlements.error} />
      {settlements.loading ? <Loading /> : null}
      {!settlements.loading && !list.length ? <Empty icon="credit-card" title="No payouts yet" text="NexHome pays your wallet to your bank account." /> : null}
      {list.map((s) => {
        const paid = s.settlementStatus === 'PAID';
        const bank = s.bankAccountLast4 ? ` · ${s.bankName || 'Bank'} •••• ${s.bankAccountLast4}` : '';
        return (
          <Card key={s.settlementId} onPress={() => router.push(`/payout/${s.settlementId}`)} style={{ flexDirection: 'row', alignItems: 'center', gap: 11 }}>
            <View style={{ width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: paid ? colors.greenSoft : colors.redSoft }}>
              <Feather name="briefcase" size={16} color={paid ? colors.green : colors.red} />
            </View>
            <View style={{ flex: 1 }}>
              <Text weight="extrabold" style={{ fontSize: 15, color: colors.navy }}>
                {money(s.netAmount)}
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={1}>
                {`${dayLabel(s.paidAt || s.creationDate)} · ${s.cashJobs + s.onlineJobs} jobs${bank}`}
              </Text>
            </View>
            <Tag label={paid ? 'Paid' : 'Failed'} fg={paid ? colors.green : colors.red} bg={paid ? colors.greenSoft : colors.redSoft} />
            <Feather name="chevron-right" size={18} color={colors.muted} />
          </Card>
        );
      })}
    </>
  );
}
