import { useState } from 'react';
import { View } from 'react-native';

import StepScreen from '../../components/StepScreen';
import { Field } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { saveProfile } from '../../utils/onboarding';

/** Step 2: the partner's home address (India only: a 6-digit PIN). Locality is required to submit. */
export default function Address() {
  const { authRequest, profile, reloadProfile } = useAuth();
  const [form, setForm] = useState({
    addressLine1: profile?.addressLine1 ?? '',
    addressLine2: profile?.addressLine2 ?? '',
    landmark: profile?.landmark ?? '',
    locality: profile?.locality ?? '',
    city: profile?.city ?? 'Nuzvid',
    state: profile?.state ?? 'Andhra Pradesh',
    postalCode: profile?.postalCode ?? ''
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  const onSave = async () => {
    const found = {};
    if (!form.addressLine1.trim()) found.addressLine1 = 'House / flat and street are required';
    if (!form.locality.trim()) found.locality = 'Locality is required';
    if (!form.city.trim()) found.city = 'City is required';
    if (!form.state.trim()) found.state = 'State is required';
    if (!/^[1-9]\d{5}$/.test(form.postalCode)) found.postalCode = 'Enter the 6-digit PIN code';
    setErrors(found);
    if (Object.keys(found).length) return false;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, {
        addressLine1: form.addressLine1.trim(),
        addressLine2: form.addressLine2.trim(),
        landmark: form.landmark.trim(),
        locality: form.locality.trim(),
        city: form.city.trim(),
        state: form.state.trim(),
        postalCode: form.postalCode
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
    <StepScreen step="address" onSave={onSave} busy={busy} error={error}>
      <Field label="House / flat, street" value={form.addressLine1} onChangeText={set('addressLine1')} error={errors.addressLine1} maxLength={300} />
      <Field label="Area (optional)" value={form.addressLine2} onChangeText={set('addressLine2')} maxLength={255} />
      <Field label="Landmark (optional)" value={form.landmark} onChangeText={set('landmark')} maxLength={255} />
      <Field label="Locality" value={form.locality} onChangeText={set('locality')} error={errors.locality} maxLength={100} placeholder="e.g. Gandhi Nagar" />
      <View style={{ flexDirection: 'row', gap: 10 }}>
        <Field label="City" value={form.city} onChangeText={set('city')} error={errors.city} maxLength={100} style={{ flex: 1 }} />
        <Field label="PIN code" value={form.postalCode} onChangeText={set('postalCode')} error={errors.postalCode} keyboardType="number-pad" maxLength={6} style={{ flex: 1 }} />
      </View>
      <Field label="State" value={form.state} onChangeText={set('state')} error={errors.state} maxLength={100} />
    </StepScreen>
  );
}
