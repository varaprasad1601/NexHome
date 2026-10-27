import { useParams } from 'react-router-dom';

import { SERVICES } from '../data/siteData';
import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';
import { buildWhatsAppUrl } from '../utils/whatsapp';
import { useSiteState } from '../context/siteState';

/** One service category: description, enquiry actions and options. */
export default function ServiceDetailPage() {
  const { slug = '' } = useParams();
  const { setCustomerForm } = useSiteState();
  const currentService = SERVICES.find((s) => s.slug === slug.replace('.html', '')) || SERVICES[0];
  const isProposed = currentService.status === 'proposed';
  const detailWa = buildWhatsAppUrl(
    `Hello NexHome, I want to enquire about ${currentService.name} in Nuzvid and Nearby Areas. Please share the available options and applicable charges.`
  );

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current={currentService.name}
        crumbs={[{ label: 'Services', to: '/services' }]}
        eyebrow="NEXHOME HOME SERVICES"
        eyebrowClassName="text-[#168F5B]"
        descriptionClassName="text-base"
        description={currentService.shortDesc}
      >
        {currentService.name}
      </PageHeader>

      <div className="container max-w-6xl py-12">
        <div className="bg-white rounded-3xl p-8 md:p-12 border border-gray-100 shadow-sm grid grid-cols-1 md:grid-cols-2 gap-10 items-center">
          <div>
            <span className="text-xs font-bold text-[#168F5B] uppercase tracking-wider">
              NEXHOME HOME SERVICES
            </span>
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] mt-2 mb-4 leading-tight">
              {currentService.name}
            </h2>
            <p className="text-gray-600 text-base leading-relaxed mb-6">
              {currentService.fullDesc}
            </p>

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
                to={`/contact?service=${currentService.slug}`}
                onClick={() => setCustomerForm((prev) => ({ ...prev, service: currentService.name }))}
              >
                Send an Enquiry <Icon name="arrow-right" size={16} />
              </AppLink>
            </div>

            <div className="text-xs text-gray-500">
              Service Area: Nuzvid and Nearby Areas, Andhra Pradesh · Mon – Sun: 9 AM – 5 PM
            </div>
          </div>

          <div className="rounded-2xl overflow-hidden aspect-[16/10] max-w-[440px] mx-auto bg-gray-100 shadow-md">
            <img
              src={currentService.image}
              alt={currentService.name}
              className="w-full h-full object-cover"
            />
          </div>
        </div>

        {/* Service options list */}
        <div className="mt-10 bg-white rounded-3xl p-8 border border-gray-100 shadow-sm">
          <h2 className="text-xl font-black text-[#0B438B] mb-4">
            What We Offer in {currentService.name}
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6">
            {currentService.options.map((opt, i) => (
              <div
                key={i}
                className="flex items-center gap-3 p-3.5 rounded-xl border border-gray-100 bg-gray-50/50"
              >
                <span className="w-5 h-5 rounded-full bg-[#168F5B]/10 text-[#168F5B] flex items-center justify-center">
                  <Icon name="check" size={12} />
                </span>
                <span className="text-sm font-medium text-gray-700">{opt}</span>
              </div>
            ))}
          </div>

          <div className="p-4 rounded-xl bg-blue-50/70 border-l-4 border-[#0B438B] text-xs text-gray-700">
            {isProposed
              ? 'Note: Individual requirements and charges are discussed and agreed before service confirmation.'
              : 'Note: Service scope, availability and material requirements are confirmed before execution.'}
          </div>
        </div>
      </div>
    </div>
  );
}
