import type { ReactNode, SelectHTMLAttributes } from "react";
import FormField from "./FormField";
import type { LucideIcon } from "lucide-react";

type FormSelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: ReactNode;
  icon?: LucideIcon;
  error?: string;
  errorId?: string;
};

export default function FormSelect({ label, icon, error, errorId, id, children, ...props }: FormSelectProps) {
  const describedBy = [props["aria-describedby"], error && errorId ? errorId : ""].filter(Boolean).join(" ") || undefined;
  return (
    <FormField label={label} icon={icon} error={error} errorId={errorId}>
      <select id={id} aria-invalid={Boolean(error)} aria-describedby={describedBy} {...props}>
        {children}
      </select>
    </FormField>
  );
}
