import { Redirect } from 'expo-router';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandSplash, Button, Text } from '../components/ui';
import { isPartner, useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { EDITABLE } from '../utils/status';

/**
 * Where the app opens, by the partner profile's onboarding status: log in; onboarding while NOT_STARTED, DRAFT or
 * CHANGES_REQUESTED; the waiting screen while UNDER_REVIEW, UPDATE_REQUESTED or REJECTED; the tabs once APPROVED.
 */
export default function Gate() {
  const { ready, signedIn, user, profile, signOut, reloadProfile } = useAuth();
  if (!ready) return <BrandSplash />;
  if (!signedIn) return <Redirect href="/login" />;
  if (!isPartner(user)) {
    return (
      <SafeAreaView style={{ flex: 1, padding: 24, justifyContent: 'center', gap: 16, backgroundColor: colors.ground }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          This number is not a partner account
        </Text>
        <Text style={{ color: colors.muted, lineHeight: 21 }}>
          It is registered for the NexHome customer app or the staff console. Please use that app, or contact NexHome support to add a
          partner account to this number.
        </Text>
        <View>
          <Button title="Use another number" onPress={signOut} />
        </View>
      </SafeAreaView>
    );
  }
  if (!profile) {
    return (
      <SafeAreaView style={{ flex: 1, padding: 24, justifyContent: 'center', gap: 16, backgroundColor: colors.ground }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          We could not load your partner profile
        </Text>
        <Text style={{ color: colors.muted, lineHeight: 21 }}>Check your connection and try again, or contact NexHome support.</Text>
        <Button title="Try again" onPress={() => reloadProfile().catch(() => {})} />
        <Button title="Log out" variant="secondary" onPress={signOut} />
      </SafeAreaView>
    );
  }
  if (EDITABLE.includes(profile.onboardingStatus)) return <Redirect href="/onboarding" />;
  if (profile.onboardingStatus === 'APPROVED') return <Redirect href="/(tabs)" />;
  return <Redirect href="/status" />;
}
