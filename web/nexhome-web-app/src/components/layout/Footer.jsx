import AppLink from '../common/AppLink';
import Icon from '../common/Icon';
import { buildWhatsAppUrl } from '../../utils/whatsapp';

/** Site footer: brand, quick links, information and contact details. */
export default function Footer() {
  return (
    <footer className="bg-[#08264d] text-white pt-14 pb-8">
      <div className="container max-w-6xl grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-10 pb-12 border-b border-white/10">
        {/* Col 1: Brand Info */}
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-14 h-14 rounded-2xl bg-white p-2 flex items-center justify-center shadow-md flex-shrink-0">
              <img
                className="w-full h-full object-contain"
                src="/assets/NexHome_Logo.png"
                alt="NexHome brand logo"
              />
            </div>
            <div>
              <div className="text-2xl font-normal tracking-tight leading-none mb-1" style={{ fontFamily: "'Montserrat', sans-serif" }}>
                <span className="text-white font-semibold">Nex</span>
                <span className="text-[#168F5B] font-semibold">Home</span>
              </div>
              <div className="text-xs text-blue-200/90 leading-tight font-normal" style={{ fontFamily: "'Montserrat', sans-serif" }}>
                Better Living, Every Day
              </div>
            </div>
          </div>
          <p className="text-xs text-blue-100/75 leading-relaxed mt-1">
            Connecting households with service professionals in Nuzvid and Nearby Areas, Andhra Pradesh.
          </p>
          <div className="flex items-center gap-2 text-xs text-blue-200">
            <Icon name="map-pin" size={15} />
            <span>Proudly serving Nuzvid and Nearby Areas</span>
          </div>
        </div>

        {/* Col 2: Quick Links */}
        <div>
          <h4 className="text-sm font-bold text-white mb-4">Quick Links</h4>
          <ul className="space-y-2.5 text-xs text-blue-200/90">
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/about"
              >
                About Us
              </AppLink>
            </li>
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/services"
              >
                Services
              </AppLink>
            </li>
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/how-it-works"
              >
                How It Works
              </AppLink>
            </li>
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/join"
              >
                Join as a Professional
              </AppLink>
            </li>
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/faqs"
              >
                FAQs
              </AppLink>
            </li>
          </ul>
        </div>

        {/* Col 3: Information */}
        <div>
          <h4 className="text-sm font-bold text-white mb-4">Information</h4>
          <ul className="space-y-2.5 text-xs text-blue-200/90">
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/team"
              >
                Our Team
              </AppLink>
            </li>
            <li>
              <AppLink
                className="hover:text-white transition-colors"
                to="/contact"
              >
                Contact Us
              </AppLink>
            </li>
            <li>
              <span className="text-blue-200/90 cursor-default">
                Policies & Terms
              </span>
            </li>
            <li className="pt-1 text-blue-200/80">
              Mon – Sun: 9 AM – 5 PM (IST)
            </li>
          </ul>
        </div>

        {/* Col 4: Get in Touch */}
        <div>
          <h4 className="text-sm font-bold text-white mb-4">Get in Touch</h4>
          <ul className="space-y-3 text-xs text-blue-200/90">
            <li className="flex items-center gap-2.5">
              <Icon name="phone" size={15} />
              <a href="tel:+919257149257" className="hover:text-white transition-colors">
                +91 9257 14 9257
              </a>
            </li>
            <li className="flex items-center gap-2.5">
              <Icon name="mail" size={15} />
              <a href="mailto:nexhome.info@gmail.com" className="hover:text-white transition-colors">
                nexhome.info@gmail.com
              </a>
            </li>
            <li className="flex items-center gap-2.5">
              <Icon name="message-circle" size={15} />
              <a
                href={buildWhatsAppUrl("Hello NexHome, I'd like to get in touch.")}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-white transition-colors"
              >
                Message us on WhatsApp
              </a>
            </li>
          </ul>
          <p className="text-[11px] text-blue-300/70 leading-relaxed mt-4">
            Service arrangements depend on scope, location and professional availability.
          </p>
        </div>
      </div>

      <div className="container max-w-6xl pt-6 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-blue-200/70">
        <div>© 2026 NexHome. All rights reserved.</div>
        <div>Made for better living in Nuzvid and Nearby Areas</div>
      </div>
    </footer>
  );
}
