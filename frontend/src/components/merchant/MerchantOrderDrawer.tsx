import { useCallback, useEffect, useMemo, useState } from "react";
import ConfigurationSummary from "../cart/ConfigurationSummary";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import FormTextarea from "../form/FormTextarea";
import { LoadError, Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import { FEATURES } from "../../config/features";
import { usePermissions } from "../../hooks/usePermissions";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  merchantOrderApi,
  merchantShipmentApi,
  type AllowedTransition,
  type AllowedTransitions,
  type OrderDetail,
  type OrderTimelineEntry,
  type ShopCarrierView,
  type ShopCarriersResponse,
  type TransitionBody,
} from "../../services/api";
import {
  setOrderTags,
  updateInternalNote,
  type OrderTag,
} from "../../services/merchantOrderExtensions";
import "../../styles/order-management.css";
import AuthImage from "./AuthImage";
import OrderPaymentPanel from "./OrderPaymentPanel";
import OrderRefundPanel, { showsRefundPanel, type RefundDialogRequest } from "./OrderRefundPanel";
import OrderTagChips from "./OrderTagChips";
import OrderTagPicker from "./OrderTagPicker";
import SearchableSelect from "../SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "./selectOptions";
import {
  bangkokNowInput,
  formatBangkokDateTime,
  isStaleOrderError,
  numberOf,
  orderErrorMessage,
  paymentTone,
  statusTone,
} from "./orderFormat";

type DrawerProps = {
  shopId: number;
  orderId: string;
  onClose: () => void;
  onChanged: () => void;
  notify: (text: string, tone?: "ok" | "fail") => void;
  /** Shop tag catalog (cached by parent). */
  shopTags?: OrderTag[];
  onShopTagsChanged?: (tags: OrderTag[]) => void;
  onCreateShopTag?: (name: string, color: string | null) => Promise<OrderTag | null>;
};

type ActionMeta = { endpoint?: string; permission: string; tone?: "primary" | "danger" };

/** drawer button ← transitions[].action (the server decides what is allowed; the map only knows how to call it) */
const ACTIONS: Record<string, ActionMeta> = {
  ACCEPT: { endpoint: "accept", permission: "ORDER_UPDATE", tone: "primary" },
  MARK_PAID: { endpoint: "mark-paid", permission: "ORDER_UPDATE" },
  CONFIRM: { endpoint: "confirm", permission: "ORDER_UPDATE", tone: "primary" },
  PROCESS: { endpoint: "process", permission: "SHIPMENT_PACK" },
  SHIP: { endpoint: "ship", permission: "SHIPMENT_DISPATCH", tone: "primary" },
  DELIVER: { endpoint: "deliver", permission: "SHIPMENT_UPDATE", tone: "primary" },
  CANCEL: { endpoint: "cancel", permission: "ORDER_CANCEL", tone: "danger" },
  APPROVE_REFUND: { permission: "ORDER_REFUND", tone: "primary" },
  REJECT_REFUND: { permission: "ORDER_REFUND", tone: "danger" },
  COLLECT_COD: { permission: "ORDER_UPDATE", tone: "primary" },
};

/** run without a dialog */
const DIRECT = new Set(["ACCEPT", "CONFIRM", "PROCESS"]);

function actionKey(action: AllowedTransition) {
  return `${action.action}:${action.toStatus ?? ""}`;
}

function actionLabelKey(action: AllowedTransition) {
  return action.action === "PROCESS" && action.toStatus
    ? `orderManagement.actions.PROCESS_${action.toStatus}`
    : `orderManagement.actions.${action.action}`;
}

