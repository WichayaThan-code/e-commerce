// src/components/Footer.tsx
import React from "react";
import { Link } from "react-router-dom";
import { ShoppingBag } from "lucide-react";
import { useTranslation } from "react-i18next";

interface FooterProps {
  notice?: string;
}

function Brand({ homeAria }: { homeAria: string }) {
  return (
    <Link className="brand" to="/" aria-label={homeAria}>
      <span className="brand-mark">
        <ShoppingBag size={24} />
        <i>✓</i>
      </span>
      <span>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </span>
    </Link>
  );
}

export default function Footer({ notice }: FooterProps) {
  const { t } = useTranslation("public");
  const { t: tc } = useTranslation("common");
  const homeAria = tc("brand.homeAria");
  return (
    <>
      <footer>
        <div className="page-width">
          <Brand homeAria={homeAria} />
          <p>{t("footer.tagline")}</p>
          <small>{t("footer.rights")}</small>
        </div>
      </footer>

      {notice && (
        <div className="toast" role="status">
          {notice}
        </div>
      )}
    </>
  );
}