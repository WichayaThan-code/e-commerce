import { useCallback, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  CheckCircle2,
  Clock3,
  Copy,
  ExternalLink,
  MapPin,
  Package,
  RotateCcw,
  ShoppingBag,
  Truck,
  X,
  XCircle,
} from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import SearchableSelect from "../SearchableSelect";
import { LoadError, Skeleton } from "../feedback/Skeleton";
import { usePermissions } from "../../hooks/usePermissions";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  merchantShipmentApi,
  type ShipmentActionResponse,
  type ShipmentDetail,
  type ShipmentUpdateBody,
  type ShopCarrierView,
} from "../../services/api";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";
import "../../styles/shipping.css";
import { formatBangkokDateTime, isStaleOrderError, orderErrorMessage } from "./orderFormat";

type Props = {
  shopId: number;
  orderId: string;
  carriers: ShopCarrierView[];
  onClose: () => void;
  onChanged: () => void;
  notify: (text: string, tone?: "ok" | "fail") => void;
};

type StatusAction = "IN_TRANSIT" | "OUT_FOR_DELIVERY" | "DELIVERED" | "DELIVERY_FAILED" | "RETURNED";

const STATUS_ACTIONS: StatusAction[] = ["IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED", "RETURNED"];
const FLOW_RANK: Record<string, number> = {
  READY_TO_PACK: 0,
  PACKING: 1,
  PACKED: 2,
  DISPATCHED: 3,
  IN_TRANSIT: 4,
  OUT_FOR_DELIVERY: 5,
  DELIVERED: 6,
  DELIVERY_FAILED: 5,
  RETURNED: 7,
  CANCELLED: 99,
};
const EXCEPTION_ACTIONS = new Set<StatusAction>(["DELIVERY_FAILED", "RETURNED"]);
const NEEDS_CONFIRM = new Set<StatusAction>(["DELIVERY_FAILED", "RETURNED"]);
const TRACKING_LOCKED = new Set(["DELIVERED", "RETURNED", "CANCELLED"]);
/** รอจัดส่ง — ship via POST …/shipment (order → DISPATCHED), then in-flight status buttons appear. */
const AWAITING_DISPATCH = new Set(["READY_TO_PACK", "PACKING", "PACKED"]);

function actionIcon(status: StatusAction) {
  switch (status) {
    case "IN_TRANSIT": return Truck;
    case "OUT_FOR_DELIVERY": return Package;
    case "DELIVERED": return CheckCircle2;
    case "DELIVERY_FAILED": return XCircle;
    case "RETURNED": return RotateCcw;
  }
}

function actionTone(status: StatusAction, isNext: boolean) {
  if (status === "DELIVERED") return isNext ? "tone-success" : "tone-success-outline";
  if (status === "DELIVERY_FAILED") return "tone-danger";
  if (status === "RETURNED") return "tone-warn";
  return isNext ? "tone-primary" : "tone-muted";
}

