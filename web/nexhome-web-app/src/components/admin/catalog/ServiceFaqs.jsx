import { ChevronDown, CircleHelp, Pencil, Plus, Trash2, UserRound, Wrench } from 'lucide-react';
import { useState } from 'react';

import useClientPaging from '../../../hooks/useClientPaging';
import FormModal from '../common/FormModal';
import Pagination from '../common/Pagination';
import ConfirmDialog from '../system/ConfirmDialog';

const SIDES = [
  { audience: 'CUSTOMER', title: 'Customer FAQs', text: 'Shown to customers with the service.', icon: UserRound },
  { audience: 'PARTNER', title: 'Partner FAQs', text: 'Shown to partners who do the job.', icon: Wrench }
];

const AUDIENCE_OPTIONS = [
  { value: 'CUSTOMER', label: 'Customers' },
  { value: 'PARTNER', label: 'Partners' },
  { value: 'BOTH', label: 'Customers and partners' }
];

const FAQ_FIELDS = [
  { key: 'question', label: 'Question', type: 'text', required: true, maxLength: 500, wide: true },
  { key: 'answer', label: 'Answer', type: 'textarea', required: true, maxLength: 4000, rows: 5 },
  { key: 'audience', label: 'Shown To', type: 'select', required: true, options: AUDIENCE_OPTIONS },
  { key: 'displayOrder', label: 'Display Order', type: 'number', hint: 'Lower numbers show first.' },
  { key: 'isPublished', label: 'Status', type: 'toggle', onText: 'Published', offText: 'Not published: not shown yet' }
];

