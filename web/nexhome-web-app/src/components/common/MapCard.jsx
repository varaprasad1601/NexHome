import Icon from './Icon';

/** Illustrated map of Nuzvid with the service-area badge. */
export default function MapCard() {
  return (
    <div className="relative w-full h-[290px] rounded-2xl overflow-hidden border border-[#d6e4d5] shadow-sm bg-[#e9f2e3] select-none">
      {/* SVG Map of Nuzvid */}
      <svg
        className="w-full h-full object-cover"
        viewBox="0 0 400 300"
        preserveAspectRatio="xMidYMid slice"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="mapBg" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ebf4e3" />
            <stop offset="100%" stopColor="#e1ecd7" />
          </linearGradient>
        </defs>

        {/* Base background */}
        <rect width="400" height="300" fill="url(#mapBg)" />

        {/* Water bodies / canal */}
        <path d="M 0 70 Q 150 110 400 60" fill="none" stroke="#cae5f2" strokeWidth="12" />
        <path d="M 0 70 Q 150 110 400 60" fill="none" stroke="#b4ddf0" strokeWidth="6" />

        {/* Secondary roads */}
        <path d="M 50 0 L 80 300" fill="none" stroke="#ffffff" strokeWidth="7" />
        <path d="M 320 0 L 290 300" fill="none" stroke="#ffffff" strokeWidth="6" />
        <path d="M 0 180 L 400 210" fill="none" stroke="#ffffff" strokeWidth="8" />
        <path d="M 0 250 L 400 230" fill="none" stroke="#ffffff" strokeWidth="6" />

        {/* Minor connective streets */}
        <line x1="80" y1="120" x2="290" y2="150" stroke="#fcfdfa" strokeWidth="4" />
        <line x1="120" y1="50" x2="220" y2="180" stroke="#fcfdfa" strokeWidth="4" />
        <line x1="160" y1="200" x2="240" y2="300" stroke="#fcfdfa" strokeWidth="4" />
        <line x1="220" y1="100" x2="350" y2="140" stroke="#fcfdfa" strokeWidth="3" />

        {/* Main Highway / Orange-yellow road */}
        <path d="M 0 140 Q 200 130 400 180" fill="none" stroke="#ffe394" strokeWidth="10" />
        <path d="M 0 140 Q 200 130 400 180" fill="none" stroke="#f5c942" strokeWidth="4" />

        {/* Railway line */}
        <path d="M 60 0 L 360 300" fill="none" stroke="#7e8c9b" strokeWidth="3" strokeDasharray="6 4" />

        {/* Town neighborhood labels */}
        <text x="140" y="90" fontSize="11" fill="#718698" fontWeight="600" fontFamily="sans-serif">Subba Rao Pet</text>
        <text x="70" y="225" fontSize="10" fill="#718698" fontWeight="600" fontFamily="sans-serif">Bus Stand Area</text>
        <text x="240" y="250" fontSize="10" fill="#718698" fontWeight="600" fontFamily="sans-serif">Kothapeta</text>

        {/* Railway Station Marker */}
        <g transform="translate(290, 85)">
          <circle cx="0" cy="0" r="13" fill="#0B438B" />
          <circle cx="0" cy="0" r="16" fill="#0B438B" opacity="0.2" />
          <path d="M -5 -4 L 5 -4 L 5 4 L -5 4 Z M -3 5 L -1 7 M 3 5 L 1 7" stroke="#ffffff" strokeWidth="1.5" fill="none" />
          <text x="16" y="4" fontSize="10" fontWeight="700" fill="#0B438B" fontFamily="sans-serif">
            Nuzvid Railway Station
          </text>
        </g>

        {/* Nuzvid Center Location Pin */}
        <g transform="translate(200, 155)">
          {/* Pulsing ring */}
          <circle cx="0" cy="0" r="16" fill="#e53e3e" opacity="0.25">
            <animate attributeName="r" values="10;20;10" dur="2s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.4;0.1;0.4" dur="2s" repeatCount="indefinite" />
          </circle>
          {/* Pin body */}
          <path
            d="M 0 0 C -9 -9 -12 -18 0 -26 C 12 -18 9 -9 0 0 Z"
            fill="#e53e3e"
          />
          <circle cx="0" cy="-16" r="4.5" fill="#ffffff" />
          {/* Label box */}
          <rect x="-35" y="-45" width="70" height="18" rx="4" fill="#0B438B" />
          <text x="0" y="-32" textAnchor="middle" fill="#ffffff" fontSize="11" fontWeight="800" fontFamily="sans-serif">
            Nuzvid
          </text>
        </g>
      </svg>

      {/* Bottom Floating Pill Badge */}
      <div className="absolute bottom-3 left-3 bg-white/95 backdrop-blur-sm border border-gray-200 rounded-full py-1.5 px-3.5 shadow-md flex items-center gap-2">
        <span className="w-6 h-6 rounded-full bg-blue-50 text-[#0B438B] flex items-center justify-center">
          <Icon name="map-pin" size={14} />
        </span>
        <div className="leading-tight">
          <div className="text-xs font-extrabold text-[#0B438B]">Nuzvid & Nearby Areas</div>
          <div className="text-[10px] text-gray-500 font-medium">Andhra Pradesh</div>
        </div>
      </div>
    </div>
  );
}
