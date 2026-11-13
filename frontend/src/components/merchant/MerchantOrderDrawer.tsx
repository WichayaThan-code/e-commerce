import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import ConfigurationSummary from "../cart/ConfigurationSummary";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Ban,
  Banknote,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  CreditCard,
  ExternalLink,
  MapPin,
  Package,
  PackageCheck,
  PackageOpen,
  Phone,
  Printer,
  ShoppingBag,
  Truck,
  Undo2,
  UserRound,
  X,
  XCircle,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
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
import "../../styles/customer-management.css";
import "../../styles/shipping.css";
import AuthImage from "./AuthImage";
import OrderPaymentPanel from "./OrderPaymentPanel";
import OrderRefundPanel, { showsRefundPanel, type RefundDialogRequest } from "./OrderRefundPanel";
import OrderTagChips from "./OrderTagChips";
import OrderTagPicker from "./OrderTagPicker";
import { OrderDateTimeField, shipNowInput } from "./ShipDateTimeFields";
import SearchableSelect from "../SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "./selectOptions";
import {
  formatBangkokDateTime,
  isStaleOrderError,
  numberOf,
  orderErrorMessage,
  orderFieldErrorMap,
  paymentTone,
  statusTone,
} from "./orderFormat";
import { printMerchantOrder } from "./orderPrint";

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

function actionIcon(action: AllowedTransition): LucideIcon {
  if (action.action === "PROCESS") {
    switch (action.toStatus) {
      case "READY_TO_PACK": return Package;
      case "PACKING": return PackageOpen;
      case "PACKED": return PackageCheck;
      default: return Package;
    }
  }
  switch (action.action) {
    case "ACCEPT": return PackageCheck;
    case "MARK_PAID": return CircleDollarSign;
    case "CONFIRM": return CheckCircle2;
    case "SHIP": return Truck;
    case "DELIVER": return CheckCircle2;
    case "CANCEL": return XCircle;
    case "APPROVE_REFUND": return Undo2;
    case "REJECT_REFUND": return Ban;
    case "COLLECT_COD": return Banknote;
    default: return Package;
  }
}

