import { useMemo } from "react";
import { Check } from "lucide-react";
import { Link } from "react-router-dom";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { mediaUrl } from "../../../services/api";
import { isLocalizedEmpty, resolveFeatureContent } from "../../../theme/blockContent";
import { BlockCtaButton, panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";

/** AG4 FEATURE: image + copy (+ bullets, CTA, optional published product link), image left or right. */
export default function FeatureSection({ shop, block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveFeatureContent(block.content), [block.content]);
  const { tp } = usePublicTranslation();
  const l = useLocalized();

  const hasCopy = !isLocalizedEmpty(content.title) || !isLocalizedEmpty(content.body) || content.bullets.length > 0;
  const image = content.mediaUrl ? mediaUrl(content.mediaUrl) : "";
  if (!hasCopy && !image) return null;

  const title = l(content.title);
  const productHref = content.productSlug ? `/shop/${encodeURIComponent(shop.slug)}/product/${encodeURIComponent(content.productSlug)}` : null;

  return (
    <section
      className={panelClasses(
        block, "shop-feature-section", `shop-feature-${content.layout === "IMAGE_RIGHT" ? "right" : "left"}`, image ? "" : "is-textonly",
      )}
      aria-label={title || tp("shop.blocks.feature.label")}
    >
      {image ? (
        <div className="shop-feature-media">
          <img src={image} alt={title} loading="lazy" />
        </div>
      ) : null}
      <div className="shop-feature-copy">
        {title ? <h2>{title}</h2> : null}
        {!isLocalizedEmpty(content.body) ? <p className="shop-feature-body">{l(content.body)}</p> : null}
        {content.bullets.length ? (
          <ul className="shop-feature-bullets">
            {content.bullets.map((bullet, index) => (
              <li key={index}>
                <Check aria-hidden="true" />
                <span>{l(bullet)}</span>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="shop-feature-actions">
          <BlockCtaButton cta={content.cta} className="shop-mall-btn is-primary shop-feature-cta" />
          {productHref ? (
            <Link className="shop-mall-btn shop-feature-product" to={productHref}>{tp("shop.blocks.feature.viewProduct")}</Link>
          ) : null}
        </div>
      </div>
    </section>
  );
}
