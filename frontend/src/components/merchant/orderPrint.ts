import { createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import type { TFunction } from "i18next";
import { merchantApi, merchantOrderApi, type MerchantSettings, type OrderDetail } from "../../services/api";
import MerchantShippingLabel, { type ShippingLabelCopy } from "./MerchantShippingLabel";
import labelCss from "../../styles/shipping-label-print.css?inline";

export function shippingLabelCopy(t: TFunction<"merchant">): ShippingLabelCopy {
  return {
    documentTitle: t("orderManagement.print.documentTitle"),
    from: t("orderManagement.print.from"),
    to: t("orderManagement.print.to"),
    note: t("orderManagement.print.note"),
    items: t("orderManagement.drawer.items"),
    noCod: t("orderManagement.print.noCod"),
    codCollect: t("orderManagement.print.codCollect"),
    printedAt: t("orderManagement.print.printedAt"),
    shipmentNo: t("orderManagement.print.shipmentNo"),
    poweredBy: t("orderManagement.print.poweredBy"),
    awaitingNote: t("orderManagement.drawer.awaitingNote"),
    addressMasked: t("orderManagement.drawer.addressMasked"),
    noItems: t("orderManagement.drawer.noItems"),
    moreItems: t("orderManagement.print.moreItems"),
  };
}

function mountPrintFrame(htmlTitle: string): {
  iframe: HTMLIFrameElement;
  frameWindow: Window;
  frameDocument: Document;
} | null {
  const iframe = document.createElement("iframe");
  iframe.setAttribute("aria-hidden", "true");
  iframe.title = "print";
  Object.assign(iframe.style, {
    position: "fixed",
    right: "0",
    bottom: "0",
    width: "0",
    height: "0",
    border: "0",
    opacity: "0",
    pointerEvents: "none",
  });
  document.body.appendChild(iframe);

  const frameWindow = iframe.contentWindow;
  const frameDocument = iframe.contentDocument ?? frameWindow?.document;
  if (!frameWindow || !frameDocument) {
    iframe.remove();
    return null;
  }

  frameDocument.open();
  frameDocument.write(`<!doctype html><html><head><meta charset="utf-8" /><title>${htmlTitle}</title></head><body></body></html>`);
  frameDocument.close();

  const style = frameDocument.createElement("style");
  style.textContent = labelCss;
  frameDocument.head.appendChild(style);

  return { iframe, frameWindow, frameDocument };
}

function printMountedLabel(
  frame: { iframe: HTMLIFrameElement; frameWindow: Window; frameDocument: Document },
  root: Root,
) {
  const trigger = () => {
    try {
      frame.frameWindow.focus();
      frame.frameWindow.print();
    } catch {
      /* user cancelled */
    } finally {
      window.setTimeout(() => {
        try {
          root.unmount();
        } catch {
          /* ignore */
        }
        frame.iframe.remove();
      }, 800);
    }
  };
  // Wait for QR/barcode paint before opening the system print dialog.
  window.setTimeout(trigger, 220);
}

export async function printMerchantOrder(
  shopId: number,
  orderOrId: string | OrderDetail,
  t: TFunction<"merchant">,
  localeTag: string,
) {
  const [order, shop] = await Promise.all([
    typeof orderOrId === "string" ? merchantOrderApi.get(shopId, orderOrId) : Promise.resolve(orderOrId),
    merchantApi.getSettings(shopId).catch(() => null as MerchantSettings | null),
  ]);

  const copy = shippingLabelCopy(t);
  const title = `${copy.documentTitle} · ${order.orderNumber}`;
  const frame = mountPrintFrame(title.replace(/[<>&"]/g, ""));
  if (!frame) throw new Error("PRINT_POPUP_BLOCKED");

  const mount = frame.frameDocument.body;
  const root = createRoot(mount);
  root.render(
    createElement(MerchantShippingLabel, {
      order,
      shop,
      copy,
      localeTag,
    }),
  );
  printMountedLabel(frame, root);
  return order;
}
