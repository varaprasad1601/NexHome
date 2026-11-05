import { Pressable, View } from 'react-native';

import useLookup from '../hooks/useLookup';
import { colors } from '../theme';
import { DAYS, DAY_SHORT } from '../utils/onboarding';
import { Text } from './ui';

/** "9 AM - 12 PM" from a slot's metadata { start: "09:00", end: "12:00" }, else its name. */
function slotTime(option) {
  const format = (hhmm) => {
    const [h, m] = (hhmm ?? '').split(':').map(Number);
    if (Number.isNaN(h)) return '';
    const suffix = h >= 12 ? 'PM' : 'AM';
    const hour = h % 12 === 0 ? 12 : h % 12;
    return m ? `${hour}:${String(m).padStart(2, '0')} ${suffix}` : `${hour} ${suffix}`;
  };
  const { start, end } = option.metadata ?? {};
  return start && end ? `${format(start)} - ${format(end)}` : '';
}

/** The slot's own name without the times in brackets: "Morning (9 AM - 12 PM)" -> "Morning". */
const slotName = (label) => label.replace(/\s*\(.*\)\s*$/, '');

/**
 * The weekly working-hours grid: a row per day (Monday first) with a box per AVAILABILITY_SLOT. With `onChange` the
 * boxes toggle (value: { MONDAY: ["MORNING", ...] }); without it the grid is read only.
 */
export default function WeeklyGrid({ value, onChange }) {
  const slots = useLookup('AVAILABILITY_SLOT');
  const grid = value ?? {};
  const toggle = (day, code) => {
    const current = grid[day] ?? [];
    const next = current.includes(code) ? current.filter((c) => c !== code) : [...current, code];
    const order = slots.options.map((o) => o.value);
    const result = { ...grid, [day]: next.sort((a, b) => order.indexOf(a) - order.indexOf(b)) };
    if (!result[day].length) delete result[day];
    onChange(result);
  };
  return (
    <View style={{ gap: 6 }}>
      <View style={{ flexDirection: 'row', gap: 4 }}>
        <View style={{ width: 40 }} />
        {slots.options.map((slot) => (
          <View key={slot.value} style={{ flex: 1, alignItems: 'center' }}>
            <Text weight="bold" style={{ fontSize: 10.5, textAlign: 'center', color: colors.navy }} numberOfLines={2}>
              {slotName(slot.label)}
            </Text>
            <Text style={{ fontSize: 9.5, textAlign: 'center', color: colors.muted }} numberOfLines={2}>
              {slotTime(slot)}
            </Text>
          </View>
        ))}
      </View>
      {DAYS.map((day) => (
        <View key={day} style={{ flexDirection: 'row', gap: 4, alignItems: 'center' }}>
          <Text weight="bold" style={{ width: 40, fontSize: 13 }}>
            {DAY_SHORT[day]}
          </Text>
          {slots.options.map((slot) => {
            const on = (grid[day] ?? []).includes(slot.value);
            const label = `${DAY_SHORT[day]} ${slotName(slot.label)}`;
            return onChange ? (
              <Pressable
                key={slot.value}
                accessibilityRole="checkbox"
                accessibilityLabel={label}
                accessibilityState={{ checked: on }}
                onPress={() => toggle(day, slot.value)}
                style={{ flex: 1, height: 44, borderRadius: 10, borderWidth: 1, borderColor: on ? colors.green : colors.line, backgroundColor: on ? colors.greenSoft : colors.white, alignItems: 'center', justifyContent: 'center' }}
              >
                {on ? <Text weight="extrabold" style={{ color: colors.green }}>✓</Text> : null}
              </Pressable>
            ) : (
              <View
                key={slot.value}
                accessibilityLabel={`${label}: ${on ? 'working' : 'off'}`}
                style={{ flex: 1, height: 30, borderRadius: 8, backgroundColor: on ? colors.green : colors.line }}
              />
            );
          })}
        </View>
      ))}
    </View>
  );
}
