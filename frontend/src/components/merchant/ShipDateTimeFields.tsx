import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CalendarDays, Clock3 } from "lucide-react";
import DatePicker from "../form/DatePicker";
import { bangkokNowInput, formatBangkokDateTime } from "./orderFormat";

const SHIP_HOURS = Array.from({ length: 24 }, (_, index) => String(index).padStart(2, "0"));
const SHIP_MINUTES = ["00", "15", "30", "45"] as const;
const SHIP_PERIODS = [
  { id: "night", hours: ["00", "01", "02", "03", "04", "05"] },
  { id: "morning", hours: ["06", "07", "08", "09", "10", "11"] },
  { id: "afternoon", hours: ["12", "13", "14", "15", "16", "17"] },
  { id: "evening", hours: ["18", "19", "20", "21", "22", "23"] },
] as const;

export type ShipPeriodId = (typeof SHIP_PERIODS)[number]["id"];

function normalizeShipTime(time: string) {
  const match = time.trim().replace(".", ":").match(/^(\d{1,2})(?::(\d{1,2}))?$/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2] ?? "0");
  if (!Number.isFinite(hour) || !Number.isFinite(minute) || hour > 23 || minute > 59) return null;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function snapShipTime(time: string) {
  const normalized = normalizeShipTime(time) ?? "12:00";
  const [rawHour = "12", rawMinute = "00"] = normalized.split(":");
  const hour = Number(rawHour);
  const minute = Number(rawMinute);
  const snapped = Math.round(minute / 15) * 15;
  if (snapped === 60) {
    return `${String((hour + 1) % 24).padStart(2, "0")}:00`;
  }
  return `${String(hour).padStart(2, "0")}:${String(snapped).padStart(2, "0")}`;
}

