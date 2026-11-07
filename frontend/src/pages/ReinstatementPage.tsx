import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import MerchantGateTopbar from "../components/merchant/MerchantGateTopbar";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { usePermissions } from "../hooks/usePermissions";
import { ApiError, merchantApi, type ReinstatementRequest } from "../services/api";
import "../styles/merchant-suspended-gate.css";

function ReinstatementGateArt() {
  return (
    <div className="merchant-suspended-gate__visual" aria-hidden="true">
      <span className="merchant-suspended-gate__orb merchant-suspended-gate__orb--a" />
      <span className="merchant-suspended-gate__orb merchant-suspended-gate__orb--b" />
      <svg viewBox="0 0 420 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="mrgPanel" x1="70" y1="60" x2="340" y2="360" gradientUnits="userSpaceOnUse">
            <stop stopColor="#ffffff" />
            <stop offset="1" stopColor="#eaf3ff" />
          </linearGradient>
        </defs>

        <ellipse cx="210" cy="348" rx="148" ry="28" fill="#d7e8fb" opacity="0.55" />

        <g className="mss-float">
          {/* Appeal document panel — same language as suspended dashboard */}
          <rect x="78" y="88" width="230" height="248" rx="28" fill="url(#mrgPanel)" stroke="#c9ddf3" strokeWidth="1.5" />
          <rect x="100" y="112" width="186" height="36" rx="12" fill="#e8f3ff" />
          <rect x="112" y="124" width="88" height="12" rx="6" fill="#0879f6" opacity="0.75" />
          <rect x="208" y="124" width="40" height="12" rx="6" fill="#b8d4f5" />

          <rect x="100" y="168" width="186" height="88" rx="14" fill="#fff" stroke="#d5e4f4" />
          <rect x="116" y="184" width="120" height="8" rx="4" fill="#d5e4f4" />
          <rect x="116" y="202" width="154" height="8" rx="4" fill="#e8eef6" />
          <rect x="116" y="220" width="96" height="8" rx="4" fill="#e8eef6" />

          <rect x="100" y="272" width="186" height="40" rx="12" fill="#fff8ef" stroke="#ffe0b5" />
          <rect x="116" y="284" width="72" height="16" rx="8" fill="#ff7900" opacity="0.9" />
          <rect x="200" y="286" width="70" height="12" rx="6" fill="#f0d9b0" />
        </g>
      </svg>
    </div>
  );
}

export default function ReinstatementPage() {
  const { t } = useTranslation("merchant");
  const { shopId, shops, ready } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canSubmit = canInShop("SHOP_UPDATE");
  const shop = shops.find((item) => item.shopId === shopId);
  const [current, setCurrent] = useState<ReinstatementRequest | null>(null);
  const [message, setMessage] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!ready || shopId == null) return;
    let active = true;
    setLoading(true);
    setNotice("");
    merchantApi
      .reinstatement(shopId)
      .then((data) => {
        if (!active) return;
        setCurrent(data);
        setMessage(data.message || "");
      })
      .catch((error) => {
        if (!active) return;
        setCurrent(null);
        setNotice(error instanceof ApiError ? error.message : t("reinstatementGate.loadError"));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [ready, shopId, t]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (shopId == null || busy || current?.status === "PENDING") return;
    const text = message.trim();
    if (text.length < 10) {
      setNotice(t("reinstatementGate.minLength"));
      return;
    }
    setBusy(true);
    try {
      const saved = await merchantApi.submitReinstatement(shopId, text);
      setCurrent(saved);
      setMessage(saved.message || text);
      setNotice(t("reinstatementGate.submitted"));
    } catch (error) {
      setNotice(error instanceof ApiError ? error.message : t("reinstatementGate.submitError"));
    } finally {
      setBusy(false);
    }
  };

  const pending = current?.status === "PENDING";
  const shopName = shop?.shopName?.trim();
  const description = shopName
    ? t("reinstatementGate.descriptionWithName", { shopName })
    : t("reinstatementGate.descriptionFallback");

  return (
    <main className="merchant-suspended-gate merchant-suspended-gate--topbar">
      <MerchantGateTopbar />
      <div className="merchant-suspended-gate__glow" aria-hidden="true" />
      <div className="merchant-suspended-gate__layout">
        <div className="merchant-suspended-gate__copy">
          <div role="alert">
            <p className="merchant-suspended-gate__badge">
              <span className="merchant-suspended-gate__badge-dot" aria-hidden="true" />
              {t("reinstatementGate.badge")}
            </p>
            <h1 className="merchant-suspended-gate__title">{t("reinstatementGate.title")}</h1>
            <p className="merchant-suspended-gate__lead">{description}</p>
          </div>

          {loading ? (
            <p className="merchant-suspended-gate__loading">{t("reinstatementGate.loading")}</p>
          ) : (
            <form className="merchant-suspended-gate__form" onSubmit={(event) => void submit(event)}>
              {pending && (
                <p className="merchant-suspended-gate__status">{t("reinstatementGate.pendingNote")}</p>
              )}
              {current?.status === "REJECTED" && (
                <p className="merchant-suspended-gate__status">{t("reinstatementGate.rejectedNote")}</p>
              )}

              <label className="merchant-suspended-gate__field">
                <span className="merchant-suspended-gate__field-label">{t("reinstatementGate.reasonLabel")}</span>
                <span className={`merchant-suspended-gate__composer${pending ? " is-readonly" : ""}`}>
                  <textarea
                    className="merchant-suspended-gate__textarea"
                    value={message}
                    maxLength={1000}
                    rows={7}
                    readOnly={pending}
                    onChange={(event) => {
                      if (pending) return;
                      setMessage(event.target.value);
                    }}
                    placeholder={t("reinstatementGate.reasonPlaceholder")}
                  />
                  <span className="merchant-suspended-gate__composer-meta">
                    <span className="merchant-suspended-gate__hint">
                      {pending ? t("reinstatementGate.pendingHint") : t("reinstatementGate.minHint")}
                    </span>
                    <span className="merchant-suspended-gate__count">{message.trim().length}/1000</span>
                  </span>
                </span>
              </label>

              {!pending && (
                <button
                  type="submit"
                  className="merchant-suspended-gate__btn merchant-suspended-gate__btn--primary merchant-suspended-gate__btn--block"
                  disabled={busy || !canSubmit}
                  title={deniedTitle(canSubmit)}
                >
                  {busy ? t("reinstatementGate.submitting") : t("reinstatementGate.submit")}
                </button>
              )}

              {notice && (
                <p className="merchant-suspended-gate__notice" role="status">
                  {notice}
                </p>
              )}

              <Link className="merchant-suspended-gate__home merchant-suspended-gate__home--in-form" to="/">
                {t("reinstatementGate.home")}
              </Link>
            </form>
          )}
        </div>

        <ReinstatementGateArt />
      </div>
    </main>
  );
}
