import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  Check, ChevronDown, ChevronUp, CircleAlert, GripVertical, Monitor, Palette, Plus, RotateCcw, Shirt, Smartphone, Store, Tablet, Trash2, UtensilsCrossed,
  type LucideIcon,
} from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import FormField, { FieldError } from "../form/FormField";
import FormTextarea from "../form/FormTextarea";
import ToggleSwitch from "../form/ToggleSwitch";
import { LoadError, SkeletonForm } from "../feedback/Skeleton";
import ThemeMediaPicker from "./ThemeMediaPicker";
import { MediaField, ThemeSelect } from "./ThemeEditorParts";
import ShopBlocksEditor from "./ShopBlocksEditor";
import { chromePayload, FontPanel, FooterPanel, HeaderPanel, HeroMediaPanel } from "./ShopChromeEditor";
import { HeroStickerDragContext, type StickerDragApi } from "../shop/sections/HeroMediaFull";
import { ColorField, GradientEditor, HEX, ThemeTokenSelect } from "./ThemeEditorParts";
import ShopStorefront from "../shop/ShopStorefront";
import { isShopSectionAvailable } from "../shop/sections/availability";
import { previewCatalogControls } from "../shop/sections/types";
import {
  ApiError, merchantApi, shopApi,
  type ApiFieldError, type MerchantSettings, type PublicProduct, type PublicShop,
} from "../../services/api";
import {
  ensureThemeFontLink, normalizeTheme, PREVIEW_WIDTHS, presetForTemplate, registerThemePresets, sectionsFromBlocks,
  sectionSupportsVariant, withSynthesizedLayout, type LayoutBlock,
  SHOP_BUTTON_SHAPES, SHOP_CARD_STYLES, SHOP_CTA_STYLES, SHOP_DENSITIES, SHOP_IMAGE_RATIOS,
  SHOP_SHADOWS, SHOP_THEME_FONTS, SHOP_THEME_TEMPLATES,
  shopThemeFontStack, themeContrastIssues, variantsForSection,
  SHOP_TITLE_ALIGNS, SHOP_TRUST_ICONS,
  type SectionVariant, type ShopButtonShape, type ShopCardStyle, type ShopCtaStyle, type ShopDensity, type ShopImageRatio,
  type ShopSectionKey, type ShopShadow, type ShopTheme,
  type ShopThemeFontKey, type ShopThemeTemplateKey, type ShopTitleAlign, type ShopTrustIcon, type ThemeBanner, type ThemeColors,
  type ThemeConfig, type ThemeGradient, type ThemeProductCard,
  type ThemeTrustItem,
} from "../../theme/shopTheme";
import "../../styles/form-fields.css";
import "../../styles/sale-types.css";
import "../../styles/theme-token-select.css";
import "../../styles/theme-media-picker.css";
import "../../styles/theme-editor-controls.css";

export type ShopThemeShopInfo = {
  shopName: string;
  shopNameEn: string;
  description: string;
  category: string;
  address: string;
  province: string;
  district: string;
  subdistrict: string;
  postalCode: string;
  slug: string;
  status: MerchantSettings["status"];
  logoUrl: string | null;
  coverUrl: string | null;
  createdAt?: string | null;
  openingHours: MerchantSettings["openingHours"];
};

type ShopThemeEditorProps = {
  shopId: number | null;
  shop: ShopThemeShopInfo;
  canUpdate: boolean;
  deniedTitle?: string;
  /** Existing logo upload control (reused from the shop info tab). */
  logoControl: ReactNode;
  /** Existing cover upload control (reused from the shop info tab). */
  coverControl: ReactNode;
  onDirtyChange: (dirty: boolean) => void;
  notify: (message: string) => void;
};

type PendingConfirm = { kind: "template"; key: ShopThemeTemplateKey } | { kind: "reset" } | null;
type DeviceKey = keyof typeof PREVIEW_WIDTHS;

const TEMPLATE_ICONS: Record<ShopThemeTemplateKey, LucideIcon> = {
  GENERAL: Store,
  FASHION: Shirt,
  FOOD_BEVERAGE: UtensilsCrossed,
  CUSTOM: Palette,
};
const COLOR_KEYS = ["primary", "secondary", "background", "text"] as const satisfies ReadonlyArray<keyof ThemeColors>;
const EXTRA_COLOR_KEYS = ["accent", "surface", "muted", "ctaBackground", "ctaText", "border"] as const satisfies ReadonlyArray<keyof ThemeColors>;
const GRADIENT_KEYS = ["hero", "page", "section"] as const;
type GradientKey = (typeof GRADIENT_KEYS)[number];
const DEVICES: { key: DeviceKey; icon: LucideIcon }[] = [
  { key: "mobile", icon: Smartphone },
  { key: "tablet", icon: Tablet },
  { key: "desktop", icon: Monitor },
];
const PREVIEW_PRODUCT_LIMIT = 8;

