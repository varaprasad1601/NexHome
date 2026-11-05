import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Image, Keyboard, KeyboardAvoidingView, Platform, ScrollView, TextInput, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError, apiRequest } from '../api/client';
import { BrandName, Button, ErrorBox, Text } from '../components/ui';
import { POLICY_KEYS } from '../constants/policies';
import { colors, fonts } from '../theme';

/**
 * Log in or sign up with the mobile number (+91). A known number gets a LOGIN code; an unknown one is offered sign-up,
 * which creates the partner account (with its NOT_STARTED partner profile) and sends a REGISTRATION code. Both continue on the code screen.
 */
export default function Login() {
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [signingUp, setSigningUp] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const insets = useSafeAreaInsets();
  const scrollRef = useRef(null);
  const [keyboardOpen, setKeyboardOpen] = useState(false);

  // While the keyboard is open the brand area shrinks and the sheet scrolls up above the keyboard
  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', () => {
      setKeyboardOpen(true);
      setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 50);
    });
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', () => setKeyboardOpen(false));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  const digits = mobile.replace(/\D/g, '');
  const valid = /^[6-9]\d{9}$/.test(digits);

  const goToCode = (purpose) => router.push({ pathname: '/otp', params: { mobile: digits, purpose } });

  const submit = async () => {
    setError('');
    if (!valid) {
      setError('Enter your 10-digit mobile number.');
      return;
    }
    setBusy(true);
    try {
      if (signingUp) {
        await apiRequest('/api/auth/register', {
          method: 'POST',
          body: { countryCode: '+91', mobileNumber: digits, email: email.trim() || null, roleCode: 'PARTNER' }
        });
        goToCode('REGISTRATION');
      } else {
        await apiRequest('/api/auth/otp/request', {
          method: 'POST',
          body: { countryCode: '+91', mobileNumber: digits, purpose: 'LOGIN' }
        });
        goToCode('LOGIN');
      }
    } catch (err) {
      if (!signingUp && err instanceof ApiError && err.status === 404) {
        setSigningUp(true);
        setError('');
      } else {
        setError(err.message);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView edges={['top']} style={{ flex: 1, backgroundColor: colors.ground }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior="padding">
        <ScrollView ref={scrollRef} contentContainerStyle={{ flexGrow: 1 }} keyboardShouldPersistTaps="handled" bounces={false}>
          {/* The brand in the middle of the free space */}
          <View style={{ flex: 1, minHeight: keyboardOpen ? 120 : 240, alignItems: 'center', justifyContent: 'center', gap: keyboardOpen ? 6 : 12, padding: keyboardOpen ? 12 : 24 }}>
            <Image source={require('../../assets/logo.png')} style={keyboardOpen ? { width: 72, height: 50 } : { width: 104, height: 72 }} resizeMode="contain" accessibilityLabel="NexHome logo" />
            <BrandName size={keyboardOpen ? 28 : 36} tag="Partner" />
            {keyboardOpen ? null : (
              <Text weight="semibold" style={{ color: colors.muted, fontSize: 13, textAlign: 'center', maxWidth: 240, lineHeight: 19 }}>
              Find work near you in Nuzvid & nearby areas. Get paid for every job.
            </Text>
            )}
          </View>

          {/* Every field in the bottom sheet */}
          <View
            style={{
              backgroundColor: colors.white,
              borderTopLeftRadius: 26,
              borderTopRightRadius: 26,
              paddingHorizontal: 20,
              paddingTop: 12,
              paddingBottom: keyboardOpen ? 16 : 24 + insets.bottom,
              gap: 14,
              shadowColor: colors.navy,
              shadowOpacity: 0.08,
              shadowRadius: 20,
              shadowOffset: { width: 0, height: -6 },
              elevation: 12
            }}
          >
            <View style={{ alignSelf: 'center', width: 40, height: 4, borderRadius: 4, backgroundColor: colors.line, marginBottom: 4 }} />
            <View style={{ gap: 4 }}>
              <Text weight="extrabold" style={{ fontSize: 20, color: colors.navy }}>
                {signingUp ? 'Join as a NexHome partner' : 'Log in or sign up'}
              </Text>
              <Text style={{ color: colors.muted, fontSize: 13, lineHeight: 19 }}>
                {signingUp ? 'This number is new to NexHome. Sign up as a partner and we will send a code to confirm it.' : 'We will send a 6-digit code to your number.'}
              </Text>
            </View>

            <View style={{ gap: 6 }}>
              <Text weight="bold" style={{ fontSize: 13 }}>
                Mobile number
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 12 }}>
                <Text weight="bold" style={{ paddingHorizontal: 14, color: colors.navy, fontSize: 15 }}>
                  +91
                </Text>
                <TextInput
                  value={mobile}
                  onChangeText={(text) => {
                    setMobile(text);
                    if (signingUp) setSigningUp(false);
                  }}
                  keyboardType="phone-pad"
                  maxLength={10}
                  placeholder="Enter mobile number"
                  placeholderTextColor="#8a9aad"
                  accessibilityLabel="Mobile number"
                  style={{ flex: 1, minHeight: 50, fontFamily: fonts.semibold, fontSize: 16, color: colors.ink, borderLeftWidth: 1, borderLeftColor: colors.line, paddingHorizontal: 14 }}
                />
              </View>
            </View>

            {signingUp ? (
              <View style={{ gap: 6 }}>
                <Text weight="bold" style={{ fontSize: 13 }}>
                  Email (optional)
                </Text>
                <TextInput
                  value={email}
                  onChangeText={setEmail}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  placeholder="you@example.com"
                  placeholderTextColor="#8a9aad"
                  accessibilityLabel="Email"
                  style={{ minHeight: 50, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 12, paddingHorizontal: 14, fontFamily: fonts.regular, fontSize: 15, color: colors.ink }}
                />
              </View>
            ) : null}

            <ErrorBox message={error} />
            <Button title={signingUp ? 'Sign up and get code' : 'Get code'} onPress={submit} loading={busy} disabled={!valid} />
            <Text style={{ color: colors.muted, fontSize: 11, textAlign: 'center' }}>
              By continuing you agree to NexHome’s{' '}
              <Text
                weight="bold"
                accessibilityRole="link"
                style={{ fontSize: 11, color: colors.blue }}
                onPress={() => router.push(`/legal?key=${signingUp ? POLICY_KEYS.REGISTRATION_TERMS : POLICY_KEYS.LOGIN_TERMS}`)}
              >
                Terms
              </Text>
              {' & '}
              <Text
                weight="bold"
                accessibilityRole="link"
                style={{ fontSize: 11, color: colors.blue }}
                onPress={() => router.push(`/legal?key=${signingUp ? POLICY_KEYS.REGISTRATION_POLICY : POLICY_KEYS.LOGIN_POLICY}`)}
              >
                Privacy Policy
              </Text>
              .
            </Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
