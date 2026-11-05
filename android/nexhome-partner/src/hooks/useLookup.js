import { useEffect, useState } from 'react';

import { lookupValues } from '../api/client';

/**
 * The active values of a lookup type (public, cached) as options { value, label, metadata }, plus `labelOf(code)`.
 * Empty until loaded or when the server cannot be reached.
 */
export default function useLookup(typeCode) {
  const [options, setOptions] = useState([]);
  useEffect(() => {
    let cancelled = false;
    lookupValues(typeCode)
      .then((values) => {
        if (!cancelled) setOptions((values ?? []).map((v) => ({ value: v.valueCode, label: v.displayName, metadata: v.metadata ?? {} })));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [typeCode]);
  const labelOf = (code) => options.find((o) => o.value === code)?.label ?? code ?? '';
  return { options, labelOf };
}
