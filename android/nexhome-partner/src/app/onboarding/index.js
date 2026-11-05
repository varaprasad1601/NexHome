import Feather from '@expo/vector-icons/Feather';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Button, Card, ErrorBox, Loading, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { STEPS, readableSubmitError, stepsDone } from '../../utils/onboarding';
import { EDITABLE, onboardingStatus } from '../../utils/status';

/**
 * The onboarding overview: the steps with a tick when complete and the share done, the staff's reason when changes
 * were requested, and Submit for review (PUT /api/partners/profiles/submit) once every step is complete. Each step
 * saves on its own, so the partner can stop and come back.
 */
export default function Onboarding() {
  const { authRequest, profile, reloadProfile, signOut } = useAuth();
  const id = profile?.partnerProfileId;
  const services = useApi(id ? `/api/partners/profiles/services/${id}` : null);
  const documents = useApi(id ? `/api/partners/profiles/documents/${id}` : null);
  const banks = useApi(id ? `/api/partners/profiles/bank-accounts/${id}` : null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  useFocusEffect(
    useCallback(() => {
      reloadProfile().catch(() => {});
    }, [reloadProfile])
  );

  if (!profile) return <Loading />;
  if (!EDITABLE.includes(profile.onboardingStatus)) {
    return (
      <SafeAreaView style={{ flex: 1, padding: 24, justifyContent: 'center', backgroundColor: colors.ground }}>
        <Button title="Continue" onPress={() => router.replace('/')} />
      </SafeAreaView>
    );
  }

  const done = stepsDone({ profile, services: services.data, documents: documents.data, banks: banks.data });
  const count = STEPS.filter((s) => done[s.key]).length;
  const percent = Math.round((count / STEPS.length) * 100);
  const nextStep = STEPS.find((s) => !done[s.key]);
  const status = onboardingStatus(profile.onboardingStatus);

  const refresh = async () => {
    setRefreshing(true);
    await Promise.all([reloadProfile().catch(() => {}), services.reload(), documents.reload(), banks.reload()]);
    setRefreshing(false);
  };

  const submit = async () => {
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/partners/profiles/submit', { method: 'PUT', body: { partnerProfileId: profile.partnerProfileId } });
      await reloadProfile();
      router.replace('/');
    } catch (err) {
      setError(readableSubmitError(err.message));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 16 }} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
          <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
            Become a NexHome partner
          </Text>
        </View>
        <Text style={{ color: colors.muted, marginTop: -8 }}>
          Partner ID <Text weight="bold">{profile.publicId}</Text>
        </Text>

        {profile.onboardingStatus === 'CHANGES_REQUESTED' ? (
          <Banner tone="warning" title="NexHome asked for changes" text={profile.rejectionReason || 'Please review your details and submit again.'} />
        ) : null}

        <Card style={{ gap: 12, borderRadius: 18 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text weight="extrabold" style={{ fontSize: 16 }}>
              {percent}% complete
            </Text>
            <Tag label={status.label} fg={status.fg} bg={status.bg} />
          </View>
          <View accessibilityLabel={`${count} of ${STEPS.length} steps complete`} style={{ height: 8, borderRadius: 4, backgroundColor: colors.line, overflow: 'hidden' }}>
            <View style={{ width: `${percent}%`, height: 8, borderRadius: 4, backgroundColor: percent === 100 ? colors.green : colors.blue }} />
          </View>
          <Text style={{ color: colors.muted, fontSize: 13 }}>
            {nextStep ? `${count} of ${STEPS.length} steps done. Your progress is saved after every step.` : 'All done. Submit your profile for review.'}
          </Text>
          {nextStep ? <Button title={count ? `Continue: ${nextStep.title}` : 'Start'} onPress={() => router.push(`/onboarding/${nextStep.key}`)} /> : null}
        </Card>

        <Card style={{ paddingVertical: 4 }}>
          {STEPS.map((step, i) => (
            <Pressable
              key={step.key}
              accessibilityRole="button"
              accessibilityLabel={`${step.title}, ${done[step.key] ? 'complete' : 'to do'}`}
              onPress={() => router.push(`/onboarding/${step.key}`)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 13, borderTopWidth: i ? 1 : 0, borderTopColor: colors.line }}
            >
              <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: done[step.key] ? colors.greenSoft : colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name={done[step.key] ? 'check' : step.icon} size={18} color={done[step.key] ? colors.green : colors.blue} />
              </View>
              <View style={{ flex: 1 }}>
                <Text weight="bold">{step.title}</Text>
                <Text style={{ fontSize: 12, color: done[step.key] ? colors.greenDark : colors.muted }}>{done[step.key] ? 'Complete' : 'To do'}</Text>
              </View>
              <Feather name="chevron-right" size={18} color={colors.muted} />
            </Pressable>
          ))}
        </Card>

        <ErrorBox message={error || services.error || documents.error || banks.error} />
        <Button title="Submit for review" icon="send" onPress={submit} loading={busy} disabled={count < STEPS.length} />
        <Text style={{ color: colors.muted, fontSize: 12.5, textAlign: 'center' }}>
          Once submitted, NexHome checks your details and documents. You can change them again only if we ask for changes.
        </Text>
        <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 24, paddingBottom: 12 }}>
          <Text weight="bold" style={{ color: colors.blue }} onPress={() => router.push('/support')} accessibilityRole="button">
            Get help
          </Text>
          <Text
            weight="bold"
            style={{ color: colors.muted }}
            onPress={async () => {
              await signOut();
              router.replace('/login');
            }}
            accessibilityRole="button"
          >
            Log out
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
