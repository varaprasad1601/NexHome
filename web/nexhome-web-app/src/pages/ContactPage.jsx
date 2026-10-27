import { SERVICES, FORMATTED_PHONE, EMAIL_ADDRESS, LOCATION_TEXT, BUSINESS_HOURS } from '../data/siteData';
import Icon from '../components/common/Icon';
import MapCard from '../components/common/MapCard';
import PageHeader from '../components/common/PageHeader';
import { useSiteState } from '../context/siteState';

/** Contact page: contact details, map and the service enquiry form. */
export default function ContactPage() {
  const { customerForm, setCustomerForm, customerError, customerSuccess, handleCustomerSubmit } = useSiteState();

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Contact Us"
        eyebrow="GET TO KNOW US"
        description="Need help at home? Tell us what you’re looking for. Our team can review your enquiry during business hours."
      >
        Let’s make life <span className="text-[#168F5B]">a little easier.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start">
          {/* Left Column: Contact details & Map */}
          <div className="lg:col-span-6 space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <a
                href={`tel:${FORMATTED_PHONE.replace(/\s+/g, '')}`}
                className="p-5 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center gap-3.5 hover:shadow-md transition"
              >
                <div className="w-10 h-10 rounded-xl bg-blue-50 text-[#0B438B] flex items-center justify-center flex-shrink-0">
                  <Icon name="phone" size={18} />
                </div>
                <div>
                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">CALL US</div>
                  <div className="text-xs font-bold text-gray-800">{FORMATTED_PHONE}</div>
                </div>
              </a>
              <a
                href={`mailto:${EMAIL_ADDRESS}`}
                className="p-5 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center gap-3.5 hover:shadow-md transition"
              >
                <div className="w-10 h-10 rounded-xl bg-green-50 text-[#168F5B] flex items-center justify-center flex-shrink-0">
                  <Icon name="mail" size={18} />
                </div>
                <div>
                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">EMAIL US</div>
                  <div className="text-xs font-bold text-gray-800 break-all">{EMAIL_ADDRESS}</div>
                </div>
              </a>
              <div className="p-5 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center gap-3.5">
                <div className="w-10 h-10 rounded-xl bg-blue-50 text-[#0B438B] flex items-center justify-center flex-shrink-0">
                  <Icon name="map-pin" size={18} />
                </div>
                <div>
                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">SERVICE LOCATION</div>
                  <div className="text-xs font-bold text-gray-800">{LOCATION_TEXT}</div>
                </div>
              </div>
              <div className="p-5 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center gap-3.5">
                <div className="w-10 h-10 rounded-xl bg-green-50 text-[#168F5B] flex items-center justify-center flex-shrink-0">
                  <Icon name="clock" size={18} />
                </div>
                <div>
                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">BUSINESS HOURS</div>
                  <div className="text-xs font-bold text-gray-800">{BUSINESS_HOURS}</div>
                </div>
              </div>
            </div>

            <MapCard />
          </div>

          {/* Right Column: Customer Enquiry Form */}
          <div className="lg:col-span-6 bg-white rounded-3xl p-6 sm:p-10 border border-gray-100 shadow-sm">
            <div className="flex items-center gap-4 mb-8">
              <div className="w-12 h-12 rounded-2xl bg-[#EAF4FC] text-[#0B438B] flex items-center justify-center flex-shrink-0">
                <Icon name="message-circle" size={22} />
              </div>
              <div>
                <h3 className="text-lg font-extrabold text-[#0B315F]">Send a service enquiry</h3>
                <p className="text-xs text-gray-500 m-0">We’ll help you take the next step.</p>
              </div>
            </div>

            <form onSubmit={handleCustomerSubmit} className="space-y-5" noValidate>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-gray-700 mb-1.5">
                    Full name <span className="text-red-500 font-bold">*</span>
                  </label>
                  <input
                    type="text"
                    placeholder="Your full name"
                    maxLength={100}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                    value={customerForm.name}
                    onChange={(e) => setCustomerForm({ ...customerForm, name: e.target.value })}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-700 mb-1.5">
                    Mobile number <span className="text-red-500 font-bold">*</span>
                  </label>
                  <input
                    type="tel"
                    placeholder="10-digit mobile number"
                    maxLength={15}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                    value={customerForm.phone}
                    onChange={(e) => setCustomerForm({ ...customerForm, phone: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-gray-700 mb-1.5">
                    Service category <span className="text-red-500 font-bold">*</span>
                  </label>
                  <div className="relative">
                    <select
                      className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-700 bg-white appearance-none focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                      value={customerForm.service}
                      onChange={(e) => setCustomerForm({ ...customerForm, service: e.target.value })}
                      required
                    >
                      <option value="">Choose a service</option>
                      {SERVICES.map((s) => (
                        <option key={s.slug} value={s.name}>
                          {s.name}
                        </option>
                      ))}
                    </select>
                    <span className="absolute right-3.5 top-3 text-gray-400 pointer-events-none">
                      <Icon name="chevron-down" size={14} />
                    </span>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-700 mb-1.5">
                    Email address (optional)
                  </label>
                  <input
                    type="email"
                    placeholder="your.email@example.com"
                    maxLength={100}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                    value={customerForm.email}
                    onChange={(e) => setCustomerForm({ ...customerForm, email: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 mb-1.5">
                  Describe your requirement <span className="text-red-500 font-bold">*</span>
                </label>
                <textarea
                  rows={3}
                  placeholder="Tell us what help you need at home"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors resize-none"
                  value={customerForm.message}
                  onChange={(e) => setCustomerForm({ ...customerForm, message: e.target.value })}
                  required
                />
              </div>

              {customerError && (
                <div className="text-xs text-red-600 bg-red-50 p-3 rounded-xl border border-red-100 flex items-center gap-2">
                  <span className="font-bold">•</span> {customerError}
                </div>
              )}
              {customerSuccess && (
                <div className="text-xs text-green-700 bg-green-50 p-3 rounded-xl border border-green-100 flex items-center gap-2">
                  <Icon name="check-circle" size={16} className="text-[#168F5B]" /> {customerSuccess}
                </div>
              )}

              <div className="pt-2">
                <button
                  type="submit"
                  className="w-full bg-[#168F5B] hover:bg-[#12774B] text-white py-3.5 px-6 rounded-full text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px] cursor-pointer"
                >
                  <Icon name="message-circle" size={18} /> Continue on WhatsApp
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
