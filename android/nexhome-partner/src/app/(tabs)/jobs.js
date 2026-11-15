import { useState } from 'react';
import { FlatList, RefreshControl, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import JobCard from '../../components/JobCard';
import { Empty, ErrorBox, Loading, Tabs, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { isoDayOf, parseApiDate, todayInIndia } from '../../utils/format';
import { UPCOMING, isPaymentPending } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

/**
 * My jobs (GET /api/bookings/partner/me): Upcoming (still to do, in sections Working now, Next and Later, each by
 * scheduled time, earliest first), Pending payment (done, not paid yet: cash to collect
 * or an online payment to come), Completed (paid) and Cancelled; the tab row scrolls sideways on narrow phones.
 */
export default function Jobs() {
  const [tab, setTab] = useState('upcoming');
  const jobs = useApi('/api/bookings/partner/me?size=100', { live: 'bookings' });
  // Opening the tab shows Upcoming again
  useTabReset(() => setTab('upcoming'), jobs.refresh);
  const all = jobs.data?.content ?? [];
  const groupOf = (job) =>
    job.bookingStatus === 'CANCELLED'
      ? 'cancelled'
      : isPaymentPending(job)
        ? 'pending'
        : UPCOMING.includes(job.bookingStatus)
          ? 'upcoming'
          : 'completed';
  const list = all.filter((job) => groupOf(job) === tab);
  // Upcoming in three sections by when the job is, each earliest first: Working now (the work under way, paused too),
  // Next ("as soon as possible" jobs without a time, and today's; a job of an earlier day not started yet also, at the
  // top) and Later (the next days). The other tabs keep the newest first.
  let rows = list;
  if (tab === 'upcoming') {
    const today = todayInIndia();
    const time = (job) => parseApiDate(job.scheduledStartAt)?.date.getTime() ?? 0;
    const sectionOf = (job) =>
      ['IN_PROGRESS', 'PAUSED'].includes(job.bookingStatus)
        ? 'working'
        : !job.scheduledStartAt || isoDayOf(job.scheduledStartAt) <= today
          ? 'today'
          : 'later';
    const sorted = [...list].sort((a, b) => time(a) - time(b));
    rows = [
      ['working', 'Working now'],
      ['today', 'Next'],
      ['later', 'Later']
    ].flatMap(([key, title]) => {
      const jobsIn = sorted.filter((job) => sectionOf(job) === key);
      return jobsIn.length ? [{ header: key, title, count: jobsIn.length }, ...jobsIn] : [];
    });
  }
  const count = (key) => all.filter((job) => groupOf(job) === key).length;

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          My jobs
        </Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          <Tabs
            tabs={[
              ['upcoming', 'Upcoming', count('upcoming')],
              ['pending', 'Pending payment', count('pending')],
              ['completed', 'Completed', count('completed')],
              ['cancelled', 'Cancelled', count('cancelled')]
            ]}
            value={tab}
            onChange={setTab}
          />
        </ScrollView>
      </View>
      {jobs.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(row) => (row.header ? `header-${row.header}` : String(row.bookingId))}
          contentContainerStyle={{ padding: 16, gap: 12 }}
          refreshControl={<RefreshControl refreshing={jobs.refreshing} onRefresh={jobs.refresh} />}
          ListHeaderComponent={<ErrorBox message={jobs.error} />}
          ListEmptyComponent={
            <Empty
              icon={tab === 'pending' ? 'check-circle' : tab === 'cancelled' ? 'x-circle' : 'briefcase'}
              title={{ upcoming: 'No upcoming jobs', pending: 'No payments pending', completed: 'No completed jobs yet', cancelled: 'No cancelled jobs' }[tab]}
              text={
                tab === 'upcoming'
                  ? 'Jobs you accept show here. Stay online to get offers.'
                  : tab === 'pending'
                    ? 'Finished jobs wait here until the cash is collected or the customer pays online.'
                    : undefined
              }
            />
          }
          renderItem={({ item }) =>
            item.header ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: item.header === rows[0]?.header ? 0 : 8 }}>
                {item.header === 'working' ? <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.green }} /> : null}
                <Text weight="extrabold" style={{ fontSize: 16, color: colors.navy }}>
                  {item.title}
                </Text>
                <Text weight="bold" style={{ fontSize: 12, color: colors.muted }}>
                  {item.count}
                </Text>
              </View>
            ) : item.header === undefined && ['IN_PROGRESS', 'PAUSED'].includes(item.bookingStatus) && tab === 'upcoming' ? (
              <View style={{ borderRadius: 20, borderWidth: 2, borderColor: colors.green }}>
                <JobCard job={item} />
              </View>
            ) : (
              <JobCard job={item} />
            )
          }
        />
      )}
    </SafeAreaView>
  );
}
