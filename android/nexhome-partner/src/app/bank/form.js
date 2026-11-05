import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Switch, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Banner, Button, ErrorBox, Field, FormScroll, Header, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { colors } from '../../theme';

const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;

/** Bank and branch of an IFSC from Razorpay's public IFSC service (optional: null when it cannot be reached). */
async function lookupIfsc(code) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch(`https://ifsc.razorpay.com/${code}`, { signal: controller.signal });
    if (!response.ok) return null;
    const data = await response.json();
    return { bank: data.BANK, branch: data.BRANCH };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Add a bank account (POST /api/partners/bank-accounts/add: holder, number typed twice, IFSC, bank and branch filled
 * from the IFSC when possible) or edit one (`accountId`; PUT /bank-accounts/update: number and IFSC are fixed, a new
 * holder name sends it back for verification). An approved partner is warned first.
 */
export default function BankForm() {
  const { accountId } = useLocalSearchParams();
  const { authRequest, profile, reloadProfile } = useAuth();
  const [form, setForm] = useState({ accountHolderName: '', accountNumber: '', confirmNumber: '', ifscCode: '', bankName: '', branchName: '', isPrimary: true });
  const [existing, setExisting] = useState(null);
  const [count, setCount] = useState(0);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const approved = profile?.onboardingStatus === 'APPROVED';
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  useEffect(() => {
    if (!profile) return;
    authRequest(`/api/partners/profiles/bank-accounts/${profile.partnerProfileId}`)
      .then((list) => {
        setCount(list.length);
        const account = accountId ? list.find((a) => String(a.partnerBankAccountId) === String(accountId)) : null;
        if (account) {
          setExisting(account);
          setForm((current) => ({ ...current, accountHolderName: account.accountHolderName ?? '', bankName: account.bankName ?? '', branchName: account.branchName ?? '', isPrimary: account.isPrimary }));
        } else if (!accountId) {
          const name = [profile.firstName, profile.lastName].filter(Boolean).join(' ');
          setForm((current) => ({ ...current, accountHolderName: current.accountHolderName || name, isPrimary: list.length === 0 }));
        }
      })
      .catch((err) => setError(err.message));
  }, [accountId, profile, authRequest]);

  const changeIfsc = async (text) => {
    const code = text.toUpperCase().replace(/[^A-Z0-9]/g, '');
    set('ifscCode')(code);
    if (IFSC.test(code)) {
      const found = await lookupIfsc(code);
      if (found) setForm((current) => (current.ifscCode === code ? { ...current, bankName: found.bank ?? current.bankName, branchName: found.branch ?? current.branchName } : current));
    }
  };

  const save = () => {
    const found = {};
    if (!form.accountHolderName.trim()) found.accountHolderName = 'Enter the name on the account';
    if (!existing) {
      if (!/^\d{9,18}$/.test(form.accountNumber)) found.accountNumber = 'Account number is 9 to 18 digits';
      else if (form.accountNumber !== form.confirmNumber) found.confirmNumber = 'The numbers do not match';
      if (!IFSC.test(form.ifscCode)) found.ifscCode = 'IFSC is 4 letters, a 0 and 6 letters or digits';
    }
    if (!form.bankName.trim()) found.bankName = 'Enter the bank';
    if (!form.branchName.trim()) found.branchName = 'Enter the branch';
    setErrors(found);
    if (Object.keys(found).length) return;

    const proceed = async () => {
      setError('');
      setBusy(true);
      try {
        if (existing) {
          await authRequest('/api/partners/bank-accounts/update', {
            method: 'PUT',
            body: {
              partnerBankAccountId: existing.partnerBankAccountId,
              ...(form.accountHolderName.trim() !== existing.accountHolderName ? { accountHolderName: form.accountHolderName.trim() } : {}),
              bankName: form.bankName.trim(),
              branchName: form.branchName.trim(),
              ...(form.isPrimary && !existing.isPrimary ? { isPrimary: true } : {})
            }
          });
        } else {
          await authRequest('/api/partners/bank-accounts/add', {
            method: 'POST',
            body: {
              partnerProfileId: profile.partnerProfileId,
              accountHolderName: form.accountHolderName.trim(),
              accountNumber: form.accountNumber,
              ifscCode: form.ifscCode,
              bankName: form.bankName.trim(),
              branchName: form.branchName.trim(),
              isPrimary: form.isPrimary
            }
          });
        }
        await reloadProfile();
        router.back();
      } catch (err) {
        setError(err.message);
      } finally {
        setBusy(false);
      }
    };

    const holderChanged = existing && form.accountHolderName.trim() !== existing.accountHolderName;
    if (approved && (!existing || holderChanged)) {
      Alert.alert('Send for verification?', 'Your profile will be verified again, and you cannot take jobs until NexHome approves the change.', [
        { text: 'Not now', style: 'cancel' },
        { text: 'Save', onPress: proceed }
      ]);
    } else {
      proceed();
    }
  };

  const remove = () => {
    Alert.alert('Remove this bank account?', undefined, [
      { text: 'Keep', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          setBusy(true);
          try {
            await authRequest(`/api/partners/bank-accounts/delete/${existing.partnerBankAccountId}`, { method: 'DELETE' });
            router.back();
          } catch (err) {
            setError(err.message);
          } finally {
            setBusy(false);
          }
        }
      }
    ]);
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={existing ? 'Edit bank account' : 'Add bank account'} />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14 }}>
          {approved ? <Banner tone="warning" title="Your profile is approved" text="A new account or a new holder name sends your profile for verification again. You cannot take jobs until it is approved." /> : null}
          <Field label="Account holder name" value={form.accountHolderName} onChangeText={set('accountHolderName')} error={errors.accountHolderName} autoCapitalize="words" maxLength={150} />
          {existing ? (
            <Text style={{ color: colors.muted, fontSize: 13 }}>
              Account ••••{existing.accountNumberLast4} · IFSC {existing.ifscCode}. To use another account, add it as a new one.
            </Text>
          ) : (
            <>
              <Field label="Account number" value={form.accountNumber} onChangeText={(t) => set('accountNumber')(t.replace(/\D/g, ''))} error={errors.accountNumber} keyboardType="number-pad" maxLength={18} secureTextEntry />
              <Field label="Account number again" value={form.confirmNumber} onChangeText={(t) => set('confirmNumber')(t.replace(/\D/g, ''))} error={errors.confirmNumber} keyboardType="number-pad" maxLength={18} />
              <Field label="IFSC" value={form.ifscCode} onChangeText={changeIfsc} error={errors.ifscCode} autoCapitalize="characters" maxLength={11} placeholder="SBIN0001234" />
            </>
          )}
          <Field label="Bank" value={form.bankName} onChangeText={set('bankName')} error={errors.bankName} maxLength={150} />
          <Field label="Branch" value={form.branchName} onChangeText={set('branchName')} error={errors.branchName} maxLength={150} />
          {count > (existing ? 1 : 0) ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 44 }}>
              <Text weight="bold">Pay my earnings to this account</Text>
              <Switch value={form.isPrimary} onValueChange={set('isPrimary')} disabled={existing?.isPrimary} trackColor={{ true: colors.green }} accessibilityLabel="Pay my earnings to this account" />
            </View>
          ) : null}
          <ErrorBox message={error} />
          <Button title="Save bank account" onPress={save} loading={busy} />
          {existing ? <Button title="Remove bank account" variant="danger" onPress={remove} disabled={busy} /> : null}
      </FormScroll>
    </SafeAreaView>
  );
}