/** One column: the side's FAQs as questions that open to their answers, paged, with Add / Edit for writers. */
function FaqColumn({ side, faqs, canEdit, canDelete, onAdd, onEdit, onRemove }) {
  const [open, setOpen] = useState(null);
  const { pageRows, pageData, setPage } = useClientPaging(faqs);
  const Icon = side.icon;
  return (
    // A full-height column, so the paging sits at the bottom of both, level with each other
    <section className="flex h-full flex-col rounded-2xl border border-[var(--line)] p-5">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
            <Icon size={17} />
          </span>
          <div>
            <h3 className="mb-0 flex items-center gap-2 text-[1.0625rem] font-black text-[var(--navy)]">
              {side.title}
              <span className="rounded-full bg-[var(--light)] px-2 text-[0.75rem] font-bold text-[var(--muted)]">
                {faqs.length}
              </span>
            </h3>
            <div className="text-xs text-[var(--muted)]">{side.text}</div>
          </div>
        </div>
        {canEdit && (
          <button
            type="button"
            onClick={onAdd}
            className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold text-[var(--blue)] hover:bg-[#EDF6FD]"
          >
            <Plus size={15} strokeWidth={2.6} />
            Add FAQ
          </button>
        )}
      </div>

      {faqs.length === 0 ? (
        <p className="flex items-center gap-2 rounded-xl bg-[var(--light)] px-4 py-6 text-sm text-[var(--muted)]">
          <CircleHelp size={16} />
          No FAQs yet.
        </p>
      ) : (
        <>
          <ul className="m-0 mb-auto list-none divide-y divide-[var(--line)] rounded-xl border border-[var(--line)] p-0">
            {pageRows.map((faq) => {
              const expanded = open === faq.faqId;
              return (
                <li key={faq.faqId}>
                  <div className="flex items-start gap-2 px-4 py-3">
                    <button
                      type="button"
                      aria-expanded={expanded}
                      onClick={() => setOpen(expanded ? null : faq.faqId)}
                      className="flex min-w-0 flex-1 items-start gap-2 text-left"
                    >
                      <ChevronDown
                        size={16}
                        className={`mt-0.5 flex-shrink-0 text-[var(--muted)] transition-transform ${expanded ? 'rotate-180 text-[var(--blue)]' : ''}`}
                      />
                      <span className="min-w-0 flex-1 text-sm font-bold text-[var(--ink)]">{faq.question}</span>
                    </button>
                    <span className="flex flex-shrink-0 items-center gap-1.5">
                      {faq.audience === 'BOTH' && (
                        <span className="rounded-full bg-[#E8EEF6] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--navy)]">
                          Both
                        </span>
                      )}
                      <span
                        className={`whitespace-nowrap rounded-full px-2 py-0.5 text-[0.6875rem] font-bold ${
                          faq.isPublished ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#FFF7DE] text-[#8A6508]'
                        }`}
                      >
                        {faq.isPublished ? 'Published' : 'Not published'}
                      </span>
                      {canEdit && (
                        <button
                          type="button"
                          onClick={() => onEdit(faq)}
                          aria-label={`Edit ${faq.question}`}
                          title="Edit"
                          className="flex h-7 w-7 items-center justify-center rounded-lg text-[var(--blue)] hover:bg-[#EDF6FD]"
                        >
                          <Pencil size={14} />
                        </button>
                      )}
                      {canDelete && (
                        <button
                          type="button"
                          onClick={() => onRemove(faq)}
                          aria-label={`Remove ${faq.question}`}
                          title="Remove"
                          className="flex h-7 w-7 items-center justify-center rounded-lg text-[#E53E3E] hover:bg-[#FDECEC]"
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </span>
                  </div>
                  {expanded && (
                    <div className="whitespace-pre-line px-4 pb-4 pl-10 text-sm leading-relaxed text-[var(--muted)]">
                      {faq.answer}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="mt-2">
            <Pagination pageData={pageData} noun="FAQs" onPageChange={setPage} />
          </div>
        </>
      )}
    </section>
  );
}

/**
 * The service page's FAQs tab: Customer FAQs and Partner FAQs side by side (an FAQ for BOTH shows in each, tagged
 * Both; every FAQ shows its status, Published or Not published (unpublished ones only reach FAQ_MANAGE readers)). With FAQ_MANAGE write each column has
 * Add FAQ and each FAQ an Edit, through POST /api/faqs/create and PUT /api/faqs/update (the service is fixed); with
 * delete each FAQ also has Remove (DELETE /api/faqs/delete/{id}, after a confirmation).
 *
 * @param faqs the service's FAQs, in display order
 * @param onSaved called after a save, to reload them
 */
export default function ServiceFaqs({ serviceId, faqs, canEdit, canDelete, authRequest, onSaved }) {
  const [editing, setEditing] = useState(null); // null, { audience } for a new FAQ, or the FAQ
  const [removing, setRemoving] = useState(null);

  const save = async (values) => {
    const body = {
      question: values.question.trim(),
      answer: values.answer.trim(),
      audience: values.audience,
      displayOrder: values.displayOrder === '' ? 0 : Number(values.displayOrder),
      isPublished: values.isPublished
    };
    const saved = editing.faqId
      ? await authRequest('/api/faqs/update', { method: 'PUT', body: { faqId: editing.faqId, ...body } })
      : await authRequest('/api/faqs/create', { method: 'POST', body: { serviceId, ...body } });
    setEditing(null);
    onSaved(editing.faqId ? 'FAQ updated.' : 'FAQ added.', saved);
  };

  return (
    <>
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {SIDES.map((side) => (
          <FaqColumn
            key={side.audience}
            side={side}
            faqs={faqs.filter((faq) => faq.audience === side.audience || faq.audience === 'BOTH')}
            canEdit={canEdit}
            onAdd={() => setEditing({ audience: side.audience })}
            canDelete={canDelete}
            onEdit={setEditing}
            onRemove={setRemoving}
          />
        ))}
      </div>
      {editing && (
        <FormModal
          title={editing.faqId ? 'Edit FAQ' : 'Add FAQ'}
          fields={FAQ_FIELDS}
          initial={{
            question: editing.question ?? '',
            answer: editing.answer ?? '',
            audience: editing.audience,
            displayOrder: editing.displayOrder != null ? String(editing.displayOrder) : '',
            isPublished: editing.isPublished ?? true
          }}
          submitLabel={editing.faqId ? 'Save Changes' : 'Add FAQ'}
          onSubmit={save}
          onClose={() => setEditing(null)}
          width="max-w-[44rem]"
        />
      )}
      {removing && (
        <ConfirmDialog
          title="Remove FAQ"
          message={`Remove "${removing.question}"? Customers and partners will no longer see it.`}
          confirmLabel="Remove"
          onConfirm={async () => {
            await authRequest(`/api/faqs/delete/${removing.faqId}`, { method: 'DELETE' });
            setRemoving(null);
            onSaved('FAQ removed.');
          }}
          onClose={() => setRemoving(null)}
        />
      )}
    </>
  );
}
