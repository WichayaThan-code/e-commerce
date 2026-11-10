import { TsfCatalogPage } from "../../components/shop/general/GeneralStorefront";
import { usesTsfTree } from "../../theme/shopTheme";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Search, SearchX } from "lucide-react";
import EmptyState from "../../components/feedback/EmptyState";
import CatalogSection from "../../components/shop/sections/CatalogSection";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { useShopCatalog } from "../../hooks/useShopCatalog";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { shopSearchPath } from "../../services/shopRoutes";

export default function ShopSearchPage() {
  const { slug, shop, theme } = useShopPublic();
  const { tp } = usePublicTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const q = searchParams.get("q")?.trim() ?? "";
  const [draft, setDraft] = useState(q);
  const label = tp("shop.search.title");
  useShopCrumb(label);
  useDocumentMeta({ title: `${label} · ${shop.name}` });

  useEffect(() => {
    setDraft(q);
  }, [q]);

  const { products, catalog } = useShopCatalog({ slug, q, enabled: q.length > 0 });
  const catalogSection = theme.config.sections.find((item) => item.key === "CATALOG");

  if (usesTsfTree(theme.templateKey)) {
    const form = (
      <div className="tsf-wrap" style={{ paddingTop: 24 }}>
        <form className="tsf-search-form" role="search" onSubmit={(event) => { event.preventDefault(); navigate(shopSearchPath(slug, draft)); }}>
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={tp("shop.search.placeholder")} aria-label={tp("shop.search.title")} />
          <button type="submit" className="tsf-btn tsf-btn-primary" aria-label={tp("shop.search.title")}><Search size={18} /></button>
        </form>
      </div>
    );
    if (q.length === 0) {
      return <main>{form}<div className="tsf-wrap"><EmptyState icon={Search} title={tp("shop.search.prompt")} /></div></main>;
    }
    if (catalog.productState === "ok" && products.length === 0) {
      return <main>{form}<div className="tsf-wrap"><EmptyState variant="filtered" icon={SearchX} title={tp("shop.search.empty", { q })} /></div></main>;
    }
    return <TsfCatalogPage shop={shop} title={`${label}: “${q}”`} products={products} catalog={catalog} showRail={false} before={form} />;
  }

  return (
    <>
      <section className="recommend-width" style={{ marginBottom: "var(--shop-gap, 12px)" }}>
        <form
          className="shop-brand-search"
          onSubmit={(event) => {
            event.preventDefault();
            navigate(shopSearchPath(slug, draft));
          }}
        >
          <Search />
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={tp("shop.search.placeholder")}
            aria-label={tp("shop.search.title")}
          />
        </form>
      </section>

      {q.length === 0 && (
        <section className="recommend-width">
          <EmptyState icon={Search} title={tp("shop.search.prompt")} />
        </section>
      )}

      {q.length > 0 && catalog.productState === "ok" && products.length === 0 && (
        <section className="recommend-width">
          <EmptyState variant="filtered" icon={SearchX} title={tp("shop.search.empty", { q })} />
        </section>
      )}

      {q.length > 0 && (products.length > 0 || catalog.productState !== "ok") && (
        <CatalogSection
          shop={shop}
          config={theme.config}
          section={{ key: "CATALOG", enabled: true, variant: catalogSection?.variant ?? null }}
          products={products}
          catalog={catalog}
          preview={false}
        />
      )}
    </>
  );
}
