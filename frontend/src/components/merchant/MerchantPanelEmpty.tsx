import type { ReactNode } from "react";

/** Centered empty / unavailable state for merchant report & finance panels. */
export default function MerchantPanelEmpty({
  icon,
  title,
  hint,
  className,
}: {
  icon?: ReactNode;
  title: ReactNode;
  hint?: ReactNode;
  className?: string;
}) {
  return (
    <div className={`merchant-panel-empty${className ? ` ${className}` : ""}`}>
      {icon != null ? <span>{icon}</span> : null}
      <b>{title}</b>
      {hint != null && hint !== "" ? <small>{hint}</small> : null}
    </div>
  );
}