function moveItem<T>(items: T[], from: number, to: number) {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return items;
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

function stripLayout(config: ThemeConfig): ThemeConfig {
  const { layout: _layout, ...rest } = config;
  void _layout;
  return rest;
}

/** Block payload for the API: derived fields (backgroundUrl) are never sent back. */
function blocksPayload(blocks: LayoutBlock[]): LayoutBlock[] {
  return blocks.map((block) => {
    const settings = { ...block.settings };
    delete settings.backgroundUrl;
    return { ...block, settings };
  });
}

function sameConfig(a: ThemeConfig, b: ThemeConfig) {
  return JSON.stringify(a) === JSON.stringify(b);
}

export default function ShopThemeEditor({
  shopId, shop, canUpdate, deniedTitle, logoControl, coverControl, onDirtyChange, notify,
}: ShopThemeEditorProps) {
  const { t } = useTranslation("merchant");
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [presets, setPresets] = useState<Partial<Record<ShopThemeTemplateKey, ShopTheme>>>({});
  const [baseline, setBaseline] = useState<ShopTheme>(() => normalizeTheme(null));
  const [draft, setDraft] = useState<ShopTheme>(() => normalizeTheme(null));
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [confirm, setConfirm] = useState<PendingConfirm>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [device, setDevice] = useState<DeviceKey>("mobile");
  const [previewProducts, setPreviewProducts] = useState<PublicProduct[]>([]);
  const [stageWidth, setStageWidth] = useState(0);
  const stageRef = useRef<HTMLDivElement>(null);

  const dirty = useMemo(() => JSON.stringify(draft) !== JSON.stringify(baseline), [draft, baseline]);

  // AG5: dragging a sticker on the preview writes x / y (0-100 %) straight into the draft; disabled when read-only.
  const stickerDrag = useMemo<StickerDragApi | null>(() => (canUpdate ? {
    onMove: (index, x, y) => setDraft((current) => (current.config.hero.stickers?.[index]
      ? {
        ...current,
        config: {
          ...current.config,
          hero: {
            ...current.config.hero,
            stickers: current.config.hero.stickers.map((row, rowIndex) => (rowIndex === index ? { ...row, x, y } : row)),
          },
        },
      }
      : current)),
  } : null), [canUpdate]);

  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadFailed(false);
    Promise.all([merchantApi.getTheme(shopId), merchantApi.getThemePresets(shopId).catch(() => [])])
      .then(([theme, rows]) => {
        if (!active) return;
        registerThemePresets(rows as Array<{ templateKey?: string; config?: Record<string, unknown> }>);
        const map: Partial<Record<ShopThemeTemplateKey, ShopTheme>> = {};
        for (const row of rows) {
          const normalized = normalizeTheme(row as Partial<ShopTheme>);
          map[normalized.templateKey] = normalized;
        }
        const loaded = normalizeTheme(theme as Partial<ShopTheme>);
        setPresets(map);
        setBaseline(loaded);
        setDraft(loaded);
        setFieldErrors([]);
      })
      .catch(() => {
        if (active) setLoadFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [shopId, reloadKey]);

  useEffect(() => {
    if (!shop.slug) return;
    let active = true;
    shopApi.products(shop.slug, { size: PREVIEW_PRODUCT_LIMIT, page: 0, sort: "newest" })
      .then((result) => {
        if (active) setPreviewProducts(result.items);
      })
      .catch(() => {
        if (active) setPreviewProducts([]);
      });
    return () => {
      active = false;
    };
  }, [shop.slug]);

  useEffect(() => {
    ensureThemeFontLink(draft.config.font);
  }, [draft.config.font]);

  // Track the preview stage width so wide devices can be scaled down to fit.
  useEffect(() => {
    const element = stageRef.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => setStageWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, [loading, loadFailed]);

  const presetFor = useCallback(
    (key: ShopThemeTemplateKey): ShopTheme => presets[key] ?? { templateKey: key, config: presetForTemplate(key) },
    [presets],
  );

  const errorFor = (path: string) =>
    fieldErrors.find((item) => item.field === `config.${path}` || item.field === path)?.message;
  const sectionError = (index: number) => errorFor(`sections[${index}].variant`) || errorFor(`sections[${index}].enabled`);
  const clearErrors = (prefix: string) =>
    setFieldErrors((current) => (current.some((item) => item.field.startsWith(prefix))
      ? current.filter((item) => !item.field.startsWith(prefix))
      : current));

  const patchConfig = (change: (config: ThemeConfig) => ThemeConfig, errorPrefix: string) => {
    clearErrors(errorPrefix);
    setDraft((current) => ({ ...current, config: change(current.config) }));
  };

  const applyPreset = (key: ShopThemeTemplateKey) => {
    const preset = presetFor(key);
    setFieldErrors([]);
    // Full preset swap — colors, font, style, sections/variants, banners, trust (no GENERAL leftovers).
    const config = structuredClone(preset.config) as ThemeConfig;
    setDraft({
      templateKey: key,
      config: key === "CUSTOM" ? withSynthesizedLayout(config) : stripLayout(config),
    });
  };

  const chooseTemplate = (key: ShopThemeTemplateKey) => {
    if (key === draft.templateKey) return;
    if (key === "CUSTOM") {
      setFieldErrors([]);
      // AG3: CUSTOM always edits layout.blocks; build them from the current sections the first time.
      setDraft((current) => ({ templateKey: "CUSTOM", config: withSynthesizedLayout(current.config) }));
      return;
    }
    const differsFromTarget = !sameConfig(draft.config, presetFor(key).config);
    if (differsFromTarget) setConfirm({ kind: "template", key });
    else applyPreset(key);
  };

  const confirmAction = () => {
    if (!confirm) return;
    if (confirm.kind === "template") applyPreset(confirm.key);
    else {
      applyPreset(draft.templateKey);
      notify(t("storeTheme.resetDone"));
    }
    setConfirm(null);
  };

  const setHeroLayout = (layout: "FULL" | "STRIP" | "MEDIA_FULL") =>
    patchConfig((config) => ({
      ...config,
      hero: { ...config.hero, layout },
      sections: config.sections.map((row) => (row.key === "HERO" ? { ...row, variant: layout } : row)),
      ...(config.layout
        ? { layout: { blocks: config.layout.blocks.map((block) => (block.type === "HERO" ? { ...block, variant: layout } : block)) } }
        : {}),
    }), "config.hero.layout");

  /** AG3: edit layout.blocks; the legacy sections list (and hero layout) is derived from them like the backend does. */
  const setBlocks = (blocks: LayoutBlock[]) =>
    patchConfig((config) => {
      const sections = sectionsFromBlocks(blocks, config.sections);
      const heroVariant = sections.find((row) => row.key === "HERO")?.variant;
      return {
        ...config,
        layout: { blocks },
        sections,
        hero: heroVariant === "FULL" || heroVariant === "STRIP" || heroVariant === "MEDIA_FULL"
          ? { ...config.hero, layout: heroVariant }
          : config.hero,
      };
    }, "config.layout");

  const setHero = (field: "title" | "subtitle" | "ctaLabel" | "ctaLink", value: string) =>
    patchConfig((config) => ({ ...config, hero: { ...config.hero, [field]: value } }), `config.hero.${field}`);

  const setSection = (index: number, change: { enabled?: boolean; variant?: SectionVariant }) =>
    patchConfig((config) => {
      const target = config.sections[index];
      const hero = target.key === "HERO" && (change.variant === "FULL" || change.variant === "STRIP" || change.variant === "MEDIA_FULL")
        ? { ...config.hero, layout: change.variant }
        : config.hero;
      return {
        ...config,
        hero,
        sections: config.sections.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)),
      };
    }, `config.sections[${index}]`);

  const reorder = (from: number, to: number) =>
    patchConfig((config) => ({ ...config, sections: moveItem(config.sections, from, to) }), "config.sections");

  const setBanner = (index: number, change: Partial<ThemeBanner>) =>
    patchConfig((config) => ({
      ...config,
      banners: config.banners.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)),
    }), `config.banners[${index}]`);

  const addBanner = () =>
    patchConfig((config) => {
      if (config.banners.length >= 8) return config;
      return { ...config, banners: [...config.banners, { imageUrl: "", title: "", link: "" }] };
    }, "config.banners");

  const removeBanner = (index: number) =>
    patchConfig((config) => ({
      ...config,
      banners: config.banners.filter((_, rowIndex) => rowIndex !== index),
    }), "config.banners");

  const setTrustEnabled = (enabled: boolean) =>
    patchConfig((config) => ({
      ...config,
      trust: { ...config.trust, enabled },
      sections: config.sections.map((row) => (row.key === "TRUST" ? { ...row, enabled } : row)),
      ...(config.layout
        ? { layout: { blocks: config.layout.blocks.map((block) => (block.type === "TRUST" ? { ...block, enabled } : block)) } }
        : {}),
    }), "config.trust.enabled");

  const setTrustItem = (index: number, change: Partial<ThemeTrustItem>) =>
    patchConfig((config) => ({
      ...config,
      trust: {
        ...config.trust,
        items: config.trust.items.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)),
      },
    }), `config.trust.items[${index}]`);

  const setExtraColor = (key: (typeof EXTRA_COLOR_KEYS)[number], value: string | undefined) =>
    patchConfig((config) => {
      const colors = { ...config.colors };
      if (value && HEX.test(value)) colors[key] = value.toLowerCase();
      else delete colors[key];
      return { ...config, colors };
    }, `config.colors.${key}`);

  const setGradient = (which: GradientKey, next: ThemeGradient) =>
    patchConfig((config) => ({
      ...config,
      gradients: { ...config.gradients, [which]: next },
    }), `config.gradients.${which}`);

  const setProductCard = (change: Partial<ThemeProductCard>) =>
    patchConfig((config) => ({
      ...config,
      productCard: { ...config.productCard, ...change },
    }), "config.productCard");

  const save = async () => {
    if (saving || !canUpdate) return;
    if (COLOR_KEYS.some((key) => !HEX.test(draft.config.colors[key]))) {
      notify(t("storeTheme.errors.hex"));
      return;
    }
    if (EXTRA_COLOR_KEYS.some((key) => {
      const value = draft.config.colors[key];
      return value != null && value !== "" && !HEX.test(value);
    })) {
      notify(t("storeTheme.errors.hex"));
      return;
    }
    setSaving(true);
    setFieldErrors([]);
    try {
      const isCustom = draft.templateKey === "CUSTOM";
      const payload: ShopTheme = {
        ...draft,
        config: {
          ...chromePayload(stripLayout(draft.config), isCustom),
          ...(isCustom && draft.config.layout ? { layout: { blocks: blocksPayload(draft.config.layout.blocks) } } : {}),
          // AG2: mediaId only travels with CUSTOM themes (the backend also strips it for the other templates).
          banners: draft.config.banners
            .filter((row) => row.imageUrl.trim() || (draft.templateKey === "CUSTOM" && row.mediaId))
            .map(({ mediaId, ...row }) => (draft.templateKey === "CUSTOM" && mediaId ? { ...row, mediaId } : row)),
        },
      };
      const saved = normalizeTheme(await merchantApi.saveTheme(payload, shopId) as Partial<ShopTheme>);
      setBaseline(saved);
      setDraft(saved);
      notify(t("storeTheme.saved"));
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
        const detail = error.fieldErrors[0]?.message;
        notify(error.fieldErrors.length
          ? `${t("storeTheme.errors.validation")}${detail ? ` — ${detail}` : ""}`
          : (error.message || t("storeTheme.errors.save")));
      } else {
        notify(t("storeTheme.errors.save"));
      }
    } finally {
      setSaving(false);
    }
  };

  const previewShop: PublicShop = useMemo(() => ({
    name: shop.shopName || t("storeTheme.preview.shopName"),
    nameEn: shop.shopNameEn,
    description: shop.description,
    category: shop.category,
    address: shop.address,
    province: shop.province,
    district: shop.district,
    subdistrict: shop.subdistrict,
    postalCode: shop.postalCode,
    slug: shop.slug || "preview",
    path: `/shop/${shop.slug || "preview"}`,
    status: shop.status,
    logoUrl: shop.logoUrl,
    coverUrl: shop.coverUrl,
    shopId: shopId ?? 0,
    openingHours: shop.openingHours,
    createdAt: shop.createdAt || null,
    productCount: previewProducts.length > 0 ? previewProducts.length : null,
    theme: draft,
  }), [shop, shopId, draft, t, previewProducts.length]);

  const previewCatalog = useMemo(
    () => previewCatalogControls({ total: previewProducts.length, productState: "ok" }),
    [previewProducts.length],
  );

  if (loading) return <section className="store-form-card store-theme-card"><SkeletonForm fields={8} /></section>;
  if (loadFailed) {
    return (
      <section className="store-form-card store-theme-card">
        <LoadError message={t("storeTheme.loadError")} onRetry={() => setReloadKey((value) => value + 1)} retryLabel={t("storeTheme.retry")} />
      </section>
    );
  }

  const { config } = draft;
  const previewWidth = PREVIEW_WIDTHS[device];
  const zoom = stageWidth > 0 ? Math.min(1, stageWidth / previewWidth) : 1;
  const disabled = !canUpdate;
  const chromeProps = { config, disabled, shopId, shopSlug: shop.slug, errorFor, patch: patchConfig };

  const confirmCopy = confirm?.kind === "template"
    ? { title: t("storeTheme.confirm.template.title"), message: t("storeTheme.confirm.template.message"), label: t("storeTheme.confirm.template.confirm") }
    : { title: t("storeTheme.confirm.reset.title"), message: t("storeTheme.confirm.reset.message"), label: t("storeTheme.confirm.reset.confirm") };

  return (
    <div className="store-layout store-theme-layout">
      <section className="store-form-card store-theme-card">
        <header>
          <div>
            <h2>{t("storeTheme.title")}</h2>
            <p>{t("storeTheme.lead")}</p>
          </div>
          {dirty ? <div className="store-enabled"><span className="theme-dirty">{t("storeTheme.unsaved")}</span></div> : null}
        </header>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.templates.title")}</legend>
          <div className="sale-types">
            {SHOP_THEME_TEMPLATES.map((key) => {
              const Icon = TEMPLATE_ICONS[key];
              const active = draft.templateKey === key;
              return (
                <button
                  type="button"
                  key={key}
                  className={active ? "active" : ""}
                  aria-pressed={active}
                  disabled={disabled}
                  onClick={() => chooseTemplate(key)}
                >
                  <i>{active ? <Check /> : null}</i>
                  <Icon />
                  <span>
                    <b>{t(`storeTheme.templates.${key}.name`)}</b>
                    <small>{t(`storeTheme.templates.${key}.desc`)}</small>
                  </span>
                </button>
              );
            })}
          </div>
        </fieldset>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.media.title")}</legend>
          <p className="theme-hint">{t("storeTheme.media.hint")}</p>
          <div className="store-form-grid">
            {logoControl}
            {coverControl}
          </div>
        </fieldset>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.colors.title")}</legend>
          <div className="theme-grid">
            {COLOR_KEYS.map((key) => (
              <ColorField
                key={key}
                label={t(`storeTheme.colors.${key}`)}
                value={config.colors[key]}
                error={errorFor(`colors.${key}`)}
                hexError={t("storeTheme.errors.hex")}
                disabled={disabled}
                onChange={(value) => patchConfig((current) => ({ ...current, colors: { ...current.colors, [key]: value } }), `config.colors.${key}`)}
              />
            ))}
          </div>
          {themeContrastIssues(config.colors).length > 0 ? (
            <p className="form-field-warn" role="status">
              <CircleAlert size={14} aria-hidden="true" />
              {t("storeTheme.colors.contrastWarn")}
            </p>
          ) : null}
        </fieldset>

        {draft.templateKey === "CUSTOM" ? (
          <fieldset className="theme-block">
            <legend>{t("storeTheme.colors.extraTitle")}</legend>
            <div className="theme-grid">
              {EXTRA_COLOR_KEYS.map((key) => (
                <ColorField
                  key={key}
                  label={t(`storeTheme.colors.${key}`)}
                  value={config.colors[key] ?? config.colors.background}
                  error={errorFor(`colors.${key}`)}
                  hexError={t("storeTheme.errors.hex")}
                  disabled={disabled}
                  onChange={(value) => setExtraColor(key, value)}
                />
              ))}
            </div>
          </fieldset>
        ) : null}

        <fieldset className="theme-block">
          <legend>{t("storeTheme.style.title")}</legend>
          <div className="theme-grid">
            <FormField label={t("storeTheme.style.radius")} error={errorFor("style.radius")}>
              <input
                type="number"
                className="num"
                min={0}
                max={32}
                step={1}
                value={config.style.radius}
                disabled={disabled}
                onChange={(event) => {
                  const next = Number.parseInt(event.target.value, 10);
                  if (!Number.isFinite(next)) return;
                  patchConfig((current) => ({
                    ...current,
                    style: { ...current.style, radius: Math.max(0, Math.min(32, next)) },
                  }), "config.style.radius");
                }}
              />
            </FormField>
            <ThemeSelect
              label={t("storeTheme.style.cardStyle")}
              value={config.style.cardStyle}
              disabled={disabled}
              error={errorFor("style.cardStyle")}
              onChange={(event) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, cardStyle: event.target.value as ShopCardStyle },
              }), "config.style.cardStyle")}
            >
              {SHOP_CARD_STYLES.map((value) => (
                <option key={value} value={value}>{t(`storeTheme.style.card.${value}`)}</option>
              ))}
            </ThemeSelect>
            <ThemeSelect
              label={t("storeTheme.style.density")}
              value={config.style.density}
              disabled={disabled}
              error={errorFor("style.density")}
              onChange={(event) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, density: event.target.value as ShopDensity },
              }), "config.style.density")}
            >
              {SHOP_DENSITIES.map((value) => (
                <option key={value} value={value}>{t(`storeTheme.style.densityOpts.${value}`)}</option>
              ))}
            </ThemeSelect>
            <ThemeSelect
              label={t("storeTheme.style.buttonShape")}
              value={config.style.buttonShape}
              disabled={disabled}
              error={errorFor("style.buttonShape")}
              onChange={(event) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, buttonShape: event.target.value as ShopButtonShape },
              }), "config.style.buttonShape")}
            >
              {SHOP_BUTTON_SHAPES.map((value) => (
                <option key={value} value={value}>{t(`storeTheme.style.button.${value}`)}</option>
              ))}
            </ThemeSelect>
          </div>
        </fieldset>

        {draft.templateKey === "CUSTOM" ? (
          <>
            <fieldset className="theme-block">
              <legend>{t("storeTheme.style.extraTitle")}</legend>
              <div className="theme-grid">
                <ThemeTokenSelect
                  label={t("storeTheme.style.shadow")}
                  value={config.style.shadow ?? "NONE"}
                  disabled={disabled}
                  error={errorFor("style.shadow")}
                  options={SHOP_SHADOWS.map((value) => ({ value, label: t(`storeTheme.style.shadows.${value}`) }))}
                  onChange={(picked) => patchConfig((current) => ({
                    ...current,
                    style: { ...current.style, shadow: picked as ShopShadow },
                  }), "config.style.shadow")}
                />
                <div className="theme-trust-toggle">
                  <ToggleSwitch
                    checked={Boolean(config.style.headingTextShadow)}
                    disabled={disabled}
                    label={t("storeTheme.style.headingTextShadow")}
                    onText={t("storeTheme.sections.on")}
                    offText={t("storeTheme.sections.off")}
                    onChange={(headingTextShadow) => patchConfig((current) => ({
                      ...current,
                      style: { ...current.style, headingTextShadow },
                    }), "config.style.headingTextShadow")}
                  />
                </div>
              </div>
            </fieldset>

            <fieldset className="theme-block">
              <legend>{t("storeTheme.gradients.title")}</legend>
              {GRADIENT_KEYS.map((which) => (
                <GradientEditor
                  key={which}
                  title={t(`storeTheme.gradients.${which}`)}
                  angleLabel={t("storeTheme.gradients.angle")}
                  stopLabel={(index) => t("storeTheme.gradients.stop", { index: index + 1 })}
                  addStopLabel={t("storeTheme.gradients.addStop")}
                  removeStopLabel={t("storeTheme.gradients.removeStop")}
                  gradient={config.gradients?.[which]}
                  fallbackColors={config.colors}
                  disabled={disabled}
                  hexError={t("storeTheme.errors.hex")}
                  errorFor={errorFor}
                  prefix={`gradients.${which}`}
                  onChange={(next) => setGradient(which, next)}
                />
              ))}
            </fieldset>

            <fieldset className="theme-block">
              <legend>{t("storeTheme.productCard.title")}</legend>
              <div className="theme-grid">
                <ThemeTokenSelect
                  label={t("storeTheme.productCard.imageRatio")}
                  value={config.productCard?.imageRatio ?? "SQUARE"}
                  disabled={disabled}
                  error={errorFor("productCard.imageRatio")}
                  options={SHOP_IMAGE_RATIOS.map((value) => ({ value, label: t(`storeTheme.productCard.ratios.${value}`) }))}
                  onChange={(picked) => setProductCard({ imageRatio: picked as ShopImageRatio })}
                />
                <ThemeTokenSelect
                  label={t("storeTheme.productCard.ctaStyle")}
                  value={config.productCard?.ctaStyle ?? "ROUNDED"}
                  disabled={disabled}
                  error={errorFor("productCard.ctaStyle")}
                  options={SHOP_CTA_STYLES.map((value) => ({ value, label: t(`storeTheme.productCard.cta.${value}`) }))}
                  onChange={(picked) => setProductCard({ ctaStyle: picked as ShopCtaStyle })}
                />
                <ThemeTokenSelect
                  label={t("storeTheme.productCard.titleAlign")}
                  value={config.productCard?.titleAlign ?? "LEFT"}
                  disabled={disabled}
                  error={errorFor("productCard.titleAlign")}
                  options={SHOP_TITLE_ALIGNS.map((value) => ({ value, label: t(`storeTheme.productCard.align.${value}`) }))}
                  onChange={(picked) => setProductCard({ titleAlign: picked as ShopTitleAlign })}
                />
                <ToggleSwitch
                  checked={config.productCard?.showPrice !== false}
                  disabled={disabled}
                  label={t("storeTheme.productCard.showPrice")}
                  onText={t("storeTheme.sections.on")}
                  offText={t("storeTheme.sections.off")}
                  onChange={(showPrice) => setProductCard({ showPrice })}
                />
                <ToggleSwitch
                  checked={Boolean(config.productCard?.edgeToEdge)}
                  disabled={disabled}
                  label={t("storeTheme.productCard.edgeToEdge")}
                  onText={t("storeTheme.sections.on")}
                  offText={t("storeTheme.sections.off")}
                  onChange={(edgeToEdge) => setProductCard({ edgeToEdge })}
                />
              </div>
            </fieldset>
          </>
        ) : null}

        <fieldset className="theme-block">
          <legend>{t("storeTheme.font.title")}</legend>
          <ThemeSelect
            label={t("storeTheme.font.label")}
            value={config.font}
            disabled={disabled}
            error={errorFor("font")}
            onChange={(event) => patchConfig((current) => ({ ...current, font: event.target.value as ShopThemeFontKey }), "config.font")}
          >
            {SHOP_THEME_FONTS.map((font) => <option key={font} value={font}>{t(`storeTheme.font.${font}`)}</option>)}
          </ThemeSelect>
          <p className="theme-font-sample" style={{ fontFamily: shopThemeFontStack(config.font) }}>{t("storeTheme.font.sample")}</p>
        </fieldset>

        {draft.templateKey === "CUSTOM" ? <FontPanel {...chromeProps} /> : null}

        <fieldset className="theme-block">
          <legend>{t("storeTheme.hero.title")}</legend>
          <div className="theme-grid">
            <ThemeSelect
              label={t("storeTheme.hero.layout")}
              value={config.hero.layout}
              disabled={disabled}
              error={errorFor("hero.layout")}
              onChange={(event) => setHeroLayout(event.target.value as "FULL" | "STRIP" | "MEDIA_FULL")}
            >
              <option value="FULL">{t("storeTheme.variants.FULL")}</option>
              <option value="STRIP">{t("storeTheme.variants.STRIP")}</option>
              {draft.templateKey === "CUSTOM" ? <option value="MEDIA_FULL">{t("storeTheme.variants.MEDIA_FULL")}</option> : null}
            </ThemeSelect>
            <FormField label={t("storeTheme.hero.heroTitle")} error={errorFor("hero.title")}>
              <input value={config.hero.title} maxLength={120} disabled={disabled} placeholder={shop.shopName} onChange={(event) => setHero("title", event.target.value)} />
            </FormField>
          </div>
          <FormTextarea
            label={t("storeTheme.hero.subtitle")}
            value={config.hero.subtitle}
            maxLength={240}
            disabled={disabled}
            error={errorFor("hero.subtitle")}
            onChange={(event) => setHero("subtitle", event.target.value)}
          />
          <div className="theme-grid">
            <FormField label={t("storeTheme.hero.ctaLabel")} error={errorFor("hero.ctaLabel")}>
              <input value={config.hero.ctaLabel} maxLength={120} disabled={disabled} onChange={(event) => setHero("ctaLabel", event.target.value)} />
            </FormField>
            <FormField label={t("storeTheme.hero.ctaLink")} error={errorFor("hero.ctaLink")}>
              <input value={config.hero.ctaLink} disabled={disabled} placeholder="/shops  |  https://" onChange={(event) => setHero("ctaLink", event.target.value)} />
            </FormField>
          </div>
          <p className="theme-hint">{t("storeTheme.hero.ctaHint")}</p>
        </fieldset>

        {draft.templateKey === "CUSTOM" && config.hero.layout === "MEDIA_FULL" ? (
          <HeroMediaPanel {...chromeProps} />
        ) : null}

        <fieldset className="theme-block">
          <legend>{t("storeTheme.sections.bannersTitle")}</legend>
          <p className="theme-hint">{t("storeTheme.sections.bannersHint")}</p>
          <FieldError text={errorFor("banners")} />
          <ul className="theme-banner-list">
            {config.banners.map((banner, index) => (
              <li key={index} className="theme-banner-row">
                {draft.templateKey === "CUSTOM" ? (
                  <MediaField label={t("storeTheme.media.bannerImage")} error={errorFor(`banners[${index}].mediaId`)}>
                    <ThemeMediaPicker
                      kind="IMAGE"
                      value={banner.mediaId}
                      shopId={shopId}
                      shopSlug={shop.slug}
                      disabled={disabled}
                      onChange={(mediaId, item) => setBanner(index, mediaId
                        ? { mediaId, imageUrl: item?.url ?? banner.imageUrl }
                        : { mediaId: undefined, imageUrl: "" })}
                    />
                  </MediaField>
                ) : null}
                <FormField label={t("storeTheme.sections.bannerImage")} error={errorFor(`banners[${index}].imageUrl`)}>
                  <input
                    value={banner.mediaId ? "" : banner.imageUrl}
                    disabled={disabled || Boolean(banner.mediaId)}
                    placeholder="https://…  |  /media/…"
                    onChange={(event) => setBanner(index, { imageUrl: event.target.value, mediaId: undefined })}
                  />
                </FormField>
                <div className="theme-grid">
                  <FormField label={t("storeTheme.sections.bannerTitle")} error={errorFor(`banners[${index}].title`)}>
                    <input
                      value={banner.title || ""}
                      maxLength={80}
                      disabled={disabled}
                      onChange={(event) => setBanner(index, { title: event.target.value })}
                    />
                  </FormField>
                  <FormField label={t("storeTheme.sections.bannerLink")} error={errorFor(`banners[${index}].link`)}>
                    <input
                      value={banner.link || ""}
                      disabled={disabled}
                      placeholder="/shops  |  https://"
                      onChange={(event) => setBanner(index, { link: event.target.value })}
                    />
                  </FormField>
                </div>
                <button
                  type="button"
                  className="theme-banner-remove"
                  disabled={disabled}
                  aria-label={t("storeTheme.sections.bannerRemove")}
                  onClick={() => removeBanner(index)}
                >
                  <Trash2 />{t("storeTheme.sections.bannerRemove")}
                </button>
              </li>
            ))}
          </ul>
          <button type="button" className="theme-banner-add" disabled={disabled || config.banners.length >= 8} onClick={addBanner}>
            <Plus />{t("storeTheme.sections.bannerAdd")}
          </button>
        </fieldset>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.sections.trustTitle")}</legend>
          <div className="theme-trust-toggle">
            <ToggleSwitch
              checked={config.trust.enabled}
              disabled={disabled}
              label={t("storeTheme.sections.trustEnabled")}
              onText={t("storeTheme.sections.on")}
              offText={t("storeTheme.sections.off")}
              onChange={setTrustEnabled}
            />
          </div>
          <FieldError text={errorFor("trust")} />
          <ul className="theme-trust-list">
            {config.trust.items.map((item, index) => (
              <li key={index} className={`theme-trust-row${item.enabled ? "" : " is-off"}`}>
                <ToggleSwitch
                  checked={item.enabled}
                  disabled={disabled || !config.trust.enabled}
                  label={`${t("storeTheme.sections.trustLabel")} ${index + 1}`}
                  onText={t("storeTheme.sections.on")}
                  offText={t("storeTheme.sections.off")}
                  onChange={(enabled) => setTrustItem(index, { enabled })}
                />
                <ThemeSelect
                  label={t("storeTheme.sections.trustIcon")}
                  value={item.icon}
                  disabled={disabled || !config.trust.enabled}
                  error={errorFor(`trust.items[${index}].icon`)}
                  onChange={(event) => setTrustItem(index, { icon: event.target.value as ShopTrustIcon })}
                >
                  {SHOP_TRUST_ICONS.map((icon) => (
                    <option key={icon} value={icon}>{t(`storeTheme.sections.trustIcons.${icon}`)}</option>
                  ))}
                </ThemeSelect>
                <FormField label={t("storeTheme.sections.trustLabel")} error={errorFor(`trust.items[${index}].label`)}>
                  <input
                    value={item.label}
                    maxLength={60}
                    disabled={disabled || !config.trust.enabled}
                    onChange={(event) => setTrustItem(index, { label: event.target.value })}
                  />
                </FormField>
              </li>
            ))}
          </ul>
        </fieldset>

        {draft.templateKey === "CUSTOM" ? (
          <>
            <HeaderPanel {...chromeProps} />
            <FooterPanel {...chromeProps} />
          </>
        ) : null}

        {draft.templateKey === "CUSTOM" && config.layout ? (
          <ShopBlocksEditor
            blocks={config.layout.blocks}
            colors={config.colors}
            disabled={disabled}
            shopId={shopId}
            shopSlug={shop.slug}
            errorFor={errorFor}
            onChange={setBlocks}
          />
        ) : (
        <fieldset className="theme-block">
          <legend>{t("storeTheme.sections.title")}</legend>
          <p className="theme-hint">{t("storeTheme.sections.hint")}</p>
          <FieldError text={errorFor("sections")} />
          <ul className="theme-sections">
            {config.sections.map((section, index) => {
              const variants = sectionSupportsVariant(section.key) ? variantsForSection(section.key) : [];
              const rowError = sectionError(index);
              return (
                <li
                  key={section.key}
                  className={`theme-section-row${section.enabled ? "" : " is-off"}${dragIndex === index ? " is-dragging" : ""}`}
                  draggable={!disabled}
                  onDragStart={() => setDragIndex(index)}
                  onDragEnd={() => setDragIndex(null)}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() => {
                    if (dragIndex != null) reorder(dragIndex, index);
                    setDragIndex(null);
                  }}
                >
                  <span className="theme-section-grip" aria-hidden="true"><GripVertical /></span>
                  <b
                    className="theme-section-name"
                    title={isShopSectionAvailable(section.key) ? undefined : t("storeTheme.sections.comingSoonHint")}
                  >
                    {t(`storeTheme.sections.${section.key as ShopSectionKey}`)}
                    {isShopSectionAvailable(section.key) ? null : ` · ${t("storeTheme.sections.comingSoon")}`}
                  </b>
                  {variants.length ? (
                    <ThemeSelect inline
                      label={`${t(`storeTheme.sections.${section.key}`)} · ${t("storeTheme.sections.variant")}`}
                      value={section.variant ?? variants[0] ?? ""}
                      disabled={disabled}
                      aria-invalid={Boolean(rowError)}
                      onChange={(event) => setSection(index, { variant: event.target.value as SectionVariant })}
                    >
                      {variants.map((variant) => <option key={variant} value={variant ?? ""}>{t(`storeTheme.variants.${variant}`)}</option>)}
                    </ThemeSelect>
                  ) : <span />}
                  <ToggleSwitch
                    checked={section.enabled}
                    disabled={disabled}
                    label={t(`storeTheme.sections.${section.key}`)}
                    onText={t("storeTheme.sections.on")}
                    offText={t("storeTheme.sections.off")}
                    onChange={(enabled) => setSection(index, { enabled })}
                  />
                  <span className="theme-section-move">
                    <button type="button" disabled={disabled || index === 0} aria-label={t("storeTheme.sections.moveUp")} title={t("storeTheme.sections.moveUp")} onClick={() => reorder(index, index - 1)}><ChevronUp /></button>
                    <button type="button" disabled={disabled || index === config.sections.length - 1} aria-label={t("storeTheme.sections.moveDown")} title={t("storeTheme.sections.moveDown")} onClick={() => reorder(index, index + 1)}><ChevronDown /></button>
                  </span>
                  {rowError ? <FieldError text={rowError} /> : null}
                </li>
              );
            })}
          </ul>
        </fieldset>
        )}

        <div className="pm-actions theme-actions">
          <button type="button" disabled={disabled || saving} title={deniedTitle} onClick={() => setConfirm({ kind: "reset" })}>
            <RotateCcw />{t("storeTheme.actions.reset")}
          </button>
          <button type="button" className="primary" disabled={disabled || saving || !dirty} title={deniedTitle} onClick={() => void save()}>
            {saving ? t("storeTheme.actions.saving") : t("storeTheme.actions.save")}
          </button>
        </div>
      </section>

      <aside className="store-side">
        <section className="store-preview store-theme-preview">
          <header>
            <h2>{t("storeTheme.preview.title")}</h2>
            <div className="theme-device-toggle" role="group" aria-label={t("storeTheme.preview.devices")}>
              {DEVICES.map(({ key, icon: Icon }) => (
                <button
                  type="button"
                  key={key}
                  className={device === key ? "active" : ""}
                  aria-pressed={device === key}
                  aria-label={`${t(`storeTheme.preview.${key}`)} ${PREVIEW_WIDTHS[key]}px`}
                  title={`${t(`storeTheme.preview.${key}`)} · ${PREVIEW_WIDTHS[key]}px`}
                  onClick={() => setDevice(key)}
                >
                  <Icon />
                </button>
              ))}
            </div>
          </header>
          <div className="theme-preview-stage" ref={stageRef}>
            <div className="theme-preview-device" style={{ width: previewWidth, zoom }}>
              <HeroStickerDragContext.Provider value={stickerDrag}>
                <ShopStorefront shop={previewShop} products={previewProducts} catalog={previewCatalog} previewWidth={previewWidth} />
              </HeroStickerDragContext.Provider>
            </div>
          </div>
          <p className="theme-hint">{t("storeTheme.preview.hint", { width: previewWidth })}</p>
        </section>
      </aside>

      {confirm ? (
        <ConfirmDialog
          title={confirmCopy.title}
          message={confirmCopy.message}
          confirmLabel={confirmCopy.label}
          cancelLabel={t("storeTheme.confirm.cancel")}
          onConfirm={confirmAction}
          onCancel={() => setConfirm(null)}
        />
      ) : null}
    </div>
  );
}