function avatarTone(status?: string | null, awaiting?: boolean) {
  if (awaiting) return "wait";
  const tone = statusTone(status);
  if (tone === "delivered" || tone === "paid") return "success";
  if (tone === "cancelled") return "failed";
  if (tone === "shipping") return "moving";
  return "wait";
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
  const loadGen = useRef(0);

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
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [carrierData, setCarrierData] = useState<ShopCarriersResponse | null>(null);
  const [codRequest, setCodRequest] = useState(0);
  const [refundRequest, setRefundRequest] = useState<RefundDialogRequest>(null);
  const [workTab, setWorkTab] = useState<"detail" | "history">("detail");
  const [deliverTrackingUrl, setDeliverTrackingUrl] = useState<string | null>(null);
  const [printing, setPrinting] = useState(false);

  const loadAux = useCallback(async (gen: number) => {
    const [line, allowed] = await Promise.all([
      merchantOrderApi.timeline(shopId, orderId).catch(() => [] as OrderTimelineEntry[]),
      merchantOrderApi.transitions(shopId, orderId).catch(() => null),
    ]);
    if (gen !== loadGen.current) return;
    setTimeline(line);
    setTransitions(allowed);
  }, [shopId, orderId]);

  const loadAll = useCallback(async () => {
    const gen = ++loadGen.current;
    setError(null);
    try {
      const detail = await merchantOrderApi.get(shopId, orderId);
      if (gen !== loadGen.current) return;
      setOrder(detail);
      setNoteDraft(detail.internalNote ?? "");
      await loadAux(gen);
    } catch (caught) {
      if (gen !== loadGen.current) return;
      setError(orderErrorMessage(t, caught));
    } finally {
      if (gen === loadGen.current) setLoading(false);
    }
  }, [shopId, orderId, loadAux, t]);

  useEffect(() => {
    setLoading(true);
    setOrder(null);
    setTransitions(null);
    setTimeline([]);
    setDialog(null);
    setFormError("");
    setWorkTab("detail");
    void loadAll();
    return () => { loadGen.current += 1; };
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

  /* Match shipping / customers — lock body scroll while the portaled drawer is open. */
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

  const afterChange = useCallback((next: OrderDetail) => {
    setOrder(next);
    setNoteDraft(next.internalNote ?? "");
    onChanged();
    void loadAux(loadGen.current);
  }, [onChanged, loadAux]);

  const reloadStale = useCallback(() => {
    onChanged();
    setLoading(true);
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
  const primaryActions = useMemo(
    () => actions.filter((item) => ACTIONS[item.action]?.tone === "primary"),
    [actions],
  );
  const secondaryActions = useMemo(
    () => actions.filter((item) => ACTIONS[item.action]?.tone !== "primary" && ACTIONS[item.action]?.tone !== "danger"),
    [actions],
  );
  const dangerActions = useMemo(
    () => actions.filter((item) => ACTIONS[item.action]?.tone === "danger"),
    [actions],
  );

  const run = async (action: AllowedTransition, extra?: Partial<TransitionBody>) => {
    const meta = ACTIONS[action.action];
    if (!meta?.endpoint || !order) return;
    const body: TransitionBody = { expectedVersion: version, ...extra };
    if (action.action === "PROCESS" && action.toStatus) body.status = action.toStatus;
    setBusy(true);
    setFormError("");
    setFieldErrors({});
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
        setFieldErrors(orderFieldErrorMap(t, caught));
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
    setTrackingNumber(order?.shipment?.trackingNumber ?? "");
    setShippedAt(action.action === "SHIP" || action.trackingRequired ? shipNowInput() : "");
    setPaymentReference("");
    setFormError("");
    setFieldErrors({});
    setCarrierCode(order?.shipping?.carrierCode ?? order?.shipment?.carrierCode ?? "");
    setDialog(action);
  };

  const needsTracking = !!dialog && (dialog.trackingRequired || dialog.action === "SHIP");
  const dialogInvalid = !!dialog && ((dialog.reasonRequired && !reason.trim()) || (needsTracking && (!carrierCode.trim() || !trackingNumber.trim())));
  const deliverTrackingNumber = order?.shipment?.trackingNumber?.trim() || trackingNumber.trim() || "";

  useEffect(() => {
    if (dialog?.action !== "DELIVER") {
      setDeliverTrackingUrl(null);
      return;
    }
    let active = true;
    setDeliverTrackingUrl(null);
    merchantShipmentApi.get(shopId, orderId)
      .then((shipment) => {
        if (!active) return;
        const url = shipment.trackingUrl?.trim();
        setDeliverTrackingUrl(url || null);
      })
      .catch(() => {
        if (active) setDeliverTrackingUrl(null);
      });
    return () => { active = false; };
  }, [dialog?.action, shopId, orderId]);

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
  const timelineNewestFirst = useMemo(
    () => [...timeline].sort((a, b) => {
      const byTime = Date.parse(b.at) - Date.parse(a.at);
      return byTime !== 0 ? byTime : b.id - a.id;
    }),
    [timeline],
  );
  const selectedCarrier = carriers.find((carrier) => carrier.code === carrierCode) ?? null;
  const trackingHint = selectedCarrier?.code === "THAIPOST"
    ? t("orderManagement.drawer.trackingHintThaipost")
    : selectedCarrier
      ? t("orderManagement.drawer.trackingHintGeneric")
      : "";
  const carrierName = (code?: string | null) => {
    if (!code) return "—";
    const hit = carrierData?.carriers.find((c) => c.code === code);
    return hit ? carrierLabel(hit) : code;
  };
  const clearFieldError = (field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const handlePrint = useCallback(async () => {
    if (!order || printing) return;
    setPrinting(true);
    try {
      await printMerchantOrder(shopId, order, t, localeTag);
      notify(t("orderManagement.printDone"));
    } catch (caught) {
      if (caught instanceof Error && caught.message === "PRINT_POPUP_BLOCKED") {
        notify(t("orderManagement.printBlocked"), "fail");
      } else {
        notify(orderErrorMessage(t, caught) || t("orderManagement.printFailed"), "fail");
      }
    } finally {
      setPrinting(false);
    }
  }, [order, printing, shopId, t, localeTag, notify]);

  const awaiting = !!order && (order.awaitingAcceptance === true || order.itemsHidden === true);
  const address = order?.shippingAddress;
  const addressLines = address
    ? [address.addressLine1, address.addressLine2, address.subdistrict, address.district, address.province, address.postalCode].filter(Boolean).join(" ")
    : "";
  const canUpdate = canInShop("ORDER_UPDATE");
  const canRefund = canInShop("ORDER_REFUND");
  const hasCod = actions.some((a) => a.action === "COLLECT_COD");
  const title = order?.orderNumber ?? t("orderManagement.drawer.title");
  const channelLabel = order?.channel
    ? t(`reports.channels.names.${order.channel}`, { defaultValue: order.channel })
    : null;

  const actionToneClass = (action: AllowedTransition, isNext: boolean) => {
    const tone = ACTIONS[action.action]?.tone;
    if (tone === "danger") return "tone-danger";
    if (isNext) return "tone-primary";
    return "tone-muted";
  };

  /** Ship only after the parcel is packed — keep the button visible but disabled earlier. */
  const shipReady = order?.status === "PACKED";
  const renderActionButton = (action: AllowedTransition, isNext = false) => {
    const meta = ACTIONS[action.action];
    const allowed = canInShop(meta.permission);
    const shipLocked = action.action === "SHIP" && !shipReady;
    const disabled = busy || !allowed || shipLocked;
    const Icon = actionIcon(action);
    const title = !allowed
      ? deniedTitle(allowed)
      : shipLocked
        ? t("orderManagement.drawer.shipRequiresPacked")
        : undefined;
    return (
      <button
        key={actionKey(action)}
        type="button"
        className={`sh-action-btn ${actionToneClass(action, isNext)}${isNext ? " is-next" : ""}`}
        disabled={disabled}
        title={title}
        onClick={() => { if (!disabled) openAction(action); }}
      >
        <Icon aria-hidden="true" />
        <span>{t(actionLabelKey(action), { defaultValue: action.action })}</span>
      </button>
    );
  };

  const drawer = (
    <div
      className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !dialog && !tagPickerOpen) onClose();
      }}
    >
      <aside
        className="om-drawer cm-detail sh-detail om-detail"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        {loading && !order ? (
          <div className="om-drawer-body cm-detail-body">
            <div className="om-skel">
              <Skeleton height={20} width="60%" />
              <Skeleton height={90} radius={8} />
              <Skeleton height={120} radius={8} />
            </div>
          </div>
        ) : error && !order ? (
          <div className="om-drawer-body cm-detail-body">
            <LoadError message={error} onRetry={() => { setLoading(true); void loadAll(); }} retryLabel={t("shopSettings.retry")} />
          </div>
        ) : order ? (
          <>
            <header className="cm-detail-head">
              <div className={`cm-avatar om-avatar tone-${avatarTone(order.status, awaiting)}`} aria-hidden="true">
                <ShoppingBag />
              </div>
              <div className="cm-detail-id">
                <h2 className="num" title={title}>{title}</h2>
                <div className="cm-detail-meta">
                  {awaiting ? (
                    <span className="om-badge warn">{t("orderManagement.awaitingBadge")}</span>
                  ) : (
                    <>
                      <span className={`om-pill ${statusTone(order.status)}`}>
                        {t(`orderManagement.status.${order.status}`, { defaultValue: order.status })}
                      </span>
                      <span className={`om-pill ${paymentTone(order.paymentStatus)}`}>
                        {t(`orderManagement.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}
                      </span>
                      {order.paymentMethod === "COD" ? <span className="om-badge cod">COD</span> : null}
                    </>
                  )}
                </div>
                {FEATURES.orderTagsApi && !awaiting ? (
                  <div className="om-drawer-tags">
                    <OrderTagChips tags={order.tags} />
                    <button type="button" className="om-tag-edit" disabled={tagBusy} onClick={() => setTagPickerOpen(true)}>
                      {t("orderManagement.tags.editOrder")}
                    </button>
                  </div>
                ) : null}
              </div>
              <div className="om-drawer-head-actions">
                <button
                  type="button"
                  className="om-drawer-close"
                  aria-label={t("orderManagement.printOrder", { number: order.orderNumber })}
                  title={printing ? t("orderManagement.printBusy") : t("orderManagement.printOrder", { number: order.orderNumber })}
                  disabled={printing}
                  onClick={() => void handlePrint()}
                >
                  <Printer aria-hidden="true" />
                </button>
                <button type="button" className="om-drawer-close" aria-label={t("orderManagement.drawer.close")} onClick={onClose}>
                  <X aria-hidden="true" />
                </button>
              </div>
            </header>

            <div className="om-drawer-body cm-detail-body">
              <dl className="cm-contact">
                <div className="cm-contact-row">
                  <span className="cm-icon-chip" aria-hidden="true"><Clock3 /></span>
                  <dt>{t("orderManagement.drawer.placedAt")}</dt>
                  <dd>{formatBangkokDateTime(order.placedAt, localeTag)}</dd>
                </div>
                {channelLabel ? (
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><ShoppingBag /></span>
                    <dt>{t("orderManagement.drawer.channel")}</dt>
                    <dd>{channelLabel}</dd>
                  </div>
                ) : null}
                {!awaiting && order.paymentMethod ? (
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><CreditCard /></span>
                    <dt>{t("orderManagement.drawer.paymentStatus")}</dt>
                    <dd>
                      {t(`orderManagement.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}
                      {order.paymentMethod === "COD" ? " · COD" : ""}
                    </dd>
                  </div>
                ) : null}
                {!awaiting && (order.fulfillmentStatus || order.shipment?.status) ? (
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><Truck /></span>
                    <dt>{t("orderManagement.drawer.fulfillmentStatus")}</dt>
                    <dd>
                      {order.fulfillmentStatus
                        ? t(`orderManagement.fulfillmentStatus.${order.fulfillmentStatus}`, { defaultValue: order.fulfillmentStatus })
                        : t(`shippingPage.status.${order.shipment!.status}`, { defaultValue: order.shipment!.status })}
                    </dd>
                  </div>
                ) : null}
                {!awaiting && order.cancelReason ? (
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><Package /></span>
                    <dt>{t("orderManagement.drawer.cancelReason")}</dt>
                    <dd>{order.cancelReason}</dd>
                  </div>
                ) : null}
              </dl>
              {awaiting ? <p className="om-note">{t("orderManagement.drawer.awaitingNote")}</p> : null}

              {!awaiting ? (
                <div className="cm-customer-kpis sh-detail-kpis">
                  <p className="tone-primary">
                    <span className="cm-icon-chip" aria-hidden="true"><CreditCard /></span>
                    <b className="num" title={money(order.grandTotal)}>{money(order.grandTotal)}</b>
                    <small>{t("orderManagement.drawer.grandTotal")}</small>
                  </p>
                  <p className="tone-warning">
                    <span className="cm-icon-chip" aria-hidden="true"><Package /></span>
                    <b className="num">{(order.items ?? []).length || order.itemCount || 0}</b>
                    <small>{t("orderManagement.drawer.items")}</small>
                  </p>
                  <p className="tone-success">
                    <span className="cm-icon-chip" aria-hidden="true"><Clock3 /></span>
                    <b className="num" title={order.paidAt ? formatBangkokDateTime(order.paidAt, localeTag) : undefined}>
                      {order.paidAt ? formatBangkokDateTime(order.paidAt, localeTag) : "—"}
                    </b>
                    <small>{t("orderManagement.drawer.paidAt")}</small>
                  </p>
                </div>
              ) : null}

              <section className="sh-work">
                <div className="sh-work-tabs" role="tablist" aria-label={t("orderManagement.drawer.workTabs")}>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={workTab === "detail"}
                    className={workTab === "detail" ? "active" : undefined}
                    onClick={() => setWorkTab("detail")}
                  >
                    <Package aria-hidden="true" />
                    {t("orderManagement.drawer.tabDetail")}
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={workTab === "history"}
                    className={workTab === "history" ? "active" : undefined}
                    onClick={() => setWorkTab("history")}
                  >
                    <Clock3 aria-hidden="true" />
                    {t("orderManagement.drawer.tabHistory")}
                    {timeline.length > 0 ? <em className="num">{timeline.length}</em> : null}
                  </button>
                </div>

                {workTab === "detail" ? (
                  <div className="sh-work-pane om-filters" role="tabpanel">
                    {awaiting ? (
                      <p className="om-muted">{t("orderManagement.drawer.awaitingNote")}</p>
                    ) : (
                      <>
                        <div className="sh-work-block">
                          <h3>{t("orderManagement.drawer.customer")}</h3>
                          {address?.masked ? <p className="om-note">{t("orderManagement.drawer.addressMasked")}</p> : null}
                          {address ? (
                            <dl className="cm-contact sh-compact-kv">
                              {address.recipientName ? (
                                <div className="cm-contact-row">
                                  <span className="cm-icon-chip" aria-hidden="true"><UserRound /></span>
                                  <dt>{t("orderManagement.drawer.recipient")}</dt>
                                  <dd>{address.recipientName}</dd>
                                </div>
                              ) : null}
                              {address.phone ? (
                                <div className="cm-contact-row">
                                  <span className="cm-icon-chip" aria-hidden="true"><Phone /></span>
                                  <dt>{t("orderManagement.drawer.phone")}</dt>
                                  <dd className="num">{address.phone}</dd>
                                </div>
                              ) : null}
                              <div className="cm-contact-row">
                                <span className="cm-icon-chip" aria-hidden="true"><MapPin /></span>
                                <dt>{t("orderManagement.drawer.address")}</dt>
                                <dd>{addressLines || "—"}</dd>
                              </div>
                            </dl>
                          ) : (
                            <p className="om-muted">—</p>
                          )}
                        </div>

                        <div className="sh-work-block">
                          <h3>{t("orderManagement.drawer.items")}</h3>
                          <ul className="om-items">
                            {(order.items ?? []).map((item) => (
                              <li key={item.id}>
                                <AuthImage className="om-item-img" path={item.imageUrl} alt={item.productName} />
                                <div>
                                  <b>{item.productName}</b>
                                  {item.optionLabel ? <small>{item.optionLabel}</small> : null}
                                  <ConfigurationSummary configuration={item.configuration} localeTag={localeTag} />
                                  {item.skuCode ? <small className="num">{item.skuCode}</small> : null}
                                </div>
                                <span className="num">{money(item.unitPrice)} × {item.quantity}</span>
                                <strong className="num">{money(item.lineTotal)}</strong>
                              </li>
                            ))}
                            {(order.items ?? []).length === 0 ? <li className="om-muted">{t("orderManagement.drawer.noItems")}</li> : null}
                          </ul>
                        </div>

                        <div className="sh-work-block">
                          <h3>{t("orderManagement.drawer.summary")}</h3>
                          <dl className="om-kv om-kv-money">
                            <div><dt>{t("orderManagement.drawer.subtotal")}</dt><dd className="num">{money(order.subtotal)}</dd></div>
                            {numberOf(order.discountTotal) > 0 ? (
                              <div>
                                <dt>{t("orderManagement.drawer.discount")}{order.couponCode ? ` (${order.couponCode})` : ""}</dt>
                                <dd className="num">-{money(order.discountTotal)}</dd>
                              </div>
                            ) : null}
                            <div><dt>{t("orderManagement.drawer.shippingFee")}</dt><dd className="num">{money(order.shippingFee)}</dd></div>
                            {numberOf(order.taxTotal) > 0 ? <div><dt>{t("orderManagement.drawer.tax")}</dt><dd className="num">{money(order.taxTotal)}</dd></div> : null}
                            {numberOf(order.codFee) > 0 ? <div><dt>{t("orderManagement.drawer.codFee")}</dt><dd className="num">{money(order.codFee)}</dd></div> : null}
                            <div className="total"><dt>{t("orderManagement.drawer.grandTotal")}</dt><dd className="num">{money(order.grandTotal)}</dd></div>
                            {order.paidAmount != null ? <div><dt>{t("orderManagement.refund.paid")}</dt><dd className="num">{money(order.paidAmount)}</dd></div> : null}
                            {numberOf(order.refundedAmount) > 0 ? <div><dt>{t("orderManagement.refund.refunded")}</dt><dd className="num">{money(order.refundedAmount)}</dd></div> : null}
                            {order.refundableAmount != null && numberOf(order.refundableAmount) > 0 ? (
                              <div><dt>{t("orderManagement.refund.refundable")}</dt><dd className="num">{money(order.refundableAmount)}</dd></div>
                            ) : null}
                          </dl>
                        </div>

                        <div className="sh-work-block">
                          <h3>{t("orderManagement.drawer.shipping")}</h3>
                          <dl className="cm-contact sh-compact-kv">
                            <div className="cm-contact-row">
                              <dt>{t("orderManagement.drawer.shippingMethod")}</dt>
                              <dd>{order.shipping?.methodName || "—"}</dd>
                            </div>
                            {order.shipment ? (
                              <>
                                <div className="cm-contact-row">
                                  <dt>{t("orderManagement.drawer.carrier")}</dt>
                                  <dd>{carrierName(order.shipment.carrierCode)}</dd>
                                </div>
                                <div className="cm-contact-row">
                                  <dt>{t("orderManagement.drawer.tracking")}</dt>
                                  <dd className="num">{order.shipment.trackingNumber || "—"}</dd>
                                </div>
                                {order.shipment.status ? (
                                  <div className="cm-contact-row">
                                    <dt>{t("orderManagement.drawer.shipmentStatus")}</dt>
                                    <dd>{t(`shippingPage.status.${order.shipment.status}`, { defaultValue: order.shipment.status })}</dd>
                                  </div>
                                ) : null}
                              </>
                            ) : null}
                          </dl>
                        </div>

                        {order.buyerNote ? (
                          <div className="sh-work-block">
                            <h3>{t("orderManagement.drawer.note")}</h3>
                            <p className="om-note-text">{order.buyerNote}</p>
                          </div>
                        ) : null}

                        {FEATURES.orderNotesApi ? (
                          <div className="sh-work-block">
                            <h3>{t("orderManagement.note.internalLabel")}</h3>
                            <div className="sh-note-field">
                              {/* <span className="form-label">{t("orderManagement.note.internalLabel")}</span> */}
                              <textarea
                                rows={3}
                                maxLength={2000}
                                value={noteDraft}
                                disabled={noteBusy}
                                onChange={(event) => setNoteDraft(event.target.value)}
                              />
                            </div>
                            <p className="om-muted"><span className="num">{noteDraft.length}</span>/2000</p>
                            <div className="om-work-actions">
                              <button type="button" className="sh-work-btn" disabled={noteBusy} onClick={() => void saveInternalNote()}>
                                {t("orderManagement.note.save")}
                              </button>
                              <button
                                type="button"
                                className="sh-work-btn ghost"
                                disabled={noteBusy}
                                onClick={() => setNoteDraft(order.internalNote ?? "")}
                              >
                                {t("orderManagement.note.revert")}
                              </button>
                            </div>
                          </div>
                        ) : null}

                        <div className="sh-work-block om-panel-block">
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
                        </div>

                        {showsRefundPanel(order) ? (
                          <div className="sh-work-block om-panel-block">
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
                          </div>
                        ) : null}
                      </>
                    )}
                  </div>
                ) : (
                  <div className="sh-work-pane sh-work-history" role="tabpanel">
                    {timeline.length === 0 ? (
                      <p className="sh-empty-events"><Clock3 aria-hidden="true" />{t("orderManagement.drawer.noTimeline")}</p>
                    ) : (
                      <ol className="sh-timeline">
                        {timelineNewestFirst.map((entry) => (
                          <li key={entry.id}>
                            <i aria-hidden="true" />
                            <div className="sh-timeline-card">
                              <b>{t(`orderManagement.timelineEvents.${entry.event}`, { defaultValue: entry.event })}</b>
                              {entry.toStatus ? (
                                <span>
                                  {entry.fromStatus ? `${t(`orderManagement.status.${entry.fromStatus}`, { defaultValue: entry.fromStatus })} → ` : ""}
                                  {t(`orderManagement.status.${entry.toStatus}`, { defaultValue: entry.toStatus })}
                                </span>
                              ) : null}
                              {entry.reason ? <em>{entry.reason}</em> : null}
                              <small>{formatBangkokDateTime(entry.at, localeTag)}{entry.actorType ? ` · ${entry.actorType}` : ""}</small>
                            </div>
                          </li>
                        ))}
                      </ol>
                    )}
                  </div>
                )}
              </section>
            </div>

            {actions.length > 0 ? (
              <div className="sh-actions" aria-label={t("orderManagement.drawer.actions")}>
                {(() => {
                  const ordered = [...primaryActions, ...secondaryActions, ...dangerActions];
                  if (ordered.length === 2) {
                    return (
                      <div className="sh-actions-pair" role="group">
                        {ordered.map((action, index) => renderActionButton(action, index === 0))}
                      </div>
                    );
                  }
                  const forward = [...primaryActions, ...secondaryActions];
                  return (
                    <>
                      {forward.length > 0 ? (
                        <div className="sh-actions-flow" role="group">
                          {(() => {
                            const [next, ...rest] = forward;
                            return (
                              <>
                                {renderActionButton(next, true)}
                                {rest.length > 0 ? (
                                  <div className="sh-actions-rest">
                                    {rest.map((action) => renderActionButton(action, false))}
                                  </div>
                                ) : null}
                              </>
                            );
                          })()}
                        </div>
                      ) : null}
                      {dangerActions.length > 0 ? (
                        <div className="sh-actions-exception" role="group">
                          {dangerActions.map((action) => renderActionButton(action, false))}
                        </div>
                      ) : null}
                    </>
                  );
                })()}
              </div>
            ) : null}
          </>
        ) : (
          <div className="om-drawer-body cm-detail-body">
            <div className="om-empty">{t("orderManagement.empty")}</div>
          </div>
        )}
      </aside>

      {dialog && order ? (
        <ConfirmDialog
          className={
            needsTracking ? "om-ship-confirm"
              : dialog.action === "DELIVER" ? "om-deliver-confirm"
                : undefined
          }
          title={t(actionLabelKey(dialog), { defaultValue: dialog.action })}
          message={t(`orderManagement.dialogs.${dialog.action}`, { defaultValue: t("orderManagement.dialogs.generic"), number: order.orderNumber })}
          confirmLabel={t(actionLabelKey(dialog), { defaultValue: dialog.action })}
          confirmIcon={
            needsTracking ? <Truck aria-hidden="true" />
              : dialog.action === "DELIVER" ? <PackageCheck aria-hidden="true" />
                : undefined
          }
          cancelLabel={t("orderManagement.drawer.cancel")}
          danger={ACTIONS[dialog.action]?.tone === "danger"}
          confirmDisabled={busy || dialogInvalid}
          onCancel={() => { if (!busy) setDialog(null); }}
          onConfirm={confirmDialog}
        >
          <div className={`om-dialog-fields${needsTracking ? " om-ship-dialog" : ""}${dialog.action === "DELIVER" ? " om-deliver-fields" : ""}`}>
            {dialog.action === "DELIVER" ? (
              <>
                <div className="om-deliver-order">
                  <span>{t("orderManagement.dialogs.DELIVER_ORDER")}</span>
                  <b className="num">{order.orderNumber}</b>
                </div>
                <div className="om-deliver-tracking">
                  <div className="om-deliver-tracking-row">
                    <span>{t("orderManagement.dialogs.DELIVER_TRACKING")}</span>
                    <b className="num">{deliverTrackingNumber || "—"}</b>
                  </div>
                  {order.shipment?.carrierCode ? (
                    <div className="om-deliver-tracking-row">
                      <span>{t("orderManagement.drawer.carrier")}</span>
                      <b>{carrierName(order.shipment.carrierCode)}</b>
                    </div>
                  ) : null}
                  {deliverTrackingUrl && deliverTrackingNumber ? (
                    <a
                      className="om-deliver-track-link"
                      href={deliverTrackingUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <ExternalLink aria-hidden="true" />
                      {t("orderManagement.dialogs.DELIVER_TRACK_WEB")}
                    </a>
                  ) : null}
                </div>
                <div className="om-deliver-verify">
                  <span className="om-deliver-verify-icon" aria-hidden="true"><Truck /></span>
                  <span>
                    <b>{t("orderManagement.dialogs.DELIVER_VERIFY_TITLE")}</b>
                    <small>{t("orderManagement.dialogs.DELIVER_VERIFY_HINT")}</small>
                  </span>
                </div>
              </>
            ) : null}
            {dialog.action === "MARK_PAID" ? (
              <label className="om-field">
                <span>{t("orderManagement.drawer.paymentReference")}</span>
                <input maxLength={120} value={paymentReference} onChange={(e) => setPaymentReference(e.target.value)} />
              </label>
            ) : null}
            {needsTracking ? (
              <>
                <div className="om-ship-grid">
                  {carriers.length > 0 ? (
                    <div className={`om-field om-ship-field${fieldErrors.carrierCode ? " invalid" : ""}`}>
                      <span>{t("orderManagement.drawer.carrier")} *</span>
                      <div className="om-ship-control">
                        <SearchableSelect
                          compact
                          label={t("orderManagement.drawer.carrier")}
                          options={carrierOptions.map((opt) => opt.label)}
                          value={labelOf(carrierOptions, carrierCode)}
                          placeholder={t("orderManagement.drawer.chooseCarrier")}
                          onChange={(picked) => {
                            setCarrierCode(valueOf(carrierOptions, picked));
                            clearFieldError("carrierCode");
                            clearFieldError("trackingNumber");
                            setFormError("");
                          }}
                        />
                      </div>
                      <small className={fieldErrors.carrierCode ? "om-field-error" : "om-field-hint"} role={fieldErrors.carrierCode ? "alert" : undefined}>
                        {fieldErrors.carrierCode || "\u00a0"}
                      </small>
                    </div>
                  ) : (
                    <label className={`om-field om-ship-field${fieldErrors.carrierCode ? " invalid" : ""}`}>
                      <span>{t("orderManagement.drawer.carrier")} *</span>
                      <div className="om-ship-control">
                        <input
                          value={carrierCode}
                          onChange={(e) => {
                            setCarrierCode(e.target.value.toUpperCase());
                            clearFieldError("carrierCode");
                            setFormError("");
                          }}
                        />
                      </div>
                      <small className={fieldErrors.carrierCode ? "om-field-error" : "om-field-hint"} role={fieldErrors.carrierCode ? "alert" : undefined}>
                        {fieldErrors.carrierCode || "\u00a0"}
                      </small>
                    </label>
                  )}
                  <label className={`om-field om-ship-field${fieldErrors.trackingNumber ? " invalid" : ""}`}>
                    <span>{t("orderManagement.drawer.tracking")} *</span>
                    <div className="om-ship-control">
                      <input
                        className="num"
                        maxLength={64}
                        value={trackingNumber}
                        placeholder={selectedCarrier?.code === "THAIPOST" ? "EX123456789TH" : t("orderManagement.drawer.trackingPlaceholder")}
                        aria-invalid={!!fieldErrors.trackingNumber}
                        onChange={(e) => {
                          setTrackingNumber(e.target.value);
                          clearFieldError("trackingNumber");
                          setFormError("");
                        }}
                      />
                    </div>
                    <small className={fieldErrors.trackingNumber ? "om-field-error" : "om-field-hint"} role={fieldErrors.trackingNumber ? "alert" : undefined}>
                      {fieldErrors.trackingNumber || trackingHint || "\u00a0"}
                    </small>
                  </label>
                </div>

                <OrderDateTimeField
                  value={shippedAt}
                  localeTag={localeTag}
                  title={t("orderManagement.drawer.shippedAt")}
                  hint={t("orderManagement.drawer.shippedAtHint")}
                  presetsLabel={t("orderManagement.drawer.shippedAtPresets")}
                  nowLabel={t("orderManagement.drawer.shippedAtNow")}
                  dateLabel={t("orderManagement.drawer.shippedAtDate")}
                  timeLabel={t("orderManagement.drawer.shippedAtTime")}
                  hourLabel={t("orderManagement.drawer.shippedAtHour")}
                  minuteLabel={t("orderManagement.drawer.shippedAtMinute")}
                  applyLabel={t("orderManagement.drawer.shippedAtApply")}
                  selectedLabel={t("orderManagement.drawer.shippedAtSelected")}
                  datePlaceholder={t("orderManagement.drawer.shippedAtDatePlaceholder")}
                  error={fieldErrors.shippedAt}
                  periodLabels={{
                    night: t("orderManagement.drawer.shippedAtPeriod.night"),
                    morning: t("orderManagement.drawer.shippedAtPeriod.morning"),
                    afternoon: t("orderManagement.drawer.shippedAtPeriod.afternoon"),
                    evening: t("orderManagement.drawer.shippedAtPeriod.evening"),
                  }}
                  presetLabels={{
                    "0900": t("orderManagement.drawer.shippedAtPreset.0900"),
                    "1200": t("orderManagement.drawer.shippedAtPreset.1200"),
                    "1500": t("orderManagement.drawer.shippedAtPreset.1500"),
                    "1800": t("orderManagement.drawer.shippedAtPreset.1800"),
                  }}
                  onChange={(next) => {
                    setShippedAt(next);
                    clearFieldError("shippedAt");
                    setFormError("");
                  }}
                />
              </>
            ) : null}
            {(dialog.reasonRequired || dialog.action === "CANCEL") ? (
              <label className="om-field">
                <span>{t("orderManagement.drawer.reason")}{dialog.reasonRequired ? " *" : ""}</span>
                <textarea rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
            ) : null}
            {formError && Object.keys(fieldErrors).length === 0 ? (
              dialog.action === "DELIVER" ? (
                <p className="om-deliver-alert" role="alert">
                  <AlertTriangle aria-hidden="true" />
                  <span>{formError}</span>
                </p>
              ) : (
                <p className="om-error" role="alert">{formError}</p>
              )
            ) : null}
          </div>
        </ConfirmDialog>
      ) : null}
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

  return createPortal(drawer, document.body);
}
