import { Linking, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Header, Text } from '../components/ui';
import useApi from '../hooks/useApi';
import { colors } from '../theme';

/**
 * Help & support: quick contact (call, WhatsApp; GET /api/settings/public). Reached from Home, Profile, onboarding
 * and the waiting screens. The partner's tickets (My tickets, New ticket) are commented out for now, as in the
 * customer app; the full screen is kept below.
 */
export default function Support() {
  const contact = useApi('/api/settings/public');
  const phone = contact.data?.SUPPORT_PHONE;
  const whatsapp = contact.data?.WHATSAPP_NUMBER;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Help & support" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
        <Card style={{ gap: 12, borderRadius: 18 }}>
          <Text weight="bold">Need help quickly?</Text>
          <Text style={{ color: colors.muted, fontSize: 13 }}>{contact.data?.WORKING_HOURS ?? 'Mon – Sun, 9:00 AM to 5:00 PM'}</Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button title="Call" variant="secondary" icon="phone" disabled={!phone} onPress={() => Linking.openURL(`tel:${phone.replace(/[^\d+]/g, '')}`)} style={{ flex: 1 }} />
            <Button title="WhatsApp" icon="message-circle" disabled={!whatsapp} onPress={() => Linking.openURL(`https://wa.me/${whatsapp}`)} style={{ flex: 1 }} />
          </View>
        </Card>
      </ScrollView>
    </SafeAreaView>
  );
}

/*
 * The screen with the partner's tickets (GET /api/support/tickets/me; New ticket), kept for later. To bring it back,
 * restore these imports and replace Support above with it:
 *
 * import { router } from 'expo-router';
 * import { FlatList, Linking, RefreshControl, View } from 'react-native';
 * import { Button, Card, Empty, ErrorBox, Header, Loading, Tag, Text } from '../components/ui';
 * import { dayLabel } from '../utils/format';
 * import { ticketStatus } from '../utils/status';
 *
 * export default function Support() {
 *   const tickets = useApi('/api/support/tickets/me?size=50');
 *   const contact = useApi('/api/settings/public');
 *   const phone = contact.data?.SUPPORT_PHONE;
 *   const whatsapp = contact.data?.WHATSAPP_NUMBER;
 *
 *   return (
 *     <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
 *       <Header title="Help & support" />
 *       <FlatList
 *         data={tickets.data?.content ?? []}
 *         keyExtractor={(t) => String(t.supportTicketId)}
 *         contentContainerStyle={{ padding: 16, gap: 12 }}
 *         refreshControl={<RefreshControl refreshing={tickets.refreshing} onRefresh={tickets.refresh} />}
 *         ListHeaderComponent={
 *           <View style={{ gap: 16, marginBottom: 4 }}>
 *             <Card style={{ gap: 12, borderRadius: 18 }}>
 *               <Text weight="bold">Need help quickly?</Text>
 *               <Text style={{ color: colors.muted, fontSize: 13 }}>{contact.data?.WORKING_HOURS ?? 'Mon – Sun, 9:00 AM to 5:00 PM'}</Text>
 *               <View style={{ flexDirection: 'row', gap: 8 }}>
 *                 <Button title="Call" variant="secondary" icon="phone" disabled={!phone} onPress={() => Linking.openURL(`tel:${phone.replace(/[^\d+]/g, '')}`)} style={{ flex: 1 }} />
 *                 <Button title="WhatsApp" icon="message-circle" disabled={!whatsapp} onPress={() => Linking.openURL(`https://wa.me/${whatsapp}`)} style={{ flex: 1 }} />
 *               </View>
 *             </Card>
 *             <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
 *               <Text weight="extrabold" style={{ fontSize: 16, color: colors.navy }}>
 *                 My tickets
 *               </Text>
 *               <Text weight="bold" style={{ color: colors.blue, padding: 10 }} onPress={() => router.push('/ticket/new')} accessibilityRole="button">
 *                 + New ticket
 *               </Text>
 *             </View>
 *             <ErrorBox message={tickets.error} />
 *           </View>
 *         }
 *         ListEmptyComponent={tickets.loading ? <Loading /> : <Empty icon="life-buoy" title="No tickets yet" text="Raise a ticket for anything about a job, payout or your profile." />}
 *         renderItem={({ item }) => {
 *           const status = ticketStatus(item.status);
 *           return (
 *             <Card onPress={() => router.push(`/ticket/${item.supportTicketId}`)} style={{ gap: 6 }}>
 *               <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
 *                 <Text weight="bold" style={{ flex: 1 }} numberOfLines={1}>
 *                   {item.subject}
 *                 </Text>
 *                 <Tag label={status.label} fg={status.fg} bg={status.bg} />
 *               </View>
 *               <Text style={{ fontSize: 12, color: colors.muted }}>{[item.publicId, item.bookingPublicId, dayLabel(item.creationDate)].filter(Boolean).join(' · ')}</Text>
 *             </Card>
 *           );
 *         }}
 *       />
 *     </SafeAreaView>
 *   );
 * }
 */
