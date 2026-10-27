import { SERVICES } from '../data/siteData';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';
import { useSiteState } from '../context/siteState';

/** Join as a Professional page with the registration options. */
export default function JoinPage() {
  const {
    workerForm,
    setWorkerForm,
    workerError,
    workerSuccess,
    workerMode,
    setWorkerMode,
    resetWorkerRegistration,
    handleWorkerSubmit,
    setWebRegisterModalOpen,
  } = useSiteState();

  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Join as a Professional"
        eyebrow="BECOME A SERVICE PROFESSIONAL"
        description="Tell us about your skills, service category and availability. Registrations are open for professionals interested in serving Nuzvid and Nearby Areas."
      >
        Grow with <span className="text-[#168F5B]">NexHome.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        {/* Two Column Layout - Matching Screenshot 2 */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start">
          {/* Left Column */}
          <div className="lg:col-span-5 flex flex-col">
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B315F] tracking-tight leading-tight mb-4">
              Your skills deserve <br />
              <span className="text-[#168F5B]">the right opportunity.</span>
            </h2>
            <p className="text-sm text-gray-600 leading-relaxed mb-6">
              Independent service professionals and technicians can express their interest in being part of the NexHome service network.
            </p>

            <div className="space-y-4 mb-8">
              <div className="flex items-center gap-3.5 pb-4 border-b border-gray-100">
                <Icon name="briefcase" size={20} className="text-[#168F5B] flex-shrink-0" />
                <span className="text-sm font-bold text-gray-800">Share your experience and skill category</span>
              </div>
              <div className="flex items-center gap-3.5 pb-4 border-b border-gray-100">
                <Icon name="map-pin" size={20} className="text-[#168F5B] flex-shrink-0" />
                <span className="text-sm font-bold text-gray-800">Tell us where you can provide services</span>
              </div>
              <div className="flex items-center gap-3.5 pb-4 border-b border-gray-100">
                <Icon name="message-circle" size={20} className="text-[#168F5B] flex-shrink-0" />
                <span className="text-sm font-bold text-gray-800">Register with a prepared WhatsApp message</span>
              </div>
            </div>

            <div className="p-5 rounded-2xl bg-[#F0F7FB] border-l-4 border-[#0B438B] text-xs sm:text-sm text-gray-600 leading-relaxed shadow-xs">
              Registration is reviewed manually and does not guarantee approval, assignments, income or specific work hours.
            </div>
          </div>

          {/* Right Column: Registration Card */}
          <div className="lg:col-span-7 bg-white rounded-3xl p-6 sm:p-10 border border-gray-100 shadow-sm min-h-[460px] flex flex-col justify-center">
            {workerMode === 'initial' ? (
              <div className="space-y-4 py-4 text-center max-w-md mx-auto w-full my-auto">
                <div className="w-12 h-12 rounded-2xl bg-[#EAF4FC] text-[#0B438B] flex items-center justify-center mx-auto mb-1">
                  <Icon name="users" size={24} />
                </div>
                <h3 className="text-lg sm:text-xl font-black text-[#0B315F]">Professional Partner Registration</h3>
                <p className="text-xs sm:text-sm text-gray-500 mb-6">Choose how you would like to register with NexHome.</p>

                <div className="space-y-3">
                  <button
                    type="button"
                    onClick={() => setWebRegisterModalOpen(true)}
                    className="w-full bg-[#0B438B] hover:bg-[#08336a] text-white py-3.5 px-4 rounded-xl text-xs sm:text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px] cursor-pointer"
                  >
                    <Icon name="globe" size={16} /> Register via Web
                  </button>

                  <button
                    type="button"
                    onClick={() => setWorkerMode('whatsapp')}
                    className="w-full bg-[#168F5B] hover:bg-[#12774B] text-white py-3.5 px-4 rounded-xl text-xs sm:text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px] cursor-pointer"
                  >
                    <Icon name="message-circle" size={16} /> Register via WhatsApp
                  </button>
                </div>
              </div>
            ) : (
              <div className="my-auto">
                <div className="flex items-center justify-between gap-4 mb-6">
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 rounded-2xl bg-[#EAF4FC] text-[#0B438B] flex items-center justify-center flex-shrink-0">
                      <Icon name="users" size={22} />
                    </div>
                    <div>
                      <h3 className="text-lg font-extrabold text-[#0B315F]">WhatsApp Registration Form</h3>
                      <p className="text-xs text-gray-500 m-0">Fill the details below to prepare your registration message.</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={resetWorkerRegistration}
                    className="text-xs sm:text-sm text-gray-400 hover:text-gray-600 font-bold whitespace-nowrap cursor-pointer"
                  >
                    ← Back
                  </button>
                </div>

                <form onSubmit={handleWorkerSubmit} className="space-y-5" noValidate>
                  {/* Row 1: Name & Mobile */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-gray-700 mb-1.5">
                        Full name <span className="text-red-500 font-bold">*</span>
                      </label>
                      <input
                        type="text"
                        placeholder="Your full name"
                        maxLength={100}
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                        value={workerForm.name}
                        onChange={(e) => setWorkerForm({ ...workerForm, name: e.target.value })}
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
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                        value={workerForm.phone}
                        onChange={(e) => setWorkerForm({ ...workerForm, phone: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  {/* Row 2: Service category & Location */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-gray-700 mb-1.5">
                        Service category <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <select
                          className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-700 bg-white appearance-none focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                          value={workerForm.service}
                          onChange={(e) => setWorkerForm({ ...workerForm, service: e.target.value })}
                          required
                        >
                          <option value="">Select your skill</option>
                          {SERVICES.map((s) => (
                            <option key={s.slug} value={s.name}>
                              {s.name}
                            </option>
                          ))}
                        </select>
                        <span className="absolute right-4 top-3.5 text-gray-400 pointer-events-none">
                          <Icon name="chevron-down" size={16} />
                        </span>
                      </div>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-gray-700 mb-1.5">
                        Location / service area <span className="text-red-500 font-bold">*</span>
                      </label>
                      <input
                        type="text"
                        placeholder="Nuzvid or nearby areas"
                        maxLength={120}
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                        value={workerForm.locality}
                        onChange={(e) => setWorkerForm({ ...workerForm, locality: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  {/* Row 3: Experience & Availability */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-gray-700 mb-1.5">
                        Experience <span className="text-red-500 font-bold">*</span>
                      </label>
                      <input
                        type="text"
                        placeholder="e.g., 2 years"
                        maxLength={100}
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                        value={workerForm.experience}
                        onChange={(e) => setWorkerForm({ ...workerForm, experience: e.target.value })}
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-gray-700 mb-1.5">
                        Availability (optional)
                      </label>
                      <div className="relative">
                        <select
                          className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-700 bg-white appearance-none focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                          value={workerForm.availability}
                          onChange={(e) => setWorkerForm({ ...workerForm, availability: e.target.value })}
                        >
                          <option value="">Please select</option>
                          <option value="Full-time">Full-time</option>
                          <option value="Part-time">Part-time</option>
                          <option value="Weekdays">Weekdays</option>
                          <option value="Weekends">Weekends</option>
                          <option value="To be discussed">To be discussed</option>
                        </select>
                        <span className="absolute right-4 top-3.5 text-gray-400 pointer-events-none">
                          <Icon name="chevron-down" size={16} />
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Consent Checkbox */}
                  <div className="pt-1">
                    <label className="flex items-start gap-3 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        className="mt-0.5 w-4 h-4 rounded border-gray-300 text-[#0B438B] focus:ring-[#0B438B]"
                        checked={workerForm.consent}
                        onChange={(e) => setWorkerForm({ ...workerForm, consent: e.target.checked })}
                        required
                      />
                      <span className="text-xs text-gray-600 leading-snug">
                        I agree to share these registration details with NexHome for professional onboarding enquiries. <span className="text-red-500 font-bold">*</span>
                      </span>
                    </label>
                  </div>

                  {/* Informational Hint */}
                  <p className="text-xs text-gray-500 leading-relaxed m-0">
                    Completing this form prepares a WhatsApp registration message. You must press Send in WhatsApp. Registering does not guarantee approval, assignments or earnings.
                  </p>

                  {/* Error & Success Alerts */}
                  {workerError && (
                    <div className="text-xs text-red-600 bg-red-50 p-3 rounded-xl border border-red-100 flex items-center gap-2">
                      <span className="font-bold">•</span> {workerError}
                    </div>
                  )}
                  {workerSuccess && (
                    <div className="text-xs text-green-700 bg-green-50 p-3 rounded-xl border border-green-100 flex items-center gap-2">
                      <Icon name="check-circle" size={16} className="text-[#168F5B]" /> {workerSuccess}
                    </div>
                  )}

                  {/* Action Button */}
                  <div className="pt-2">
                    <button
                      type="submit"
                      className="w-full bg-[#168F5B] hover:bg-[#12774B] text-white py-3.5 px-6 rounded-xl text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px] cursor-pointer"
                    >
                      <Icon name="message-circle" size={17} /> Send via WhatsApp
                    </button>
                  </div>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
