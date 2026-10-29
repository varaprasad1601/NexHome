import { Coffee } from 'lucide-react';

/** The daily lunch break, 12 PM to 1 PM (V22): in no availability slot. */
const BREAK_START = '12:00';

/**
 * The lunch break column of a weekly availability grid, placed after the slot that ends at 12 PM (renders nothing
 * after any other slot): a greyed heading, or a greyed cell that cannot be chosen.
 *
 * @param header true for the heading, false for a day's cell
 */
export default function LunchBreak({ after, header = false }) {
  if (after.metadata?.end !== BREAK_START) return null;
  if (header) {
    return (
      <th className="w-16 bg-[#EEF2F6] px-1 py-2.5 text-center align-top">
        <span className="flex flex-col items-center gap-0.5 text-[#8A9AAD]">
          <Coffee size={14} />
          <span className="text-[0.6875rem] font-black">Lunch</span>
          <span className="whitespace-nowrap text-[0.625rem]">12 – 1 PM</span>
        </span>
      </th>
    );
  }
  return (
    <td className="bg-[#F4F7FA] px-1 py-1.5 text-center" title="Lunch break: 12 PM to 1 PM">
      <span className="text-[0.6875rem] font-bold text-[#B5C2CF]">Break</span>
    </td>
  );
}
