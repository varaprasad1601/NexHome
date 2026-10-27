import { ALL_FAQS } from '../data/siteData';
import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';
import { useSiteState } from '../context/siteState';

/** All frequently asked questions. */
export default function FaqsPage() {
  const { openFaqIndex, setOpenFaqIndex } = useSiteState();

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="FAQs"
        eyebrow="HELP & ANSWERS"
        description="Find answers about the service process, location, timings, pricing and professional registration."
      >
        Frequently asked <span className="text-[#168F5B]">questions.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        <div className="bg-white rounded-3xl p-6 sm:p-10 border border-gray-100 shadow-sm space-y-4 mb-10">
          {ALL_FAQS.map((faq, i) => (
            <div
              key={i}
              className="border border-gray-100 rounded-2xl overflow-hidden transition-colors"
            >
              <button
                type="button"
                className="w-full text-left p-5 flex items-center justify-between gap-4 font-bold text-[#0B315F] hover:bg-gray-50/70 transition"
                onClick={() => setOpenFaqIndex(openFaqIndex === i ? null : i)}
              >
                <span className="text-sm sm:text-base">{faq.q}</span>
                <span
                  className={`transform transition-transform text-[#0B438B] ${
                    openFaqIndex === i ? 'rotate-180' : ''
                  }`}
                >
                  <Icon name="chevron-down" size={18} />
                </span>
              </button>
              {openFaqIndex === i && (
                <div className="px-5 pb-5 pt-1 text-xs sm:text-sm text-gray-600 leading-relaxed border-t border-gray-50 bg-[#FBFDFF]">
                  {faq.a}
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="bg-white rounded-3xl p-8 sm:p-10 border border-gray-100 shadow-sm text-center">
          <h3 className="text-2xl font-black text-[#0B315F] mb-2">Still have a question?</h3>
          <p className="text-xs sm:text-sm text-gray-500 mb-6 max-w-md mx-auto">
            We're happy to discuss your specific enquiry during business hours (Mon – Sun, 9 AM – 5 PM).
          </p>
          <AppLink
            to="/contact"
            className="bg-[#0B438B] hover:bg-[#08336a] text-white px-8 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-sm transition"
          >
            Contact Us <Icon name="arrow-right" size={16} />
          </AppLink>
        </div>
      </div>
    </div>
  );
}
