import { ArrowDownRight, ArrowUpRight, FileText, IndianRupee, Star, UserRound, UsersRound } from 'lucide-react';

import { formatNumber, formatRupees } from '../../../utils/format';

/** NexHome web colours: blue, green, navy, green-2 and the gold of the site's star ratings. */
const TONES = {
  blue: { icon: UserRound, tile: 'bg-[#EDF6FD] text-[var(--blue)]', line: '#0B438B' },
  green: { icon: UsersRound, tile: 'bg-[#E9F6EF] text-[var(--green)]', line: '#168F5B' },
  navy: { icon: FileText, tile: 'bg-[#E8EEF6] text-[var(--navy)]', line: '#0B315F' },
  teal: { icon: IndianRupee, tile: 'bg-[#E6F3EE] text-[var(--green-2)]', line: '#13774B' },
  amber: { icon: Star, tile: 'bg-[#FFF7DE] text-[#B7860B]', line: '#D9A21B' }
};

/** A small trend line beside the change; decoration only, so it is hidden from screen readers. */
function Sparkline({ points, color }) {
  const width = 63;
  const height = 25;
  const min = Math.min(...points);
  const max = Math.max(...points);
  const coords = points.map((value, i) => [
    (i / (points.length - 1)) * width,
    height - 3 - ((value - min) / (max - min || 1)) * (height - 6)
  ]);
  const line = coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const gradientId = `spark-${color.slice(1)}`;
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true" className="flex-shrink-0">
      <defs>
        <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.18" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={`${line} L${width},${height} L0,${height} Z`} fill={`url(#${gradientId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * A headline figure: icon and label, value, change against last month with a sparkline, and a note. A null value
 * shows a loading placeholder, or a dash when `failed`; a null change hides the badge (nothing to compare with yet).
 */
export default function StatCard({ stat }) {
  const tone = TONES[stat.tone];
  const Icon = tone.icon;
  const loading = stat.value == null && !stat.failed;
  const value = stat.value == null ? '—' : stat.currency ? formatRupees(stat.value) : formatNumber(stat.value, stat.decimals);
  const down = stat.change < 0;
  const ChangeIcon = down ? ArrowDownRight : ArrowUpRight;

  return (
    <div className="flex min-w-[13.125rem] flex-1 flex-col rounded-2xl border border-[var(--line)] bg-white p-4 shadow-[0_1px_2px_rgba(14,51,89,0.04)] xl:min-w-0">
      <div className="flex items-center gap-3">
        <span className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl ${tone.tile}`}>
          <Icon size={18} strokeWidth={2} />
        </span>
        <span className="text-[0.8125rem] font-bold text-[var(--muted)]">{stat.label}</span>
      </div>
      <div className="mt-3 h-[1.625rem] text-[1.625rem] font-black leading-none text-[var(--ink)]">
        {loading ? <span className="block h-full w-24 animate-pulse rounded-md bg-[var(--light)]" aria-label="Loading" /> : value}
      </div>
      <div className="mt-3 flex min-h-[1.5625rem] items-end justify-between gap-2">
        {stat.change != null ? (
          <span
            className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-bold ${
              down ? 'bg-[#FDECEC] text-[#C53030]' : 'bg-[#E9F6EF] text-[var(--green-2)]'
            }`}
          >
            <ChangeIcon size={11} strokeWidth={2.6} aria-hidden="true" />
            {Math.abs(stat.change)}%<span className="sr-only"> {down ? 'down' : 'up'} from last month</span>
          </span>
        ) : (
          <span />
        )}
        {stat.trend && <Sparkline points={stat.trend} color={tone.line} />}
      </div>
      <div className="mt-2 truncate border-t border-[var(--line)] pt-2 text-xs text-[var(--muted)]" title={stat.noteTitle ?? stat.note}>
        {stat.note ?? '\u00a0'}
      </div>
    </div>
  );
}
