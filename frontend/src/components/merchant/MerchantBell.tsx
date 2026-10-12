import { useEffect, useRef, useState } from "react";
import { Bell } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useImportActivity } from "../../contexts/ImportActivityContext";

export default function MerchantBell() {
  const { t } = useTranslation("merchant");
  const { notice, attention, dismiss } = useImportActivity();
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const working = notice != null && notice.phase !== "done" && notice.phase !== "error";

  useEffect(() => {
    if (attention > 0) setOpen(true);
  }, [attention]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    const onPointer = (event: MouseEvent) => {
      if (!panelRef.current?.contains(event.target as Node)) setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onPointer);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onPointer);
    };
  }, [open]);

  return (
    <div className="merchant-notify" ref={panelRef}>
      <button
        className="merchant-bell"
        type="button"
        aria-label={t("catalog.bell.label")}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell />
        {working ? <i>{notice.percent}</i> : notice ? <i>1</i> : null}
      </button>
      {open ? (
        <section className="merchant-notify-panel" role="dialog" aria-label={t("catalog.bell.label")}>
          <header>
            <b>{t("catalog.bell.label")}</b>
          </header>
          {notice == null ? <p className="merchant-notify-empty">{t("catalog.bell.empty")}</p> : (
            <article className={notice.phase === "error" ? "fail" : undefined}>
              <b>{notice.title}</b>
              {working || notice.phase === "done" ? (
                <div className="merchant-notify-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={notice.percent}>
                  <span style={{ width: `${notice.percent}%` }} />
                </div>
              ) : null}
              <small>{notice.detail}</small>
              {notice.errors.length > 0 ? (
                <ul>
                  {notice.errors.map((line) => <li key={line}>{line}</li>)}
                </ul>
              ) : null}
              {notice.phase === "done" || notice.phase === "error" ? (
                <button type="button" onClick={() => { dismiss(); setOpen(false); }}>{t("catalog.bell.dismiss")}</button>
              ) : null}
            </article>
          )}
        </section>
      ) : null}
    </div>
  );
}
