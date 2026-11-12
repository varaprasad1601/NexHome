import Feather from '@expo/vector-icons/Feather';
import { useState } from 'react';
import { Pressable, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Button, Card, Choices, ErrorBox, Field, FormScroll, Header, SectionTitle, Text } from '../components/ui';
import WeeklyGrid from '../components/WeeklyGrid';
import { useAuth } from '../context/AuthProvider';
import useLookup from '../hooks/useLookup';
import { colors, fonts } from '../theme';
import { saveProfile, weekendProblem } from '../utils/onboarding';

/**
 * Working hours and areas (Profile): shown read only; **Change working hours** makes them editable (the areas they
 * work in, how far they travel, weekends and the weekly grid of AVAILABILITY_SLOT boxes, checked against the
 * weekend choice as in onboarding) with Save and Cancel. Save sends only these fields (PUT
 * /api/partners/profiles/update), which the backend lets a partner change after submitting too, without a new
 * review: the onboarding status stays as it is.
 */
export default function Availability() {
  const { authRequest, profile, reloadProfile } = useAuth();
  const weekends = useLookup('WEEKEND_AVAILABILITY');
  const [areas, setAreas] = useState(profile?.serviceAreas ?? []);
  const [area, setArea] = useState('');
  const [km, setKm] = useState(profile?.maxTravelKm !== null && profile?.maxTravelKm !== undefined ? String(profile.maxTravelKm) : '');
  const [weekend, setWeekend] = useState(profile?.weekendAvailability ?? null);
  const [weekly, setWeekly] = useState(profile?.weeklyAvailability ?? {});
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const blocked = profile?.onboardingStatus === 'REJECTED';
  const locked = !editing;

  // Back to the saved values (Cancel, or before editing again)
  const resetForm = () => {
    setAreas(profile?.serviceAreas ?? []);
    setArea('');
    setKm(profile?.maxTravelKm !== null && profile?.maxTravelKm !== undefined ? String(profile.maxTravelKm) : '');
    setWeekend(profile?.weekendAvailability ?? null);
    setWeekly(profile?.weeklyAvailability ?? {});
    setErrors({});
    setError('');
  };

  const addArea = () => {
    const value = area.trim().replace(/\s+/g, ' ');
    if (!value) return;
    if (!areas.some((a) => a.toLowerCase() === value.toLowerCase())) setAreas([...areas, value]);
    setArea('');
  };

  const save = async () => {
    const found = {};
    const pending = area.trim() ? [...areas, area.trim()] : areas;
    if (!pending.length) found.areas = 'Add at least one area you work in';
    const distance = km.trim() === '' ? null : Number(km);
    if (distance === null || Number.isNaN(distance) || distance < 0 || distance > 999.9) found.km = 'Enter a distance from 0 to 999.9 km';
    if (!weekend) found.weekend = 'Choose your weekend availability';
    if (!Object.keys(weekly).length) found.weekly = 'Tick at least one slot you can work';
    else if (weekend && weekendProblem(weekend, weekly)) found.weekly = weekendProblem(weekend, weekly);
    setErrors(found);
    setNotice('');
    if (Object.keys(found).length) return;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, {
        serviceAreas: pending,
        maxTravelKm: Math.round(distance * 10) / 10,
        weekendAvailability: weekend,
        weeklyAvailability: weekly
      });
      setAreas(pending);
      setArea('');
      await reloadProfile();
      setEditing(false);
      setNotice('Your working hours and areas are saved.');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Working hours & areas" />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 32 }}>
        {notice ? <Banner tone="success" text={notice} /> : null}
        <ErrorBox message={error} />
        <Card style={{ gap: 12 }}>
          <SectionTitle>Areas you work in</SectionTitle>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {areas.map((a) => (
              <View key={a} style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: colors.blueSoft, borderRadius: 999, paddingLeft: 14 }}>
                <Text weight="bold" style={{ color: colors.blue, fontSize: 13 }}>
                  {a}
                </Text>
                {editing ? (
                  <Pressable accessibilityRole="button" accessibilityLabel={`Remove ${a}`} onPress={() => setAreas(areas.filter((x) => x !== a))} style={{ width: 40, height: 40, alignItems: 'center', justifyContent: 'center' }}>
                    <Feather name="x" size={15} color={colors.blue} />
                  </Pressable>
                ) : (
                  <View style={{ width: 14, height: 34 }} />
                )}
              </View>
            ))}
          </View>
          {editing ? (
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <TextInput
              value={area}
              onChangeText={setArea}
              onSubmitEditing={addArea}
              editable={!locked}
              placeholder="e.g. Nuzvid town, Hanuman Junction"
              placeholderTextColor="#8a9aad"
              accessibilityLabel="Area to add"
              maxLength={100}
              style={{ flex: 1, minHeight: 48, borderWidth: 1, borderColor: errors.areas ? colors.red : colors.line, borderRadius: 12, paddingHorizontal: 14, fontFamily: fonts.regular, fontSize: 15, color: colors.ink, backgroundColor: colors.white }}
            />
            <Pressable accessibilityRole="button" accessibilityLabel="Add area" disabled={locked} onPress={addArea} style={{ width: 48, height: 48, borderRadius: 12, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="plus" size={20} color={colors.white} />
            </Pressable>
          </View>
          ) : null}
          {errors.areas ? <Text style={{ color: colors.red, fontSize: 12 }}>{errors.areas}</Text> : null}
          <Field label="How far you travel (km)" value={km} onChangeText={(t) => setKm(t.replace(/[^\d.]/g, ''))} error={errors.km} keyboardType="decimal-pad" maxLength={5} editable={!locked} />
        </Card>
        <Card style={{ gap: 12 }}>
          <SectionTitle>When you can work</SectionTitle>
          <Choices label="Weekends" options={weekends.options} value={weekend} onChange={locked ? () => {} : setWeekend} error={errors.weekend} />
          <Text style={{ color: colors.muted, fontSize: 12.5 }}>Tick every slot you are free to take jobs. 12 PM - 1 PM is lunch.</Text>
          <WeeklyGrid value={weekly} onChange={locked ? undefined : setWeekly} />
          {errors.weekly ? <Text style={{ color: colors.red, fontSize: 12 }}>{errors.weekly}</Text> : null}
        </Card>
        {blocked ? <Banner tone="warning" text="Your profile was not approved, so these cannot be changed. Contact NexHome support." /> : null}
        {editing ? (
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <Button
              title="Cancel"
              variant="secondary"
              disabled={busy}
              onPress={() => {
                resetForm();
                setEditing(false);
              }}
              style={{ flex: 1 }}
            />
            <Button title="Save" variant="blue" icon="check" loading={busy} disabled={busy} onPress={save} style={{ flex: 1 }} />
          </View>
        ) : (
          <Button
            title="Change working hours"
            variant="blue"
            icon="edit-3"
            disabled={blocked}
            onPress={() => {
              resetForm();
              setNotice('');
              setEditing(true);
            }}
          />
        )}
      </FormScroll>
    </SafeAreaView>
  );
}
