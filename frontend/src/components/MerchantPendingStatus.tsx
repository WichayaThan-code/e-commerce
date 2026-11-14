import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import MerchantGateTopbar from "./merchant/MerchantGateTopbar";
import "../styles/merchant-signup-gate.css";

type Props = {
  shopName?: string | null;
  rejected?: boolean;
};

function PendingGateArt({ rejected }: { rejected: boolean }) {
  return (
    <div className="merchant-signup-gate__visual" aria-hidden="true">
      <span className="merchant-signup-gate__orb merchant-signup-gate__orb--a" />
      <span className="merchant-signup-gate__orb merchant-signup-gate__orb--b" />
      <svg viewBox="0 0 420 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="mpgPanel" x1="70" y1="60" x2="340" y2="360" gradientUnits="userSpaceOnUse">
            <stop stopColor="#ffffff" />
            <stop offset="1" stopColor="#eaf3ff" />
          </linearGradient>
        </defs>

        <ellipse cx="210" cy="348" rx="148" ry="28" fill="#d7e8fb" opacity="0.55" />

        <g className="msg-float">
          <rect x="78" y="88" width="230" height="248" rx="28" fill="url(#mpgPanel)" stroke="#c9ddf3" strokeWidth="1.5" />
          <rect x="100" y="112" width="186" height="36" rx="12" fill="#e8f3ff" />
          <rect x="112" y="124" width="72" height="12" rx="6" fill="#0879f6" opacity="0.55" />
          <rect x="192" y="124" width="48" height="12" rx="6" fill="#b8d4f5" />

          <rect x="100" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="112" y="180" width="58" height="34" rx="8" fill="#dcebff" />
          <rect x="112" y="222" width="40" height="8" rx="4" fill="#9bb8d8" />

          <rect x="196" y="168" width="82" height="72" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="208" y="180" width="58" height="34" rx="8" fill={rejected ? "#ffe8e0" : "#fff0e0"} />
          <rect x="208" y="222" width="40" height="8" rx="4" fill="#9bb8d8" />

          <rect x="100" y="256" width="178" height="52" rx="14" fill="#f5f9fd" stroke="#d5e4f4" />
          <circle cx="128" cy="282" r="14" fill="#0879f6" opacity="0.15" />
          <path d="M122 282h12M128 276v12" stroke="#0879f6" strokeWidth="2.5" strokeLinecap="round" />
          <rect x="154" y="274" width="96" height="8" rx="4" fill="#b8d4f5" />
          <rect x="154" y="290" width="64" height="6" rx="3" fill="#d5e4f4" />

          {/* Status card — waiting clock / rejected mark, same tile language as dashboard */}
          <g transform="translate(250 176)">
            <rect x="0" y="0" width="100" height="118" rx="16" fill="#fff" stroke="#c9ddf3" strokeWidth="1.5" />

            {/* Icon tile — filled shapes only, flush inside rounded square */}
            <rect
              x="14"
              y="14"
              width="36"
              height="36"
              rx="10"
              fill={rejected ? "#ffe8e0" : "#e8f3ff"}
            />
            {rejected ? (
              <>
                <circle cx="32" cy="32" r="11" fill="#e04b4b" />
                <path
                  d="M27 27l10 10M37 27L27 37"
                  stroke="#fff"
                  strokeWidth="2.6"
                  strokeLinecap="round"
                />
              </>
            ) : (
              <>
                {/* Clock face nested in the blue tile */}
                <circle cx="32" cy="32" r="11" fill="#0879f6" />
                <circle cx="32" cy="32" r="7.5" fill="#fff" />
                <path
                  d="M32 27.5v5.2l3.6 2.2"
                  stroke="#0879f6"
                  strokeWidth="2.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                <circle cx="32" cy="32" r="1.4" fill="#0879f6" />
              </>
            )}

            <rect x="58" y="20" width="28" height="7" rx="3.5" fill="#b8d4f5" />
            <rect x="58" y="34" width="18" height="5" rx="2.5" fill="#d5e4f4" />

            {/* Waiting progress — matches “รออนุมัติ”, not a floating chart */}
            <rect x="14" y="62" width="72" height="8" rx="4" fill="#e8f3ff" />
            <rect x="14" y="62" width={rejected ? 28 : 44} height="8" rx="4" fill={rejected ? "#e04b4b" : "#0879f6"} />
            <rect x="14" y="80" width="52" height="6" rx="3" fill="#d5e4f4" />
            <rect x="14" y="94" width="36" height="6" rx="3" fill="#e8eef6" />
            <circle cx="22" cy="108" r="3.5" fill="#0879f6" />
            <circle cx="36" cy="108" r="3.5" fill={rejected ? "#e04b4b" : "#9ec5f5"} />
            <circle cx="50" cy="108" r="3.5" fill="#d5e4f4" />
          </g>
        </g>
      </svg>
    </div>
  );
}

/**
 * Full-page gate when shop is pending review or rejected — same pattern as signup gate; blocks all merchant menus.
 * Shows the merchant topbar (same as the suspended gate) on top.
 */
export default function MerchantPendingStatus({ shopName, rejected = false }: Props) {
  const { t } = useTranslation("merchant");
  const trimmed = shopName?.trim();
  const description = trimmed
    ? t(rejected ? "pendingGate.rejectedDescriptionWithName" : "pendingGate.descriptionWithName", {
        shopName: trimmed,
      })
    : t(rejected ? "pendingGate.rejectedDescriptionFallback" : "pendingGate.descriptionFallback");

  return (
    <main className="merchant-signup-gate merchant-signup-gate--topbar">
      <MerchantGateTopbar />
      <div className="merchant-signup-gate__glow" aria-hidden="true" />
      <div className="merchant-signup-gate__layout">
        <div className="merchant-signup-gate__copy">
          <div role="alert">
            <p
              className={`merchant-signup-gate__badge${rejected ? " merchant-signup-gate__badge--reject" : ""}`}
            >
              <span className="merchant-signup-gate__badge-dot" aria-hidden="true" />
              {t(rejected ? "pendingGate.rejectedBadge" : "pendingGate.badge")}
            </p>

            <h1 className="merchant-signup-gate__title">
              {t(rejected ? "pendingGate.rejectedTitle" : "pendingGate.title")}
            </h1>
            <p className="merchant-signup-gate__lead">{description}</p>
          </div>

          <ol className="merchant-signup-gate__steps" aria-label={t("pendingGate.stepsAria")}>
            <li className="is-done">
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                1
              </span>
              <span>{t("pendingGate.stepSubmit")}</span>
            </li>
            <li className={rejected ? "is-reject" : "is-active"}>
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                2
              </span>
              <span>{t(rejected ? "pendingGate.stepRejected" : "pendingGate.stepReview")}</span>
            </li>
            <li className="is-todo">
              <span className="merchant-signup-gate__step-index" aria-hidden="true">
                3
              </span>
              <span>{t("pendingGate.stepOpen")}</span>
            </li>
          </ol>

          <div className="merchant-signup-gate__actions">
            {rejected ? (
              <Link className="merchant-signup-gate__btn merchant-signup-gate__btn--primary" to="/merchant/register">
                {t("pendingGate.reapply")}
              </Link>
            ) : null}
            <Link
              className={`merchant-signup-gate__btn merchant-signup-gate__btn--${rejected ? "secondary" : "primary"}`}
              to="/"
            >
              {t("pendingGate.home")}
            </Link>
          </div>
        </div>

        <PendingGateArt rejected={rejected} />
      </div>
    </main>
  );
}
