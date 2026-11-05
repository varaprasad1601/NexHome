import Feather from '@expo/vector-icons/Feather';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { FlatList, Pressable, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import CartBar from '../components/CartBar';
import { Empty, Header, Loading, Text } from '../components/ui';
import useApi from '../hooks/useApi';
import { colors, fonts } from '../theme';
import { durationLabel, money } from '../utils/format';

/** "Category · 1 hr" (the category only when searching across all of them); empty when neither is known. */
function details(item, categoryId) {
  return [!categoryId && item.categoryName, durationLabel(item.estimatedDurationMinutes)].filter(Boolean).join(' · ');
}

/** Services of one category (from Home's grid) or a search across all of them. */
export default function Search() {
  const { categoryId, title } = useLocalSearchParams();
  const [text, setText] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setSearch(text.trim()), 350);
    return () => clearTimeout(timer);
  }, [text]);

  const query = new URLSearchParams({ size: '50' });
  if (categoryId) query.set('categoryId', String(categoryId));
  if (search) query.set('search', search);
  const services = useApi(`/api/catalog/services?${query}`);
  const list = services.data?.content ?? [];

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={title || 'Find a service'} />
      <View style={{ padding: 16, paddingBottom: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, height: 48, paddingHorizontal: 16, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 14 }}>
          <Feather name="search" size={18} color={colors.muted} />
          <TextInput
            value={text}
            onChangeText={setText}
            autoFocus={!categoryId}
            placeholder={categoryId ? `Search in ${title}` : 'Search for a service'}
            placeholderTextColor="#8a9aad"
            accessibilityLabel="Search services"
            style={{ flex: 1, fontFamily: fonts.regular, fontSize: 14, color: colors.ink }}
          />
        </View>
      </View>
      {services.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={list}
          keyExtractor={(item) => String(item.serviceId)}
          contentContainerStyle={{ padding: 16, paddingTop: 8, gap: 8, paddingBottom: 100 }}
          ListEmptyComponent={
            <Empty icon="search" title={search ? 'No services match your search' : 'Services coming soon'} text={services.error || undefined} />
          }
          // Compact cards (sample 9 of search-samples.html, as separate cards), one fact per line: name; category and
          // time; rating; price with the original price struck through when discounted
          renderItem={({ item }) => {
            const discounted = item.discountPrice != null && Number(item.discountPrice) < Number(item.basePrice);
            const line = details(item, categoryId);
            return (
              <Pressable
                accessibilityRole="button"
                onPress={() => router.push(`/service/${item.serviceId}`)}
                style={({ pressed }) => ({
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 10,
                  paddingVertical: 12,
                  paddingHorizontal: 14,
                  backgroundColor: pressed ? colors.blueSoft : colors.white,
                  borderWidth: 1,
                  borderColor: colors.line,
                  borderRadius: 14
                })}
              >
                <View style={{ flex: 1, gap: 3 }}>
                  <Text weight="bold" style={{ fontSize: 14 }} numberOfLines={1}>
                    {item.name}
                  </Text>
                  {line ? (
                    <Text style={{ fontSize: 12, color: colors.muted }} numberOfLines={1}>
                      {line}
                    </Text>
                  ) : null}
                  <Text style={{ fontSize: 12, color: colors.muted }}>
                    {item.averageRating ? (
                      <>
                        <Text weight="bold" style={{ color: colors.star, fontSize: 12 }}>
                          ★
                        </Text>
                        {` ${item.averageRating} (${item.reviewCount} ${item.reviewCount === 1 ? 'review' : 'reviews'})`}
                      </>
                    ) : (
                      'New · no reviews yet'
                    )}
                  </Text>
                  <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 6 }}>
                    <Text weight="extrabold" style={{ color: colors.navy, fontSize: 14.5 }}>
                      {money(discounted ? item.discountPrice : item.basePrice)}
                    </Text>
                    {discounted ? (
                      <Text style={{ color: '#8a9aad', fontSize: 12, textDecorationLine: 'line-through' }}>{money(item.basePrice)}</Text>
                    ) : null}
                  </View>
                </View>
                <Feather name="chevron-right" size={18} color="#8a9aad" />
              </Pressable>
            );
          }}
        />
      )}
      <CartBar covers={Object.fromEntries(list.map((s) => [s.serviceId, s.coverImageUrl]))} />
    </SafeAreaView>
  );
}
