import Feather from '@expo/vector-icons/Feather';
import { View } from 'react-native';

import { colors } from '../theme';
import { Text } from './ui';

/** A job's four steps as the partner sees them, with their icons (as the customer app's booking steps). */
const STEPS = [
  ['user-check', 'Assigned'],
  ['thumbs-up', 'Accepted'],
  ['tool', 'Working'],
  ['check-circle', 'Completed']
];

/**
 * A job's progress as four steps with icons and labels (Assigned, Accepted, Working, Completed): done steps and the
 * line between them green, the rest grey. `step` 1 to 4 (utils/status: bookingStatus(code).step).
 */
export default function Progress({ step }) {
  const reached = Math.max(0, Math.min(step ?? 0, STEPS.length));
  return (
    <View accessibilityLabel={`${STEPS[reached - 1]?.[1] ?? 'Not started'}, step ${reached} of 4`} style={{ flexDirection: 'row' }}>
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
