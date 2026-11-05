import { RefreshControl, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import DocumentsList from '../../components/DocumentsList';
import { Banner, ErrorBox, Header, Loading } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/**
 * My documents (from Profile): the same list as onboarding. An approved partner may add documents or change ones
 * not verified yet; that sends the profile for verification again (UPDATE_REQUESTED), as the banner warns.
 */
export default function MyDocuments() {
  const { profile } = useAuth();
  const documents = useApi(`/api/partners/profiles/documents/${profile?.partnerProfileId}`);
  const status = profile?.onboardingStatus;
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="My documents" />
      {documents.loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }} refreshControl={<RefreshControl refreshing={documents.refreshing} onRefresh={documents.refresh} />}>
          {status === 'APPROVED' ? (
            <Banner tone="info" text="Adding or changing a document sends your profile for verification again. You cannot take jobs until NexHome approves it." />
          ) : null}
          {status === 'UPDATE_REQUESTED' || status === 'UNDER_REVIEW' ? <Banner tone="info" text="NexHome is verifying your documents." /> : null}
          <ErrorBox message={documents.error} />
          <DocumentsList documents={documents.data} canChange={status !== 'REJECTED'} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}
