import { Fragment } from 'react';

import AppLink from './AppLink';

/**
 * The banner at the top of every inner page: breadcrumb, eyebrow, title (children) and description.
 *
 * @param crumbs links between Home and the current page, e.g. [{ label: 'Services', to: '/services' }]
 * @param current the current page's name, last in the breadcrumb
 */
export default function PageHeader({
  crumbs = [],
  current,
  eyebrow,
  eyebrowClassName = 'text-[#0B438B]',
  description,
  descriptionClassName = 'text-base sm:text-lg',
  children
}) {
  return (
    <div className="bg-gradient-to-r from-[#edf6fd] via-[#f7fbfd] to-[#f0f8f4] border-b border-[#dcebf8] py-8 sm:py-10">
      <div className="container max-w-6xl">
        {/* Breadcrumb */}
        <div className="text-xs text-gray-500 font-semibold mb-4 flex items-center gap-2">
          <AppLink to="/" className="text-[#0B438B] hover:underline">
            Home
          </AppLink>
          <span>/</span>
          {crumbs.map((crumb) => (
            <Fragment key={crumb.to}>
              <AppLink to={crumb.to} className="text-[#0B438B] hover:underline">
                {crumb.label}
              </AppLink>
              <span>/</span>
            </Fragment>
          ))}
          <span className="text-gray-800">{current}</span>
        </div>

        {/* Header Title Section */}
        <div>
          <span className={`text-xs font-bold ${eyebrowClassName} uppercase tracking-wider block mb-2`}>
            {eyebrow}
          </span>
          <h1 className="text-4xl sm:text-5xl font-black text-[#0B315F] tracking-tight mb-3 leading-tight">
            {children}
          </h1>
          <p className={`${descriptionClassName} text-gray-600 max-w-2xl font-normal leading-relaxed m-0`}>
            {description}
          </p>
        </div>
      </div>
    </div>
  );
}
