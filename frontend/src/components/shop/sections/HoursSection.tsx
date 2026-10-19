import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { todayHours } from "../shopHours";
import type { ShopSectionProps } from "./types";

const DAY_KEYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;

export default function HoursSection({ shop }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const hours = shop.openingHours;
  const [open, setOpen] = useState(false);
  if (!hours || hours.length !== DAY_KEYS.length) return null;
  const today = todayHours(hours);
  const todayLabel = today ? tp(`shop.hours.${DAY_KEYS[today.dayIndex]}`) : tp("shop.hoursToday");
  const todayRange =
    today && today.hour.open
      ? `${today.hour.start}–${today.hour.end}`
      : tp("shop.hoursClosed");
  const status = today?.openNow ? tp("shop.openNow") : tp("shop.closedNow");

  return (
    <section className={`shop-hours recommend-width ${open ? "is-open" : ""}`}>
      <header>
        <h2>{tp("shop.hoursTitle")}</h2>
      </header>
      <button
        type="button"
        className="shop-hours-summary"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span>
          {tp("shop.hoursTodayLine", { day: todayLabel, range: todayRange })}
          <span className="shop-hero-dot" aria-hidden="true">·</span>
          <em className={today?.openNow ? "is-open" : "is-closed"}>{status}</em>
        </span>
        <ChevronDown />
      </button>
      <ul className="shop-hours-list">
        {DAY_KEYS.map((day, index) => {
          const item = hours[index];
          return (
            <li key={day} className={item.open ? "" : "is-closed"}>
              <b>{tp(`shop.hours.${day}`)}</b>
              <span className="num">{item.open ? `${item.start} – ${item.end}` : tp("shop.hoursClosed")}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
