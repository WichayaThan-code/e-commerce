import { useEffect, useId, useRef, useState } from "react";
import MerchantSaveButton from "./MerchantSaveButton";
import { ApiError, type Promotion, type PromotionType, type PromotionWrite } from "../../services/api";
import { bangkokNowInput, toBangkokInput } from "./promotionFormat";
import "../../styles/product-management.css";
import "../../styles/promotion-management.css";

export type PromotionDraft = {
  name: string;
  code: string;
  type: PromotionType;
  value: string;
  minOrderAmount: string;
  maxDiscountAmount: string;
  startsAt: string;
  endsAt: string;
  usageLimitTotal: string;
  usageLimitPerBuyer: string;
  showOnStorefront: boolean;
  active: boolean;
};

export function emptyDraft(partial: Partial<PromotionDraft> = {}): PromotionDraft {
  return {
    name: "", code: "", type: "PERCENT", value: "", minOrderAmount: "", maxDiscountAmount: "",
    startsAt: bangkokNowInput(), endsAt: bangkokNowInput(30), usageLimitTotal: "", usageLimitPerBuyer: "",
    showOnStorefront: true, active: true, ...partial,
  };
}

const text = (value: number | null | undefined) => (value == null ? "" : String(value));

export function draftFrom(promotion: Promotion, copy = false): PromotionDraft {
  const code = promotion.code ?? "";
  return {
    name: copy ? `${promotion.name} (สำเนา)`.slice(0, 150) : promotion.name,
    code: copy ? `${code.slice(0, 27)}-COPY` : code,
    type: promotion.type,
    value: promotion.type === "FREE_SHIPPING" ? "" : text(promotion.value),
    minOrderAmount: text(promotion.minOrderAmount),
    maxDiscountAmount: text(promotion.maxDiscountAmount),
    startsAt: copy ? bangkokNowInput() : toBangkokInput(promotion.startsAt),
    endsAt: copy ? bangkokNowInput(30) : toBangkokInput(promotion.endsAt),
    usageLimitTotal: text(promotion.usageLimitTotal),
    usageLimitPerBuyer: text(promotion.usageLimitPerBuyer),
    showOnStorefront: promotion.showOnStorefront,
    active: copy ? true : promotion.active,
  };
}

const CODE_PATTERN = /^[A-Z0-9_-]{3,32}$/;
const decimal = (raw: string) => (raw.trim() === "" ? null : Number(raw));
const integer = (raw: string) => (raw.trim() === "" ? null : Number(raw));

/** Client checks mirror the backend (which stays the source of truth and returns the same field codes). */
export function validateDraft(draft: PromotionDraft): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = draft.name.trim();
  if (!name) errors.name = "กรุณาระบุชื่อโปรโมชัน";
  else if (name.length > 150) errors.name = "ชื่อโปรโมชันยาวเกิน 150 ตัวอักษร";
  const code = draft.code.trim().toUpperCase();
  if (!code) errors.code = "กรุณาระบุรหัสคูปอง";
  else if (!CODE_PATTERN.test(code)) errors.code = "ใช้ได้เฉพาะ A-Z 0-9 _ - ยาว 3-32 ตัว";
  const value = decimal(draft.value);
  if (draft.type === "PERCENT" && (value == null || !Number.isFinite(value) || value < 1 || value > 99)) errors.value = "ส่วนลดเปอร์เซ็นต์ต้องอยู่ระหว่าง 1-99";
  if (draft.type === "FIXED_AMOUNT" && (value == null || !Number.isFinite(value) || value <= 0 || value > 1_000_000)) errors.value = "ส่วนลดต้องมากกว่า 0 และไม่เกิน 1,000,000 บาท";
  const max = decimal(draft.maxDiscountAmount);
  if (draft.type === "PERCENT" && max != null && (!Number.isFinite(max) || max <= 0)) errors.maxDiscountAmount = "ส่วนลดสูงสุดต้องมากกว่า 0";
  const min = decimal(draft.minOrderAmount);
  if (min != null && (!Number.isFinite(min) || min < 0)) errors.minOrderAmount = "ยอดขั้นต่ำต้องไม่ติดลบ";
  if (draft.type === "FIXED_AMOUNT" && value != null && min != null && min > 0 && value > min) errors.value = "ส่วนลดต้องไม่มากกว่ายอดสั่งซื้อขั้นต่ำ";
  if (!draft.startsAt) errors.startsAt = "กรุณาระบุวันเริ่ม";
  if (draft.startsAt && draft.endsAt && draft.endsAt <= draft.startsAt) errors.endsAt = "วันสิ้นสุดต้องหลังวันเริ่ม";
  const limit = integer(draft.usageLimitTotal);
  if (limit != null && (!Number.isInteger(limit) || limit < 1 || limit > 1_000_000)) errors.usageLimitTotal = "จำนวนสิทธิ์ต้องอยู่ระหว่าง 1-1,000,000";
  const perBuyer = integer(draft.usageLimitPerBuyer);
  if (perBuyer != null && (!Number.isInteger(perBuyer) || perBuyer < 1 || perBuyer > 1000)) errors.usageLimitPerBuyer = "สิทธิ์ต่อผู้ซื้อต้องอยู่ระหว่าง 1-1,000";
  else if (perBuyer != null && limit != null && perBuyer > limit) errors.usageLimitPerBuyer = "ต้องไม่มากกว่าจำนวนสิทธิ์ทั้งหมด";
  return errors;
}

