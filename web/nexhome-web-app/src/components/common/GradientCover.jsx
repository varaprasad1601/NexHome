/**
 * The stand-in for a category or service without an image, as the admin console shows it: a soft blue-to-sand
 * gradient with a simple house shape and a sun. Fills its parent.
 */
export default function GradientCover({ className = '' }) {
  return (
    <span
      aria-hidden="true"
      className={`relative block h-full w-full bg-[linear-gradient(135deg,#CFE2F6,#F6E2C3_60%,#E8D3B6)] ${className}`}
    >
      <span className="absolute bottom-[18%] left-[18%] right-[18%] h-[34%] rounded-t-[0.6rem] rounded-b-[0.3rem] bg-white/50" />
      <span className="absolute right-[14%] top-[16%] aspect-square w-[14%] rounded-full bg-white/55" />
    </span>
  );
}
