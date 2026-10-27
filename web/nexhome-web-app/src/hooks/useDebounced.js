import { useEffect, useState } from 'react';

/** The value once it has stopped changing for `delayMs` (e.g. a search box's text, passed on once typing pauses). */
export default function useDebounced(value, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
