import { createContext, useContext } from 'react';

/** State shared by several pages: the enquiry and registration forms, the open FAQ and the registration modal. */
export const SiteStateContext = createContext(null);

export function useSiteState() {
  return useContext(SiteStateContext);
}
