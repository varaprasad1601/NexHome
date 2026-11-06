import Feather from '@expo/vector-icons/Feather';
import { router } from 'expo-router';
import { Image, Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandName, Header, Text } from '../components/ui';
import { colors } from '../theme';

/** The website's About Us text (web: pages/AboutPage.jsx), kept the same here. */
const PARAGRAPHS = [
  'Welcome to NexHome, a household services platform designed to deliver customers with skilled service professionals for everyday home maintenance and household needs.',
  'From routine household tasks to specialized services, NexHome aims to make finding the right assistance easier, more convenient, and more organized.',
  'We believe that maintaining a comfortable home should not be complicated. By bringing customers and service professionals together, we work toward creating a simpler and more reliable household service experience.'
];

/** About us (Profile > About us, as the customer app's): the brand, the website's About Us text, and a way to contact NexHome. */
export default function About() {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="About us" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: 32 }}>
        <View style={{ alignItems: 'center', gap: 10, paddingVertical: 12 }}>
          <Image source={require('../../assets/logo.png')} style={{ width: 84, height: 58 }} resizeMode="contain" accessibilityLabel="NexHome logo" />
          <BrandName size={30} />
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center', maxWidth: 280, lineHeight: 19 }}>
            A simpler and more organized way to connect homes and skilled professionals.
          </Text>
        </View>

        <View style={{ backgroundColor: colors.white, borderRadius: 20, borderWidth: 1, borderColor: colors.line, padding: 18, gap: 12 }}>
          {PARAGRAPHS.map((text) => (
            <Text key={text.slice(0, 20)} style={{ fontSize: 14, lineHeight: 22, color: colors.ink }}>
              {text}
            </Text>
          ))}
        </View>

        <View style={{ backgroundColor: colors.white, borderRadius: 20, borderWidth: 1, borderColor: colors.line, padding: 20, gap: 10, alignItems: 'center' }}>
          <Text weight="extrabold" style={{ fontSize: 19, color: colors.navy, textAlign: 'center', lineHeight: 25 }}>
            Household help begins with{' '}
            <Text weight="extrabold" style={{ fontSize: 19, color: colors.green }}>
              one conversation
            </Text>
            .
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, textAlign: 'center', lineHeight: 19 }}>
            Questions about jobs, payouts or your profile? Reach us during business hours in Nuzvid and nearby areas.
          </Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/support')}
            style={{ marginTop: 4, flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.blue, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 999 }}
          >
            <Text weight="bold" style={{ color: colors.white, fontSize: 14 }}>
              Contact NexHome
            </Text>
            <Feather name="arrow-right" size={16} color={colors.white} />
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
