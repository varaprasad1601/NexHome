import { useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/** Lookup values change rarely: one request per type serves every screen for a few minutes. */
const CACHE_MS = 5 * 60 * 1000;
const cache = new Map();

function loadValues(authRequest, type) {
  const hit = cache.get(type);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.promise;
  const promise = authRequest(`/api/lookups/values/${type}`);
  cache.set(type, { promise, at: Date.now() });
  // A failed request is not kept, so the next screen tries again
  promise.catch(() => cache.delete(type));
  return promise;
}

/**
 * The display names of lookup codes, from the public GET /api/lookups/values/{typeCode} (shared by every screen for 5
 * minutes): { values, label(type, code) }.
 * `values[type]` is that type's active values in display order (for dropdowns); `label` falls back to the code while
 * loading or for a code the lookup does not have.
 *
 * @param types lookup type codes, e.g. ['SKILL_CATEGORY', 'PAYMENT_BASIS'] (loaded once per set of codes)
 */
export default function useLookupLabels(types) {
  const { authRequest } = useAdminAuth();
  const [values, setValues] = useState({});
  // Keyed by the codes, not the array: an array written inline is new on every render and would load them again
  const key = types.join(',');

  useEffect(() => {
    let cancelled = false;
    key.split(',').filter(Boolean).forEach((type) =>
      loadValues(authRequest, type)
        .then((list) => {
          if (!cancelled) setValues((current) => ({ ...current, [type]: list }));
        })
        .catch(() => {})
    );
    return () => {
      cancelled = true;
    };
  }, [authRequest, key]);

  const label = (type, code) => (code ? (values[type]?.find((value) => value.valueCode === code)?.displayName ?? code) : null);
  return { values, label };
}
