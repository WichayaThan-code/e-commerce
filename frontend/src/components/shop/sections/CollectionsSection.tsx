import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { isLocalizedEmpty, resolveCollectionsContent } from "../../../theme/blockContent";
import { publicCollectionApi, type PublicCollection } from "../../../services/collectionsApi";
import { shopCollectionPath } from "../../../services/shopRoutes";
import { mediaUrl } from "../../../services/api";
import { BlockHeading, panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";
import "../../../styles/shop-collections.css";

/**
 * V41 COLLECTIONS (CUSTOM only): cards of the shop's published collections. Data comes from the public
 * `/api/shops/{slug}/collections` endpoint only; merchant text is rendered as plain text.
 */
export default function CollectionsSection({ shop, block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveCollectionsContent(block.content), [block.content]);
  const { tp, localeTag } = usePublicTranslation();
  const l = useLocalized();
  const [rows, setRows] = useState<PublicCollection[] | null>(null);

  useEffect(() => {
    let alive = true;
    publicCollectionApi.list(shop.slug, localeTag.slice(0, 2))
      .then((list) => { if (alive) setRows(list); })
      .catch(() => { if (alive) setRows([]); });
    return () => { alive = false; };
  }, [shop.slug, localeTag]);

  const cards = useMemo(() => {
    if (!rows) return [];
    const picked = content.collectionSlugs.length
      ? content.collectionSlugs.map((slug) => rows.find((row) => row.slug === slug)).filter((row): row is PublicCollection => !!row)
      : rows;
    return picked.slice(0, content.limit);
  }, [rows, content.collectionSlugs, content.limit]);

  if (rows && !cards.length && isLocalizedEmpty(content.title)) return null;

  return (
    <section className={panelClasses(block, "shop-collection-section")} aria-label={l(content.title) || tp("shop.blocks.collections.label")}>
      <BlockHeading title={content.title} subtitle={content.subtitle} prefix="shop-collection" />
      {rows == null ? (
        <p className="shop-collection-empty">{tp("shop.blocks.collections.loading")}</p>
      ) : cards.length ? (
        <ul className="shop-collection-grid">
          {cards.map((card) => (
            <li key={card.slug} className="shop-collection-card">
              <Link to={shopCollectionPath(shop.slug, card.slug)} className="shop-collection-link">
                <span className="shop-collection-cover">
                  {card.coverUrl ? <img className="shop-collection-cover" src={mediaUrl(card.coverUrl)} alt="" loading="lazy" /> : <span className="shop-collection-cover shop-collection-cover-fallback" aria-hidden="true">{card.name.slice(0, 1)}</span>}
                </span>
                <span className="shop-collection-name">{card.name}</span>
                <span className="shop-collection-count">{tp("shop.blocks.collections.count", { count: card.productCount })}</span>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="shop-collection-empty">{tp("shop.blocks.collections.empty")}</p>
      )}
    </section>
  );
}
