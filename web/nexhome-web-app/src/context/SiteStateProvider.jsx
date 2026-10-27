import { useEffect, useState } from 'react';

import { SERVICES } from '../data/siteData';
import { buildWhatsAppUrl } from '../utils/whatsapp';
import { SiteStateContext } from './siteState';

const EMPTY_WORKER_FORM = {
  name: '',
  phone: '',
  service: '',
  locality: '',
  experience: '',
  availability: '',
  consent: false
};

/** The service named by `?service=` on the contact page (slug or name), if any. */
function serviceFromUrl() {
  if (!window.location.pathname.toLowerCase().includes('contact')) return null;
  const serviceParam = new URLSearchParams(window.location.search).get('service');
  if (!serviceParam) return null;
  const match = SERVICES.find(
    (s) => s.slug === serviceParam || s.name.toLowerCase() === serviceParam.toLowerCase()
  );
  return match ? match.name : null;
}

/**
 * Holds the state the home page and the inner pages share, so it survives navigation: the customer enquiry form,
 * the worker registration form, the open FAQ and the "Register via Web" modal.
 */
export default function SiteStateProvider({ children }) {
  // Customer Contact / Enquiry Form State
  const [customerForm, setCustomerForm] = useState(() => ({
    name: '',
    email: '',
    phone: '',
    service: serviceFromUrl() || '',
    message: ''
  }));
  const [customerError, setCustomerError] = useState(null);
  const [customerSuccess, setCustomerSuccess] = useState(null);

  // Worker Registration Form State
  const [workerForm, setWorkerForm] = useState(EMPTY_WORKER_FORM);
  const [workerError, setWorkerError] = useState(null);
  const [workerSuccess, setWorkerSuccess] = useState(null);
  const [workerMode, setWorkerMode] = useState('initial');
  const [webRegisterModalOpen, setWebRegisterModalOpen] = useState(false);

  // FAQ Accordion State (first open by default)
  const [openFaqIndex, setOpenFaqIndex] = useState(0);

  // Going back or forward to /contact?service=... selects that service, as on first load
  useEffect(() => {
    const handlePopState = () => {
      const service = serviceFromUrl();
      if (service) {
        setCustomerForm((prev) => ({ ...prev, service }));
      }
    };
    window.addEventListener('popstate', handlePopState);
    window.addEventListener('hashchange', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
      window.removeEventListener('hashchange', handlePopState);
    };
  }, []);

  const resetWorkerRegistration = () => {
    setWorkerForm(EMPTY_WORKER_FORM);
    setWorkerError(null);
    setWorkerSuccess(null);
    setWorkerMode('initial');
  };

  const handleCustomerSubmit = (e) => {
    e.preventDefault();
    setCustomerError(null);
    setCustomerSuccess(null);

    if (!customerForm.name.trim()) {
      setCustomerError('Please enter your full name');
      return;
    }
    const cleanPhone = customerForm.phone.replace(/\D/g, '');
    if (!/^[6-9][0-9]{9}$/.test(cleanPhone)) {
      setCustomerError('Please enter a valid 10-digit Indian mobile number');
      return;
    }
    if (!customerForm.service) {
      setCustomerError('Please select a service category');
      return;
    }
    if (!customerForm.message.trim()) {
      setCustomerError('Please describe your requirement / message');
      return;
    }

    const lines = [
      'NexHome – Customer Service Enquiry',
      `Name: ${customerForm.name.trim()}`,
      `Phone: ${cleanPhone}`,
      `Service: ${customerForm.service}`
    ];
    if (customerForm.email) lines.push(`Email: ${customerForm.email.trim()}`);
    if (customerForm.message) lines.push(`Message: ${customerForm.message.trim()}`);
    lines.push('Location: Nuzvid and Nearby Areas, Andhra Pradesh');
    lines.push('Sent via NexHome website.');

    const url = buildWhatsAppUrl(lines.join('\n'));
    window.open(url, '_blank', 'noopener,noreferrer');
    setCustomerSuccess('WhatsApp should now open. Please review the message and press Send to complete your enquiry.');
  };

  const handleWorkerSubmit = (e) => {
    e.preventDefault();
    setWorkerError(null);
    setWorkerSuccess(null);

    if (!workerForm.name.trim()) {
      setWorkerError('Please enter your full name');
      return;
    }
    const cleanPhone = workerForm.phone.replace(/\D/g, '');
    if (!/^[6-9][0-9]{9}$/.test(cleanPhone)) {
      setWorkerError('Please enter a valid 10-digit Indian mobile number');
      return;
    }
    if (!workerForm.service) {
      setWorkerError('Please select your service category');
      return;
    }
    if (!workerForm.locality.trim()) {
      setWorkerError('Please enter your location / service area');
      return;
    }
    if (!workerForm.experience.trim()) {
      setWorkerError('Please enter your experience');
      return;
    }
    if (!workerForm.consent) {
      setWorkerError('Please agree to share your details for onboarding enquiries');
      return;
    }

    const lines = [
      'NexHome – Professional Registration',
      `Name: ${workerForm.name.trim()}`,
      `Mobile: ${cleanPhone}`,
      `Service: ${workerForm.service}`,
      `Location / Area: ${workerForm.locality.trim()}`,
      `Experience: ${workerForm.experience.trim()}`
    ];
    if (workerForm.availability) {
      lines.push(`Availability: ${workerForm.availability}`);
    }
    lines.push('I am interested in registering as a service professional with NexHome in Nuzvid and Nearby Areas.');
    lines.push('Sent via NexHome website.');

    const url = buildWhatsAppUrl(lines.join('\n'));
    window.open(url, '_blank', 'noopener,noreferrer');
    resetWorkerRegistration();
  };

  const value = {
    customerForm,
    setCustomerForm,
    customerError,
    customerSuccess,
    handleCustomerSubmit,
    workerForm,
    setWorkerForm,
    workerError,
    workerSuccess,
    workerMode,
    setWorkerMode,
    resetWorkerRegistration,
    handleWorkerSubmit,
    openFaqIndex,
    setOpenFaqIndex,
    webRegisterModalOpen,
    setWebRegisterModalOpen
  };

  return <SiteStateContext.Provider value={value}>{children}</SiteStateContext.Provider>;
}
