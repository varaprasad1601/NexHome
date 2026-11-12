import { router, useFocusEffect, useLocalSearchParams, useNavigation } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { BackHandler } from 'react-native';

import OnlinePaymentScreen from '../../components/OnlinePaymentScreen';
import { setPaymentLock } from '../../utils/paymentLock';

/**
 * Payment processing for an online job the customer confirmed done (V70) and has not paid yet. The partner cannot
 * leave it until it is paid or the cash is collected: there is no Back, Android's Back closes the app, and
 * `components/PaymentLock` brings the partner back here from any other screen but Help & support (also when the app
 * is opened again). Then the job page, completed.
 */
export default function AwaitPayment() {
  const { id } = useLocalSearchParams();
  const leaving = useRef(false);
  const navigation = useNavigation();

  useEffect(() => {
    setPaymentLock(id);
  }, [id]);

  // No way back (the button and gestures)
  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (event) => {
      if (!leaving.current) event.preventDefault();
    });
    return unsubscribe;
  }, [navigation]);

  // Android's Back closes the app, but only while this screen is shown: on Help & support (opened from here) Back
  // returns here
  useFocusEffect(
    useCallback(() => {
      const back = BackHandler.addEventListener('hardwareBackPress', () => {
        BackHandler.exitApp();
        return true;
      });
      return () => back.remove();
    }, [])
  );

  return (
    // Paid: Payment done; NexHome staff completing the job unpaid: Payment pending; either way then the job
    <OnlinePaymentScreen
      bookingId={Number(id)}
      onDone={() => {
        leaving.current = true;
        setPaymentLock(null);
        router.replace({ pathname: '/job/[id]', params: { id: String(id) } });
      }}
    />
  );
}
