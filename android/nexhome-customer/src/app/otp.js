import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ScrollView, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { apiRequest } from '../api/client';
import { Button, ErrorBox, Header, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import { colors, fonts } from '../theme';

/** The 6-digit code sent to the number; verifying signs the customer in. Resend after the backend's 60 s wait. */
export default function Otp() {
  const { mobile, purpose } = useLocalSearchParams();
  const { signIn } = useAuth();
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [wait, setWait] = useState(60);

  useEffect(() => {
    if (wait <= 0) return undefined;
    const timer = setTimeout(() => setWait((current) => current - 1), 1000);
    return () => clearTimeout(timer);
  }, [wait]);

  const verify = async () => {
    setError('');
    setBusy(true);
    try {
      const result = await apiRequest('/api/auth/otp/verify', {
        method: 'POST',
        body: { countryCode: '+91', mobileNumber: mobile, purpose, otp }
      });
      await signIn(result);
      router.replace('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setError('');
    try {
      await apiRequest('/api/auth/otp/request', { method: 'POST', body: { countryCode: '+91', mobileNumber: mobile, purpose } });
      setWait(60);
    } catch (err) {
      const seconds = /(\d+) seconds?/.exec(err.message);
      if (seconds) setWait(Number(seconds[1]));
      setError(err.message);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="Verify your number" />
      <ScrollView contentContainerStyle={{ padding: 24, gap: 18 }} keyboardShouldPersistTaps="handled">
        <Text style={{ color: colors.muted, lineHeight: 21 }}>
          Enter the 6-digit code sent to <Text weight="bold">+91 {mobile}</Text>.
        </Text>
        <TextInput
          value={otp}
          onChangeText={(text) => setOtp(text.replace(/\D/g, '').slice(0, 6))}
          keyboardType="number-pad"
          maxLength={6}
          autoFocus
          accessibilityLabel="Verification code"
          placeholder="••••••"
          placeholderTextColor="#c3cfdb"
          style={{ height: 60, borderRadius: 14, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, textAlign: 'center', letterSpacing: 12, fontFamily: fonts.extrabold, fontSize: 26, color: colors.navy }}
        />
        <ErrorBox message={error} />
        <Button title="Verify and continue" onPress={verify} loading={busy} disabled={otp.length !== 6} />
        <View style={{ alignItems: 'center' }}>
          {wait > 0 ? (
            <Text style={{ color: colors.muted }}>Resend the code in {wait} s</Text>
          ) : (
            <Text weight="bold" style={{ color: colors.blue }} onPress={resend} accessibilityRole="button">
              Resend code
            </Text>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
