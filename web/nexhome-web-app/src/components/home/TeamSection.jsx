import AppLink from '../common/AppLink';
import Icon from '../common/Icon';

/** The people behind NexHome. */
export default function TeamSection() {
  return (
    <section className="py-16 bg-[#F8FAFC]" id="team">
      <div className="container max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          {/* Left Column: Copy & Link */}
          <div className="lg:col-span-6 flex flex-col justify-center items-start">
            <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-2">
              THE PEOPLE BEHIND NEXHOME
            </span>
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-black text-[#0B315F] tracking-tight mb-4 leading-tight">
              Working together for <br />
              <span className="text-[#168F5B]">better living.</span>
            </h2>
            <p className="text-sm sm:text-base text-gray-600 leading-relaxed mb-6 font-normal max-w-xl">
              NexHome is being built by a team focused on simplifying household service discovery and coordination through clear communication and practical problem-solving.
            </p>
            <AppLink
              to="/team"
              className="text-sm font-bold text-[#0B438B] hover:text-[#168F5B] inline-flex items-center gap-1.5 transition-colors group"
            >
              More about our team <Icon name="arrow-right" size={14} className="transform group-hover:translate-x-1 transition-transform" />
            </AppLink>
          </div>

          {/* Right Column: Decorative Card Graphic */}
          <div className="lg:col-span-6 flex items-center justify-center">
            <div className="w-full bg-gradient-to-br from-[#F8FAFC] to-[#EFF6FF] border border-gray-100 rounded-3xl p-6 sm:p-12 relative overflow-hidden flex items-center justify-center min-h-[250px] sm:min-h-[300px] shadow-sm">
              {/* Dashed Circle Ring */}
              <div className="w-44 h-44 sm:w-60 sm:h-60 rounded-full border border-dashed border-[#168F5B]/40 flex items-center justify-center relative">
                {/* Center Icon Badge */}
                <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-[#0B438B] text-white flex items-center justify-center shadow-xl">
                  <Icon name="users" size={30} />
                </div>
              </div>

              {/* Floating Badge Card at Bottom Right */}
              <div className="absolute bottom-3 right-3 sm:bottom-6 sm:right-6 bg-white px-3.5 py-2.5 sm:px-4 sm:py-3 rounded-2xl shadow-lg border border-gray-100 text-xs flex flex-col">
                <span className="text-gray-600 font-semibold">People first.</span>
                <span className="text-[#168F5B] font-black tracking-wide">Homes always.</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
