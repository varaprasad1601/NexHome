// import { router } from 'expo-router'; // tickets, see the note below
import { Linking, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Text } from '../../components/ui';
// import { Empty, ErrorBox, Loading, Tag } from '../../components/ui'; // tickets
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
// import { dayLabel } from '../../utils/format'; // tickets
// import { ticketStatus } from '../../utils/status'; // tickets

/**
 * Support: quick contact (call, WhatsApp).
 *
 * The tickets section (the customer's tickets from GET /api/support/tickets/me and "+ New ticket") is turned off for
 * now and kept below in comments to bring back later; the ticket screens (`ticket/new`, `ticket/[id]`) and the
 * booking page's "Get help" still work.
 */
export default function Support() {
  const contact = useApi('/api/settings/public');
  const phone = contact.data?.SUPPORT_PHONE;
  const whatsapp = contact.data?.WHATSAPP_NUMBER;

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView
        contentContainerStyle={{ padding: 20, gap: 16 }}
        refreshControl={<RefreshControl refreshing={contact.refreshing} onRefresh={contact.refresh} />}
      >
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          Support
        </Text>
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
 * Tickets section, turned off for now. To bring it back: restore the commented imports above, add
 * `const tickets = useApi('/api/support/tickets/me?size=50');` to Support, and render the list below in place of the
 * ScrollView (the contact card becomes its ListHeaderComponent, before "My tickets").
 *
 * <FlatList
 *   data={tickets.data?.content ?? []}
 *   keyExtractor={(t) => String(t.supportTicketId)}
 *   contentContainerStyle={{ padding: 20, gap: 12 }}
 *   refreshControl={<RefreshControl refreshing={tickets.refreshing} onRefresh={tickets.refresh} />}
 *   ListHeaderComponent={
 *     <View style={{ gap: 16, marginBottom: 4 }}>
 *       ...the title and the "Need help quickly?" card...
 *       <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
 *         <Text weight="extrabold" style={{ fontSize: 16, color: colors.navy }}>
 *           My tickets
 *         </Text>
 *         <Text weight="bold" style={{ color: colors.blue }} onPress={() => router.push('/ticket/new')} accessibilityRole="button">
 *           + New ticket
 *         </Text>
 *       </View>
 *       <ErrorBox message={tickets.error} />
 *     </View>
 *   }
 *   ListEmptyComponent={tickets.loading ? <Loading /> : <Empty icon="life-buoy" title="No tickets yet" text="Raise a ticket for anything about a booking, payment or professional." />}
 *   renderItem={({ item }) => {
 *     const status = ticketStatus(item.status);
 *     return (
 *       <Card onPress={() => router.push(`/ticket/${item.supportTicketId}`)} style={{ gap: 6 }}>
 *         <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
 *           <Text weight="bold" style={{ flex: 1 }} numberOfLines={1}>
 *             {item.subject}
 *           </Text>
 *           <Tag label={status.label} fg={status.fg} bg={status.bg} />
 *         </View>
 *         <Text style={{ fontSize: 12, color: colors.muted }}>
 *           {[item.publicId, item.bookingPublicId, dayLabel(item.creationDate)].filter(Boolean).join(' · ')}
 *         </Text>
 *       </Card>
 *     );
 *   }}
 * />
 */
