import { Outlet, useLocation, useNavigate } from 'react-router-dom';

import { useSiteState } from '../../context/siteState';
import useNavigationScroll, { scrollToTop } from '../../hooks/useNavigationScroll';
import { getActiveNavKey, isLaunchLocation } from '../../utils/navigation';
import CinematicLaunch from '../launch/CinematicLaunch';
import WebRegisterModal from '../registration/WebRegisterModal';
import Footer from './Footer';
import Header from './Header';

/** The page frame around every route: skip link, header, the routed page, footer and site-wide overlays. */
export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { resetWorkerRegistration } = useSiteState();
  const isLaunch = isLaunchLocation(location);
  useNavigationScroll();

  return (
    <div className="min-h-screen flex flex-col font-sans bg-white selection:bg-[#168F5B]/20 selection:text-[#0B438B]">
      {/* Top Accessibility skip link */}
      <a
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:bg-white focus:px-4 focus:py-2 focus:shadow-lg focus:text-blue-900"
        href="#main-content"
      >
        Skip to content
      </a>

      <Header activeNavKey={getActiveNavKey(location.pathname, isLaunch)} />

      <main id="main-content" className="flex-grow">
        <Outlet />
      </main>

      <Footer />

      {/* Register via Web Modal Box */}
      <WebRegisterModal />

      {/* Floating WhatsApp Action Button (Commented out per request) */}
      {/*
      <a
        className="floating-whatsapp"
        href={buildWhatsAppUrl('Hi NexHome, I have a service enquiry.')}
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Contact NexHome on WhatsApp"
      >
        <Icon name="message-circle" size={20} />
        <span>WhatsApp</span>
      </a>
      */}
      {/* Cinematic Launch Portal Overlay for /launch */}
      {isLaunch && (
        <CinematicLaunch
          onComplete={() => {
            resetWorkerRegistration();
            navigate('/');
            scrollToTop();
          }}
        />
      )}
    </div>
  );
}
