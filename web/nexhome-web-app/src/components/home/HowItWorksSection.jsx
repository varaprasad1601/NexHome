import { HOW_IT_WORKS_STEPS } from '../../data/siteData';
import Icon from '../common/Icon';

/** The four steps of getting a service. */
export default function HowItWorksSection() {
  return (
    <section className="py-16 bg-white" id="how-it-works">
      <div className="container max-w-6xl">
        <div className="text-center max-w-xl mx-auto mb-12">
          <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-1">
            SIMPLE STEPS
          </span>
          <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] tracking-tight">
            How It <span className="text-[#168F5B]">Works</span>
          </h2>
          <p className="text-xs text-gray-500 mt-2">
            Getting your home service done is easy with NexHome.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 relative">
          {HOW_IT_WORKS_STEPS.map((step) => (
            <div
              key={step.number}
              className="bg-white border border-gray-100 rounded-2xl p-6 shadow-sm hover:shadow-md transition text-center flex flex-col items-center relative"
            >
              {/* Step Number Circle */}
              <div
                className={`w-7 h-7 rounded-full ${step.badgeColor} text-white font-black text-xs flex items-center justify-center mb-4`}
              >
                {step.number}
              </div>

              {/* Step Icon */}
              <div className="w-14 h-14 rounded-2xl bg-blue-50 text-[#0B438B] flex items-center justify-center mb-4">
                <Icon name={step.icon} size={26} />
              </div>

              <h3 className="text-base font-black text-[#0B438B] mb-2">{step.title}</h3>
              <p className="text-xs text-gray-500 leading-relaxed">{step.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
