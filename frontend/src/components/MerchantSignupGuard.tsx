import { Link, Outlet, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../contexts/AuthContext";
import BrandLogo from "./BrandLogo";
import "../styles/merchant-signup-gate.css";

function SignupGateArt() {
  return (
    <div className="merchant-signup-gate__visual" aria-hidden="true">
      <span className="merchant-signup-gate__orb merchant-signup-gate__orb--a" />
      <span className="merchant-signup-gate__orb merchant-signup-gate__orb--b" />
      <svg viewBox="0 0 420 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="msgPanel" x1="70" y1="60" x2="340" y2="360" gradientUnits="userSpaceOnUse">
            <stop stopColor="#ffffff" />
            <stop offset="1" stopColor="#eaf3ff" />
          </linearGradient>
        </defs>

        {/* Soft ground plane */}
        <ellipse cx="210" cy="348" rx="148" ry="28" fill="#d7e8fb" opacity="0.55" />

        {/* Main storefront panel */}
        <g className="msg-float">
          <rect x="78" y="88" width="230" height="248" rx="28" fill="url(#msgPanel)" stroke="#c9ddf3" strokeWidth="1.5" />
          <rect x="100" y="112" width="186" height="36" rx="12" fill="#e8f3ff" />
          <rect x="112" y="124" width="72" height="12" rx="6" fill="#0879f6" opacity="0.85" />
          <rect x="192" y="124" width="48" height="12" rx="6" fill="#b8d4f5" />

          {/* Product tiles */}
          <rect x="100" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="112" y="180" width="58" height="34" rx="8" fill="#dcebff" />
          <rect x="112" y="222" width="40" height="8" rx="4" fill="#9bb8d8" />

          <rect x="196" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="208" y="180" width="58" height="34" rx="8" fill="#ffe8d4" />
          <rect x="208" y="222" width="40" height="8" rx="4" fill="#9bb8d8" />

          <rect x="100" y="256" width="178" height="52" rx="14" fill="#f5f9fd" stroke="#d5e4f4" />
          <circle cx="128" cy="282" r="14" fill="#0879f6" opacity="0.15" />
          <path d="M122 282h12M128 276v12" stroke="#0879f6" strokeWidth="2.5" strokeLinecap="round" />
          <rect x="154" y="274" width="96" height="8" rx="4" fill="#b8d4f5" />
          <rect x="154" y="290" width="64" height="6" rx="3" fill="#d5e4f4" />

          {/* Shop insight — overlaps panel edge, same UI language */}
          <g transform="translate(250 176)">
            <rect x="0" y="0" width="100" height="118" rx="16" fill="#fff" stroke="#c9ddf3" strokeWidth="1.5" />
            <rect x="14" y="16" width="36" height="36" rx="10" fill="#e8f3ff" />
            <path
              d="M26 30c0-5 3.2-8 7-8s7 3 7 8"
              stroke="#0879f6"
              strokeWidth="2.4"
              strokeLinecap="round"
              fill="none"
            />
            <rect x="24" y="32" width="18" height="13" rx="3" fill="#0879f6" />
            <rect x="58" y="22" width="28" height="7" rx="3.5" fill="#b8d4f5" />
            <rect x="58" y="36" width="18" height="5" rx="2.5" fill="#d5e4f4" />
            {/* Sales bars */}
            <rect x="18" y="78" width="12" height="22" rx="3" fill="#dcebff" />
            <rect x="36" y="68" width="12" height="32" rx="3" fill="#9ec5f5" />
            <rect x="54" y="58" width="12" height="42" rx="3" fill="#0879f6" />
            <rect x="72" y="48" width="12" height="52" rx="3" fill="#ff7900" />
          </g>
        </g>
      </svg>
    </div>
  );
}

export default function MerchantSignupGuard() {
  const { user, loading } = useAuth();
  const location = useLocation();
  const { t } = useTranslation("merchant");
  const next = `${location.pathname}${location.search}`;

  if (loading) {
    return (
      <main className="merchant-signup-gate merchant-signup-gate--checking">
        {t("signupGate.checking")}
      </main>
    );
  }

  if (user) return <Outlet />;

  return (
    <main className="merchant-signup-gate">
      <div className="merchant-signup-gate__glow" aria-hidden="true" />
      <div className="merchant-signup-gate__layout">
        <div className="merchant-signup-gate__copy">
          <Link to="/" className="merchant-signup-gate__brand" aria-label="ThaiShopFun">
            <BrandLogo variant="withName" alt="" />
          </Link>

          <h1 className="merchant-signup-gate__title">{t("signupGate.title")}</h1>
          <p className="merchant-signup-gate__lead">{t("signupGate.description")}</p>

          <ol className="merchant-signup-gate__steps" aria-label={t("signupGate.stepsAria")}>
            <li>
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                1
              </span>
              <span>{t("signupGate.stepLogin")}</span>
            </li>
            <li>
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                2
              </span>
              <span>{t("signupGate.stepShop")}</span>
            </li>
            <li>
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                3
              </span>
              <span>{t("signupGate.stepSubmit")}</span>
            </li>
          </ol>

          <div className="merchant-signup-gate__actions">
            <Link
              className="merchant-signup-gate__btn merchant-signup-gate__btn--primary"
              to={`/login?next=${encodeURIComponent(next)}`}
            >
              {t("signupGate.login")}
            </Link>
            <Link className="merchant-signup-gate__btn merchant-signup-gate__btn--secondary" to="/signup">
              {t("signupGate.signup")}
            </Link>
          </div>

          <Link className="merchant-signup-gate__home" to="/">
            {t("signupGate.home")}
          </Link>
        </div>

        <SignupGateArt />
      </div>
    </main>
  );
}
