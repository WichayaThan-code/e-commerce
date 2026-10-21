import { useEffect, useState } from "react";
import { CalendarDays } from "lucide-react";
import MerchantSaveButton from "./MerchantSaveButton";
import { ApiError, merchantApi, type ShopHour } from "../../services/api";

export const DAY_NAMES = ["จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์"] as const;

export const defaultHours = (): ShopHour[] =>
  DAY_NAMES.map((_, index) => ({
    open: index < 5,
    start: index < 5 ? "08:00" : "09:00",
    end: index < 5 ? "20:00" : "18:00",
  }));

type ShopHoursEditorProps = {
  shopId: number | null;
  /** openingHours from GET /api/merchant/settings (page already loaded it — no second fetch). */
  initialHours: ShopHour[] | undefined;
  canUpdate: boolean;
  deniedTitle?: string;
  notify: (message: string) => void;
  onSaved?: (hours: ShopHour[]) => void;
  onDirtyChange?: (dirty: boolean) => void;
};

const normalize = (hours: ShopHour[] | undefined) => (hours?.length === 7 ? hours : defaultHours());

/** "เวลาทำการ" card — moved verbatim from SettingsPage (same markup/classes: settings-card general-card + hours-list). */
export default function ShopHoursEditor({
  shopId,
  initialHours,
  canUpdate,
  deniedTitle,
  notify,
  onSaved,
  onDirtyChange,
}: ShopHoursEditorProps) {
  const [hours, setHours] = useState<ShopHour[]>(() => normalize(initialHours));
  const [baseline, setBaseline] = useState<ShopHour[]>(() => normalize(initialHours));
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const next = normalize(initialHours);
    setHours(next);
    setBaseline(next);
  }, [initialHours]);

  const dirty = JSON.stringify(hours) !== JSON.stringify(baseline);
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);

  const updateHour = (index: number, patch: Partial<ShopHour>) =>
    setHours((current) => current.map((day, i) => (i === index ? { ...day, ...patch } : day)));

  const saveHours = async () => {
    if (saving) return;
    if (hours.some((day) => day.open && day.start >= day.end)) {
      notify("เวลาปิดต้องอยู่หลังเวลาเปิด");
      return;
    }
    setSaving(true);
    try {
      const saved = await merchantApi.saveHours(hours, shopId);
      const next = saved.openingHours?.length === 7 ? saved.openingHours : hours;
      setHours(next);
      setBaseline(next);
      onSaved?.(next);
      notify("บันทึกเวลาทำการแล้ว");
    } catch (error) {
      notify(error instanceof ApiError ? error.message : "บันทึกไม่สำเร็จ กรุณาลองใหม่");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="settings-card general-card">
      <header>
        <div>
          <h2><CalendarDays />เวลาทำการ</h2>
          <p>เลือกวันที่ร้านเปิด และกำหนดเวลาเปิดถึงเวลาปิดของแต่ละวัน · แสดงบนหน้าร้านและหน้าติดต่อร้าน</p>
        </div>
        <MerchantSaveButton saving={saving} disabled={!canUpdate} title={deniedTitle} onClick={() => void saveHours()} />
      </header>
      <ul className="hours-list">
        {DAY_NAMES.map((day, index) => {
          const item = hours[index];
          return (
            <li key={day}>
              <label>
                <input type="checkbox" checked={item?.open ?? false} disabled={!canUpdate} onChange={() => updateHour(index, { open: !item?.open })} />
                <b>{day}</b>
              </label>
              <input type="time" aria-label={`เวลาเปิด${day}`} value={item?.start || "08:00"} disabled={!item?.open || !canUpdate} onChange={(event) => updateHour(index, { start: event.target.value })} />
              <span>ถึง</span>
              <input type="time" aria-label={`เวลาปิด${day}`} value={item?.end || "20:00"} disabled={!item?.open || !canUpdate} onChange={(event) => updateHour(index, { end: event.target.value })} />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
