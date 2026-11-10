import type { SupportContactDayHours } from "../services/api";

/** Collapse consecutive days that share the same open/start/end into compact lines. */
export function formatSupportHours(
  hours: SupportContactDayHours[] | null | undefined,
  dayLabels: string[],
  closedLabel = "",
): string {
  if (!Array.isArray(hours) || hours.length !== 7) return "";
  const ordered = [...hours].sort((a, b) => a.day - b.day);
  const lines: string[] = [];
  let i = 0;
  while (i < ordered.length) {
    const first = ordered[i];
    let j = i;
    while (
      j + 1 < ordered.length
      && ordered[j + 1].day === ordered[j].day + 1
      && ordered[j + 1].open === first.open
      && ordered[j + 1].start === first.start
      && ordered[j + 1].end === first.end
    ) {
      j += 1;
    }
    const startLabel = dayLabels[first.day] ?? String(first.day);
    const endLabel = dayLabels[ordered[j].day] ?? String(ordered[j].day);
    const dayPart = i === j ? startLabel : `${startLabel}–${endLabel}`;
    if (first.open) {
      lines.push(`${dayPart} ${first.start}–${first.end}`);
    } else if (closedLabel) {
      lines.push(`${dayPart} ${closedLabel}`);
    }
    i = j + 1;
  }
  return lines.join("\n");
}
