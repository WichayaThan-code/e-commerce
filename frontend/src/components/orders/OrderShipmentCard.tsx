import { useEffect, useMemo, useState } from "react";
import { Copy, ExternalLink, Package } from "lucide-react";
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

export function shouldShowShipmentCard(order: OrderDetail) {
  return SHOW_STATUSES.has(order.status) || Boolean(order.shipment?.trackingNumber);
}

function when(value: string | null | undefined, localeTag: string) {
  if (!value) return "";
  return new Date(value).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
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
    list.sort((a, b) => b.eventAt.localeCompare(a.eventAt));
    return list;
  }, [shipment]);

  const copyTracking = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  return (
    <article className="checkout-card orders-shipment-card">
      <h2>{tp("orders.shipment.title")}</h2>
      {shipment === undefined && !failed ? (
        <div aria-busy="true">
          <Skeleton height={18} />
          <Skeleton height={18} />
          <Skeleton height={80} />
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
          <p className="checkout-meta">
            <span>{tp("orders.shipment.status")}</span>
            <b>{tp(`orders.shipment.statusValue.${shipment.status}`, { defaultValue: shipment.status })}</b>
          </p>
          {carrierName ? (
            <p className="checkout-meta">
              <span>{tp("orders.shipment.carrier")}</span>
              <b>{carrierName}</b>
            </p>
          ) : null}
          {shipment.trackingNumber ? (
            <div className="orders-tracking">
              <span>{tp("orders.tracking")}</span>
              <b className="num">{shipment.trackingNumber}</b>
              <button type="button" onClick={() => void copyTracking(shipment.trackingNumber!)}>
                <Copy size={14} /> {copied ? tp("orders.copied") : tp("orders.copy")}
              </button>
              {shipment.trackingUrl ? (
                <a
                  className="checkout-secondary"
                  href={shipment.trackingUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink size={14} /> {tp("orders.shipment.trackWeb")}
                </a>
              ) : null}
            </div>
          ) : null}
          {events.length > 0 ? (
            <ol className="orders-timeline orders-shipment-events">
              {events.map((event) => (
                <li key={event.id}>
                  <time dateTime={event.eventAt}>{when(event.eventAt, localeTag)}</time>
                  <b>
                    {event.status
                      ? tp(`orders.shipment.statusValue.${event.status}`, { defaultValue: event.status })
                      : event.eventType}
                  </b>
                  {event.location ? <small>{event.location}</small> : null}
                  {event.note ? <small>{event.note}</small> : null}
                </li>
              ))}
            </ol>
          ) : null}
        </>
      )}
    </article>
  );
}
