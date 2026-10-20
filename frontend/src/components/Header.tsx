import React, { useState, useRef, useEffect } from "react";
import { Link, useLocation, useMatch, useNavigate } from "react-router-dom";
import Navbar from "./Navbar";
import { useAuth } from "../contexts/AuthContext";
import { useCart } from "../contexts/CartContext";
import { accountKind } from "../services/permissions";
import { marketplaceSearchPath, shopSearchPath } from "../services/shopRoutes";
import { useTranslation } from "react-i18next";
import LanguageSwitcher from "./LanguageSwitcher";
import {
  Menu, Search, ShoppingCart, UserRound, ChevronDown,
  ChevronRight, Gift, Headphones, LogOut, X, ShoppingBag, Store
} from "lucide-react";

function Brand({ homeAria }: { homeAria: string }) {
  return (
    <Link className="brand" to="/" aria-label={homeAria}>
      <span className="brand-mark">
        <ShoppingBag size={24} />
        <i>✓</i>
      </span>
      <span>
        <b>ThaiShop<span>Fun</span></b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </span>
    </Link>
  );
}

interface HeaderProps {
  hideNavbar?: boolean;
  hideSearch?: boolean;
}

export default function Header({ hideNavbar = false, hideSearch = false }: HeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const { user, loading, logout } = useAuth();
  const { count, access: cartAccess } = useCart();
  const { t } = useTranslation("common");
  const { t: ta } = useTranslation("auth");
  const navigate = useNavigate();
  const location = useLocation();
  const shopExact = useMatch("/shop/:slug");
  const shopNested = useMatch("/shop/:slug/*");
  const shopMatch = shopNested ?? shopExact;
  const homeAria = t("brand.homeAria");
  const accountRef = useRef<HTMLDivElement>(null);
  const accountButton = useRef<HTMLButtonElement>(null);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    const onShopSearch = Boolean(shopMatch?.params.slug) && location.pathname.includes("/search");
    const onMarketplaceSearch = location.pathname === "/search";
    if (!onShopSearch && !onMarketplaceSearch) return;
    const q = new URLSearchParams(location.search).get("q") ?? "";
    setQuery(q);
  }, [shopMatch?.params.slug, location.pathname, location.search]);

  useEffect(() => {
    if (!accountOpen) return;
    const outside = (event: PointerEvent) => {
      if (!accountRef.current?.contains(event.target as Node)) setAccountOpen(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [accountOpen]);

  const signOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try { await logout(); setAccountOpen(false); }
    finally { setSigningOut(false); }
  };

  const dashboardPath = accountKind(user) === "ADMIN" ? "/admin" : accountKind(user) === "MERCHANT" ? "/merchant/dashboard" : "/";
  const accountName = user ? `${user.firstName || ""} ${user.lastName || ""}`.trim() || user.email : "";

  return (
    <header>
      <div className="topbar page-width">
        <button className="mobile-menu" onClick={() => setMenuOpen(true)} aria-label={t('menu.open')}>
          <Menu />
        </button>
        <Brand homeAria={homeAria} />

        {!hideSearch && (
          <form
            className="search"
            onSubmit={(e) => {
              e.preventDefault();
              const shopSlug = shopMatch?.params.slug;
              const trimmed = query.trim();
              if (!trimmed) return;
              // inside a storefront the header keeps searching that shop; everywhere else it searches the marketplace
              navigate(shopSlug ? shopSearchPath(shopSlug, trimmed) : marketplaceSearchPath(trimmed));
            }}
          >
            <input
              enterKeyHint="search"
              aria-label={t('search.action')}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('search.placeholder')}
            />
            <button aria-label={t('search.action')}><Search size={21} /></button>
          </form>
        )}
        
        <div className={`account-actions ${user ? "authenticated" : ""}`}>
          <LanguageSwitcher />
          {loading ? (
            <span className="account-loading" />
          ) : user ? (
            <div className="account-profile-wrap" ref={accountRef}>
              <button
                className="account-profile"
                ref={accountButton}
                onClick={() => setAccountOpen((open) => !open)}
              >
                <span className="member-avatar">{(user.firstName || user.email).slice(0, 1).toUpperCase()}</span>
                <p>
                  <b>{accountName}</b>
                  <small>{accountKind(user) === "CUSTOMER" ? t('role.customer') : accountKind(user) === "MERCHANT" ? t('role.merchant') : t('role.admin')}</small>
                </p>
                <ChevronDown />
              </button>
              {accountOpen && (
                <div className="account-menu">
                  <div className="member-menu-heading">
                    <b>{accountName}</b><span>{user.email}</span>
                  </div>
                  {accountKind(user) !== "CUSTOMER" && (
                    <Link to={dashboardPath}><Store /> {accountKind(user) === "ADMIN" ? t('account.admin') : t('account.store')} <ChevronRight /></Link>
                  )}
                  <Link to="/cart"><ShoppingCart /> {t('account.cart')} <ChevronRight /></Link>
                  <Link to="/deals"><Gift /> {t('account.deals')} <ChevronRight /></Link>
                  <Link to="/help"><Headphones /> {t('account.help')} <ChevronRight /></Link>
                  <button className="member-signout" disabled={signingOut} onClick={() => void signOut()}>
                    <LogOut /> {signingOut ? t('account.loggingOut') : t('account.logout')}
                  </button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link className="login" to="/login"><UserRound size={22} /> {ta('login')}</Link>
              <Link className="outline" to="/signup">{ta('signup')}</Link>
            </>
          )}
          <Link className="cart" to="/cart" aria-label={t('account.cart')}><ShoppingCart />{cartAccess === "buyer" && <em>{count}</em>}</Link>
        </div>
      </div>

      {!hideNavbar && <Navbar />}

      {menuOpen && (
        <div className="drawer-wrap" onClick={() => setMenuOpen(false)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <button className="drawer-close" onClick={() => setMenuOpen(false)}><X /></button>
            <Brand homeAria={homeAria} />
            <Link to="/#categories" onClick={() => setMenuOpen(false)}>{t('nav.categories')} <ChevronRight size={17} /></Link>
            <Link to="/deals" onClick={() => setMenuOpen(false)}>{t('nav.deals')} <ChevronRight size={17} /></Link>
            <Link to="/recommendations" onClick={() => setMenuOpen(false)}>{t('nav.recommended')} <ChevronRight size={17} /></Link>
            <Link to="/shops" onClick={() => setMenuOpen(false)}>{t('nav.shops')} <ChevronRight size={17} /></Link>
            <Link to="/best-sellers" onClick={() => setMenuOpen(false)}>{t('nav.best')} <ChevronRight size={17} /></Link>
            <Link to="/thai-brands" onClick={() => setMenuOpen(false)}>{t('nav.brands')} <ChevronRight size={17} /></Link>
            <Link to="/articles" onClick={() => setMenuOpen(false)}>{t('nav.articles')} <ChevronRight size={17} /></Link>
            <Link to="/help" onClick={() => setMenuOpen(false)}>{t('nav.help')} <ChevronRight size={17} /></Link>
          </aside>
        </div>
      )}
    </header>
  );
}