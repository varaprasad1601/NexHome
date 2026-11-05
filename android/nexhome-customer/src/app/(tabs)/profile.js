import Feather from '@expo/vector-icons/Feather';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import { Alert, Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { fileUrl } from '../../api/client';
import { Card, Cover, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { colors } from '../../theme';

function Row({ icon, label, onPress, danger }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
      <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: danger ? colors.redSoft : colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
        <Feather name={icon} size={18} color={danger ? colors.red : colors.blue} />
      </View>
      <Text weight="bold" style={{ flex: 1, color: danger ? colors.red : colors.ink }}>
        {label}
      </Text>
      {danger ? null : <Feather name="chevron-right" size={18} color={colors.muted} />}
    </Pressable>
  );
}

/** The customer: photo or initials, name, number and email; edit profile, addresses, policies, log out. */
export default function Profile() {
  const { user, profile, signOut } = useAuth();
  const name = [profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || 'Your profile';
  const initials = name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 16 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          Profile
        </Text>
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, borderRadius: 18 }}>
          {user?.profilePhotoUrl ? (
            <Cover uri={fileUrl(user.profilePhotoUrl)} style={{ width: 60, height: 60, borderRadius: 30 }} />
          ) : (
            <View style={{ width: 60, height: 60, borderRadius: 30, backgroundColor: colors.blue, alignItems: 'center', justifyContent: 'center' }}>
              <Text weight="extrabold" style={{ color: colors.white, fontSize: 20 }}>
                {initials}
              </Text>
            </View>
          )}
          <View style={{ flex: 1, gap: 2 }}>
            <Text weight="extrabold" style={{ fontSize: 17 }}>
              {name}
            </Text>
            <Text style={{ color: colors.muted, fontSize: 13 }}>
              {user?.countryCode} {user?.mobileNumber}
            </Text>
            {user?.email ? <Text style={{ color: colors.muted, fontSize: 13 }}>{user.email}</Text> : null}
          </View>
        </Card>

        <Card style={{ paddingVertical: 4 }}>
          <Row icon="edit-3" label="Edit profile" onPress={() => router.push('/edit-profile')} />
          <Row icon="map-pin" label="My addresses" onPress={() => router.push('/addresses')} />
          <Row icon="info" label="About us" onPress={() => router.push('/about')} />
          <Row icon="file-text" label="Terms & Conditions" onPress={() => router.push('/legal?view=terms')} />
          <Row icon="shield" label="Privacy Policy" onPress={() => router.push('/legal?view=policies')} />
        </Card>
        <Card style={{ paddingVertical: 4 }}>
          <Row
            icon="log-out"
            label="Log out"
            danger
            // Asks first, so a stray tap never signs out
            onPress={() =>
              Alert.alert('Log out?', 'You will need your mobile number and a code to log in again.', [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Log out',
                  style: 'destructive',
                  onPress: async () => {
                    await signOut();
                    router.replace('/login');
                  }
                }
              ])
            }
          />
        </Card>
        <Text style={{ color: colors.muted, fontSize: 12, textAlign: 'center' }}>NexHome · version {Constants.expoConfig?.version ?? '1.0.0'}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}
