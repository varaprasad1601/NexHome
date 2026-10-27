import { WHY_ITEMS } from '../../data/siteData';
import Icon from '../common/Icon';

/** Why choose NexHome. */
export default function WhyChooseSection() {
  return (
    <section className="py-16 bg-[#F8FAFC]">
      <div className="container max-w-6xl">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-10">
          <div>
            <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-1">
              YOUR TRUST OUR STRENGTH
            </span>
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] tracking-tight">
              Why Choose <span className="text-[#168F5B]">NexHome</span>
            </h2>
          </div>
          <p className="text-xs text-gray-500 max-w-sm m-0">
            We are committed to making your home care experience simple, safe and satisfying.
          </p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3.5">
          {WHY_ITEMS.map((item, idx) => (
            <div
              key={idx}
              className="bg-white border border-gray-100 rounded-2xl p-3.5 sm:p-5 shadow-sm text-center flex flex-col items-center hover:shadow-md transition"
            >
              <div className="w-11 h-11 rounded-full bg-green-50 text-[#168F5B] flex items-center justify-center mb-3">
                <Icon name={item.icon} size={22} />
              </div>
              <h3 className="text-xs font-black text-[#0B438B] mb-1.5 leading-snug">
                {item.title}
              </h3>
              <p className="text-[11px] text-gray-500 leading-relaxed m-0">
                {item.desc}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
