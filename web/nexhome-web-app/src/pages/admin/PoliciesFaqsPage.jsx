import { CheckCircle2, CircleHelp, Eye, FilePlus2, FileText, Pencil, Plus, Search, Send, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Modal from '../../components/admin/common/Modal';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import PagedTable from '../../components/admin/payment/PagedTable';
import { MoneyTabs, MoneyTile, When } from '../../components/admin/payment/moneyParts';
import ConfirmDialog from '../../components/admin/system/ConfirmDialog';
import RowActions from '../../components/admin/system/RowActions';
import {
  cardClass,
  primaryButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import useDebounced from '../../hooks/useDebounced';
import { formatNumber } from '../../utils/format';
import { cleanRichText, richTextLength, toRichText } from '../../utils/richText';
import { TIME_OPTIONS, apiDateTimeParts, toInstant, todayInIndia } from '../../utils/schedule';

/** POLICY_AUDIENCE values (FAQ_AUDIENCE has the same and WEB). */
const AUDIENCES = [
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'PARTNER', label: 'Partner' },
  { value: 'BOTH', label: 'Customer & Partner' }
];
/** FAQ_AUDIENCE values: WEB is the public website (V48), the others the apps. */
const FAQ_AUDIENCES = [{ value: 'WEB', label: 'Web Audiences' }, ...AUDIENCES];
/** A tile count, null (the loading bar) until known. */
const countText = (count) => (count == null ? null : formatNumber(count));
const audienceLabel = (code) => FAQ_AUDIENCES.find((option) => option.value === code)?.label ?? code;

const badgeClass = 'whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold';
const BADGES = {
  published: `${badgeClass} bg-[#E9F6EF] text-[var(--green-2)]`,
  scheduled: `${badgeClass} bg-[#EDF6FD] text-[var(--blue)]`,
  draft: `${badgeClass} bg-[#FFF7DE] text-[#8A6508]`
};

/** When a policy version takes effect, as an instant in milliseconds (null when unknown). */
function effectiveAt(policy) {
  const parts = apiDateTimeParts(policy.effectiveFrom);
  return parts ? new Date(toInstant(parts.date, parts.time)).getTime() : null;
}

/**
 * The policy dialog's fields. The key is one of the POLICY_KEY lookup (V55: login, registration and app terms for
 * customers and partners); choosing it fills Applies To (the value's `metadata.audience`) and, on a new policy, the
 * title. The key and version are fixed once created; a new version keeps the key and asks for another version.
 */
function policyFields(mode, policyKeys) {
  const keyOf = (code) => policyKeys.find((value) => value.valueCode === code);
  return [
    {
      key: 'policyKey',
      label: 'Policy Key',
      type: 'select',
      required: true,
      disabled: mode !== 'create',
      // An older policy's key that is not in the list still shows
      options: (values) => {
        const options = policyKeys.map((value) => ({ value: value.valueCode, label: value.displayName }));
        return values.policyKey && !keyOf(values.policyKey) ? [{ value: values.policyKey, label: values.policyKey }, ...options] : options;
      },
      hint: 'Which policy this is; the apps open each one by its key (Settings > Lookups: POLICY_KEY). Fixed once created.'
    },
    {
      key: 'version',
      label: 'Version',
      type: 'text',
      required: true,
      maxLength: 30,
      disabled: mode === 'edit',
      placeholder: 'e.g. 1.0',
      hint: 'Each version is kept; once published it never changes. Fixed once created.',
      validate: (value) =>
        /^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(value) ? '' : 'Use letters, digits, dots, hyphens and underscores'
    },
    {
      key: 'title',
      label: 'Title',
      type: 'text',
      required: true,
      maxLength: 200,
      wide: true,
      derive: mode === 'create' ? (values) => keyOf(values.policyKey)?.displayName ?? values.title : undefined
    },
    {
      key: 'audience',
      label: 'Applies To',
      type: 'select',
      required: true,
      options: AUDIENCES,
      derive: (values) => keyOf(values.policyKey)?.metadata?.audience ?? values.audience
    },
    { key: 'effectiveDate', label: 'Effective From', type: 'date', required: true },
    {
      key: 'effectiveTime',
      label: 'Effective Time',
      type: 'select',
      required: true,
      options: TIME_OPTIONS,
      hint: 'Indian time. Users are asked to accept it from then on, once it is published.'
    },
    {
      key: 'content',
      label: 'Policy Text',
      type: 'richtext',
      required: true,
      maxLength: 50000,
      hint: 'Bold, italic, underline and lists; the apps show it formatted.',
      validate: (value) => (richTextLength(value) > 50000 ? 'Policy text can have at most 50000 characters' : '')
    }
  ];
}

const FAQ_FIELDS = [
  { key: 'question', label: 'Question', type: 'text', required: true, maxLength: 500, wide: true },
  { key: 'answer', label: 'Answer', type: 'textarea', required: true, maxLength: 4000, rows: 6 },
  {
    key: 'audience',
    label: 'Shown To',
    type: 'select',
    required: true,
    options: FAQ_AUDIENCES,
    hint: 'Web Audiences: the public website. The others: the customer and partner apps.'
  },
  { key: 'displayOrder', label: 'Display Order', type: 'number', hint: 'Lower numbers show first.' },
  { key: 'isPublished', label: 'Status', type: 'toggle', onText: 'Published', offText: 'Not published: not shown yet' }
];

/** The tiles' counts: a list's totalElements with the given filters (null while loading or without access). */
function useCount(path, allowed, reloadKey) {
  const { authRequest } = useAdminAuth();
  const [count, setCount] = useState(null);
  useEffect(() => {
    if (!allowed) return undefined;
    let cancelled = false;
    authRequest(`${path}${path.includes('?') ? '&' : '?'}size=1`)
      .then((data) => !cancelled && setCount(data.totalElements))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, path, allowed, reloadKey]);
  return allowed ? count : null;
}

/** A policy version's text, read only. */
function PolicyView({ policy, onClose }) {
  return (
    <Modal title={policy.title} onClose={onClose} width="max-w-[48rem]">
      <div className="mb-3 flex flex-wrap gap-x-5 gap-y-1 text-[0.8125rem] text-[var(--muted)]">
        <span>
          <b className="text-[var(--ink)]">{policy.policyKey}</b> · Version {policy.version}
        </span>
        <span>{audienceLabel(policy.audience)}</span>
        <span>Effective {policy.effectiveFrom}</span>
      </div>
      {/* The formatted text, cleaned to the allowed tags (older plain text as paragraphs) */}
      <div
        className="nh-rich-text max-h-[60vh] overflow-y-auto rounded-xl border border-[var(--line)] bg-[var(--light)] p-4 text-sm leading-relaxed"
        dangerouslySetInnerHTML={{ __html: cleanRichText(toRichText(policy.content)) }}
      />
    </Modal>
  );
}

/**
 * Settings > Policies & FAQs (/admin/settings/policies): tiles, then the tabs **Policies** (POLICY_MANAGE: every
 * version of every policy, drafts included; with write Add, Edit and Publish a draft and New Version of a published
 * one; with delete Remove a draft) and **App FAQs** (FAQ_MANAGE: the FAQs about the app itself, with neither a
 * category nor a service (`general=true`); service FAQs stay on each service's page; with write Add, Edit,
 * Publish / Unpublish; with delete Remove). Each tab shows only with read on its function.
 */
export default function PoliciesFaqsPage() {
  const { authRequest, can } = useAdminAuth();
  const canReadPolicies = can('POLICY_MANAGE', 'READ');
  const canReadFaqs = can('FAQ_MANAGE', 'READ');
  const [chosenTab, setTab] = useState('policies');
  const tab = !canReadPolicies ? 'faqs' : !canReadFaqs ? 'policies' : chosenTab;

  const [policyAudience, setPolicyAudience] = useState('');
  const [policyStatus, setPolicyStatus] = useState('');
  const [faqAudience, setFaqAudience] = useState('');
  const [faqStatus, setFaqStatus] = useState('');
  const [faqSearch, setFaqSearch] = useState('');
  const debouncedSearch = useDebounced(faqSearch.trim(), 350);

  const [now] = useState(() => Date.now());
  const [reloadKey, setReloadKey] = useState(0);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const [policyDialog, setPolicyDialog] = useState(null); // { mode: create | edit | version, policy }
  const [viewing, setViewing] = useState(null);
  const [confirm, setConfirm] = useState(null); // { title, message, label, run }
  const [faqDialog, setFaqDialog] = useState(null);
  // The policy keys to choose from (POLICY_KEY lookup, V55)
  const [policyKeys, setPolicyKeys] = useState([]);
  useEffect(() => {
    let cancelled = false;
    authRequest('/api/lookups/values/POLICY_KEY')
      .then((values) => !cancelled && setPolicyKeys(values ?? []))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  useEffect(() => {
    document.title = 'Policies & FAQs | NexHome Admin';
  }, []);

  const policyCount = useCount('/api/policies/all', canReadPolicies, reloadKey);
  const publishedPolicies = useCount('/api/policies/all?isPublished=true', canReadPolicies, reloadKey);
  const faqCount = useCount('/api/faqs/all?general=true', canReadFaqs, reloadKey);
  const publishedFaqs = useCount('/api/faqs/all?general=true&isPublished=true', canReadFaqs, reloadKey);

  const done = (message) => {
    setToast({ message, tone: 'success', id: Date.now() });
    setReloadKey((key) => key + 1);
  };

  // --- Policies ---
  const policyQuery = new URLSearchParams();
  if (policyAudience) policyQuery.set('audience', policyAudience);
  if (policyStatus) policyQuery.set('isPublished', policyStatus === 'published' ? 'true' : 'false');

  const savePolicy = async (values) => {
    const effectiveFrom = toInstant(values.effectiveDate, values.effectiveTime);
    const common = { title: values.title.trim(), audience: values.audience, content: cleanRichText(values.content), effectiveFrom };
    if (policyDialog.mode === 'edit') {
      await authRequest('/api/policies/update', {
        method: 'PUT',
        body: { policyDocumentId: policyDialog.policy.policyDocumentId, ...common }
      });
    } else {
      await authRequest('/api/policies/create', {
        method: 'POST',
        body: { policyKey: values.policyKey, version: values.version.trim(), ...common }
      });
    }
    setPolicyDialog(null);
    done(policyDialog.mode === 'edit' ? 'Policy updated.' : 'Policy saved as a draft. Publish it when it is ready.');
  };

  const policyInitial = (dialog) => {
    const policy = dialog.policy;
    const parts = policy ? apiDateTimeParts(policy.effectiveFrom) : null;
    return {
      policyKey: policy?.policyKey ?? '',
      version: dialog.mode === 'edit' ? policy.version : '',
      title: policy?.title ?? '',
      audience: policy?.audience ?? 'CUSTOMER',
      effectiveDate: dialog.mode === 'edit' && parts ? parts.date : todayInIndia(),
      effectiveTime: dialog.mode === 'edit' && parts ? parts.time : '00:00',
      // Older plain-text policies become paragraphs
      content: toRichText(policy?.content ?? '')
    };
  };

  const policyActions = (policy) => {
    const actions = [{ label: 'View', icon: Eye, onClick: () => setViewing(policy) }];
    if (can('POLICY_MANAGE', 'WRITE')) {
      if (policy.isPublished) {
        actions.push({ label: 'New Version', icon: FilePlus2, onClick: () => setPolicyDialog({ mode: 'version', policy }) });
      } else {
        actions.push({ label: 'Edit', icon: Pencil, onClick: () => setPolicyDialog({ mode: 'edit', policy }) });
        actions.push({
          label: 'Publish',
          icon: Send,
          onClick: () =>
            setConfirm({
              title: 'Publish Policy',
              message: `Publish "${policy.title}" version ${policy.version}? From ${policy.effectiveFrom} users are asked to accept it. A published version cannot be changed or unpublished; changes need a new version.`,
              label: 'Publish',
              run: async () => {
                await authRequest('/api/policies/publish', {
                  method: 'PUT',
                  body: { policyDocumentId: policy.policyDocumentId }
                });
                done('Policy published.');
              }
            })
        });
      }
    }
    if (!policy.isPublished && can('POLICY_MANAGE', 'DELETE')) {
      actions.push({
        label: 'Remove',
        icon: Trash2,
        danger: true,
        onClick: () =>
          setConfirm({
            title: 'Remove Draft',
            message: `Remove the draft "${policy.title}" version ${policy.version}?`,
            label: 'Remove',
            run: async () => {
              await authRequest(`/api/policies/delete/${policy.policyDocumentId}`, { method: 'DELETE' });
              done('Draft removed.');
            }
          })
      });
    }
    return actions;
  };

  const policyColumns = [
    {
      header: 'Policy',
      className: 'max-w-0 w-[34%]',
      cell: (row) => (
        <button type="button" onClick={() => setViewing(row)} className="block w-full min-w-0 text-left">
          <span className="block truncate font-bold text-[var(--blue)]" title={row.title}>
            {row.title}
          </span>
          <span className="block truncate font-mono text-[0.75rem] text-[var(--muted)]">{row.policyKey}</span>
        </button>
      )
    },
    { header: 'Version', cell: (row) => <span className="font-bold">{row.version}</span> },
    { header: 'Applies To', cell: (row) => audienceLabel(row.audience) },
    {
      header: 'Status',
      cell: (row) => {
        if (!row.isPublished) return <span className={BADGES.draft}>Draft</span>;
        const at = effectiveAt(row);
        return at && at > now ? (
          <span className={BADGES.scheduled}>Scheduled</span>
        ) : (
          <span className={BADGES.published}>Published</span>
        );
      }
    },
    { header: 'Effective From', cell: (row) => <When value={row.effectiveFrom} /> },
    { header: 'Published', cell: (row) => <When value={row.publishedAt} /> },
    { header: '', headClassName: 'w-12', cell: (row) => <RowActions label={`Actions for ${row.title}`} actions={policyActions(row)} /> }
  ];

  // --- App FAQs ---
  const faqQuery = new URLSearchParams({ general: 'true' });
  if (faqAudience) faqQuery.set('audience', faqAudience);
  if (faqStatus) faqQuery.set('isPublished', faqStatus === 'published' ? 'true' : 'false');
  if (debouncedSearch) faqQuery.set('search', debouncedSearch);

  const faqBody = (faq, overrides = {}) => ({
    faqId: faq.faqId,
    question: faq.question,
    answer: faq.answer,
    audience: faq.audience,
    displayOrder: faq.displayOrder,
    isPublished: faq.isPublished,
    ...overrides
  });

  const saveFaq = async (values) => {
    const body = {
      question: values.question.trim(),
      answer: values.answer.trim(),
      audience: values.audience,
      displayOrder: values.displayOrder === '' ? 0 : Number(values.displayOrder),
      isPublished: values.isPublished
    };
    if (faqDialog.faqId) {
      await authRequest('/api/faqs/update', { method: 'PUT', body: { faqId: faqDialog.faqId, ...body } });
    } else {
      // Neither a category nor a service: an app FAQ
      await authRequest('/api/faqs/create', { method: 'POST', body });
    }
    setFaqDialog(null);
    done(faqDialog.faqId ? 'FAQ updated.' : 'FAQ added.');
  };

  const faqActions = (faq) => {
    const actions = [];
    if (can('FAQ_MANAGE', 'WRITE')) {
      actions.push({ label: 'Edit', icon: Pencil, onClick: () => setFaqDialog(faq) });
      actions.push({
        label: faq.isPublished ? 'Unpublish' : 'Publish',
        icon: faq.isPublished ? CircleHelp : Send,
        onClick: async () => {
          try {
            await authRequest('/api/faqs/update', {
              method: 'PUT',
              body: faqBody(faq, { isPublished: !faq.isPublished })
            });
            done(faq.isPublished ? 'FAQ unpublished.' : 'FAQ published.');
          } catch (err) {
            setToast({ message: err.message, tone: 'error', id: Date.now() });
          }
        }
      });
    }
    if (can('FAQ_MANAGE', 'DELETE')) {
      actions.push({
        label: 'Remove',
        icon: Trash2,
        danger: true,
        onClick: () =>
          setConfirm({
            title: 'Remove FAQ',
            message: `Remove "${faq.question}"? Customers and partners will no longer see it.`,
            label: 'Remove',
            run: async () => {
              await authRequest(`/api/faqs/delete/${faq.faqId}`, { method: 'DELETE' });
              done('FAQ removed.');
            }
          })
      });
    }
    return actions;
  };

  const faqColumns = [
    {
      header: 'Question',
      className: 'max-w-0 w-[46%]',
      cell: (row) => (
        <span className="block min-w-0">
          <span className="block truncate font-bold" title={row.question}>
            {row.question}
          </span>
          <span className="block truncate text-[0.8125rem] text-[var(--muted)]" title={row.answer}>
            {row.answer}
          </span>
        </span>
      )
    },
    { header: 'Shown To', cell: (row) => audienceLabel(row.audience) },
    { header: 'Order', cell: (row) => row.displayOrder },
    {
      header: 'Status',
      cell: (row) =>
        row.isPublished ? (
          <span className={BADGES.published}>Published</span>
        ) : (
          <span className={BADGES.draft}>Not published</span>
        )
    },
    { header: 'Added', cell: (row) => <When value={row.creationDate} /> },
    ...(can('FAQ_MANAGE', 'WRITE') || can('FAQ_MANAGE', 'DELETE')
      ? [
          {
            header: '',
            headClassName: 'w-12',
            cell: (row) => <RowActions label={`Actions for ${row.question}`} actions={faqActions(row)} />
          }
        ]
      : [])
  ];

  const tabs = [
    canReadPolicies && { key: 'policies', label: 'Policies' },
    canReadFaqs && { key: 'faqs', label: 'App FAQs' }
  ].filter(Boolean);

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Settings' }, { label: 'Policies & FAQs' }]}
        title="Policies & FAQs"
        description="The terms and policies customers and partners accept, and the frequently asked questions about the app."
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        {canReadPolicies && (
          <>
            <MoneyTile icon={FileText} tone="bg-[#EDF6FD] text-[var(--blue)]" label="Policy Versions" value={countText(policyCount)} />
            <MoneyTile
              icon={CheckCircle2}
              tone="bg-[#E9F6EF] text-[var(--green)]"
              label="Published Policies"
              value={countText(publishedPolicies)}
              note={policyCount != null && publishedPolicies != null ? `${formatNumber(policyCount - publishedPolicies)} drafts` : undefined}
            />
          </>
        )}
        {canReadFaqs && (
          <>
            <MoneyTile icon={CircleHelp} tone="bg-[#FFF7DE] text-[#B7860B]" label="App FAQs" value={countText(faqCount)} />
            <MoneyTile
              icon={Send}
              tone="bg-[#E8EEF6] text-[var(--navy)]"
              label="Published FAQs"
              value={countText(publishedFaqs)}
              note={faqCount != null && publishedFaqs != null ? `${formatNumber(faqCount - publishedFaqs)} not published` : undefined}
            />
          </>
        )}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <MoneyTabs tabs={tabs} active={tab} onChange={setTab} />
          {/* The tab's filters, then its Add button */}
          {tab === 'policies' ? (
            <div className="mb-4 flex items-center gap-3">
              <div className="w-52 flex-shrink-0">
                <Select
                  ariaLabel="Applies to"
                  value={policyAudience}
                  onChange={setPolicyAudience}
                  options={[{ value: '', label: 'All Audiences' }, ...AUDIENCES]}
                />
              </div>
              <div className="w-40 flex-shrink-0">
                <Select
                  ariaLabel="Status"
                  value={policyStatus}
                  onChange={setPolicyStatus}
                  options={[
                    { value: '', label: 'All Statuses' },
                    { value: 'published', label: 'Published' },
                    { value: 'draft', label: 'Drafts' }
                  ]}
                />
              </div>
              {can('POLICY_MANAGE', 'WRITE') && (
                <button
                  type="button"
                  className={`${primaryButtonClass} flex-shrink-0 whitespace-nowrap`}
                  onClick={() => setPolicyDialog({ mode: 'create' })}
                >
                  <Plus size={16} strokeWidth={2.6} />
                  Add Policy
                </button>
              )}
            </div>
          ) : (
            <div className="mb-4 flex items-center gap-3">
              <label className="relative w-64 flex-shrink-0">
                <span className="sr-only">Search questions</span>
                <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
                <input
                  type="search"
                  className={searchInputClass}
                  placeholder="Search"
                  value={faqSearch}
                  onChange={(e) => setFaqSearch(e.target.value)}
                />
              </label>
              <div className="w-48 flex-shrink-0">
                <Select
                  ariaLabel="Shown to"
                  value={faqAudience}
                  onChange={setFaqAudience}
                  options={[{ value: '', label: 'All Audiences' }, ...FAQ_AUDIENCES]}
                />
              </div>
              <div className="w-48 flex-shrink-0">
                <Select
                  ariaLabel="Status"
                  value={faqStatus}
                  onChange={setFaqStatus}
                  options={[
                    { value: '', label: 'All Statuses' },
                    { value: 'published', label: 'Published' },
                    { value: 'draft', label: 'Not published' }
                  ]}
                />
              </div>
              {can('FAQ_MANAGE', 'WRITE') && (
                <button
                  type="button"
                  className={`${primaryButtonClass} flex-shrink-0 whitespace-nowrap`}
                  onClick={() => setFaqDialog({})}
                >
                  <Plus size={16} strokeWidth={2.6} />
                  Add FAQ
                </button>
              )}
            </div>
          )}
        </div>
        {tab === 'policies' ? (
          <PagedTable
            path={`/api/policies/all${policyQuery.toString() ? `?${policyQuery}` : ''}`}
            columns={policyColumns}
            rowKey={(row) => row.policyDocumentId}
            reloadKey={reloadKey}
            emptyText="No policies yet."
            minWidth="62rem"
          />
        ) : (
          <PagedTable
            path={`/api/faqs/all?${faqQuery}`}
            columns={faqColumns}
            rowKey={(row) => row.faqId}
            reloadKey={reloadKey}
            emptyText="No app FAQs yet. FAQs of a service are on that service's page."
            minWidth="60rem"
          />
        )}
      </section>

      {policyDialog && (
        <FormModal
          title={
            policyDialog.mode === 'edit'
              ? 'Edit Policy Draft'
              : policyDialog.mode === 'version'
                ? `New Version of ${policyDialog.policy.policyKey}`
                : 'Add Policy'
          }
          fields={policyFields(policyDialog.mode, policyKeys)}
          initial={policyInitial(policyDialog)}
          submitLabel={policyDialog.mode === 'edit' ? 'Save Changes' : 'Save as Draft'}
          onSubmit={savePolicy}
          onClose={() => setPolicyDialog(null)}
          width="max-w-[50rem]"
        />
      )}
      {faqDialog && (
        <FormModal
          title={faqDialog.faqId ? 'Edit FAQ' : 'Add FAQ'}
          fields={FAQ_FIELDS}
          initial={{
            question: faqDialog.question ?? '',
            answer: faqDialog.answer ?? '',
            audience: faqDialog.audience ?? 'WEB',
            displayOrder: faqDialog.displayOrder != null ? String(faqDialog.displayOrder) : '',
            isPublished: faqDialog.isPublished ?? true
          }}
          submitLabel={faqDialog.faqId ? 'Save Changes' : 'Add FAQ'}
          onSubmit={saveFaq}
          onClose={() => setFaqDialog(null)}
          width="max-w-[44rem]"
        />
      )}
      {viewing && <PolicyView policy={viewing} onClose={() => setViewing(null)} />}
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          message={confirm.message}
          confirmLabel={confirm.label}
          onConfirm={async () => {
            await confirm.run();
            setConfirm(null);
          }}
          onClose={() => setConfirm(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
