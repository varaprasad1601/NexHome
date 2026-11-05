import Feather from '@expo/vector-icons/Feather';
import { useState } from 'react';
import { Pressable, View } from 'react-native';

import StepScreen from '../../components/StepScreen';
import { Button, Card, Choices, Field, SectionTitle, Tag, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import { colors } from '../../theme';

/**
 * Step 4: the services the partner offers (GET /api/partners/profiles/services/{id}). Add one from the catalogue
 * (GET /api/catalog/services?categoryId=, main category first) with the years of experience (POST
 * /api/partners/services/add; the first is primary), make another the main one (PUT /services/update), remove one
 * (DELETE /services/delete/{id}).
 */
export default function Services() {
  const { authRequest, profile } = useAuth();
  const mine = useApi(`/api/partners/profiles/services/${profile?.partnerProfileId}`);
  const categories = useApi('/api/catalog/categories');
  const [categoryId, setCategoryId] = useState(profile?.mainServiceCategoryId ?? null);
  const catalogue = useApi(categoryId ? `/api/catalog/services?categoryId=${categoryId}&size=100` : null);
  const [adding, setAdding] = useState(null);
  const [years, setYears] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const offered = mine.data ?? [];
  const offeredIds = new Set(offered.map((s) => s.serviceId));
  const available = (catalogue.data?.content ?? []).filter((s) => !offeredIds.has(s.serviceId));

  const run = async (action) => {
    setError('');
    setBusy(true);
    try {
      await action();
      await mine.reload();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  };

  const add = () => {
    const value = years.trim() === '' ? null : Number(years);
    if (value !== null && (value < 0 || value > 60)) {
      setError('Experience must be 0 to 60 years.');
      return;
    }
    run(() =>
      authRequest('/api/partners/services/add', {
        method: 'POST',
        body: { partnerProfileId: profile.partnerProfileId, serviceId: adding.serviceId, experienceYears: value }
      })
    ).then((ok) => {
      if (ok) {
        setAdding(null);
        setYears('');
      }
    });
  };

  const onSave = async () => {
    if (!offered.some((s) => s.isPrimaryService && s.isActive)) {
      setError('Add at least one service and mark your main one.');
      return false;
    }
    return true;
  };

  return (
    <StepScreen step="services" onSave={onSave} busy={busy} error={error}>
      <SectionTitle>Your services</SectionTitle>
      {offered.length === 0 ? <Text style={{ color: colors.muted }}>No services yet. Add the ones you do below.</Text> : null}
      {offered.map((s) => (
        <Card key={s.partnerServiceId} style={{ gap: 8 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ flex: 1 }}>
              <Text weight="bold">{s.serviceName}</Text>
              <Text style={{ fontSize: 12, color: colors.muted }}>
                {[s.categoryName, s.experienceYears !== null && s.experienceYears !== undefined ? `${s.experienceYears} yrs experience` : null].filter(Boolean).join(' · ')}
              </Text>
            </View>
            {s.isPrimaryService ? <Tag label="Main" fg={colors.greenDark} bg={colors.greenSoft} /> : null}
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Remove ${s.serviceName}`}
              disabled={busy}
              onPress={() => run(() => authRequest(`/api/partners/services/delete/${s.partnerServiceId}`, { method: 'DELETE' }))}
              style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}
            >
              <Feather name="trash-2" size={18} color={colors.red} />
            </Pressable>
          </View>
          {!s.isPrimaryService ? (
            <Text
              weight="bold"
              style={{ color: colors.blue, paddingVertical: 6 }}
              accessibilityRole="button"
              onPress={() => run(() => authRequest('/api/partners/services/update', { method: 'PUT', body: { partnerServiceId: s.partnerServiceId, isPrimaryService: true } }))}
            >
              Make this my main service
            </Text>
          ) : null}
        </Card>
      ))}

      <SectionTitle style={{ marginTop: 6 }}>Add a service</SectionTitle>
      <Choices options={(categories.data ?? []).map((c) => ({ value: c.serviceCategoryId, label: c.name }))} value={categoryId} onChange={(v) => { setCategoryId(v); setAdding(null); }} />
      {categoryId && !catalogue.loading && available.length === 0 ? <Text style={{ color: colors.muted }}>Nothing more to add in this category.</Text> : null}
      {available.map((service) =>
        adding?.serviceId === service.serviceId ? (
          <Card key={service.serviceId} style={{ gap: 12, borderColor: colors.blue }}>
            <Text weight="bold">{service.name}</Text>
            <Field label="Years of experience in this (optional)" value={years} onChangeText={(t) => setYears(t.replace(/\D/g, ''))} keyboardType="number-pad" maxLength={2} />
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Button title="Cancel" variant="secondary" onPress={() => setAdding(null)} style={{ flex: 1 }} />
              <Button title="Add" onPress={add} loading={busy} style={{ flex: 1 }} />
            </View>
          </Card>
        ) : (
          <Card key={service.serviceId} onPress={() => setAdding(service)} style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12 }}>
            <Text weight="semibold" style={{ flex: 1 }}>
              {service.name}
            </Text>
            <Feather name="plus-circle" size={20} color={colors.green} />
          </Card>
        )
      )}
    </StepScreen>
  );
}
