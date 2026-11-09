import { QRCodeSVG } from "qrcode.react";
import { barcode128Svg } from "./barcode128";
import type { MerchantSettings, OrderDetail } from "../../services/api";
import { formatBangkokDateTime } from "./orderFormat";

export type ShippingLabelCopy = {
  documentTitle: string;
  from: string;
  to: string;
  note: string;
  items: string;
  noCod: string;
  codCollect: string;
  printedAt: string;
  shipmentNo: string;
  poweredBy: string;
  awaitingNote: string;
  addressMasked: string;
  noItems: string;
  moreItems: string;
};

const ITEM_LIMIT = 5;

type Props = {
  order: OrderDetail;
  shop: MerchantSettings | null;
  copy: ShippingLabelCopy;
  localeTag: string;
};

function joinAddress(parts: Array<string | null | undefined>) {
  return parts.map((part) => (part ?? "").trim()).filter(Boolean).join(" ");
}

function sortCode(postalCode?: string | null, district?: string | null) {
  const postal = (postalCode ?? "").replace(/\D/g, "");
  if (postal.length >= 2) return postal.slice(0, 2);
  const dist = (district ?? "").trim();
  if (dist) return dist.slice(0, 2).toUpperCase();
  return "—";
}

function destinationLine(order: OrderDetail) {
  const a = order.shippingAddress;
  if (!a) return "DST —";
  const place = (a.subdistrict || a.district || a.province || "").trim();
  return place ? `DST ${place}` : "DST —";
}

export default function MerchantShippingLabel({ order, shop, copy, localeTag }: Props) {
  const awaiting = order.awaitingAcceptance === true || order.itemsHidden === true;
  const isCod = order.paymentMethod === "COD";
  const tracking = (order.shipment?.trackingNumber || "").trim();
  const orderNo = order.orderNumber;
  const primaryCode = tracking || orderNo;
  const routeCode = tracking || orderNo;
  const carrier =
    order.shipment?.carrierCode ||
    order.shipping?.carrierCode ||
    order.shipping?.methodName ||
    "—";
  const shipmentId = tracking || orderNo;
  const qrPayload = tracking
    ? `TRACK:${tracking}|ORDER:${orderNo}`
    : `ORDER:${orderNo}|SHOP:${order.shopId}`;

  const senderName = shop?.shopName || order.shopName;
  const senderPhone = shop?.publicPhone || shop?.phone || "";
  const senderAddress = shop
    ? joinAddress([shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode])
    : "";

  const addr = order.shippingAddress;
  const recipientName = addr?.recipientName || "";
  const recipientPhone = addr?.phone || "";
  const recipientAddress = addr
    ? joinAddress([
        addr.addressLine1,
        addr.addressLine2,
        addr.subdistrict,
        addr.district,
        addr.province,
        addr.postalCode,
      ])
    : "";

  const items = order.items ?? [];
  const visibleItems = items.slice(0, ITEM_LIMIT);
  const hiddenItemCount = Math.max(0, items.length - visibleItems.length);
  const topBarcode = barcode128Svg(primaryCode, { height: 36, module: 1.15 });
  const shipBarcode = barcode128Svg(shipmentId, { height: 32, module: 1.05 });
  const printedAt = formatBangkokDateTime(new Date().toISOString(), localeTag);
  const sort = sortCode(addr?.postalCode, addr?.district);

  return (
    <div className="sl-page">
      <article className="sl-label" aria-label={copy.documentTitle}>
        <header className="sl-top">
          <p className="sl-route">{routeCode}</p>
          <div
            className="sl-top-barcode"
            dangerouslySetInnerHTML={{ __html: topBarcode }}
          />
          <p className="sl-track">{primaryCode}</p>
          <p className="sl-dst">{destinationLine(order)}</p>
        </header>

        <div className={`sl-pay${isCod ? " is-cod" : ""}`}>
          {isCod ? copy.codCollect : copy.noCod}
          {isCod ? ` ${formatMoney(order.grandTotal, localeTag)}` : ""}
        </div>

        <section className="sl-party">
          <div className="sl-party-main">
            <div className="sl-block">
              <p className="sl-block-title">{copy.from}</p>
              <p className="sl-name">{senderName || "—"}</p>
              {senderPhone ? <p className="sl-phone">{senderPhone}</p> : null}
              {senderAddress ? <p className="sl-addr">{senderAddress}</p> : null}
            </div>
            <div className="sl-block is-to">
              <p className="sl-block-title">{copy.to}</p>
              {awaiting ? (
                <p className="sl-warn">{copy.awaitingNote}</p>
              ) : addr?.masked ? (
                <p className="sl-warn">{copy.addressMasked}</p>
              ) : (
                <>
                  <p className="sl-name">{recipientName || "—"}</p>
                  {recipientPhone ? <p className="sl-phone">{recipientPhone}</p> : null}
                  {recipientAddress ? <p className="sl-addr">{recipientAddress}</p> : null}
                </>
              )}
            </div>
          </div>
          <aside className="sl-party-side">
            <div className="sl-sort" aria-hidden="true">{sort}</div>
            <QRCodeSVG value={qrPayload} size={60} level="M" includeMargin={false} />
          </aside>
        </section>

        <section className="sl-ship">
          <div className="sl-carrier">
            <p className="sl-carrier-name">{String(carrier)}</p>
            <p className="sl-brand">ThaiShopFun</p>
          </div>
          <div className="sl-ship-barcode">
            <div dangerouslySetInnerHTML={{ __html: shipBarcode }} />
            <p className="sl-ship-no">
              {copy.shipmentNo} {shipmentId}
            </p>
          </div>
        </section>

        <section className="sl-detail">
          {!awaiting && order.buyerNote?.trim() ? (
            <div className="sl-note">
              <h2>{copy.note}</h2>
              <p>{order.buyerNote.trim()}</p>
            </div>
          ) : null}
          <div className="sl-items">
            <h2>{copy.items}</h2>
            {awaiting ? (
              <p className="sl-warn">{copy.awaitingNote}</p>
            ) : items.length === 0 ? (
              <p>{copy.noItems}</p>
            ) : (
              <>
                <ol>
                  {visibleItems.map((item) => (
                    <li key={item.id}>
                      {item.productName}
                      {item.optionLabel ? ` (${item.optionLabel})` : ""}
                      {" / "}
                      ({item.quantity})
                    </li>
                  ))}
                </ol>
                {hiddenItemCount > 0 ? (
                  <p className="sl-more">{copy.moreItems.replace("{{count}}", String(hiddenItemCount))}</p>
                ) : null}
              </>
            )}
          </div>
        </section>

        <footer className="sl-foot">
          <span>
            {copy.printedAt} {printedAt}
          </span>
          <span>{copy.poweredBy}</span>
        </footer>
      </article>
    </div>
  );
}

function formatMoney(value: number | string | null | undefined, localeTag: string) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return "";
  const tag = localeTag.startsWith("en") ? "en-US" : localeTag.startsWith("zh") ? "zh-CN" : "th-TH";
  const fraction = Math.round(amount * 100) % 100 !== 0;
  return `฿${new Intl.NumberFormat(tag, {
    minimumFractionDigits: fraction ? 2 : 0,
    maximumFractionDigits: 2,
  }).format(amount)}`;
}
