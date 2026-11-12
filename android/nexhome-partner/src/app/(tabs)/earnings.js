import { router } from 'expo-router';
import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';

import { Card, Empty, ErrorBox, Loading, Pick, SectionTitle, Tabs, Tag, Text } from '../../components/ui';
import { CashOnlineCards, HowWorkedOut, PayHero } from '../../components/WalletParts';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import useLookup from '../../hooks/useLookup';
import { colors } from '../../theme';
import { dayLabel, localDateLabel, money, parseApiDate, serviceNameOf } from '../../utils/format';
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
 * Earnings, three tabs. Wallet (GET /api/payouts/wallet/{id}): what NexHome still owes, since joining: "NexHome
 * owes you" (every job, cash and online, less commission, less what NexHome has paid; partners hand cash to NexHome),
 * the cash and online cards and how it is worked out. My earnings: the period switcher (Today, This week, This month, Overall) over GET /api/payouts/earnings/{id}?period=
 * with the jobs, total, commission and earnings and each job's record; and the payouts to the bank account
 * (unchanged). Payouts: what NexHome has paid (recorded by staff: PhonePe, Google Pay, ...; GET /api/payouts/manual),
 * each opening its page (payout/[id]), and what NexHome deducted (penalties, damage, ...; GET /api/payouts/deductions),
 * in one list by date.
 */
