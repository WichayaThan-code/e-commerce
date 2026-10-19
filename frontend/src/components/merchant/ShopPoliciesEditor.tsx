import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FileText } from "lucide-react";
import { LoadError, Skeleton } from "../feedback/Skeleton";
import MerchantSaveButton from "./MerchantSaveButton";
import { ApiError, merchantApi, type ApiFieldError, type ShopPolicyDto, type ShopPolicyType } from "../../services/api";
import { SHOP_POLICY_TYPES } from "../../hooks/useShopPolicies";

type PolicyDraft = { type: ShopPolicyType; title: string; body: string };

type ShopPoliciesEditorProps = {
  shopId: number | null;
  canUpdate: boolean;
  deniedTitle?: string;
  notify: (message: string) => void;
  onDirtyChange: (dirty: boolean) => void;
};

const TITLE_MAX = 150;
const BODY_MAX = 20000;

function toDrafts(rows: ShopPolicyDto[]): PolicyDraft[] {
  return SHOP_POLICY_TYPES.map((type) => {
    const found = rows.find((row) => row.type === type);
    return { type, title: found?.title?.trim() || "", body: found?.body || "" };
  });
}

function toUpdatedMap(rows: ShopPolicyDto[]): Partial<Record<ShopPolicyType, string>> {
  const map: Partial<Record<ShopPolicyType, string>> = {};
  for (const row of rows) if (row.updatedAt) map[row.type] = row.updatedAt;
  return map;
}

const sameDraft = (a: PolicyDraft | undefined, b: PolicyDraft | undefined) =>
  (a?.title ?? "") === (b?.title ?? "") && (a?.body ?? "") === (b?.body ?? "");

