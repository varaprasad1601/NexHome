import { useNavigation } from 'expo-router';
import { useEffect, useRef } from 'react';

/**
 * For the bottom tabs' screens: opening the tab from another tab (its tab button) puts the screen back to its defaults
 * with `reset` (its first sub-tab; useApi already reloads its data on focus). Coming back to it from a screen opened on
 * top of it (e.g. a payout or a job, then Back) keeps it as it was. Tapping the tab while it is already open does both:
 * `reset` and `reload` (fresh data). Pass `reload` = a function that reloads the screen's data.
 */
export default function useTabReset(reset, reload) {
  const navigation = useNavigation();
  const resetRef = useRef(reset);
  const reloadRef = useRef(reload);
  // The latest functions, for the listener below (kept up to date after each render)
  useEffect(() => {
    resetRef.current = reset;
    reloadRef.current = reload;
  });

  useEffect(
    () =>
      navigation.addListener('tabPress', () => {
        // Switching to this tab: back to its defaults; tapping it while open: also fresh data
        const alreadyOpen = navigation.isFocused();
        resetRef.current?.();
        if (alreadyOpen) reloadRef.current?.();
      }),
    [navigation]
  );
}
