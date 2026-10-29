import { useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/**
 * An activity log (a list of NexHomeAuditEntry) from `path`, loaded each time the path is given: pages pass it only
 * while their Activity Logs tab is open, so the log is fresh after edits. { entries, loading }.
 */
export default function useActivity(path) {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ path: null, entries: [] });

  useEffect(() => {
    if (!path) return undefined;
    let cancelled = false;
    authRequest(path)
      .then((entries) => !cancelled && setState({ path, entries }))
      .catch(() => !cancelled && setState({ path, entries: [] }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, path]);

  return { entries: state.entries, loading: Boolean(path) && state.path !== path };
}
