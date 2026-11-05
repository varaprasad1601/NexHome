import { router } from 'expo-router';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '../theme';
import { STEPS } from '../utils/onboarding';
import { Button, ErrorBox, FormScroll, Header, Text } from './ui';

/**
 * One onboarding step: the header, "Step n of 8" with the step bar, the form, and Save and continue (saves, then
 * opens the next step; the last one returns to the overview). `onSave` returns true when saved.
 */
export default function StepScreen({ step, children, onSave, busy, error, saveTitle, footer }) {
  const index = STEPS.findIndex((s) => s.key === step);
  const next = STEPS[index + 1];
  const save = async () => {
    const ok = await onSave();
    if (!ok) return;
    if (next) router.replace(`/onboarding/${next.key}`);
    else router.replace('/onboarding');
  };
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={STEPS[index].title} />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: 32 }}>
          <View style={{ gap: 8 }}>
            <Text weight="bold" style={{ color: colors.green, fontSize: 12, letterSpacing: 0.5 }}>
              STEP {index + 1} OF {STEPS.length}
            </Text>
            <View accessibilityLabel={`Step ${index + 1} of ${STEPS.length}`} style={{ flexDirection: 'row', gap: 4 }}>
              {STEPS.map((s, i) => (
                <View key={s.key} style={{ flex: 1, height: 5, borderRadius: 3, backgroundColor: i <= index ? colors.green : colors.line }} />
              ))}
            </View>
            <Text style={{ color: colors.muted, fontSize: 13 }}>{STEPS[index].hint}</Text>
          </View>
          {children}
          <ErrorBox message={error} />
          {onSave ? <Button title={saveTitle ?? (next ? 'Save and continue' : 'Save and finish')} onPress={save} loading={busy} /> : null}
          {footer}
          <Text weight="bold" style={{ color: colors.muted, textAlign: 'center', paddingVertical: 10 }} onPress={() => router.replace('/onboarding')} accessibilityRole="button">
            Back to all steps
          </Text>
      </FormScroll>
    </SafeAreaView>
  );
}
