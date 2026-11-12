import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { View } from 'react-native';

import { colors } from '../theme';
import { money } from '../utils/format';
import { Card, Text } from './ui';

/**
 * The wallet's pieces (Earnings > Wallet): the green "NexHome owes you" card, the cash and online cards with their
 * commission, and how the amount is worked out. Partners hand the cash of cash jobs to NexHome, so NexHome
 * owes them every job (cash and online) less its commission, less what it has already paid them.
 */

/** The amount on a green card (amber for `owe`, e.g. paid in advance). */
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

function MoneyCard({ cash, amount, commission, note }) {
  return (
    <View style={{ flex: 1, borderRadius: 16, padding: 13, gap: 3, backgroundColor: cash ? colors.amberSoft : colors.blueTint }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
        <Feather name={cash ? 'dollar-sign' : 'credit-card'} size={13} color={cash ? colors.amber : colors.blue} />
        <Text weight="extrabold" style={{ fontSize: 12, color: colors.ink }}>
          {cash ? 'Cash' : 'Online'}
        </Text>
      </View>
      <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy }}>
        {money(amount)}
      </Text>
      <Text style={{ fontSize: 11.5, color: colors.muted }}>{note}</Text>
      <Text weight="bold" style={{ fontSize: 11.5, color: colors.red }}>
        Commission {money(commission)}
      </Text>
    </View>
  );
}

/** Cash and online side by side. */
export function CashOnlineCards({ data }) {
  return (
    <View style={{ flexDirection: 'row', gap: 10 }}>
      <MoneyCard cash amount={data.cashAmount} commission={data.cashCommission} note="Handed to NexHome" />
      <MoneyCard amount={data.onlineAmount} commission={data.onlineCommission} note="NexHome received it" />
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

/**
 * The cash and the online amount, each less its commission (and adjustments) = earned, less what NexHome paid and
 * deducted (penalties, damage, ...) = what NexHome owes.
 */
export function HowWorkedOut({ data }) {
  const adjustments = Number(data.adjustmentsAmount || 0);
  const balance = Number(data.balance);
  return (
    <Card style={{ gap: 9 }}>
      <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14 }}>
        How it is worked out
      </Text>
      <Line label="Cash collected" value={money(data.cashAmount)} />
      <Line label="Commission on cash" value={`− ${money(data.cashCommission)}`} tone="red" />
      <Line label="Online payments" value={`+ ${money(data.onlineAmount)}`} />
      <Line label="Commission on online" value={`− ${money(data.onlineCommission)}`} tone="red" />
      {adjustments ? <Line label="Adjustments" value={`${adjustments > 0 ? '+' : '−'} ${money(Math.abs(adjustments))}`} tone={adjustments < 0 ? 'red' : undefined} /> : null}
      <View style={{ height: 1, backgroundColor: colors.line }} />
      <Line label="You earned" value={money(data.totalEarned)} strong />
      <Line label="Paid by NexHome" value={`− ${money(data.paidTotal)}`} tone="red" />
      {Number(data.deductedTotal || 0) > 0 ? <Line label="Deducted by NexHome" value={`− ${money(data.deductedTotal)}`} tone="red" /> : null}
      <View style={{ height: 1, backgroundColor: colors.line }} />
      <Line label={balance < 0 ? 'Paid in advance' : 'NexHome owes you'} value={money(Math.abs(balance))} strong />
    </Card>
  );
}
