import { Link } from "react-router-dom";
import BrandLogo from "./BrandLogo";

type Props = {
  /** Accessible name for the home link (i18n). Image alt stays empty to avoid duplicate announcements. */
  homeAria: string;
  className?: string;
  /** Use <a href> instead of React Router Link when needed. */
  href?: string;
};

/** Public/header/footer brand: PNG logo-with-name only (no tagline, no HTML wordmark). */
export default function SiteBrand({ homeAria, className, href }: Props) {
  const body = <BrandLogo variant="withName" alt="" />;
  const cls = `brand brand--logo${className ? ` ${className}` : ""}`;
  if (href != null) {
    return <a className={cls} href={href} aria-label={homeAria}>{body}</a>;
  }
  return <Link className={cls} to="/" aria-label={homeAria}>{body}</Link>;
}
