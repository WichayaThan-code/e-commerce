import { useEffect, useMemo, useState } from "react";
import { Copy, ExternalLink, Package, Truck } from "lucide-react";
import { Skeleton } from "../feedback/Skeleton";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  ApiError,
  buyerOrderApi,
  carrierApi,
  type Carrier,
  type OrderDetail,
  type ShipmentDetail,
} from "../../services/api";

const SHOW_STATUSES = new Set([
  "DISPATCHED", "DELIVERED", "COMPLETED", "REFUND_REQUESTED", "REFUNDED",
]);

/** Workflow order: รอแพ็ก → กำลังแพ็ก → แพ็กแล้ว → ออกจากร้าน → … (higher = later). */
const STATUS_RANK: Record<string, number> = {
  READY_TO_PACK: 10,
  PACKING: 20,
  PACKED: 30,
  DISPATCHED: 40,
  IN_TRANSIT: 50,
  OUT_FOR_DELIVERY: 60,
  DELIVERED: 70,
  DELIVERY_FAILED: 65,
  RETURNED: 80,
  CANCELLED: 90,
};

function statusRank(status?: string | null, eventType?: string | null) {
  if (status && STATUS_RANK[status] != null) return STATUS_RANK[status];
  if (eventType && STATUS_RANK[eventType] != null) return STATUS_RANK[eventType];
  return 0;
}

export function shouldShowShipmentCard(order: OrderDetail) {
  return SHOW_STATUSES.has(order.status) || Boolean(order.shipment?.trackingNumber);
}

function when(value: string | null | undefined, localeTag: string) {
  if (!value) return "";
  return new Date(value).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
}

function cleanDetail(text: string | null | undefined, tracking?: string | null) {
  if (!text) return "";
  let next = text.trim();
  if (tracking) {
    next = next.replace(tracking, "").replace(/\s{2,}/g, " ").trim();
  }
  return next;
}

type Props = { order: OrderDetail };

export default function OrderShipmentCard({ order }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const isEn = localeTag.toLowerCase().startsWith("en");
  const [shipment, setShipment] = useState<ShipmentDetail | null | undefined>(undefined);
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [failed, setFailed] = useState(false);
  const [empty, setEmpty] = useState(false);
  const [reload, setReload] = useState(0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let alive = true;
    setFailed(false);
    setEmpty(false);
    setShipment(undefined);
    Promise.all([
      buyerOrderApi.shipment(order.id).catch((error) => {
        if (error instanceof ApiError && (error.status === 404 || error.code === "SHIPMENT_NOT_FOUND")) {
          return null;
        }
        throw error;
      }),
      carrierApi.list().catch(() => [] as Carrier[]),
    ])
      .then(([detail, list]) => {
        if (!alive) return;
        setCarriers(list);
        if (detail == null) {
          setEmpty(true);
          setShipment(null);
        } else {
          setShipment(detail);
        }
      })
      .catch(() => {
        if (alive) setFailed(true);
      });
    return () => { alive = false; };
  }, [order.id, order.status, order.version, reload]);

  const carrierName = useMemo(() => {
    if (!shipment) return "";
    if (shipment.carrierName) return shipment.carrierName;
    const code = shipment.carrierCode;
    if (!code) return "";
    const row = carriers.find((c) => c.code === code);
    if (!row) return code;
    return isEn ? row.nameEn : row.nameTh;
  }, [shipment, carriers, isEn]);

  const events = useMemo(() => {
    const list = [...(shipment?.events ?? [])];
    // Newest time first; same timestamp → later workflow step first
    // (รอแพ็ก → กำลังแพ็ก → แพ็กแล้ว → ออกจากร้าน, so ออกจากร้าน stays on top).
    list.sort((a, b) => {
      const byTime = b.eventAt.localeCompare(a.eventAt);
      if (byTime !== 0) return byTime;
      const byStatus = statusRank(b.status, b.eventType) - statusRank(a.status, a.eventType);
      if (byStatus !== 0) return byStatus;
      return b.id - a.id;
    });
    return list;
  }, [shipment]);

  const copyTracking = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  const statusLabel = shipment
    ? tp(`orders.shipment.statusValue.${shipment.status}`, { defaultValue: shipment.status })
    : "";

  return (
    <article className="checkout-card orders-shipment-card">
      <header className="orders-ship-head">
        <span className="orders-ship-ico" aria-hidden="true"><Truck size={18} /></span>
        <h2>{tp("orders.shipment.title")}</h2>
      </header>
      {shipment === undefined && !failed ? (
        <div aria-busy="true" className="orders-ship-skel">
          <Skeleton height={56} />
          <Skeleton height={18} />
          <Skeleton height={96} />
        </div>
      ) : failed ? (
        <div className="orders-card-state" role="alert">
          <p>{tp("orders.shipment.loadError")}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>{tp("cart.retry")}</button>
        </div>
      ) : empty || !shipment ? (
        <div className="orders-card-state" role="status">
          <Package size={20} />
          <p>{tp("orders.shipment.empty")}</p>
        </div>
      ) : (
        <>
          <div className="orders-ship-summary">
            <div className="orders-ship-status">
              <span>{tp("orders.shipment.status")}</span>
              <b>{statusLabel}</b>
            </div>
            {carrierName ? (
              <div className="orders-ship-carrier">
                <span>{tp("orders.shipment.carrier")}</span>
                <b>{carrierName}</b>
              </div>
            ) : null}
            {shipment.trackingNumber ? (
              <div className="orders-ship-tracking">
                <span>{tp("orders.tracking")}</span>
                <div className="orders-ship-tracking-row">
                  <b className="num">{shipment.trackingNumber}</b>
                  <button type="button" onClick={() => void copyTracking(shipment.trackingNumber!)}>
                    <Copy size={14} aria-hidden="true" />
                    {copied ? tp("orders.copied") : tp("orders.copy")}
                  </button>
                  {shipment.trackingUrl ? (
                    <a href={shipment.trackingUrl} target="_blank" rel="noopener noreferrer">
                      <ExternalLink size={14} aria-hidden="true" />
                      {tp("orders.shipment.trackWeb")}
                    </a>
                  ) : null}
                </div>
              </div>
            ) : null}
          </div>

          {events.length > 0 ? (
            <ol className="orders-ship-timeline">
              {events.map((event, index) => {
                const title = event.status
                  ? tp(`orders.shipment.statusValue.${event.status}`, { defaultValue: event.status })
                  : event.eventType;
                const detail = cleanDetail(event.note || event.location, shipment.trackingNumber);
                const locationOnly = event.location && event.note !== event.location
                  ? cleanDetail(event.location, shipment.trackingNumber)
                  : "";
                return (
                  <li key={event.id} className={index === 0 ? "is-latest" : undefined}>
                    <span className="orders-ship-dot" aria-hidden="true" />
                    <div className="orders-ship-event">
                      <time dateTime={event.eventAt}>{when(event.eventAt, localeTag)}</time>
                      <b>{title}</b>
                      {detail && detail !== title ? <small>{detail}</small> : null}
                      {locationOnly && locationOnly !== detail ? <small>{locationOnly}</small> : null}
                    </div>
                  </li>
                );
              })}
            </ol>
          ) : null}
        </>
      )}
    </article>
  );
}
