import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { CollectScreen } from '../../components/WorkScreens';
import { Empty, Header, Loading } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/**
 * Collect payment (its own screen, opened after Finish work): the job's final amount in large type. Cash: Cash
 * collected (PUT /api/payments/cash-collections/update COLLECTED) and Back; online: the customer pays in the app.
 */
export default function Collect() {
  const { id } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const detail = useApi(`/api/bookings/${id}`);
  const booking = detail.data?.booking;

  if (detail.loading) return <Loading />;
  if (!booking) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Header title="Collect payment" />
        <Empty icon="alert-circle" title="Job not found" text={detail.error || undefined} />
      </SafeAreaView>
    );
  }

  return (
    <CollectScreen
      booking={booking}
      onBack={() => router.back()}
      onCashCollected={async () => {
        const payments = await authRequest(`/api/payments/bookings/${booking.bookingId}`);
        const cash = payments?.cashCollection;
        if (!cash) throw new Error('No cash is due for this job.');
        if (cash.status !== 'COLLECTED') {
          await authRequest('/api/payments/cash-collections/update', { method: 'PUT', body: { cashCollectionId: cash.cashCollectionId, status: 'COLLECTED' } });
        }
        router.replace({ pathname: '/job/[id]', params: { id: String(booking.bookingId), collected: '1' } });
      }}
    />
  );
}