export default function ShipmentDrawer({ shopId, orderId, carriers, onClose, onChanged, notify }: Props) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { canInShop, deniedTitle } = usePermissions();
  const canUpdate = canInShop("SHIPMENT_UPDATE");
  const canDispatch = canInShop("SHIPMENT_DISPATCH");
  const [shipment, setShipment] = useState<ShipmentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const [location, setLocation] = useState("");
  const [confirm, setConfirm] = useState<StatusAction | null>(null);
  const [reason, setReason] = useState("");
  const [returned, setReturned] = useState(false);
  const [formError, setFormError] = useState("");
  const [carrierCode, setCarrierCode] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [workTab, setWorkTab] = useState<"update" | "history">("update");

  const load = useCallback(async () => {
    setError(null);
    try {
      const detail = await merchantShipmentApi.get(shopId, orderId);
      setShipment(detail);
      setCarrierCode(detail.carrierCode ?? "");
      setTrackingNumber(detail.trackingNumber ?? "");
    } catch (caught) {
      setError(orderErrorMessage(t, caught));
    } finally {
      setLoading(false);
    }
  }, [shopId, orderId, t]);

  useEffect(() => {
    setLoading(true);
    setShipment(null);
    void load();
  }, [load]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || document.querySelector(".pm-dialog")) return;
      onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  /* Match /merchant/customers — lock body scroll while the portaled drawer is open. */
  useEffect(() => {
    const { overflow, paddingRight } = document.body.style;
    const scrollbar = window.innerWidth - document.documentElement.clientWidth;
    document.body.style.overflow = "hidden";
    if (scrollbar > 0) document.body.style.paddingRight = `${scrollbar}px`;
    return () => {
      document.body.style.overflow = overflow;
      document.body.style.paddingRight = paddingRight;
    };
  }, []);

  const carrierLabel = useCallback((code?: string | null) => {
    if (!code) return "";
    const hit = carriers.find((c) => c.code === code);
    if (!hit) return code;
    return (localeTag.startsWith("en") ? hit.nameEn : hit.nameTh) || hit.code;
  }, [carriers, localeTag]);

  const allowed = useMemo(
    () => STATUS_ACTIONS.filter((s) => shipment?.allowedStatuses?.includes(s)),
    [shipment],
  );
  /** Hide statuses already passed; keep exception paths (failed / returned). */
  const visibleActions = useMemo(() => {
    const rank = FLOW_RANK[shipment?.status ?? ""] ?? 0;
    return allowed.filter((status) => {
      if (EXCEPTION_ACTIONS.has(status)) return true;
      return (FLOW_RANK[status] ?? 0) > rank;
    });
  }, [allowed, shipment?.status]);
  const forwardActions = useMemo(
    () => visibleActions.filter((status) => !EXCEPTION_ACTIONS.has(status)),
    [visibleActions],
  );
  const exceptionActions = useMemo(
    () => visibleActions.filter((status) => EXCEPTION_ACTIONS.has(status)),
    [visibleActions],
  );

  const apply = async (call: () => Promise<ShipmentActionResponse>, doneKey = "shippingPage.drawer.toast.done") => {
    setBusy(true);
    setFormError("");
    try {
      const result = await call();
      setShipment(result.shipment);
      setCarrierCode(result.shipment.carrierCode ?? "");
      setTrackingNumber(result.shipment.trackingNumber ?? "");
      setConfirm(null);
      setNote("");
      setLocation("");
      setReason("");
      setReturned(false);
      onChanged();
      notify(t(result.changed ? doneKey : "orderManagement.drawer.toast.unchanged"));
    } catch (caught) {
      const message = orderErrorMessage(t, caught);
      if (isStaleOrderError(caught)) {
        setConfirm(null);
        notify(message, "fail");
        onChanged();
        void load();
      } else if (confirm) {
        setFormError(message);
      } else {
        notify(message, "fail");
      }
    } finally {
      setBusy(false);
    }
  };

  const body = (extra?: ShipmentUpdateBody): ShipmentUpdateBody => ({
    note: note.trim() || undefined,
    location: location.trim() || undefined,
    expectedVersion: shipment?.version,
    ...extra,
  });

  const runStatus = (status: StatusAction, extra?: ShipmentUpdateBody) => {
    if (!shipment) return;
    const payload = body(extra);
    const call = (): Promise<ShipmentActionResponse> => {
      switch (status) {
        case "IN_TRANSIT": return merchantShipmentApi.inTransit(shopId, orderId, payload);
        case "OUT_FOR_DELIVERY": return merchantShipmentApi.outForDelivery(shopId, orderId, payload);
        case "DELIVERED": return merchantShipmentApi.delivered(shopId, orderId, payload);
        case "DELIVERY_FAILED": return merchantShipmentApi.failed(shopId, orderId, payload);
        default: return merchantShipmentApi.returned(shopId, orderId, payload);
      }
    };
    void apply(call);
  };

  const onStatusClick = (status: StatusAction) => {
    if (NEEDS_CONFIRM.has(status)) {
      setReason("");
      setReturned(false);
      setFormError("");
      setConfirm(status);
      return;
    }
    runStatus(status);
  };

  const confirmDialog = () => {
    if (!confirm || !reason.trim()) return;
    runStatus(confirm, { reason: reason.trim(), returned: confirm === "DELIVERY_FAILED" && returned ? true : undefined });
  };

  const addEntry = () => {
    if (!note.trim() && !location.trim()) return;
    void apply(() => merchantShipmentApi.event(shopId, orderId, body()), "shippingPage.drawer.toast.noteAdded");
  };

  const trackingChanged = !!shipment && ((carrierCode || "") !== (shipment.carrierCode ?? "") || (trackingNumber.trim() || "") !== (shipment.trackingNumber ?? ""));
  const awaitingDispatch = !!shipment && AWAITING_DISPATCH.has(shipment.status);
  const shipReady = !!(carrierCode && trackingNumber.trim());
  const saveTracking = () => {
    if (!shipment || !trackingChanged || !trackingNumber.trim()) return;
    void apply(
      () => merchantShipmentApi.updateTracking(shopId, orderId, {
        carrierCode: carrierCode || undefined,
        trackingNumber: trackingNumber.trim(),
        expectedVersion: shipment.version,
      }),
      "shippingPage.drawer.toast.trackingSaved",
    );
  };

  const runShip = () => {
    if (!shipment || !shipReady) return;
    void apply(
      () => merchantShipmentApi.create(shopId, orderId, {
        carrierCode: carrierCode || undefined,
        trackingNumber: trackingNumber.trim(),
        note: note.trim() || undefined,
        expectedOrderVersion: shipment.orderVersion,
      }),
      "shippingPage.drawer.toast.shipped",
    );
  };

  const copyTracking = () => {
    if (!shipment?.trackingNumber || !navigator.clipboard) return;
    void navigator.clipboard.writeText(shipment.trackingNumber).then(
      () => notify(t("shippingPage.drawer.copied")),
      () => undefined,
    );
  };

  const selectable = carriers.filter((c) => c.active && c.enabled);
  const options = carrierCode && !selectable.some((c) => c.code === carrierCode)
    ? [...selectable, ...carriers.filter((c) => c.code === carrierCode)]
    : selectable;
  const label = (key: string, value: string) => t(key, { defaultValue: value });
  const title = shipment?.trackingNumber || shipment?.orderNumber || t("shippingPage.drawer.title");
  const carrierDisplay = shipment?.carrierName || carrierLabel(shipment?.carrierCode) || t("shippingPage.carriers.unassigned");

  const drawer = (
    <div
      className="om-drawer-overlay cm-drawer-overlay sh-drawer-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !confirm) onClose();
      }}
    >
      <aside
        className="om-drawer cm-detail sh-detail"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        {loading && !shipment ? (
          <div className="om-drawer-body cm-detail-body"><Skeleton height={220} /></div>
        ) : error && !shipment ? (
          <div className="om-drawer-body cm-detail-body">
            <LoadError message={error} onRetry={() => { setLoading(true); void load(); }} retryLabel={t("shopSettings.retry")} />
          </div>
        ) : shipment ? (
          <>
            <header className="cm-detail-head">
              <div className={`cm-avatar sh-avatar tone-${statusClass(shipment.status)}`} aria-hidden="true">
                <Package />
              </div>
              <div className="cm-detail-id">
                <h2 className="num" title={title}>{title}</h2>
                <div className="cm-detail-meta">
                  <span className={`shipping-status ${statusClass(shipment.status)}`}>
                    {label(`shippingPage.status.${shipment.status}`, shipment.status)}
                  </span>
                  <small className="num">{shipment.orderNumber}</small>
                </div>
              </div>
              <button
                type="button"
                className="om-drawer-close"
                aria-label={t("orderManagement.drawer.close")}
                onClick={onClose}
              >
                <X />
              </button>
            </header>

            <div className="om-drawer-body cm-detail-body">
              <dl className="cm-contact">
                <div className="cm-contact-row">
                  <span className="cm-icon-chip" aria-hidden="true"><ShoppingBag /></span>
                  <dt>{t("shippingPage.drawer.order")}</dt>
                  <dd>
                    <a className="num" href="/merchant/orders">{shipment.orderNumber}</a>
                  </dd>
                </div>
                <div className="cm-contact-row">
                  <span className="cm-icon-chip" aria-hidden="true"><Package /></span>
                  <dt>{t("shippingPage.drawer.orderStatus")}</dt>
                  <dd>{label(`orderManagement.status.${shipment.orderStatus}`, shipment.orderStatus)}</dd>
                </div>
                <div className="cm-contact-row">
                  <span className="cm-icon-chip" aria-hidden="true"><Truck /></span>
                  <dt>{t("shippingPage.drawer.carrier")}</dt>
                  <dd>{carrierDisplay}</dd>
                </div>
                <div className="cm-contact-row">
                  <span className="cm-icon-chip" aria-hidden="true"><MapPin /></span>
                  <dt>{t("shippingPage.drawer.tracking")}</dt>
                  <dd>
                    {shipment.trackingNumber ? (
                      <span className="num">{shipment.trackingNumber}</span>
                    ) : (
                      <span className="cm-missing">{t("shippingPage.carriers.unassigned")}</span>
                    )}
                    {shipment.trackingNumber && typeof navigator !== "undefined" && navigator.clipboard ? (
                      <button
                        type="button"
                        className="cm-copy"
                        onClick={copyTracking}
                        aria-label={t("shippingPage.drawer.copyTracking")}
                        title={t("shippingPage.drawer.copyTracking")}
                      >
                        <Copy aria-hidden="true" />
                      </button>
                    ) : null}
                    {shipment.trackingUrl ? (
                      <a
                        className="cm-copy"
                        href={shipment.trackingUrl}
                        target="_blank"
                        rel="noreferrer"
                        aria-label={t("shippingPage.drawer.openTracking")}
                        title={t("shippingPage.drawer.openTracking")}
                      >
                        <ExternalLink aria-hidden="true" />
                      </a>
                    ) : null}
                  </dd>
                </div>
                {shipment.paymentMethod ? (
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><Clock3 /></span>
                    <dt>{t("shippingPage.drawer.paymentMethod")}</dt>
                    <dd>{shipment.paymentMethod === "COD" ? "COD" : t("shippingPage.drawer.prepaid")}</dd>
                  </div>
                ) : null}
              </dl>

              <div className="cm-customer-kpis sh-detail-kpis">
                <p className="tone-warning">
                  <span className="cm-icon-chip" aria-hidden="true"><Package /></span>
                  <b className="num" title={shipment.packedAt ? formatBangkokDateTime(shipment.packedAt, localeTag) : undefined}>
                    {shipment.packedAt ? formatBangkokDateTime(shipment.packedAt, localeTag) : "—"}
                  </b>
                  <small>{t("shippingPage.drawer.packedAt")}</small>
                </p>
                <p className="tone-primary">
                  <span className="cm-icon-chip" aria-hidden="true"><Truck /></span>
                  <b className="num" title={shipment.shippedAt ? formatBangkokDateTime(shipment.shippedAt, localeTag) : undefined}>
                    {shipment.shippedAt ? formatBangkokDateTime(shipment.shippedAt, localeTag) : "—"}
                  </b>
                  <small>{t("shippingPage.drawer.shippedAt")}</small>
                </p>
                <p className="tone-success">
                  <span className="cm-icon-chip" aria-hidden="true"><Clock3 /></span>
                  <b className="num" title={shipment.deliveredAt ? formatBangkokDateTime(shipment.deliveredAt, localeTag) : undefined}>
                    {shipment.deliveredAt ? formatBangkokDateTime(shipment.deliveredAt, localeTag) : "—"}
                  </b>
                  <small>{t("shippingPage.drawer.deliveredAt")}</small>
                </p>
              </div>

              {(shipment.pickedUpAt || shipment.failedAt || shipment.returnedAt || shipment.failureReason) ? (
                <section>
                  <h3>{t("shippingPage.drawer.moreDates")}</h3>
                  <dl className="cm-contact sh-compact-kv">
                    {shipment.pickedUpAt ? (
                      <div className="cm-contact-row">
                        <dt>{t("shippingPage.drawer.pickedUpAt")}</dt>
                        <dd className="num">{formatBangkokDateTime(shipment.pickedUpAt, localeTag)}</dd>
                      </div>
                    ) : null}
                    {shipment.failedAt ? (
                      <div className="cm-contact-row">
                        <dt>{t("shippingPage.drawer.failedAt")}</dt>
                        <dd className="num">{formatBangkokDateTime(shipment.failedAt, localeTag)}</dd>
                      </div>
                    ) : null}
                    {shipment.returnedAt ? (
                      <div className="cm-contact-row">
                        <dt>{t("shippingPage.drawer.returnedAt")}</dt>
                        <dd className="num">{formatBangkokDateTime(shipment.returnedAt, localeTag)}</dd>
                      </div>
                    ) : null}
                    {shipment.failureReason ? (
                      <div className="cm-contact-row">
                        <dt>{t("shippingPage.drawer.failureReason")}</dt>
                        <dd>{shipment.failureReason}</dd>
                      </div>
                    ) : null}
                  </dl>
                </section>
              ) : null}

              <section className="sh-work">
                <div className="sh-work-tabs" role="tablist" aria-label={t("shippingPage.drawer.workTabs")}>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={workTab === "update"}
                    className={workTab === "update" ? "active" : undefined}
                    onClick={() => setWorkTab("update")}
                  >
                    <Truck aria-hidden="true" />
                    {t("shippingPage.drawer.tabUpdate")}
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={workTab === "history"}
                    className={workTab === "history" ? "active" : undefined}
                    onClick={() => setWorkTab("history")}
                  >
                    <Clock3 aria-hidden="true" />
                    {t("shippingPage.drawer.tabHistory")}
                    {(shipment.events ?? []).length > 0 ? (
                      <em className="num">{(shipment.events ?? []).length}</em>
                    ) : null}
                  </button>
                </div>

                {workTab === "update" ? (
                  <div className="sh-work-pane om-filters" role="tabpanel">
                    {!TRACKING_LOCKED.has(shipment.status) ? (
                      <div className="sh-work-block">
                        <h3>{t("shippingPage.drawer.editTracking")}</h3>
                        <div className="sh-form-grid">
                          <SearchableSelect
                            label={t("shippingPage.drawer.carrier")}
                            disabled={!canUpdate}
                            options={[
                              "—",
                              ...options.map((c) => (localeTag.startsWith("en") ? c.nameEn : c.nameTh) || c.code),
                            ]}
                            value={
                              carrierCode
                                ? ((localeTag.startsWith("en")
                                  ? options.find((c) => c.code === carrierCode)?.nameEn
                                  : options.find((c) => c.code === carrierCode)?.nameTh) || carrierCode)
                                : "—"
                            }
                            onChange={(picked) => {
                              if (picked === "—") {
                                setCarrierCode("");
                                return;
                              }
                              const hit = options.find((c) => {
                                const name = (localeTag.startsWith("en") ? c.nameEn : c.nameTh) || c.code;
                                return name === picked || c.code === picked;
                              });
                              setCarrierCode(hit?.code ?? "");
                            }}
                          />
                          <div className="om-filter-search">
                            <span className="form-label">{t("shippingPage.drawer.tracking")}</span>
                            <label className="om-search">
                              <input
                                className="num"
                                maxLength={64}
                                value={trackingNumber}
                                disabled={!canUpdate}
                                placeholder={t("shippingPage.drawer.trackingPlaceholder")}
                                onChange={(e) => setTrackingNumber(e.target.value)}
                              />
                            </label>
                          </div>
                        </div>
                        <button
                          type="button"
                          className="sh-work-btn"
                          disabled={busy || !canUpdate || !trackingChanged || !trackingNumber.trim()}
                          title={deniedTitle(canUpdate)}
                          onClick={saveTracking}
                        >
                          {t("shippingPage.drawer.saveTracking")}
                        </button>
                      </div>
                    ) : (
                      <p className="sh-work-locked">{t("shippingPage.drawer.trackingLocked")}</p>
                    )}

                    {canUpdate ? (
                      <div className="sh-work-block">
                        <h3>{t("shippingPage.drawer.addEvent")}</h3>
                        <div className="om-filter-search">
                          <span className="form-label">{t("shippingPage.drawer.location")}</span>
                          <label className="om-search">
                            <input
                              maxLength={200}
                              value={location}
                              placeholder={t("shippingPage.drawer.locationPlaceholder")}
                              onChange={(e) => setLocation(e.target.value)}
                            />
                          </label>
                        </div>
                        <div className="sh-note-field">
                          <span className="form-label">{t("shippingPage.drawer.note")}</span>
                          <textarea
                            rows={2}
                            maxLength={500}
                            value={note}
                            placeholder={t("shippingPage.drawer.notePlaceholder")}
                            onChange={(e) => setNote(e.target.value)}
                          />
                        </div>
                        <button
                          type="button"
                          className="sh-work-btn ghost"
                          disabled={busy || (!note.trim() && !location.trim())}
                          onClick={addEntry}
                        >
                          {t("shippingPage.drawer.addEventButton")}
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <div className="sh-work-pane sh-work-history" role="tabpanel">
                    {(shipment.events ?? []).length === 0 ? (
                      <p className="sh-empty-events"><Truck aria-hidden="true" />{t("shippingPage.drawer.noEvents")}</p>
                    ) : (
                      <ol className="sh-timeline">
                        {[...(shipment.events ?? [])]
                          .sort((a, b) => {
                            const byTime = Date.parse(b.eventAt) - Date.parse(a.eventAt);
                            return byTime !== 0 ? byTime : Number(b.id) - Number(a.id);
                          })
                          .map((event) => (
                          <li key={event.id}>
                            <i aria-hidden="true" />
                            <div className="sh-timeline-card">
                              <b>{event.status ? label(`shippingPage.status.${event.status}`, event.status) : label(`shippingPage.eventTypes.${event.eventType}`, event.eventType)}</b>
                              {event.location ? <span className="sh-timeline-loc"><MapPin aria-hidden="true" />{event.location}</span> : null}
                              {event.note ? <em>{event.note}</em> : null}
                              <small>{formatBangkokDateTime(event.eventAt, localeTag)}{event.source ? ` · ${event.source}` : ""}</small>
                            </div>
                          </li>
                        ))}
                      </ol>
                    )}
                  </div>
                )}
              </section>
            </div>

            <div className="sh-actions">
              {shipment.trackingUrl ? (
                <a className="sh-actions-link" href={shipment.trackingUrl} target="_blank" rel="noreferrer">
                  <ExternalLink aria-hidden="true" />
                  {t("shippingPage.drawer.openTracking")}
                </a>
              ) : null}

              {awaitingDispatch ? (
                <div className="sh-actions-flow" role="group" aria-label={t("shippingPage.drawer.nextSteps")}>
                  <button
                    type="button"
                    className="sh-action-btn tone-primary is-next"
                    disabled={busy || !canDispatch}
                    title={!canDispatch ? deniedTitle(canDispatch) : !shipReady ? t("shippingPage.drawer.shipHint") : undefined}
                    onClick={() => {
                      if (!shipReady) {
                        setWorkTab("update");
                        notify(t("shippingPage.drawer.shipHint"), "fail");
                        return;
                      }
                      runShip();
                    }}
                  >
                    <Truck aria-hidden="true" />
                    <span>{t("shippingPage.actions.DISPATCH")}</span>
                  </button>
                  {!shipReady ? (
                    <p className="sh-actions-hint">{t("shippingPage.drawer.shipHint")}</p>
                  ) : null}
                </div>
              ) : null}

              {!awaitingDispatch && forwardActions.length + exceptionActions.length === 2 ? (
                <div className="sh-actions-pair" role="group" aria-label={t("shippingPage.drawer.nextSteps")}>
                  {[...forwardActions, ...exceptionActions].map((status, index) => {
                    const Icon = actionIcon(status);
                    return (
                      <button
                        key={status}
                        type="button"
                        className={`sh-action-btn ${actionTone(status, index === 0)}${index === 0 ? " is-next" : ""}`}
                        disabled={busy || !canUpdate}
                        title={deniedTitle(canUpdate)}
                        onClick={() => onStatusClick(status)}
                      >
                        <Icon aria-hidden="true" />
                        <span>{t(`shippingPage.actions.${status}`)}</span>
                      </button>
                    );
                  })}
                </div>
              ) : (
                <>
                  {!awaitingDispatch && forwardActions.length > 0 ? (
                    <div className="sh-actions-flow" role="group" aria-label={t("shippingPage.drawer.nextSteps")}>
                      {(() => {
                        const [next, ...rest] = forwardActions;
                        const NextIcon = actionIcon(next);
                        return (
                          <>
                            <button
                              type="button"
                              className={`sh-action-btn ${actionTone(next, true)} is-next`}
                              disabled={busy || !canUpdate}
                              title={deniedTitle(canUpdate)}
                              onClick={() => onStatusClick(next)}
                            >
                              <NextIcon aria-hidden="true" />
                              <span>{t(`shippingPage.actions.${next}`)}</span>
                            </button>
                            {rest.length > 0 ? (
                              <div className="sh-actions-rest">
                                {rest.map((status) => {
                                  const Icon = actionIcon(status);
                                  return (
                                    <button
                                      key={status}
                                      type="button"
                                      className={`sh-action-btn ${actionTone(status, false)}`}
                                      disabled={busy || !canUpdate}
                                      title={deniedTitle(canUpdate)}
                                      onClick={() => onStatusClick(status)}
                                    >
                                      <Icon aria-hidden="true" />
                                      <span>{t(`shippingPage.actions.${status}`)}</span>
                                    </button>
                                  );
                                })}
                              </div>
                            ) : null}
                          </>
                        );
                      })()}
                    </div>
                  ) : null}

                  {!awaitingDispatch && exceptionActions.length > 0 ? (
                    <div className="sh-actions-exception" role="group" aria-label={t("shippingPage.drawer.exceptionSteps")}>
                      {exceptionActions.map((status) => {
                        const Icon = actionIcon(status);
                        return (
                          <button
                            key={status}
                            type="button"
                            className={`sh-action-btn ${actionTone(status, false)}`}
                            disabled={busy || !canUpdate}
                            title={deniedTitle(canUpdate)}
                            onClick={() => onStatusClick(status)}
                          >
                            <Icon aria-hidden="true" />
                            <span>{t(`shippingPage.actions.${status}`)}</span>
                          </button>
                        );
                      })}
                    </div>
                  ) : null}
                </>
              )}

              {!awaitingDispatch && !shipment.trackingUrl && forwardActions.length === 0 && exceptionActions.length === 0 ? (
                <p className="sh-actions-done">{t("shippingPage.drawer.noMoreActions")}</p>
              ) : null}
            </div>
          </>
        ) : (
          <div className="om-drawer-body cm-detail-body"><div className="cm-empty">{t("shippingPage.empty")}</div></div>
        )}
      </aside>

      {confirm && shipment ? (
        <ConfirmDialog
          title={t(`shippingPage.actions.${confirm}`)}
          message={t(`shippingPage.confirm.${confirm}`, { number: shipment.orderNumber })}
          confirmLabel={t(`shippingPage.actions.${confirm}`)}
          cancelLabel={t("orderManagement.drawer.cancel")}
          danger
          confirmDisabled={busy || !reason.trim()}
          onCancel={() => { if (!busy) setConfirm(null); }}
          onConfirm={confirmDialog}
        >
          <div className="om-dialog-fields">
            <label className="om-field">
              <span>{t("orderManagement.drawer.reason")} *</span>
              <textarea rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
            {confirm === "DELIVERY_FAILED" ? (
              <label className="om-check">
                <input type="checkbox" checked={returned} onChange={(e) => setReturned(e.target.checked)} />
                {t("shippingPage.confirm.returnedCheck")}
              </label>
            ) : null}
            {formError ? <p className="om-error" role="alert">{formError}</p> : null}
          </div>
        </ConfirmDialog>
      ) : null}
    </div>
  );

  return createPortal(drawer, document.body);
}

export function statusClass(status?: string | null) {
  switch (status) {
    case "DELIVERED":
      return "success";
    case "DELIVERY_FAILED":
    case "RETURNED":
    case "CANCELLED":
      return "failed";
    case "IN_TRANSIT":
    case "OUT_FOR_DELIVERY":
    case "DISPATCHED":
      return "moving";
    default:
      return "wait";
  }
}