export default function Earnings() {
  const { profile } = useAuth();
  const [tab, setTab] = useState('wallet');
  const [period, setPeriod] = useState('TODAY');
  const earnings = useApi(profile ? `/api/payouts/earnings/${profile.partnerProfileId}?period=${period}&size=100` : null);
  const wallet = useApi(profile ? `/api/payouts/wallet/${profile.partnerProfileId}` : null);
  const payouts = useApi(profile ? `/api/payouts/manual?partnerProfileId=${profile.partnerProfileId}&size=50` : null);
  const deductions = useApi(profile ? `/api/payouts/deductions?partnerProfileId=${profile.partnerProfileId}&size=50` : null);
  const data = earnings.data;
  const records = data?.records?.content ?? [];
  const refresh = () => Promise.all([earnings.refresh(), wallet.refresh(), payouts.refresh(), deductions.refresh()]);
  const refreshing = earnings.refreshing || wallet.refreshing || payouts.refreshing || deductions.refreshing;
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
          <PayoutsTab payouts={payouts} deductions={deductions} wallet={wallet} />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

/** Wallet: what NexHome still owes the partner, since joining (no period). */
function WalletTab({ wallet }) {
  const { labelOf } = useLookup('PAYOUT_MODE');
  const data = wallet.data;
  if (wallet.loading) return <Loading />;
  if (!data) return <ErrorBox message={wallet.error} />;
  const balance = Number(data.balance);
  const advance = balance < 0;
  const settled = balance === 0;
  const empty = !data.items?.length;
  const last = data.lastPayout;
  const footer = last ? `Last paid ${money(last.amount)} on ${localDateLabel(last.paidOn)} via ${labelOf(last.paymentMode)}` : 'No payouts yet';
  let label = 'NexHome owes you';
  let note = 'Your share of every job, less what NexHome has paid you';
  if (advance) {
    label = 'Paid in advance';
    note = 'NexHome has paid you more than your jobs so far; it comes off your next jobs.';
  } else if (settled) {
    label = 'All settled';
    note = 'NexHome has paid you for every job';
  }
  return (
    <>
      <ErrorBox message={wallet.error} />
      <PayHero owe={advance} label={label} amount={Math.abs(balance)} note={note} footer={footer} />
      {empty ? (
        <Card style={{ alignItems: 'center', gap: 6, paddingVertical: 22 }}>
          <Feather name="briefcase" size={28} color={colors.green} />
          <Text weight="extrabold" style={{ color: colors.navy, fontSize: 15 }}>
            No jobs yet
          </Text>
          <Text style={{ color: colors.muted, fontSize: 12.5, textAlign: 'center' }}>Your completed jobs show here with your share.</Text>
        </Card>
      ) : (
        <>
          <CashOnlineCards data={data} />
          <HowWorkedOut data={data} />
        </>
      )}
    </>
  );
}

/**
 * Payouts: what NexHome has paid (staff record each payment made through PhonePe, Google Pay, Paytm, a bank transfer
 * or cash), what was earned and what is still to be paid, then every payout and deduction (penalties, damage, ...) by date.
 */
function PayoutsTab({ payouts, deductions, wallet }) {
  const { labelOf } = useLookup('PAYOUT_MODE');
  const reasons = useLookup('DEDUCTION_REASON');
  const data = wallet.data;
  const deducted = Number(data?.deductedTotal || 0);
  // Payouts and deductions in one list: the newest day first, then the latest recorded
  const list = [
    ...(payouts.data?.content ?? []).map((p) => ({ kind: 'payout', key: `p${p.payoutId}`, day: p.paidOn, item: p })),
    ...(deductions.data?.content ?? []).map((d) => ({ kind: 'deduction', key: `d${d.deductionId}`, day: d.deductedOn, item: d }))
  ].sort((x, y) => (y.day ?? '').localeCompare(x.day ?? '') || (parseApiDate(y.item.creationDate) ?? 0) - (parseApiDate(x.item.creationDate) ?? 0));
  const loading = payouts.loading || deductions.loading;
  return (
    <>
      <LinearGradient colors={[colors.green, '#1ea36a']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ borderRadius: 20, padding: 18, gap: 2 }}>
        <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5 }}>Paid by NexHome</Text>
        <Text weight="extrabold" style={{ color: colors.white, fontSize: 30 }}>
          {data ? money(data.paidTotal) : '—'}
        </Text>
        <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5 }}>
          {data ? `${data.payoutCount} ${data.payoutCount === 1 ? 'payout' : 'payouts'} since you joined` : ' '}
        </Text>
      </LinearGradient>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {[
          ['You earned', data?.totalEarned],
          ['Paid', data?.paidTotal],
          ['Still to be paid', data ? Math.max(0, Number(data.balance)) : null]
        ].map(([label, value]) => (
          <Card key={label} style={{ flex: 1, gap: 2, padding: 11 }}>
            <Text weight="extrabold" style={{ fontSize: 14, color: colors.navy }}>
              {value === null || value === undefined ? '—' : money(value)}
            </Text>
            <Text style={{ fontSize: 11.5, color: colors.muted }}>{label}</Text>
          </Card>
        ))}
      </View>
      {deducted > 0 ? (
        <Text weight="semibold" style={{ fontSize: 12.5, color: colors.red, opacity: 0.85 }}>
          {`${money(deducted)} deducted by NexHome`}
        </Text>
      ) : null}
      <SectionTitle>Payouts & deductions</SectionTitle>
      <ErrorBox message={payouts.error || deductions.error} />
      {loading ? <Loading /> : null}
      {!loading && !list.length ? <Empty icon="credit-card" title="No payouts yet" text="When NexHome pays you, the payment shows here." /> : null}
      {list.map(({ kind, key, item }) => {
        const cancelled = (kind === 'payout' ? item.payoutStatus : item.status) === 'CANCELLED';
        if (kind === 'deduction') {
          const line = [localDateLabel(item.deductedOn), reasons.labelOf(item.deductionReason), item.notes].filter(Boolean).join(' · ');
          return (
            <Card key={key} style={{ flexDirection: 'row', alignItems: 'center', gap: 11, opacity: cancelled ? 0.6 : 1 }}>
              <View style={{ width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: cancelled ? colors.ground : colors.redSoft }}>
                <Feather name="minus-circle" size={16} color={cancelled ? colors.muted : colors.red} />
              </View>
              <View style={{ flex: 1 }}>
                <Text weight="extrabold" style={{ fontSize: 15, color: cancelled ? colors.muted : colors.red, textDecorationLine: cancelled ? 'line-through' : 'none' }}>
                  {`− ${money(item.amount)}`}
                </Text>
                <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={2}>
                  {line}
                </Text>
              </View>
              {cancelled ? <Tag label="Cancelled" fg={colors.muted} bg={colors.ground} /> : <Tag label="Deduction" fg={colors.red} bg={colors.redSoft} />}
            </Card>
          );
        }
        return (
          <Card key={key} onPress={() => router.push(`/payout/${item.payoutId}`)} style={{ flexDirection: 'row', alignItems: 'center', gap: 11, opacity: cancelled ? 0.6 : 1 }}>
            <View style={{ width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: cancelled ? colors.ground : colors.greenSoft }}>
              <Feather name="send" size={16} color={cancelled ? colors.muted : colors.green} />
            </View>
            <View style={{ flex: 1 }}>
              <Text weight="extrabold" style={{ fontSize: 15, color: cancelled ? colors.muted : colors.navy, textDecorationLine: cancelled ? 'line-through' : 'none' }}>
                {money(item.amount)}
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={1}>
                {`${localDateLabel(item.paidOn)} · ${labelOf(item.paymentMode)}${item.transactionId ? ` · ${item.transactionId}` : ''}`}
              </Text>
            </View>
            {cancelled ? <Tag label="Cancelled" fg={colors.muted} bg={colors.ground} /> : null}
            <Feather name="chevron-right" size={18} color={colors.muted} />
          </Card>
        );
      })}
    </>
  );
}
