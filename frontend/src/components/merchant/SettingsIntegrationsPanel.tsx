import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { QRCodeCanvas } from "qrcode.react";
import {
  CheckCircle2,
  ChevronRight,
  Copy,
  CreditCard,
  Download,
  ExternalLink,
  Facebook,
  Globe2,
  Instagram,
  Link2,
  MessageCircle,
  Music2,
  QrCode,
  Share2,
  Store,
  Truck,
} from "lucide-react";
import FormField from "../form/FormField";
import { LoadError, SkeletonForm } from "../feedback/Skeleton";
import MerchantSaveButton from "./MerchantSaveButton";
import { usePermissions } from "../../hooks/usePermissions";
import { ApiError, mediaUrl, merchantApi, type MerchantChannelsPayload, type MerchantSettings } from "../../services/api";
import { copyText } from "../../utils/productLinks";
import { channelsEqual, validateAndNormalizeChannels } from "../../utils/shopChannelValidation";
import { isLocalOrPrivateHost, shopStorefrontUrl, storefrontAccess } from "../../utils/shopStorefront";
import "../../styles/form-fields.css";
import "../../styles/product-qr.css";

type Props = {
  shopId: number;
  notify: (text: string) => void;
  onDirtyChange?: (dirty: boolean) => void;
  onNavigateHref?: (href: string) => void;
};

const emptyChannels = (): MerchantChannelsPayload => ({
  website: "",
  lineId: "",
  facebookUrl: "",
  instagramUrl: "",
  tiktokUrl: "",
});

function fromSettings(data: MerchantSettings): MerchantChannelsPayload {
  return {
    website: data.website || "",
    lineId: data.lineId || "",
    facebookUrl: data.facebookUrl || "",
    instagramUrl: data.instagramUrl || "",
    tiktokUrl: data.tiktokUrl || "",
  };
}

