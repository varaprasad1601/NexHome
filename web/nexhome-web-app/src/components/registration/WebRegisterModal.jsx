import Icon from '../common/Icon';
import { useSiteState } from '../../context/siteState';

/** Register via Web: the professional registration Google Form in a modal. */
export default function WebRegisterModal() {
  const { webRegisterModalOpen, setWebRegisterModalOpen } = useSiteState();
  if (!webRegisterModalOpen) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs pt-20 pb-3 px-3 sm:px-6"
      onClick={() => setWebRegisterModalOpen(false)}
    >
      <div
        className="bg-white rounded-3xl w-full max-w-3xl h-[calc(100vh-92px)] max-h-[87vh] flex flex-col overflow-hidden shadow-2xl border border-gray-200 animate-in fade-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 sm:px-6 py-4 bg-gradient-to-r from-[#0B438B] to-[#0E56B0] text-white">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-white/15 flex items-center justify-center">
              <Icon name="globe" size={18} />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-extrabold leading-tight">
                Professional Partner Registration
              </h3>
              <p className="text-[11px] text-blue-100 m-0">
                Complete the registration form below
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <a
              href="https://forms.gle/1t1Nt8zbFazynmVq6"
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs font-semibold bg-white/15 hover:bg-white/25 text-white px-3 py-1.5 rounded-lg transition hidden sm:inline-flex items-center gap-1.5"
            >
              Open in new tab <Icon name="arrow-right" size={13} />
            </a>
            <button
              type="button"
              onClick={() => setWebRegisterModalOpen(false)}
              className="w-8 h-8 rounded-lg bg-white/15 hover:bg-white/25 flex items-center justify-center text-white transition cursor-pointer"
              aria-label="Close modal"
            >
              <Icon name="x" size={18} />
            </button>
          </div>
        </div>

        {/* Embedded Google Form */}
        <div className="flex-1 w-full bg-[#F8FAFC] relative overflow-hidden">
          <iframe
            src="https://docs.google.com/forms/d/e/1FAIpQLScxy3ZwFrYEAx9zcAcQti2TB1a4PBhwiAFiXJA-wNIkZzc0xA/viewform?embedded=true"
            title="NexHome Professional Partner Registration Form"
            className="w-full h-full border-0"
          >
            Loading form…
          </iframe>
        </div>
      </div>
    </div>
  );
}
