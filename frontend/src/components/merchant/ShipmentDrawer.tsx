import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ExternalLink, X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
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
const NEEDS_CONFIRM = new Set<StatusAction>(["DELIVERY_FAILED", "RETURNED"]);
const TRACKING_LOCKED = new Set(["DELIVERED", "RETURNED", "CANCELLED"]);

export default function ShipmentDrawer({ shopId, orderId, carriers, onClose, onChanged, notify }: Props) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { canInShop, deniedTitle } = usePermissions();
  const canUpdate = canInShop("SHIPMENT_UPDATE");
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

  const carrierLabel = useCallback((code?: string | null) => {
    if (!code) return "—";
    const hit = carriers.find((c) => c.code === code);
    if (!hit) return code;
    return (localeTag.startsWith("en") ? hit.nameEn : hit.nameTh) || hit.code;
  }, [carriers, localeTag]);

  const allowed = useMemo(
    () => STATUS_ACTIONS.filter((s) => shipment?.allowedStatuses?.includes(s)),
    [shipment],
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

  const selectable = carriers.filter((c) => c.active && c.enabled);
  const options = carrierCode && !selectable.some((c) => c.code === carrierCode)
    ? [...selectable, ...carriers.filter((c) => c.code === carrierCode)]
    : selectable;
  const label = (key: string, value: string) => t(key, { defaultValue: value });

  return (
    <div className="om-drawer-overlay sh-drawer-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside className="om-drawer" role="dialog" aria-modal="true" aria-label={t("shippingPage.drawer.title")}>
        <header className="om-drawer-head">
          <div>
            <h2 className="num">{shipment?.trackingNumber || shipment?.orderNumber || t("shippingPage.drawer.title")}</h2>
            {shipment && (
              <p>
                <span className={`shipping-status ${statusClass(shipment.status)}`}>{label(`shippingPage.status.${shipment.status}`, shipment.status)}</span>
              </p>
            )}
          </div>
          <button type="button" className="om-drawer-close" aria-label={t("orderManagement.drawer.close")} onClick={onClose}><X /></button>
        </header>

        <div className="om-drawer-body">
          {loading && <div className="om-skel"><Skeleton height={20} width="50%" /><Skeleton height={110} radius={8} /><Skeleton height={140} radius={8} /></div>}
          {!loading && error && <LoadError message={error} onRetry={() => { setLoading(true); void load(); }} retryLabel={t("shopSettings.retry")} />}

          {shipment && (
            <>
              <section className="om-section">
                <dl className="om-kv">
                  <div><dt>{t("shippingPage.drawer.order")}</dt><dd><a className="num" href="/merchant/orders">{shipment.orderNumber}</a></dd></div>
                  <div><dt>{t("shippingPage.drawer.orderStatus")}</dt><dd>{label(`orderManagement.status.${shipment.orderStatus}`, shipment.orderStatus)}</dd></div>
                  {shipment.paymentMethod && <div><dt>{t("shippingPage.drawer.paymentMethod")}</dt><dd>{shipment.paymentMethod === "COD" ? "COD" : t("shippingPage.drawer.prepaid")}</dd></div>}
                  <div><dt>{t("shippingPage.drawer.carrier")}</dt><dd>{shipment.carrierName || carrierLabel(shipment.carrierCode)}</dd></div>
                  <div>
                    <dt>{t("shippingPage.drawer.tracking")}</dt>
                    <dd className="num">
                      {shipment.trackingNumber || "—"}
                      {shipment.trackingUrl && <a className="sh-ext" href={shipment.trackingUrl} target="_blank" rel="noreferrer" aria-label={t("shippingPage.drawer.openTracking")}><ExternalLink /></a>}
                    </dd>
                  </div>
                  {shipment.packedAt && <div><dt>{t("shippingPage.drawer.packedAt")}</dt><dd>{formatBangkokDateTime(shipment.packedAt, localeTag)}</dd></div>}
                  {shipment.shippedAt && <div><dt>{t("shippingPage.drawer.shippedAt")}</dt><dd>{formatBangkokDateTime(shipment.shippedAt, localeTag)}</dd></div>}
                  {shipment.pickedUpAt && <div><dt>{t("shippingPage.drawer.pickedUpAt")}</dt><dd>{formatBangkokDateTime(shipment.pickedUpAt, localeTag)}</dd></div>}
                  {shipment.deliveredAt && <div><dt>{t("shippingPage.drawer.deliveredAt")}</dt><dd>{formatBangkokDateTime(shipment.deliveredAt, localeTag)}</dd></div>}
                  {shipment.failedAt && <div><dt>{t("shippingPage.drawer.failedAt")}</dt><dd>{formatBangkokDateTime(shipment.failedAt, localeTag)}</dd></div>}
                  {shipment.failureReason && <div><dt>{t("shippingPage.drawer.failureReason")}</dt><dd>{shipment.failureReason}</dd></div>}
                  {shipment.returnedAt && <div><dt>{t("shippingPage.drawer.returnedAt")}</dt><dd>{formatBangkokDateTime(shipment.returnedAt, localeTag)}</dd></div>}
                </dl>
              </section>

              {!TRACKING_LOCKED.has(shipment.status) && (
                <section className="om-section">
                  <h3>{t("shippingPage.drawer.editTracking")}</h3>
                  <div className="om-dialog-fields">
                    <label className="om-field">
                      <span>{t("shippingPage.drawer.carrier")}</span>
                      <select value={carrierCode} disabled={!canUpdate} onChange={(e) => setCarrierCode(e.target.value)}>
                        <option value="">—</option>
                        {options.map((c) => <option key={c.code} value={c.code}>{(localeTag.startsWith("en") ? c.nameEn : c.nameTh) || c.code}</option>)}
                      </select>
                    </label>
                    <label className="om-field">
                      <span>{t("shippingPage.drawer.tracking")}</span>
                      <input maxLength={64} value={trackingNumber} disabled={!canUpdate} onChange={(e) => setTrackingNumber(e.target.value)} />
                    </label>
                    <div className="om-btn-row">
                      <button type="button" className="om-btn" disabled={busy || !canUpdate || !trackingChanged || !trackingNumber.trim()} title={deniedTitle(canUpdate)} onClick={saveTracking}>
                        {t("shippingPage.drawer.saveTracking")}
                      </button>
                    </div>
                  </div>
                </section>
              )}

              {canUpdate && (
                <section className="om-section">
                  <h3>{t("shippingPage.drawer.addEvent")}</h3>
                  <div className="om-dialog-fields">
                    <label className="om-field">
                      <span>{t("shippingPage.drawer.location")}</span>
                      <input maxLength={200} value={location} onChange={(e) => setLocation(e.target.value)} />
                    </label>
                    <label className="om-field">
                      <span>{t("shippingPage.drawer.note")}</span>
                      <textarea rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
                    </label>
                    <div className="om-btn-row">
                      <button type="button" className="om-btn" disabled={busy || (!note.trim() && !location.trim())} onClick={addEntry}>
                        {t("shippingPage.drawer.addEventButton")}
                      </button>
                    </div>
                  </div>
                </section>
              )}

              <section className="om-section">
                <h3>{t("shippingPage.drawer.timeline")}</h3>
                {(shipment.events ?? []).length === 0 ? <p className="om-muted">—</p> : (
                  <ol className="om-timeline">
                    {(shipment.events ?? []).map((event) => (
                      <li key={event.id}>
                        <b>{event.status ? label(`shippingPage.status.${event.status}`, event.status) : label(`shippingPage.eventTypes.${event.eventType}`, event.eventType)}</b>
                        {event.location && <span>{event.location}</span>}
                        {event.note && <em>{event.note}</em>}
                        <small>{formatBangkokDateTime(event.eventAt, localeTag)}{event.source ? ` · ${event.source}` : ""}</small>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </>
          )}
        </div>

        {shipment && allowed.length > 0 && (
          <footer className="om-drawer-actions">
            {allowed.map((status) => (
              <button
                key={status}
                type="button"
                className={`om-btn ${status === "DELIVERED" ? "primary" : NEEDS_CONFIRM.has(status) ? "danger" : ""}`}
                disabled={busy || !canUpdate}
                title={deniedTitle(canUpdate)}
                onClick={() => onStatusClick(status)}
              >
                {t(`shippingPage.actions.${status}`)}
              </button>
            ))}
          </footer>
        )}
      </aside>

      {confirm && shipment && (
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
            {confirm === "DELIVERY_FAILED" && (
              <label className="om-check">
                <input type="checkbox" checked={returned} onChange={(e) => setReturned(e.target.checked)} />
                {t("shippingPage.confirm.returnedCheck")}
              </label>
            )}
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}
    </div>
  );
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
