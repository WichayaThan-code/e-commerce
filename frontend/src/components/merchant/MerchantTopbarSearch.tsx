import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { usePermissions } from "../../hooks/usePermissions";

const PRODUCTS_PATH = "/merchant/products";

/**
 * Merchant top-bar search: searches the active shop's own products through the existing merchant products page
 * (`/merchant/products?q=` → GET /api/merchant/shops/{shopId}/products?q=, shop-permission scoped).
 * Orders/customers have no search API yet, so they are not offered here.
 */
export default function MerchantTopbarSearch() {
  const { t } = useTranslation("merchant");
  const { canVisit } = usePermissions();
  const navigate = useNavigate();
  const location = useLocation();
  const onProducts = location.pathname === PRODUCTS_PATH;
  const current = onProducts ? new URLSearchParams(location.search).get("q") ?? "" : "";
  const [query, setQuery] = useState(current);
  const allowed = canVisit(PRODUCTS_PATH);

  useEffect(() => {
    setQuery(current);
  }, [current]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!allowed) return;
    const q = query.trim();
    navigate(q ? `${PRODUCTS_PATH}?q=${encodeURIComponent(q)}` : PRODUCTS_PATH);
  };

  return (
    <form onSubmit={submit} role="search">
      <Search />
      <input
        enterKeyHint="search"
        aria-label={t("catalog.menu.searchPh")}
        placeholder={t("catalog.menu.searchPh")}
        value={query}
        disabled={!allowed}
        onChange={(event) => setQuery(event.target.value)}
      />
    </form>
  );
}
