import { useCallback, useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/**
 * A signed-in GET of an unpaged list (e.g. /api/roles): { items, loading, error, reload }. `reload` fetches again and
 * keeps the current rows on screen meanwhile.
 */
export default function useAdminList(path) {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ items: [], loading: true, error: '' });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    authRequest(path)
      .then((items) => {
        if (!cancelled) setState({ items, loading: false, error: '' });
      })
      .catch((err) => {
        if (!cancelled) setState({ items: [], loading: false, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, path, reloadKey]);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);
  return { ...state, reload };
}
