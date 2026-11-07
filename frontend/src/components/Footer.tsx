// src/components/Footer.tsx
import React from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import SiteBrand from "./SiteBrand";

interface FooterProps {
  notice?: string;
}

export default function Footer({ notice }: FooterProps) {
  const { t } = useTranslation("public");
  const { t: tc } = useTranslation("common");
  const homeAria = tc("brand.homeAria");
  return (
    <>
      <footer>
        <div className="page-width">
          <SiteBrand homeAria={homeAria} />
          <p>{t("footer.tagline")}</p>
          <p><Link to="/track-order">{t("footer.trackOrder")}</Link></p>
          <small>{t("footer.rights")}</small>
        </div>
      </footer>
      {notice ? <div className="toast" role="status">{notice}</div> : null}
    </>
  );
}
