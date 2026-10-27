import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';

/** How It Works page: the five steps and why NexHome. */
export default function HowItWorksPage() {
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="How It Works"
        eyebrow="HOW WE WORK"
        description="From selecting a service to discussing the arrangement, here is how NexHome works."
      >
        Simple steps. <span className="text-[#168F5B]">Clear communication.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        {/* 5 Steps Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-6 mb-10">
          {[
            { n: '01', t: 'Explore a service', d: 'Browse available categories and select the type of assistance you need.', i: 'search' },
            { n: '02', t: 'Share your requirement', d: 'Describe the work, location and any relevant details.', i: 'file-text' },
            { n: '03', t: 'We review your enquiry', d: 'Our team checks the request and suitable professional availability.', i: 'message-circle' },
            { n: '04', t: 'Discuss & confirm', d: 'Review the proposed scope, charges and service arrangements.', i: 'check-circle' },
            { n: '05', t: 'Complete & give feedback', d: 'After the work is completed, share feedback to help us improve.', i: 'heart' }
          ].map((step) => (
            <div key={step.n} className="bg-white border border-gray-100 rounded-3xl p-8 shadow-sm hover:shadow-md transition flex flex-col relative">
              <div className="text-[20px] font-black text-gray-300 absolute top-4 right-6">{step.n}</div>
              <div className="w-14 h-14 rounded-2xl bg-blue-50 text-[#0B438B] flex items-center justify-center mb-6">
                <Icon name={step.i} size={28} />
              </div>
              <h3 className="text-base font-extrabold text-[#0B438B] mb-3">{step.t}</h3>
              <p className="text-xs text-gray-600 leading-relaxed m-0">{step.d}</p>
            </div>
          ))}
        </div>

        {/* Notice Box */}
        <div className="bg-blue-50/50 border border-blue-100 rounded-2xl p-5 flex items-start gap-4 text-blue-900 mb-12">
          <Icon name="info" size={24} className="flex-shrink-0 mt-0.5" />
          <p className="text-xs sm:text-sm font-medium leading-relaxed">
            Submitting a request does not confirm service. A booking is only arranged after work scope, price and professional availability are confirmed.
          </p>
        </div>

        {/* Why NexHome Section */}
        <div className="bg-white rounded-3xl p-8 sm:p-12 border border-gray-100 shadow-sm">
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 mb-10">
            <div>
              <span className="text-xs font-bold text-[#0B438B] uppercase tracking-wider block mb-2">
                WHY NEXHOME
              </span>
              <h2 className="text-3xl sm:text-4xl font-black text-[#0B315F] tracking-tight leading-tight">
                Built around <span className="text-[#168F5B]">your everyday needs</span>.
              </h2>
            </div>
            <p className="text-sm text-gray-600 max-w-sm m-0 leading-relaxed">
              Our focus is on practical service coordination and a better customer experience.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { t: 'Multiple services, one place', d: 'Explore household help without searching separately for every task.', i: 'layers' },
              { t: 'Focused on Nuzvid & Nearby Areas', d: 'A local service platform built around the needs of Nuzvid and Nearby Areas.', i: 'map-pin' },
              { t: 'Easy communication', d: 'Tell us what you need through WhatsApp or the website.', i: 'message-circle' },
              { t: 'Clear requirements', d: 'Discuss work scope and charges before any service arrangement.', i: 'file-text' },
              { t: 'Relevant professionals', d: 'We aim to connect requests with professionals based on their skills.', i: 'users' },
              { t: 'People-first support', d: 'Reach our team during published business hours.', i: 'headphones' }
            ].map((item, idx) => (
              <div key={idx} className="bg-[#F8FAFC] border border-gray-100 rounded-2xl p-6 shadow-sm hover:shadow-md transition flex flex-col">
                <div className="w-12 h-12 rounded-2xl bg-white text-[#168F5B] flex items-center justify-center mb-4 border border-green-100 shadow-sm">
                  <Icon name={item.i} size={24} />
                </div>
                <h3 className="text-base font-extrabold text-[#0B438B] mb-2">{item.t}</h3>
                <p className="text-xs text-gray-600 leading-relaxed m-0">{item.d}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
