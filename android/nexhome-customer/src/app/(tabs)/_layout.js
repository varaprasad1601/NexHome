import Feather from '@expo/vector-icons/Feather';
import { Redirect, Tabs, router } from 'expo-router';
import { useEffect } from 'react';

import { useAuth } from '../../context/AuthProvider';
import { colors, fonts } from '../../theme';

/** The four tabs of design A: Home, Bookings, Support, Profile. New policy versions to accept open first. */
export default function TabsLayout() {
  const { signedIn, authRequest } = useAuth();

  useEffect(() => {
    if (!signedIn) return;
    authRequest('/api/policies/pending')
      .then((pending) => {
        if (pending?.length) router.push('/policies');
      })
      .catch(() => {});
  }, [signedIn, authRequest]);

  if (!signedIn) return <Redirect href="/login" />;
  const icon = (name) =>
    function TabIcon({ color }) {
      return <Feather name={name} size={22} color={color} />;
    };
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.blue,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: { height: 68, paddingTop: 6, paddingBottom: 10, borderTopColor: colors.line, backgroundColor: colors.white },
        tabBarLabelStyle: { fontFamily: fonts.bold, fontSize: 11 }
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Home', tabBarIcon: icon('home') }} />
      <Tabs.Screen name="bookings" options={{ title: 'Bookings', tabBarIcon: icon('calendar') }} />
      <Tabs.Screen name="support" options={{ title: 'Support', tabBarIcon: icon('message-circle') }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile', tabBarIcon: icon('user') }} />
    </Tabs>
  );
}
