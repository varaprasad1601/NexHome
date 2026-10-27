import { HOMEPAGE_FAQS } from '../../data/siteData';
import AppLink from '../common/AppLink';
import Icon from '../common/Icon';
import { useSiteState } from '../../context/siteState';

/** The homepage FAQs. */
export default function FaqSection() {
  const { openFaqIndex, setOpenFaqIndex } = useSiteState();

  return (
    <section className="py-16 bg-white" id="faqs">
      <div className="container max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          {/* Left Column: Thinker Image & Script Note */}
          <div className="lg:col-span-5 flex flex-col items-center text-center relative">
            <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-1">
              FREQUENTLY ASKED QUESTIONS
            </span>
            <div className="flex items-center gap-2 mb-2">
              <h2 className="text-4xl font-black text-[#0B438B] tracking-tight">FAQ</h2>
              <span className="w-8 h-8 rounded-full bg-blue-100 text-[#0B438B] font-black text-sm flex items-center justify-center">
                ?
              </span>
            </div>

            <div className="font-cursive text-2xl text-[#0B438B] font-bold mb-4">
              Got Questions? We've Got Answers!
            </div>

            <div className="w-56 h-56 sm:w-64 sm:h-64 rounded-full overflow-hidden border-4 border-white shadow-xl bg-gray-100">
              <img
                src="/assets/faq-thinker.jpg"
                alt="Got Questions? We've got answers!"
                className="w-full h-full object-cover"
              />
            </div>
          </div>

          {/* Right Column: Accordion List */}
          <div className="lg:col-span-7 space-y-3">
            {HOMEPAGE_FAQS.map((faq, idx) => {
              const isOpen = openFaqIndex === idx;
              return (
                <div
                  key={idx}
                  className="border border-gray-200 rounded-2xl overflow-hidden transition-all bg-white"
                >
                  <button
                    type="button"
                    className="w-full px-5 py-4 flex items-center justify-between text-left gap-4 hover:bg-gray-50/70 transition"
                    onClick={() => setOpenFaqIndex(isOpen ? null : idx)}
                  >
                    <span className="text-sm font-extrabold text-[#0B438B] flex items-center gap-3">
                      <span className="text-gray-400">
                        <Icon name="info" size={17} />
                      </span>
                      {faq.q}
                    </span>
                    <span className="text-gray-500 flex-shrink-0">
                      <Icon name={isOpen ? 'minus' : 'plus'} size={16} />
                    </span>
                  </button>

                  {isOpen && (
                    <div className="px-5 pb-5 pt-1 text-xs text-gray-600 leading-relaxed border-t border-gray-100">
                      {faq.a}
                    </div>
                  )}
                </div>
              );
            })}

            {/* See All FAQs Button */}
            <div className="pt-2 flex justify-start">
              <AppLink
                className="border border-[#168F5B] text-[#168F5B] hover:bg-green-50 text-xs font-bold px-5 py-2.5 rounded-full inline-flex items-center gap-1.5 transition shadow-sm hover:translate-y-[-1px]"
                to="/faqs"
              >
                See All FAQs <Icon name="arrow-right" size={13} />
              </AppLink>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
