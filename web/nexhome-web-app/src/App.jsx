import { Route, Routes } from 'react-router-dom';

import AdminLayout from './components/admin/AdminLayout';
import AdminRoot from './components/admin/AdminRoot';
import AdminRoute from './components/admin/AdminRoute';
import Layout from './components/layout/Layout';
import SiteStateProvider from './context/SiteStateProvider';
import AboutPage from './pages/AboutPage';
import AdminComingSoonPage from './pages/admin/AdminComingSoonPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminLoginPage from './pages/admin/AdminLoginPage';
import AdminProfilePage from './pages/admin/AdminProfilePage';
import AdminsPage from './pages/admin/AdminsPage';
import CustomerProfilePage from './pages/admin/CustomerProfilePage';
import CustomersPage from './pages/admin/CustomersPage';
import PartnerProfilePage from './pages/admin/PartnerProfilePage';
import PartnersPage from './pages/admin/PartnersPage';
import FunctionsPage from './pages/admin/FunctionsPage';
import RoleDetailPage from './pages/admin/RoleDetailPage';
import RolesPage from './pages/admin/RolesPage';
import ContactPage from './pages/ContactPage';
import FaqsPage from './pages/FaqsPage';
import HomePage from './pages/HomePage';
import HowItWorksPage from './pages/HowItWorksPage';
import JoinPage from './pages/JoinPage';
import PoliciesPage from './pages/PoliciesPage';
import ServiceDetailPage from './pages/ServiceDetailPage';
import ServicesPage from './pages/ServicesPage';
import TeamPage from './pages/TeamPage';

export default function App() {
  return (
    <SiteStateProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="about" element={<AboutPage />} />
          <Route path="services" element={<ServicesPage />} />
          <Route path="services/:slug" element={<ServiceDetailPage />} />
          <Route path="how-it-works" element={<HowItWorksPage />} />
          <Route path="join" element={<JoinPage />} />
          <Route path="team" element={<TeamPage />} />
          <Route path="faqs" element={<FaqsPage />} />
          <Route path="faq" element={<FaqsPage />} />
          <Route path="contact" element={<ContactPage />} />
          <Route path="policies" element={<PoliciesPage />} />
          {/* The launch countdown plays over the home page (Layout shows it) */}
          <Route path="launch" element={<HomePage />} />
          {/* Any other address shows the home page */}
          <Route path="*" element={<HomePage />} />
        </Route>
        {/* Admin screens, without the public site's header and footer */}
        <Route path="admin" element={<AdminRoot />}>
          <Route index element={<AdminLoginPage />} />
          <Route element={<AdminRoute />}>
            <Route element={<AdminLayout />}>
              <Route path="dashboard" element={<AdminDashboardPage />} />
              <Route path="users/admins" element={<AdminsPage key="admins" />} />
              <Route path="users/super-admins" element={<AdminsPage key="super-admins" variant="superAdmins" />} />
              <Route path="users/sub-admins" element={<AdminsPage key="sub-admins" variant="subAdmins" />} />
              <Route path="users/customers" element={<CustomersPage />} />
              <Route path="users/customers/:customerProfileId" element={<CustomerProfilePage />} />
              <Route path="users/partners" element={<PartnersPage />} />
              <Route path="users/partners/:partnerProfileId" element={<PartnerProfilePage />} />
              <Route path="users/admins/:adminProfileId" element={<AdminProfilePage key="admins" list="admins" />} />
              <Route path="users/sub-admins/:adminProfileId" element={<AdminProfilePage key="sub-admins" list="subAdmins" />} />
              <Route path="users/super-admins/:adminProfileId" element={<AdminProfilePage key="super-admins" list="superAdmins" />} />
              <Route path="system/roles" element={<RolesPage />} />
              <Route path="system/roles/:roleId" element={<RoleDetailPage />} />
              <Route path="system/functions" element={<FunctionsPage />} />
              <Route path="profile" element={<AdminProfilePage self />} />
              {/* Sidebar links whose screens are not built yet */}
              <Route path="*" element={<AdminComingSoonPage />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </SiteStateProvider>
  );
}
