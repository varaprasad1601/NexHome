import { RefreshControl, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import BankAccountsList from '../../components/BankAccountsList';
import { Banner, ErrorBox, Header, Loading } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/**
 * My bank accounts (from Profile). Once approved (APPROVED or UPDATE_REQUESTED) they are read only: no Edit (which
 * holds Delete) and no Add; the backend refuses the partner's changes too. Staff can still change them.
 */
export default function MyBankAccounts() {
  const { profile } = useAuth();
  const accounts = useApi(`/api/partners/profiles/bank-accounts/${profile?.partnerProfileId}`);
  const status = profile?.onboardingStatus;
  const locked = status === 'APPROVED' || status === 'UPDATE_REQUESTED';
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Bank accounts" />
      {accounts.loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }} refreshControl={<RefreshControl refreshing={accounts.refreshing} onRefresh={accounts.refresh} />}>
          {locked ? (
            <Banner tone="info" text="Your bank account is locked after approval. To change it, contact NexHome support from Help & support." />
          ) : null}
          <ErrorBox message={accounts.error} />
          <BankAccountsList accounts={accounts.data} canChange={!locked && status !== 'REJECTED'} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}
