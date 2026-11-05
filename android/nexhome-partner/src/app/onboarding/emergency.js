import { useState } from 'react';

import StepScreen from '../../components/StepScreen';
import { Choices, Field } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useLookup from '../../hooks/useLookup';
import { saveProfile } from '../../utils/onboarding';

/** Step 6: the emergency contact's name, relationship (EMERGENCY_CONTACT_RELATIONSHIP) and number. */
export default function Emergency() {
  const { authRequest, user, profile, reloadProfile } = useAuth();
  const relationships = useLookup('EMERGENCY_CONTACT_RELATIONSHIP');
  const [name, setName] = useState(profile?.emergencyContactName ?? '');
  const [relationship, setRelationship] = useState(profile?.emergencyContactRelationship ?? null);
  const [number, setNumber] = useState(profile?.emergencyContactNumber ?? '');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const onSave = async () => {
    const found = {};
    const digits = number.replace(/\D/g, '');
    if (!name.trim()) found.name = 'Enter their name';
    if (!relationship) found.relationship = 'Choose how you know them';
    if (!/^[6-9]\d{9}$/.test(digits)) found.number = 'Enter a 10-digit mobile number';
    else if (digits === user?.mobileNumber) found.number = 'Use someone else’s number, not yours';
    setErrors(found);
    if (Object.keys(found).length) return false;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, { emergencyContactName: name.trim(), emergencyContactRelationship: relationship, emergencyContactNumber: digits });
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
    <StepScreen step="emergency" onSave={onSave} busy={busy} error={error}>
      <Field label="Name" value={name} onChangeText={setName} error={errors.name} autoCapitalize="words" maxLength={120} />
      <Choices label="Relationship" options={relationships.options} value={relationship} onChange={setRelationship} error={errors.relationship} />
      <Field label="Mobile number" value={number} onChangeText={setNumber} error={errors.number} keyboardType="phone-pad" maxLength={10} />
    </StepScreen>
  );
}
