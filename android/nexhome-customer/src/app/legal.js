import { useLocalSearchParams } from 'expo-router';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import RichText from '../components/RichText';
import { Empty, Header, Loading, Text } from '../components/ui';
import { POLICY_KEYS } from '../constants/policies';
import useApi from '../hooks/useApi';
import { colors } from '../theme';

/**
 * One current terms or policy document by its key (public, GET /api/policies/current/{key}): `?key=` (e.g. the
 * login or registration terms or policy), `?view=terms` the app's terms, `?view=policies` the app's policy.
 */
export default function Legal() {
  const { key, view } = useLocalSearchParams();
  const chosen = key || (view === 'policies' ? POLICY_KEYS.APP_POLICY : POLICY_KEYS.APP_TERMS);
  return <OnePolicy policyKey={String(chosen)} fallbackTitle={String(chosen).endsWith('_POLICY') ? 'Privacy Policy' : 'Terms & Conditions'} />;
}

function OnePolicy({ policyKey, fallbackTitle }) {
  const policy = useApi(`/api/policies/current/${policyKey}`);
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={policy.data?.title ?? fallbackTitle} />
      {policy.loading ? (
        <Loading />
      ) : !policy.data ? (
        <Empty icon="file-text" title="Not published yet" text="This will appear here once NexHome publishes it." />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          <View style={{ backgroundColor: colors.white, borderRadius: 16, borderWidth: 1, borderColor: colors.line, padding: 16, gap: 12 }}>
            <Text style={{ fontSize: 12, color: colors.muted }}>
              Version {policy.data.version} · effective {String(policy.data.effectiveFrom ?? '').slice(0, 16)}
            </Text>
            <RichText html={policy.data.content} />
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}
