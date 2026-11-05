import { router } from 'expo-router';
import { useState } from 'react';

import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorBox, Field, FormScroll, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';

const NAME = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;

/** A new customer's name (the customer profile exists empty since sign-up): PUT /api/profiles/customers/update. */
export default function CompleteProfile() {
  const { authRequest, profile, reloadProfile, signOut } = useAuth();
  const [firstName, setFirstName] = useState(profile?.firstName ?? '');
  const [lastName, setLastName] = useState(profile?.lastName ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    setError('');
    if (!NAME.test(firstName) || !NAME.test(lastName)) {
      setError('Enter your first and last name (letters only).');
      return;
    }
    setBusy(true);
    try {
      await authRequest('/api/profiles/customers/update', {
        method: 'PUT',
        body: { customerProfileId: profile.customerProfileId, firstName: firstName.trim(), lastName: lastName.trim() }
      });
      await reloadProfile();
      router.replace('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <FormScroll contentContainerStyle={{ padding: 24, gap: 18 }}>
        <Text weight="extrabold" style={{ fontSize: 24, color: colors.navy, marginTop: 20 }}>
          Welcome to NexHome
        </Text>
        <Text style={{ color: colors.muted }}>Tell us your name so our professionals know who to ask for.</Text>
        <Field label="First name" value={firstName} onChangeText={setFirstName} autoCapitalize="words" maxLength={50} />
        <Field label="Last name" value={lastName} onChangeText={setLastName} autoCapitalize="words" maxLength={50} />
        <ErrorBox message={error} />
        <Button title="Continue" onPress={save} loading={busy} disabled={!profile} />
        <Text
          weight="bold"
          style={{ color: colors.muted, textAlign: 'center' }}
          onPress={async () => {
            await signOut();
            router.replace('/login');
          }}
          accessibilityRole="button"
        >
          Use another number
        </Text>
      </FormScroll>
    </SafeAreaView>
  );
}
