import { router } from 'expo-router';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Button, Card, Header, Row, SectionTitle, Text } from '../components/ui';
import WeeklyGrid from '../components/WeeklyGrid';
import { useAuth } from '../context/AuthProvider';
import useLookup from '../hooks/useLookup';
import { colors } from '../theme';

/**
 * Working hours and areas, read only: the weekly grid (AVAILABILITY_SLOT), weekends, service areas and travel
 * distance from the profile. After submitting, partners change these only through support; leave goes in Time off.
 */
export default function Availability() {
  const { profile } = useAuth();
  const weekends = useLookup('WEEKEND_AVAILABILITY');
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Working hours & areas" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
        <Card style={{ gap: 12 }}>
          <SectionTitle>Weekly hours</SectionTitle>
          <WeeklyGrid value={profile?.weeklyAvailability} />
          <View style={{ flexDirection: 'row', gap: 14 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <View style={{ width: 14, height: 14, borderRadius: 4, backgroundColor: colors.green }} />
              <Text style={{ fontSize: 12, color: colors.muted }}>Working</Text>
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <View style={{ width: 14, height: 14, borderRadius: 4, backgroundColor: colors.line }} />
              <Text style={{ fontSize: 12, color: colors.muted }}>Off</Text>
            </View>
          </View>
        </Card>
        <Card style={{ gap: 10 }}>
          <SectionTitle>Coverage</SectionTitle>
          <Row label="Weekends" value={weekends.labelOf(profile?.weekendAvailability) || '—'} />
          <Row label="Travels up to" value={profile?.maxTravelKm !== null && profile?.maxTravelKm !== undefined ? `${profile.maxTravelKm} km` : '—'} />
          <Text style={{ color: colors.muted, fontSize: 13.5 }}>Areas</Text>
          <Text weight="semibold">{(profile?.serviceAreas ?? []).join(', ') || '—'}</Text>
        </Card>
        <Banner tone="info" text="To change your weekly hours or areas, contact NexHome support. For a day off, add time off." />
        <Button title="Add time off" variant="blue" icon="sun" onPress={() => router.push('/time-off')} />
      </ScrollView>
    </SafeAreaView>
  );
}
