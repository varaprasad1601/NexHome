import { useId } from 'react';

import { paymentStatusClass } from '../../../data/bookingOptions';

/** Cash: two green rupee notes, one stacked on the other. */
function CashIcon({ size }) {
  const id = useId();
  return (
    <svg width={size * 1.4} height={size} viewBox="0 0 28 20" aria-hidden="true" className="flex-shrink-0">
      <defs>
        <linearGradient id={`${id}-note`} x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#E3F6E8" />
          <stop offset="1" stopColor="#B9E4C6" />
        </linearGradient>
      </defs>
      {/* The note behind */}
      <rect x="5" y="1" width="22" height="13" rx="2" fill="#7CC796" stroke="#2F7D4A" strokeWidth="1" />
      {/* The note in front, with its inner frame, the ₹ seal and corner marks */}
      <rect x="1" y="5.5" width="22" height="13" rx="2" fill={`url(#${id}-note)`} stroke="#2F7D4A" strokeWidth="1.1" />
      <rect x="3" y="7.5" width="18" height="9" rx="1.2" fill="none" stroke="#5BAF78" strokeWidth="0.6" strokeDasharray="1.2 0.8" />
      <circle cx="12" cy="12" r="3.6" fill="#FFFFFF" stroke="#2F7D4A" strokeWidth="0.9" />
      <text x="12" y="14.3" textAnchor="middle" fontSize="6" fontWeight="900" fill="#1F6B3A" fontFamily="Arial, sans-serif">
        ₹
      </text>
      <circle cx="5" cy="9.5" r="0.9" fill="#2F7D4A" />
      <circle cx="19" cy="14.5" r="0.9" fill="#2F7D4A" />
    </svg>
  );
}

/** Online: a blue bank card with a magnetic stripe, a gold chip and the contactless mark. */
function OnlineIcon({ size }) {
  const id = useId();
  return (
    <svg width={size * 1.4} height={size} viewBox="0 0 28 20" aria-hidden="true" className="flex-shrink-0">
      <defs>
        <linearGradient id={`${id}-card`} x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#3B82F6" />
          <stop offset="1" stopColor="#0B438B" />
        </linearGradient>
        <linearGradient id={`${id}-chip`} x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#FCE79A" />
          <stop offset="1" stopColor="#D9A21B" />
        </linearGradient>
      </defs>
      <rect x="1" y="1.5" width="26" height="17" rx="2.6" fill={`url(#${id}-card)`} />
      <rect x="1" y="4.6" width="26" height="2.8" fill="#0A2A55" />
      {/* Chip with its contacts */}
      <rect x="4" y="9.6" width="5.6" height="4.4" rx="0.9" fill={`url(#${id}-chip)`} stroke="#A87B10" strokeWidth="0.5" />
      <path d="M4 11.8h5.6M6.8 9.6v4.4" stroke="#A87B10" strokeWidth="0.45" />
      {/* Contactless waves */}
      <path
        d="M20.2 10.2a2.4 2.4 0 0 1 0 3.2M22 9a4.2 4.2 0 0 1 0 5.6"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth="0.9"
        strokeLinecap="round"
      />
      {/* Card number line */}
      <rect x="4" y="15.4" width="11" height="1.1" rx="0.55" fill="#BFD7FA" />
    </svg>
  );
}

const METHOD_ICONS = { CASH: CashIcon, ONLINE: OnlineIcon };

/**
 * A booking's payment status badge, after a picture of its payment method: rupee notes for cash, a bank card for
 * online (cash and online share the statuses, V40). `methodLabel` names the method for the tooltip and screen readers;
 * `size` is the badge's text size class.
 */
export default function PaymentStatusBadge({ status, statusLabel, method, methodLabel, size = 'text-[0.75rem]' }) {
  // A cancelled booking has no payment status (V42): a dash, no icon
  if (!status) return <span className="text-[var(--muted)]">—</span>;
  const Icon = METHOD_ICONS[method] ?? OnlineIcon;
  const methodName = methodLabel ?? method;
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <span className="inline-flex" title={methodName}>
        <Icon size={13} />
        <span className="sr-only">{methodName}:</span>
      </span>
      <span className={`rounded-full px-2.5 py-1 font-bold ${size} ${paymentStatusClass(status)}`}>
        {statusLabel ?? status}
      </span>
    </span>
  );
}
