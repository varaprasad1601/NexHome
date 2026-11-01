import { useState } from 'react';

import AppLink from './AppLink';
import GradientCover from './GradientCover';
import Icon from './Icon';

/**
 * A service category card linking to its page: the category's image (the gradient stand-in when it has none or it
 * fails to load), its name and the services it offers. With `limit`, only that many services are listed, then
 * "+N more…" (the home page shows 5); without it every service is listed (the Services page).
 */
export default function ServiceCard({ category, limit }) {
  const [failed, setFailed] = useState(false);
  const shown = limit ? category.services.slice(0, limit) : category.services;
  const more = category.services.length - shown.length;
  return (
    <AppLink className="service-card-modern" to={`/services/${category.slug}`}>
      <div className="service-card-img-wrap">
        {category.imageUrl && !failed ? (
          <img src={category.imageUrl} alt={category.name} loading="lazy" onError={() => setFailed(true)} />
        ) : (
          <GradientCover />
        )}
      </div>
      <div className="service-card-title">{category.name}</div>
      <ul className="service-card-bullets">
        {shown.map((service) => (
          <li key={service.serviceId}>{service.name}</li>
        ))}
        {more > 0 && <li className="font-bold text-[#168F5B]">+{more} more…</li>}
        {category.services.length === 0 && <li>Services coming soon</li>}
      </ul>
      <div className="service-card-arrow">
        <Icon name="arrow-right" size={13} />
      </div>
    </AppLink>
  );
}

/** A grey placeholder card while the catalogue loads. */
export function ServiceCardSkeleton() {
  return (
    <div className="service-card-modern animate-pulse" aria-hidden="true">
      <div className="service-card-img-wrap" />
      <div className="mb-3 h-3.5 w-2/3 rounded bg-gray-200" />
      <div className="mb-1.5 h-2.5 w-5/6 rounded bg-gray-100" />
      <div className="mb-1.5 h-2.5 w-4/6 rounded bg-gray-100" />
      <div className="h-2.5 w-3/6 rounded bg-gray-100" />
    </div>
  );
}
