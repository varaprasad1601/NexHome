import { useFocusEffect, useNavigation } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';

/**
 * For the bottom tabs' screens: every time the tab is opened, `reset` puts the screen back to its defaults (its first
 * sub-tab; useApi already reloads its data on focus). Tapping the tab while it is already open does both: `reset`
 * and `reload` (fresh data). Pass `reload` = a function that reloads the screen's data.
 */
export default function useTabReset(reset, reload) {
  const navigation = useNavigation();
  const resetRef = useRef(reset);
  const reloadRef = useRef(reload);
  // The latest functions, for the listeners below (kept up to date after each render)
  useEffect(() => {
    resetRef.current = reset;
    reloadRef.current = reload;
  });

  useFocusEffect(
    useCallback(() => {
      resetRef.current?.();
    }, [])
  );

  useEffect(
    () =>
      navigation.addListener('tabPress', () => {
        if (!navigation.isFocused()) return;
        resetRef.current?.();
        reloadRef.current?.();
      }),
    [navigation]
  );
}
