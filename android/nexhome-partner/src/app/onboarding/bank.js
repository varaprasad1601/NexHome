import { useState } from 'react';

import BankAccountsList from '../../components/BankAccountsList';
import StepScreen from '../../components/StepScreen';
import { Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/** Step 8: the bank account online earnings are paid to (GET /api/partners/profiles/bank-accounts/{id}). */
export default function Bank() {
  const { profile } = useAuth();
  const accounts = useApi(`/api/partners/profiles/bank-accounts/${profile?.partnerProfileId}`);
  const [error, setError] = useState('');

  const onSave = async () => {
    if (!(accounts.data ?? []).length) {
      setError('Add your bank account.');
      return false;
    }
    return true;
  };

  return (
    <StepScreen step="bank" onSave={onSave} error={error || accounts.error} saveTitle="Done">
      <Text style={{ color: colors.muted, fontSize: 13 }}>Use an account in your own name. NexHome checks it before the first payout.</Text>
      <BankAccountsList accounts={accounts.data} canChange />
    </StepScreen>
  );
}