export function draftToPayload(draft: PromotionDraft): PromotionWrite {
  return {
    name: draft.name.trim(),
    code: draft.code.trim().toUpperCase(),
    type: draft.type,
    value: draft.type === "FREE_SHIPPING" ? 0 : decimal(draft.value),
    minOrderAmount: decimal(draft.minOrderAmount),
    // FREE_SHIPPING keeps its shipping cap (e.g. FREESHIP399 = 60) even though the form only edits it for PERCENT
    maxDiscountAmount: draft.type === "FIXED_AMOUNT" ? null : decimal(draft.maxDiscountAmount),
    startsAt: draft.startsAt,
    endsAt: draft.endsAt || null,
    usageLimitTotal: integer(draft.usageLimitTotal),
    usageLimitPerBuyer: integer(draft.usageLimitPerBuyer),
    showOnStorefront: draft.showOnStorefront,
    active: draft.active,
  };
}

type Props = {
  mode: "create" | "edit";
  initial: PromotionDraft;
  usedCount?: number;
  onSubmit: (payload: PromotionWrite) => Promise<void>;
  onCancel: () => void;
};

/** Create/edit promotion — same `.pm-dialog` shell, focus trap and Escape handling as ConfirmDialog. */
export default function PromotionFormDialog({ mode, initial, usedCount = 0, onSubmit, onCancel }: Props) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);

  useEffect(() => {
    firstFieldRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (savingRef.current) return;
        event.preventDefault();
        onCancelRef.current();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")]
        .filter((node) => !node.hasAttribute("disabled"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  const set = <K extends keyof PromotionDraft>(key: K, value: PromotionDraft[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => { if (!current[key as string]) return current; const next = { ...current }; delete next[key as string]; return next; });
  };

  const submit = async () => {
    if (savingRef.current) return;
    const found = validateDraft(draft);
    setErrors(found);
    setFormError("");
    if (Object.keys(found).length) return;
    savingRef.current = true;
    setSaving(true);
    try {
      await onSubmit(draftToPayload(draft));
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors?.length) {
        const mapped: Record<string, string> = {};
        for (const item of error.fieldErrors) if (!mapped[item.field]) mapped[item.field] = item.message;
        setErrors(mapped);
      }
      setFormError(error instanceof Error ? error.message : "บันทึกไม่สำเร็จ กรุณาลองใหม่");
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const field = (name: keyof PromotionDraft) => ({
    "aria-invalid": errors[name as string] ? true : undefined,
    "aria-describedby": errors[name as string] ? `${titleId}-${String(name)}` : undefined,
  });
  const error = (name: string) => errors[name] ? <small className="promo-field-error" id={`${titleId}-${name}`}>{errors[name]}</small> : null;

  return (
    <div className="pm-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialogRef}>
      <section className="promo-dialog">
        <h2 id={titleId}>{mode === "create" ? "สร้างโปรโมชั่น / คูปอง" : "แก้ไขโปรโมชั่น / คูปอง"}</h2>
        <p>ลูกค้าจะเห็นรหัสคูปองบนหน้าร้าน — การใช้คูปองตอนชำระเงินจะเปิดให้ใช้เร็วๆ นี้</p>
        <form className="promo-form" noValidate onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          <label className="wide">ชื่อโปรโมชั่น<input ref={firstFieldRef} value={draft.name} maxLength={150} onChange={(event) => set("name", event.target.value)} placeholder="เช่น ส่วนลดต้อนรับลูกค้าใหม่" {...field("name")} />{error("name")}</label>
          <label>รหัสคูปอง<input className="num" value={draft.code} maxLength={32} autoCapitalize="characters" spellCheck={false} onChange={(event) => set("code", event.target.value.toUpperCase().replace(/\s+/g, ""))} placeholder="WELCOME10" {...field("code")} />{error("code")}</label>
          <label>ประเภท<select value={draft.type} onChange={(event) => set("type", event.target.value as PromotionType)}>
            <option value="PERCENT">ส่วนลด %</option>
            <option value="FIXED_AMOUNT">ส่วนลดบาท</option>
            <option value="FREE_SHIPPING">ส่งฟรี</option>
          </select></label>
          {draft.type !== "FREE_SHIPPING" && <label>{draft.type === "PERCENT" ? "ส่วนลด (%)" : "ส่วนลด (บาท)"}<input className="num" inputMode="decimal" value={draft.value} onChange={(event) => set("value", event.target.value)} placeholder={draft.type === "PERCENT" ? "10" : "50"} {...field("value")} />{error("value")}</label>}
          {draft.type === "PERCENT" && <label>ส่วนลดสูงสุด (บาท)<input className="num" inputMode="decimal" value={draft.maxDiscountAmount} onChange={(event) => set("maxDiscountAmount", event.target.value)} placeholder="ไม่จำกัด" {...field("maxDiscountAmount")} />{error("maxDiscountAmount")}</label>}
          <label>ยอดสั่งซื้อขั้นต่ำ (บาท)<input className="num" inputMode="decimal" value={draft.minOrderAmount} onChange={(event) => set("minOrderAmount", event.target.value)} placeholder="ไม่มีขั้นต่ำ" {...field("minOrderAmount")} />{error("minOrderAmount")}</label>
          <label>วันเริ่ม<input className="num" type="datetime-local" value={draft.startsAt} onChange={(event) => set("startsAt", event.target.value)} {...field("startsAt")} />{error("startsAt")}</label>
          <label>วันสิ้นสุด<input className="num" type="datetime-local" value={draft.endsAt} min={draft.startsAt || undefined} onChange={(event) => set("endsAt", event.target.value)} {...field("endsAt")} />{error("endsAt") ?? <small>เว้นว่าง = ไม่มีกำหนด (เวลาประเทศไทย)</small>}</label>
          <label>จำนวนสิทธิ์ทั้งหมด<input className="num" inputMode="numeric" value={draft.usageLimitTotal} onChange={(event) => set("usageLimitTotal", event.target.value.replace(/[^0-9]/g, ""))} placeholder="ไม่จำกัด" {...field("usageLimitTotal")} />{error("usageLimitTotal") ?? (usedCount > 0 ? <small>ใช้ไปแล้ว <span className="num">{usedCount.toLocaleString()}</span> ครั้ง</small> : null)}</label>
          <label>สิทธิ์ต่อผู้ซื้อ<input className="num" inputMode="numeric" value={draft.usageLimitPerBuyer} onChange={(event) => set("usageLimitPerBuyer", event.target.value.replace(/[^0-9]/g, ""))} placeholder="ไม่จำกัด" {...field("usageLimitPerBuyer")} />{error("usageLimitPerBuyer")}</label>
          <label className="promo-check"><input type="checkbox" checked={draft.showOnStorefront} onChange={(event) => set("showOnStorefront", event.target.checked)} />แสดงคูปองบนหน้าร้าน</label>
          <label className="promo-check"><input type="checkbox" checked={draft.active} onChange={(event) => set("active", event.target.checked)} />เปิดใช้งาน</label>
        </form>
        {formError && <p className="promo-form-error" role="alert">{formError}</p>}
        <div className="promo-form-actions">
          <button type="button" className="secondary" disabled={saving} onClick={onCancel}>ยกเลิก</button>
          <MerchantSaveButton saving={saving} onClick={() => void submit()} label={mode === "create" ? "สร้างโปรโมชั่น" : "บันทึก"} />
        </div>
      </section>
    </div>
  );
}
