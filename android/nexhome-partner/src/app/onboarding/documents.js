import { useState } from 'react';

import DocumentsList from '../../components/DocumentsList';
import StepScreen from '../../components/StepScreen';
import { useAuth } from '../../context/AuthProvider';
import useApi from '../../hooks/useApi';

/** Step 7: the partner's documents (GET /api/partners/profiles/documents/{id}); at least one government ID. */
export default function Documents() {
  const { profile } = useAuth();
  const documents = useApi(`/api/partners/profiles/documents/${profile?.partnerProfileId}`);
  const [error, setError] = useState('');

  const onSave = async () => {
    if (!(documents.data ?? []).some((d) => d.documentType === 'GOVERNMENT_ID')) {
      setError('Add at least one government ID.');
      return false;
    }
    return true;
  };

  return (
    <StepScreen step="documents" onSave={onSave} error={error || documents.error} saveTitle="Continue">
      <DocumentsList documents={documents.data} canChange />
    </StepScreen>
  );
}
