import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';

/**
 * Loads `path` (signed in) whenever the screen comes into focus, so lists are fresh after going back.
 * Returns { data, loading, error, reload, refreshing, refresh }; `refresh` is for pull-to-refresh. A null path loads
 * nothing. With `pollMs` it also loads again every `pollMs` while the screen is focused and the app is in the
 * foreground, and as soon as the app comes back to the foreground (new notifications, offers).
 */
export default function useApi(path, { pollMs } = {}) {
  const { authRequest } = useAuth();
  const [state, setState] = useState({ data: null, loading: Boolean(path), error: '' });
  const [refreshing, setRefreshing] = useState(false);
  const pathRef = useRef(path);
  pathRef.current = path;

  const load = useCallback(async () => {
    if (!pathRef.current) return;
    try {
      const data = await authRequest(pathRef.current);
      setState({ data, loading: false, error: '' });
    } catch (err) {
      setState((current) => ({ data: current.data, loading: false, error: err.message }));
    }
  }, [authRequest]);

  useFocusEffect(
    useCallback(() => {
      load();
      if (!pollMs) return undefined;
      const timer = setInterval(() => {
        if (AppState.currentState === 'active') load();
      }, pollMs);
      const subscription = AppState.addEventListener('change', (next) => {
        if (next === 'active') load();
      });
      return () => {
        clearInterval(timer);
        subscription.remove();
      };
    }, [load, path, pollMs]) // eslint-disable-line react-hooks/exhaustive-deps
  );

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  return { ...state, reload: load, refreshing, refresh };
}
