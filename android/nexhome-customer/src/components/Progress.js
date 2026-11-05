import Feather from '@expo/vector-icons/Feather';
import { View } from 'react-native';

import { colors } from '../theme';
import { Text } from './ui';

/** The four steps of a booking, with their icons (Admin Sreens/customer-app-samples/booking-v2.html). */
const STEPS = [
  ['check', 'Booked'],
  ['shield', 'Confirmed'],
  ['user', 'Accepted'],
  ['star', 'Completed']
];

/** How far each booking status is. */
const STEP_OF = { PENDING: 1, CONFIRMED: 2, ASSIGNED: 2, PARTNER_EN_ROUTE: 3, IN_PROGRESS: 3, PAUSED: 3, COMPLETED: 4 };

/**
 * A booking's progress as four steps with icons and labels (Booked, Confirmed, Accepted, Completed): done steps and
 * the line between them green, the rest grey. Pass the booking `status` (or a `step` from 1 to 4).
 */
export default function Progress({ status, step }) {
  const reached = step ?? STEP_OF[status] ?? 1;
  return (
    <View accessibilityLabel={`${STEPS[reached - 1]?.[1] ?? ''}, step ${reached} of 4`} style={{ flexDirection: 'row' }}>
      {STEPS.map(([icon, label], i) => {
        const on = i < reached;
        return (
          <View key={label} style={{ flex: 1, alignItems: 'center', gap: 4 }}>
            {i < STEPS.length - 1 ? (
              <View style={{ position: 'absolute', top: 11, left: '50%', right: '-50%', height: 2, backgroundColor: i < reached - 1 ? colors.green : colors.line }} />
            ) : null}
            <View style={{ width: 24, height: 24, borderRadius: 12, backgroundColor: on ? colors.green : colors.line, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name={icon} size={12} color={colors.white} />
            </View>
            <Text weight="bold" style={{ fontSize: 10.5, color: on ? colors.green : '#8a9aad' }}>
              {label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}
