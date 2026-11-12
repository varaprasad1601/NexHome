import Feather from '@expo/vector-icons/Feather';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '../theme';
import { money } from '../utils/format';
import { Text } from './ui';

/**
 * NexHome staff finished an online job before the customer paid: "Payment pending" with the amount, shown for a
 * moment where the partner was (the timer or the Payment processing screen), before the job page.
 */
export default function PaymentPendingScreen({ amount, publicId }) {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 14 }}>
      <View style={{ width: 110, height: 110, borderRadius: 55, backgroundColor: colors.amberSoft, alignItems: 'center', justifyContent: 'center' }}>
        <Feather name="clock" size={58} color={colors.amber} />
      </View>
      <Text weight="extrabold" style={{ fontSize: 22, color: colors.amber }}>
        Payment pending
      </Text>
      <Text weight="extrabold" style={{ fontSize: 40, color: colors.navy }}>
        {money(amount)}
      </Text>
      {publicId ? <Text style={{ color: colors.muted }}>{publicId}</Text> : null}
      <Text style={{ color: colors.muted, textAlign: 'center' }}>
        NexHome completed this job. The customer will pay online; you can carry on with your next job.
      </Text>
    </SafeAreaView>
  );
}
