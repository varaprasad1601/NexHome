import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import RichText from '../components/RichText';
import { Button, Card, ErrorBox, Loading, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';

/**
 * The current policy versions the customer has not accepted yet (GET /api/policies/pending), one at a time; each
 * is accepted with POST /api/policies/accept (APP_CHECKBOX). Shown after sign-in and whenever a new version is out.
 */
export default function Policies() {
  const { authRequest } = useAuth();
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    authRequest('/api/policies/pending')
      .then(setPending)
      .catch((err) => {
        setPending([]);
        setError(err.message);
      });
  }, [authRequest]);

  // All accepted (or none waiting): back to where the customer was
  useEffect(() => {
    if (pending && pending.length === 0) {
      if (router.canGoBack()) router.back();
      else router.replace('/(tabs)');
    }
  }, [pending]);

  const policy = pending?.[0];
  if (!policy) return <Loading />;

  const accept = async () => {
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/policies/accept', {
        method: 'POST',
        body: { policyDocumentId: policy.policyDocumentId, acceptanceMethod: 'APP_CHECKBOX' }
      });
      setPending((current) => current.slice(1));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ padding: 20, gap: 6 }}>
        <Text weight="bold" style={{ color: colors.green, fontSize: 12, letterSpacing: 0.5 }}>
          PLEASE REVIEW{pending.length > 1 ? ` · 1 OF ${pending.length}` : ''}
        </Text>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          {policy.title}
        </Text>
        <Text style={{ color: colors.muted, fontSize: 12 }}>Version {policy.version}</Text>
      </View>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 20 }}>
        <Card>
          <RichText html={policy.content} fontSize={14.5} />
        </Card>
      </ScrollView>
      <View style={{ padding: 20, gap: 12, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line }}>
        <ErrorBox message={error} />
        <Button title="I agree" onPress={accept} loading={busy} />
      </View>
    </SafeAreaView>
  );
}
