import { Link } from 'react-router-dom';

import { useSiteState } from '../../context/siteState';

/**
 * An in-app link. Like every navigation on the site, following it also resets the professional registration card
 * to its first step.
 */
export default function AppLink({ onClick, ...props }) {
  const { resetWorkerRegistration } = useSiteState();

  return (
    <Link
      {...props}
      onClick={(e) => {
        if (onClick) onClick(e);
        resetWorkerRegistration();
      }}
    />
  );
}
