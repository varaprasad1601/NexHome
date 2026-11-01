import ServiceCard, { ServiceCardSkeleton } from '../components/common/ServiceCard';
import PageHeader from '../components/common/PageHeader';
import { useSiteCatalog } from '../hooks/useSiteCatalog';

/** Every service category from the catalogue, each card listing all of its services. */
export default function ServicesPage() {
  const { loading, categories, serviceCount } = useSiteCatalog();
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Services"
        eyebrow="EXPLORE WHAT WE OFFER"
        descriptionClassName="text-base"
        description={
          loading
            ? 'Browse our service categories. Each service can be discussed with NexHome before confirmation.'
            : `Browse our ${serviceCount} services in ${categories.length} ${categories.length === 1 ? 'category' : 'categories'}. Each service can be discussed with NexHome before confirmation.`
        }
      >
        Household services <span className="text-[#168F5B]">in one place.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        {/* All categories, with all their services */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {loading
            ? Array.from({ length: 8 }, (_, index) => <ServiceCardSkeleton key={index} />)
            : categories.map((category) => <ServiceCard key={category.slug} category={category} />)}
        </div>
        {!loading && categories.length === 0 && (
          <p className="text-center text-sm text-gray-500">Services will be listed here soon.</p>
        )}
      </div>
    </div>
  );
}
