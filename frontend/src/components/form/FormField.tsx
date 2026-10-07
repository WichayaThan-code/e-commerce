import { CircleAlert, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

type FormFieldProps = {
  label: ReactNode;
  icon?: LucideIcon;
  error?: string;
  errorId?: string;
  className?: string;
  multiline?: boolean;
  children: ReactNode;
};

export default function FormField({ label, icon: Icon, error, errorId, className, multiline, children }: FormFieldProps) {
  const classes = ["form-field", className].filter(Boolean).join(" ");
  return (
    <label className={classes}>
      <span className="form-label">{label}</span>
      <span className={multiline ? "form-control form-control-area" : "form-control"}>
        {Icon ? <Icon aria-hidden="true" /> : null}
        {children}
      </span>
      {error ? (
        <span className="form-field-error" id={errorId}>
          <CircleAlert size={14} aria-hidden="true" />
          {error}
        </span>
      ) : null}
    </label>
  );
}

export function FieldError({ text, id }: { text?: string; id?: string }) {
  if (!text) return null;
  return (
    <span className="form-field-error" id={id}>
      <CircleAlert size={14} aria-hidden="true" />
      {text}
    </span>
  );
}
