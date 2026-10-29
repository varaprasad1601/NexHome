import { useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import PagedTable from '../../components/admin/payment/PagedTable';
import { MoneyBadge, MoneyTabs, PartnerLink, When } from '../../components/admin/payment/moneyParts';
import { cardClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../data/paymentOptions';
import useLookupLabels from '../../hooks/useLookupLabels';

/**
 * Payments & Payouts > Transactions (/admin/payments/transactions): the gateway's side. Transfers (money sent to
 * partners, PAYOUT_MANAGE read) and Webhook Events (what the payment provider reported, with whether the signature
 * matched and how it was processed, PAYMENT_MANAGE read); each tab only with its read.
 */
export default function TransactionsPage() {
  const { can } = useAdminAuth();
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  const tabs = [
    ...(can('PAYOUT_MANAGE', 'READ') ? [{ key: 'transfers', label: 'Transfers' }] : []),
    ...(can('PAYMENT_MANAGE', 'READ') ? [{ key: 'webhooks', label: 'Webhook Events' }] : [])
  ];
  const [tab, setTab] = useState(null);
  const active = tab ?? tabs[0]?.key;

  useEffect(() => {
    document.title = 'Transactions | NexHome Admin';
  }, []);

  const transferColumns = [
    { header: 'Payout', cell: (row) => <span className="font-black">{row.payoutPublicId}</span> },
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName} /> },
    { header: 'Amount', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
    {
      header: 'Status',
      cell: (row) => <MoneyBadge status={row.status} label={label('TRANSFER_STATUS', row.status)} />
    },
    {
      header: 'Reference',
      cell: (row) => <span className="text-[var(--muted)]">{row.providerTransferId ?? '—'}</span>
    },
    { header: 'Settled', cell: (row) => <When value={row.settledAt} /> }
  ];
  const webhookColumns = [
    { header: 'Event', cell: (row) => <span className="font-bold">{row.eventType}</span> },
    { header: 'Provider', cell: (row) => row.provider },
    { header: 'Event ID', cell: (row) => <span className="text-[var(--muted)]">{row.providerEventId}</span> },
    {
      header: 'Signature',
      cell: (row) => (
        <MoneyBadge
          status={row.signatureVerified ? 'PROCESSED' : 'FAILED'}
          label={row.signatureVerified ? 'Verified' : 'Invalid'}
        />
      )
    },
    {
      header: 'Processing',
      cell: (row) => (
        <>
          <MoneyBadge status={row.processingStatus} label={label('WEBHOOK_PROCESSING_STATUS', row.processingStatus)} />
          {row.processingError && <div className="mt-1 text-[0.75rem] text-[var(--muted)]">{row.processingError}</div>}
        </>
      )
    },
    { header: 'Received', cell: (row) => <When value={row.receivedAt} /> }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Payments & Payouts' }, { label: 'Transactions' }]}
        title="Transactions"
        description="Transfers to partners and the payment provider's events (simulated MANUAL provider for now)."
      />
      <section className={cardClass}>
        {tabs.length > 0 && <MoneyTabs tabs={tabs} active={active} onChange={setTab} />}
        {active === 'transfers' && (
          <PagedTable
            path="/api/payouts/transfers"
            columns={transferColumns}
            rowKey={(row) => row.transferId}
            emptyText="No transfers yet."
          />
        )}
        {active === 'webhooks' && (
          <PagedTable
            path="/api/payments/webhooks"
            columns={webhookColumns}
            rowKey={(row) => row.webhookEventId}
            emptyText="No webhook events yet."
          />
        )}
      </section>
    </div>
  );
}
