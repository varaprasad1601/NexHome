import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { apiRequest } from '../../api/client';
import { Button, ErrorBox, Field, FormScroll, Header, Pick, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { colors } from '../../theme';

/**
 * A new support ticket from the customer app (requesterRole CUSTOMER): category (SUPPORT_CATEGORY), subject,
 * description, and the booking it is about when opened from one. POST /api/support/tickets/create.
 */
export default function NewTicket() {
  const { bookingId, bookingPublicId } = useLocalSearchParams();
  const { authRequest, user } = useAuth();
  const [categories, setCategories] = useState([]);
  const [category, setCategory] = useState(bookingId ? 'BOOKING' : null);
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest('/api/lookups/values/SUPPORT_CATEGORY')
      .then((values) => {
        setCategories(values);
        setCategory((current) => current ?? values[0]?.valueCode ?? null);
      })
      .catch(() => {});
  }, []);

  const submit = async () => {
    setError('');
    if (!category || !subject.trim() || !description.trim()) {
      setError('Choose a topic and fill in the subject and what happened.');
      return;
    }
    setBusy(true);
    try {
      const ticket = await authRequest('/api/support/tickets/create', {
        method: 'POST',
        body: {
          requesterId: user.userId,
          requesterRole: 'CUSTOMER',
          bookingId: bookingId ? Number(bookingId) : null,
          category,
          subject: subject.trim(),
          description: description.trim()
        }
      });
      router.replace(`/ticket/${ticket.supportTicketId}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="New ticket" />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14 }}>
          {bookingPublicId ? (
            <Text style={{ color: colors.muted }}>
              About booking <Text weight="bold">{bookingPublicId}</Text>
            </Text>
          ) : null}
          <Text weight="bold" style={{ fontSize: 13 }}>
            Topic
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: -6 }}>
            {categories.map((value) => (
              <Pick key={value.valueCode} selected={category === value.valueCode} onPress={() => setCategory(value.valueCode)} style={{ paddingVertical: 8, paddingHorizontal: 12 }}>
                <Text weight="bold" style={{ fontSize: 13, color: category === value.valueCode ? colors.blue : colors.ink }}>
                  {value.displayName}
                </Text>
              </Pick>
            ))}
          </View>
          <Field label="Subject" value={subject} onChangeText={setSubject} maxLength={200} placeholder="e.g. The professional did not arrive" />
          <Field label="What happened?" value={description} onChangeText={setDescription} maxLength={5000} multiline />
          <ErrorBox message={error} />
          <Button title="Send ticket" onPress={submit} loading={busy} />
      </FormScroll>
    </SafeAreaView>
  );
}