export default function SettingsIntegrationsPanel({ shopId, notify, onDirtyChange, onNavigateHref }: Props) {
  const { t, i18n } = useTranslation("merchant");
  const { canInShop, deniedTitle } = usePermissions();
  const canUpdate = canInShop("SHOP_UPDATE");
  const qrWrap = useRef<HTMLDivElement>(null);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [settings, setSettings] = useState<MerchantSettings | null>(null);
  const [draft, setDraft] = useState<MerchantChannelsPayload>(emptyChannels);
  const [baseline, setBaseline] = useState<MerchantChannelsPayload>(emptyChannels);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);
  const requestShop = useRef(shopId);

  const dirty = useMemo(() => !channelsEqual(draft, baseline), [draft, baseline]);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);

  const load = useCallback(async (id: number) => {
    requestShop.current = id;
    setLoading(true);
    setLoadError("");
    setFormError("");
    setFieldErrors({});
    try {
      const data = await merchantApi.getSettings(id);
      if (requestShop.current !== id) return;
      const channels = fromSettings(data);
      setSettings(data);
      setDraft(channels);
      setBaseline(channels);
    } catch (error) {
      if (requestShop.current !== id) return;
      setSettings(null);
      setLoadError(error instanceof ApiError ? error.message : t("integrations.loadError"));
    } finally {
      if (requestShop.current === id) setLoading(false);
    }
  }, [t]);

  useEffect(() => { void load(shopId); }, [shopId, load]);

  const slug = settings?.slug?.trim() || "";
  const storeUrl = shopStorefrontUrl(slug);
  const local = isLocalOrPrivateHost();
  const access = storefrontAccess(settings?.status, slug, local);
  const qrEnabled = Boolean(storeUrl) && access.canOpen;

  const channelMessages = useMemo(() => ({
    max: t("integrations.validation.max"),
    url: t("integrations.validation.url"),
    host: t("integrations.validation.host"),
    protocol: t("integrations.validation.protocol"),
    creds: t("integrations.validation.creds"),
    line: t("integrations.validation.line"),
    lineId: t("integrations.validation.lineId"),
  }), [t, i18n.language]);

  const patch = (key: keyof MerchantChannelsPayload, value: string) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setFieldErrors((current) => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const cancel = () => {
    setDraft(baseline);
    setFieldErrors({});
    setFormError("");
  };

  const save = async () => {
    if (!canUpdate || saving) return;
    const checked = validateAndNormalizeChannels(draft, channelMessages);
    if (Object.keys(checked.errors).length) {
      setFieldErrors(checked.errors);
      setFormError(t("integrations.validation.fix"));
      return;
    }
    setSaving(true);
    setFormError("");
    setFieldErrors({});
    try {
      const saved = await merchantApi.saveChannels(checked.values, shopId);
      if (requestShop.current !== shopId) return;
      const channels = fromSettings(saved);
      setSettings(saved);
      setDraft(channels);
      setBaseline(channels);
      notify(t("integrations.saved"));
    } catch (error) {
      if (requestShop.current !== shopId) return;
      if (error instanceof ApiError) {
        const map: Record<string, string> = {};
        (error.fieldErrors ?? []).forEach((row) => { map[row.field] = row.message; });
        setFieldErrors(map);
        setFormError(error.message || t("integrations.saveError"));
      } else {
        setFormError(t("integrations.saveError"));
      }
    } finally {
      setSaving(false);
    }
  };

  const openStore = () => {
    if (!storeUrl || !access.canOpen) return;
    window.open(storeUrl, "_blank", "noopener,noreferrer");
  };

  const onCopy = async () => {
    if (!storeUrl || !access.canOpen) return;
    const ok = await copyText(storeUrl);
    if (ok) {
      setCopied(true);
      notify(t("integrations.copyOk"));
      window.setTimeout(() => setCopied(false), 1600);
      return;
    }
    notify(t("integrations.copyFail"));
  };

  const onShare = async () => {
    if (!storeUrl || !access.canOpen) return;
    if (typeof navigator === "undefined" || typeof navigator.share !== "function") {
      await onCopy();
      return;
    }
    try {
      await navigator.share({
        title: settings?.shopName || t("integrations.shareTitle"),
        text: t("integrations.shareText", { name: settings?.shopName || "" }),
        url: storeUrl,
      });
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      notify(t("integrations.shareFail"));
    }
  };

  const canDownloadQr = qrEnabled && (access.canSharePublic || access.reason === "local");
  const downloadQr = () => {
    if (!qrEnabled) return;
    if (!canDownloadQr) {
      notify(t("integrations.qrBlocked"));
      return;
    }
    const canvas = qrWrap.current?.querySelector("canvas");
    if (!canvas) return;
    const link = document.createElement("a");
    link.href = canvas.toDataURL("image/png");
    link.download = `qr-shop-${(slug || "store").replace(/[^\p{L}\p{N}]+/gu, "-").slice(0, 40)}.png`;
    link.click();
    notify(t("integrations.qrDownloaded"));
  };

  const go = (href: string) => {
    if (onNavigateHref) onNavigateHref(href);
    else window.location.assign(href);
  };

  if (loading) {
    return (
      <div className="settings-layout integrations-settings">
        <div className="settings-main"><section className="settings-card"><SkeletonForm fields={8} /></section></div>
      </div>
    );
  }

  if (loadError || !settings) {
    return (
      <div className="settings-layout integrations-settings">
        <div className="settings-main">
          <section className="settings-card">
            <LoadError message={loadError || t("integrations.loadError")} onRetry={() => void load(shopId)} />
          </section>
        </div>
      </div>
    );
  }

  const statusLabel = t(`integrations.status.${settings.status}`, { defaultValue: settings.status });
  const accessHint = t(`integrations.access.${access.reason}`);
  const shopMark = (settings.shopName.trim().charAt(0) || "ร").toUpperCase();
  const logoSrc = mediaUrl(settings.logoUrl);
  const filledCount = channelRowsFilled(draft);
  const channelRows: { key: keyof MerchantChannelsPayload; label: string; icon: typeof Globe2; hint: string; tone: string }[] = [
    { key: "website", label: t("integrations.fields.website"), icon: Globe2, hint: t("integrations.fields.websiteHint"), tone: "web" },
    { key: "lineId", label: t("integrations.fields.line"), icon: MessageCircle, hint: t("integrations.fields.lineHint"), tone: "line" },
    { key: "facebookUrl", label: t("integrations.fields.facebook"), icon: Facebook, hint: t("integrations.fields.facebookHint"), tone: "facebook" },
    { key: "instagramUrl", label: t("integrations.fields.instagram"), icon: Instagram, hint: t("integrations.fields.instagramHint"), tone: "instagram" },
    { key: "tiktokUrl", label: t("integrations.fields.tiktok"), icon: Music2, hint: t("integrations.fields.tiktokHint"), tone: "tiktok" },
  ];

  return (
    <div className="settings-layout integrations-settings">
      <div className="settings-main">
        <section className="settings-card integrations-drive-card">
          <header className="integrations-section-head">
            <div className="integrations-section-title">
              <span className="integrations-section-icon" aria-hidden="true"><Store /></span>
              <div>
                <h2>{t("integrations.drive.title")}</h2>
                <p>{t("integrations.drive.subtitle")}</p>
              </div>
            </div>
            <span className={`integrations-status is-${settings.status.toLowerCase()}`}>{statusLabel}</span>
          </header>

          <div className="integrations-hero">
            <div className="integrations-hero-main">
              <div className="integrations-shop-row">
                <span className={`integrations-shop-mark${logoSrc ? " has-logo" : ""}`} aria-hidden="true">
                  {logoSrc ? <img src={logoSrc} alt="" /> : shopMark}
                </span>
                <div>
                  <b>{settings.shopName}</b>
                  <p>{accessHint}</p>
                </div>
              </div>

              {storeUrl ? (
                <div className="integrations-url-bar">
                  <code tabIndex={0}>{storeUrl}</code>
                  <button type="button" className="integrations-btn ghost" disabled={!access.canOpen} onClick={() => void onCopy()} title={t("integrations.actions.copy")}>
                    <Copy aria-hidden />{copied ? t("integrations.actions.copied") : t("integrations.actions.copy")}
                  </button>
                </div>
              ) : (
                <p className="integrations-empty-url">{t("integrations.access.missing_slug")}</p>
              )}
              {local ? <p className="integrations-local-note">{t("integrations.access.local")}</p> : null}

              <div className="integrations-drive-actions">
                <button type="button" className="integrations-btn primary" disabled={!access.canOpen || !storeUrl} onClick={openStore}>
                  <ExternalLink aria-hidden />{t("integrations.actions.open")}
                </button>
                <button type="button" className="integrations-btn" disabled={!access.canOpen || !storeUrl} onClick={() => void onShare()}>
                  <Share2 aria-hidden />{t("integrations.actions.share")}
                </button>
                <button type="button" className="integrations-btn" disabled={!canDownloadQr} onClick={downloadQr}>
                  <Download aria-hidden />{t("integrations.actions.downloadQr")}
                </button>
              </div>
              {!canDownloadQr && access.canOpen ? <p className="integrations-soft-note">{t("integrations.qrBlocked")}</p> : null}
            </div>

            <aside className="integrations-qr-panel">
              <div className="integrations-qr-preview" ref={qrWrap}>
                {qrEnabled ? (
                  <QRCodeCanvas value={storeUrl} size={168} marginSize={2} level="M" aria-label={t("integrations.qrLabel")} />
                ) : (
                  <div className="integrations-qr-empty"><QrCode aria-hidden /><span>{t("integrations.qrUnavailable")}</span></div>
                )}
              </div>
              <div className="integrations-qr-meta">
                <b>{t("integrations.qrTitle")}</b>
                <p>{t("integrations.qrHint")}</p>
              </div>
            </aside>
          </div>
        </section>

        <section className="settings-card integrations-channels-card">
          <header className="integrations-section-head">
            <div className="integrations-section-title">
              <span className="integrations-section-icon" aria-hidden="true"><Link2 /></span>
              <div>
                <h2>{t("integrations.channels.title")}</h2>
                <p>{t("integrations.channels.subtitle")}</p>
              </div>
            </div>
            <div className="integrations-channel-actions">
              <span className="integrations-filled-count">{filledCount}/5</span>
              {dirty ? <button type="button" className="integrations-btn" disabled={saving} onClick={cancel}>{t("integrations.actions.cancel")}</button> : null}
              <MerchantSaveButton saving={saving} disabled={!canUpdate || !dirty} title={deniedTitle(canUpdate)} onClick={() => void save()} />
            </div>
          </header>

          <p className="integrations-channels-note">{t("integrations.channels.note")}</p>
          {formError ? <p className="form-field-error" role="alert">{formError}</p> : null}

          <div className="integrations-channel-grid">
            {channelRows.map(({ key, label, icon: Icon, hint, tone }) => {
              const filled = Boolean(draft[key].trim());
              return (
                <div key={key} className={`integrations-channel-item tone-${tone}${filled ? " is-filled" : ""}`}>
                  <FormField
                    label={
                      <span className="integrations-channel-label">
                        <span className="integrations-channel-name"><Icon aria-hidden />{label}</span>
                        <small className={filled ? "is-set" : ""}>{filled ? t("integrations.channels.filled") : t("integrations.channels.empty")}</small>
                      </span>
                    }
                    error={fieldErrors[key]}
                    className="flush"
                  >
                    <input
                      value={draft[key]}
                      disabled={!canUpdate || saving}
                      placeholder={hint}
                      onChange={(event) => patch(key, event.target.value)}
                    />
                  </FormField>
                </div>
              );
            })}
          </div>

          <p className="integrations-footer-link">
            {t("integrations.channels.storeLinkLead")}{" "}
            <a href="/merchant/store?tab=info" onClick={(event) => { event.preventDefault(); go("/merchant/store?tab=info"); }}>
              {t("integrations.channels.storeLink")}
            </a>
          </p>
        </section>
      </div>

      <aside className="settings-side">
        <section className="settings-card integrations-related-card">
          <header className="integrations-section-head">
            <div className="integrations-section-title">
              <span className="integrations-section-icon" aria-hidden="true"><CheckCircle2 /></span>
              <div>
                <h2>{t("integrations.related.title")}</h2>
                <p>{t("integrations.related.subtitle")}</p>
              </div>
            </div>
          </header>
          <button type="button" className="integrations-related-link" onClick={() => go("/merchant/settings?tab=payment")}>
            <span className="integrations-related-icon tone-pay" aria-hidden="true"><CreditCard /></span>
            <span>
              <b>{t("integrations.related.payment")}</b>
              <small>{t("integrations.related.paymentHint")}</small>
            </span>
            <ChevronRight aria-hidden />
          </button>
          <button type="button" className="integrations-related-link" onClick={() => go("/merchant/settings?tab=shipping")}>
            <span className="integrations-related-icon tone-ship" aria-hidden="true"><Truck /></span>
            <span>
              <b>{t("integrations.related.shipping")}</b>
              <small>{t("integrations.related.shippingHint")}</small>
            </span>
            <ChevronRight aria-hidden />
          </button>
          <p className="integrations-related-note"><CheckCircle2 aria-hidden />{t("integrations.related.note")}</p>
        </section>
      </aside>
    </div>
  );
}

function channelRowsFilled(draft: MerchantChannelsPayload) {
  return (["website", "lineId", "facebookUrl", "instagramUrl", "tiktokUrl"] as const).filter((key) => draft[key].trim()).length;
}
