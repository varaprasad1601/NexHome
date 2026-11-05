import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { View } from 'react-native';

import { colors } from '../theme';
import { verificationStatus } from '../utils/status';
import { Button, Card, Tag, Text } from './ui';

/** The partner's bank accounts: bank, branch, holder, last 4 digits, IFSC, status (and why it was rejected), Edit. */
export default function BankAccountsList({ accounts, canChange }) {
  const list = accounts ?? [];
  return (
    <View style={{ gap: 10 }}>
      {list.map((account) => {
        const status = verificationStatus(account.verificationStatus);
        return (
          <Card key={account.partnerBankAccountId} style={{ gap: 8 }}>
            <View style={{ flexDirection: 'row', gap: 10 }}>
              <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name="credit-card" size={18} color={colors.green} />
              </View>
              <View style={{ flex: 1, gap: 2 }}>
                <Text weight="bold">{[account.bankName, account.branchName].filter(Boolean).join(' · ') || 'Bank account'}</Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>
                  {account.accountHolderName} · ••••{account.accountNumberLast4} · {account.ifscCode}
                </Text>
                {account.isPrimary && list.length > 1 ? <Text style={{ fontSize: 12, color: colors.greenDark }}>Payouts go here</Text> : null}
              </View>
              <Tag label={status.label} fg={status.fg} bg={status.bg} />
            </View>
            {account.verificationStatus === 'REJECTED' && account.rejectionReason ? (
              <Text style={{ color: colors.red, fontSize: 12.5 }}>Rejected: {account.rejectionReason}</Text>
            ) : null}
            {canChange ? (
              <Button
                title="Edit"
                variant="secondary"
                icon="edit-3"
                onPress={() => router.push({ pathname: '/bank/form', params: { accountId: account.partnerBankAccountId } })}
                style={{ minHeight: 44 }}
              />
            ) : null}
          </Card>
        );
      })}
      {canChange ? <Button title="Add bank account" variant="blue" icon="plus" onPress={() => router.push('/bank/form')} /> : null}
    </View>
  );
}
