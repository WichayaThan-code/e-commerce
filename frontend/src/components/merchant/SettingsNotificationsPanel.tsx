import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type MouseEvent } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Bell, RefreshCw, RotateCcw, Search, ShieldCheck, X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import ToggleSwitch from "../form/ToggleSwitch";
import { LoadError, Skeleton, SkeletonTable } from "../feedback/Skeleton";
import MerchantSaveButton from "./MerchantSaveButton";
import {
  ApiError,
  MERCHANT_NOTIFICATION_PREFS_EVENT,
  merchantNotificationPreferencesApi,
  type NotificationCategoryInfo,
  type NotificationPreferences,
} from "../../services/api";
import "../../styles/product-management.css";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";
import "../../styles/form-fields.css";

type Props = {
  shopId: number;
  notify: (text: string) => void;
  onDirtyChange?: (dirty: boolean) => void;
};

type DraftMap = Record<string, boolean>;

function toDraft(categories: NotificationCategoryInfo[]): DraftMap {
  const next: DraftMap = {};
  for (const c of categories) next[c.code] = c.inAppEnabled;
  return next;
}

function draftsEqual(a: DraftMap, b: DraftMap) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (Boolean(a[k]) !== Boolean(b[k])) return false;
  return true;
}

const EXAMPLE = {
  orderNo: "ORD-123",
  amount: "1,200",
  customerName: "คุณเอ",
  productName: "สินค้าตัวอย่าง",
  skuCode: "SKU-1",
  available: 2,
  rating: 5,
  shipmentStatus: "DELIVERY_FAILED",
  planCode: "PRO",
  role: "STAFF",
} as const;

