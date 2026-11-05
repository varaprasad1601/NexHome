import Feather from '@expo/vector-icons/Feather';
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { LinearGradient } from 'expo-linear-gradient';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, View, useWindowDimensions } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { fileUrl } from '../../api/client';
import { Button, Cover, Empty, ErrorBox, Loading, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { durationLabel, money } from '../../utils/format';

/** The formatted description (p, b, ul, li ...) as paragraphs of plain text and bullet lines. */
function paragraphsOf(html) {
  if (!html) return [];
  return html
    .replace(/<li[^>]*>/gi, '\n• ')
    .replace(/<\/(p|div|ul|ol)>|<br\s*\/?>/gi, '\n\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .split(/\n{2,}/)
    .map((part) => part.trim())
    .filter(Boolean);
}

/** The three promises under the title. */
const PROMISES = [
  ['shield-check-outline', 'Verified pros'],
  ['cash', 'Pay after service'],
  ['whatsapp', 'Help on WhatsApp']
];

/** "How is the service done?": soft blobs with these icons (Admin Sreens/customer-app-samples/service-v2.html). */
const STEPS = [
  ['calendar-check-outline', 'Book a time', 'As soon as possible, or any day this week', ['#e3efff', '#cfe2f6'], colors.blue],
  ['shield-account-outline', 'A verified pro comes', 'You get their name and number', ['#e6f7ee', '#cdeedd'], colors.green],
  ['check-circle-outline', 'The job is done', 'They do the work and check it with you', ['#fff4dc', '#fde7b3'], '#a36a00'],
  ['currency-inr', 'Pay after the job', 'Cash to the pro, or online', ['#efe9ff', '#ddd3fb'], '#5b3cc4']
];

/** A soft, slightly uneven rounded shape. */
const BLOB = { borderTopLeftRadius: 22, borderTopRightRadius: 14, borderBottomRightRadius: 20, borderBottomLeftRadius: 12 };

/**
 * A service (design: service-v2.html): its photos; category, name, time and rating; the short description; three
 * promises; the options with prices (GET /api/catalog/services/quote); About this service (the main description);
 * How is the service done?; Please keep ready; Frequently asked questions (its customer FAQs); You may also need
 * (other services of its category); and the price with Add to cart (POST /api/cart/items/add).
 */
export default function ServiceScreen() {
  const { id } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const { width } = useWindowDimensions();
  const detail = useApi(`/api/catalog/services/${id}`);
  const faqs = useApi(`/api/faqs?serviceId=${id}&audience=CUSTOMER`);
  const [variantId, setVariantId] = useState(null);
  const [quote, setQuote] = useState(null);
  const [openFaq, setOpenFaq] = useState(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState('');
  const [imageIndex, setImageIndex] = useState(0);

  const service = detail.data?.service;
  const related = useApi(service ? `/api/catalog/services?categoryId=${service.categoryId}&size=12` : null);
  const variants = useMemo(() => (detail.data?.variants ?? []).filter((v) => v.isActive), [detail.data]);
  // The first option is chosen until the customer picks another
  const chosenId = variantId ?? variants[0]?.serviceVariantId ?? null;
  const chosen = variants.find((v) => v.serviceVariantId === chosenId) ?? null;

  // The price of the choice
  useEffect(() => {
    if (!service) return undefined;
    let cancelled = false;
    const query = new URLSearchParams({ serviceId: String(service.serviceId) });
    if (chosen) query.set('serviceVariantId', String(chosen.serviceVariantId));
    authRequest(`/api/catalog/services/quote?${query}`)
      .then((data) => !cancelled && setQuote(data))
      .catch(() => !cancelled && setQuote(null));
    return () => {
      cancelled = true;
    };
  }, [authRequest, service, chosen]);

  if (detail.loading) return <Loading />;
  if (!service) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
        <Empty icon="alert-circle" title="This service is not available" text={detail.error || undefined} action={<Button title="Go back" onPress={() => router.back()} />} />
      </SafeAreaView>
    );
  }

  const images = detail.data.images ?? [];
  const keepReady = (detail.data.requirements ?? []).filter((r) => r.requirementFor === 'CUSTOMER');
  const about = paragraphsOf(service.description);
  const discounted = quote && Number(quote.regularSubtotal) > Number(quote.total);
  const others = (related.data?.content ?? []).filter((s) => s.serviceId !== service.serviceId);
  const priceKind = service.pricingType === 'HOURLY' ? 'Hourly' : service.pricingType === 'AREA' ? 'By home size' : 'Fixed price';
  const meta = [durationLabel(chosen?.durationMinutes ?? service.estimatedDurationMinutes), priceKind].filter(Boolean);

  const addToCart = async () => {
    setError('');
    setAdding(true);
    try {
      await authRequest('/api/cart/items/add', {
        method: 'POST',
        body: { serviceId: service.serviceId, serviceVariantId: chosen?.serviceVariantId ?? null }
      });
      router.push('/cart');
    } catch (err) {
      setError(err.message);
    } finally {
      setAdding(false);
    }
  };

  return (
    <SafeAreaView edges={['bottom']} style={{ flex: 1, backgroundColor: colors.white }}>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        {/* Photos */}
        <View style={{ height: 260 }}>
          {images.length ? (
            <ScrollView
              horizontal
              pagingEnabled
              showsHorizontalScrollIndicator={false}
              onMomentumScrollEnd={(e) => setImageIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
            >
              {images.map((image) => (
                <Cover key={image.serviceImageId} uri={fileUrl(image.imageUrl)} style={{ width, height: 260 }} />
              ))}
            </ScrollView>
          ) : (
            <Cover style={{ width, height: 260 }} />
          )}
          <SafeAreaView edges={['top']} style={{ position: 'absolute', top: 0, left: 0 }}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Back"
              onPress={() => router.back()}
              style={{ margin: 16, width: 44, height: 44, borderRadius: 14, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' }}
            >
              <Feather name="chevron-left" size={24} color={colors.navy} />
            </Pressable>
          </SafeAreaView>
          {images.length > 1 ? (
            <View style={{ position: 'absolute', bottom: 30, right: 16, backgroundColor: 'rgba(255,255,255,0.9)', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 }}>
              <Text weight="bold" style={{ fontSize: 11, color: colors.navy }}>
                {imageIndex + 1} / {images.length}
              </Text>
            </View>
          ) : null}
        </View>

        <View style={{ marginTop: -20, backgroundColor: colors.white, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, gap: 14 }}>
          {/* Title */}
          <View style={{ gap: 5 }}>
            <Text weight="bold" style={{ fontSize: 12, color: colors.green, letterSpacing: 0.5 }}>
              {(service.categoryName ?? '').toUpperCase()}
            </Text>
            <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
              {service.name}
            </Text>
            <Text style={{ fontSize: 13, color: colors.muted }}>
              {meta[0]}
              {service.averageRating ? (
                <>
                  {' · '}
                  <Text weight="bold" style={{ color: colors.star, fontSize: 13 }}>
                    ★ {service.averageRating}
                  </Text>
                  {` (${service.reviewCount} ${service.reviewCount === 1 ? 'review' : 'reviews'})`}
                </>
              ) : null}
              {meta[1] ? ` · ${meta[1]}` : ''}
            </Text>
          </View>

          {/* Short description */}
          {service.shortDescription ? <Text style={{ fontSize: 14, lineHeight: 21, color: colors.ink }}>{service.shortDescription}</Text> : null}

          {/* Promises */}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {PROMISES.map(([icon, label]) => (
              <View key={label} style={{ flex: 1, alignItems: 'center', gap: 6, paddingVertical: 10, paddingHorizontal: 4, borderRadius: 14, backgroundColor: colors.ground }}>
                <View style={{ width: 34, height: 34, borderRadius: 17, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
                  <MaterialCommunityIcons name={icon} size={18} color={colors.green} />
                </View>
                <Text weight="bold" style={{ fontSize: 11, textAlign: 'center' }}>
                  {label}
                </Text>
              </View>
            ))}
          </View>

          {/* Options */}
          {variants.length ? (
            <Section title={service.pricingType === 'HOURLY' ? 'Choose a package' : service.pricingType === 'AREA' ? 'Choose your home size' : 'Choose an option'}>
              {variants.map((variant) => {
                const price = variant.discountPrice ?? variant.priceOverride ?? service.discountPrice ?? service.basePrice;
                const regular = variant.priceOverride ?? service.basePrice;
                const on = variant.serviceVariantId === chosenId;
                return (
                  <Pressable
                    key={variant.serviceVariantId}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: on }}
                    onPress={() => setVariantId(variant.serviceVariantId)}
                    style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 13, paddingHorizontal: 14, borderRadius: 14, borderWidth: 1.5, borderColor: on ? colors.blue : colors.line, backgroundColor: on ? colors.blueSoft : colors.white }}
                  >
                    <View style={{ width: 20, height: 20, borderRadius: 10, borderWidth: on ? 6 : 2, borderColor: on ? colors.blue : '#c3cfdb' }} />
                    <View style={{ flex: 1, gap: 2 }}>
                      <Text weight="bold">{variant.name}</Text>
                      {variant.description || variant.durationMinutes ? (
                        <Text style={{ fontSize: 12, color: colors.muted }}>{variant.description || durationLabel(variant.durationMinutes)}</Text>
                      ) : null}
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text weight="extrabold" style={{ color: colors.navy }}>
                        {money(price)}
                      </Text>
                      {Number(regular) > Number(price) ? (
                        <Text style={{ fontSize: 11.5, color: colors.muted, textDecorationLine: 'line-through' }}>{money(regular)}</Text>
                      ) : null}
                    </View>
                  </Pressable>
                );
              })}
            </Section>
          ) : null}

          {/* Main description */}
          {about.length ? (
            <Section title="About this service">
              {about.map((paragraph, i) => (
                <Text key={i} style={{ fontSize: 14, lineHeight: 21, color: colors.muted }}>
                  {paragraph}
                </Text>
              ))}
            </Section>
          ) : null}

          {/* How is the service done? */}
          <Section title="How is the service done?">
            <View>
              {STEPS.map(([icon, title, text, gradient, color], i) => (
                <View key={title} style={{ flexDirection: 'row', gap: 12, paddingBottom: i < STEPS.length - 1 ? 16 : 0 }}>
                  {i < STEPS.length - 1 ? (
                    <View style={{ position: 'absolute', left: 19, top: 44, bottom: 2, width: 2, alignItems: 'center', justifyContent: 'space-between' }}>
                      {[0, 1, 2].map((d) => (
                        <View key={d} style={{ width: 2, height: 4, borderRadius: 1, backgroundColor: colors.line }} />
                      ))}
                    </View>
                  ) : null}
                  <LinearGradient colors={gradient} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[{ width: 40, height: 40, alignItems: 'center', justifyContent: 'center' }, BLOB]}>
                    <MaterialCommunityIcons name={icon} size={20} color={color} />
                  </LinearGradient>
                  <View style={{ flex: 1, gap: 2, paddingTop: 2 }}>
                    <Text weight="bold" style={{ fontSize: 14 }}>
                      {title}
                    </Text>
                    <Text style={{ fontSize: 12.5, color: colors.muted }}>{text}</Text>
                  </View>
                </View>
              ))}
            </View>
          </Section>

          {/* Please keep ready */}
          {keepReady.length ? (
            <Section title="Please keep ready">
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                {keepReady.map((r) => (
                  <View key={r.serviceRequirementId} style={{ backgroundColor: colors.blueTint, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999 }}>
                    <Text weight="semibold" style={{ fontSize: 12, color: colors.blue }}>
                      {r.description}
                    </Text>
                  </View>
                ))}
              </View>
            </Section>
          ) : null}

          {/* Frequently asked questions */}
          {(faqs.data ?? []).length ? (
            <Section title="Frequently asked questions">
              {faqs.data.map((faq) => (
                <Pressable
                  key={faq.faqId}
                  accessibilityRole="button"
                  accessibilityState={{ expanded: openFaq === faq.faqId }}
                  onPress={() => setOpenFaq(openFaq === faq.faqId ? null : faq.faqId)}
                  style={{ borderWidth: 1, borderColor: colors.line, borderRadius: 14, padding: 14, gap: 8 }}
                >
                  <View style={{ flexDirection: 'row', gap: 8 }}>
                    <Text weight="bold" style={{ flex: 1 }}>
                      {faq.question}
                    </Text>
                    <Feather name={openFaq === faq.faqId ? 'chevron-up' : 'chevron-down'} size={18} color={colors.muted} />
                  </View>
                  {openFaq === faq.faqId ? <Text style={{ color: colors.muted, lineHeight: 20 }}>{faq.answer}</Text> : null}
                </Pressable>
              ))}
            </Section>
          ) : null}

          {/* You may also need: the same category */}
          {others.length ? (
            <Section
              title="You may also need"
              right={
                <Text
                  weight="bold"
                  style={{ fontSize: 13, color: colors.blue }}
                  accessibilityRole="button"
                  onPress={() => router.push({ pathname: '/search', params: { categoryId: service.categoryId, title: service.categoryName } })}
                >
                  See all
                </Text>
              }
            >
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 10 }}>
                {others.map((s) => (
                  <Pressable
                    key={s.serviceId}
                    accessibilityRole="button"
                    onPress={() => router.push(`/service/${s.serviceId}`)}
                    style={{ width: 140, borderWidth: 1, borderColor: colors.line, borderRadius: 14, padding: 8, gap: 5 }}
                  >
                    <Cover uri={fileUrl(s.coverImageUrl)} style={{ height: 76, borderRadius: 10 }} />
                    <Text weight="bold" style={{ fontSize: 12.5 }} numberOfLines={1}>
                      {s.name}
                    </Text>
                    <Text style={{ fontSize: 11.5, color: colors.muted }}>
                      {s.averageRating ? (
                        <>
                          <Text weight="bold" style={{ color: colors.star, fontSize: 11.5 }}>
                            ★
                          </Text>
                          {` ${s.averageRating}`}
                        </>
                      ) : (
                        'New'
                      )}
                    </Text>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Text weight="extrabold" style={{ color: colors.blue, fontSize: 13 }}>
                        {money(s.discountPrice ?? s.basePrice)}
                      </Text>
                      <View style={{ width: 24, height: 24, borderRadius: 8, backgroundColor: colors.blue, alignItems: 'center', justifyContent: 'center' }}>
                        <Feather name="plus" size={15} color={colors.white} />
                      </View>
                    </View>
                  </Pressable>
                ))}
              </ScrollView>
            </Section>
          ) : null}
          <ErrorBox message={error} />
        </View>
      </ScrollView>

      <View style={{ borderTopWidth: 1, borderTopColor: colors.line, paddingHorizontal: 20, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <View>
          <Text style={{ fontSize: 12, color: colors.muted }}>{chosen?.name ?? (service.pricingType === 'HOURLY' ? 'Per hour' : 'Price')}</Text>
          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 6 }}>
            <Text weight="extrabold" style={{ fontSize: 19, color: colors.navy }}>
              {quote ? money(quote.total) : '…'}
            </Text>
            {discounted ? <Text style={{ fontSize: 12, color: colors.muted, textDecorationLine: 'line-through' }}>{money(quote.regularSubtotal)}</Text> : null}
          </View>
        </View>
        <Button title="Add to cart" onPress={addToCart} loading={adding} style={{ flex: 1 }} />
      </View>
    </SafeAreaView>
  );
}

/** A section with room above it and a thin line, its title (and an optional link on the right). */
function Section({ title, right, children }) {
  return (
    <View style={{ gap: 10, marginTop: 12, paddingTop: 16, borderTopWidth: 1, borderTopColor: colors.line }}>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <Text weight="extrabold" style={{ fontSize: 16, color: colors.navy }}>
          {title}
        </Text>
        {right}
      </View>
      {children}
    </View>
  );
}
