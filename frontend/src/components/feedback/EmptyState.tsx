import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle } from "lucide-react";

export type EmptyStateAction = {
  label: string;
  to?: string;
  onClick?: () => void;
};

type EmptyStateProps = {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: EmptyStateAction;
  /** Extra actions (e.g. contact/policy link groups). Rendered after `action`. */
  actions?: EmptyStateAction[];
  variant?: "empty" | "filtered" | "error";
  role?: "status" | "alert";
  className?: string;
  children?: ReactNode;
};

/**
 * Public/customer empty-state shell matching /shops
 * (`shop-list-empty shop-list-none`). Do not pass icon size/strokeWidth —
 * sizing comes from `.shop-list-none svg`.
 */
export default function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  actions,
  variant = "empty",
  role,
  className,
  children,
}: EmptyStateProps) {
  const resolvedRole = role ?? (variant === "error" ? "alert" : "status");
  const ResolvedIcon = variant === "error" ? AlertTriangle : Icon;
  const allActions = [
    ...(action ? [action] : []),
    ...(actions ?? []),
  ];

  return (
    <article
      className={["shop-list-empty", "shop-list-none", className].filter(Boolean).join(" ")}
      role={resolvedRole}
    >
      {ResolvedIcon ? <ResolvedIcon aria-hidden="true" /> : null}
      <p>{title}</p>
      {description ? <span className="empty-state-desc">{description}</span> : null}
      {children}
      {allActions.length > 0 ? (
        <div className="empty-state-actions">
          {allActions.map((item) =>
            item.to ? (
              <Link key={`${item.label}:${item.to}`} className="empty-state-action" to={item.to}>
                {item.label}
              </Link>
            ) : (
              <button
                key={item.label}
                type="button"
                className="empty-state-action"
                onClick={item.onClick}
              >
                {item.label}
              </button>
            ),
          )}
        </div>
      ) : null}
    </article>
  );
}
