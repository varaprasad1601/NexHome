import { RefreshControl, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import BankAccountsList from '../../components/BankAccountsList';
import { Banner, ErrorBox, Header, Loading } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/** My bank accounts (from Profile). An approved partner's change sends the profile for verification again. */
export default function MyBankAccounts() {
  const { profile } = useAuth();
  const accounts = useApi(`/api/partners/profiles/bank-accounts/${profile?.partnerProfileId}`);
  const status = profile?.onboardingStatus;
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Bank accounts" />
      {accounts.loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }} refreshControl={<RefreshControl refreshing={accounts.refreshing} onRefresh={accounts.refresh} />}>
          {status === 'APPROVED' ? (
            <Banner tone="info" text="Adding a bank account or changing the holder's name sends your profile for verification again. You cannot take jobs until NexHome approves it." />
          ) : null}
          <ErrorBox message={accounts.error} />
          <BankAccountsList accounts={accounts.data} canChange={status !== 'REJECTED'} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}
