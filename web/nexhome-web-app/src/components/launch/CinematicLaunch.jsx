import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';

/** Full-screen launch countdown shown on /launch; calls onComplete when the reveal has faded out. */
export default function CinematicLaunch({ onComplete }) {
  const [phase, setPhase] = useState('idle');
  const [countdown, setCountdown] = useState(5);
  const [progress, setProgress] = useState(0);

  const onCompleteRef = useRef(onComplete);
  useEffect(() => {
    onCompleteRef.current = onComplete;
  }, [onComplete]);

  // Force scroll to top (Hero section) immediately on mount
  useEffect(() => {
    window.scrollTo(0, 0);
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, []);

  // 1. Handle countdown timer & circular progress ring
  useEffect(() => {
    if (phase !== 'countdown') return;

    const startTime = Date.now();
    const duration = 5000; // 5 seconds total countdown

    const interval = setInterval(() => {
      const elapsed = Date.now() - startTime;
      const currentProgress = Math.min((elapsed / duration) * 100, 100);
      const remainingSeconds = Math.max(Math.ceil((duration - elapsed) / 1000), 1);

      setProgress(currentProgress);
      setCountdown(remainingSeconds);

      if (elapsed >= duration) {
        clearInterval(interval);
        setProgress(100);
        setPhase('logo_reveal');
      }
    }, 30);

    return () => clearInterval(interval);
  }, [phase]);

  // 2. Handle Logo Reveal -> Blur Out transition
  useEffect(() => {
    if (phase !== 'logo_reveal') return;

    const revealTimer = setTimeout(() => {
      window.scrollTo(0, 0);
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      setPhase('blur_fade');
    }, 2200);

    return () => clearTimeout(revealTimer);
  }, [phase]);

  // 3. Handle Blur Out -> Complete unmount into Home Hero section
  useEffect(() => {
    if (phase !== 'blur_fade') return;

    const fadeTimer = setTimeout(() => {
      window.scrollTo(0, 0);
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      setPhase('completed');
      onCompleteRef.current();
    }, 1100);

    return () => clearTimeout(fadeTimer);
  }, [phase]);

  const handleLaunchClick = () => {
    if (phase === 'idle') {
      setCountdown(5);
      setProgress(0);
      setPhase('countdown');
    }
  };

  if (phase === 'completed') return null;

  // SVG parameters for circular progress ring
  const radius = 110;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (progress / 100) * circumference;

  // Dynamic status text based on progress
  const getStatusText = () => {
    const p = Math.round(progress);
    if (p < 25) return `Synchronizing Data... ${p}%`;
    if (p < 50) return `Loading Resources... ${p}%`;
    if (p < 75) return `Activating Portal... ${p}%`;
    return `Finalizing Launch... ${p}%`;
  };

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 1, filter: 'blur(0px)', scale: 1 }}
        animate={
          phase === 'blur_fade'
            ? { opacity: 0, filter: 'blur(32px)', scale: 1.06 }
            : { opacity: 1, filter: 'blur(0px)', scale: 1 }
        }
        transition={{ duration: 1.05, ease: [0.25, 1, 0.5, 1] }}
        className="fixed inset-0 z-[9999] bg-gradient-to-r from-[#edf6fd] via-[#f7fbfd] to-[#f0f8f4] text-[#0B315F] flex flex-col items-center justify-center overflow-hidden selection:bg-[#0B438B] selection:text-white pointer-events-auto"
      >
        {/* Ambient Soft Glows */}
        <div className="absolute inset-0 pointer-events-none overflow-hidden">
          <div className="absolute -top-40 -left-40 w-96 h-96 bg-[#0B438B]/10 rounded-full blur-[140px] animate-pulse" />
          <div className="absolute -bottom-40 -right-40 w-96 h-96 bg-[#168F5B]/10 rounded-full blur-[140px] animate-pulse" style={{ animationDelay: '1.5s' }} />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[700px] bg-[#0B438B]/5 rounded-full blur-[160px]" />

          {/* Frame border in Blue */}
          <div className="absolute inset-4 sm:inset-8 border border-[#0B438B]/20 rounded-3xl pointer-events-none flex items-center justify-center">
            <div className="absolute inset-2 border border-[#0B438B]/10 rounded-2xl" />
          </div>
        </div>

        {/* Center Content Area */}
        <div className="relative z-20 flex flex-col items-center justify-center text-center px-4 max-w-4xl mx-auto">

          {/* IDLE PHASE: CLEAN LAUNCH BUTTON */}
          {phase === 'idle' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 1.05, filter: 'blur(10px)' }}
              transition={{ duration: 0.5 }}
              className="flex flex-col items-center justify-center"
            >
              {/* Clean Launch Button */}
              <div className="relative group">
                <div className="absolute -inset-1.5 bg-gradient-to-r from-[#0B438B] via-[#0B315F] to-[#0B438B] rounded-full blur-md opacity-40 group-hover:opacity-80 transition duration-500"></div>

                <button
                  onClick={handleLaunchClick}
                  className="relative px-16 py-6 bg-[#0B315F] hover:bg-[#072242] text-white font-black tracking-[0.3em] text-2xl rounded-full border-2 border-[#0B438B] shadow-[0_12px_35px_rgba(11,67,139,0.35)] transition-all duration-300 transform group-hover:scale-105 cursor-pointer flex items-center gap-3"
                >
                  LAUNCH
                </button>
              </div>
            </motion.div>
          )}

          {/* COUNTDOWN PHASE */}
          {phase === 'countdown' && (
            <div className="relative flex flex-col items-center justify-center space-y-6">

              {/* Circular Progress SVG Ring */}
              <div className="relative w-72 h-72 sm:w-80 sm:h-80 flex items-center justify-center">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 260 260">
                  {/* Background Track */}
                  <circle
                    cx="130"
                    cy="130"
                    r={radius}
                    className="stroke-[#dcebf8]"
                    strokeWidth="6"
                    fill="transparent"
                  />
                  {/* Animated Progress Ring in Blue */}
                  <circle
                    cx="130"
                    cy="130"
                    r={radius}
                    className="stroke-[#0B438B] transition-all duration-75 ease-linear"
                    strokeWidth="8"
                    strokeDasharray={circumference}
                    strokeDashoffset={strokeDashoffset}
                    strokeLinecap="round"
                    fill="transparent"
                  />
                </svg>

                {/* Inner Background with Blue Border */}
                <div className="absolute inset-8 rounded-full bg-white/95 border-2 border-[#0B438B]/40 shadow-[0_10px_30px_rgba(11,67,139,0.15)] flex flex-col items-center justify-center">

                  {/* Animated Countdown Number in Blue */}
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={countdown}
                      initial={{ scale: 0.4, opacity: 0, filter: 'blur(12px)' }}
                      animate={{ scale: 1, opacity: 1, filter: 'blur(0px)' }}
                      exit={{ scale: 1.6, opacity: 0, filter: 'blur(12px)' }}
                      transition={{ duration: 0.35, ease: 'easeOut' }}
                      className="text-8xl font-black text-[#0B438B]"
                    >
                      {countdown}
                    </motion.div>
                  </AnimatePresence>
                </div>
              </div>

              {/* Synchronizing / Activating Percentage Status Text */}
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="text-xs sm:text-sm font-bold tracking-widest uppercase text-[#0B438B] bg-white/90 px-5 py-2.5 rounded-full border border-[#0B438B]/30 shadow-sm backdrop-blur-md"
              >
                {getStatusText()}
              </motion.div>
            </div>
          )}

          {/* LOGO REVEAL & BLUR-OUT PHASE */}
          {(phase === 'logo_reveal' || phase === 'blur_fade') && (
            <motion.div
              initial={{ scale: 0.6, opacity: 0, filter: 'blur(15px)' }}
              animate={{ scale: 1, opacity: 1, filter: 'blur(0px)' }}
              transition={{ duration: 0.65, ease: [0.25, 1, 0.5, 1] }}
              className="flex flex-col items-center space-y-6 px-4 w-full"
            >
              {/* Logo Box with Blue Border */}
              <div className="w-56 h-56 sm:w-64 sm:h-64 rounded-full bg-white p-6 border-4 border-[#0B438B] shadow-[0_10px_60px_rgba(11,67,139,0.25)] flex items-center justify-center overflow-hidden">
                <img
                  src="/assets/NexHome_Logo.png"
                  alt="NexHome Logo"
                  className="w-full h-full object-contain"
                />
              </div>

              {/* NexHome Brand Titles */}
              <div className="space-y-1.5 text-center w-full">
                <h2 className="text-3xl sm:text-4xl font-black tracking-tight">
                  <span className="text-[#0B438B]">Nex</span>
                  <span className="text-[#168F5B]">Home</span>
                </h2>
                <p className="text-sm sm:text-base font-bold text-gray-600 tracking-wider m-0">
                  Better Living, Every Day
                </p>
                <p className="text-xs sm:text-sm font-extrabold text-[#0B438B] tracking-wider m-0">
                  Nuzvid & Nearby Areas
                </p>
              </div>
            </motion.div>
          )}
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
