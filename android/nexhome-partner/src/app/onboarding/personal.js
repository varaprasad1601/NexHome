import { useState } from 'react';

import StepScreen from '../../components/StepScreen';
import { Check, Choices, Field, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useLookup from '../../hooks/useLookup';
import { colors } from '../../theme';
import { saveProfile } from '../../utils/onboarding';

const NAME = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;

/**
 * Step 1: names, Aadhaar (stored encrypted and only shown masked, so it is typed again to change it), "18 or older"
 * (the backend keeps isAdult, not a date of birth), preferred language (PREFERRED_CONTACT_LANGUAGE) and an
 * alternate number.
 */
export default function Personal() {
  const { authRequest, user, profile, reloadProfile } = useAuth();
  const languages = useLookup('PREFERRED_CONTACT_LANGUAGE');
  const [form, setForm] = useState({
    firstName: profile?.firstName ?? '',
    middleName: profile?.middleName ?? '',
    lastName: profile?.lastName ?? '',
    aadhaarNumber: '',
    isAdult: profile?.isAdult === true,
    preferredContactLanguage: profile?.preferredContactLanguage ?? null,
    alternateContactNumber: profile?.alternateContactNumber ?? ''
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  const onSave = async () => {
    const found = {};
    if (!NAME.test(form.firstName)) found.firstName = 'Enter your first name (letters only)';
    if (form.middleName.trim() && !NAME.test(form.middleName)) found.middleName = 'Letters only';
    if (!NAME.test(form.lastName)) found.lastName = 'Enter your last name (letters only)';
    const aadhaar = form.aadhaarNumber.replace(/\s/g, '');
    if (aadhaar && !/^[2-9]\d{11}$/.test(aadhaar)) found.aadhaarNumber = 'Aadhaar is 12 digits and does not start with 0 or 1';
    if (!aadhaar && !profile.aadhaarNumber) found.aadhaarNumber = 'Enter your 12-digit Aadhaar number';
    if (!form.isAdult) found.isAdult = 'You must be 18 or older to work with NexHome';
    if (!form.preferredContactLanguage) found.preferredContactLanguage = 'Choose a language';
    const alternate = form.alternateContactNumber.replace(/\D/g, '');
    if (alternate && !/^[6-9]\d{9}$/.test(alternate)) found.alternateContactNumber = 'Enter a 10-digit mobile number';
    if (alternate && alternate === user?.mobileNumber) found.alternateContactNumber = 'Use a number other than your own';
    setErrors(found);
    if (Object.keys(found).length) return false;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, {
        firstName: form.firstName.trim(),
        middleName: form.middleName.trim(),
        lastName: form.lastName.trim(),
        ...(aadhaar ? { aadhaarNumber: aadhaar } : {}),
        isAdult: true,
        preferredContactLanguage: form.preferredContactLanguage,
        alternateContactNumber: alternate
      });
      await reloadProfile();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  };

  return (
    <StepScreen step="personal" onSave={onSave} busy={busy} error={error}>
      <Field label="First name" value={form.firstName} onChangeText={set('firstName')} error={errors.firstName} autoCapitalize="words" maxLength={80} />
      <Field label="Middle name (optional)" value={form.middleName} onChangeText={set('middleName')} error={errors.middleName} autoCapitalize="words" maxLength={80} />
      <Field label="Last name" value={form.lastName} onChangeText={set('lastName')} error={errors.lastName} autoCapitalize="words" maxLength={80} />
      <Field
        label={profile?.aadhaarNumber ? 'Aadhaar number (saved; type it again only to change it)' : 'Aadhaar number'}
        value={form.aadhaarNumber}
        onChangeText={(text) => set('aadhaarNumber')(text.replace(/[^\d\s]/g, ''))}
        error={errors.aadhaarNumber}
        placeholder={profile?.aadhaarNumber ?? '1234 5678 9012'}
        keyboardType="number-pad"
        maxLength={14}
      />
      <Text style={{ color: colors.muted, fontSize: 12, marginTop: -8 }}>Stored encrypted. Only the last 4 digits are ever shown.</Text>
      <Check checked={form.isAdult} onChange={set('isAdult')} label="I am 18 years or older" />
      {errors.isAdult ? <Text style={{ color: colors.red, fontSize: 12, marginTop: -8 }}>{errors.isAdult}</Text> : null}
      <Choices label="Preferred language" options={languages.options} value={form.preferredContactLanguage} onChange={set('preferredContactLanguage')} error={errors.preferredContactLanguage} />
      <Field
        label="Alternate mobile (optional)"
        value={form.alternateContactNumber}
        onChangeText={set('alternateContactNumber')}
        error={errors.alternateContactNumber}
        keyboardType="phone-pad"
        maxLength={10}
      />
    </StepScreen>
  );
}
