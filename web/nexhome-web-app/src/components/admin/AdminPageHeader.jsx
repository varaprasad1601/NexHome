import { ChevronRight, House } from 'lucide-react';
import { Fragment } from 'react';
import { Link } from 'react-router-dom';

/**
 * The top of every admin screen: breadcrumb, then an optional title and description, and actions on the right.
 *
 * @param crumbs the trail after Home, last one the current screen, e.g. [{ label: 'User Management' },
 *   { label: 'Admins' }]; a crumb with `to` (and not last) is a link
 */
export default function AdminPageHeader({ crumbs, title, description, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <nav aria-label="Breadcrumb" className={title ? 'mb-2' : ''}>
          <ol className="flex flex-wrap items-center gap-1.5 text-[0.8125rem] text-[var(--muted)]">
            <li>
              <Link to="/admin/dashboard" className="flex items-center gap-1.5 hover:text-[var(--blue)]">
                <House size={13} strokeWidth={2.2} aria-hidden="true" />
                Home
              </Link>
            </li>
            {crumbs.map((crumb, i) => {
              const last = i === crumbs.length - 1;
              return (
                <Fragment key={crumb.label}>
                  <li aria-hidden="true">
                    <ChevronRight size={12} strokeWidth={2.4} className="text-[#A8B6C6]" />
                  </li>
                  <li>
                    {last ? (
                      <span className="font-bold text-[var(--blue)]" aria-current="page">
                        {crumb.label}
                      </span>
                    ) : crumb.to ? (
                      <Link to={crumb.to} className="hover:text-[var(--blue)]">
                        {crumb.label}
                      </Link>
                    ) : (
                      <span>{crumb.label}</span>
                    )}
                  </li>
                </Fragment>
              );
            })}
          </ol>
        </nav>
        {title && <h1 className="mb-1 text-[1.75rem] font-black leading-tight text-[var(--navy)]">{title}</h1>}
        {description && <div className="text-[0.9375rem] text-[var(--muted)]">{description}</div>}
      </div>
      {actions}
    </div>
  );
}
