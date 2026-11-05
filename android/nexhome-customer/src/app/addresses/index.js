import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { FlatList, Pressable, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Empty, ErrorBox, Header, Loading, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/**
 * The customer's addresses (GET /api/addresses/me, default first). Tapping one makes it the default (the address
 * Home and checkout use) and, when opened from checkout (`pick`), goes back there.
 */
export default function Addresses() {
  const { pick } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const addresses = useApi('/api/addresses/me');

  const choose = async (address) => {
    if (!address.isDefault) {
      await authRequest('/api/addresses/update', { method: 'PUT', body: { addressId: address.addressId, isDefault: true } }).catch(() => {});
    }
    if (pick) router.back();
    else addresses.reload();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={pick ? 'Choose an address' : 'My addresses'} />
      {addresses.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={addresses.data ?? []}
          keyExtractor={(a) => String(a.addressId)}
          contentContainerStyle={{ padding: 16, gap: 12 }}
          ListHeaderComponent={<ErrorBox message={addresses.error} />}
          ListEmptyComponent={<Empty icon="map-pin" title="No addresses yet" text="Add the place where you need services." />}
          renderItem={({ item }) => (
            <Card onPress={() => choose(item)} style={{ flexDirection: 'row', gap: 12, borderWidth: item.isDefault ? 2 : 1, borderColor: item.isDefault ? colors.blue : colors.line }}>
              <Feather name={item.addressType === 'WORK' ? 'briefcase' : 'home'} size={20} color={colors.green} style={{ marginTop: 2 }} />
              <View style={{ flex: 1, gap: 3 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <Text weight="bold">{item.label || item.addressType}</Text>
                  {item.isDefault ? <Tag label="Default" fg={colors.blue} bg={colors.blueTint} /> : null}
                </View>
                <Text style={{ fontSize: 13, color: colors.muted, lineHeight: 19 }}>
                  {[item.addressLine1, item.addressLine2, item.landmark, item.city, item.state, item.postalCode].filter(Boolean).join(', ')}
                </Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>
                  {item.contactName} · {item.mobileNumber}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Edit ${item.label || 'address'}`}
                hitSlop={10}
                onPress={() => router.push({ pathname: '/addresses/form', params: { addressId: item.addressId } })}
              >
                <Feather name="edit-2" size={18} color={colors.blue} />
              </Pressable>
            </Card>
          )}
        />
      )}
      <View style={{ padding: 16, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line }}>
        <Button title="Add a new address" icon="plus" onPress={() => router.push('/addresses/form')} />
      </View>
    </SafeAreaView>
  );
}
