import Feather from '@expo/vector-icons/Feather';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Header, Loading, Text } from '../components/ui';
import { useAuth } from '../context/AuthProvider';
import useApi from '../hooks/useApi';
import useLookup from '../hooks/useLookup';
import { colors } from '../theme';
import { money } from '../utils/format';

/** One labelled value; a dash when empty. */
function Item({ label, value }) {
  const empty = value === null || value === undefined || value === '' || (Array.isArray(value) && !value.length);
  return (
    <View style={{ flexDirection: 'row', gap: 12, paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: colors.line }}>
      <Text style={{ width: 128, fontSize: 13, color: colors.muted }}>{label}</Text>
      <Text weight="semibold" style={{ flex: 1, fontSize: 13.5, textAlign: 'right' }}>
        {empty ? '—' : Array.isArray(value) ? value.join(', ') : String(value)}
      </Text>
    </View>
  );
}

function Section({ icon, title, children }) {
  return (
    <View style={{ backgroundColor: colors.white, borderRadius: 18, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 16, paddingTop: 14, paddingBottom: 6 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <View style={{ width: 30, height: 30, borderRadius: 10, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
          <Feather name={icon} size={15} color={colors.blue} />
        </View>
        <Text weight="extrabold" style={{ fontSize: 15, color: colors.navy }}>
          {title}
        </Text>
      </View>
      {children}
    </View>
  );
}

/**
 * My details (Profile > My details): everything on the partner's profile, read only: personal, contact, address, work
 * and emergency contact. Changes go through onboarding while editable, else NexHome support.
 */
export default function Details() {
  const { user, profile } = useAuth();
  const categories = useApi('/api/catalog/categories');
  const language = useLookup('PREFERRED_CONTACT_LANGUAGE');
  const skill = useLookup('SKILL_CATEGORY');
  const arrangement = useLookup('PREFERRED_ARRANGEMENT');
  const basis = useLookup('PAYMENT_BASIS');
  const relationship = useLookup('EMERGENCY_CONTACT_RELATIONSHIP');
  if (!profile) return <Loading />;

  const p = profile;
  const category = (categories.data ?? []).find((c) => c.serviceCategoryId === p.mainServiceCategoryId)?.name;
  const amount = (value) => (value === null || value === undefined ? null : money(value));
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.ground }}>
      <Header title="My details" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 32 }}>
        <Section icon="user" title="Personal">
          <Item label="Partner ID" value={p.publicId} />
          <Item label="Name" value={[p.firstName, p.middleName, p.lastName].filter(Boolean).join(' ')} />
          <Item label="Aadhaar" value={p.aadhaarNumber} />
          <Item label="18 or older" value={p.isAdult === null || p.isAdult === undefined ? null : p.isAdult ? 'Yes' : 'No'} />
          <Item label="Language" value={p.preferredContactLanguage ? language.labelOf(p.preferredContactLanguage) : null} />
        </Section>

        <Section icon="phone" title="Contact">
          <Item label="Mobile" value={user ? `${user.countryCode} ${user.mobileNumber}` : null} />
          <Item label="Email" value={user?.email} />
          <Item label="Alternate number" value={p.alternateContactNumber} />
        </Section>

        <Section icon="map-pin" title="Address">
          <Item label="Address" value={[p.addressLine1, p.addressLine2].filter(Boolean).join(', ')} />
          <Item label="Landmark" value={p.landmark} />
          <Item label="Locality" value={p.locality} />
          <Item label="City" value={p.city} />
          <Item label="State" value={p.state} />
          <Item label="PIN code" value={p.postalCode} />
        </Section>

        <Section icon="briefcase" title="Work">
          <Item label="Skill category" value={p.skillCategory ? skill.labelOf(p.skillCategory) : null} />
          <Item label="Experience" value={p.yearsExperience === null || p.yearsExperience === undefined ? null : `${p.yearsExperience} years`} />
          <Item label="Main service" value={category} />
          <Item label="Other services" value={p.additionalSkills} />
          <Item label="Arrangement" value={p.preferredArrangement ? arrangement.labelOf(p.preferredArrangement) : null} />
          <Item label="Payment basis" value={p.paymentBasis ? basis.labelOf(p.paymentBasis) : null} />
          <Item label="Expected earnings" value={amount(p.expectedEarningsInr)} />
          <Item label="Visit charge" value={amount(p.visitChargeInr)} />
          <Item label="Minimum per job" value={amount(p.minimumJobPaymentInr)} />
          <Item label="Previous work" value={p.previousWorkDetails} />
        </Section>

        <Section icon="navigation" title="Service area">
          <Item label="Areas" value={p.serviceAreas} />
          <Item label="Travels up to" value={p.maxTravelKm === null || p.maxTravelKm === undefined ? null : `${p.maxTravelKm} km`} />
        </Section>

        <Section icon="heart" title="Emergency contact">
          <Item label="Name" value={p.emergencyContactName} />
          <Item label="Relationship" value={p.emergencyContactRelationship ? relationship.labelOf(p.emergencyContactRelationship) : null} />
          <Item label="Number" value={p.emergencyContactNumber} />
        </Section>

        <Text style={{ fontSize: 12, color: colors.muted, textAlign: 'center', lineHeight: 18 }}>
          To change your details once your profile is submitted, contact NexHome support.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}
