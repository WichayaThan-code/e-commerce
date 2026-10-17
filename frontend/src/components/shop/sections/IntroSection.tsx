import { MapPin, Store } from "lucide-react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import type { ShopSectionProps } from "./types";

export default function IntroSection({ shop, config }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const address = [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode].filter(Boolean).join(" ");
  const area = [shop.district, shop.province].filter(Boolean).join(" ") || "—";
  const approved = shop.status === "APPROVED";
  const statusLabel =
    shop.status === "APPROVED"
      ? tp("shop.statusApproved")
      : shop.status === "REJECTED"
        ? tp("shop.statusRejected")
        : shop.status === "SUSPENDED"
          ? tp("shop.statusSuspended")
          : tp("shop.statusPending");
  // The hero shows the description unless the merchant wrote a custom subtitle.
  const showDescription = Boolean(config.hero.subtitle.trim() && shop.description);

  return (
    <>
      {showDescription && <p className="shop-intro-text recommend-width">{shop.description}</p>}
      <ul className="shop-facts recommend-width">
        <li>
          <MapPin />
          <span>{address || tp("common.noAddress")}</span>
        </li>
        <li>
          <Store />
          <b>{tp("common.area")}</b>
          <span>{area}</span>
        </li>
        <li>
          <b>{tp("common.status")}</b>
          <span className={`shop-status-badge ${approved ? "is-success" : "is-danger"}`}>{statusLabel}</span>
        </li>
      </ul>
    </>
  );
}
