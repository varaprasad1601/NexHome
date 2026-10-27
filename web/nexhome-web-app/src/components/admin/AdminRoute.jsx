import { Navigate, Outlet } from 'react-router-dom';

import { useAdminAuth } from '../../context/adminAuth';

/** Renders the admin screens only for a signed-in admin; anyone else goes to the sign-in at /admin. */
export default function AdminRoute() {
  const { session } = useAdminAuth();
  return session ? <Outlet /> : <Navigate to="/admin" replace />;
}
