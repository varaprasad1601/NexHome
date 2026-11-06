import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { FlatList, Pressable, RefreshControl, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Empty, ErrorBox, Header, Loading, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import { colors } from '../theme';
import { whenLabel } from '../utils/format';

/** Where a notification leads, by what it is about. */
function linkOf(item) {
  if (item.referenceType === 'BOOKING') return `/booking/${item.referenceId}`;
  if (item.referenceType === 'SUPPORT_TICKET') return `/ticket/${item.referenceId}`;
  return null;
}

/** The customer's notifications (GET /api/notifications/me): unread bold with a dot; opening one marks it read. */
export default function Notifications() {
  const { authRequest } = useAuth();
  const list = useApi('/api/notifications/me?size=50&app=CUSTOMER', { pollMs: 15000 });
  const items = list.data?.content ?? [];
  const unread = items.some((n) => !n.isRead);

  const open = async (item) => {
    if (!item.isRead) {
      await authRequest('/api/notifications/me/read', { method: 'PUT', body: { notificationIds: [item.notificationId] } }).catch(() => {});
    }
    const link = linkOf(item);
    if (link) router.push(link);
    else list.reload();
  };

  const readAll = async () => {
    await authRequest('/api/notifications/me/read-all?app=CUSTOMER', { method: 'PUT' }).catch(() => {});
    list.reload();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header
        title="Notifications"
        right={
          unread ? (
            <Text weight="bold" style={{ color: colors.blue, paddingHorizontal: 12 }} onPress={readAll} accessibilityRole="button">
              Mark all read
            </Text>
          ) : null
        }
      />
      {list.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(n) => String(n.notificationId)}
          contentContainerStyle={{ padding: 16, gap: 10 }}
          refreshControl={<RefreshControl refreshing={list.refreshing} onRefresh={list.refresh} />}
          ListHeaderComponent={<ErrorBox message={list.error} />}
          ListEmptyComponent={<Empty icon="bell" title="No notifications yet" text="Booking updates and replies show here." />}
          renderItem={({ item }) => (
            <Pressable
              accessibilityRole="button"
              onPress={() => open(item)}
              style={{ flexDirection: 'row', gap: 12, backgroundColor: colors.white, borderRadius: 16, borderWidth: 1, borderColor: item.isRead ? colors.line : '#cfe2f6', padding: 14 }}
            >
              <View style={{ width: 38, height: 38, borderRadius: 19, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name={item.referenceType === 'SUPPORT_TICKET' ? 'message-circle' : item.referenceType === 'BOOKING' ? 'calendar' : 'bell'} size={17} color={colors.blue} />
              </View>
              <View style={{ flex: 1, gap: 2 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  {!item.isRead ? <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.blue }} /> : null}
                  <Text weight={item.isRead ? 'semibold' : 'extrabold'} style={{ flex: 1 }} numberOfLines={1}>
                    {item.title}
                  </Text>
                </View>
                <Text style={{ fontSize: 13, color: colors.muted }} numberOfLines={3}>
                  {item.message}
                </Text>
                <Text style={{ fontSize: 11.5, color: colors.muted }}>{whenLabel(item.creationDate)}</Text>
              </View>
            </Pressable>
          )}
        />
      )}
    </SafeAreaView>
  );
}
