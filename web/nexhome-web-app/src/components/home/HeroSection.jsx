import { SERVICES } from '../../data/siteData';
import AppLink from '../common/AppLink';
import Icon from '../common/Icon';
import { buildWhatsAppUrl } from '../../utils/whatsapp';

/** Hero banner with the headline, calls to action and trust cards. */
export default function HeroSection() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-[#edf6fd] via-[#f5fafe] to-[#eaf4fc] border-b border-[#dcebf8]">
      {/* Soft Light Blue Ambient Glow Orbs */}
      <div className="absolute -top-24 -left-20 w-[420px] h-[420px] rounded-full bg-[#38bdf8]/12 blur-3xl pointer-events-none" />
      <div className="absolute top-1/4 left-1/3 w-[460px] h-[460px] rounded-full bg-[#60a5fa]/10 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-16 left-10 w-96 h-96 rounded-full bg-[#93c5fd]/15 blur-3xl pointer-events-none" />
      <div className="absolute top-10 right-1/4 w-80 h-80 rounded-full bg-[#bae6fd]/15 blur-3xl pointer-events-none" />

      {/* Glass surface reflection specular bar at top edge */}
      <div className="absolute top-0 inset-x-0 h-[1.5px] bg-gradient-to-r from-transparent via-white/80 to-transparent pointer-events-none z-20" />

      {/* Panoramic Background Container */}
      <div className="relative w-full lg:aspect-[1672/782] lg:min-h-[580px] flex flex-col justify-between pt-5 pb-6 sm:pt-6 sm:pb-8 overflow-hidden">
        {/* Background Image Layer (All screen sizes) */}
        <div className="absolute inset-0 z-0 pointer-events-none select-none">
          {/* Photo maintaining original ratio */}
          <img
            src="/assets/Hero_Image.png"
            alt="NexHome home service professionals in Nuzvid and Nearby Areas"
            className="w-full h-full object-cover object-right opacity-40 lg:opacity-100"
          />
          {/* Enhanced smooth fade gradient mask for text contrast */}
          <div
            className="absolute inset-0"
            style={{
              background: 'linear-gradient(90deg, rgba(238, 246, 253, 0.97) 0%, rgba(238, 246, 253, 0.92) 35%, rgba(240, 248, 254, 0.78) 60%, rgba(245, 250, 255, 0.45) 80%, rgba(248, 252, 255, 0.15) 100%)'
            }}
          />
          {/* Top and bottom soft fade blend */}
          <div className="absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-[#edf6fd]/60 to-transparent" />
          <div className="absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-[#edf6fd]/70 to-transparent" />
        </div>

        {/* Main Hero Foreground Content */}
        <div className="container max-w-6xl relative z-10 my-auto py-5 sm:py-8 lg:py-16">
          <div className="max-w-xl lg:max-w-2xl">
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-white/90 border border-[#c3e1fd] shadow-sm mb-2">
              <span className="w-1.5 h-1.5 sm:w-2 sm:h-2 rounded-full bg-[#168F5B] animate-pulse" />
              <span className="text-[9px] sm:text-[10px] lg:text-[11px] font-bold tracking-wider text-[#0B438B] uppercase">
                TRUSTED HOME SERVICES IN NUZVID & NEARBY AREAS
              </span>
            </div>

            <h1 className="text-2xl sm:text-3xl lg:text-[44px] xl:text-[50px] font-black text-[#0B315F] leading-[1.1] tracking-tight mb-2.5">
              Your Vision.
              <br />
              <span className="text-[#168F5B]">Our Expertise.</span>
              <br />
              Better Living.
            </h1>

            <p className="text-xs sm:text-sm lg:text-base text-gray-700 leading-relaxed max-w-lg sm:max-w-xl mb-4 font-normal">
              NexHome is a household services platform connecting customers with skilled workers for everyday home needs.
            </p>

            <div className="flex flex-wrap items-center gap-2 sm:gap-3 mb-1">
              <AppLink
                className="bg-[#0B438B] hover:bg-[#08336a] text-white font-bold text-xs sm:text-sm px-4 py-2 sm:px-5 sm:py-2.5 rounded-full inline-flex items-center gap-1.5 shadow-md shadow-blue-900/20 transition-all hover:translate-y-[-1px]"
                to="/services"
              >
                Explore Services <Icon name="arrow-right" size={14} />
              </AppLink>

              <a
                className="bg-white/90 hover:bg-white border-2 border-[#168F5B] text-[#168F5B] font-bold text-xs sm:text-sm px-4 py-1.5 sm:px-5 sm:py-2 rounded-full inline-flex items-center gap-1.5 shadow-sm transition-all hover:translate-y-[-1px]"
                href={buildWhatsAppUrl('Hello NexHome! I would like to enquire about a home service in Nuzvid and Nearby Areas.')}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon name="message-circle" size={15} /> WhatsApp Us
              </a>
            </div>
          </div>
        </div>

        {/* Bottom Trust Cards Strip with Light Blue Frosted Glass Accent */}
        <div className="container max-w-6xl relative z-10 pt-2">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 sm:gap-3 max-w-4xl">
            {/* Item 1 */}
            <div className="bg-white/80 hover:bg-white/95 backdrop-blur-xl border border-white/90 rounded-xl py-2 px-3 sm:py-2.5 sm:px-3.5 shadow-[0_4px_16px_rgba(11,67,139,0.06),inset_0_1px_0_rgba(255,255,255,0.9)] flex items-center gap-3 transition-all">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#ddecfa] to-[#c7e3fc] text-[#0B438B] flex items-center justify-center flex-shrink-0 shadow-inner">
                <Icon name="gear" size={17} />
              </div>
              <div>
                <div className="text-sm font-black text-[#0B438B] leading-none">{SERVICES.length}</div>
                <div className="text-[10px] text-gray-600 font-medium mt-0.5">Service Categories</div>
              </div>
            </div>

            {/* Item 2 */}
            <div className="bg-white/80 hover:bg-white/95 backdrop-blur-xl border border-white/90 rounded-xl py-2 px-3 sm:py-2.5 sm:px-3.5 shadow-[0_4px_16px_rgba(11,67,139,0.06),inset_0_1px_0_rgba(255,255,255,0.9)] flex items-center gap-3 transition-all whitespace-nowrap">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#ddecfa] to-[#c7e3fc] text-[#0B438B] flex items-center justify-center flex-shrink-0 shadow-inner">
                <Icon name="map-pin" size={17} />
              </div>
              <div className="whitespace-nowrap">
                <div className="text-xs font-black text-[#0B438B] leading-tight whitespace-nowrap">Serving</div>
                <div className="text-[10px] text-gray-600 font-medium mt-0.5 whitespace-nowrap">Nuzvid and Nearby Areas</div>
              </div>
            </div>

            {/* Item 3 */}
            <div className="bg-white/80 hover:bg-white/95 backdrop-blur-xl border border-white/90 rounded-xl py-2 px-3 sm:py-2.5 sm:px-3.5 shadow-[0_4px_16px_rgba(11,67,139,0.06),inset_0_1px_0_rgba(255,255,255,0.9)] flex items-center gap-3 transition-all">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#ddecfa] to-[#c7e3fc] text-[#0B438B] flex items-center justify-center flex-shrink-0 shadow-inner">
                <Icon name="clock" size={17} />
              </div>
              <div>
                <div className="text-xs font-black text-[#0B438B] leading-tight">Mon – Sun</div>
                <div className="text-[10px] text-gray-600 font-medium mt-0.5">9 AM – 5 PM</div>
              </div>
            </div>

            {/* Item 4 */}
            <div className="bg-white/80 hover:bg-white/95 backdrop-blur-xl border border-white/90 rounded-xl py-2 px-3 sm:py-2.5 sm:px-3.5 shadow-[0_4px_16px_rgba(11,67,139,0.06),inset_0_1px_0_rgba(255,255,255,0.9)] flex items-center gap-3 transition-all">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#ddecfa] to-[#c7e3fc] text-[#0B438B] flex items-center justify-center flex-shrink-0 shadow-inner">
                <Icon name="users" size={17} />
              </div>
              <div>
                <div className="text-xs font-black text-[#0B438B] leading-tight">Worker</div>
                <div className="text-[10px] text-gray-600 font-medium mt-0.5">Registration Open</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
