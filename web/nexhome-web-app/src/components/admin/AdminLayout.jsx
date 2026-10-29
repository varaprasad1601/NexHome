import { ShieldOff } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigationType } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';
import { canOpen, findNavItem } from '../../data/adminNavigation';
import { scrollToTop } from '../../hooks/useNavigationScroll';
import AdminSidebar from './AdminSidebar';
import AdminTopbar from './AdminTopbar';

/** Shown instead of a screen the admin may not open (a link hidden from their sidebar, opened by its address). */
function NoAccess() {
  return (
    <div className="mx-auto mt-10 flex max-w-[30rem] flex-col items-center rounded-2xl border border-[var(--line)] bg-white px-6 py-10 text-center shadow-[0_1px_2px_rgba(14,51,89,0.04)]">
      <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-[#FDECEC] text-[#E53E3E]">
        <ShieldOff size={24} />
      </span>
      <h1 className="mb-2 text-xl font-black text-[var(--navy)]">No access</h1>
      <p className="mb-5 text-sm text-[var(--muted)]">You do not have permission to view this page. Ask a super user if you need it.</p>
      <Link to="/admin/dashboard" className="rounded-xl bg-[var(--blue)] px-5 py-2.5 text-sm font-bold text-white hover:bg-[var(--navy)]">
        Go to Dashboard
      </Link>
    </div>
  );
}

/**
 * The admin shell: sidebar, top bar and the current screen, in the NexHome web colours. A screen whose sidebar link
 * the admin may not open (see canOpen) shows No access instead, once the permissions have loaded. Opening another
 * screen starts at its top; filters in the address (?categoryId=) and Back / Forward keep the scroll position.
 */
export default function AdminLayout() {
  const location = useLocation();
  const { can, isSuperUser, permissionsLoaded } = useAdminAuth();
  const navItem = findNavItem(location.pathname);
  const allowed = !navItem || canOpen(navItem, can, isSuperUser);
  // Desktop: the menu button hides the sidebar; mobile: it opens the drawer, which closes on navigation
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpenAt, setDrawerOpenAt] = useState(null);
  const drawerOpen = drawerOpenAt === location.key;
  const navigationType = useNavigationType();

  useEffect(() => {
    if (navigationType !== 'POP') scrollToTop();
    // Only a new screen (path), not a changed query string
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  const toggleMenu = () => {
    if (window.matchMedia('(min-width: 1024px)').matches) setCollapsed((value) => !value);
    else setDrawerOpenAt(drawerOpen ? null : location.key);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#edf6fd] via-[#f7fbfd] to-[#f0f8f4] bg-fixed leading-normal text-[var(--ink)]">
      <AdminSidebar collapsed={collapsed} drawerOpen={drawerOpen} onClose={() => setDrawerOpenAt(null)} />
      <div className={`flex min-h-screen flex-col transition-[padding] duration-200 ${collapsed ? 'lg:pl-0' : 'lg:pl-[16.5rem]'}`}>
        <AdminTopbar onMenuClick={toggleMenu} />
        <main id="main-content" className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-7">
          {allowed ? (
            <Outlet />
          ) : permissionsLoaded ? (
            <NoAccess />
          ) : (
            <div className="h-40 animate-pulse rounded-2xl bg-white" />
          )}
        </main>
      </div>
    </div>
  );
}
