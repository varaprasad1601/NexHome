import Feather from '@expo/vector-icons/Feather';
import { useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Image, Linking, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { fileUrl } from '../../api/client';
import { Button, ErrorBox, Header, Text } from '../../components/ui';
import { useAuth } from '../../context/AuthProvider';
import { colors } from '../../theme';

/**
 * One document's file (from My documents: View document). The file opens through a short-lived link
 * (GET /api/files/download-url/{fileId}): a photo is shown here, scaled to fit; a PDF opens in the phone's viewer.
 */
export default function DocumentView() {
  const { fileId, title, contentType } = useLocalSearchParams();
  const { authRequest } = useAuth();
  const [url, setUrl] = useState(null);
  const [error, setError] = useState('');
  const isImage = String(contentType ?? '').startsWith('image/');

  useEffect(() => {
    authRequest(`/api/files/download-url/${fileId}`)
      .then((link) => setUrl(fileUrl(link.url)))
      .catch((err) => setError(err.message));
  }, [authRequest, fileId]);

  const openOutside = () => Linking.openURL(url).catch(() => setError('The file could not be opened on this phone.'));

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title={title || 'Document'} />
      <View style={{ flex: 1, padding: 16, gap: 12 }}>
        <ErrorBox message={error} />
        {!url && !error ? <ActivityIndicator color={colors.blue} style={{ marginTop: 40 }} /> : null}
        {url && isImage ? (
          <View style={{ flex: 1, borderRadius: 16, overflow: 'hidden', backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }}>
            <Image
              source={{ uri: url }}
              resizeMode="contain"
              style={{ flex: 1 }}
              accessibilityLabel={title || 'Document'}
              onError={() => setError('The photo could not be loaded. Try again.')}
            />
          </View>
        ) : null}
        {url && !isImage ? (
          <View style={{ alignItems: 'center', gap: 10, paddingVertical: 40 }}>
            <Feather name="file-text" size={44} color={colors.blue} />
            <Text style={{ color: colors.muted, textAlign: 'center' }}>This document is a PDF. It opens in your phone’s viewer.</Text>
          </View>
        ) : null}
        {url ? <Button title={isImage ? 'Open outside the app' : 'Open PDF'} variant={isImage ? 'secondary' : 'blue'} icon="external-link" onPress={openOutside} /> : null}
      </View>
    </SafeAreaView>
  );
}
