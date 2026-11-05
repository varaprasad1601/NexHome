import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { Linking, View } from 'react-native';

import { fileUrl } from '../api/client';
import { useAuth } from '../context/AuthProvider';
import useLookup from '../hooks/useLookup';
import { colors } from '../theme';
import { localDateLabel } from '../utils/format';
import { verificationStatus } from '../utils/status';
import { Button, Card, SectionTitle, Tag, Text } from './ui';

/**
 * The partner's documents in two groups, Government ID and Skill certificates, each with Add; a document shows its
 * ID type or name, masked number, expiry, file (View opens a short-lived link: GET /api/files/download-url/{id}),
 * status and a rejection reason; Edit unless verified (the partner cannot change a verified document).
 */
export default function DocumentsList({ documents, canChange }) {
  const { authRequest } = useAuth();
  const idTypes = useLookup('GOVERNMENT_ID_TYPE');
  const list = documents ?? [];
  const groups = [
    { kind: 'GOVERNMENT_ID', title: 'Government ID', empty: 'Add your Aadhaar, PAN, voter ID, driving licence or passport.', items: list.filter((d) => d.documentType === 'GOVERNMENT_ID') },
    { kind: 'CERTIFICATION', title: 'Skill certificates', empty: 'Optional: ITI, trade or training certificates.', items: list.filter((d) => d.documentType === 'CERTIFICATION') }
  ];
  const others = list.filter((d) => d.documentType !== 'GOVERNMENT_ID' && d.documentType !== 'CERTIFICATION');
  if (others.length) groups.push({ kind: null, title: 'Other documents', items: others });

  const view = async (doc) => {
    try {
      const link = await authRequest(`/api/files/download-url/${doc.fileId}`);
      await Linking.openURL(fileUrl(link.url));
    } catch {
      // Nothing to open
    }
  };

  return groups.map((group) => (
    <View key={group.title} style={{ gap: 10 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <SectionTitle>{group.title}</SectionTitle>
        {canChange && group.kind ? (
          <Text weight="bold" style={{ color: colors.blue, padding: 10 }} accessibilityRole="button" onPress={() => router.push({ pathname: '/documents/form', params: { kind: group.kind } })}>
            + Add
          </Text>
        ) : null}
      </View>
      {group.items.length === 0 ? <Text style={{ color: colors.muted, fontSize: 13 }}>{group.empty}</Text> : null}
      {group.items.map((doc) => {
        const status = verificationStatus(doc.verificationStatus);
        const title = doc.documentType === 'GOVERNMENT_ID' ? idTypes.labelOf(doc.idType) || 'Government ID' : doc.documentName || 'Document';
        return (
          <Card key={doc.partnerDocumentId} style={{ gap: 8 }}>
            <View style={{ flexDirection: 'row', gap: 10 }}>
              <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
                <Feather name="file-text" size={18} color={colors.blue} />
              </View>
              <View style={{ flex: 1, gap: 2 }}>
                <Text weight="bold">{title}</Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>
                  {[doc.documentNumber, doc.expiryDate ? `Valid till ${localDateLabel(doc.expiryDate)}` : null].filter(Boolean).join(' · ') || 'No number'}
                </Text>
              </View>
              <Tag label={status.label} fg={status.fg} bg={status.bg} />
            </View>
            {doc.verificationStatus === 'REJECTED' && doc.rejectionReason ? (
              <Text style={{ color: colors.red, fontSize: 12.5 }}>Rejected: {doc.rejectionReason}</Text>
            ) : null}
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {doc.fileId ? <Button title="View file" variant="secondary" icon="eye" onPress={() => view(doc)} style={{ flex: 1, minHeight: 44 }} /> : null}
              {canChange && doc.verificationStatus !== 'VERIFIED' && group.kind ? (
                <Button
                  title="Edit"
                  variant="secondary"
                  icon="edit-3"
                  onPress={() => router.push({ pathname: '/documents/form', params: { kind: doc.documentType, documentId: doc.partnerDocumentId } })}
                  style={{ flex: 1, minHeight: 44 }}
                />
              ) : null}
            </View>
          </Card>
        );
      })}
    </View>
  ));
}
