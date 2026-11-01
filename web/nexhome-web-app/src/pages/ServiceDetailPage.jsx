import { useState } from 'react';
import { useParams } from 'react-router-dom';

import AppLink from '../components/common/AppLink';
import GradientCover from '../components/common/GradientCover';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';
import { useSiteCatalog } from '../hooks/useSiteCatalog';
import { buildWhatsAppUrl } from '../utils/whatsapp';
import { useSiteState } from '../context/siteState';

/** The category's image, or the gradient stand-in when it has none or it fails to load. */
function CategoryImage({ category }) {
  const [failed, setFailed] = useState(false);
  return category.imageUrl && !failed ? (
    <img src={category.imageUrl} alt={category.name} className="w-full h-full object-cover" onError={() => setFailed(true)} />
  ) : (
    <GradientCover />
  );
}

/**
 * One service category from the catalogue (/services/:slug; an unknown slug shows the first category): its
 * description and image, enquiry actions, and every service it offers with its short description.
 */
export default function ServiceDetailPage() {
  const { slug = '' } = useParams();
  const { setCustomerForm } = useSiteState();
  const { loading, categories } = useSiteCatalog();
  const wanted = slug.replace('.html', '');
  const category = categories.find((c) => c.slug === wanted) || categories[0];

  if (loading || !category) {
    return (
      <div className="bg-[#F8FAFC]">
        <PageHeader current="Services" crumbs={[{ label: 'Services', to: '/services' }]} eyebrow="NEXHOME HOME SERVICES">
          {loading ? 'Loading…' : 'Services coming soon'}
        </PageHeader>
        <div className="container max-w-6xl py-12">
          {loading ? (
            <div className="h-64 rounded-3xl bg-white border border-gray-100 animate-pulse" />
          ) : (
            <p className="text-center text-sm text-gray-500">Services will be listed here soon.</p>
          )}
        </div>
      </div>
    );
  }

  const detailWa = buildWhatsAppUrl(
    `Hello NexHome, I want to enquire about ${category.name} in Nuzvid and Nearby Areas. Please share the available options and applicable charges.`
  );

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current={category.name}
        crumbs={[{ label: 'Services', to: '/services' }]}
        eyebrow="NEXHOME HOME SERVICES"
        eyebrowClassName="text-[#168F5B]"
      >
        {category.name}
      </PageHeader>

      <div className="container max-w-6xl py-12">
        <div className="bg-white rounded-3xl p-8 md:p-12 border border-gray-100 shadow-sm grid grid-cols-1 md:grid-cols-2 gap-10 items-center">
          <div>
            <span className="text-xs font-bold text-[#168F5B] uppercase tracking-wider">
              NEXHOME HOME SERVICES
            </span>
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] mt-2 mb-4 leading-tight">
              {category.name}
            </h2>
            {category.description && (
              <p className="text-gray-600 text-base leading-relaxed mb-6">{category.description}</p>
            )}

            <div className="flex flex-wrap gap-3 mb-6">
              <a
                className="bg-[#168F5B] hover:bg-[#12774B] text-white px-6 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-sm transition"
                href={detailWa}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon name="message-circle" size={17} /> Enquire on WhatsApp
              </a>
              <AppLink
                className="border border-[#0B438B] text-[#0B438B] hover:bg-blue-50 px-6 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 transition"
                to={`/contact?service=${category.slug}`}
                onClick={() => setCustomerForm((prev) => ({ ...prev, service: category.name }))}
              >
                Send an Enquiry <Icon name="arrow-right" size={16} />
              </AppLink>
            </div>

            <div className="text-xs text-gray-500">
              Service Area: Nuzvid and Nearby Areas, Andhra Pradesh · Mon – Sun: 9 AM – 5 PM
            </div>
          </div>

          <div className="rounded-2xl overflow-hidden aspect-[16/10] w-full max-w-[440px] mx-auto bg-gray-100 shadow-md">
            <CategoryImage key={category.slug} category={category} />
          </div>
        </div>

        {/* Every service of the category */}
        <div className="mt-10 bg-white rounded-3xl p-8 border border-gray-100 shadow-sm">
          <h2 className="text-xl font-black text-[#0B438B] mb-4">
            What We Offer in {category.name}
          </h2>
          {category.services.length ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6">
              {category.services.map((service) => (
                <div
                  key={service.serviceId}
                  className="flex items-start gap-3 p-3.5 rounded-xl border border-gray-100 bg-gray-50/50"
                >
                  <span className="mt-0.5 w-5 h-5 flex-shrink-0 rounded-full bg-[#168F5B]/10 text-[#168F5B] flex items-center justify-center">
                    <Icon name="check" size={12} />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-bold text-gray-700">{service.name}</span>
                    {service.shortDescription && (
                      <span className="mt-0.5 block text-xs leading-relaxed text-gray-500 line-clamp-3">
                        {service.shortDescription}
                      </span>
                    )}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="mb-6 text-sm text-gray-500">Services in this category will be listed here soon.</p>
          )}

          <div className="p-4 rounded-xl bg-blue-50/70 border-l-4 border-[#0B438B] text-xs text-gray-700">
            Note: Service scope, availability, charges and material requirements are discussed and confirmed before the
            service.
          </div>
        </div>
      </div>
    </div>
  );
}
