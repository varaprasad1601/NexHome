import { View } from 'react-native';

import { colors } from '../theme';

/** The 4-step progress bar of design A, as the partner sees a job: assigned, accepted, working, completed. */
export default function Progress({ step }) {
  return (
    <View accessibilityLabel={`Step ${step} of 4`} style={{ flexDirection: 'row', gap: 6 }}>
      {[1, 2, 3, 4].map((n) => (
        <View key={n} style={{ flex: 1, height: 5, borderRadius: 3, backgroundColor: n <= step ? colors.green : colors.line }} />
      ))}
    </View>
  );
}
