import { SERVICES } from '../data/siteData';
import ServiceCard from '../components/common/ServiceCard';
import PageHeader from '../components/common/PageHeader';

/** All service categories. */
export default function ServicesPage() {
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Services"
        eyebrow="EXPLORE WHAT WE OFFER"
        descriptionClassName="text-base"
        description={`Browse our ${SERVICES.length} service categories. Each service can be discussed with NexHome before confirmation.`}
      >
        Household services <span className="text-[#168F5B]">in one place.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        {/* All Services Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {SERVICES.map((s) => (
            <ServiceCard key={s.slug} service={s} />
          ))}
        </div>
      </div>
    </div>
  );
}