export default function MerchantOrderDrawer({
  shopId,
  orderId,
  onClose,
  onChanged,
  notify,
  shopTags = [],
  onCreateShopTag,
}: DrawerProps) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { canInShop, deniedTitle } = usePermissions();
  const money = (value: number | string | null | undefined) => formatBaht(value, localeTag);

  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [timeline, setTimeline] = useState<OrderTimelineEntry[]>([]);
  const [transitions, setTransitions] = useState<AllowedTransitions | null>(null);
  const [noteDraft, setNoteDraft] = useState("");
  const [noteBusy, setNoteBusy] = useState(false);
  const [tagPickerOpen, setTagPickerOpen] = useState(false);
  const [tagBusy, setTagBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<AllowedTransition | null>(null);
  const [reason, setReason] = useState("");
  const [carrierCode, setCarrierCode] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [shippedAt, setShippedAt] = useState("");
  const [paymentReference, setPaymentReference] = useState("");
  const [formError, setFormError] = useState("");
  const [carrierData, setCarrierData] = useState<ShopCarriersResponse | null>(null);
  const [codRequest, setCodRequest] = useState(0);
  const [refundRequest, setRefundRequest] = useState<RefundDialogRequest>(null);

  const loadAux = useCallback(async () => {
    const [line, allowed] = await Promise.all([
      merchantOrderApi.timeline(shopId, orderId).catch(() => [] as OrderTimelineEntry[]),
      merchantOrderApi.transitions(shopId, orderId).catch(() => null),
    ]);
    setTimeline(line);
    setTransitions(allowed);
  }, [shopId, orderId]);

  const loadAll = useCallback(async () => {
    setError(null);
    try {
      const detail = await merchantOrderApi.get(shopId, orderId);
      setOrder(detail);
      setNoteDraft(detail.internalNote ?? "");
      await loadAux();
    } catch (caught) {
      setError(orderErrorMessage(t, caught));
    } finally {
      setLoading(false);
    }
  }, [shopId, orderId, loadAux, t]);

  useEffect(() => {
    setLoading(true);
    setOrder(null);
    setTransitions(null);
    setTimeline([]);
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    let alive = true;
    merchantShipmentApi.carriers(shopId)
      .then((res) => { if (alive) setCarrierData(res); })
      .catch(() => { if (alive) setCarrierData({ restricted: false, carriers: [] }); });
    return () => { alive = false; };
  }, [shopId]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (document.querySelector(".pm-dialog")) return;
      onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const afterChange = useCallback((next: OrderDetail) => {
    setOrder(next);
    setNoteDraft(next.internalNote ?? "");
    onChanged();
    void loadAux();
  }, [onChanged, loadAux]);

  const reloadStale = useCallback(() => {
    onChanged();
    void loadAll();
  }, [onChanged, loadAll]);

  const saveInternalNote = async () => {
    if (!order || noteBusy) return;
    setNoteBusy(true);
    try {
      const result = await updateInternalNote(shopId, order.id, {
        internalNote: noteDraft.trim() ? noteDraft.trim() : null,
        expectedVersion: order.version ?? 0,
      });
      afterChange({ ...order, internalNote: result.internalNote, version: result.version });
      notify(t("orderManagement.note.saved"));
    } catch (caught) {
      const message = orderErrorMessage(t, caught);
      notify(message, "fail");
      if (isStaleOrderError(caught)) reloadStale();
    } finally {
      setNoteBusy(false);
    }
  };

  const saveOrderTags = async (tagIds: string[]) => {
    if (!order || tagBusy) return;
    setTagBusy(true);
    try {
      const result = await setOrderTags(shopId, order.id, {
        tagIds,
        expectedVersion: order.version ?? 0,
      });
      afterChange({ ...order, tags: result.tags, version: result.version });
      setTagPickerOpen(false);
      notify(t("orderManagement.tags.saved"));
    } catch (caught) {
      const message = orderErrorMessage(t, caught);
      notify(message, "fail");
      if (isStaleOrderError(caught)) reloadStale();
    } finally {
      setTagBusy(false);
    }
  };

  const version = transitions?.version ?? order?.version;
  const actions = useMemo(
    () => (transitions?.transitions ?? []).filter((item) => item.action in ACTIONS && item.action !== "REQUEST_REFUND"),
    [transitions],
  );

  const run = async (action: AllowedTransition, extra?: Partial<TransitionBody>) => {
    const meta = ACTIONS[action.action];
    if (!meta?.endpoint || !order) return;
    const body: TransitionBody = { expectedVersion: version, ...extra };
    if (action.action === "PROCESS" && action.toStatus) body.status = action.toStatus;
    setBusy(true);
    setFormError("");
    try {
      const result = await merchantOrderApi.act(shopId, order.id, meta.endpoint, body);
      setDialog(null);
      afterChange(result.order);
      notify(t(result.changed ? "orderManagement.drawer.toast.done" : "orderManagement.drawer.toast.unchanged"));
    } catch (caught) {
      const message = orderErrorMessage(t, caught);
      if (isStaleOrderError(caught)) {
        setDialog(null);
        notify(message, "fail");
        reloadStale();
      } else if (dialog) {
        setFormError(message);
      } else {
        notify(message, "fail");
      }
    } finally {
      setBusy(false);
    }
  };

  const openAction = (action: AllowedTransition) => {
    if (action.action === "COLLECT_COD") { setCodRequest((n) => n + 1); return; }
    if (action.action === "APPROVE_REFUND") { setRefundRequest({ kind: "approve", nonce: Date.now() }); return; }
    if (action.action === "REJECT_REFUND") { setRefundRequest({ kind: "reject", nonce: Date.now() }); return; }
    if (DIRECT.has(action.action) && !action.reasonRequired && !action.trackingRequired) { void run(action); return; }
    setReason("");
    setTrackingNumber("");
    setShippedAt("");
    setPaymentReference("");
    setFormError("");
    setCarrierCode(order?.shipping?.carrierCode ?? "");
    setDialog(action);
  };

  const needsTracking = !!dialog && (dialog.trackingRequired || dialog.action === "SHIP");
  const dialogInvalid = !!dialog && ((dialog.reasonRequired && !reason.trim()) || (needsTracking && (!carrierCode.trim() || !trackingNumber.trim())));

  const confirmDialog = () => {
    if (!dialog || dialogInvalid) return;
    const extra: Partial<TransitionBody> = {};
    if (reason.trim()) extra.reason = reason.trim();
    if (needsTracking) {
      extra.carrierCode = carrierCode.trim();
      extra.trackingNumber = trackingNumber.trim();
      if (shippedAt) extra.shippedAt = shippedAt;
    }
    if (dialog.action === "MARK_PAID" && order) {
      extra.provider = "BANK_TRANSFER";
      extra.amount = numberOf(order.grandTotal);
      if (paymentReference.trim()) extra.paymentReference = paymentReference.trim();
    }
    void run(dialog, extra);
  };

  const carrierLabel = (carrier: ShopCarrierView) => (localeTag.startsWith("en") ? carrier.nameEn : carrier.nameTh) || carrier.code;
  const carriers = useMemo(
    () => (carrierData ? carrierData.carriers.filter((c) => c.active && (!carrierData.restricted || c.enabled)) : []),
    [carrierData],
  );
  const carrierOptions = uniqueOptions(carriers.map((carrier) => ({ value: carrier.code, label: carrierLabel(carrier) })));
  const carrierName = (code?: string | null) => {
    if (!code) return "—";
    const hit = carrierData?.carriers.find((c) => c.code === code);
    return hit ? carrierLabel(hit) : code;
  };

  const awaiting = !!order && (order.awaitingAcceptance === true || order.itemsHidden === true);
  const address = order?.shippingAddress;
  const addressLines = address
    ? [address.addressLine1, address.addressLine2, address.subdistrict, address.district, address.province, address.postalCode].filter(Boolean).join(" ")
    : "";
  const canUpdate = canInShop("ORDER_UPDATE");
  const canRefund = canInShop("ORDER_REFUND");
  const hasCod = actions.some((a) => a.action === "COLLECT_COD");

  return (
    <div className="om-drawer-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside className="om-drawer" role="dialog" aria-modal="true" aria-label={t("orderManagement.drawer.title")}>
        <header className="om-drawer-head">
          <div>
            <h2 className="num">{order?.orderNumber ?? t("orderManagement.drawer.title")}</h2>
            {order && !awaiting && (
              <p>
                <span className={`om-pill ${statusTone(order.status)}`}>{t(`orderManagement.status.${order.status}`, { defaultValue: order.status })}</span>
                <span className={`om-pill ${paymentTone(order.paymentStatus)}`}>{t(`orderManagement.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}</span>
                {order.paymentMethod === "COD" && <span className="om-badge cod">COD</span>}
              </p>
            )}
            {order && awaiting && <p><span className="om-badge warn">{t("orderManagement.awaitingBadge")}</span></p>}
            {FEATURES.orderTagsApi && order && !awaiting ? (
              <div className="om-drawer-tags">
                <OrderTagChips tags={order.tags} />
                <button type="button" className="om-tag-edit" disabled={tagBusy} onClick={() => setTagPickerOpen(true)}>
                  {t("orderManagement.tags.editOrder")}
                </button>
              </div>
            ) : null}
          </div>
          <button type="button" className="om-drawer-close" aria-label={t("orderManagement.drawer.close")} onClick={onClose}><X /></button>
        </header>

        <div className="om-drawer-body">
          {loading && (
            <div className="om-skel">
              <Skeleton height={20} width="60%" />
              <Skeleton height={90} radius={8} />
              <Skeleton height={120} radius={8} />
            </div>
          )}
          {!loading && error && <LoadError message={error} onRetry={() => { setLoading(true); void loadAll(); }} retryLabel={t("shopSettings.retry")} />}

          {order && (
            <>
              <section className="om-section">
                <dl className="om-kv">
                  <div><dt>{t("orderManagement.drawer.placedAt")}</dt><dd>{formatBangkokDateTime(order.placedAt, localeTag)}</dd></div>
                  {!awaiting && order.merchantConfirmedAt && <div><dt>{t("orderManagement.drawer.acceptedAt")}</dt><dd>{formatBangkokDateTime(order.merchantConfirmedAt, localeTag)}</dd></div>}
                  {!awaiting && order.termsAcceptedAt && <div><dt>{t("orderManagement.drawer.termsAcceptedAt")}</dt><dd>{formatBangkokDateTime(order.termsAcceptedAt, localeTag)}</dd></div>}
                  {!awaiting && order.paidAt && <div><dt>{t("orderManagement.drawer.paidAt")}</dt><dd>{formatBangkokDateTime(order.paidAt, localeTag)}</dd></div>}
                  {!awaiting && order.cancelReason && <div><dt>{t("orderManagement.drawer.cancelReason")}</dt><dd>{order.cancelReason}</dd></div>}
                </dl>
                {awaiting && <p className="om-note">{t("orderManagement.drawer.awaitingNote")}</p>}
              </section>

              {!awaiting && (
                <>
                  <section className="om-section">
                    <h3>{t("orderManagement.drawer.items")}</h3>
                    <ul className="om-items">
                      {(order.items ?? []).map((item) => (
                        <li key={item.id}>
                          <AuthImage className="om-item-img" path={item.imageUrl} alt={item.productName} />
                          <div>
                            <b>{item.productName}</b>
                            {item.optionLabel && <small>{item.optionLabel}</small>}
                            <ConfigurationSummary configuration={item.configuration} localeTag={localeTag} />
                            {item.skuCode && <small className="num">{item.skuCode}</small>}
                          </div>
                          <span className="num">{money(item.unitPrice)} × {item.quantity}</span>
                          <strong className="num">{money(item.lineTotal)}</strong>
                        </li>
                      ))}
                      {(order.items ?? []).length === 0 && <li className="om-muted">{t("orderManagement.drawer.noItems")}</li>}
                    </ul>
                  </section>

                  <section className="om-section">
                    <h3>{t("orderManagement.drawer.summary")}</h3>
                    <dl className="om-kv om-kv-money">
                      <div><dt>{t("orderManagement.drawer.subtotal")}</dt><dd className="num">{money(order.subtotal)}</dd></div>
                      {numberOf(order.discountTotal) > 0 && <div><dt>{t("orderManagement.drawer.discount")}{order.couponCode ? ` (${order.couponCode})` : ""}</dt><dd className="num">-{money(order.discountTotal)}</dd></div>}
                      <div><dt>{t("orderManagement.drawer.shippingFee")}</dt><dd className="num">{money(order.shippingFee)}</dd></div>
                      {numberOf(order.taxTotal) > 0 && <div><dt>{t("orderManagement.drawer.tax")}</dt><dd className="num">{money(order.taxTotal)}</dd></div>}
                      {numberOf(order.codFee) > 0 && <div><dt>{t("orderManagement.drawer.codFee")}</dt><dd className="num">{money(order.codFee)}</dd></div>}
                      <div className="total"><dt>{t("orderManagement.drawer.grandTotal")}</dt><dd className="num">{money(order.grandTotal)}</dd></div>
                      {order.paidAmount != null && <div><dt>{t("orderManagement.refund.paid")}</dt><dd className="num">{money(order.paidAmount)}</dd></div>}
                      {numberOf(order.refundedAmount) > 0 && <div><dt>{t("orderManagement.refund.refunded")}</dt><dd className="num">{money(order.refundedAmount)}</dd></div>}
                      {order.refundableAmount != null && numberOf(order.refundableAmount) > 0 && <div><dt>{t("orderManagement.refund.refundable")}</dt><dd className="num">{money(order.refundableAmount)}</dd></div>}
                    </dl>
                  </section>

                  <section className="om-section">
                    <h3>{t("orderManagement.drawer.address")}</h3>
                    {address?.masked && <p className="om-note">{t("orderManagement.drawer.addressMasked")}</p>}
                    {address ? (
                      <dl className="om-kv">
                        {address.recipientName && <div><dt>{t("orderManagement.drawer.recipient")}</dt><dd>{address.recipientName}</dd></div>}
                        {address.phone && <div><dt>{t("orderManagement.drawer.phone")}</dt><dd className="num">{address.phone}</dd></div>}
                        <div><dt>{t("orderManagement.drawer.address")}</dt><dd>{addressLines || "—"}</dd></div>
                      </dl>
                    ) : <p className="om-muted">—</p>}
                  </section>

                  <section className="om-section">
                    <h3>{t("orderManagement.drawer.shipping")}</h3>
                    <dl className="om-kv">
                      <div><dt>{t("orderManagement.drawer.shippingMethod")}</dt><dd>{order.shipping?.methodName || "—"}</dd></div>
                      {order.shipment ? (
                        <>
                          <div><dt>{t("orderManagement.drawer.carrier")}</dt><dd>{carrierName(order.shipment.carrierCode)}</dd></div>
                          <div><dt>{t("orderManagement.drawer.tracking")}</dt><dd className="num">{order.shipment.trackingNumber || "—"}</dd></div>
                          {order.shipment.status && <div><dt>{t("orderManagement.drawer.shipmentStatus")}</dt><dd>{t(`shippingPage.status.${order.shipment.status}`, { defaultValue: order.shipment.status })}</dd></div>}
                          {order.shipment.packedAt && <div><dt>{t("orderManagement.drawer.packedAt")}</dt><dd>{formatBangkokDateTime(order.shipment.packedAt, localeTag)}</dd></div>}
                          {order.shipment.dispatchedAt && <div><dt>{t("orderManagement.drawer.dispatchedAt")}</dt><dd>{formatBangkokDateTime(order.shipment.dispatchedAt, localeTag)}</dd></div>}
                          {order.shipment.deliveredAt && <div><dt>{t("orderManagement.drawer.deliveredAt")}</dt><dd>{formatBangkokDateTime(order.shipment.deliveredAt, localeTag)}</dd></div>}
                        </>
                      ) : null}
                    </dl>
                  </section>

                  {order.buyerNote && (
                    <section className="om-section">
                      <h3>{t("orderManagement.drawer.note")}</h3>
                      <p className="om-note-text">{order.buyerNote}</p>
                    </section>
                  )}

                  {FEATURES.orderNotesApi && !awaiting ? (
                    <section className="om-section">
                      <h3>{t("orderManagement.note.internalLabel")}</h3>
                      <FormTextarea
                        label={t("orderManagement.note.internalLabel")}
                        value={noteDraft}
                        maxLength={2000}
                        rows={4}
                        disabled={noteBusy}
                        onChange={(event) => setNoteDraft(event.target.value)}
                      />
                      <p className="om-muted"><span className="num">{noteDraft.length}</span>/2000</p>
                      <div className="om-row-actions">
                        <button type="button" className="om-btn primary" disabled={noteBusy} onClick={() => void saveInternalNote()}>
                          {t("orderManagement.note.save")}
                        </button>
                        <button
                          type="button"
                          className="om-btn"
                          disabled={noteBusy}
                          onClick={() => setNoteDraft(order.internalNote ?? "")}
                        >
                          {t("orderManagement.note.revert")}
                        </button>
                      </div>
                    </section>
                  ) : null}

                  <OrderPaymentPanel
                    shopId={shopId}
                    order={order}
                    canUpdate={canUpdate}
                    deniedTitle={deniedTitle(canUpdate)}
                    codRequest={codRequest}
                    canCollectCod={hasCod}
                    onOrderChanged={afterChange}
                    onStale={reloadStale}
                    notify={notify}
                  />

                  {showsRefundPanel(order) && (
                    <OrderRefundPanel
                      shopId={shopId}
                      order={order}
                      canRefund={canRefund}
                      deniedTitle={deniedTitle(canRefund)}
                      openRequest={refundRequest}
                      onOrderChanged={afterChange}
                      onStale={reloadStale}
                      notify={notify}
                    />
                  )}
                </>
              )}

              <section className="om-section">
                <h3>{t("orderManagement.drawer.timeline")}</h3>
                {timeline.length === 0 ? <p className="om-muted">—</p> : (
                  <ol className="om-timeline">
                    {timeline.map((entry) => (
                      <li key={entry.id}>
                        <b>{t(`orderManagement.timelineEvents.${entry.event}`, { defaultValue: entry.event })}</b>
                        {entry.toStatus && <span>{entry.fromStatus ? `${t(`orderManagement.status.${entry.fromStatus}`, { defaultValue: entry.fromStatus })} → ` : ""}{t(`orderManagement.status.${entry.toStatus}`, { defaultValue: entry.toStatus })}</span>}
                        {entry.reason && <em>{entry.reason}</em>}
                        <small>{formatBangkokDateTime(entry.at, localeTag)}{entry.actorType ? ` · ${entry.actorType}` : ""}</small>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </>
          )}
        </div>

        {order && actions.length > 0 && (
          <footer className="om-drawer-actions">
            {actions.map((action) => {
              const meta = ACTIONS[action.action];
              const allowed = canInShop(meta.permission);
              return (
                <button
                  key={actionKey(action)}
                  type="button"
                  className={`om-btn ${meta.tone ?? ""}`}
                  disabled={busy || !allowed}
                  title={deniedTitle(allowed)}
                  onClick={() => openAction(action)}
                >
                  {t(actionLabelKey(action), { defaultValue: action.action })}
                </button>
              );
            })}
          </footer>
        )}
      </aside>

      {dialog && order && (
        <ConfirmDialog
          title={t(actionLabelKey(dialog), { defaultValue: dialog.action })}
          message={t(`orderManagement.dialogs.${dialog.action}`, { defaultValue: t("orderManagement.dialogs.generic"), number: order.orderNumber })}
          confirmLabel={t(actionLabelKey(dialog), { defaultValue: dialog.action })}
          cancelLabel={t("orderManagement.drawer.cancel")}
          danger={ACTIONS[dialog.action]?.tone === "danger"}
          confirmDisabled={busy || dialogInvalid}
          onCancel={() => { if (!busy) setDialog(null); }}
          onConfirm={confirmDialog}
        >
          <div className="om-dialog-fields">
            {dialog.action === "MARK_PAID" && (
              <label className="om-field">
                <span>{t("orderManagement.drawer.paymentReference")}</span>
                <input maxLength={120} value={paymentReference} onChange={(e) => setPaymentReference(e.target.value)} />
              </label>
            )}
            {needsTracking && (
              <>
                {carriers.length > 0 ? (
                  <div className="om-field">
                    <span>{t("orderManagement.drawer.carrier")} *</span>
                    <SearchableSelect
                      compact
                      label={t("orderManagement.drawer.carrier")}
                      options={carrierOptions.map((opt) => opt.label)}
                      value={labelOf(carrierOptions, carrierCode)}
                      placeholder={t("orderManagement.drawer.chooseCarrier")}
                      onChange={(picked) => setCarrierCode(valueOf(carrierOptions, picked))}
                    />
                  </div>
                ) : (
                  <label className="om-field">
                    <span>{t("orderManagement.drawer.carrier")} *</span>
                    <input value={carrierCode} onChange={(e) => setCarrierCode(e.target.value.toUpperCase())} />
                  </label>
                )}
                <label className="om-field">
                  <span>{t("orderManagement.drawer.tracking")} *</span>
                  <input maxLength={64} value={trackingNumber} onChange={(e) => setTrackingNumber(e.target.value)} />
                </label>
                <label className="om-field">
                  <span>{t("orderManagement.drawer.shippedAt")}</span>
                  <input type="datetime-local" max={bangkokNowInput()} value={shippedAt} onChange={(e) => setShippedAt(e.target.value)} />
                </label>
              </>
            )}
            {(dialog.reasonRequired || dialog.action === "CANCEL") && (
              <label className="om-field">
                <span>{t("orderManagement.drawer.reason")}{dialog.reasonRequired ? " *" : ""}</span>
                <textarea rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
            )}
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}
      {FEATURES.orderTagsApi && tagPickerOpen && order ? (
        <OrderTagPicker
          tags={shopTags}
          selectedIds={(order.tags ?? []).map((tag) => tag.id)}
          busy={tagBusy}
          onClose={() => setTagPickerOpen(false)}
          onSave={(tagIds) => void saveOrderTags(tagIds)}
          onCreate={async (name, color) => {
            if (!onCreateShopTag) return null;
            return onCreateShopTag(name, color);
          }}
        />
      ) : null}
    </div>
  );
}
