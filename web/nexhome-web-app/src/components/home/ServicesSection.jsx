import { SERVICES } from '../../data/siteData';
import AppLink from '../common/AppLink';
import ServiceCard from '../common/ServiceCard';
import Icon from '../common/Icon';

/** Four featured services and the "see all services" card. */
export default function ServicesSection() {
  return (
    <section className="py-16 bg-[#F8FAFC]" id="services">
      <div className="container max-w-6xl">
        {/* Section Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-10">
          <div>
            <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-1">
              WHAT WE OFFER
            </span>
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] tracking-tight">
              Our <span className="text-[#168F5B]">Services</span>
            </h2>
          </div>

          <p className="text-xs text-gray-500 max-w-md m-0">
            {SERVICES.length} essential home services, all in one place. Professional. Reliable. Convenient.
          </p>
        </div>

        {/* 4 Featured Services + 5th Stacked "See All Services" Card */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3.5 sm:gap-4">
          {SERVICES.slice(0, 4).map((s) => (
            <ServiceCard key={s.slug} service={s} />
          ))}

          {/* 5th Case: Full See All Services Card Visual */}
          <AppLink
            to="/services"
            className="service-card-modern col-span-2 sm:col-span-1 lg:col-span-1 group bg-gradient-to-br from-[#EFF6FF] via-[#F4FBF7] to-[#E2F8EC] border border-[#168F5B]/30 hover:border-[#168F5B] hover:shadow-xl transition-all duration-300 flex flex-col items-center justify-center p-4 min-h-[190px]"
          >
            {/* Visual: Full Stack of Cards */}
            <div className="relative w-full h-full flex items-center justify-center py-4">
              {/* Back Left Tilted Card */}
              <div className="absolute w-24 h-16 rounded-xl bg-white/90 border border-blue-200 shadow-sm -rotate-12 -translate-x-6 -translate-y-2 flex items-center justify-center text-[#0B438B] opacity-70 group-hover:-rotate-[16deg] group-hover:-translate-x-7 transition-transform duration-300">
                <Icon name="wind" size={20} />
              </div>

              {/* Back Right Tilted Card */}
              <div className="absolute w-24 h-16 rounded-xl bg-white/90 border border-emerald-200 shadow-sm rotate-12 translate-x-6 -translate-y-2 flex items-center justify-center text-[#168F5B] opacity-80 group-hover:rotate-[16deg] group-hover:translate-x-7 transition-transform duration-300">
                <Icon name="droplet" size={20} />
              </div>

              {/* Center Front Card */}
              <div className="relative z-10 w-32 h-20 rounded-2xl bg-white border-2 border-[#168F5B]/50 shadow-md p-2 flex flex-col items-center justify-center text-center group-hover:shadow-lg group-hover:scale-105 transition-all duration-300">
                <span className="text-[10px] font-black text-[#0B438B] tracking-wider uppercase">
                  +{SERVICES.length - 4} MORE
                </span>
                <span className="text-xs font-black text-[#168F5B] leading-tight mt-0.5">
                  SERVICES
                </span>
                <div className="flex items-center gap-1.5 mt-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#168F5B] animate-pulse" />
                  <span className="text-[9px] text-gray-500 font-bold">{SERVICES.length} Total</span>
                </div>
              </div>
            </div>
          </AppLink>
        </div>
      </div>
    </section>
  );
}
