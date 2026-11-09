import { useEffect, type ComponentType, type ReactNode, type SVGProps } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";
import "../../styles/shipping.css";
import "../../styles/admin-detail-drawer.css";

type IconType = ComponentType<SVGProps<SVGSVGElement>>;

export type AdminDetailDrawerTab = {
  id: string;
  label: string;
  icon?: IconType;
};

type Props = {
  open?: boolean;
  onClose: () => void;
  title: ReactNode;
  /** Status pill, muted date, id chips — shown under the title */
  meta?: ReactNode;
  /** Extra controls in the header row (before close), e.g. edit buttons */
  headerActions?: ReactNode;
  avatar?: ReactNode;
  /** Fallback letter/icon avatar when `avatar` is omitted */
  avatarFallback?: ReactNode;
  avatarClassName?: string;
  tabs?: AdminDetailDrawerTab[];
  activeTab?: string;
  onTabChange?: (id: string) => void;
  children: ReactNode;
  footer?: ReactNode;
  footerClassName?: string;
  wide?: boolean;
  className?: string;
  closeLabel?: string;
  empty?: ReactNode;
};

/**
 * Shared Admin row-detail drawer — same visual shell as /admin/store-applications
 * (portaled om/cm/sh panel with avatar header, optional tabs, sticky footer).
 */
export default function AdminDetailDrawer({
  open = true,
  onClose,
  title,
  meta,
  headerActions,
  avatar,
  avatarFallback,
  avatarClassName = "",
  tabs,
  activeTab,
  onTabChange,
  children,
  footer,
  footerClassName = "",
  wide = false,
  className = "",
  closeLabel = "Close",
  empty,
}: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !document.querySelector(".pm-dialog")) onClose();
    };
    window.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  const showAvatar = avatar != null || avatarFallback != null;
  const tabCount = tabs?.length ?? 0;

  const drawer = (
    <div
      className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !document.querySelector(".pm-dialog")) onClose();
      }}
    >
      <aside
        className={[
          "om-drawer",
          "cm-detail",
          "sh-detail",
          "om-detail",
          "admin-detail-drawer",
          wide ? "admin-detail-drawer--wide" : "",
          showAvatar ? "" : "admin-detail-drawer--no-avatar",
          className,
        ]
          .filter(Boolean)
          .join(" ")}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === "string" ? title : undefined}
      >
        {empty != null ? (
          <div className="om-drawer-body cm-detail-body">{empty}</div>
        ) : (
          <>
            <header className="cm-detail-head">
              {showAvatar ? (
                <div
                  className={["cm-avatar", "om-avatar", "tone-waiting", "admin-detail-drawer-avatar", avatarClassName]
                    .filter(Boolean)
                    .join(" ")}
                  aria-hidden="true"
                >
                  {avatar ?? avatarFallback}
                </div>
              ) : null}
              <div className="cm-detail-id">
                <h2>{title}</h2>
                {meta ? <div className="cm-detail-meta">{meta}</div> : null}
              </div>
              {headerActions ? <div className="admin-detail-drawer-header-actions">{headerActions}</div> : null}
              <button type="button" className="om-drawer-close" aria-label={closeLabel} onClick={onClose}>
                <X aria-hidden="true" />
              </button>
            </header>

            {tabs && tabs.length > 0 ? (
              <nav
                className="admin-detail-drawer-tabs"
                style={{ gridTemplateColumns: `repeat(${Math.max(1, tabCount)}, minmax(0, 1fr))` }}
                aria-label={typeof title === "string" ? title : undefined}
              >
                {tabs.map((tab) => {
                  const Icon = tab.icon;
                  return (
                    <button
                      key={tab.id}
                      type="button"
                      className={activeTab === tab.id ? "active" : undefined}
                      onClick={() => onTabChange?.(tab.id)}
                    >
                      {Icon ? <Icon aria-hidden="true" /> : null}
                      <span>{tab.label}</span>
                    </button>
                  );
                })}
              </nav>
            ) : null}

            <div className="om-drawer-body cm-detail-body">{children}</div>

            {footer ? (
              <footer className={["om-drawer-actions", "admin-detail-drawer-actions", footerClassName].filter(Boolean).join(" ")}>
                {footer}
              </footer>
            ) : null}
          </>
        )}
      </aside>
    </div>
  );

  return createPortal(drawer, document.body);
}
