import AppLink from './AppLink';
import Icon from './Icon';

/** A service category card linking to its detail page (Services page and the home page's featured services). */
export default function ServiceCard({ service }) {
  return (
    <AppLink className="service-card-modern" to={`/services/${service.slug}`}>
      <div className="service-card-img-wrap">
        <img src={service.image} alt={service.name} loading="lazy" />
      </div>
      <div className="service-card-title">{service.name}</div>
      <ul className="service-card-bullets">
        {service.bullets.map((b, idx) => (
          <li key={idx}>{b}</li>
        ))}
      </ul>
      <div className="service-card-arrow">
        <Icon name="arrow-right" size={13} />
      </div>
    </AppLink>
  );
}
