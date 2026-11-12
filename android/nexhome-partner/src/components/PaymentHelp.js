import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';

import { colors } from '../theme';
import { Text } from './ui';

/**
 * The booking id and a way to get help, on the payment screens (online Payment processing and cash Collect payment):
 * Help opens Help & support (call / WhatsApp NexHome), from where Back returns to the payment screen.
 */
export default function PaymentHelp({ publicId }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, borderRadius: 14, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}>
      <Feather name="hash" size={16} color={colors.muted} />
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 12, color: colors.muted }}>Booking ID</Text>
        <Text weight="bold" style={{ fontSize: 14, color: colors.navy }}>
          {publicId ?? '—'}
        </Text>
      </View>
      <Pressable
        accessibilityRole="button"
        onPress={() => router.push('/support')}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 8, paddingHorizontal: 12, borderRadius: 10, backgroundColor: colors.blueTint }}
      >
        <Feather name="headphones" size={15} color={colors.blue} />
        <Text weight="bold" style={{ fontSize: 13, color: colors.blue }}>
          Help
        </Text>
      </Pressable>
    </View>
  );
}