function formatTimeTyping(raw: string) {
  const digits = raw.replace(/\D/g, "").slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

export function splitShippedAt(value: string) {
  if (!value.includes("T")) return { date: "", time: "12:00" };
  const [date, time = "12:00"] = value.split("T");
  return { date, time: normalizeShipTime(time) ?? "12:00" };
}

export function joinShippedAt(date: string, time: string) {
  if (!date) return "";
  return `${date}T${normalizeShipTime(time) ?? "12:00"}`;
}

export function shipNowInput() {
  const now = bangkokNowInput();
  const date = now.slice(0, 10);
  return joinShippedAt(date, snapShipTime(now.slice(11, 16)));
}

export function shipPreviewValue(value: string) {
  if (!value.includes("T")) return "";
  return `${value.length === 16 ? value : value.slice(0, 16)}:00+07:00`;
}

function parseShipClock(value: string) {
  const time = snapShipTime(value || "12:00");
  return { hour: time.slice(0, 2), minute: time.slice(3, 5) };
}

export function ShipTimePicker({
  label,
  value,
  onChange,
  hourLabel,
  minuteLabel,
  applyLabel,
  periodLabels,
}: {
  label: string;
  value: string;
  onChange: (time: string) => void;
  hourLabel: string;
  minuteLabel: string;
  applyLabel: string;
  periodLabels: Record<ShipPeriodId, string>;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const controlRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const hourListRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [text, setText] = useState(value || "");
  const [panelPos, setPanelPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const initial = parseShipClock(value);
  const [draftHour, setDraftHour] = useState(initial.hour);
  const [draftMinute, setDraftMinute] = useState(initial.minute);

  const activePeriod = SHIP_PERIODS.find((period) => (period.hours as readonly string[]).includes(draftHour))?.id
    ?? "afternoon";
  const visibleHours = SHIP_PERIODS.find((period) => period.id === activePeriod)?.hours ?? SHIP_HOURS;

  useEffect(() => {
    setText(value || "");
  }, [value]);

  useEffect(() => {
    if (!open) return;
    const next = parseShipClock(value);
    setDraftHour(next.hour);
    setDraftMinute(next.minute);
  }, [open, value]);

  useLayoutEffect(() => {
    if (!open || !controlRef.current) {
      setPanelPos(null);
      return;
    }
    const place = () => {
      const rect = controlRef.current?.getBoundingClientRect();
      if (!rect) return;
      const width = Math.min(300, window.innerWidth - 24);
      const left = Math.max(12, Math.min(rect.right - width, window.innerWidth - width - 12));
      const below = rect.bottom + 10;
      const estimatedHeight = 360;
      const top = below + estimatedHeight > window.innerHeight - 12
        ? Math.max(12, rect.top - estimatedHeight - 10)
        : below;
      setPanelPos({ top, left, width });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node;
      if (rootRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  useEffect(() => {
    if (!open || !hourListRef.current) return;
    const active = hourListRef.current.querySelector<HTMLElement>('[data-active="true"]');
    active?.scrollIntoView({ block: "center" });
  }, [open, draftHour, activePeriod]);

  const commitTyped = () => {
    const next = normalizeShipTime(text);
    if (!next) {
      setText(value || "");
      return;
    }
    setText(next);
    if (next !== value) onChange(next);
  };

  const draftValue = `${draftHour}:${draftMinute}`;
  const commit = (hour = draftHour, minute = draftMinute) => {
    const next = `${hour}:${minute}`;
    setText(next);
    onChange(next);
    setOpen(false);
  };

  const panel = open && panelPos ? createPortal(
    <div
      className="om-ship-time-panel"
      role="dialog"
      aria-label={label}
      ref={panelRef}
      style={{ top: panelPos.top, left: panelPos.left, width: panelPos.width }}
    >
      <div className="om-ship-time-preview">
        <Clock3 aria-hidden="true" />
        <strong>{draftValue}</strong>
      </div>
      <div className="om-ship-time-periods" role="tablist" aria-label={label}>
        {SHIP_PERIODS.map((period) => (
          <button
            key={period.id}
            type="button"
            role="tab"
            aria-selected={activePeriod === period.id}
            className={activePeriod === period.id ? "active" : undefined}
            onClick={() => {
              const hours = period.hours as readonly string[];
              setDraftHour(hours.includes(draftHour) ? draftHour : period.hours[0]);
            }}
          >
            {periodLabels[period.id]}
          </button>
        ))}
      </div>
      <div className="om-ship-time-columns">
        <div className="om-ship-time-col">
          <span>{hourLabel}</span>
          <div className="om-ship-time-list" ref={hourListRef} role="listbox" aria-label={hourLabel}>
            {visibleHours.map((hour) => (
              <button
                key={hour}
                type="button"
                role="option"
                aria-selected={hour === draftHour}
                data-active={hour === draftHour ? "true" : undefined}
                className={hour === draftHour ? "active" : undefined}
                onClick={() => setDraftHour(hour)}
              >
                {hour}
              </button>
            ))}
          </div>
        </div>
        <div className="om-ship-time-col">
          <span>{minuteLabel}</span>
          <div className="om-ship-time-list om-ship-time-minutes" role="listbox" aria-label={minuteLabel}>
            {SHIP_MINUTES.map((minute) => (
              <button
                key={minute}
                type="button"
                role="option"
                aria-selected={minute === draftMinute}
                className={minute === draftMinute ? "active" : undefined}
                onClick={() => {
                  setDraftMinute(minute);
                  commit(draftHour, minute);
                }}
              >
                :{minute}
              </button>
            ))}
          </div>
        </div>
      </div>
      <button type="button" className="om-ship-time-apply" onClick={() => commit()}>
        {applyLabel} · {draftValue}
      </button>
    </div>,
    document.body,
  ) : null;

  return (
    <div className={`om-ship-time${open ? " open" : ""}`} ref={rootRef}>
      <span className="om-ship-time-label">{label}</span>
      <div className={`om-ship-time-control${open ? " open" : ""}`} ref={controlRef}>
        <input
          className="om-ship-time-input"
          inputMode="numeric"
          autoComplete="off"
          spellCheck={false}
          placeholder="HH:mm"
          aria-label={label}
          value={text}
          onChange={(event) => setText(formatTimeTyping(event.target.value))}
          onFocus={() => setOpen(false)}
          onBlur={commitTyped}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              commitTyped();
              (event.target as HTMLInputElement).blur();
            }
          }}
        />
        <button
          type="button"
          className="om-ship-time-toggle"
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-label={label}
          title={label}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => setOpen((prev) => !prev)}
        >
          <Clock3 aria-hidden="true" />
        </button>
      </div>
      {panel}
    </div>
  );
}

type OrderDateTimeFieldProps = {
  value: string;
  onChange: (value: string) => void;
  localeTag: string;
  title: string;
  hint: string;
  presetsLabel: string;
  nowLabel: string;
  dateLabel: string;
  timeLabel: string;
  hourLabel: string;
  minuteLabel: string;
  applyLabel: string;
  selectedLabel: string;
  datePlaceholder?: string;
  error?: string;
  maxDate?: string;
  periodLabels: Record<ShipPeriodId, string>;
  presetLabels: Record<"0900" | "1200" | "1500" | "1800", string>;
};

/** Shared date+time card used by ship confirm and refund slip dialogs. */
export function OrderDateTimeField({
  value,
  onChange,
  localeTag,
  title,
  hint,
  presetsLabel,
  nowLabel,
  dateLabel,
  timeLabel,
  hourLabel,
  minuteLabel,
  applyLabel,
  selectedLabel,
  datePlaceholder,
  error,
  maxDate,
  periodLabels,
  presetLabels,
}: OrderDateTimeFieldProps) {
  const parts = splitShippedAt(value);
  const today = bangkokNowInput().slice(0, 10);
  const limit = maxDate ?? today;

  return (
    <div className="om-ship-datetime">
      <div className="om-ship-datetime-head">
        <div className="om-ship-datetime-title">
          <CalendarDays aria-hidden="true" />
          <span>{title}</span>
        </div>
        <small>{hint}</small>
      </div>
      <div className="om-ship-presets" role="group" aria-label={presetsLabel}>
        <button type="button" onClick={() => onChange(shipNowInput())}>
          <Clock3 aria-hidden="true" />
          {nowLabel}
        </button>
        {(["09:00", "12:00", "15:00", "18:00"] as const).map((time) => {
          const next = `${today}T${time}`;
          return (
            <button
              key={time}
              type="button"
              className={value === next ? "active" : undefined}
              onClick={() => onChange(next)}
            >
              {presetLabels[time.replace(":", "") as "0900" | "1200" | "1500" | "1800"]}
            </button>
          );
        })}
      </div>
      <div className="om-ship-datetime-row">
        <DatePicker
          compact
          className="om-ship-date"
          label={dateLabel}
          value={parts.date}
          max={limit}
          placeholder={datePlaceholder}
          error={error}
          onChange={(date) => {
            onChange(joinShippedAt(date, parts.time || shipNowInput().slice(11, 16)));
          }}
        />
        <ShipTimePicker
          label={timeLabel}
          value={parts.time}
          hourLabel={hourLabel}
          minuteLabel={minuteLabel}
          applyLabel={applyLabel}
          periodLabels={periodLabels}
          onChange={(time) => {
            const date = parts.date || today;
            onChange(joinShippedAt(date, time));
          }}
        />
      </div>
      {error ? (
        <p className="om-field-error" role="alert">{error}</p>
      ) : value ? (
        <p className="om-ship-datetime-preview">
          <span>{selectedLabel}</span>
          <strong>{formatBangkokDateTime(shipPreviewValue(value), localeTag)}</strong>
        </p>
      ) : null}
    </div>
  );
}
