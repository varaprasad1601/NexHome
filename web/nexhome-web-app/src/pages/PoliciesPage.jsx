import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { apiRequest } from '../api/client';
import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';
import { cleanRichText } from '../utils/richText';

/** The website's documents: policy keys written and published by staff (Settings › Policies & FAQs, V71). */
const DOCUMENTS = [
  { key: 'WEB_TERMS', tab: 'terms', label: 'Terms & Conditions', icon: 'file-text' },
  { key: 'WEB_POLICY', tab: 'privacy', label: 'Privacy Policy', icon: 'shield' }
];

/** "Thu, 08 Oct 2026 14:29:20 IST" → "08 Oct 2026". */
function dayOf(value) {
  const match = /^\w{3}, (\d{2} \w{3} \d{4})/.exec(value ?? '');
  return match ? match[1] : value;
}

/**
 * Policies & Terms (/policies; ?tab=terms or privacy): the current published versions of WEB_TERMS and WEB_POLICY
 * (public GET /api/policies/current/{key}), formatted as written in the console. A document not published yet says
 * so.
 */
export default function PoliciesPage() {
  const [params, setParams] = useSearchParams();
  const active = DOCUMENTS.find((doc) => doc.tab === params.get('tab')) ?? DOCUMENTS[0];
  // { [key]: { status: 'loading' | 'ready' | 'missing' | 'error', document } }
  const [docs, setDocs] = useState({});

  useEffect(() => {
    let cancelled = false;
    DOCUMENTS.forEach((doc) => {
      apiRequest(`/api/policies/current/${doc.key}`)
        .then((document) => !cancelled && setDocs((current) => ({ ...current, [doc.key]: { status: 'ready', document } })))
        .catch((err) => {
          if (cancelled) return;
          setDocs((current) => ({ ...current, [doc.key]: { status: err.status === 404 ? 'missing' : 'error' } }));
        });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const shown = docs[active.key] ?? { status: 'loading' };

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Policies & Terms"
        eyebrow="IMPORTANT INFORMATION"
        description="The terms that apply when you use NexHome, and how we look after your information."
      >
        Policies <span className="text-[#168F5B]">& terms.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">
        <div className="mb-6 flex flex-wrap gap-2" role="tablist">
          {DOCUMENTS.map((doc) => {
            const on = doc.key === active.key;
            return (
              <button
                key={doc.key}
                type="button"
                role="tab"
                aria-selected={on}
                onClick={() => setParams({ tab: doc.tab }, { replace: true })}
                className={`inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-bold transition ${
                  on ? 'bg-[#0B438B] text-white shadow-sm' : 'bg-white text-[#0B315F] border border-gray-200 hover:border-[#0B438B]'
                }`}
              >
                <Icon name={doc.icon} size={16} />
                {doc.label}
              </button>
            );
          })}
        </div>

        <div>
          <article className="bg-white rounded-3xl p-8 sm:p-12 border border-gray-100 shadow-sm">
            {shown.status === 'loading' ? (
              <div className="space-y-3" aria-busy="true">
                <div className="h-7 w-2/3 rounded-lg bg-gray-100 animate-pulse" />
                <div className="h-4 w-full rounded bg-gray-100 animate-pulse" />
                <div className="h-4 w-5/6 rounded bg-gray-100 animate-pulse" />
                <div className="h-4 w-4/6 rounded bg-gray-100 animate-pulse" />
              </div>
            ) : shown.status === 'ready' ? (
              <>
                <h2 className="text-2xl font-black text-[#0B315F] mb-2">{shown.document.title}</h2>
                <p className="text-xs text-gray-500 mb-8">
                  Version {shown.document.version}
                  {shown.document.effectiveFrom ? ` · Effective from ${dayOf(shown.document.effectiveFrom)}` : ''}
                </p>
                <div
                  className="text-sm text-gray-700 leading-relaxed [&_p]:mb-4 [&_ul]:mb-4 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:mb-4 [&_ol]:list-decimal [&_ol]:pl-6 [&_li]:mb-1.5 [&_b]:font-bold [&_strong]:font-bold [&_i]:italic [&_em]:italic [&_u]:underline"
                  // The backend keeps only basic formatting (p, br, b, strong, i, em, u, ul, ol, li); cleaned again here
                  dangerouslySetInnerHTML={{ __html: cleanRichText(shown.document.content) }}
                />
              </>
            ) : (
              <>
                <div className="w-14 h-14 rounded-2xl bg-blue-50 text-[#0B438B] flex items-center justify-center mb-6">
                  <Icon name={active.icon} size={28} />
                </div>
                <h2 className="text-2xl font-black text-[#0B315F] mb-4">
                  {shown.status === 'missing' ? `Our ${active.label} will be published soon.` : `We could not load the ${active.label}.`}
                </h2>
                <p className="text-sm text-gray-600 leading-relaxed mb-8">
                  {shown.status === 'missing'
                    ? 'It is being finalized. For any question about how a service is handled, please reach out.'
                    : 'Please check your connection and try again, or contact us for a copy.'}
                </p>
                <AppLink
                  to="/contact"
                  className="bg-[#0B438B] hover:bg-[#08336a] text-white px-8 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-sm transition"
                >
                  Ask NexHome <Icon name="arrow-right" size={16} />
                </AppLink>
              </>
            )}
          </article>

        </div>
      </div>
    </div>
  );
}
