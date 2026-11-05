import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Switch, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorBox, Field, FormScroll, Header, Pick, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { colors } from '../../theme';

const TYPES = [
  { code: 'HOME', label: 'Home' },
  { code: 'WORK', label: 'Work' },
  { code: 'OTHER', label: 'Other' }
];
const EMPTY = { contactName: '', mobileNumber: '', addressType: 'HOME', label: '', addressLine1: '', addressLine2: '', landmark: '', city: 'Nuzvid', state: 'Andhra Pradesh', postalCode: '', isDefault: true };

/**
 * Add an address (POST /api/addresses/create) or edit one (`addressId`; PUT /api/addresses/update, an emptied
 * optional field sent as "" to clear it), with Delete for an existing one (refused while a booking uses it).
 */
export default function AddressForm() {
  const { addressId } = useLocalSearchParams();
  const { authRequest, user, profile } = useAuth();
  const [form, setForm] = useState({
    ...EMPTY,
    contactName: [profile?.firstName, profile?.lastName].filter(Boolean).join(' '),
    mobileNumber: user?.mobileNumber ?? ''
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!addressId) return;
    authRequest(`/api/addresses/${addressId}`)
      .then((a) =>
        setForm({
          contactName: a.contactName ?? '',
          mobileNumber: a.mobileNumber ?? '',
          addressType: a.addressType ?? 'HOME',
          label: a.label ?? '',
          addressLine1: a.addressLine1 ?? '',
          addressLine2: a.addressLine2 ?? '',
          landmark: a.landmark ?? '',
          city: a.city ?? '',
          state: a.state ?? '',
          postalCode: a.postalCode ?? '',
          isDefault: a.isDefault
        })
      )
      .catch((err) => setError(err.message));
  }, [addressId, authRequest]);

  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  const save = async () => {
    const found = {};
    if (!form.contactName.trim()) found.contactName = 'Contact name is required';
    if (!/^\d{10}$/.test(form.mobileNumber.replace(/\D/g, ''))) found.mobileNumber = 'Enter a 10-digit mobile number';
    if (!form.addressLine1.trim()) found.addressLine1 = 'House / flat and street are required';
    if (!form.city.trim()) found.city = 'City is required';
    if (!form.state.trim()) found.state = 'State is required';
    if (!/^[1-9]\d{5}$/.test(form.postalCode)) found.postalCode = 'Enter the 6-digit PIN code';
    setErrors(found);
    if (Object.keys(found).length) return;
    setError('');
    setBusy(true);
    const body = {
      contactName: form.contactName.trim(),
      mobileNumber: form.mobileNumber.replace(/\D/g, ''),
      addressType: form.addressType,
      label: form.label.trim(),
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim(),
      landmark: form.landmark.trim(),
      city: form.city.trim(),
      state: form.state.trim(),
      postalCode: form.postalCode,
      isDefault: form.isDefault
    };
    try {
      if (addressId) {
        await authRequest('/api/addresses/update', { method: 'PUT', body: { addressId: Number(addressId), ...body } });
      } else {
        // A new address leaves out empty optional fields
        const created = Object.fromEntries(Object.entries(body).filter(([, v]) => v !== ''));
        await authRequest('/api/addresses/create', { method: 'POST', body: { userId: user.userId, countryCode: 'IN', ...created } });
      }
      router.back();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setError('');
    setBusy(true);
    try {
      await authRequest(`/api/addresses/delete/${addressId}`, { method: 'DELETE' });
      router.back();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={addressId ? 'Edit address' : 'Add an address'} />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14 }}>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {TYPES.map((type) => (
              <Pick key={type.code} selected={form.addressType === type.code} onPress={() => set('addressType')(type.code)} style={{ flex: 1, alignItems: 'center' }}>
                <Text weight="bold" style={{ color: form.addressType === type.code ? colors.blue : colors.ink }}>
                  {type.label}
                </Text>
              </Pick>
            ))}
          </View>
          <Field label="Label (optional)" value={form.label} onChangeText={set('label')} placeholder="e.g. Home, Parents' house" maxLength={50} />
          <Field label="House / flat, street" value={form.addressLine1} onChangeText={set('addressLine1')} error={errors.addressLine1} maxLength={200} />
          <Field label="Area (optional)" value={form.addressLine2} onChangeText={set('addressLine2')} maxLength={200} />
          <Field label="Landmark (optional)" value={form.landmark} onChangeText={set('landmark')} maxLength={150} />
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <Field label="City" value={form.city} onChangeText={set('city')} error={errors.city} style={{ flex: 1 }} />
            <Field label="PIN code" value={form.postalCode} onChangeText={set('postalCode')} error={errors.postalCode} keyboardType="number-pad" maxLength={6} style={{ flex: 1 }} />
          </View>
          <Field label="State" value={form.state} onChangeText={set('state')} error={errors.state} />
          <Field label="Contact name" value={form.contactName} onChangeText={set('contactName')} error={errors.contactName} />
          <Field label="Contact mobile" value={form.mobileNumber} onChangeText={set('mobileNumber')} error={errors.mobileNumber} keyboardType="phone-pad" maxLength={10} />
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text weight="bold">Use as my default address</Text>
            <Switch value={form.isDefault} onValueChange={set('isDefault')} trackColor={{ true: colors.green }} />
          </View>
          <ErrorBox message={error} />
          <Button title="Save address" onPress={save} loading={busy} />
          {addressId ? <Button title="Delete address" variant="danger" onPress={remove} disabled={busy} /> : null}
      </FormScroll>
    </SafeAreaView>
  );
}
