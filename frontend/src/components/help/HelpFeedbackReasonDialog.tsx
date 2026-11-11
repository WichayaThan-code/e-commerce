import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

export const HELP_NOT_HELPFUL_REASONS = [
  "HARD_TO_UNDERSTAND",
  "INCORRECT_OR_INCOMPLETE",
  "DID_NOT_SOLVE",
  "BAD_LINKS",
  "POLICY_FRICTION",
] as const;

export type HelpNotHelpfulReason = (typeof HELP_NOT_HELPFUL_REASONS)[number];

type Props = {
  busy?: boolean;
  onCancel: () => void;
  onSubmit: (reasonCode: HelpNotHelpfulReason) => void;
};

/** Modal asking why an FAQ answer was not helpful. */
export default function HelpFeedbackReasonDialog({ busy = false, onCancel, onSubmit }: Props) {
  const { t } = useTranslation("public");
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const [reason, setReason] = useState<HelpNotHelpfulReason | "">("");

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCancel]);

  return (
    <div className="help-reason-overlay" role="presentation" onMouseDown={onCancel}>
      <div
        ref={dialogRef}
        className="help-reason-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button type="button" className="help-reason-close" aria-label={t("helpPage.faqDetail.reasonClose")} onClick={onCancel}>
          <X aria-hidden="true" />
        </button>
        <h2 id={titleId}>{t("helpPage.faqDetail.reasonTitle")}</h2>
        <div className="help-reason-options" role="radiogroup" aria-labelledby={titleId}>
          {HELP_NOT_HELPFUL_REASONS.map((code) => (
            <label key={code} className="help-reason-option">
              <input
                type="radio"
                name="help-not-helpful-reason"
                value={code}
                checked={reason === code}
                disabled={busy}
                onChange={() => setReason(code)}
              />
              <span>{t(`helpPage.faqDetail.reasons.${code}`)}</span>
            </label>
          ))}
        </div>
        <div className="help-reason-actions">
          <button type="button" className="help-reason-cancel" disabled={busy} onClick={onCancel}>
            {t("helpPage.faqDetail.reasonCancel")}
          </button>
          <button
            type="button"
            className="help-reason-submit"
            disabled={busy || !reason}
            onClick={() => {
              if (reason) onSubmit(reason);
            }}
          >
            {t("helpPage.faqDetail.reasonSubmit")}
          </button>
        </div>
      </div>
    </div>
  );
}
