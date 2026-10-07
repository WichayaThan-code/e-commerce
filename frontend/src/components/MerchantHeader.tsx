import { useEffect, useRef, useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  Gift,
  Headphones,
  LogOut,
  Search,
  ShoppingBag,
  ShoppingCart,
  Store,
  UserRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { accountKind } from "../services/permissions";
import { useLanguage } from "../contexts/LanguageContext";
import LanguageSwitcher from "./LanguageSwitcher";
import "../styles/merchant-signup.css";

function MerchantBrand() {
  return (
    <Link className="brand" to="/" aria-label="ThaiShopFun หน้าแรก">
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

export default function MerchantHeader() {
  const { user, loading, logout } = useAuth();
  const { t } = useLanguage();
  const [accountOpen, setAccountOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const accountRef = useRef<HTMLDivElement>(null);
  const accountButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!accountOpen) return;
    const outside = (event: PointerEvent) => {
      if (!accountRef.current?.contains(event.target as Node)) {
        setAccountOpen(false);
      }
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [accountOpen]);

  const signOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await logout();
      setAccountOpen(false);
    } finally {
      setSigningOut(false);
    }
  };

  const dashboardPath =
    accountKind(user) === "ADMIN"
      ? "/admin"
      : accountKind(user) === "MERCHANT"
      ? "/merchant/dashboard"
      : "/";
  const accountName = user
    ? `${user.firstName || ""} ${user.lastName || ""}`.trim() || user.email
    : "";

  return (
    <header className="merchant-header">
      <MerchantBrand />
      <nav>
        <a href="/">{t('merchant.home')}</a>
        <a href="/#recommended">{t('merchant.products')}</a>
        <a href="/shops">{t('merchant.shops')}</a>
        <a className="active" href="/merchant/register">
          {t('merchant.apply')}
        </a>
        <a href="/help">{t('merchant.help')}</a>
      </nav>

      <div className="merchant-actions">
        <button aria-label={t('search.action')} type="button">
          <Search />
        </button>
        <LanguageSwitcher />

        <div className={`account-actions ${user ? "authenticated" : ""}`}>
          {loading ? (
            <span className="account-loading" />
          ) : user ? (
            <div className="account-profile-wrap" ref={accountRef}>
              <button
                className="account-profile"
                type="button"
                ref={accountButton}
                onClick={() => setAccountOpen((open) => !open)}
              >
                <span className="member-avatar">
                  {(user.firstName || user.email).slice(0, 1).toUpperCase()}
                </span>
                <p>
                  <b>{accountName}</b>
                  <small>
                    {accountKind(user) === "CUSTOMER"
                      ? t('role.customer')
                      : accountKind(user) === "MERCHANT"
                      ? t('role.merchant')
                      : t('role.admin')}
                  </small>
                </p>
                <ChevronDown />
              </button>
              {accountOpen && (
                <div className="account-menu">
                  <div className="member-menu-heading">
                    <b>{accountName}</b>
                    <span>{user.email}</span>
                  </div>
                  {accountKind(user) !== "CUSTOMER" && (
                    <Link to={dashboardPath}>
                      <Store />{" "}
                      {accountKind(user) === "ADMIN" ? t('account.admin') : t('account.store')}{" "}
                      <ChevronRight />
                    </Link>
                  )}
                  <Link to="/cart">
                    <ShoppingCart /> {t('account.cart')} <ChevronRight />
                  </Link>
                  <Link to="/deals">
                    <Gift /> {t('account.deals')} <ChevronRight />
                  </Link>
                  <Link to="/help">
                    <Headphones /> {t('account.help')} <ChevronRight />
                  </Link>
                  <button
                    className="member-signout"
                    type="button"
                    disabled={signingOut}
                    onClick={() => void signOut()}
                  >
                    <LogOut /> {signingOut ? t('account.loggingOut') : t('account.logout')}
                  </button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link className="login" to="/login">
                <UserRound size={22} /> {t('auth.login')}
              </Link>
              <Link className="outline" to="/signup">
                {t('auth.signup')}
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
