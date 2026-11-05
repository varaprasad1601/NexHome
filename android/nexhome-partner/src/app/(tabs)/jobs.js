import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import JobCard from '../../components/JobCard';
import { Empty, ErrorBox, Loading, Tabs, Text } from '../../components/ui';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';
import { UPCOMING } from '../../utils/status';

/** My jobs (GET /api/bookings/partner/me): Upcoming (still to do) and Completed (done or cancelled). */
export default function Jobs() {
  const [tab, setTab] = useState('upcoming');
  const jobs = useApi('/api/bookings/partner/me?size=100');
  const all = jobs.data?.content ?? [];
  const list = all.filter((b) => (tab === 'upcoming') === UPCOMING.includes(b.bookingStatus));

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <View style={{ paddingHorizontal: 20, paddingTop: 16, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 }}>
        <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
          My jobs
        </Text>
        <Tabs
          tabs={[
            ['upcoming', 'Upcoming'],
            ['completed', 'Completed']
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
              icon="briefcase"
              title={tab === 'upcoming' ? 'No upcoming jobs' : 'No completed jobs yet'}
              text={tab === 'upcoming' ? 'Jobs you accept show here. Stay online to get offers.' : undefined}
            />
          }
          renderItem={({ item }) => <JobCard job={item} />}
        />
      )}
    </SafeAreaView>
  );
}
