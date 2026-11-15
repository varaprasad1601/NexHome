import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { onLive } from '../utils/live';

/**
 * Loads `path` (signed in) whenever the screen comes into focus, so lists are fresh after going back.
 * Returns { data, loading, error, reload, refreshing, refresh }; `refresh` is for pull-to-refresh. A null path loads
 * nothing. With `pollMs` it also loads again every `pollMs` while the screen is focused and the app is in the
 * foreground, and as soon as the app comes back to the foreground (new notifications, offers). With `pollWhile(data)`
 * those repeats happen only while it returns true for the data loaded last (e.g. while a job is under way), so a
 * screen makes no requests while nothing can change. With `live` ('bookings', 'notifications' or a list of them) it
 * also loads again whenever `components/LiveWatcher` sees that topic change, while the screen is focused
 * (`utils/live`), so a screen follows what others do without polling on its own.
 */
export default function useApi(path, { pollMs, pollWhile, live } = {}) {
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
      const topics = live ? [].concat(live) : [];
      const unsubscribe = topics.map((topic) => onLive(topic, load));
      const stopLive = () => unsubscribe.forEach((off) => off());
      if (!pollMs) return stopLive;
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
        stopLive();
      };
    }, [load, path, pollMs, String(live ?? '')]) // eslint-disable-line react-hooks/exhaustive-deps
  );

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  return { ...state, reload: load, refreshing, refresh };
}
