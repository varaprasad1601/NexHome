import { useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/**
 * The display names of lookup codes, from the public GET /api/lookups/values/{typeCode}: { values, label(type, code) }.
 * `values[type]` is that type's active values in display order (for dropdowns); `label` falls back to the code while
 * loading or for a code the lookup does not have.
 *
 * @param types lookup type codes, e.g. ['SKILL_CATEGORY', 'PAYMENT_BASIS']; pass a constant array
 */
export default function useLookupLabels(types) {
  const { authRequest } = useAdminAuth();
  const [values, setValues] = useState({});

  useEffect(() => {
    let cancelled = false;
    types.forEach((type) =>
      authRequest(`/api/lookups/values/${type}`)
        .then((list) => {
          if (!cancelled) setValues((current) => ({ ...current, [type]: list }));
        })
        .catch(() => {})
    );
    return () => {
      cancelled = true;
    };
  }, [authRequest, types]);

  const label = (type, code) => (code ? (values[type]?.find((value) => value.valueCode === code)?.displayName ?? code) : null);
  return { values, label };
}
