import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useActiveShop } from "./ActiveShopContext";
import { ApiError, productApi, type ProductImportJob } from "../services/api";
import { catalogMessage } from "../services/catalogText";

const POLL_MS = 1500;

export function importJobKey(shopId: number) {
  return `thaishopfun.productImport.${shopId}`;
}

export type ImportNotice = {
  id: string;
  phase: "upload" | "import" | "done" | "error";
  percent: number;
  title: string;
  detail: string;
  errors: string[];
};

type ImportActivityValue = {
  busy: boolean;
  notice: ImportNotice | null;
  attention: number;
  finishedTick: number;
  startImport: (file: File) => Promise<void>;
  dismiss: () => void;
};

const ImportActivityContext = createContext<ImportActivityValue | null>(null);

function jobPercent(job: ProductImportJob) {
  if (job.status === "COMPLETED" || job.status === "FAILED") return 100;
  if (job.total <= 0) return 10;
  return Math.min(100, Math.round((job.processed / job.total) * 100));
}

export function ImportActivityProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation("merchant");
  const { shopId } = useActiveShop();
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);
  const [job, setJob] = useState<ProductImportJob | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [attention, setAttention] = useState(0);
  const [finishedTick, setFinishedTick] = useState(0);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (shopId == null) return;
    const saved = sessionStorage.getItem(importJobKey(shopId));
    if (!saved) return;
    setJobId(saved);
    setDismissed(false);
    setAttention((value) => value + 1);
  }, [shopId]);

  useEffect(() => {
    if (uploadPercent == null || jobId) return;
    const onLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onLeave);
    return () => window.removeEventListener("beforeunload", onLeave);
  }, [uploadPercent, jobId]);

  useEffect(() => {
    if (shopId == null || !jobId || error) return;
    let cancelled = false;
    let timer = 0;
    let misses = 0;
    const poll = async () => {
      try {
        const next = await productApi.importStatus(shopId, jobId);
        if (cancelled) return;
        misses = 0;
        setJob(next);
        if (next.status === "COMPLETED" || next.status === "FAILED") {
          sessionStorage.removeItem(importJobKey(shopId));
          setFinishedTick((value) => value + 1);
          setAttention((value) => value + 1);
          return;
        }
      } catch {
        if (cancelled) return;
        misses += 1;
        if (misses >= 5) {
          setError(t("catalog.bell.pollError"));
          setAttention((value) => value + 1);
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
  }, [shopId, jobId, error, attempt, t]);

  const startImport = async (file: File) => {
    if (shopId == null || busyOf(uploadPercent, job, error)) return;
    setError(null);
    setJob(null);
    setJobId(null);
    setDismissed(false);
    setUploadPercent(0);
    setAttention((value) => value + 1);
    try {
      const accepted = await productApi.importExcel(shopId, file, setUploadPercent);
      sessionStorage.setItem(importJobKey(shopId), accepted.jobId);
      setUploadPercent(null);
      setJobId(accepted.jobId);
      setAttempt((value) => value + 1);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409 && caught.code === "IMPORT_RUNNING" && caught.jobId) {
        sessionStorage.setItem(importJobKey(shopId), caught.jobId);
        setUploadPercent(null);
        setJobId(caught.jobId);
        setAttempt((value) => value + 1);
        return;
      }
      setUploadPercent(null);
      setError(catalogMessage(t, caught));
      setAttention((value) => value + 1);
    }
  };

  const dismiss = () => {
    if (shopId != null) sessionStorage.removeItem(importJobKey(shopId));
    setDismissed(true);
    setUploadPercent(null);
    setJob(null);
    setJobId(null);
    setError(null);
  };

  const notice = useMemo(() => buildNotice(t, uploadPercent, job, error, dismissed), [t, uploadPercent, job, error, dismissed]);
  const busy = busyOf(uploadPercent, job, error);

  return (
    <ImportActivityContext.Provider value={{ busy, notice, attention, finishedTick, startImport, dismiss }}>
      {children}
    </ImportActivityContext.Provider>
  );
}

function busyOf(uploadPercent: number | null, job: ProductImportJob | null, error: string | null) {
  if (error) return false;
  if (uploadPercent != null) return true;
  return job != null && job.status !== "COMPLETED" && job.status !== "FAILED";
}

function buildNotice(
  t: (key: string, options?: Record<string, unknown>) => string,
  uploadPercent: number | null,
  job: ProductImportJob | null,
  error: string | null,
  dismissed: boolean,
): ImportNotice | null {
  if (dismissed) return null;
  if (error) {
    return { id: "import-error", phase: "error", percent: 0, title: t("catalog.bell.failed"), detail: error, errors: [] };
  }
  if (uploadPercent != null && !job) {
    return {
      id: "import-upload",
      phase: "upload",
      percent: uploadPercent,
      title: t("catalog.bell.uploading", { percent: uploadPercent }),
      detail: t("catalog.bell.uploadingHint"),
      errors: [],
    };
  }
  if (!job) return null;
  const finished = job.status === "COMPLETED" || job.status === "FAILED";
  const counts = { created: job.created, skuCreated: job.skuCreated ?? 0, failed: job.failed };
  if (!finished) {
    const percent = jobPercent(job);
    return {
      id: "import-running",
      phase: "import",
      percent,
      title: t("catalog.bell.importing", { percent }),
      detail: t("catalog.importRunning", { processed: job.processed, total: job.total, ...counts }),
      errors: [],
    };
  }
  const partial = job.failed > 0 || job.status === "FAILED";
  return {
    id: "import-done",
    phase: "done",
    percent: 100,
    title: partial ? t("catalog.bell.partial", counts) : t("catalog.bell.done", counts),
    detail: t("catalog.importDialogDone"),
    errors: job.errors.slice(0, 5).map((item) => t("catalog.importRow", { row: item.row, message: item.message })),
  };
}

export function useImportActivity() {
  const value = useContext(ImportActivityContext);
  if (!value) throw new Error("useImportActivity must be used inside ImportActivityProvider");
  return value;
}
