import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Box,
  CircleAlert,
  HardDrive,
  History,
  Info,
  Package,
  RefreshCw,
  ShoppingBag,
  Sparkles,
  UsersRound,
  X,
} from "lucide-react";
import { LoadError, SkeletonForm } from "../feedback/Skeleton";
import {
  ApiError,
  merchantPlanUsageApi,
  type MerchantPlanMetric,
  type MerchantPlanMetricStatus,
  type MerchantPlanUsage,
} from "../../services/api";
import { usePermissions } from "../../hooks/usePermissions";
import "../../styles/product-management.css";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";

type Props = {
  shopId: number;
  notify: (text: string) => void;
  onNavigateHref?: (href: string) => void;
};

const METRIC_ICONS = {
  PRODUCTS: Package,
  ORDERS_ROLLING_30D: ShoppingBag,
  STAFF: UsersRound,
  STORAGE: HardDrive,
} as const;

function formatCount(n: number) {
  return n.toLocaleString("en-US");
}

/** Binary units (1024) labeled KiB/MiB/GiB — matches trackedBytes semantics. */
function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KiB", "MiB", "GiB", "TiB"] as const;
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unit]}`;
}

function formatValue(metric: MerchantPlanMetric, n: number) {
  return metric.unit === "BYTES" ? formatBytes(n) : formatCount(n);
}

function barWidth(metric: MerchantPlanMetric) {
  if (metric.limit.kind !== "FINITE" || metric.limit.value == null || metric.limit.value <= 0) return 0;
  if (metric.usedRatio == null) return 0;
  return Math.min(100, Math.round(metric.usedRatio * 100));
}

function statusTone(status: MerchantPlanMetricStatus): "ok" | "warn" | "over" | "muted" | "info" {
  switch (status) {
    case "OK":
    case "UNLIMITED":
      return "ok";
    case "WARN":
    case "FULL":
      return "warn";
    case "OVER":
      return "over";
    case "RESTRICTED":
    case "UNKNOWN":
    case "NOT_APPLICABLE":
    case "ZERO_QUOTA":
      return "muted";
    default:
      return "info";
  }
}

function bangkokStamp(iso: string, locale: string) {
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone: "Asia/Bangkok",
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export default function SettingsPlanPanel({ shopId, notify, onNavigateHref }: Props) {
  const { t, i18n } = useTranslation("merchant");
  const { canInShop } = usePermissions();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<MerchantPlanUsage | null>(null);
  const [stale, setStale] = useState(false);
  const [detail, setDetail] = useState<MerchantPlanMetric | null>(null);
  const reqSeq = useRef(0);
  const hasDataRef = useRef(false);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const load = useCallback(async (mode: "initial" | "refresh" = "initial") => {
    const seq = ++reqSeq.current;
    if (mode === "refresh") setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const row = await merchantPlanUsageApi.get(shopId);
      if (seq !== reqSeq.current) return;
      if (row.shopId !== shopId) {
        setError(t("plan.staleShop"));
        return;
      }
      setData(row);
      hasDataRef.current = true;
      setStale(false);
      if (mode === "refresh") notify(t("plan.refreshed"));
    } catch (e) {
      if (seq !== reqSeq.current) return;
      if (e instanceof ApiError && (e.status === 403 || e.code === "FORBIDDEN" || e.code === "PERMISSION_DENIED")) {
        setError("restricted");
      } else {
        setError(e instanceof Error ? e.message : t("plan.loadError"));
      }
      if (hasDataRef.current) setStale(true);
    } finally {
      if (seq === reqSeq.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [shopId, t, notify]);

  useEffect(() => {
    setData(null);
    hasDataRef.current = false;
    setStale(false);
    setDetail(null);
    void load("initial");
  }, [shopId, load]);

  useEffect(() => {
    if (!detail) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeDetail();
      }
    };
    window.addEventListener("keydown", onKey);
    const timer = window.setTimeout(() => closeBtnRef.current?.focus(), 0);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.clearTimeout(timer);
    };
  }, [detail]);

  const openDetail = (metric: MerchantPlanMetric, from?: HTMLElement | null) => {
    returnFocusRef.current = from ?? (document.activeElement as HTMLElement | null);
    setDetail(metric);
  };

  const closeDetail = () => {
    setDetail(null);
    const el = returnFocusRef.current;
    returnFocusRef.current = null;
    window.setTimeout(() => el?.focus?.(), 0);
  };

  const go = (href: string) => {
    if (onNavigateHref) onNavigateHref(href);
    else window.location.assign(href);
  };

  const localeTag = i18n.language?.startsWith("zh") ? "zh-CN" : i18n.language?.startsWith("en") ? "en-GB" : "th-TH";

  if (loading && !data) {
    return (
      <div className="plan-settings">
        <section className="settings-card plan-card"><SkeletonForm fields={8} /></section>
      </div>
    );
  }

  if (!data && error === "restricted") {
    return (
      <div className="plan-settings">
        <section className="settings-card plan-card">
          <div className="plan-restricted" role="status">
            <CircleAlert aria-hidden />
            <div>
              <b>{t("plan.restrictedTitle")}</b>
              <p>{t("plan.restrictedBody")}</p>
            </div>
          </div>
        </section>
      </div>
    );
  }

  if (!data && error) {
    return (
      <div className="plan-settings">
        <section className="settings-card plan-card">
          <LoadError message={error} onRetry={() => void load("initial")} retryLabel={t("plan.retry")} />
        </section>
      </div>
    );
  }

  if (!data) return null;

  const planTitle = data.plan.resolution === "NONE"
    ? t("plan.noPlan")
    : data.plan.name || data.plan.code || t("plan.unknownPlan");

  const alerts = data.metrics.filter((m) => m.status === "WARN" || m.status === "FULL" || m.status === "OVER");

  const limitLabel = (metric: MerchantPlanMetric) => {
    if (metric.limit.kind === "UNLIMITED") return t("plan.unlimited");
    if (metric.limit.kind === "NOT_APPLICABLE") return t("plan.notApplicable");
    if (metric.limit.kind === "UNKNOWN") return t("plan.unknownLimit");
    if (metric.limit.value == null) return "—";
    return formatValue(metric, metric.limit.value);
  };

  return (
    <div className="plan-settings">
      <section className="settings-card plan-card">
        <header className="plan-section-head">
          <div className="plan-section-title">
            <span className="plan-section-icon" aria-hidden="true"><Sparkles /></span>
            <div>
              <h2>{t("plan.title")}</h2>
              <p>{t("plan.subtitle", { shop: data.shopName })}</p>
            </div>
          </div>
          <div className="plan-head-actions">
            <p className="plan-asof-inline">
              {t("plan.asOf", { time: bangkokStamp(data.asOf, localeTag), tz: data.timezone })}
            </p>
            <button
              type="button"
              className="plan-refresh"
              disabled={refreshing}
              onClick={() => void load("refresh")}
            >
              <RefreshCw aria-hidden className={refreshing ? "is-spin" : undefined} />
              {refreshing ? t("plan.refreshing") : t("plan.refresh")}
            </button>
          </div>
        </header>

        <div className="plan-card-body">
          {stale ? (
            <p className="plan-stale" role="status"><AlertTriangle aria-hidden />{t("plan.staleData")}</p>
          ) : null}
          {error && data ? (
            <p className="plan-inline-error" role="alert">{error === "restricted" ? t("plan.restrictedBody") : error}</p>
          ) : null}

          <div className="plan-identity">
            <div className="plan-identity-mark" aria-hidden="true"><Sparkles /></div>
            <div className="plan-identity-copy">
              <div className="plan-identity-top">
                <span className={`plan-badge is-${data.plan.resolution.toLowerCase()}`}>
                  {t(`plan.resolution.${data.plan.resolution}`)}
                </span>
                {data.plan.code ? <code className="plan-code">{data.plan.code}</code> : null}
              </div>
              <b className="plan-name">{planTitle}</b>
              <p className="plan-desc">
                {data.plan.note || (data.plan.resolution === "NONE" ? t("plan.noPlanHint") : t("plan.catalogHint"))}
              </p>
            </div>
          </div>

          {alerts.length > 0 ? (
            <ul className="plan-alerts" aria-live="polite">
              {alerts.map((m) => (
                <li key={m.code} className={`is-${statusTone(m.status)}`}>
                  <AlertTriangle aria-hidden />
                  <span>
                    {t(`plan.alert.${m.status}`, {
                      name: t(`plan.metrics.${m.code}.name`),
                      used: m.used == null ? "—" : formatValue(m, m.used),
                      limit: m.limit.value == null ? "—" : formatValue(m, m.limit.value),
                      over: m.used != null && m.limit.value != null && m.used > m.limit.value
                        ? formatValue(m, m.used - m.limit.value)
                        : "0",
                    })}
                  </span>
                </li>
              ))}
            </ul>
          ) : null}

          <div className="plan-usage-block">
            <div className="plan-usage-heading">
              <Box aria-hidden />
              <div>
                <h3>{t("plan.usageTitle")}</h3>
                <p>{t("plan.usageSubtitle")}</p>
              </div>
            </div>

            <ul className="plan-metric-list">
              {data.metrics.map((metric) => {
                const Icon = METRIC_ICONS[metric.code as keyof typeof METRIC_ICONS] || Package;
                const tone = statusTone(metric.status);
                const showBar = metric.limit.kind === "FINITE" && metric.limit.value != null && metric.limit.value > 0 && metric.used != null;
                const pctLabel = metric.usedRatio == null ? null : `${Math.round(metric.usedRatio * 100)}%`;
                return (
                  <li key={metric.code} className={`plan-metric-row is-${tone}`}>
                    <div className="plan-metric-main">
                      <span className="plan-metric-icon" aria-hidden="true"><Icon /></span>
                      <div className="plan-metric-copy">
                        <div className="plan-metric-title">
                          <b>{t(`plan.metrics.${metric.code}.name`)}</b>
                          <em className={`plan-status is-${tone}`}>{t(`plan.status.${metric.status}`)}</em>
                        </div>
                        <p>{t(`plan.metrics.${metric.code}.short`)}</p>
                      </div>
                    </div>

                    {metric.availability === "RESTRICTED" ? (
                      <p className="plan-metric-restricted">{t("plan.metricRestricted", { permission: metric.permissionRequired })}</p>
                    ) : (
                      <div className="plan-metric-stats">
                        <div className="plan-metric-nums">
                          <span>
                            <small>{t("plan.used")}</small>
                            <strong>{metric.used == null ? "—" : formatValue(metric, metric.used)}</strong>
                          </span>
                          <span>
                            <small>{t("plan.limit")}</small>
                            <strong>{limitLabel(metric)}</strong>
                          </span>
                          <span>
                            <small>{t("plan.remaining")}</small>
                            <strong>
                              {metric.limit.kind === "UNLIMITED"
                                ? t("plan.unlimited")
                                : metric.remaining == null
                                  ? "—"
                                  : formatValue(metric, metric.remaining)}
                            </strong>
                          </span>
                          {pctLabel && showBar ? (
                            <span className="plan-metric-pct" aria-hidden="true">{pctLabel}</span>
                          ) : null}
                        </div>
                        {showBar ? (
                          <div
                            className={`plan-quota-bar is-${tone}`}
                            role="meter"
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-valuenow={barWidth(metric)}
                            aria-label={t(`plan.metrics.${metric.code}.name`)}
                          >
                            <i style={{ width: `${barWidth(metric)}%` }} />
                          </div>
                        ) : null}
                        {metric.period.kind === "ROLLING_DAYS" && metric.period.start && metric.period.end ? (
                          <p className="plan-period">
                            {t("plan.rollingPeriod", {
                              days: metric.period.rollingDays ?? 30,
                              from: bangkokStamp(metric.period.start, localeTag),
                              to: bangkokStamp(metric.period.end, localeTag),
                            })}
                          </p>
                        ) : null}
                        {metric.completeness === "PARTIAL" ? (
                          <p className="plan-partial">{t("plan.partialStorage")}</p>
                        ) : null}
                      </div>
                    )}

                    <div className="plan-metric-actions">
                      <button type="button" onClick={(e) => openDetail(metric, e.currentTarget)}>
                        {t("plan.howCounted")}
                      </button>
                      {metric.code === "PRODUCTS" && canInShop("PRODUCT_READ") ? (
                        <button type="button" className="ghost" onClick={() => go("/merchant/products")}>{t("plan.links.products")}</button>
                      ) : null}
                      {metric.code === "STAFF" && canInShop("USER_READ") ? (
                        <button type="button" className="ghost" onClick={() => go("/merchant/settings?tab=team")}>{t("plan.links.team")}</button>
                      ) : null}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>

          <details className="plan-enforce-details">
            <summary>
              <Info aria-hidden />
              <span>
                <b>{t("plan.enforcementTitle")}</b>
                <small>{t("plan.enforcementSubtitle")}</small>
              </span>
            </summary>
            <ul className="plan-enforcement-list">
              {data.enforcement.map((row) => (
                <li key={row.metricCode}>
                  <span className={`plan-enforce-pill is-${row.level === "ENFORCED" ? "warn" : "muted"}`}>
                    {t(`plan.enforcementLevel.${row.level}`, { defaultValue: row.level })}
                  </span>
                  <div>
                    <b>{t(`plan.metrics.${row.metricCode}.name`, { defaultValue: row.metricCode })}</b>
                    <p>{row.detail}</p>
                  </div>
                </li>
              ))}
            </ul>
            <p className="plan-policy-note"><Info aria-hidden />{data.displayPolicy.note}</p>
          </details>
        </div>
      </section>

      {detail ? createPortal(
        <div
          className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
          role="presentation"
          onMouseDown={(e) => { if (e.target === e.currentTarget) closeDetail(); }}
        >
          <aside
            className="om-drawer cm-detail sh-detail om-detail plan-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="plan-drawer-title"
          >
            <header className="cm-detail-head">
              <div className="cm-avatar om-avatar tone-waiting" aria-hidden="true"><History /></div>
              <div className="cm-detail-id">
                <h2 id="plan-drawer-title">{t(`plan.metrics.${detail.code}.name`)}</h2>
                <div className="cm-detail-meta">
                  <span className={`om-pill ${detail.status === "OVER" ? "cancelled" : detail.status === "WARN" || detail.status === "FULL" ? "waiting" : "delivered"}`}>
                    {t(`plan.status.${detail.status}`)}
                  </span>
                </div>
              </div>
              <button type="button" className="om-drawer-close" aria-label={t("plan.close")} ref={closeBtnRef} onClick={closeDetail}>
                <X aria-hidden />
              </button>
            </header>
            <div className="om-drawer-body cm-detail-body plan-drawer-body">
              <section className="om-section plan-block">
                <h3>{t("plan.drawer.source")}</h3>
                <p>{detail.definition.source}</p>
              </section>
              <section className="om-section plan-block">
                <h3>{t("plan.drawer.includes")}</h3>
                <p>{detail.definition.includes}</p>
              </section>
              <section className="om-section plan-block">
                <h3>{t("plan.drawer.excludes")}</h3>
                <p>{detail.definition.excludes}</p>
              </section>
              <section className="om-section plan-block">
                <h3>{t("plan.drawer.note")}</h3>
                <p>{detail.definition.note}</p>
              </section>
              {detail.period.kind === "ROLLING_DAYS" ? (
                <section className="om-section plan-block">
                  <h3>{t("plan.drawer.period")}</h3>
                  <p>
                    {t("plan.rollingPeriod", {
                      days: detail.period.rollingDays ?? 30,
                      from: detail.period.start ? bangkokStamp(detail.period.start, localeTag) : "—",
                      to: detail.period.end ? bangkokStamp(detail.period.end, localeTag) : "—",
                    })}
                  </p>
                </section>
              ) : null}
              {Object.keys(detail.breakdown).length > 0 ? (
                <section className="om-section plan-block">
                  <h3>{t("plan.drawer.breakdown")}</h3>
                  <ul className="plan-breakdown">
                    {Object.entries(detail.breakdown).map(([key, value]) => (
                      <li key={key}>
                        <span>{t(`plan.breakdown.${key}`, { defaultValue: key })}</span>
                        <strong>{detail.unit === "BYTES" && key.toLowerCase().includes("bytes") ? formatBytes(value) : formatCount(value)}</strong>
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}
              <section className="om-section plan-block">
                <h3>{t("plan.drawer.completeness")}</h3>
                <p>{t(`plan.completeness.${detail.completeness}`)}</p>
              </section>
            </div>
            <footer className="om-drawer-actions">
              <button type="button" onClick={closeDetail}>{t("plan.close")}</button>
            </footer>
          </aside>
        </div>,
        document.body,
      ) : null}
    </div>
  );
}