function formatUpdated(value: string | undefined, locale: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const tag = locale.startsWith("zh") ? "zh-CN" : locale.startsWith("en") ? "en-GB" : "th-TH";
  return new Intl.DateTimeFormat(tag, { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}

/**
 * The ONE store-policies editor (GET/PUT /api/merchant/settings/policies).
 * PUT replaces all 4 types: a type with an empty body is deleted server-side — so the payload always carries all 4 drafts.
 */
export default function ShopPoliciesEditor({ shopId, canUpdate, deniedTitle, notify, onDirtyChange }: ShopPoliciesEditorProps) {
  const { t, i18n } = useTranslation("merchant");
  const [state, setState] = useState<"loading" | "ok" | "error">("loading");
  const [drafts, setDrafts] = useState<PolicyDraft[]>(() => toDrafts([]));
  const [updated, setUpdated] = useState<Partial<Record<ShopPolicyType, string>>>({});
  const [openType, setOpenType] = useState<ShopPolicyType | null>(null);
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [reloadKey, setReloadKey] = useState(0);
  const baseline = useRef<PolicyDraft[]>(toDrafts([]));

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    let active = true;
    setState("loading");
    setFieldErrors([]);
    merchantApi
      .getPolicies(shopId)
      .then((rows) => {
        if (!active) return;
        const next = toDrafts(rows);
        baseline.current = next;
        setDrafts(next);
        setUpdated(toUpdatedMap(rows));
        setOpenType(null);
        setState("ok");
      })
      .catch(() => {
        if (active) setState("error");
      });
    return () => {
      active = false;
    };
  }, [shopId, reloadKey]);

  const dirtyTypes = useMemo(
    () => new Set(drafts.filter((row) => !sameDraft(row, baseline.current.find((b) => b.type === row.type))).map((row) => row.type)),
    [drafts],
  );
  const dirty = state === "ok" && dirtyTypes.size > 0;
  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);

  const patch = (type: ShopPolicyType, next: Partial<Pick<PolicyDraft, "title" | "body">>) =>
    setDrafts((current) => current.map((row) => (row.type === type ? { ...row, ...next } : row)));

  const save = async () => {
    if (saving || !canUpdate || !dirty) return;
    const orphanTitle = drafts.find((row) => row.title.trim() && !row.body.trim());
    if (orphanTitle) {
      setOpenType(orphanTitle.type);
      notify(t("storePolicies.bodyRequired", { type: t(`storePolicies.types.${orphanTitle.type}`) }));
      return;
    }
    setSaving(true);
    setFieldErrors([]);
    try {
      const rows = await merchantApi.savePolicies(
        { policies: drafts.map((row) => ({ type: row.type, title: row.title.trim(), body: row.body })) },
        shopId,
      );
      const next = toDrafts(rows);
      baseline.current = next;
      setDrafts(next);
      setUpdated(toUpdatedMap(rows));
      setOpenType(null);
      notify(t("storePolicies.saved"));
    } catch (error) {
      if (error instanceof ApiError) {
        notify(error.message);
        setFieldErrors(error.fieldErrors);
      } else {
        notify(t("storePolicies.saveError"));
      }
    } finally {
      setSaving(false);
    }
  };

  if (state === "loading") {
    return (
      <section className="settings-card policy-card policy-editor" aria-busy="true">
        <header>
          <div>
            <Skeleton width={180} height={22} />
            <Skeleton width={320} height={12} />
          </div>
          <Skeleton width={96} height={40} radius={6} />
        </header>
        {SHOP_POLICY_TYPES.map((type) => (
          <div className="policy-row" key={type} aria-hidden="true">
            <Skeleton width="70%" height={14} />
            <Skeleton width="45%" height={12} />
            <Skeleton width={56} height={14} />
          </div>
        ))}
      </section>
    );
  }

  if (state === "error") {
    return (
      <section className="settings-card policy-card policy-editor">
        <LoadError message={t("storePolicies.loadError")} onRetry={reload} retryLabel={t("storePolicies.retry")} />
      </section>
    );
  }

  const filledCount = baseline.current.filter((row) => row.body.trim()).length;

  return (
    <section className="settings-card policy-card policy-editor">
      <header>
        <div>
          <h2><FileText />{t("storePolicies.title")}</h2>
          <p>{t("storePolicies.lead")}</p>
          <p className="policy-count">{t("storePolicies.count", { filled: filledCount, total: SHOP_POLICY_TYPES.length })}</p>
        </div>
        <MerchantSaveButton
          saving={saving}
          disabled={!canUpdate || !dirty}
          title={deniedTitle ?? (!dirty ? t("storePolicies.nothingToSave") : undefined)}
          label={t("storePolicies.save")}
          savingLabel={t("storePolicies.saving")}
          onClick={() => void save()}
        />
      </header>

      {fieldErrors.length > 0 && (
        <ul className="policy-errors" role="alert">
          {fieldErrors.map((error) => (
            <li key={`${error.field}-${error.code}`}>{error.message}</li>
          ))}
        </ul>
      )}

      {drafts.map((row) => {
        const saved = baseline.current.find((item) => item.type === row.type);
        const filled = Boolean(saved?.body.trim());
        const isDirty = dirtyTypes.has(row.type);
        const isOpen = openType === row.type;
        const label = t(`storePolicies.types.${row.type}`);
        const when = formatUpdated(updated[row.type], i18n.language || "th");
        const panelId = `policy-edit-${row.type}`;
        return (
          <div className={`policy-item${isOpen ? " is-open" : ""}`} key={row.type}>
            <div className="policy-row">
              <b>{label}</b>
              <span className="policy-status">
                <em className={isDirty ? "is-dirty" : filled ? "is-filled" : "is-empty"}>
                  {isDirty ? t("storePolicies.status.unsaved") : filled ? t("storePolicies.status.filled") : t("storePolicies.status.empty")}
                </em>
                {filled && saved?.title ? <span className="policy-title">{saved.title}</span> : null}
                {filled && when ? (
                  <small>
                    {t("storePolicies.updated")} <time className="policy-updated" dateTime={updated[row.type]}>{when}</time>
                  </small>
                ) : null}
              </span>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => setOpenType(isOpen ? null : row.type)}
              >
                {isOpen ? t("storePolicies.close") : canUpdate ? `✎ ${t("storePolicies.edit")}` : t("storePolicies.view")}
              </button>
            </div>
            {isOpen && (
              <div className="policy-edit" id={panelId}>
                <label>
                  {t("storePolicies.titleLabel")}
                  <input
                    value={row.title}
                    maxLength={TITLE_MAX}
                    disabled={!canUpdate}
                    title={deniedTitle}
                    placeholder={label}
                    onChange={(event) => patch(row.type, { title: event.target.value })}
                  />
                  <small>{row.title.length}/{TITLE_MAX}</small>
                </label>
                <label>
                  {t("storePolicies.bodyLabel")}
                  <textarea
                    value={row.body}
                    maxLength={BODY_MAX}
                    rows={12}
                    disabled={!canUpdate}
                    title={deniedTitle}
                    onChange={(event) => patch(row.type, { body: event.target.value })}
                  />
                  <small>{row.body.length.toLocaleString()}/{BODY_MAX.toLocaleString()}</small>
                </label>
                <p className="policy-hint">{t("storePolicies.formatHint")}</p>
                <p className="policy-hint">{t("storePolicies.removeHint")}</p>
                {isDirty && (
                  <button type="button" className="policy-revert" onClick={() => saved && patch(row.type, { title: saved.title, body: saved.body })}>
                    {t("storePolicies.revert")}
                  </button>
                )}
              </div>
            )}
          </div>
        );
      })}
    </section>
  );
}
