import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { logout, requestAdminLoginOtp, verifyLoginOtp } from '../../api/auth';
import { useAdminAuth } from '../../context/adminAuth';
import { isStaffUser } from '../../utils/adminSession';

const COUNTRY_CODE = '+91';
const RESEND_SECONDS = 60;

/** Seconds left until the time (ms), refreshed every second while above zero. */
function useSecondsUntil(untilMs) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!untilMs) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [untilMs]);
  return Math.max(0, Math.ceil((untilMs - now) / 1000));
}

/**
 * Staff sign-in at /admin: mobile number, then the LOGIN OTP. Only SUPER_USER, ADMIN and SUB_ADMIN accounts get in;
 * a signed-in admin goes straight to the dashboard.
 */
export default function AdminLoginPage() {
  const { session, signIn } = useAdminAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState('phone');
  const [mobileNumber, setMobileNumber] = useState('');
  const [otp, setOtp] = useState('');
  const [otpFocused, setOtpFocused] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [resendAt, setResendAt] = useState(0);
  const resendSeconds = useSecondsUntil(resendAt);
  // The backend's resend cooldown (429), shown with the seconds counting down instead of a fixed error
  const [coolingDown, setCoolingDown] = useState(false);
  const cooldownMessage =
    coolingDown && resendSeconds > 0
      ? `Please wait ${resendSeconds} ${resendSeconds === 1 ? 'second' : 'seconds'} before requesting another OTP.`
      : '';

  useEffect(() => {
    document.title = 'Admin Login | NexHome';
  }, []);

  const sendOtp = async () => {
    setError('');
    setNotice('');
    setCoolingDown(false);
    if (!/^[0-9]{10}$/.test(mobileNumber)) {
      setError('Enter your 10-digit mobile number.');
      return;
    }
    setBusy(true);
    try {
      // The backend sends the code only to an active staff account, else 404 "Invalid user. ..."
      await requestAdminLoginOtp(COUNTRY_CODE, mobileNumber);
      setStep('otp');
      setOtp('');
      setNotice(`We sent a 6-digit code to ${COUNTRY_CODE} ${mobileNumber}.`);
      setResendAt(Date.now() + RESEND_SECONDS * 1000);
    } catch (err) {
      const waitSeconds = err.status === 429 && Number(/(\d+) seconds?/.exec(err.message)?.[1]);
      if (waitSeconds) {
        setResendAt(Date.now() + waitSeconds * 1000);
        setCoolingDown(true);
      } else {
        setError(err.message);
      }
    } finally {
      setBusy(false);
    }
  };

  const handlePhoneSubmit = (e) => {
    e.preventDefault();
    sendOtp();
  };

  const handleOtpSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!/^[0-9]{6}$/.test(otp)) {
      setError('Enter the 6-digit code.');
      return;
    }
    setBusy(true);
    try {
      const result = await verifyLoginOtp(COUNTRY_CODE, mobileNumber, otp);
      if (!isStaffUser(result.user)) {
        // The staff role was withdrawn after the code was sent: end the session it just opened
        await logout(result.tokens.refreshToken).catch(() => {});
        setError('Invalid user. This mobile number does not have admin access.');
        setStep('phone');
        setOtp('');
        setNotice('');
        setBusy(false);
        return;
      }
      signIn({ user: result.user, tokens: result.tokens });
      navigate('/admin/dashboard', { replace: true });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const handleChangeNumber = () => {
    setStep('phone');
    setOtp('');
    setError('');
    setNotice('');
  };

  if (session) return <Navigate to="/admin/dashboard" replace />;

  const inputClass =
    'w-full min-w-0 rounded-xl border border-[#dcebf8] bg-white px-4 py-3 text-base text-gray-900 outline-none transition focus:border-[#0B438B] focus:ring-2 focus:ring-[#0B438B]/20 disabled:bg-gray-50';
  const buttonClass =
    'w-full rounded-xl bg-[#0B438B] px-4 py-3 text-base font-bold text-white transition hover:bg-[#093672] disabled:cursor-not-allowed disabled:opacity-60';

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#edf6fd] via-[#f7fbfd] to-[#f0f8f4] flex flex-col">
      <main id="main-content" className="flex-1 flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-md">
          {/* Brand */}
          <Link to="/" className="flex items-center justify-center gap-2.5 mb-8" aria-label="NexHome home">
            <img className="h-12 w-auto object-contain" src="/assets/NexHome_Logo.png" alt="NexHome Logo" />
            <div className="text-2xl font-normal tracking-tight leading-none" style={{ fontFamily: "'ManropeCustom', sans-serif" }}>
              <span className="text-[#0B438B] font-semibold">Nex</span>
              <span className="text-[#168F5B] font-semibold">Home</span>
            </div>
          </Link>

          <div className="rounded-2xl border border-[#dcebf8] bg-white p-6 sm:p-8 shadow-[0_18px_50px_rgba(11,67,139,0.08)]">
            <div>
              <span className="block mb-2 text-xs font-bold uppercase tracking-wider text-[#0B438B]">Admin portal</span>
              <h1 className="mb-2 text-2xl font-extrabold text-gray-900">
                {step === 'phone' ? 'Sign in to NexHome' : 'Enter verification code'}
              </h1>
              <p className="mb-0 text-sm text-gray-600">
                {step === 'phone'
                  ? 'Use the mobile number registered to your staff account. We will send you a one-time code.'
                  : notice}
              </p>

              {(error || cooldownMessage) && (
                <div role="alert" className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {error || cooldownMessage}
                </div>
              )}

              {step === 'phone' ? (
                <form className="mt-6 space-y-5" onSubmit={handlePhoneSubmit} noValidate>
                  <div>
                    <label htmlFor="admin-mobile" className="block text-sm font-semibold text-gray-800 mb-2">
                      Mobile number
                    </label>
                    <div className="flex gap-2">
                      <span className="flex items-center rounded-xl border border-[#dcebf8] bg-[#f7fbfd] px-4 text-base font-semibold text-gray-700">
                        {COUNTRY_CODE}
                      </span>
                      <input
                        id="admin-mobile"
                        className={inputClass}
                        type="tel"
                        inputMode="numeric"
                        autoComplete="tel-national"
                        maxLength={10}
                        placeholder="10-digit number"
                        value={mobileNumber}
                        onChange={(e) => setMobileNumber(e.target.value.replace(/\D/g, ''))}
                        disabled={busy}
                        autoFocus
                      />
                    </div>
                  </div>
                  <button type="submit" className={buttonClass} disabled={busy || cooldownMessage !== ''}>
                    {busy ? 'Sending code…' : cooldownMessage ? `Send OTP in ${resendSeconds}s` : 'Send OTP'}
                  </button>
                </form>
              ) : (
                <form className="mt-6 space-y-5" onSubmit={handleOtpSubmit} noValidate>
                  <div>
                    <label htmlFor="admin-otp" className="block text-sm font-semibold text-gray-800 mb-2">
                      One-time code
                    </label>
                    <div className="relative">
                      <input
                        id="admin-otp"
                        className={`${inputClass} text-xl font-bold text-transparent caret-transparent selection:bg-transparent`}
                        type="text"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        maxLength={6}
                        value={otp}
                        onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                        onFocus={() => setOtpFocused(true)}
                        onBlur={() => setOtpFocused(false)}
                        disabled={busy}
                        autoFocus
                      />
                      {/* What the field shows: six evenly spaced slots, the typed digits and dots for the rest; the
                          input underneath takes the typing, pasting and autofill */}
                      <div
                        aria-hidden="true"
                        className="pointer-events-none absolute inset-0 flex items-center justify-evenly px-4 text-xl font-bold"
                      >
                        {Array.from({ length: 6 }, (_, index) => (
                          <span key={index} className="relative flex w-4 justify-center">
                            {otp[index] ? (
                              <span className="text-gray-900">{otp[index]}</span>
                            ) : (
                              <span className="text-gray-400">•</span>
                            )}
                            {otpFocused && index === Math.min(otp.length, 5) && otp.length < 6 && (
                              <span className="absolute -left-0.5 top-1/2 h-6 w-0.5 -translate-y-1/2 animate-pulse bg-[#0B438B]" />
                            )}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <button type="submit" className={buttonClass} disabled={busy}>
                    {busy ? 'Verifying…' : 'Verify & sign in'}
                  </button>
                  <div className="flex items-center justify-between text-sm">
                    <button
                      type="button"
                      className="font-semibold text-[#0B438B] hover:underline disabled:opacity-60"
                      onClick={handleChangeNumber}
                      disabled={busy}
                    >
                      Change number
                    </button>
                    <button
                      type="button"
                      className="font-semibold text-[#168F5B] hover:underline disabled:cursor-not-allowed disabled:text-gray-400 disabled:no-underline"
                      onClick={sendOtp}
                      disabled={busy || resendSeconds > 0}
                    >
                      {resendSeconds > 0 ? `Resend in ${resendSeconds}s` : 'Resend OTP'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>

          <div className="mt-6 text-center text-sm text-gray-500">
            <Link to="/" className="font-semibold text-[#0B438B] hover:underline">
              ← Back to NexHome
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
