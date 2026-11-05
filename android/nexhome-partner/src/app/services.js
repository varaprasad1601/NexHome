import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Card, Empty, ErrorBox, Header, Loading, Tag, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import { colors } from '../theme';

/**
 * The services the partner offers (GET /api/partners/profiles/services/{id}), read only: after submitting, partners
 * cannot change them (the backend allows it only while NOT_STARTED, DRAFT or CHANGES_REQUESTED); support can.
 */
export default function MyServices() {
  const { profile } = useAuth();
  const services = useApi(profile ? `/api/partners/profiles/services/${profile.partnerProfileId}` : null);
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="My services" />
      {services.loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} refreshControl={<RefreshControl refreshing={services.refreshing} onRefresh={services.refresh} />}>
          <Banner tone="info" text="To add or remove a service, contact NexHome support." />
          <ErrorBox message={services.error} />
          {!(services.data ?? []).length ? <Empty icon="tool" title="No services yet" /> : null}
          {(services.data ?? []).map((s) => (
            <Card key={s.partnerServiceId} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
              <View style={{ flex: 1 }}>
                <Text weight="bold">{s.serviceName}</Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>
                  {[s.categoryName, s.experienceYears !== null && s.experienceYears !== undefined ? `${s.experienceYears} yrs experience` : null].filter(Boolean).join(' · ')}
                </Text>
              </View>
              {s.isPrimaryService ? <Tag label="Main" fg={colors.greenDark} bg={colors.greenSoft} /> : null}
              {!s.isActive ? <Tag label="Paused" fg={colors.muted} bg={colors.ground} /> : null}
            </Card>
          ))}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}
