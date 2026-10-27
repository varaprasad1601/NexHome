import { useState } from 'react';
import { useLocation } from 'react-router-dom';

import AppLink from '../common/AppLink';
import Icon from '../common/Icon';
import { buildWhatsAppUrl } from '../../utils/whatsapp';

/** Sticky site header with the desktop navigation and the mobile drawer. */
export default function Header({ activeNavKey }) {
  const location = useLocation();
  // The drawer belongs to the page it was opened on, so any navigation closes it
  const [menuOpenAt, setMenuOpenAt] = useState(null);
  const mobileMenuOpen = menuOpenAt === location.key;
  const setMobileMenuOpen = (open) => setMenuOpenAt(open ? location.key : null);

  return (
    <header
      className="site-header"
      id="site-header"
      style={{
        background: '#ffffff',
        borderBottom: 'none'
      }}
    >
      <div className="container max-w-6xl header-inner">
        {/* Brand Logo */}
        <AppLink
          className="brand flex items-center gap-2.5"
          to="/"
          aria-label="NexHome home"
        >
          <img
            className="brand-logo-img h-11 w-auto object-contain flex-shrink-0"
            src="/assets/NexHome_Logo.png"
            alt="NexHome Logo"
          />
          <div className="flex flex-col justify-end pb-0.5">
            <div className="text-xl sm:text-[21px] font-normal tracking-tight leading-none" style={{ fontFamily: "'ManropeCustom', sans-serif" }}>
              <span className="text-[#0B438B] font-semibold">Nex</span>
              <span className="text-[#168F5B] font-semibold">Home</span>
            </div>
            <div className="text-[9px] sm:text-[10px] font-bold text-gray-500 tracking-wider leading-none mt-[2px]" style={{ fontFamily: "'DMSansCustom', sans-serif" }}>
              Better Living, Every Day
            </div>

          </div>
        </AppLink>

        {/* Desktop Navigation */}
        <nav className="desktop-nav" aria-label="Main navigation">
          <AppLink
            className={`nav-link ${activeNavKey === 'home' ? 'is-active' : ''}`}
            to="/"
          >
            Home
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'about' ? 'is-active' : ''}`}
            to="/about"
          >
            About Us
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'services' ? 'is-active' : ''}`}
            to="/services"
          >
            Services
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'how' ? 'is-active' : ''}`}
            to="/how-it-works"
          >
            How It Works
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'join' ? 'is-active' : ''}`}
            to="/join"
          >
            Join as Professional
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'team' ? 'is-active' : ''}`}
            to="/team"
          >
            Team
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'faqs' ? 'is-active' : ''}`}
            to="/faqs"
          >
            FAQ
          </AppLink>
          <AppLink
            className={`nav-link ${activeNavKey === 'contact' ? 'is-active' : ''}`}
            to="/contact"
          >
            Contact
          </AppLink>
        </nav>

        {/* Header CTA Button */}
        <div className="flex items-center gap-2 sm:gap-3">
          <a
            className="header-book-btn hidden lg:inline-flex"
            href={buildWhatsAppUrl("Hello NexHome, I'd like to book or enquire about a home service.")}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon name="phone" size={15} /> Book / Enquire
          </a>

          <button
            className="lg:hidden flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50 flex-shrink-0"
            type="button"
            aria-label="Toggle Menu"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            <Icon name={mobileMenuOpen ? 'x' : 'menu'} size={20} />
          </button>
        </div>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="lg:hidden border-t border-gray-100 bg-white px-5 py-4 shadow-xl flex flex-col gap-2">
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'home' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/"
          >
            Home
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'about' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/about"
          >
            About Us
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'services' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/services"
          >
            Services
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'how' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/how-it-works"
          >
            How It Works
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'join' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/join"
          >
            Join as Professional
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'team' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/team"
          >
            Team
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'faqs' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/faqs"
          >
            FAQ
          </AppLink>
          <AppLink
            className={`py-2 mb-1.5 text-sm font-semibold ${activeNavKey === 'contact' ? 'text-[#0B438B]' : 'text-gray-700'}`}
            to="/contact"
          >
            Contact
          </AppLink>
          <a
            className="w-full text-center py-2.5 mt-1 rounded-full bg-[#168F5B] text-white text-sm font-bold shadow-sm inline-flex items-center justify-center gap-2"
            href={buildWhatsAppUrl("Hello NexHome, I'd like to book or enquire about a home service.")}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon name="phone" size={15} /> Book / Enquire
          </a>
        </div>
      )}
    </header>
  );
}
