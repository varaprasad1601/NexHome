import Feather from '@expo/vector-icons/Feather';
import { LinearGradient } from 'expo-linear-gradient';
import { router, useFocusEffect } from 'expo-router';
import { setStatusBarStyle } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Linking, Pressable, RefreshControl, ScrollView, Switch, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import JobCard from '../../components/JobCard';
import OfferCard from '../../components/OfferCard';
import { Banner, Card, Empty, ErrorBox, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { isoDayOf, money, parseApiDate, todayInIndia } from '../../utils/format';
import { isPaid } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

function Stat({ label, value, icon }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 16, padding: 12, gap: 4 }}>
      <Feather name={icon} size={16} color={colors.blue} />
      <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
        {value}
      </Text>
      <Text style={{ fontSize: 11.5, color: colors.muted }}>{label}</Text>
    </View>
  );
}

/**
 * Home: greeting and bell, the Online / Offline switch (PUT /api/partners/profiles/availability/update; BUSY is set
 * by work), new offers to accept or decline (GET /api/bookings/offers/me), jobs assigned and waiting for an answer,
 * today's jobs (GET /api/bookings/partner/me), the rating (GET /api/reviews/summary) and job figures
 * (GET /api/bookings/partner-stats/{id}).
 */
export default function Home() {
  // The support number and hours (public general settings) for Need help
  const contact = useApi('/api/settings/public');
  const phone = contact.data?.SUPPORT_PHONE;
  const { authRequest, profile, reloadProfile } = useAuth();
  const insets = useSafeAreaInsets();
  // White status bar text over the blue header while Home is shown
  useFocusEffect(
    useCallback(() => {
      setStatusBarStyle('light');
      return () => setStatusBarStyle('dark');
    }, [])
  );
  const id = profile?.partnerProfileId;
  const offers = useApi('/api/bookings/offers/me?size=20', { pollMs: 20000 });
  const jobs = useApi('/api/bookings/partner/me?size=100', { pollMs: 20000 });
  const summary = useApi('/api/notifications/me/summary?app=PARTNER', { pollMs: 10000 });
  const rating = useApi(id ? `/api/reviews/summary?partnerProfileId=${id}` : null);
  const stats = useApi(id ? `/api/bookings/partner-stats/${id}` : null);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const unread = summary.data?.unread ?? 0;
  // A new notification (a job assigned or offered, cancelled, ...): load the offers and jobs at once, not at their
  // next 20-second check
  const lastUnread = useRef(null);
  useEffect(() => {
    if (lastUnread.current !== null && unread > lastUnread.current) {
      offers.reload();
      jobs.reload();
      stats.reload();
    }
    lastUnread.current = unread;
  }, [unread]); // eslint-disable-line react-hooks/exhaustive-deps
  const approved = profile?.onboardingStatus === 'APPROVED';
  const availability = profile?.availabilityStatus;
  const all = jobs.data?.content ?? [];
  const today = todayInIndia();
  const waiting = all.filter((j) => j.bookingStatus === 'ASSIGNED');
  // Work under way or accepted, and anything else scheduled for today (assigned ones are listed above)
  // The current job: the one being worked on, else the next accepted one (as soon as possible first, then soonest)
  const startOf = (job) => (job.scheduledStartAt ? parseApiDate(job.scheduledStartAt)?.date.getTime() ?? Infinity : -Infinity);
  const current =
    all.find((j) => ['IN_PROGRESS', 'PAUSED'].includes(j.bookingStatus)) ??
    [...all].filter((j) => j.bookingStatus === 'PARTNER_EN_ROUTE').sort((a, b) => startOf(a) - startOf(b))[0] ??
    null;
  const todays = all.filter(
    (j) => j.bookingId !== current?.bookingId
  ).filter(
    (j) =>
      ['PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'].includes(j.bookingStatus) ||
      // Finished today, or finished and still waiting for the payment
      (j.bookingStatus === 'COMPLETED' && (isoDayOf(j.actualEndAt) === today || !isPaid(j))) ||
      (['PENDING', 'CONFIRMED'].includes(j.bookingStatus) && isoDayOf(j.scheduledStartAt) === today)
  );

  const refresh = async () => {
    setRefreshing(true);
    await Promise.all([offers.reload(), jobs.reload(), summary.reload(), rating.reload(), stats.reload(), reloadProfile().catch(() => {})]);
    setRefreshing(false);
  };
  useTabReset(null, refresh);

  const setOnline = async (online) => {
    setError('');
    setSwitching(true);
    try {
      await authRequest('/api/partners/profiles/availability/update', {
        method: 'PUT',
        body: { partnerProfileId: id, availabilityStatus: online ? 'ONLINE' : 'OFFLINE' }
      });
      await reloadProfile();
    } catch (err) {
      setError(err.message);
    } finally {
      setSwitching(false);
    }
  };

  const offerList = offers.data?.content ?? [];
  return (
    <View style={{ flex: 1, backgroundColor: colors.ground }}>
      {/* Fixed blue header with bubbles (the customer app's home header) */}
      <LinearGradient
        colors={['#1a73d9', colors.blue]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.8, y: 1 }}
        style={{ paddingTop: insets.top + 10, paddingHorizontal: 20, paddingBottom: 22, overflow: 'hidden', borderBottomLeftRadius: 28, borderBottomRightRadius: 28 }}
      >
        <View style={{ position: 'absolute', width: 190, height: 190, borderRadius: 95, right: -50, top: -60, backgroundColor: 'rgba(255,255,255,0.10)' }} />
        <View style={{ position: 'absolute', width: 110, height: 110, borderRadius: 55, left: -36, bottom: -48, backgroundColor: 'rgba(255,255,255,0.10)' }} />
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, gap: 2 }}>
            <Text weight="semibold" style={{ fontSize: 12.5, color: colors.white, opacity: 0.85 }}>
              Hello{profile?.firstName ? `, ${profile.firstName}` : ''}
            </Text>
            <Text weight="extrabold" style={{ fontSize: 20, color: colors.white }}>
              Ready for work?
            </Text>
          </View>
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

      <ScrollView contentContainerStyle={{ padding: 20, gap: 18, paddingBottom: 32 }} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>


        <LinearGradient
          colors={availability === 'ONLINE' ? [colors.green, '#1ea36a'] : [colors.blue, '#1660b8']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={{ borderRadius: 20, padding: 18, flexDirection: 'row', alignItems: 'center', gap: 12 }}
        >
          <View style={{ flex: 1, gap: 4 }}>
            <Text weight="extrabold" style={{ color: colors.white, fontSize: 18 }}>
              {availability === 'ONLINE' ? 'You are online' : availability === 'BUSY' ? 'You are on a job' : 'You are offline'}
            </Text>
            <Text style={{ color: colors.white, opacity: 0.88, fontSize: 12.5 }}>
              {availability === 'ONLINE' ? 'NexHome can send you new jobs.' : availability === 'BUSY' ? 'You show as busy until the job is done.' : 'Go online to get new jobs.'}
            </Text>
          </View>
          {availability !== 'BUSY' ? (
            <Switch
              value={availability === 'ONLINE'}
              onValueChange={setOnline}
              disabled={!approved || switching}
              accessibilityLabel="Online for new jobs"
              trackColor={{ true: '#7fd3a8', false: '#6d97cf' }}
              thumbColor={colors.white}
            />
          ) : null}
        </LinearGradient>
        {!approved ? (
          <Banner tone="warning" title="Your changes are being verified" text="You cannot go online or take new jobs until NexHome approves your profile again." />
        ) : null}
        <ErrorBox message={error} />

        <View style={{ flexDirection: 'row', gap: 10 }}>
          <Stat icon="star" label={rating.data?.total ? `${rating.data.total} reviews` : 'No reviews yet'} value={rating.data?.averageRating ? Number(rating.data.averageRating).toFixed(1) : '—'} />
          <Stat icon="check-circle" label="Jobs done" value={stats.data?.completed ?? '—'} />
          <Stat icon="trending-up" label="Earned" value={stats.data ? money(stats.data.totalEarnings ?? 0) : '—'} />
        </View>

        {current ? (
          <View style={{ gap: 12 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
                Current job
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5, backgroundColor: colors.greenSoft, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 3 }}>
                <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: colors.green }} />
                <Text weight="extrabold" style={{ fontSize: 10.5, color: colors.greenDark }}>
                  Now
                </Text>
              </View>
            </View>
            <View style={{ borderRadius: 20, borderWidth: 2, borderColor: colors.green }}>
              <JobCard job={current} />
            </View>
          </View>
        ) : null}

        {offerList.length ? (
          <View style={{ gap: 12 }}>
            <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
              New offers
            </Text>
            {offerList.map((offer) => (
              <OfferCard key={offer.assignmentId} assignment={offer} onDone={() => Promise.all([offers.reload(), jobs.reload()])} />
            ))}
          </View>
        ) : null}

        {waiting.length ? (
          <View style={{ gap: 12 }}>
            <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
              Assigned to you
            </Text>
            {waiting.map((job) => (
              <JobCard key={job.bookingId} job={job} />
            ))}
          </View>
        ) : null}

        <View style={{ gap: 12 }}>
          <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
            Today
          </Text>
          {todays.length ? (
            todays.map((job) => <JobCard key={job.bookingId} job={job} />)
          ) : (
            <Card>
              <Empty icon="sun" title={jobs.loading ? 'Loading your jobs…' : 'No jobs today'} text={offerList.length || jobs.loading ? undefined : 'Stay online and new offers show here.'} />
            </Card>
          )}
          <ErrorBox message={jobs.error || offers.error} />
        </View>

        {/* Need help? (as the customer app's Home): opens Support; the green button calls NexHome */}
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push('/support')}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderRadius: 16, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}
        >
          <View style={{ flex: 1, gap: 2 }}>
            <Text weight="bold" style={{ fontSize: 14 }}>
              Need help?
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
      </ScrollView>
    </View>
  );
}
