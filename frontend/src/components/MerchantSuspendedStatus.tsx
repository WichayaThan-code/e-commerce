import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import MerchantGateTopbar from "./merchant/MerchantGateTopbar";
import "../styles/merchant-suspended-gate.css";

const APPEAL_PATH = "/merchant/reinstatement";

function SuspendedGateArt() {
  return (
    <div className="merchant-suspended-gate__visual" aria-hidden="true">
      <span className="merchant-suspended-gate__orb merchant-suspended-gate__orb--a" />
      <span className="merchant-suspended-gate__orb merchant-suspended-gate__orb--b" />
      <svg viewBox="0 0 420 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="mssPanel" x1="70" y1="60" x2="340" y2="360" gradientUnits="userSpaceOnUse">
            <stop stopColor="#ffffff" />
            <stop offset="1" stopColor="#eaf3ff" />
          </linearGradient>
        </defs>

        <ellipse cx="210" cy="348" rx="148" ry="28" fill="#d7e8fb" opacity="0.55" />

        <g className="mss-float">
          <rect x="78" y="88" width="230" height="248" rx="28" fill="url(#mssPanel)" stroke="#c9ddf3" strokeWidth="1.5" />
          <rect x="100" y="112" width="186" height="36" rx="12" fill="#e8f3ff" />
          <rect x="112" y="124" width="72" height="12" rx="6" fill="#0879f6" opacity="0.45" />
          <rect x="192" y="124" width="28" height="12" rx="6" fill="#b8d4f5" opacity="0.7" />
          <rect x="248" y="120" width="26" height="20" rx="6" fill="#fff4e5" stroke="#ffe0b5" />
          <rect x="254" y="125" width="5" height="10" rx="1.5" fill="#ff7900" />
          <rect x="263" y="125" width="5" height="10" rx="1.5" fill="#ff7900" />

          <rect x="100" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="112" y="180" width="58" height="34" rx="8" fill="#e8eef6" />
          <rect x="112" y="222" width="40" height="8" rx="4" fill="#c5d3e4" />

          <rect x="196" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="208" y="180" width="58" height="34" rx="8" fill="#f3e9df" />
          <rect x="208" y="222" width="40" height="8" rx="4" fill="#c5d3e4" />

          <rect x="100" y="256" width="178" height="52" rx="14" fill="#fff8ef" stroke="#ffe0b5" />
          <rect x="112" y="268" width="72" height="8" rx="4" fill="#f0d9b0" />
          <rect x="112" y="282" width="52" height="6" rx="3" fill="#e8c48a" opacity="0.85" />
          <rect x="112" y="294" width="40" height="5" rx="2.5" fill="#f0d9b0" opacity="0.7" />
          <rect x="198" y="272" width="64" height="20" rx="8" fill="#0879f6" />
          <rect x="214" y="279" width="32" height="6" rx="3" fill="#fff" opacity="0.92" />
        </g>
      </svg>
    </div>
  );
}

type Props = {
  shopName?: string | null;
};

/** Hero status screen for a suspended shop — full merchant topbar; content mirrors signup-gate layout. */
export default function MerchantSuspendedStatus({ shopName }: Props) {
  const { t } = useTranslation("merchant");
  const trimmed = shopName?.trim();
  const description = trimmed
    ? t("suspendedGate.descriptionWithName", { shopName: trimmed })
    : t("suspendedGate.descriptionFallback");

  return (
    <main className="merchant-suspended-gate merchant-suspended-gate--topbar">
      <MerchantGateTopbar />
      <div className="merchant-suspended-gate__glow" aria-hidden="true" />
      <div className="merchant-suspended-gate__layout">
        <div className="merchant-suspended-gate__copy">
          <div role="alert">
            <p className="merchant-suspended-gate__badge">
              <span className="merchant-suspended-gate__badge-dot" aria-hidden="true" />
              {t("suspendedGate.badge")}
            </p>

            <h1 className="merchant-suspended-gate__title">{t("suspendedGate.title")}</h1>
            <p className="merchant-suspended-gate__lead">{description}</p>
          </div>

          <div className="merchant-suspended-gate__actions">
            <Link
              className="merchant-suspended-gate__btn merchant-suspended-gate__btn--primary"
              to={APPEAL_PATH}
            >
              {t("suspendedGate.appeal")}
            </Link>
          </div>

          <Link className="merchant-suspended-gate__home" to="/">
            {t("suspendedGate.home")}
          </Link>
        </div>

        <SuspendedGateArt />
      </div>
    </main>
  );
}
