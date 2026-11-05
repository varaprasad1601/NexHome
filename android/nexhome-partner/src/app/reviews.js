import Feather from '@expo/vector-icons/Feather';
import { FlatList, RefreshControl, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Card, Empty, ErrorBox, Header, Loading, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import { colors } from '../theme';
import { dayLabel, serviceNameOf } from '../utils/format';

function Stars({ value, size = 14 }) {
  return (
    <View accessibilityLabel={`${value} out of 5`} style={{ flexDirection: 'row', gap: 2 }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Feather key={n} name="star" size={size} color={n <= value ? colors.star : '#c3cfdb'} />
      ))}
    </View>
  );
}

/**
 * Reviews of the partner: the summary (GET /api/reviews/summary?partnerProfileId=: average, count, a bar per rating)
 * and the published reviews (GET /api/reviews/me).
 */
export default function Reviews() {
  const { profile } = useAuth();
  const summary = useApi(profile ? `/api/reviews/summary?partnerProfileId=${profile.partnerProfileId}` : null);
  const reviews = useApi('/api/reviews/me?size=50');
  const total = summary.data?.total ?? 0;
  const counts = summary.data?.ratingCounts ?? {};
  const average = Number(summary.data?.averageRating) || 0;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Reviews" />
      {reviews.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={reviews.data?.content ?? []}
          keyExtractor={(r) => String(r.reviewId)}
          contentContainerStyle={{ padding: 16, gap: 10 }}
          refreshControl={<RefreshControl refreshing={reviews.refreshing} onRefresh={() => Promise.all([reviews.refresh(), summary.refresh()])} />}
          ListHeaderComponent={
            <View style={{ gap: 10, marginBottom: 2 }}>
              <Card style={{ flexDirection: 'row', gap: 18, alignItems: 'center', borderRadius: 18 }}>
                <View style={{ alignItems: 'center', gap: 4, minWidth: 80 }}>
                  <Text weight="extrabold" style={{ fontSize: 32, color: colors.navy }}>
                    {average ? average.toFixed(1) : '—'}
                  </Text>
                  <Stars value={Math.round(average)} />
                  <Text style={{ fontSize: 12, color: colors.muted }}>{`${total} ${total === 1 ? 'review' : 'reviews'}`}</Text>
                </View>
                <View style={{ flex: 1, gap: 4 }}>
                  {[5, 4, 3, 2, 1].map((n) => {
                    const count = Number(counts[n] ?? 0);
                    return (
                      <View key={n} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                        <Text style={{ fontSize: 12, width: 10 }}>{n}</Text>
                        <View style={{ flex: 1, height: 6, borderRadius: 3, backgroundColor: colors.line, overflow: 'hidden' }}>
                          <View style={{ width: `${total ? (count / total) * 100 : 0}%`, height: 6, backgroundColor: colors.star }} />
                        </View>
                        <Text style={{ fontSize: 12, width: 22, textAlign: 'right', color: colors.muted }}>{count}</Text>
                      </View>
                    );
                  })}
                </View>
              </Card>
              <ErrorBox message={reviews.error || summary.error} />
            </View>
          }
          ListEmptyComponent={<Empty icon="star" title="No reviews yet" text="Customers rate you after each completed job." />}
          renderItem={({ item }) => (
            <Card style={{ gap: 6 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text weight="bold">{item.customerName ?? 'Customer'}</Text>
                <Stars value={item.partnerRating} />
              </View>
              <Text style={{ fontSize: 12, color: colors.muted }}>
                {serviceNameOf(item.serviceName)} · {item.bookingPublicId} · {dayLabel(item.creationDate)}
              </Text>
              {item.reviewText ? <Text style={{ lineHeight: 20 }}>{item.reviewText}</Text> : null}
            </Card>
          )}
        />
      )}
    </SafeAreaView>
  );
}
