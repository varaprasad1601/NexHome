import Feather from '@expo/vector-icons/Feather';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Pressable, ScrollView, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Card, ErrorBox, Header, Loading, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors, fonts } from '../../theme';
import { whenLabel } from '../../utils/format';
import { ticketStatus } from '../../utils/status';

/** One ticket and its conversation (staff on the left, the customer on the right); replies until it is closed. */
export default function Ticket() {
  const { id } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const detail = useApi(`/api/support/tickets/${id}`);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (detail.loading) return <Loading />;
  const ticket = detail.data?.ticket;
  if (!ticket) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Ticket" />
        <ErrorBox message={detail.error} />
      </SafeAreaView>
    );
  }
  const status = ticketStatus(ticket.status);
  const closed = ticket.status === 'CLOSED';

  const send = async () => {
    if (!message.trim()) return;
    setError('');
    setBusy(true);
    try {
      await authRequest('/api/support/tickets/messages/add', { method: 'POST', body: { supportTicketId: ticket.supportTicketId, message: message.trim() } });
      setMessage('');
      detail.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={ticket.publicId} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior="padding">
        <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
          <Card style={{ gap: 8 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
              <Text weight="extrabold" style={{ flex: 1, fontSize: 16 }}>
                {ticket.subject}
              </Text>
              <Tag label={status.label} fg={status.fg} bg={status.bg} />
            </View>
            <Text style={{ color: colors.muted, lineHeight: 20 }}>{ticket.description}</Text>
            <Text style={{ fontSize: 12, color: colors.muted }}>{[ticket.bookingPublicId, whenLabel(ticket.creationDate)].filter(Boolean).join(' · ')}</Text>
          </Card>
          {(detail.data.messages ?? []).map((m) => (
            <View
              key={m.messageId}
              style={{
                alignSelf: m.fromStaff ? 'flex-start' : 'flex-end',
                maxWidth: '85%',
                backgroundColor: m.fromStaff ? colors.white : colors.blue,
                borderWidth: m.fromStaff ? 1 : 0,
                borderColor: colors.line,
                borderRadius: 16,
                padding: 12,
                gap: 4
              }}
            >
              <Text weight="bold" style={{ fontSize: 12, color: m.fromStaff ? colors.green : colors.white, opacity: m.fromStaff ? 1 : 0.85 }}>
                {m.fromStaff ? 'NexHome support' : 'You'}
              </Text>
              <Text style={{ color: m.fromStaff ? colors.ink : colors.white, lineHeight: 20 }}>{m.message}</Text>
              <Text style={{ fontSize: 11, color: m.fromStaff ? colors.muted : colors.white, opacity: 0.8 }}>{whenLabel(m.creationDate)}</Text>
            </View>
          ))}
          <ErrorBox message={error} />
        </ScrollView>
        {closed ? (
          <View style={{ padding: 16, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line }}>
            <Text style={{ color: colors.muted, textAlign: 'center' }}>This ticket is closed. Raise a new one if you need more help.</Text>
          </View>
        ) : (
          <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8, padding: 12, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line }}>
            <TextInput
              value={message}
              onChangeText={setMessage}
              placeholder="Write a reply"
              placeholderTextColor="#8a9aad"
              multiline
              maxLength={5000}
              accessibilityLabel="Reply"
              style={{ flex: 1, maxHeight: 120, minHeight: 44, borderWidth: 1, borderColor: colors.line, borderRadius: 14, paddingHorizontal: 14, paddingVertical: 10, fontFamily: fonts.regular, fontSize: 14, color: colors.ink }}
            />
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Send reply"
              onPress={send}
              disabled={busy || !message.trim()}
              style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center', opacity: busy || !message.trim() ? 0.5 : 1 }}
            >
              <Feather name="send" size={18} color={colors.white} />
            </Pressable>
          </View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
