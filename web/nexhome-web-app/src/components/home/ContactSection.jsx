import { SERVICES, PHONE_NUMBER, DISPLAY_PHONE, EMAIL_ADDRESS, LOCATION_TEXT, BUSINESS_HOURS } from '../../data/siteData';
import Icon from '../common/Icon';
import MapCard from '../common/MapCard';
import { useSiteState } from '../../context/siteState';

/** Connect with NexHome: contact details, enquiry form and map. */
export default function ContactSection() {
  const { customerForm, setCustomerForm, customerError, customerSuccess, handleCustomerSubmit } = useSiteState();

  return (
    <section className="py-16 bg-[#F8FAFC]" id="contact">
      <div className="container max-w-6xl">
        <div className="mb-10">
          <span className="text-[11px] font-bold text-[#0B438B] uppercase tracking-wider block mb-1">
            GET IN TOUCH
          </span>
          <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] tracking-tight mb-2">
            Connect With <span className="text-[#168F5B]">NexHome</span>
          </h2>
          <p className="text-xs text-gray-500 max-w-xl">
            Have a question or need a service? We're here to help. Reach out to us via phone, WhatsApp or email, or send us a message using the form.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
          {/* Column 1: Contact Detail Pills */}
          <div className="lg:col-span-4 bg-white border border-gray-200 rounded-3xl p-4 sm:p-6 shadow-sm flex flex-col justify-between h-full space-y-3">
            {/* Email Card */}
            <div className="bg-gray-50/70 border border-gray-100 rounded-2xl p-3.5 shadow-sm flex items-center gap-3.5 min-h-[64px] sm:h-[68px]">
              <div className="w-9 h-9 rounded-xl bg-blue-50 text-[#0B438B] flex items-center justify-center flex-shrink-0">
                <Icon name="mail" size={18} />
              </div>
              <div className="flex flex-col justify-center min-w-0">
                <div className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">
                  Email
                </div>
                <a
                  href={`mailto:${EMAIL_ADDRESS}`}
                  className="text-xs font-bold text-[#0B438B] hover:underline break-all sm:break-normal"
                >
                  {EMAIL_ADDRESS}
                </a>
              </div>
            </div>

            {/* Phone Card */}
            <div className="bg-gray-50/70 border border-gray-100 rounded-2xl p-3.5 shadow-sm flex items-center gap-3.5 min-h-[64px] sm:h-[68px]">
              <div className="w-9 h-9 rounded-xl bg-green-50 text-[#168F5B] flex items-center justify-center flex-shrink-0">
                <Icon name="phone" size={18} />
              </div>
              <div className="flex flex-col justify-center">
                <div className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">
                  Phone / WhatsApp
                </div>
                <a
                  href={`tel:+${PHONE_NUMBER}`}
                  className="text-xs font-bold text-[#0B438B] hover:underline"
                >
                  {DISPLAY_PHONE}
                </a>
              </div>
            </div>

            {/* Service Area Card */}
            <div className="bg-gray-50/70 border border-gray-100 rounded-2xl p-3.5 shadow-sm flex items-center gap-3.5 min-h-[64px] sm:h-[68px]">
              <div className="w-9 h-9 rounded-xl bg-blue-50 text-[#0B438B] flex items-center justify-center flex-shrink-0">
                <Icon name="map-pin" size={18} />
              </div>
              <div className="flex flex-col justify-center">
                <div className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">
                  Service Area
                </div>
                <div className="text-xs font-bold text-[#0B438B]">{LOCATION_TEXT}</div>
              </div>
            </div>

            {/* Availability Card */}
            <div className="bg-gray-50/70 border border-gray-100 rounded-2xl p-3.5 shadow-sm flex items-center gap-3.5 min-h-[64px] sm:h-[68px]">
              <div className="w-9 h-9 rounded-xl bg-green-50 text-[#168F5B] flex items-center justify-center flex-shrink-0">
                <Icon name="clock" size={18} />
              </div>
              <div className="flex flex-col justify-center">
                <div className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">
                  Availability
                </div>
                <div className="text-xs font-bold text-[#0B438B]">{BUSINESS_HOURS}</div>
              </div>
            </div>
          </div>

          {/* Column 2: Send Enquiry Form */}
          <div className="lg:col-span-4 bg-white border border-gray-200 rounded-3xl p-4 sm:p-6 shadow-sm flex flex-col justify-between h-full">
            <form onSubmit={handleCustomerSubmit} className="space-y-3">
              {/* Name */}
              <div className="relative">
                <span className="absolute left-3.5 top-3.5 text-gray-400">
                  <Icon name="user" size={16} />
                </span>
                <input
                  type="text"
                  placeholder="Your Name"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:border-[#0B438B]"
                  value={customerForm.name}
                  onChange={(e) => setCustomerForm({ ...customerForm, name: e.target.value })}
                  required
                />
              </div>



              {/* Phone */}
              <div className="relative">
                <span className="absolute left-3.5 top-3.5 text-gray-400">
                  <Icon name="phone" size={16} />
                </span>
                <input
                  type="tel"
                  placeholder="Phone Number"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:border-[#0B438B]"
                  value={customerForm.phone}
                  onChange={(e) => setCustomerForm({ ...customerForm, phone: e.target.value })}
                  required
                />
              </div>

              {/* Email */}
              <div className="relative">
                <span className="absolute left-3.5 top-3.5 text-gray-400">
                  <Icon name="mail" size={16} />
                </span>
                <input
                  type="email"
                  placeholder="Email Address (optional)"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:border-[#0B438B]"
                  value={customerForm.email}
                  onChange={(e) => setCustomerForm({ ...customerForm, email: e.target.value })}
                />
              </div>

              {/* Service Category */}
              <div className="relative">
                <span className="absolute left-3.5 top-3.5 text-gray-400">
                  <Icon name="briefcase" size={16} />
                </span>
                <select
                  className="w-full pl-10 pr-8 py-2.5 rounded-xl border border-gray-200 text-xs text-gray-700 bg-white focus:outline-none focus:border-[#0B438B] appearance-none"
                  value={customerForm.service}
                  onChange={(e) => setCustomerForm({ ...customerForm, service: e.target.value })}
                  required
                >
                  <option value="">Service Category</option>
                  {SERVICES.map((s) => (
                    <option key={s.slug} value={s.name}>
                      {s.name}
                    </option>
                  ))}
                </select>
                <span className="absolute right-3.5 top-3.5 text-gray-400 pointer-events-none">
                  <Icon name="chevron-down" size={14} />
                </span>
              </div>

              {/* Message */}
              <div className="relative">
                <span className="absolute left-3.5 top-3 text-gray-400">
                  <Icon name="file-text" size={16} />
                </span>
                <textarea
                  rows={3}
                  placeholder="Your Message *"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:border-[#0B438B] resize-none"
                  value={customerForm.message}
                  onChange={(e) => setCustomerForm({ ...customerForm, message: e.target.value })}
                  required
                />
              </div>

              {customerError && (
                <div className="text-xs text-red-600 bg-red-50 p-2 rounded-lg">
                  {customerError}
                </div>
              )}
              {customerSuccess && (
                <div className="text-xs text-green-700 bg-green-50 p-2 rounded-lg">
                  {customerSuccess}
                </div>
              )}

              <button
                type="submit"
                className="w-full bg-[#0B438B] hover:bg-[#08336a] text-white py-3 rounded-xl text-xs font-bold inline-flex items-center justify-center gap-2 shadow-sm transition"
              >
                Send Enquiry <Icon name="arrow-right" size={14} />
              </button>
            </form>
          </div>

          {/* Column 3: Nuzvid Map Card */}
          <div className="lg:col-span-4 bg-white border border-gray-200 rounded-3xl p-4 sm:p-6 shadow-sm flex flex-col justify-between h-full">
            <MapCard />
          </div>
        </div>
      </div>
    </section>
  );
}
