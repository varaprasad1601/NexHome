import { Check, Copy, RefreshCw, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import PagedTable from '../../components/admin/payment/PagedTable';
import { When } from '../../components/admin/payment/moneyParts';
import { cardClass, moduleLabel, resetButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import useDebounced from '../../hooks/useDebounced';

const STATUS_CLASS = {
  PENDING: 'bg-[#E7F6EE] text-[#168F5B]',
  VERIFIED: 'bg-[#EDF6FD] text-[var(--blue)]',
  EXPIRED: 'bg-[#F1F4F8] text-[var(--muted)]',
  LOCKED: 'bg-[#FDECEC] text-[#C53030]'
};
const ROLE_CLASS = {
  CUSTOMER: 'bg-[#EDF6FD] text-[var(--blue)]',
  PARTNER: 'bg-[#E7F6EE] text-[#168F5B]',
  STAFF: 'bg-[#FFF4E5] text-[#B7600B]'
};
const STAFF_ROLES = ['SUPER_USER', 'ADMIN', 'SUB_ADMIN'];

/**
 * The login's role: the app that asked for the code (Customer, Partner), or for the admin console the account's
 * highest staff role (Super User, Admin, Sub Admin); older codes without the app: the account's first role.
 */
function roleOf(row) {
  const roles = row.roles ?? [];
  if (row.requestedFrom === 'STAFF') return STAFF_ROLES.find((role) => roles.includes(role)) ?? 'STAFF';
  if (row.requestedFrom) return row.requestedFrom;
  return roles.find((role) => ['CUSTOMER', 'PARTNER'].includes(role)) ?? roles[0] ?? null;
}

/** "+91 98765 43210". */
function phone(countryCode, mobile) {
  const spaced = mobile?.length === 10 ? `${mobile.slice(0, 5)} ${mobile.slice(5)}` : mobile;
  return `${countryCode ?? ''} ${spaced ?? ''}`.trim();
}

/** The code, large, with a copy button; a dash once it can no longer be used. */
function OtpCell({ otp }) {
  const [copied, setCopied] = useState(false);
  if (!otp) return <span className="text-[#A8B6C6]">—</span>;
  const copy = () => {
    navigator.clipboard?.writeText(otp).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };
  return (
    <span className="inline-flex items-center gap-2">
      <span className="rounded-md bg-[#E8EEF6] px-2 py-0.5 font-mono text-[0.95rem] font-bold tracking-[0.2em] text-[var(--navy)]">{otp}</span>
      <button type="button" onClick={copy} aria-label="Copy OTP" title="Copy" className="text-[var(--muted)] hover:text-[var(--blue)]">
        {copied ? <Check size={15} className="text-[#168F5B]" /> : <Copy size={15} />}
      </button>
    </span>
  );
}

/**
 * Login OTPs (/admin/users/login-otps and /admin/system/login-otps, LOGIN_OTP_VIEW read; temporary until OTPs are
 * sent by SMS): customers' and partners' login and sign-up OTPs from GET /api/auth/otps, newest first: user id, role,
 * mobile number, the code (only while it can still be used) with Copy, purpose, status and when it was sent / expires.
 * Role is the login's: the app that asked (Customer, Partner; V65) or, from the admin console, the staff role. Super
 * users' and admins' codes are listed for super users only. Search by phone number; refreshes every 10 seconds.
 */
export default function LoginOtpsPage() {
  const { pathname } = useLocation();
  const group = pathname.startsWith('/admin/system') ? 'System Configuration' : 'User Management';
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.replace(/\D/g, ''));
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    document.title = 'Login OTPs | NexHome Admin';
    // Every 30 s, only while the tab is visible
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') setReloadKey((key) => key + 1);
    }, 30000);
    return () => clearInterval(timer);
  }, []);

  const path = `/api/auth/otps${search ? `?search=${encodeURIComponent(search)}` : ''}`;

  const columns = [
    { header: 'User ID', cell: (row) => <span className="font-bold text-[var(--navy)]">{row.userId}</span> },
    {
      header: 'Role',
      cell: (row) => {
        const role = roleOf(row);
        return role ? (
          <span className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-[0.75rem] font-bold ${ROLE_CLASS[role] ?? 'bg-[#FFF4E5] text-[#B7600B]'}`}>
            {moduleLabel(role)}
          </span>
        ) : (
          <span className="text-[#A8B6C6]">—</span>
        );
      }
    },
    { header: 'Mobile Number', cell: (row) => <span className="whitespace-nowrap">{phone(row.countryCode, row.mobileNumber)}</span> },
    { header: 'OTP', cell: (row) => <OtpCell otp={row.otp} /> },
    { header: 'Purpose', cell: (row) => <span className="whitespace-nowrap">{row.purpose === 'REGISTRATION' ? 'Sign Up' : 'Login'}</span> },
    {
      header: 'Status',
      cell: (row) => (
        <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${STATUS_CLASS[row.requestStatus] ?? STATUS_CLASS.EXPIRED}`}>
          {moduleLabel(row.requestStatus)}
        </span>
      )
    },
    { header: 'Sent At', cell: (row) => <When value={row.creationDate} /> },
    { header: 'Expires At', cell: (row) => <When value={row.expiresAt} /> }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: group }, { label: 'Login OTPs' }]}
        title="Login OTPs"
        description="Login and sign-up codes, to share until OTPs are sent by SMS; admins' codes are shown to super users only. A code shows only while it can still be used."
      />
      <section className={cardClass}>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <label className="relative w-full sm:w-[20rem]">
            <span className="sr-only">Search by phone number</span>
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
            <input
              type="search"
              inputMode="numeric"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search by phone number..."
              className={searchInputClass}
            />
          </label>
          <button type="button" onClick={() => setReloadKey((key) => key + 1)} className={resetButtonClass}>
            <RefreshCw size={15} />
            Refresh
          </button>
        </div>
        <PagedTable
          path={path}
          columns={columns}
          rowKey={(row) => row.otpRequestId}
          reloadKey={reloadKey}
          emptyText={search ? 'No OTPs for this phone number.' : 'No login OTPs yet.'}
        />
      </section>
    </div>
  );
}
