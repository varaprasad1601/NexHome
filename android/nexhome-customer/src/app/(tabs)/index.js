import Feather from '@expo/vector-icons/Feather';
import { router, useFocusEffect } from 'expo-router';
import { setStatusBarStyle } from 'expo-status-bar';
import { useCallback, useMemo } from 'react';
import { Image, Linking, Pressable, RefreshControl, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';

import { fileUrl } from '../../api/client';
import CartBar from '../../components/CartBar';
import { Cover, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { durationLabel, money, rangeLabel, serviceNameOf } from '../../utils/format';
import { UPCOMING, bookingStatus, compareUpcoming } from '../../utils/status';

/** The header's blues (the "Ocean curve" of Admin Sreens/customer-app-samples/home-v2-styles.html). */
const HEADER = ['#1a73d9', colors.blue];

/**
 * Home v2 with bubbles: a fixed blue header (greeting, address, bell; rounded bottom) with two soft bubbles and the
 * search floating on its edge, the next booking's status, the banner with the logo, Services, Popular near you (with ratings), Need help
 * choosing?, and the cart bar.
 */
export default function Home() {
  const insets = useSafeAreaInsets();
  const { profile } = useAuth();
  const categories = useApi('/api/catalog/categories');
  const services = useApi('/api/catalog/services?size=8');
  const addresses = useApi('/api/addresses/me');
  const summary = useApi('/api/notifications/me/summary?app=CUSTOMER');
  const bookings = useApi('/api/bookings/me?size=20');
  const contact = useApi('/api/settings/public');

  // White status bar text over the blue header while Home is shown
  useFocusEffect(
    useCallback(() => {
      setStatusBarStyle('light');
      return () => setStatusBarStyle('dark');
    }, [])
  );

  const address = (addresses.data ?? []).find((a) => a.isDefault) ?? addresses.data?.[0];
  const unread = summary.data?.unread ?? 0;
  const popular = useMemo(() => services.data?.content ?? [], [services.data]);
  const covers = useMemo(() => Object.fromEntries(popular.map((s) => [s.serviceId, s.coverImageUrl])), [popular]);
  const next = useMemo(() => nextBooking(bookings.data?.content ?? []), [bookings.data]);
  const phone = contact.data?.SUPPORT_PHONE;
  const refresh = () =>
    Promise.all([categories.refresh(), services.refresh(), addresses.refresh(), summary.refresh(), bookings.refresh(), contact.refresh()]);

  return (
    <View style={{ flex: 1, backgroundColor: colors.ground }}>
      {/* Fixed at the top: the header and the search on its edge */}
      <View style={{ zIndex: 2, backgroundColor: colors.ground, paddingBottom: 4 }}>
        {/* Header with bubbles */}
        <LinearGradient
          colors={HEADER}
          start={{ x: 0, y: 0 }}
          end={{ x: 0.8, y: 1 }}
          style={{ paddingTop: insets.top + 10, paddingHorizontal: 20, paddingBottom: 46, overflow: 'hidden', borderBottomLeftRadius: 28, borderBottomRightRadius: 28 }}
        >
          <View style={{ position: 'absolute', width: 190, height: 190, borderRadius: 95, right: -50, top: -60, backgroundColor: 'rgba(255,255,255,0.10)' }} />
          <View style={{ position: 'absolute', width: 110, height: 110, borderRadius: 55, left: -36, bottom: -48, backgroundColor: 'rgba(255,255,255,0.10)' }} />
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Pressable accessibilityRole="button" onPress={() => router.push('/addresses')} style={{ gap: 3, flex: 1 }}>
              <Text weight="semibold" style={{ fontSize: 12.5, color: colors.white, opacity: 0.85 }}>
                Hello{profile?.firstName ? `, ${profile.firstName}` : ''}
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
                <Feather name="map-pin" size={15} color={colors.white} />
                <Text weight="bold" style={{ fontSize: 15.5, color: colors.white, flexShrink: 1 }} numberOfLines={1}>
                  {address ? `${address.label || 'Home'} · ${address.city}` : 'Add your address'}
                </Text>
                <Feather name="chevron-down" size={15} color={colors.white} />
              </View>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={unread ? `Notifications, ${unread} unread` : 'Notifications'}
              onPress={() => router.push('/notifications')}
              style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.16)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)', alignItems: 'center', justifyContent: 'center' }}
            >
              <Feather name="bell" size={20} color={colors.white} />
              {unread ? (
                <View style={{ position: 'absolute', top: 7, right: 7, minWidth: 16, height: 16, borderRadius: 8, backgroundColor: '#e53e3e', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 3, borderWidth: 1.5, borderColor: colors.blue }}>
                  <Text weight="bold" style={{ color: colors.white, fontSize: 9 }}>
                    {unread > 9 ? '9+' : unread}
                  </Text>
                </View>
              ) : null}
            </Pressable>
          </View>
        </LinearGradient>

        {/* Search on the header's edge */}
        <Pressable
          accessibilityRole="search"
          onPress={() => router.push('/search')}
          style={{
            marginTop: -26,
            marginHorizontal: 20,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            height: 50,
            paddingHorizontal: 16,
            backgroundColor: colors.white,
            borderRadius: 14,
            borderWidth: 1,
            borderColor: colors.line,
            shadowColor: colors.navy,
            shadowOpacity: 0.12,
            shadowRadius: 14,
            shadowOffset: { width: 0, height: 8 },
            elevation: 6
          }}
        >
          <Feather name="search" size={18} color={colors.muted} />
          <Text style={{ color: '#8a9aad' }}>Search for a service</Text>
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={{ paddingBottom: 110 }}
        refreshControl={<RefreshControl refreshing={categories.refreshing} onRefresh={refresh} />}
      >

        <View style={{ padding: 20, paddingTop: 12, gap: 18 }}>
          {/* The next booking, while there is one */}
          {next ? (
            <Pressable
              accessibilityRole="button"
              onPress={() => router.push(`/booking/${next.bookingId}`)}
              style={{ flexDirection: 'row', alignItems: 'stretch', gap: 12, padding: 14, borderRadius: 16, backgroundColor: colors.greenSoft, borderWidth: 1, borderColor: '#cdebdc' }}
            >
              {/* The icon is as tall as the three lines: they start at its top and end at its bottom */}
              <View style={{ width: 48, borderRadius: 13, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name="calendar" size={20} color={colors.green} />
              </View>
              <View style={{ flex: 1, gap: 3, justifyContent: 'space-between' }}>
                <Text weight="bold" style={{ fontSize: 13.5, lineHeight: 18 }} numberOfLines={1}>
                  {serviceNameOf(next.serviceName)}
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
                  <Feather name={next.scheduledStartAt ? 'clock' : 'zap'} size={12} color={colors.greenDark} />
                  <Text weight="semibold" style={{ flex: 1, fontSize: 12, lineHeight: 16, color: colors.greenDark }} numberOfLines={1}>
                    {next.scheduledStartAt ? rangeLabel(next.scheduledStartAt, next.scheduledEndAt) : 'As soon as possible'}
                  </Text>
                </View>
                <Text style={{ fontSize: 12, lineHeight: 16, color: colors.muted }} numberOfLines={1}>
                  {bookingStatus(next.bookingStatus).label}
                  {next.partnerName ? ` · ${next.partnerName}` : ''}
                </Text>
              </View>
              <View style={{ justifyContent: 'center' }}>
                <Feather name="chevron-right" size={18} color={colors.green} />
              </View>
            </Pressable>
          ) : null}

          {/* Banner with the logo */}
          <LinearGradient
            colors={[colors.blue, '#1660b8']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{ borderRadius: 20, padding: 18, flexDirection: 'row', alignItems: 'center', gap: 12 }}
          >
            <View style={{ flex: 1, gap: 4 }}>
              <Text weight="extrabold" style={{ fontSize: 16, color: colors.white, lineHeight: 21 }}>
                Trusted help at home, today
              </Text>
              <Text style={{ fontSize: 12, color: colors.white, opacity: 0.85 }}>Verified professionals in Nuzvid</Text>
              <Pressable
                accessibilityRole="button"
                onPress={() => router.push('/search')}
                style={{ alignSelf: 'flex-start', marginTop: 8, backgroundColor: colors.white, paddingHorizontal: 15, paddingVertical: 8, borderRadius: 999 }}
              >
                <Text weight="extrabold" style={{ color: colors.blue, fontSize: 12.5 }}>
                  Book a service
                </Text>
              </Pressable>
            </View>
            <View style={{ width: 80, height: 80, borderRadius: 18, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
              <Image source={require('../../../assets/logo.png')} style={{ width: 56, height: 39 }} resizeMode="contain" accessibilityLabel="NexHome logo" />
            </View>
          </LinearGradient>

          {/* Services */}
          <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
            Services
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: -6, rowGap: 14 }}>
            {(categories.data ?? []).map((category) => (
              <Pressable
                key={category.serviceCategoryId}
                accessibilityRole="button"
                onPress={() => router.push({ pathname: '/search', params: { categoryId: category.serviceCategoryId, title: category.name } })}
                style={{ width: '25%', alignItems: 'center', gap: 6 }}
              >
                <View style={{ width: 62, height: 62, borderRadius: 18, backgroundColor: colors.white, borderWidth: 0.5, borderColor: '#f3f6fa', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                  <Cover uri={fileUrl(category.iconUrl)} style={{ width: 55, height: 55, borderRadius: 15 }} />
                </View>
                <Text weight="semibold" style={{ fontSize: 11.5, textAlign: 'center' }} numberOfLines={2}>
                  {category.name}
                </Text>
              </Pressable>
            ))}
            {categories.error ? <Text style={{ color: colors.red }}>{categories.error}</Text> : null}
          </View>

          {/* Popular near you */}
          <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' }}>
            <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
              Popular near you
            </Text>
            <Pressable accessibilityRole="button" onPress={() => router.push('/search')}>
              <Text weight="bold" style={{ color: colors.blue, fontSize: 13 }}>
                See all
              </Text>
            </Pressable>
          </View>
          <View style={{ gap: 10, marginTop: -6 }}>
            {popular.map((service) => (
              <Pressable
                key={service.serviceId}
                accessibilityRole="button"
                onPress={() => router.push(`/service/${service.serviceId}`)}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 10, borderRadius: 16, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}
              >
                <Cover uri={fileUrl(service.coverImageUrl)} style={{ width: 64, height: 64, borderRadius: 14 }} />
                <View style={{ flex: 1, gap: 2 }}>
                  <Text weight="bold" style={{ fontSize: 14 }} numberOfLines={1}>
                    {service.name}
                  </Text>
                  <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={1}>
                    {service.averageRating ? (
                      <Text weight="bold" style={{ color: colors.star, fontSize: 12 }}>
                        ★{' '}
                      </Text>
                    ) : null}
                    {[
                      service.averageRating ? `${service.averageRating} (${service.reviewCount})` : 'New',
                      service.estimatedDurationMinutes ? durationLabel(service.estimatedDurationMinutes) : null
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </Text>
                  <Text weight="extrabold" style={{ fontSize: 13.5, color: colors.navy }}>
                    From {money(service.discountPrice ?? service.basePrice)}
                  </Text>
                </View>
                <View style={{ borderWidth: 1.5, borderColor: colors.blue, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 6 }}>
                  <Text weight="extrabold" style={{ color: colors.blue, fontSize: 12 }}>
                    Add
                  </Text>
                </View>
              </Pressable>
            ))}
          </View>

          {/* Need help choosing? */}
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/(tabs)/support')}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderRadius: 16, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}
          >
            <View style={{ flex: 1, gap: 2 }}>
              <Text weight="bold" style={{ fontSize: 14 }}>
                Need help choosing?
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }}>Call or WhatsApp us, {contact.data?.WORKING_HOURS ?? '8 AM – 8 PM'}.</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Call NexHome"
              disabled={!phone}
              onPress={() => Linking.openURL(`tel:${phone.replace(/[^\d+]/g, '')}`)}
              style={{ width: 42, height: 42, borderRadius: 12, borderWidth: 1.5, borderColor: colors.green, alignItems: 'center', justifyContent: 'center', opacity: phone ? 1 : 0.5 }}
            >
              <Feather name="phone" size={18} color={colors.green} />
            </Pressable>
          </Pressable>
        </View>
      </ScrollView>
      <CartBar covers={covers} />
    </View>
  );
}

/** The upcoming booking that comes first in the Bookings tab's order (compareUpcoming), or null. */
function nextBooking(list) {
  const upcoming = list.filter((b) => UPCOMING.includes(b.bookingStatus));
  if (!upcoming.length) return null;
  return [...upcoming].sort(compareUpcoming)[0];
}
