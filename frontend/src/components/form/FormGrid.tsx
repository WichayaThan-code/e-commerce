import type { ReactNode } from "react";

export default function FormGrid({ children }: { children: ReactNode }) {
  return <div className="form-grid">{children}</div>;
}
