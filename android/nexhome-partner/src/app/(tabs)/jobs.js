import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import JobCard from '../../components/JobCard';
import { Empty, ErrorBox, Loading, Tabs, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { UPCOMING, isPaymentPending } from '../../utils/status';
import useTabReset from '../../hooks/useTabReset';

/**
 * My jobs (GET /api/bookings/partner/me): Upcoming (still to do), Pending payment (done, not paid yet: cash to collect
 * or an online payment to come) and Completed (paid, or cancelled).
 */
export default function Jobs() {
  const [tab, setTab] = useState('upcoming');
  const jobs = useApi('/api/bookings/partner/me?size=100');
  // Opening the tab shows Upcoming again
  useTabReset(() => setTab('upcoming'), jobs.refresh);
  const all = jobs.data?.content ?? [];
  const groupOf = (job) => (isPaymentPending(job) ? 'pending' : UPCOMING.includes(job.bookingStatus) ? 'upcoming' : 'completed');
  const list = all.filter((job) => groupOf(job) === tab);
  const count = (key) => all.filter((job) => groupOf(job) === key).length;

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          My jobs
        </Text>
        <Tabs
          tabs={[
            ['upcoming', 'Upcoming', count('upcoming')],
            ['pending', 'Pending payment', count('pending')],
            ['completed', 'Completed', count('completed')]
          ]}
          value={tab}
          onChange={setTab}
        />
      </View>
      {jobs.loading ? (
        <Loading />
      ) : (
        <FlatList
          data={list}
          keyExtractor={(b) => String(b.bookingId)}
          contentContainerStyle={{ padding: 16, gap: 12 }}
          refreshControl={<RefreshControl refreshing={jobs.refreshing} onRefresh={jobs.refresh} />}
          ListHeaderComponent={<ErrorBox message={jobs.error} />}
          ListEmptyComponent={
            <Empty
              icon={tab === 'pending' ? 'check-circle' : 'briefcase'}
              title={{ upcoming: 'No upcoming jobs', pending: 'No payments pending', completed: 'No completed jobs yet' }[tab]}
              text={
                tab === 'upcoming'
                  ? 'Jobs you accept show here. Stay online to get offers.'
                  : tab === 'pending'
                    ? 'Finished jobs wait here until the cash is collected or the customer pays online.'
                    : undefined
              }
            />
          }
          renderItem={({ item }) => <JobCard job={item} />}
        />
      )}
    </SafeAreaView>
  );
}