export default function SettingsNotificationsPanel({ shopId, notify, onDirtyChange }: Props) {
  const { t } = useTranslation("merchant");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [saved, setSaved] = useState<NotificationPreferences | null>(null);
  const [draft, setDraft] = useState<DraftMap>({});
  const [baseline, setBaseline] = useState<DraftMap>({});
  const [version, setVersion] = useState(1);
  const [q, setQ] = useState("");
  const [saving, setSaving] = useState(false);
  const [detailCode, setDetailCode] = useState<string | null>(null);
  const [confirmRestore, setConfirmRestore] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const reqSeq = useRef(0);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const dirty = useMemo(() => !draftsEqual(draft, baseline), [draft, baseline]);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);

  const load = useCallback(async () => {
    if (!shopId) return;
    const seq = ++reqSeq.current;
    setLoading(true);
    setError(null);
    setFieldError(null);
    try {
      const row = await merchantNotificationPreferencesApi.get(shopId);
      if (seq !== reqSeq.current) return;
      if (row.shopId !== shopId) return;
      setSaved(row);
      const map = toDraft(row.categories);
      setDraft(map);
      setBaseline(map);
      setVersion(row.version);
    } catch (e) {
      if (seq !== reqSeq.current) return;
      setSaved(null);
      setError(e instanceof Error ? e.message : t("notificationPrefs.loadError"));
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  }, [shopId, t, reload]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    setDetailCode(null);
    setQ("");
    setFieldError(null);
  }, [shopId]);

  const categories = saved?.categories ?? [];
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return categories;
    return categories.filter((c) => {
      const name = t(`notificationPrefs.categories.${c.code}.name`, { defaultValue: c.code }).toLowerCase();
      const desc = t(`notificationPrefs.categories.${c.code}.desc`, { defaultValue: "" }).toLowerCase();
      return name.includes(needle) || desc.includes(needle) || c.code.toLowerCase().includes(needle);
    });
  }, [categories, q, t]);

  const detail = categories.find((c) => c.code === detailCode) ?? null;

  const closeDrawer = useCallback(() => {
    setDetailCode(null);
    const el = returnFocusRef.current;
    returnFocusRef.current = null;
    window.setTimeout(() => el?.focus?.(), 0);
  }, []);

  useEffect(() => {
    if (!detailCode) return;
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeDrawer();
      }
    };
    window.addEventListener("keydown", onKey);
    const timer = window.setTimeout(() => closeBtnRef.current?.focus(), 0);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.clearTimeout(timer);
    };
  }, [detailCode, closeDrawer]);

  const openDrawer = (code: string, from?: HTMLElement | null) => {
    returnFocusRef.current = from ?? (document.activeElement as HTMLElement | null);
    setDetailCode(code);
  };

  const setCategory = (code: string, enabled: boolean) => {
    const cat = categories.find((c) => c.code === code);
    if (!cat || cat.mandatory || !cat.editable) return;
    setDraft((prev) => ({ ...prev, [code]: enabled }));
    setFieldError(null);
  };

  const discard = () => {
    setDraft({ ...baseline });
    setFieldError(null);
  };

  const restoreDefaults = () => {
    const next: DraftMap = {};
    for (const c of categories) next[c.code] = c.mandatory ? true : c.defaultInApp;
    setDraft(next);
    setConfirmRestore(false);
    setFieldError(null);
    notify(t("notificationPrefs.restoredDraft"));
  };

  const save = async () => {
    if (!saved || saving || !dirty) return;
    setSaving(true);
    setFieldError(null);
    const expectedShop = shopId;
    try {
      const row = await merchantNotificationPreferencesApi.save(expectedShop, {
        version,
        categories: categories
          .filter((c) => c.editable)
          .map((c) => ({ code: c.code, inAppEnabled: Boolean(draft[c.code]) })),
      });
      if (row.shopId !== expectedShop) {
        setFieldError(t("notificationPrefs.staleShop"));
        return;
      }
      setSaved(row);
      const map = toDraft(row.categories);
      setDraft(map);
      setBaseline(map);
      setVersion(row.version);
      window.dispatchEvent(new CustomEvent(MERCHANT_NOTIFICATION_PREFS_EVENT, { detail: { shopId: expectedShop } }));
      notify(t("notificationPrefs.saved"));
    } catch (e) {
      if (e instanceof ApiError && e.code === "VERSION_CONFLICT") setFieldError(t("notificationPrefs.versionConflict"));
      else if (e instanceof ApiError && e.code === "MANDATORY_CATEGORY") setFieldError(e.message || t("notificationPrefs.mandatoryError"));
      else setFieldError(e instanceof Error ? e.message : t("notificationPrefs.saveError"));
    } finally {
      setSaving(false);
    }
  };

  const eventLabel = (type: string) =>
    t(`notifications.types.${type}`, { ...EXAMPLE, defaultValue: type });

  const onRowKey = (event: KeyboardEvent<HTMLTableRowElement>, code: string) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openDrawer(code, event.currentTarget);
    }
  };

  const onRowClick = (event: MouseEvent<HTMLTableRowElement>, code: string) => {
    const target = event.target as HTMLElement | null;
    if (target?.closest("button, input, label, a, [data-stop-row]")) return;
    openDrawer(code, event.currentTarget);
  };

  if (!loading && error && !saved) {
    return (
      <section className="product-form-card notif-panel">
        <header className="notif-panel-head">
          <div>
            <h3><Bell aria-hidden />{t("notificationPrefs.title")}</h3>
          </div>
        </header>
        <LoadError message={error} onRetry={() => setReload((n) => n + 1)} retryLabel={t("notificationPrefs.retry")} />
      </section>
    );
  }

  return (
    <div className="notif-settings">
      <section className="product-form-card notif-panel">
        <header className="notif-panel-head">
          <div>
            <h3><Bell aria-hidden />{t("notificationPrefs.title")}</h3>
            <p className="pm-lang-hint">
              {t("notificationPrefs.subtitle", { shop: saved?.shopName || "—" })}
            </p>
            <p className="pm-lang-hint">{t("notificationPrefs.personalNote")}</p>
            <p className="pm-lang-hint">{t("notificationPrefs.hideEffect")}</p>
          </div>
          <div className="pm-actions">
            {dirty ? <span className="notif-dirty" role="status">{t("notificationPrefs.unsaved")}</span> : null}
            <button type="button" disabled={!dirty || saving} onClick={discard}>
              {t("notificationPrefs.discard")}
            </button>
            <button
              type="button"
              disabled={saving || !saved}
              onClick={() => setConfirmRestore(true)}
            >
              <RotateCcw aria-hidden />{t("notificationPrefs.restore")}
            </button>
            <MerchantSaveButton
              saving={saving}
              disabled={!dirty}
              onClick={() => { void save(); }}
              label={t("notificationPrefs.save")}
              savingLabel={t("notificationPrefs.saving")}
            />
          </div>
        </header>
        {fieldError ? <p className="notif-field-error" role="alert">{fieldError}</p> : null}
        <p className="pm-lang-hint notif-channel-note">{t("notificationPrefs.channelsNote")}</p>
      </section>

      <section className="pm-table-card notif-table-card">
        <form
          className="pm-filters notif-filters"
          onSubmit={(e) => { e.preventDefault(); }}
        >
          <label className="pm-search">
            <Search aria-hidden />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t("notificationPrefs.search")}
              aria-label={t("notificationPrefs.search")}
            />
          </label>
          <div className="notif-filter-actions">
            <button
              type="button"
              disabled={loading || saving}
              aria-label={t("notificationPrefs.retry")}
              title={t("notificationPrefs.retry")}
              onClick={() => setReload((n) => n + 1)}
            >
              <RefreshCw aria-hidden />{t("notificationPrefs.retry")}
            </button>
          </div>
        </form>

        {loading && !saved ? <SkeletonTable rows={6} cols={5} /> : null}

        {!loading || saved ? (
          <div className="pm-table-wrap notif-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{t("notificationPrefs.col.category")}</th>
                  <th>{t("notificationPrefs.col.example")}</th>
                  <th>{t("notificationPrefs.col.inApp")}</th>
                  <th>{t("notificationPrefs.col.policy")}</th>
                  <th>{t("notificationPrefs.col.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="notif-empty">{t("notificationPrefs.emptyFilter")}</td>
                  </tr>
                ) : filtered.map((c) => {
                  const enabled = c.mandatory ? true : Boolean(draft[c.code]);
                  const name = t(`notificationPrefs.categories.${c.code}.name`, { defaultValue: c.code });
                  const desc = t(`notificationPrefs.categories.${c.code}.desc`, { defaultValue: "" });
                  const exampleType = c.eventTypes[0];
                  return (
                    <tr
                      key={c.code}
                      tabIndex={0}
                      className={`notif-row${detailCode === c.code ? " is-open" : ""}`}
                      onClick={(e) => onRowClick(e, c.code)}
                      onKeyDown={(e) => onRowKey(e, c.code)}
                      aria-label={name}
                    >
                      <td>
                        <div className="pm-product">
                          <p>
                            <b title={name}>{name}</b>
                            <small title={desc}>{desc}</small>
                          </p>
                        </div>
                        {!c.viewerHasPermission ? (
                          <span className="pm-status empty">{t("notificationPrefs.noPermission")}</span>
                        ) : null}
                      </td>
                      <td>
                        <span className="notif-example">
                          {exampleType ? eventLabel(exampleType) : "—"}
                        </span>
                      </td>
                      <td data-stop-row onClick={(e) => e.stopPropagation()}>
                        {loading && !saved ? (
                          <Skeleton height={28} width={72} />
                        ) : (
                          <ToggleSwitch
                            checked={enabled}
                            disabled={c.mandatory || !c.editable || saving || !c.viewerHasPermission}
                            label={name}
                            onText={t("notificationPrefs.on")}
                            offText={t("notificationPrefs.off")}
                            onChange={(next) => setCategory(c.code, next)}
                          />
                        )}
                      </td>
                      <td>
                        {c.mandatory ? (
                          <span className="pm-status low">{t("notificationPrefs.mandatory")}</span>
                        ) : (
                          <span className="pm-status ready">{t("notificationPrefs.optional")}</span>
                        )}
                      </td>
                      <td data-stop-row onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          className="notif-detail-btn"
                          onClick={(e) => openDrawer(c.code, e.currentTarget)}
                        >
                          {t("notificationPrefs.details")}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {detail ? createPortal(
        <div
          className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
          role="presentation"
          onMouseDown={(e) => { if (e.target === e.currentTarget) closeDrawer(); }}
        >
          <aside
            className="om-drawer cm-detail sh-detail om-detail notif-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notif-drawer-title"
          >
            <header className="cm-detail-head">
              <div className="cm-avatar om-avatar tone-moving" aria-hidden>
                <Bell />
              </div>
              <div className="cm-detail-id">
                <h2 id="notif-drawer-title">
                  {t(`notificationPrefs.categories.${detail.code}.name`, { defaultValue: detail.code })}
                </h2>
                <p className="notif-drawer-sub">
                  {t(`notificationPrefs.categories.${detail.code}.desc`, { defaultValue: "" })}
                </p>
                <div className="cm-detail-meta">
                  {detail.mandatory ? (
                    <span className="om-pill waiting">{t("notificationPrefs.mandatory")}</span>
                  ) : (
                    <span className="om-pill delivered">{t("notificationPrefs.optional")}</span>
                  )}
                </div>
              </div>
              <button
                ref={closeBtnRef}
                type="button"
                className="om-drawer-close"
                aria-label={t("notificationPrefs.close")}
                onClick={closeDrawer}
              >
                <X aria-hidden />
              </button>
            </header>

            <div className="om-drawer-body cm-detail-body notif-drawer-body">
              <section className="om-section notif-block">
                <h3>{t("notificationPrefs.drawer.inApp")}</h3>
                <div className="notif-toggle-row">
                  <span className="notif-toggle-copy">
                    {detail.mandatory
                      ? t("notificationPrefs.mandatory")
                      : t("notificationPrefs.optional")}
                  </span>
                  <ToggleSwitch
                    checked={detail.mandatory ? true : Boolean(draft[detail.code])}
                    disabled={detail.mandatory || !detail.editable || saving || !detail.viewerHasPermission}
                    label={t(`notificationPrefs.categories.${detail.code}.name`, { defaultValue: detail.code })}
                    onText={t("notificationPrefs.on")}
                    offText={t("notificationPrefs.off")}
                    onChange={(next) => setCategory(detail.code, next)}
                  />
                </div>
                <p className="notif-hint">{t("notificationPrefs.hideEffect")}</p>
              </section>

              <section className="om-section notif-block">
                <h3>{t("notificationPrefs.drawer.events")}</h3>
                <ul className="notif-event-list">
                  {detail.eventTypes.map((type) => (
                    <li key={type}>
                      <span className="notif-event-label">{eventLabel(type)}</span>
                      <code className="notif-event-code">{type}</code>
                    </li>
                  ))}
                </ul>
              </section>

              <section className="om-section notif-block">
                <h3><ShieldCheck aria-hidden />{t("notificationPrefs.drawer.permission")}</h3>
                <div className="notif-perm-card">
                  <code>{detail.viewerPermission}</code>
                  <span className={`pm-status ${detail.viewerHasPermission ? "ready" : "empty"}`}>
                    {detail.viewerHasPermission
                      ? t("notificationPrefs.drawer.permissionYes")
                      : t("notificationPrefs.drawer.permissionNo")}
                  </span>
                </div>
              </section>

              <section className="om-section notif-block">
                <h3>{t("notificationPrefs.drawer.policy")}</h3>
                <p className="notif-hint">
                  {detail.mandatory
                    ? t(`notificationPrefs.categories.${detail.code}.mandatoryReason`, {
                        defaultValue: t("notificationPrefs.mandatoryReasonFallback"),
                      })
                    : t("notificationPrefs.optionalReason")}
                </p>
              </section>
            </div>

            <footer className="om-drawer-actions">
              {dirty ? <span className="notif-dirty" role="status">{t("notificationPrefs.unsaved")}</span> : null}
              <button type="button" onClick={closeDrawer}>{t("notificationPrefs.close")}</button>
              <MerchantSaveButton
                saving={saving}
                disabled={!dirty}
                onClick={() => { void save(); }}
                label={t("notificationPrefs.save")}
                savingLabel={t("notificationPrefs.saving")}
              />
            </footer>
          </aside>
        </div>,
        document.body,
      ) : null}

      {confirmRestore ? (
        <ConfirmDialog
          title={t("notificationPrefs.restoreConfirmTitle")}
          message={t("notificationPrefs.restoreConfirmMessage")}
          confirmLabel={t("notificationPrefs.restoreConfirm")}
          cancelLabel={t("notificationPrefs.cancel")}
          onConfirm={restoreDefaults}
          onCancel={() => setConfirmRestore(false)}
        />
      ) : null}
    </div>
  );
}
