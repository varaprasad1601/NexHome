import { FORMATTED_PHONE, EMAIL_ADDRESS, LOCATION_TEXT, BUSINESS_HOURS } from '../data/siteData';
import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';

/** Policies & Terms page (policies are still being finalized). */
export default function PoliciesPage() {
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Policies & Terms"
        eyebrow="IMPORTANT INFORMATION"
        description="NexHome’s legal and operational policies are currently being finalized."
      >
        Policies <span className="text-[#168F5B]">& terms.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="md:col-span-2 bg-white rounded-3xl p-8 sm:p-12 border border-gray-100 shadow-sm">
            <div className="w-14 h-14 rounded-2xl bg-blue-50 text-[#0B438B] flex items-center justify-center mb-6">
              <Icon name="file-text" size={28} />
            </div>
            <h2 className="text-2xl font-black text-[#0B315F] mb-4">Official policies are under review.</h2>
            <p className="text-sm text-gray-600 leading-relaxed mb-4">
              Privacy policy, terms of service, cancellation policies, service inclusions and other operating rules will be published after NexHome business and legal approval. This page is not a substitute for finalized policies.
            </p>
            <p className="text-sm text-gray-600 leading-relaxed mb-8">
              For questions about how a particular service will be handled, please reach out before proceeding.
            </p>
            <AppLink
              to="/contact"
              className="bg-[#0B438B] hover:bg-[#08336a] text-white px-8 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-sm transition"
            >
              Ask NexHome <Icon name="arrow-right" size={16} />
            </AppLink>
          </div>

          <aside className="bg-white rounded-3xl p-8 border border-gray-100 shadow-sm h-fit space-y-4">
            <h3 className="text-base font-extrabold text-[#0B315F] pb-2 border-b border-gray-100">Contact details</h3>
            <div className="text-xs text-gray-600 space-y-3">
              <div>
                <span className="font-bold text-gray-800 block mb-0.5">Phone:</span>
                <span>{FORMATTED_PHONE}</span>
              </div>
              <div>
                <span className="font-bold text-gray-800 block mb-0.5">Email:</span>
                <span className="break-all">{EMAIL_ADDRESS}</span>
              </div>
              <div>
                <span className="font-bold text-gray-800 block mb-0.5">Area:</span>
                <span>{LOCATION_TEXT}</span>
              </div>
              <div>
                <span className="font-bold text-gray-800 block mb-0.5">Hours:</span>
                <span>{BUSINESS_HOURS} (IST)</span>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
