import Feather from '@expo/vector-icons/Feather';
import * as DocumentPicker from 'expo-document-picker';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { fileUrl, uploadFile } from '../../api/client';
import { Avatar, Card, ErrorBox, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { onboardingStatus } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

function Row({ icon, label, onPress, danger, detail }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
      <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: danger ? colors.redSoft : colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
        <Feather name={icon} size={18} color={danger ? colors.red : colors.blue} />
      </View>
      <Text weight="bold" style={{ flex: 1, color: danger ? colors.red : colors.ink }}>
        {label}
      </Text>
      {detail ? <Text style={{ color: colors.muted, fontSize: 12.5 }}>{detail}</Text> : null}
      {danger ? null : <Feather name="chevron-right" size={18} color={colors.muted} />}
    </Pressable>
  );
}

/**
 * The partner: photo (tap to change: POST /api/files/upload PROFILE_PHOTO, then PUT /api/users/photo/update) or
 * initials, name, partner id, rating and onboarding status; services, working hours, time off, documents, bank
 * accounts, reviews, notifications, support, policies, log out.
 */
export default function Profile() {
  const { authRequest, user, profile, signOut, reloadProfile } = useAuth();
  const rating = useApi(profile ? `/api/reviews/summary?partnerProfileId=${profile.partnerProfileId}` : null);
  useTabReset(null, () => Promise.all([rating.refresh(), reloadProfile().catch(() => {})]));
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const name = [profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || 'Your profile';
  const status = onboardingStatus(profile?.onboardingStatus);

  const changePhoto = async () => {
    setError('');
    const result = await DocumentPicker.getDocumentAsync({ type: ['image/jpeg', 'image/png', 'image/webp'], copyToCacheDirectory: true });
    if (result.canceled || !result.assets?.length) return;
    const asset = result.assets[0];
    if (asset.size && asset.size > 2 * 1024 * 1024) {
      setError('Pick a photo of up to 2 MB.');
      return;
    }
    setUploading(true);
    try {
      const file = await uploadFile(authRequest, asset, 'PROFILE_PHOTO');
      await authRequest('/api/users/photo/update', { method: 'PUT', body: { userId: user.userId, fileId: file.fileId } });
      await reloadProfile();
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 16 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          Profile
        </Text>
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, borderRadius: 18 }}>
          <Pressable accessibilityRole="button" accessibilityLabel={user?.profilePhotoUrl ? 'Change photo' : 'Add a photo'} onPress={changePhoto} disabled={uploading}>
            <Avatar uri={fileUrl(user?.profilePhotoUrl)} name={name} size={64} />
            <View style={{ position: 'absolute', right: -2, bottom: -2, width: 26, height: 26, borderRadius: 13, backgroundColor: colors.green, borderWidth: 2, borderColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
              {uploading ? <ActivityIndicator size="small" color={colors.white} /> : <Feather name="camera" size={12} color={colors.white} />}
            </View>
          </Pressable>
          <View style={{ flex: 1, gap: 3 }}>
            <Text weight="extrabold" style={{ fontSize: 17 }}>
              {name}
            </Text>
            <Text style={{ color: colors.muted, fontSize: 13 }}>
              {profile?.publicId} · {user?.countryCode} {user?.mobileNumber}
            </Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Tag label={status.label} fg={status.fg} bg={status.bg} />
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
                <Feather name="star" size={13} color={colors.star} />
                <Text weight="bold" style={{ fontSize: 12.5 }}>
                  {rating.data?.averageRating ? `${Number(rating.data.averageRating).toFixed(1)} (${rating.data.total})` : 'No reviews yet'}
                </Text>
              </View>
            </View>
          </View>
        </Card>
        <ErrorBox message={error} />

        <Card style={{ paddingVertical: 4 }}>
          <Row icon="user" label="My details" onPress={() => router.push('/details')} />
          <Row icon="tool" label="My services" onPress={() => router.push('/services')} />
          <Row icon="file-text" label="Documents" onPress={() => router.push('/documents')} />
          <Row icon="credit-card" label="Bank accounts" onPress={() => router.push('/bank')} />
          <Row icon="calendar" label="Working hours & areas" onPress={() => router.push('/availability')} />
          <Row icon="sun" label="Time off" onPress={() => router.push('/time-off')} />
          <Row icon="star" label="Reviews" onPress={() => router.push('/reviews')} />
        </Card>
        <Card style={{ paddingVertical: 4 }}>
          <Row icon="info" label="About us" onPress={() => router.push('/about')} />
          <Row icon="life-buoy" label="Help & support" onPress={() => router.push('/support')} />
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
                    if (router.canDismiss()) router.dismissAll();
                    router.replace('/login');
                  }
                }
              ])
            }
          />
        </Card>
        <Text style={{ color: colors.muted, fontSize: 12, textAlign: 'center' }}>NexHome Partner · version {Constants.expoConfig?.version ?? '1.0.0'}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}
