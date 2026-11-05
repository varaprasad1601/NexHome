import Feather from '@expo/vector-icons/Feather';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';

import { fileUrl } from '../api/client';
import useApi from '../hooks/useApi';
import { colors } from '../theme';
import { money, serviceNameOf } from '../utils/format';
import { Cover, Text } from './ui';

/**
 * The cart bar while the cart has services (design 2 of home-v2-styles.html, tightened): up to two overlapping
 * service pictures, "First service… + N more" on one line, the estimate, and a round white arrow to the cart.
 * `covers` maps a service id to its cover image address (the home page passes the popular services').
 */
export default function CartBar({ covers = {} }) {
  const cart = useApi('/api/cart/me');
  const items = cart.data?.items ?? [];
  if (!items.length) return null;
  const first = serviceNameOf(items[0].serviceName);
  const more = items.length - 1;
  return (
    <View style={{ position: 'absolute', left: 16, right: 16, bottom: 12 }}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`View cart: ${items.length} ${items.length === 1 ? 'service' : 'services'}, ${money(cart.data.estimatedTotal)}`}
        onPress={() => router.push('/cart')}
        style={{ borderRadius: 20, shadowColor: colors.blue, shadowOpacity: 0.3, shadowRadius: 14, shadowOffset: { width: 0, height: 8 }, elevation: 8 }}
      >
        <LinearGradient
          colors={['#1a73d9', colors.blue]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={{ borderRadius: 20, paddingVertical: 9, paddingLeft: 10, paddingRight: 9, flexDirection: 'row', alignItems: 'center', gap: 8 }}
        >
          <View style={{ flexDirection: 'row' }}>
            {items.slice(0, 2).map((item, index) => (
              <View
                key={item.cartItemId}
                style={{ width: 36, height: 36, borderRadius: 18, borderWidth: 2, borderColor: colors.white, overflow: 'hidden', marginLeft: index ? -22 : 0 }}
              >
                <Cover uri={fileUrl(covers[item.serviceId])} style={{ width: '100%', height: '100%' }} />
              </View>
            ))}
          </View>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', gap: 4 }}>
              <Text style={{ color: colors.white, fontSize: 11, opacity: 0.9, flexShrink: 1 }} numberOfLines={1}>
                {first}
              </Text>
              {more ? <Text style={{ color: colors.white, fontSize: 11, opacity: 0.9 }}>+ {more} more</Text> : null}
            </View>
            <Text weight="extrabold" style={{ color: colors.white, fontSize: 15.5 }}>
              {money(cart.data.estimatedTotal)}
            </Text>
          </View>
          <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name="arrow-right" size={18} color={colors.blue} />
          </View>
        </LinearGradient>
      </Pressable>
    </View>
  );
}
