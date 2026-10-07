import { useEffect, useRef, useState } from "react";
import { useBlocker } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { productApi, type ProductImportJob } from "../../services/api";

const POLL_MS = 1500;

export function importJobKey(shopId: number) {
  return `thaishopfun.productImport.${shopId}`;
}

type Props = {
  shopId: number;
  uploadPercent: number | null;
  jobId: string | null;
  uploadError: string | null;
  onBusy: (busy: boolean) => void;
  onClose: () => void;
};

export default function ImportProgressDialog({ shopId, uploadPercent, jobId, uploadError, onBusy, onClose }: Props) {
  const { t } = useTranslation("merchant");
  const dialogRef = useRef<HTMLDivElement>(null);
  const [job, setJob] = useState<ProductImportJob | null>(null);
  const [pollError, setPollError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const finished = job?.status === "COMPLETED" || job?.status === "FAILED";
  const working = !uploadError && !pollError && !finished;
  const blocker = useBlocker(working);

  useEffect(() => {
    onBusy(working);
  }, [onBusy, working]);

  useEffect(() => {
    if (blocker.state === "blocked") blocker.reset();
  }, [blocker]);

  useEffect(() => {
    if (!working) return;
    const onLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onLeave);
    return () => window.removeEventListener("beforeunload", onLeave);
  }, [working]);

  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  useEffect(() => {
    const node = dialogRef.current;
    const focusable = () => Array.from(node?.querySelectorAll<HTMLElement>("button, [href], input, [tabindex]:not([tabindex='-1'])") || []).filter((item) => !item.hasAttribute("disabled"));
    const items = focusable();
    (items[0] || node)?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      if (event.key !== "Tab") return;
      const current = focusable();
      if (current.length === 0) {
        event.preventDefault();
        node?.focus();
        return;
      }
      const first = current[0];
      const last = current[current.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [finished, pollError, uploadError]);

  useEffect(() => {
    if (!jobId || uploadError) return;
    let cancelled = false;
    let timer = 0;
    let misses = 0;
    const poll = async () => {
      try {
        const next = await productApi.importStatus(shopId, jobId);
        if (cancelled) return;
        misses = 0;
        setPollError(false);
        setJob(next);
        if (next.status === "COMPLETED" || next.status === "FAILED") return;
      } catch {
        if (cancelled) return;
        misses += 1;
        if (misses >= 5) {
          setPollError(true);
          return;
        }
      }
      timer = window.setTimeout(() => void poll(), POLL_MS);
    };
    void poll();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shopId, jobId, uploadError, attempt]);

  const percent = finished
    ? 100
    : uploadPercent != null
      ? Math.min(10, Math.round(uploadPercent * 0.1))
      : job && job.total > 0
        ? Math.min(100, 10 + Math.round((job.processed / job.total) * 90))
        : 10;
  const caption = uploadError
    ? uploadError
    : pollError
      ? t("catalog.importPollError")
      : uploadPercent != null && !job
        ? t("catalog.importUploading", { percent })
        : t("catalog.importRunning", {
          processed: job?.processed ?? 0,
          total: job?.total ?? 0,
          created: job?.created ?? 0,
          failed: job?.failed ?? 0,
        });

  return (
    <div className="pm-import-overlay">
      <div
        ref={dialogRef}
        className="pm-import-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="pm-import-title"
        aria-busy={working}
        tabIndex={-1}
      >
        <h2 id="pm-import-title">{finished ? t("catalog.importDialogDone") : t("catalog.importDialogTitle")}</h2>
        <p className="pm-import-percent">{percent}%</p>
        <div className="pm-import-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
          <span style={{ width: `${percent}%` }} />
        </div>
        <p>{caption}</p>
        {working ? <p className="pm-import-stay">{t("catalog.importStay")}</p> : null}
        {finished && job && job.errors.length > 0 ? (
          <ul className="pm-import-errors">
            {job.errors.map((error) => <li key={`${error.row}-${error.message}`}>{t("catalog.importRow", { row: error.row, message: error.message })}</li>)}
          </ul>
        ) : null}
        {pollError ? <button type="button" onClick={() => { setPollError(false); setAttempt((value) => value + 1); }}>{t("catalog.importRetry")}</button> : null}
        {finished || uploadError || pollError ? <button type="button" onClick={onClose}>{t("catalog.importClose")}</button> : null}
      </div>
    </div>
  );
}
