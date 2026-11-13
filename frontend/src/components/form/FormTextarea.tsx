import type { ReactNode, TextareaHTMLAttributes } from "react";
import FormField from "./FormField";
import type { LucideIcon } from "lucide-react";

type FormTextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: ReactNode;
  icon?: LucideIcon;
  error?: string;
  errorId?: string;
  field?: string;
};

export default function FormTextarea({ label, icon, error, errorId, field, id, rows = 3, ...props }: FormTextareaProps) {
  const describedBy = [props["aria-describedby"], error && errorId ? errorId : ""].filter(Boolean).join(" ") || undefined;
  return (
    <FormField label={label} icon={icon} error={error} errorId={errorId} field={field} multiline>
      <textarea id={id} rows={rows} aria-invalid={Boolean(error)} aria-describedby={describedBy} {...props} />
    </FormField>
  );
}
