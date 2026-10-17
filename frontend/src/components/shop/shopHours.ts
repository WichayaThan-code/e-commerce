import type { ShopHour } from "../../services/api";

/** Maps JS getDay() (Sun=0) to store hours index (Mon=0 … Sun=6). */
const JS_DAY_TO_HOURS = [6, 0, 1, 2, 3, 4, 5] as const;

export type TodayHours = {
  dayIndex: number;
  hour: ShopHour;
  openNow: boolean;
};

function minutesNow(date = new Date()) {
  return date.getHours() * 60 + date.getMinutes();
}

function parseHm(value: string) {
  const match = /^(\d{1,2}):(\d{2})$/.exec(value.trim());
  if (!match) return null;
  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null;
  return hours * 60 + minutes;
}

export function todayHours(hours?: ShopHour[] | null, now = new Date()): TodayHours | null {
  if (!hours || hours.length !== 7) return null;
  const dayIndex = JS_DAY_TO_HOURS[now.getDay()];
  const hour = hours[dayIndex];
  if (!hour) return null;
  if (!hour.open) return { dayIndex, hour, openNow: false };
  const start = parseHm(hour.start);
  const end = parseHm(hour.end);
  if (start == null || end == null) return { dayIndex, hour, openNow: false };
  const current = minutesNow(now);
  const openNow = end >= start ? current >= start && current < end : current >= start || current < end;
  return { dayIndex, hour, openNow };
}
