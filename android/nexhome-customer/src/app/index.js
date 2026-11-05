import { Redirect } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandSplash, Button, ErrorBox, Text } from '../components/ui';
import { isCustomer, useAuth } from '../context/AuthProvider';
import { colors } from '../theme';

const STAFF_ROLES = ['SUPER_USER', 'ADMIN', 'SUB_ADMIN'];

/** True when the account holds an active staff role (they may add a customer account to their own number). */
function isStaff(user) {
  return Boolean(user?.roles?.some((role) => STAFF_ROLES.includes(role.roleCode) && role.isActive));
}

/** Where the app opens: log in, add a customer account (staff), finish the profile, or the tabs. */
export default function Gate() {
  const { ready, signedIn, user, profile, signOut } = useAuth();
  if (!ready) return <BrandSplash />;
  if (!signedIn) return <Redirect href="/login" />;
  if (!isCustomer(user)) return isStaff(user) ? <JoinAsCustomer /> : <NotACustomer onSignOut={signOut} />;
  if (!profile?.firstName) return <Redirect href="/complete-profile" />;
  return <Redirect href="/(tabs)" />;
}

/**
 * A staff number (admin, sub-admin, super user) without a customer account: sign up as a customer with the same
 * number (POST /api/users/me/customer/join), then fill in the name as any new customer.
 */
function JoinAsCustomer() {
  const { authRequest, reloadProfile, signOut } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const join = async () => {
    setBusy(true);
    setError('');
    try {
      await authRequest('/api/users/me/customer/join', { method: 'POST' });
      // The Gate then sends the new customer to complete the profile
      await reloadProfile();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, padding: 24, justifyContent: 'center', gap: 16, backgroundColor: colors.ground }}>
      <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
        Create your customer account
      </Text>
      <Text style={{ color: colors.muted, lineHeight: 21 }}>
        This number belongs to a NexHome staff account, but has no customer account yet. Sign up as a customer to book services with the
        same number; your staff access does not change.
      </Text>
      <ErrorBox message={error} />
      <View style={{ gap: 10 }}>
        <Button title="Sign up as a customer" onPress={join} loading={busy} />
        <Button title="Use another number" variant="secondary" onPress={signOut} disabled={busy} />
      </View>
    </SafeAreaView>
  );
}

function NotACustomer({ onSignOut }) {
  return (
    <SafeAreaView style={{ flex: 1, padding: 24, justifyContent: 'center', gap: 16, backgroundColor: colors.ground }}>
      <Text weight="extrabold" style={{ fontSize: 22, color: colors.navy }}>
        This number is not a customer account
      </Text>
      <Text style={{ color: colors.muted, lineHeight: 21 }}>
        It is registered for the NexHome partner app. Please use that app, or contact NexHome support to add a customer account to this
        number.
      </Text>
      <View>
        <Button title="Use another number" onPress={onSignOut} />
      </View>
    </SafeAreaView>
  );
}
