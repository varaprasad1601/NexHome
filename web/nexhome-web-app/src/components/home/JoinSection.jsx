import { SERVICES } from '../../data/siteData';
import Icon from '../common/Icon';
import { useSiteState } from '../../context/siteState';

/** Join as a service professional, with the compact registration card. */
export default function JoinSection() {
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
    <section className="py-16 bg-white overflow-hidden" id="join">
      <div className="container max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-6 xl:gap-8 items-center">
          {/* Left Column: Worker Portrait with Mint Green Circle Backdrop */}
          <div className="lg:col-span-4 flex items-center justify-center relative">
            <div className="relative w-full max-w-[280px] sm:max-w-[320px] lg:max-w-none flex items-center justify-center">
              {/* Soft Mint Green Circle Backdrop */}
              <div className="w-60 h-60 sm:w-72 sm:h-72 lg:w-80 lg:h-80 rounded-full bg-[#E5F8EE] absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 -z-0" />

              {/* Transparent Worker Cutout */}
              <div className="relative z-10 w-full max-w-[270px] sm:max-w-[300px] lg:max-w-[330px]">
                <img
                  src="/assets/NexHome_Partener.png"
                  alt="Skilled NexHome Service Professional"
                  className="w-full h-auto object-contain filter drop-shadow-[0_8px_20px_rgba(0,0,0,0.08)]"
                />
              </div>
            </div>
          </div>

          {/* Middle Column: Typography, Copy and Cursive Slogan */}
          <div className="lg:col-span-4 flex flex-col justify-center items-center lg:items-start text-center lg:text-left px-2 sm:px-4 lg:px-0">
            <span className="text-[11px] font-bold text-[#1E6FD9] uppercase tracking-[0.18em] block mb-2">
              GROW WITH NEXHOME
            </span>

            <h2 className="text-2xl sm:text-3xl lg:text-[34px] xl:text-[38px] font-black text-[#0B315F] leading-[1.12] tracking-tight mb-3">
              Join NexHome as <br />
              <span className="text-[#168F5B]">a Service Professional</span>
            </h2>

            <p className="text-xs sm:text-[13px] text-gray-600 leading-relaxed max-w-md mb-2.5 font-normal">
              Are you a skilled professional? Join NexHome and get more opportunities to work, grow and serve your community.
            </p>

            <p className="text-xs sm:text-[13px] text-gray-400 leading-relaxed max-w-md mb-6 font-normal">
              Registration is available through our website form or via WhatsApp.
            </p>

            {/* Slogan with hand-drawn green curve */}
            <div className="inline-block pt-1">
              <span className="font-cursive text-2xl sm:text-[28px] text-[#0B438B] font-bold tracking-wide block transform -rotate-1">
                'Skilled People Stronger Homes!'
              </span>
              <svg className="w-24 sm:w-28 h-2.5 ml-8 mt-0.5 text-[#168F5B]" viewBox="0 0 100 12" fill="none">
                <path d="M 5 6 Q 50 11 95 4" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" />
              </svg>
            </div>
          </div>

          {/* Right Column: Worker Registration Form Card */}
          <div className="lg:col-span-4 w-full max-w-md mx-auto lg:max-w-none">
            <div className="bg-white border border-gray-200/90 rounded-2xl p-5 sm:p-6 shadow-[0_10px_35px_rgba(11,67,139,0.06)] min-h-[320px] lg:min-h-[440px] flex flex-col justify-center">
              {workerMode === 'initial' ? (
                <div className="space-y-4 py-4 text-center my-auto">
                  <div className="w-12 h-12 rounded-2xl bg-blue-50 text-[#0B438B] flex items-center justify-center mx-auto mb-1">
                    <Icon name="users" size={24} />
                  </div>
                  <h3 className="text-base sm:text-lg font-black text-[#0B315F]">Professional Partner Registration</h3>
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
                      className="w-full bg-[#168F5B] hover:bg-[#12774B] text-white py-3.5 px-4 rounded-xl text-xs sm:text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px]"
                    >
                      <Icon name="message-circle" size={16} /> Register via WhatsApp
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleWorkerSubmit} className="space-y-3 my-auto">
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-sm sm:text-base font-black text-[#0B438B]">WhatsApp Registration Form</span>
                    <button
                      type="button"
                      onClick={resetWorkerRegistration}
                      className="text-xs sm:text-sm text-gray-400 hover:text-gray-600 font-bold cursor-pointer"
                    >
                      ← Back
                    </button>
                  </div>
                  {/* Name */}
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-gray-400">
                      <Icon name="user" size={16} />
                    </span>
                    <input
                      type="text"
                      placeholder="Full Name"
                      className="w-full pl-9 pr-3.5 py-2.5 rounded-lg border border-gray-200 text-xs sm:text-[13px] placeholder:text-gray-400 text-gray-800 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                      value={workerForm.name}
                      onChange={(e) => setWorkerForm({ ...workerForm, name: e.target.value })}
                      required
                    />
                  </div>

                  {/* Mobile */}
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-gray-400">
                      <Icon name="phone" size={16} />
                    </span>
                    <input
                      type="tel"
                      placeholder="Mobile Number"
                      className="w-full pl-9 pr-3.5 py-2.5 rounded-lg border border-gray-200 text-xs sm:text-[13px] placeholder:text-gray-400 text-gray-800 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                      value={workerForm.phone}
                      onChange={(e) => setWorkerForm({ ...workerForm, phone: e.target.value })}
                      required
                    />
                  </div>

                  {/* Service Category */}
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-gray-400">
                      <Icon name="users" size={16} />
                    </span>
                    <select
                      className="w-full pl-9 pr-8 py-2.5 rounded-lg border border-gray-200 text-xs sm:text-[13px] text-gray-700 bg-white focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] appearance-none transition-colors"
                      value={workerForm.service}
                      onChange={(e) => setWorkerForm({ ...workerForm, service: e.target.value })}
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

                  {/* Service Area / Location */}
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-gray-400">
                      <Icon name="map-pin" size={16} />
                    </span>
                    <input
                      type="text"
                      placeholder="Service Area / Location"
                      className="w-full pl-9 pr-3.5 py-2.5 rounded-lg border border-gray-200 text-xs sm:text-[13px] placeholder:text-gray-400 text-gray-800 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                      value={workerForm.locality}
                      onChange={(e) => setWorkerForm({ ...workerForm, locality: e.target.value })}
                      required
                    />
                  </div>

                  {/* Experience */}
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-gray-400">
                      <Icon name="file-text" size={16} />
                    </span>
                    <input
                      type="text"
                      placeholder="Experience (e.g. 2 years)"
                      className="w-full pl-9 pr-3.5 py-2.5 rounded-lg border border-gray-200 text-xs sm:text-[13px] placeholder:text-gray-400 text-gray-800 focus:outline-none focus:border-[#0B438B] focus:ring-1 focus:ring-[#0B438B] transition-colors"
                      value={workerForm.experience}
                      onChange={(e) => setWorkerForm({ ...workerForm, experience: e.target.value })}
                      required
                    />
                  </div>

                  {/* Consent Checkbox */}
                  <div className="pt-0.5">
                    <label className="flex items-start gap-2.5 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        className="mt-0.5 w-3.5 h-3.5 rounded border-gray-300 text-[#0B438B] focus:ring-[#0B438B]"
                        checked={workerForm.consent}
                        onChange={(e) => setWorkerForm({ ...workerForm, consent: e.target.checked })}
                        required
                      />
                      <span className="text-[11px] sm:text-xs text-gray-600 leading-snug">
                        I agree to share these registration details with NexHome. <span className="text-red-500 font-bold">*</span>
                      </span>
                    </label>
                  </div>

                  {workerError && (
                    <div className="text-xs text-red-600 bg-red-50 p-2.5 rounded-lg">
                      {workerError}
                    </div>
                  )}
                  {workerSuccess && (
                    <div className="text-xs text-green-700 bg-green-50 p-2.5 rounded-lg">
                      {workerSuccess}
                    </div>
                  )}

                  <button
                    type="submit"
                    className="w-full bg-[#168F5B] hover:bg-[#12774B] text-white py-3 px-4 rounded-xl text-xs sm:text-sm font-bold inline-flex items-center justify-center gap-2 shadow-sm transition hover:translate-y-[-1px]"
                  >
                    <Icon name="message-circle" size={16} /> Send via WhatsApp
                  </button>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
