import Feather from '@expo/vector-icons/Feather';
import * as DocumentPicker from 'expo-document-picker';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Pressable, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { uploadFile } from '../../api/client';
import { Banner, Button, Choices, ErrorBox, Field, FormScroll, Header, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import useLookup from '../../hooks/useLookup';
import { colors } from '../../theme';
import { isIsoDate, todayInIndia } from '../../utils/format';

const MAX_MB = 10;

/**
 * Add a document (POST /api/partners/documents/add) or change one (`documentId`; PUT /documents/update): a
 * government ID with its ID type (GOVERNMENT_ID_TYPE), or a skill certificate with its name; the number, an
 * optional expiry date, and the scan or photo (JPEG, PNG or PDF up to 10 MB), uploaded first (POST /api/files/upload,
 * purpose PARTNER_DOCUMENT) and sent as `fileId` (required when adding). An approved partner is warned that a change
 * sends the profile for verification again (UPDATE_REQUESTED).
 */
export default function DocumentForm() {
  const { kind, documentId } = useLocalSearchParams();
  const { authRequest, profile, reloadProfile } = useAuth();
  const idTypes = useLookup('GOVERNMENT_ID_TYPE');
  const isId = kind === 'GOVERNMENT_ID';
  const [form, setForm] = useState({ idType: null, documentName: '', documentNumber: '', expiryDate: '' });
  const [existing, setExisting] = useState(null);
  const [file, setFile] = useState(null);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const approved = profile?.onboardingStatus === 'APPROVED';
  const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));

  useEffect(() => {
    if (!documentId || !profile) return;
    authRequest(`/api/partners/profiles/documents/${profile.partnerProfileId}`)
      .then((list) => {
        const doc = list.find((d) => String(d.partnerDocumentId) === String(documentId));
        if (!doc) return;
        setExisting(doc);
        setForm({ idType: doc.idType, documentName: doc.documentName ?? '', documentNumber: '', expiryDate: doc.expiryDate ?? '' });
      })
      .catch((err) => setError(err.message));
  }, [documentId, profile, authRequest]);

  const pick = async () => {
    setError('');
    const result = await DocumentPicker.getDocumentAsync({ type: ['image/jpeg', 'image/png', 'application/pdf'], copyToCacheDirectory: true });
    if (result.canceled || !result.assets?.length) return;
    const asset = result.assets[0];
    if (asset.size && asset.size > MAX_MB * 1024 * 1024) {
      setError(`The file is larger than ${MAX_MB} MB. Pick a smaller one.`);
      return;
    }
    setFile(asset);
  };

  const save = async () => {
    const found = {};
    if (isId && !form.idType) found.idType = 'Choose the ID';
    if (!isId && !form.documentName.trim()) found.documentName = 'Enter the certificate’s name';
    const number = form.documentNumber.trim();
    if (number && !/^[A-Za-z0-9][A-Za-z0-9 /-]{2,28}[A-Za-z0-9]$/.test(number)) found.documentNumber = '4 to 30 letters, digits, spaces, / or -';
    if (isId && !number && !existing) found.documentNumber = 'Enter the ID number';
    if (form.expiryDate && (!isIsoDate(form.expiryDate) || form.expiryDate < todayInIndia())) found.expiryDate = 'Enter a date from today on, as YYYY-MM-DD';
    if (!file && !existing) found.file = 'Add a photo or scan of the document';
    setErrors(found);
    if (Object.keys(found).length) return;

    const proceed = async () => {
      setError('');
      setBusy(true);
      try {
        const uploaded = file ? await uploadFile(authRequest, file, 'PARTNER_DOCUMENT') : null;
        const body = {
          documentNumber: number || undefined,
          expiryDate: form.expiryDate || undefined,
          ...(isId ? { idType: form.idType } : { documentName: form.documentName.trim() }),
          ...(uploaded ? { fileId: uploaded.fileId } : {})
        };
        if (existing) {
          await authRequest('/api/partners/documents/update', { method: 'PUT', body: { partnerDocumentId: existing.partnerDocumentId, ...body } });
        } else {
          await authRequest('/api/partners/documents/add', { method: 'POST', body: { partnerProfileId: profile.partnerProfileId, documentType: kind, ...body } });
        }
        await reloadProfile();
        router.back();
      } catch (err) {
        setError(err.message);
      } finally {
        setBusy(false);
      }
    };

    if (approved) {
      Alert.alert('Send for verification?', 'Your profile will be verified again, and you cannot take jobs until NexHome approves the change.', [
        { text: 'Not now', style: 'cancel' },
        { text: 'Save', onPress: proceed }
      ]);
    } else {
      proceed();
    }
  };

  const remove = () => {
    Alert.alert('Delete this document?', undefined, [
      { text: 'Keep', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          setBusy(true);
          try {
            await authRequest(`/api/partners/documents/delete/${existing.partnerDocumentId}`, { method: 'DELETE' });
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
      <Header title={`${existing ? 'Edit' : 'Add'} ${isId ? 'government ID' : 'skill certificate'}`} />
      <FormScroll contentContainerStyle={{ padding: 16, gap: 14 }}>
          {approved ? <Banner tone="warning" title="Your profile is approved" text="Adding or changing a document sends your profile for verification again. You cannot take jobs until it is approved." /> : null}
          {isId ? (
            <Choices label="ID" options={idTypes.options} value={form.idType} onChange={set('idType')} error={errors.idType} />
          ) : (
            <Field label="Certificate name" value={form.documentName} onChangeText={set('documentName')} error={errors.documentName} placeholder="e.g. ITI Electrician" maxLength={150} />
          )}
          <Field
            label={existing ? `Number (saved: ${existing.documentNumber ?? 'none'}; type it only to change it)` : isId ? 'ID number' : 'Certificate number (optional)'}
            value={form.documentNumber}
            onChangeText={set('documentNumber')}
            error={errors.documentNumber}
            autoCapitalize="characters"
            maxLength={30}
          />
          <Field label="Valid till (optional, YYYY-MM-DD)" value={form.expiryDate} onChangeText={set('expiryDate')} error={errors.expiryDate} placeholder="2030-12-31" keyboardType="numbers-and-punctuation" maxLength={10} />
          <Text weight="bold" style={{ fontSize: 13 }}>
            Photo or scan
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={file || existing?.fileName ? 'Change the file' : 'Pick a file'}
            onPress={pick}
            style={{ borderWidth: 1, borderStyle: 'dashed', borderColor: errors.file ? colors.red : '#b8c6d4', borderRadius: 14, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.white, marginTop: -6 }}
          >
            <Feather name={file || existing?.fileName ? 'file' : 'upload'} size={22} color={colors.blue} />
            <View style={{ flex: 1 }}>
              <Text weight="bold" numberOfLines={1}>
                {file?.name ?? existing?.fileName ?? 'Pick a photo or PDF'}
              </Text>
              <Text style={{ fontSize: 12, color: colors.muted }}>{file || existing?.fileName ? 'Tap to change' : `JPEG, PNG or PDF, up to ${MAX_MB} MB`}</Text>
            </View>
          </Pressable>
          {errors.file ? <Text style={{ color: colors.red, fontSize: 12, marginTop: -8 }}>{errors.file}</Text> : null}
          <ErrorBox message={error} />
          <Button title="Save document" onPress={save} loading={busy} />
          {existing && existing.verificationStatus !== 'VERIFIED' ? <Button title="Delete document" variant="danger" onPress={remove} disabled={busy} /> : null}
      </FormScroll>
    </SafeAreaView>
  );
}
