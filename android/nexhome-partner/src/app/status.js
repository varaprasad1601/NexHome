import Feather from '@expo/vector-icons/Feather';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Button, Card, Loading, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';
import { EDITABLE } from '../utils/status';

const SCREENS = {
  UNDER_REVIEW: {
    icon: 'clock',
    tone: colors.blue,
    bg: colors.blueTint,
    title: 'We are verifying your profile',
    text: 'NexHome is checking your details, documents and bank account. This usually takes 1 to 2 working days. We will let you know here as soon as it is done.'
  },
  UPDATE_REQUESTED: {
    icon: 'refresh-cw',
    tone: colors.blue,
    bg: colors.blueTint,
    title: 'We are verifying your changes',
    text: 'You changed a document or bank account, so NexHome is checking it again. You cannot go online or take new jobs until it is approved; your jobs and earnings stay open.'
  },
  REJECTED: {
    icon: 'x-circle',
    tone: colors.red,
    bg: colors.redSoft,
    title: 'Your application was not approved',
    text: 'NexHome could not approve your partner profile. If you think this is a mistake or your details have changed, contact support.'
  }
};

/**
 * Waiting screens: UNDER_REVIEW and UPDATE_REQUESTED ("we are verifying ..."), REJECTED (the reason, contact
 * support). Pull to refresh reloads the profile; when the status moves on the gate opens the right screen.
 */
export default function Status() {
  const { profile, reloadProfile, signOut } = useAuth();
  const [refreshing, setRefreshing] = useState(false);
  if (!profile) return <Loading />;
  if (profile.onboardingStatus === 'APPROVED' || EDITABLE.includes(profile.onboardingStatus)) return <Redirect href="/" />;
  const screen = SCREENS[profile.onboardingStatus] ?? SCREENS.UNDER_REVIEW;

  const refresh = async () => {
    setRefreshing(true);
    await reloadProfile().catch(() => {});
    setRefreshing(false);
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView contentContainerStyle={{ padding: 24, gap: 18, flexGrow: 1, justifyContent: 'center' }} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <View style={{ alignItems: 'center', gap: 14 }}>
          <View style={{ width: 84, height: 84, borderRadius: 42, backgroundColor: screen.bg, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name={screen.icon} size={38} color={screen.tone} />
          </View>
          <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy, textAlign: 'center' }}>
            {screen.title}
          </Text>
          <Text style={{ color: colors.muted, textAlign: 'center', lineHeight: 21 }}>{screen.text}</Text>
        </View>
        {profile.onboardingStatus === 'REJECTED' && profile.rejectionReason ? <Banner tone="danger" title="Reason" text={profile.rejectionReason} /> : null}
        <Card style={{ gap: 4, alignItems: 'center' }}>
          <Text style={{ color: colors.muted, fontSize: 12 }}>Partner ID</Text>
          <Text weight="extrabold" style={{ fontSize: 16, color: colors.navy }}>
            {profile.publicId}
          </Text>
        </Card>
        <Button title="Check again" variant="secondary" icon="refresh-cw" onPress={refresh} loading={refreshing} />
        {profile.onboardingStatus === 'UPDATE_REQUESTED' ? <Button title="Open my jobs and earnings" onPress={() => router.replace('/(tabs)')} /> : null}
        <Button title="Contact support" variant={profile.onboardingStatus === 'REJECTED' ? 'primary' : 'secondary'} icon="life-buoy" onPress={() => router.push('/support')} />
        <Text
          weight="bold"
          style={{ color: colors.muted, textAlign: 'center', padding: 10 }}
          accessibilityRole="button"
          onPress={async () => {
            await signOut();
            if (router.canDismiss()) router.dismissAll();
            router.replace('/login');
          }}
        >
          Log out
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}
