import { Save } from "lucide-react";

type MerchantSaveButtonProps = {
  saving: boolean;
  onClick: () => void;
  /** Extra disable reason (no permission, nothing changed, …). `saving` always disables. */
  disabled?: boolean;
  title?: string;
  label?: string;
  savingLabel?: string;
};

/**
 * Card-header save button — the design of the "เวลาทำการ" save button in /merchant/settings
 * (blue #087bf5, 40px, radius 6, Save icon 15px, "บันทึก" / "กำลังบันทึก..."). Styles: `.merchant-save-btn` in merchant-dashboard.css.
 */
export default function MerchantSaveButton({
  saving,
  onClick,
  disabled = false,
  title,
  label = "บันทึก",
  savingLabel = "กำลังบันทึก...",
}: MerchantSaveButtonProps) {
  return (
    <button
      type="button"
      className="merchant-save-btn"
      disabled={saving || disabled}
      aria-busy={saving || undefined}
      title={title}
      onClick={onClick}
    >
      <Save aria-hidden="true" />
      {saving ? savingLabel : label}
    </button>
  );
}
