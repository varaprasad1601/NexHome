import { useState } from 'react';

import StepScreen from '../../components/StepScreen';
import { Choices, Field, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';
import useLookup from '../../hooks/useLookup';
import { colors } from '../../theme';
import { saveProfile } from '../../utils/onboarding';

const toNumber = (text) => (text.trim() === '' ? null : Number(text));
const asText = (value) => (value === null || value === undefined ? '' : String(value));

/**
 * Step 3: skill category (SKILL_CATEGORY), years of experience, the main service category (/api/catalog/categories),
 * other categories (stored as additional skills, as the console does), arrangement (PREFERRED_ARRANGEMENT), payment
 * basis (PAYMENT_BASIS), expected earnings, visit charge, minimum job payment and previous work.
 */
export default function Work() {
  const { authRequest, profile, reloadProfile } = useAuth();
  const skills = useLookup('SKILL_CATEGORY');
  const arrangements = useLookup('PREFERRED_ARRANGEMENT');
  const bases = useLookup('PAYMENT_BASIS');
  const categories = useApi('/api/catalog/categories');
  const [form, setForm] = useState({
    skillCategory: profile?.skillCategory ?? null,
    yearsExperience: asText(profile?.yearsExperience),
    mainServiceCategoryId: profile?.mainServiceCategoryId ?? null,
    additionalSkills: profile?.additionalSkills ?? [],
    preferredArrangement: profile?.preferredArrangement ?? null,
    paymentBasis: profile?.paymentBasis ?? null,
    expectedEarningsInr: asText(profile?.expectedEarningsInr),
    visitChargeInr: asText(profile?.visitChargeInr),
    minimumJobPaymentInr: asText(profile?.minimumJobPaymentInr),
    previousWorkDetails: profile?.previousWorkDetails ?? ''
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  const categoryOptions = (categories.data ?? []).map((c) => ({ value: c.serviceCategoryId, label: c.name }));
  const mainName = categoryOptions.find((c) => c.value === form.mainServiceCategoryId)?.label;
  const otherOptions = categoryOptions.filter((c) => c.value !== form.mainServiceCategoryId).map((c) => ({ value: c.label, label: c.label }));
  // Skills saved earlier that are not category names stay chosen
  const extra = form.additionalSkills.filter((s) => !otherOptions.some((o) => o.value === s) && s !== mainName).map((s) => ({ value: s, label: s }));

  const money = (text) => text.replace(/[^\d.]/g, '');

  const onSave = async () => {
    const found = {};
    if (!form.skillCategory) found.skillCategory = 'Choose your skill category';
    const years = toNumber(form.yearsExperience);
    if (years === null || !Number.isInteger(years) || years < 0 || years > 60) found.yearsExperience = 'Enter whole years from 0 to 60';
    if (!form.mainServiceCategoryId) found.mainServiceCategoryId = 'Choose your main service';
    if (!form.preferredArrangement) found.preferredArrangement = 'Choose how you want to work';
    if (!form.paymentBasis) found.paymentBasis = 'Choose how you want to be paid';
    const expected = toNumber(form.expectedEarningsInr);
    if (!(expected > 0)) found.expectedEarningsInr = 'Enter the amount you expect to earn';
    const visit = toNumber(form.visitChargeInr);
    if (visit === null || Number.isNaN(visit) || visit < 0) found.visitChargeInr = 'Enter your visit charge (0 if none)';
    const minimum = toNumber(form.minimumJobPaymentInr);
    if (minimum === null || Number.isNaN(minimum) || minimum < 0) found.minimumJobPaymentInr = 'Enter your minimum job payment (0 if none)';
    setErrors(found);
    if (Object.keys(found).length) return false;
    setError('');
    setBusy(true);
    try {
      await saveProfile(authRequest, profile, {
        skillCategory: form.skillCategory,
        yearsExperience: years,
        mainServiceCategoryId: form.mainServiceCategoryId,
        additionalSkills: form.additionalSkills.filter((s) => s !== mainName),
        preferredArrangement: form.preferredArrangement,
        paymentBasis: form.paymentBasis,
        expectedEarningsInr: expected,
        visitChargeInr: visit,
        minimumJobPaymentInr: minimum,
        previousWorkDetails: form.previousWorkDetails.trim()
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
    <StepScreen step="work" onSave={onSave} busy={busy} error={error}>
      <Choices label="Skill category" options={skills.options} value={form.skillCategory} onChange={set('skillCategory')} error={errors.skillCategory} />
      <Field label="Years of experience" value={form.yearsExperience} onChangeText={(t) => set('yearsExperience')(t.replace(/\D/g, ''))} error={errors.yearsExperience} keyboardType="number-pad" maxLength={2} />
      <Choices label="Main service" options={categoryOptions} value={form.mainServiceCategoryId} onChange={set('mainServiceCategoryId')} error={errors.mainServiceCategoryId || categories.error} />
      {otherOptions.length ? (
        <Choices label="Other services you can do (optional)" options={[...otherOptions, ...extra]} value={form.additionalSkills} onChange={set('additionalSkills')} multiple />
      ) : null}
      <Choices label="How you want to work" options={arrangements.options} value={form.preferredArrangement} onChange={set('preferredArrangement')} error={errors.preferredArrangement} />
      <Choices label="Payment basis" options={bases.options} value={form.paymentBasis} onChange={set('paymentBasis')} error={errors.paymentBasis} />
      <Field label="Expected earnings (₹)" value={form.expectedEarningsInr} onChangeText={(t) => set('expectedEarningsInr')(money(t))} error={errors.expectedEarningsInr} keyboardType="decimal-pad" maxLength={11} />
      <Text style={{ color: colors.muted, fontSize: 12, marginTop: -8 }}>For the payment basis you chose, e.g. per day or per month.</Text>
      <Field label="Visit charge (₹)" value={form.visitChargeInr} onChangeText={(t) => set('visitChargeInr')(money(t))} error={errors.visitChargeInr} keyboardType="decimal-pad" maxLength={11} />
      <Field label="Minimum job payment (₹)" value={form.minimumJobPaymentInr} onChangeText={(t) => set('minimumJobPaymentInr')(money(t))} error={errors.minimumJobPaymentInr} keyboardType="decimal-pad" maxLength={11} />
      <Field label="Previous work (optional)" value={form.previousWorkDetails} onChangeText={set('previousWorkDetails')} multiline maxLength={2000} placeholder="Where you worked before, notable jobs" />
    </StepScreen>
  );
}
