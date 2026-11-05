import { router } from 'expo-router';
import { useState } from 'react';

import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorBox, Field, FormScroll, Header, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors } from '../theme';

const NAME = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;

/**
 * Name (PUT /api/profiles/customers/update) and email (PUT /api/users/update; "" removes it). The mobile number
 * changes only through support for now.
 */
export default function EditProfile() {
  const { authRequest, user, profile, reloadProfile } = useAuth();
  const [firstName, setFirstName] = useState(profile?.firstName ?? '');
  const [lastName, setLastName] = useState(profile?.lastName ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    setError('');
    if (!NAME.test(firstName) || !NAME.test(lastName)) {
      setError('Enter your first and last name (letters only).');
      return;
    }
    if (email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Enter a valid email address, or leave it empty.');
      return;
    }
    setBusy(true);
    try {
      await authRequest('/api/profiles/customers/update', {
        method: 'PUT',
        body: { customerProfileId: profile.customerProfileId, firstName: firstName.trim(), lastName: lastName.trim() }
      });
      if ((user?.email ?? '') !== email.trim()) {
        await authRequest('/api/users/update', { method: 'PUT', body: { userId: user.userId, email: email.trim() } });
      }
      await reloadProfile();
      router.back();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Edit profile" />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14 }}>
        <Field label="First name" value={firstName} onChangeText={setFirstName} autoCapitalize="words" maxLength={50} />
        <Field label="Last name" value={lastName} onChangeText={setLastName} autoCapitalize="words" maxLength={50} />
        <Field label="Email (optional)" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" />
        <Text style={{ color: colors.muted, fontSize: 12.5 }}>
          Mobile number: {user?.countryCode} {user?.mobileNumber}. To change it, contact support.
        </Text>
        <ErrorBox message={error} />
        <Button title="Save" onPress={save} loading={busy} />
      </FormScroll>
    </SafeAreaView>
  );
}
