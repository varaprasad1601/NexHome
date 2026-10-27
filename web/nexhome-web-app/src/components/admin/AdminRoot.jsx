import { useEffect } from 'react';
import { Outlet } from 'react-router-dom';

import AdminAuthProvider from '../../context/AdminAuthProvider';
import '../../styles/admin.css';

const LATO_HREF = 'https://fonts.googleapis.com/css2?family=Lato:wght@400;700;900&display=swap';

/**
 * Everything under /admin: the Lato font and the 87.5% sizing (both only here, so the public site is unchanged) and
 * the session.
 */
export default function AdminRoot() {
  useEffect(() => {
    document.documentElement.classList.add('nh-admin-scale');
    return () => document.documentElement.classList.remove('nh-admin-scale');
  }, []);

  useEffect(() => {
    if (document.querySelector(`link[href="${LATO_HREF}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = LATO_HREF;
    document.head.appendChild(link);
  }, []);

  return (
    <AdminAuthProvider>
      <div className="nh-admin">
        <Outlet />
      </div>
    </AdminAuthProvider>
  );
}
