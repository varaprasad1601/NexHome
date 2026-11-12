import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';

/**
 * Loads `path` (signed in) whenever the screen comes into focus, so lists are fresh after going back.
 * Returns { data, loading, error, reload, refreshing, refresh }; `refresh` is for pull-to-refresh. A null path loads
 * nothing. With `pollMs` it also loads again every `pollMs` while the screen is focused and the app is in the
 * foreground, and as soon as the app comes back to the foreground (new notifications, offers). With `pollWhile(data)`
 * those repeats happen only while it returns true for the data loaded last (e.g. while a job is under way), so a
 * screen makes no requests while nothing can change.
 */
export default function useApi(path, { pollMs, pollWhile } = {}) {
  const { authRequest } = useAuth();
  const [state, setState] = useState({ data: null, loading: Boolean(path), error: '' });
  const [refreshing, setRefreshing] = useState(false);
  const pathRef = useRef(path);
  pathRef.current = path;
  const dataRef = useRef(null);
  const pollWhileRef = useRef(pollWhile);
  pollWhileRef.current = pollWhile;

  const load = useCallback(async () => {
    if (!pathRef.current) return;
    try {
      const data = await authRequest(pathRef.current);
      dataRef.current = data;
      setState({ data, loading: false, error: '' });
    } catch (err) {
      setState((current) => ({ data: current.data, loading: false, error: err.message }));
    }
  }, [authRequest]);

  useFocusEffect(
    useCallback(() => {
      load();
      if (!pollMs) return undefined;
      const wanted = () => !pollWhileRef.current || pollWhileRef.current(dataRef.current);
      const timer = setInterval(() => {
        if (AppState.currentState === 'active' && wanted()) load();
      }, pollMs);
      const subscription = AppState.addEventListener('change', (next) => {
        if (next === 'active' && wanted()) load();
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
