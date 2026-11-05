import Feather from '@expo/vector-icons/Feather';
import { useState } from 'react';
import { Pressable, TextInput, View } from 'react-native';

import StepScreen from '../../components/StepScreen';
import { Choices, Field, Text } from '../../components/ui';
import WeeklyGrid from '../../components/WeeklyGrid';
import { useAuth } from '../../context/AuthProvider';
import useLookup from '../../hooks/useLookup';
import { colors, fonts } from '../../theme';
import { saveProfile, weekendProblem } from '../../utils/onboarding';

/**
 * Step 5: service areas (a list of place names), the farthest the partner travels (km), weekend availability
 * (WEEKEND_AVAILABILITY) and the weekly grid of AVAILABILITY_SLOT boxes, which must agree with the weekend choice.
 */
export default function Coverage() {
  const { authRequest, profile, reloadProfile } = useAuth();
  const weekends = useLookup('WEEKEND_AVAILABILITY');
  const [areas, setAreas] = useState(profile?.serviceAreas ?? []);
  const [area, setArea] = useState('');
  const [km, setKm] = useState(profile?.maxTravelKm !== null && profile?.maxTravelKm !== undefined ? String(profile.maxTravelKm) : '');
  const [weekend, setWeekend] = useState(profile?.weekendAvailability ?? null);
  const [weekly, setWeekly] = useState(profile?.weeklyAvailability ?? {});
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const addArea = () => {
    const value = area.trim().replace(/\s+/g, ' ');
    if (!value) return;
    if (!areas.some((a) => a.toLowerCase() === value.toLowerCase())) setAreas([...areas, value]);
    setArea('');
  };

  const onSave = async () => {
    const found = {};
    const pending = area.trim() ? [...areas, area.trim()] : areas;
    if (!pending.length) found.areas = 'Add at least one area you work in';
    const distance = km.trim() === '' ? null : Number(km);
    if (distance === null || Number.isNaN(distance) || distance < 0 || distance > 999.9) found.km = 'Enter a distance from 0 to 999.9 km';
    if (!weekend) found.weekend = 'Choose your weekend availability';
    if (!Object.keys(weekly).length) found.weekly = 'Tick at least one slot you can work';
    else if (weekend && weekendProblem(weekend, weekly)) found.weekly = weekendProblem(weekend, weekly);
    setErrors(found);
    if (Object.keys(found).length) return false;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, {
        serviceAreas: pending,
        maxTravelKm: Math.round(distance * 10) / 10,
        weekendAvailability: weekend,
        weeklyAvailability: weekly
      });
      await reloadProfile();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  };

  return (
    <StepScreen step="coverage" onSave={onSave} busy={busy} error={error}>
      <Text weight="bold" style={{ fontSize: 13 }}>
        Areas you work in
      </Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: -6 }}>
        {areas.map((a) => (
          <View key={a} style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: colors.blueSoft, borderRadius: 999, paddingLeft: 14 }}>
            <Text weight="bold" style={{ color: colors.blue, fontSize: 13 }}>
              {a}
            </Text>
            <Pressable accessibilityRole="button" accessibilityLabel={`Remove ${a}`} onPress={() => setAreas(areas.filter((x) => x !== a))} style={{ width: 40, height: 40, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="x" size={15} color={colors.blue} />
            </Pressable>
          </View>
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <TextInput
          value={area}
          onChangeText={setArea}
          onSubmitEditing={addArea}
          placeholder="e.g. Nuzvid town, Hanuman Junction"
          placeholderTextColor="#8a9aad"
          accessibilityLabel="Area to add"
          maxLength={100}
          style={{ flex: 1, minHeight: 48, borderWidth: 1, borderColor: errors.areas ? colors.red : colors.line, borderRadius: 12, paddingHorizontal: 14, fontFamily: fonts.regular, fontSize: 15, color: colors.ink, backgroundColor: colors.white }}
        />
        <Pressable accessibilityRole="button" accessibilityLabel="Add area" onPress={addArea} style={{ width: 48, height: 48, borderRadius: 12, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' }}>
          <Feather name="plus" size={20} color={colors.white} />
        </Pressable>
      </View>
      {errors.areas ? <Text style={{ color: colors.red, fontSize: 12, marginTop: -8 }}>{errors.areas}</Text> : null}
      <Field label="How far you travel (km)" value={km} onChangeText={(t) => setKm(t.replace(/[^\d.]/g, ''))} error={errors.km} keyboardType="decimal-pad" maxLength={5} />
      <Choices label="Weekends" options={weekends.options} value={weekend} onChange={setWeekend} error={errors.weekend} />
      <Text weight="bold" style={{ fontSize: 13 }}>
        When you can work
      </Text>
      <Text style={{ color: colors.muted, fontSize: 12.5, marginTop: -8 }}>Tick every slot you are free to take jobs. 12 PM - 1 PM is lunch.</Text>
      <WeeklyGrid value={weekly} onChange={setWeekly} />
      {errors.weekly ? <Text style={{ color: colors.red, fontSize: 12 }}>{errors.weekly}</Text> : null}
    </StepScreen>
  );
}
