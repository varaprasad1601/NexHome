import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, RefreshControl, ScrollView, Switch, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import JobCard from '../../components/JobCard';
import OfferCard from '../../components/OfferCard';
import { Banner, Card, Empty, ErrorBox, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { isoDayOf, money, todayInIndia } from '../../utils/format';

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
  const { authRequest, profile, reloadProfile } = useAuth();
  const id = profile?.partnerProfileId;
  const offers = useApi('/api/bookings/offers/me?size=20');
  const jobs = useApi('/api/bookings/partner/me?size=100');
  const summary = useApi('/api/notifications/me/summary?app=PARTNER');
  const rating = useApi(id ? `/api/reviews/summary?partnerProfileId=${id}` : null);
  const stats = useApi(id ? `/api/bookings/partner-stats/${id}` : null);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const unread = summary.data?.unread ?? 0;
  const approved = profile?.onboardingStatus === 'APPROVED';
  const availability = profile?.availabilityStatus;
  const all = jobs.data?.content ?? [];
  const today = todayInIndia();
  const waiting = all.filter((j) => j.bookingStatus === 'ASSIGNED');
  // Work under way or accepted, and anything else scheduled for today (assigned ones are listed above)
  const todays = all.filter(
    (j) =>
      ['PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'].includes(j.bookingStatus) ||
      (j.bookingStatus === 'COMPLETED' && isoDayOf(j.actualEndAt) === today) ||
      (['PENDING', 'CONFIRMED'].includes(j.bookingStatus) && isoDayOf(j.scheduledStartAt) === today)
  );

  const refresh = async () => {
    setRefreshing(true);
    await Promise.all([offers.reload(), jobs.reload(), summary.reload(), rating.reload(), stats.reload(), reloadProfile().catch(() => {})]);
    setRefreshing(false);
  };

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
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 18, paddingBottom: 32 }} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ fontSize: 12, color: colors.muted }}>Hello{profile?.firstName ? `, ${profile.firstName}` : ''}</Text>
            <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy }}>
              Ready for work?
            </Text>
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={unread ? `Notifications, ${unread} unread` : 'Notifications'}
            onPress={() => router.push('/notifications')}
            style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' }}
          >
            <Feather name="bell" size={20} color={colors.navy} />
            {unread ? (
              <View style={{ position: 'absolute', top: 7, right: 7, minWidth: 16, height: 16, borderRadius: 8, backgroundColor: '#e53e3e', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 3 }}>
                <Text weight="bold" style={{ color: colors.white, fontSize: 9.5 }}>
                  {unread > 9 ? '9+' : unread}
                </Text>
              </View>
            ) : null}
          </Pressable>
        </View>

        {!approved ? (
          <Banner tone="warning" title="Your changes are being verified" text="You cannot go online or take new jobs until NexHome approves your profile again." />
        ) : null}

        <View style={{ borderRadius: 20, padding: 18, backgroundColor: availability === 'ONLINE' ? colors.green : availability === 'BUSY' ? colors.blue : colors.navy, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
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
              trackColor={{ true: '#7fd3a8', false: '#55708f' }}
              thumbColor={colors.white}
            />
          ) : null}
        </View>
        <ErrorBox message={error} />

        <View style={{ flexDirection: 'row', gap: 10 }}>
          <Stat icon="star" label={rating.data?.total ? `${rating.data.total} reviews` : 'No reviews yet'} value={rating.data?.averageRating ? Number(rating.data.averageRating).toFixed(1) : '—'} />
          <Stat icon="check-circle" label="Jobs done" value={stats.data?.completed ?? '—'} />
          <Stat icon="trending-up" label="Earned" value={stats.data ? money(stats.data.totalEarnings ?? 0) : '—'} />
        </View>

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

        <Card onPress={() => router.push('/support')} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
            <Feather name="life-buoy" size={18} color={colors.blue} />
          </View>
          <View style={{ flex: 1 }}>
            <Text weight="bold">Need help?</Text>
            <Text style={{ fontSize: 12, color: colors.muted }}>Call, WhatsApp or raise a ticket</Text>
          </View>
          <Feather name="chevron-right" size={18} color={colors.muted} />
        </Card>
      </ScrollView>
    </SafeAreaView>
  );
}
