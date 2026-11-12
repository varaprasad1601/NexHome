import { router } from 'expo-router';
import { useEffect } from 'react';

import { Loading } from '../components/ui';

/**
 * Where the payment page returns to (nexhome://payment-result). The screen that opened the payment (utils/pay)
 * checks the order itself, so this only steps back to it.
 */
export default function PaymentResult() {
  useEffect(() => {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }, []);
  return <Loading />;
}
