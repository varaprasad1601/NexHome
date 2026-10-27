import { Construction } from 'lucide-react';
import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import { findNavTrail } from '../../data/adminNavigation';

/** Stands in for the admin screens that are not built yet, so every sidebar link opens something. */
export default function AdminComingSoonPage() {
  const { pathname } = useLocation();
  const trail = findNavTrail(pathname);
  const known = trail.length > 0;
  const title = known ? trail[trail.length - 1].label : 'Page not found';

  useEffect(() => {
    document.title = `${title} | NexHome Admin`;
  }, [title]);

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader crumbs={known ? trail : [{ label: title }]} title={title} />
      <div className="flex flex-col items-center rounded-2xl border border-dashed border-[#C7E3FC] bg-white px-6 py-16 text-center">
        <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
          <Construction size={23} />
        </span>
        <div className="mb-1 text-lg font-black text-[var(--ink)]">This screen is coming soon</div>
        <div className="mb-5 max-w-md text-sm text-[var(--muted)]">
          {known ? `The ${title} screen has not been built yet.` : 'There is no admin screen at this address.'}
        </div>
        <Link to="/admin/dashboard" className="rounded-full bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)]">
          Back to Dashboard
        </Link>
      </div>
    </div>
  );
}
