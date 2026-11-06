import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { View } from 'react-native';

import { colors } from '../theme';
import { dayLabel, money, serviceNameOf } from '../utils/format';
import { Card, SectionTitle, Text } from './ui';

/**
 * The wallet's pieces (Earnings > Wallet and a payout's page): the green "NexHome pays you" card, the cash and
 * online cards with their commission, how the amount is worked out, and the jobs. Online jobs: NexHome received the
 * money and owes the partner it less commission; cash jobs: the partner kept the cash and owes its commission.
 */

/** The amount on a green card (amber when the partner owes NexHome). */
export function PayHero({ label, amount, note, footer, owe }) {
  return (
    <LinearGradient
      colors={owe ? ['#c98a06', colors.amber] : [colors.green, '#0f7a4c']}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{ borderRadius: 20, padding: 18, gap: 2 }}
    >
      <Text weight="bold" style={{ color: colors.white, opacity: 0.9, fontSize: 13 }}>
        {label}
      </Text>
      <Text weight="extrabold" style={{ color: colors.white, fontSize: 32 }}>
        {money(amount)}
      </Text>
      {note ? <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12.5 }}>{note}</Text> : null}
      {footer ? (
        <View style={{ marginTop: 8, paddingTop: 8, borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.25)' }}>
          <Text style={{ color: colors.white, opacity: 0.9, fontSize: 12 }}>{footer}</Text>
        </View>
      ) : null}
    </LinearGradient>
  );
}

function MoneyCard({ cash, jobs, amount, commission, note }) {
  return (
    <View style={{ flex: 1, borderRadius: 16, padding: 13, gap: 3, backgroundColor: cash ? colors.amberSoft : colors.blueTint }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
        <Feather name={cash ? 'dollar-sign' : 'credit-card'} size={13} color={cash ? colors.amber : colors.blue} />
        <Text weight="extrabold" style={{ fontSize: 12, color: colors.ink }}>
          {cash ? 'Cash' : 'Online'} · {jobs} {jobs === 1 ? 'job' : 'jobs'}
        </Text>
      </View>
      <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy }}>
        {money(amount)}
      </Text>
      <Text style={{ fontSize: 11.5, color: colors.muted }}>{note}</Text>
      <Text weight="bold" style={{ fontSize: 11.5, color: cash ? colors.red : colors.blue }}>
        Commission {money(commission)}
      </Text>
    </View>
  );
}

/** Cash and online side by side; `paid` words them for a payout already made. */
export function CashOnlineCards({ data, paid }) {
  return (
    <View style={{ flexDirection: 'row', gap: 10 }}>
      <MoneyCard cash jobs={data.cashJobs} amount={data.cashAmount} commission={data.cashCommission} note="You kept it" />
      <MoneyCard jobs={data.onlineJobs} amount={data.onlineAmount} commission={data.onlineCommission} note={paid ? 'NexHome received it' : 'NexHome holds it'} />
    </View>
  );
}

function Line({ label, value, tone, strong }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
      <Text weight={strong ? 'extrabold' : 'regular'} style={{ fontSize: strong ? 14 : 13, color: strong ? colors.navy : colors.muted, flex: 1 }}>
        {label}
      </Text>
      <Text weight="extrabold" style={{ fontSize: strong ? 16 : 13.5, color: tone === 'red' ? colors.red : strong ? colors.navy : colors.ink }}>
        {value}
      </Text>
    </View>
  );
}

/** Online after commission (and adjustments), less the commission on cash, = the amount. */
export function HowWorkedOut({ data, total, label }) {
  const adjustments = Number(data.adjustmentsAmount || 0);
  return (
    <Card style={{ gap: 9 }}>
      <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14 }}>
        How it is worked out
      </Text>
      <Line label="Online jobs" value={money(data.onlineAmount)} />
      <Line label="Commission on online jobs" value={`− ${money(data.onlineCommission)}`} tone="red" />
      {adjustments ? <Line label="Adjustments" value={`${adjustments > 0 ? '+' : '−'} ${money(Math.abs(adjustments))}`} tone={adjustments < 0 ? 'red' : undefined} /> : null}
      <Line label="Commission on your cash jobs" value={`− ${money(data.cashCommission)}`} tone="red" />
      <View style={{ height: 1, backgroundColor: colors.line }} />
      <Line label={label} value={money(Math.abs(Number(total)))} strong />
    </Card>
  );
}

/** Each job with its method, commission and what it does to the payout (online +, cash −). */
export function WalletJobs({ items, title }) {
  if (!items?.length) return null;
  return (
    <>
      <SectionTitle>{title}</SectionTitle>
      {items.map((item) => {
        const cash = item.paymentMethod === 'CASH';
        return (
          <Card key={`${item.paymentMethod}-${item.bookingId}`} onPress={() => router.push(`/job/${item.bookingId}`)} style={{ flexDirection: 'row', alignItems: 'center', gap: 11 }}>
            <View style={{ width: 36, height: 36, borderRadius: 11, alignItems: 'center', justifyContent: 'center', backgroundColor: cash ? colors.amberSoft : colors.blueTint }}>
              <Feather name={cash ? 'dollar-sign' : 'credit-card'} size={16} color={cash ? colors.amber : colors.blue} />
            </View>
            <View style={{ flex: 1 }}>
              <Text weight="bold" numberOfLines={1}>
                {serviceNameOf(item.serviceName)}
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={1}>
                {item.bookingPublicId}
                {item.completedAt ? ` · ${dayLabel(item.completedAt)}` : ''}
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }}>
                {cash ? 'Cash' : 'Online'} {money(item.amount)} · commission {money(item.commission)}
              </Text>
            </View>
            <Text weight="extrabold" style={{ fontSize: 14, color: cash ? colors.red : colors.greenDark }}>
              {cash ? `− ${money(item.commission)}` : `+ ${money(item.effect)}`}
            </Text>
          </Card>
        );
      })}
    </>
  );
}
