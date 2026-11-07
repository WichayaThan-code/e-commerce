import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  ArrowLeft, Check, ChevronDown, ImagePlus, GalleryHorizontal, LayoutGrid, List, ChevronUp, CircleAlert, GripVertical, Monitor, Palette, Plus, RotateCcw, Shirt, Smartphone, Store, Tablet, Trash2, UtensilsCrossed,
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
import SearchableSelect from "../SearchableSelect";
import { chromePayload, FontPanel, FooterPanel, HeaderPanel, HeroMediaPanel } from "./ShopChromeEditor";
import { HeroStickerDragContext, type StickerDragApi } from "../shop/sections/HeroMediaFull";
import { ColorField, GradientEditor, HEX, ThemeTokenSelect } from "./ThemeEditorParts";
import ShopStorefront from "../shop/ShopStorefront";
import { isShopSectionAvailable } from "../shop/sections/availability";
import { previewCatalogControls, shopApiSort } from "../shop/sections/types";
import { useShopCategories } from "../../hooks/useShopCategories";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { localizedText } from "../../services/catalogText";
import {
  ApiError, mediaUrl, merchantApi, shopApi,
  type ApiFieldError, type MerchantSettings, type PublicProduct, type PublicShop,
} from "../../services/api";
import {
  ensureThemeFontLink, normalizeTheme, PREVIEW_WIDTHS, presetForTemplate, registerThemePresets, sectionsFromBlocks,
  sectionSupportsVariant, type LayoutBlock,
  SHOP_BUTTON_SHAPES, SHOP_CARD_STYLES, SHOP_CTA_STYLES, SHOP_DENSITIES, SHOP_IMAGE_RATIOS,
  SHOP_SHADOWS, SHOP_THEME_FONTS, SHOP_THEME_PICKER_TEMPLATES,
  shopThemeFontStack, themeContrastIssues, variantsForSection,
  SHOP_TITLE_ALIGNS, SHOP_TRUST_ICONS, MAX_HERO_SLIDES,
  type SectionVariant, type ShopButtonShape, type ShopCardStyle, type ShopCtaStyle, type ShopDensity, type ShopImageRatio,
  type ShopSectionKey, type ShopShadow, type ShopTheme,
  type ShopThemeFontKey, type ShopThemeTemplateKey, type ShopTitleAlign, type ShopTrustIcon, type ThemeBanner, type ThemeColors,
  type ThemeConfig, type ThemeGradient, type ThemeProductCard,
  type ThemeTrustItem, type ThemeHeroSlide, type ThemeIntro, usesTsfTree,
} from "../../theme/shopTheme";
import "../../styles/form-fields.css";
import "../../styles/sale-types.css";
import "../../styles/theme-token-select.css";
import "../../styles/theme-media-picker.css";
import "../../styles/theme-editor-controls.css";
import "../../styles/theme-customizer.css";

type HeroLayoutValue = "FULL" | "STRIP" | "MEDIA_FULL" | "SPLIT" | "POSTER";
const HERO_LAYOUT_VALUES = new Set<string>(["FULL", "STRIP", "MEDIA_FULL", "SPLIT", "POSTER"]);
function isHeroLayoutValue(value: unknown): value is HeroLayoutValue {
  return typeof value === "string" && HERO_LAYOUT_VALUES.has(value);
}

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
  onDirtyChange: (dirty: boolean) => void;
  notify: (message: string) => void;
  /** Customizer top bar "← back" (the page guards unsaved changes). */
  onBack?: () => void;
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
const PREVIEW_CATALOG_SIZE = 30;

/** WCAG contrast ratio of two #rgb/#rrggbb colours (non-hex → 21, i.e. no warning). */
function contrastRatio(a?: string | null, b?: string | null) {
  const lum = (hex?: string | null) => {
    const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec((hex ?? "").trim());
    if (!m) return null;
    const h = m[1].length === 3 ? m[1].split("").map((c) => c + c).join("") : m[1];
    const [r, g, bl] = [0, 2, 4].map((i) => {
      const v = parseInt(h.slice(i, i + 2), 16) / 255;
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const la = lum(a); const lb = lum(b);
  if (la == null || lb == null) return 21;
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

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

/** Artifact ThemeCustomizer segmented control (.tsf-seg): one pressed button per option. */
function ThemeSegmented({ label, value, options, disabled, error, hideLabel, onChange }: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  disabled?: boolean;
  error?: string;
  hideLabel?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <div className={`form-field tc-seg-field${hideLabel ? " tc-seg-nolabel" : ""}`}>
      <span className="form-label">{label}</span>
      <div className="tc-seg" role="group" aria-label={label} aria-invalid={Boolean(error)}>
        {options.map((opt) => (
          <button key={opt.value} type="button" aria-pressed={opt.value === value} disabled={disabled} onClick={() => onChange(opt.value)}>{opt.label}</button>
        ))}
      </div>
      {error ? <FieldError text={error} /> : null}
    </div>
  );
}

/** Artifact colour row (.tsf-color): swatch, label, mono hex on the right. */
function ColorRow({ label, value, disabled, error, hexError, onChange }: {
  label: string; value: string; disabled?: boolean; error?: string; hexError: string; onChange: (value: string) => void;
}) {
  const valid = HEX.test(value);
  return (
    <div className={`tc-color${valid ? "" : " is-invalid"}`}>
      <input type="color" className="tc-color-swatch" aria-label={label} value={valid && value.length === 7 ? value : "#000000"} disabled={disabled} onChange={(event) => onChange(event.target.value)} />
      <span className="tc-color-name">{label}</span>
      <input className="tc-color-hex" aria-label={`${label} HEX`} value={value} maxLength={7} spellCheck={false} disabled={disabled} onChange={(event) => onChange(event.target.value.trim())} />
      {error || !valid ? <FieldError text={error || hexError} /> : null}
    </div>
  );
}

/** Mini template preview (artifact .tsf-tpl): header band, title bars, product grid in the preset colours. */
function TemplateThumb({ colors }: { colors: ThemeColors }) {
  return (
    <span className="tc-tpl-thumb" aria-hidden="true" style={{ background: colors.background }}>
      <span className="tc-tpl-band" style={{ background: colors.primary }}>
        <i style={{ background: colors.background }} />
        <i style={{ background: colors.accent ?? colors.secondary }} />
      </span>
      <span className="tc-tpl-grid">{[0, 1, 2, 3].map((n) => <i key={n} style={{ background: colors.text }} />)}</span>
    </span>
  );
}

export default function ShopThemeEditor({
  shopId, shop, canUpdate, deniedTitle, logoControl, onDirtyChange, notify, onBack,
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
  const [device, setDevice] = useState<DeviceKey>("desktop");
  const [panelTab, setPanelTab] = useState<"template" | "brand" | "hero" | "sections">("template");
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
        // CUSTOM is retired from the picker — migrate the editor draft to GENERAL so merchants can save the switch.
        if (loaded.templateKey === "CUSTOM") {
          const general = map.GENERAL ?? { templateKey: "GENERAL" as const, config: presetForTemplate("GENERAL") };
          setBaseline(loaded);
          setDraft({ templateKey: "GENERAL", config: stripLayout(structuredClone(general.config)) });
        } else {
          setBaseline(loaded);
          setDraft(loaded);
        }
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
    // Preview parity: same public request as the store home (useShopCatalog default: featured sort, 30 per page)
    // so FEATURED / BESTSELLERS (sold counts) match /shop/:slug.
    shopApi.products(shop.slug, { size: PREVIEW_CATALOG_SIZE, page: 0, sort: shopApiSort("featured") })
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
    if (draft.config.fontBody) ensureThemeFontLink(draft.config.fontBody);
  }, [draft.config.font, draft.config.fontBody]);

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

  const setIntro = (change: Partial<ThemeIntro>, field: string) =>
    patchConfig((current) => ({ ...current, intro: { ...(current.intro ?? {}), ...change } }), "intro." + field);

  const applyPreset = (key: ShopThemeTemplateKey) => {
    const preset = presetFor(key);
    setFieldErrors([]);
    // Full preset swap — colors, font, style, sections/variants, banners, trust (no GENERAL leftovers).
    const config = structuredClone(preset.config) as ThemeConfig;
    setDraft({
      templateKey: key,
      config: stripLayout(config),
    });
  };

  const chooseTemplate = (key: ShopThemeTemplateKey) => {
    if (key === draft.templateKey) return;
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


  // Hero slider (every template): max MAX_HERO_SLIDES shop-media images, saved with the theme.
  const setSlides = (change: (slides: ThemeHeroSlide[]) => ThemeHeroSlide[]) =>
    patchConfig((config) => ({ ...config, hero: { ...config.hero, slides: change(config.hero.slides ?? []).slice(0, MAX_HERO_SLIDES) } }), "config.hero.slides");
  const addSlide = (slide: ThemeHeroSlide) => setSlides((slides) => (slides.some((row) => row.slideMediaId === slide.slideMediaId) ? slides : [...slides, slide]));
  const removeSlide = (index: number) => setSlides((slides) => slides.filter((_, rowIndex) => rowIndex !== index));
  const moveSlide = (index: number, delta: number) => setSlides((slides) => {
    const target = index + delta;
    if (target < 0 || target >= slides.length) return slides;
    const next = [...slides];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });
  const setHeroLayout = (layout: HeroLayoutValue) =>
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
        hero: isHeroLayoutValue(heroVariant)
          ? { ...config.hero, layout: heroVariant }
          : config.hero,
      };
    }, "config.layout");

  const setHero = (field: "title" | "subtitle" | "ctaLabel" | "ctaLink" | "eyebrow" | "ctaSecondaryLabel" | "ctaSecondaryLink", value: string) =>
    patchConfig((config) => ({ ...config, hero: { ...config.hero, [field]: value } }), `config.hero.${field}`);

  const setHeroBadge = (slot: number, value: string) =>
    patchConfig((config) => {
      const next = [0, 1, 2].map((index) => (index === slot ? value : (config.hero.badges?.[index] ?? "")));
      while (next.length && !next[next.length - 1].trim()) next.pop();
      return { ...config, hero: { ...config.hero, badges: next } };
    }, "config.hero.badges");

  const setAnnouncement = (slot: number, value: string) =>
    patchConfig((config) => {
      const next = [0, 1].map((index) => (index === slot ? value : (config.announcement?.items?.[index] ?? "")));
      while (next.length && !next[next.length - 1].trim()) next.pop();
      return { ...config, announcement: { items: next } };
    }, "config.announcement");

  const setSection = (index: number, change: { enabled?: boolean; variant?: SectionVariant }) =>
    patchConfig((config) => {
      const target = config.sections[index];
      const hero = target.key === "HERO" && isHeroLayoutValue(change.variant)
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

  const previewCategories = useShopCategories(shop.slug || null);
  const { localeTag: previewLocale } = usePublicTranslation();
  const previewCategoryNames = useMemo(
    () => new Map(previewCategories.rows.map((row) => [row.id, localizedText(previewLocale, row.nameTh, row.nameEn, row.nameZh) || row.nameTh])),
    [previewCategories.rows, previewLocale],
  );
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
    <div className="tc-shell">
      <header className="tc-topbar">
        {onBack ? <button type="button" className="tc-back" onClick={onBack}><ArrowLeft />{t("storeTheme.customizer.back")}</button> : null}
        <div className="tc-title">
          <h1>{t("storeTheme.title")}</h1>
          <p>{t("storeTheme.lead")}</p>
        </div>
        <div className="theme-device-toggle tc-devices" role="group" aria-label={t("storeTheme.preview.devices")}>
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
              <Icon /><span>{PREVIEW_WIDTHS[key]}</span>
            </button>
          ))}
        </div>
        <div className="tc-actions">
          {dirty ? <span className="theme-dirty">{t("storeTheme.unsaved")}</span> : null}
          <button type="button" disabled={disabled || saving} title={deniedTitle} onClick={() => setConfirm({ kind: "reset" })}>
            <RotateCcw />{t("storeTheme.actions.reset")}
          </button>
          <button type="button" className="primary" disabled={disabled || saving || !dirty} title={deniedTitle} onClick={() => void save()}>
            {saving ? t("storeTheme.actions.saving") : t("storeTheme.customizer.publish")}
          </button>
        </div>
      </header>
      <div className="tc-body">
      <section className="tc-panel store-theme-card">
        <nav className="tc-tabs" role="tablist" aria-label={t("storeTheme.customizer.tabs")}>
          <button type="button" role="tab" aria-selected={panelTab === "template"} className={panelTab === "template" ? "active" : ""} onClick={() => setPanelTab("template")}><LayoutGrid aria-hidden="true" /><span>{t("storeTheme.customizer.tab.templates")}</span></button>
          <button type="button" role="tab" aria-selected={panelTab === "brand"} className={panelTab === "brand" ? "active" : ""} onClick={() => setPanelTab("brand")}><Palette aria-hidden="true" /><span>{t("storeTheme.customizer.tab.brand")}</span></button>
          <button type="button" role="tab" aria-selected={panelTab === "hero"} className={panelTab === "hero" ? "active" : ""} onClick={() => setPanelTab("hero")}><GalleryHorizontal aria-hidden="true" /><span>{t("storeTheme.customizer.tab.hero")}</span></button>
          <button type="button" role="tab" aria-selected={panelTab === "sections"} className={panelTab === "sections" ? "active" : ""} onClick={() => setPanelTab("sections")}><List aria-hidden="true" /><span>{t("storeTheme.customizer.tab.sections")}</span></button>
        </nav>
        <div className="tc-panel-body">
        {panelTab === "template" ? (<>
        <fieldset className="theme-block">
          <legend>{t("storeTheme.tc.templateHead")}</legend>
          <div className="tc-tpls">
            {SHOP_THEME_PICKER_TEMPLATES.map((key) => {
              const active = draft.templateKey === key;
              const colors = presetFor(key).config.colors;
              const dots = [colors.primary, colors.secondary, colors.accent ?? colors.text, colors.background];
              return (
                <button
                  type="button"
                  key={key}
                  className={`tc-tpl${active ? " active" : ""}`}
                  aria-pressed={active}
                  disabled={disabled}
                  onClick={() => chooseTemplate(key)}
                >
                  <TemplateThumb colors={colors} />
                  <b>{t(`storeTheme.tc.tpl.${key}.name`)}</b>
                  <small>{t(`storeTheme.tc.tpl.${key}.desc`)}</small>
                  <span className="tc-tpl-dots">{dots.map((c, n) => <i key={n} style={{ background: c }} />)}</span>
                </button>
              );
            })}
          </div>
          <p className="theme-hint tc-note">{t("storeTheme.tc.templateNote")}</p>
        </fieldset>
        </>) : null}
        {panelTab === "brand" ? (<>
        <fieldset className="theme-block">
          <legend>{t("storeTheme.tc.logo")}</legend>
          <div className="tc-upload tc-logo-box">{logoControl}</div>
          <FormField label={t("storeTheme.tc.shopName")}>
            <input value={shop.shopName} readOnly aria-readonly="true" title={t("storeTheme.tc.shopNameHint")} />
          </FormField>
          {draft.templateKey === "CUSTOM" && config.header ? (
            <ThemeSegmented
              label={t("storeTheme.tc.logoPos")}
              value={config.header.layout}
              disabled={disabled}
              error={errorFor("header.layout")}
              options={[{ value: "LOGO_LEFT", label: t("storeTheme.tc.left") }, { value: "LOGO_CENTER", label: t("storeTheme.tc.center") }]}
              onChange={(value) => patchConfig((current) => (current.header
                ? { ...current, header: { ...current.header, layout: value as NonNullable<ThemeConfig["header"]>["layout"] } }
                : current), "config.header.layout")}
            />
          ) : draft.templateKey !== "CUSTOM" ? (
            <ThemeSegmented
              label={t("storeTheme.tc.logoPos")}
              value={config.brand?.logoPosition ?? "LEFT"}
              disabled={disabled}
              error={errorFor("brand.logoPosition")}
              options={[{ value: "LEFT", label: t("storeTheme.tc.left") }, { value: "CENTER", label: t("storeTheme.tc.center") }]}
              onChange={(value) => patchConfig((current) => ({ ...current, brand: { ...current.brand, logoPosition: value as "LEFT" | "CENTER" } }), "config.brand.logoPosition")}
            />
          ) : null}
        </fieldset>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.tc.colors")}</legend>
          <div className="tc-colors">
            {(["primary", "secondary", "accent", "background", "text"] as const).map((key) => (
              key === "accent" ? (
                <ColorRow
                  key={key}
                  label={t("storeTheme.tc.color.accent")}
                  value={config.colors.accent ?? config.colors.primary}
                  error={errorFor("colors.accent")}
                  hexError={t("storeTheme.errors.hex")}
                  disabled={disabled}
                  onChange={(value) => setExtraColor("accent", value)}
                />
              ) : (
                <ColorRow
                  key={key}
                  label={t(`storeTheme.tc.color.${key}`)}
                  value={config.colors[key]}
                  error={errorFor(`colors.${key}`)}
                  hexError={t("storeTheme.errors.hex")}
                  disabled={disabled}
                  onChange={(value) => patchConfig((current) => ({ ...current, colors: { ...current.colors, [key]: value } }), `config.colors.${key}`)}
                />
              )
            ))}
          </div>
          {themeContrastIssues(config.colors).length > 0 ? (
            <p className="form-field-warn" role="status">
              <CircleAlert size={14} aria-hidden="true" />
              {t("storeTheme.colors.contrastWarn")}
            </p>
          ) : null}
        </fieldset>

        <fieldset className="theme-block">
          <legend>{t("storeTheme.tc.fonts")}</legend>
          <ThemeSelect
            label={t("storeTheme.tc.fontHeading")}
            value={config.font}
            disabled={disabled}
            error={errorFor("font")}
            onChange={(event) => patchConfig((current) => ({ ...current, font: event.target.value as ShopThemeFontKey }), "config.font")}
          >
            {SHOP_THEME_FONTS.map((font) => <option key={font} value={font}>{t(`storeTheme.font.${font}`)}</option>)}
          </ThemeSelect>
          <p className="theme-font-sample" style={{ fontFamily: shopThemeFontStack(config.font) }}>{t("storeTheme.font.sample")}</p>
          <ThemeSelect
            label={t("storeTheme.tc.fontBody")}
            value={config.fontBody ?? config.font}
            disabled={disabled}
            error={errorFor("fontBody")}
            onChange={(event) => patchConfig((current) => ({ ...current, fontBody: event.target.value as ShopThemeFontKey }), "config.fontBody")}
          >
            {SHOP_THEME_FONTS.map((font) => <option key={font} value={font}>{t(`storeTheme.font.${font}`)}</option>)}
          </ThemeSelect>
          <p className="theme-font-sample" style={{ fontFamily: shopThemeFontStack(config.fontBody ?? config.font) }}>{t("storeTheme.font.sample")}</p>
        </fieldset>

        {draft.templateKey === "CUSTOM" ? (
          <fieldset className="theme-block">
            <legend>{t("storeTheme.colors.extraTitle")}</legend>
            <div className="theme-grid">
              {EXTRA_COLOR_KEYS.filter((key) => key !== "accent").map((key) => (
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
            <ThemeSegmented
              label={t("storeTheme.style.cardStyle")}
              value={config.style.cardStyle}
              disabled={disabled}
              error={errorFor("style.cardStyle")}
              options={SHOP_CARD_STYLES.map((value) => ({ value, label: t(`storeTheme.style.card.${value}`) }))}
              onChange={(value) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, cardStyle: value as ShopCardStyle },
              }), "config.style.cardStyle")}
            />
            <ThemeSegmented
              label={t("storeTheme.style.density")}
              value={config.style.density}
              disabled={disabled}
              error={errorFor("style.density")}
              options={SHOP_DENSITIES.map((value) => ({ value, label: t(`storeTheme.style.densityOpts.${value}`) }))}
              onChange={(value) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, density: value as ShopDensity },
              }), "config.style.density")}
            />
            <ThemeSegmented
              label={t("storeTheme.style.buttonShape")}
              value={config.style.buttonShape}
              disabled={disabled}
              error={errorFor("style.buttonShape")}
              options={SHOP_BUTTON_SHAPES.map((value) => ({ value, label: t(`storeTheme.style.button.${value}`) }))}
              onChange={(value) => patchConfig((current) => ({
                ...current,
                style: { ...current.style, buttonShape: value as ShopButtonShape },
              }), "config.style.buttonShape")}
            />
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

        {draft.templateKey === "CUSTOM" ? <FontPanel {...chromeProps} /> : null}

        {draft.templateKey === "CUSTOM" ? (
          <>
            <HeaderPanel {...chromeProps} />
            <FooterPanel {...chromeProps} />
          </>
        ) : null}
        {(() => {
          const warn = [
            contrastRatio(config.colors.text, config.colors.background) < 4.5 ? t("storeTheme.customizer.contrastText") : null,
            contrastRatio(config.colors.primary, config.colors.background) < 3 ? t("storeTheme.customizer.contrastPrimary") : null,
          ].filter(Boolean);
          return warn.length ? <p className="tc-contrast-warn" role="status"><CircleAlert />{warn.join(" · ")}</p> : null;
        })()}
        </>) : null}
        {panelTab === "hero" ? (<>
        {(() => {
          // Item 2: primary เต็มจอ / แบ่งครึ่ง / โปสเตอร์; legacy STRIP (+ MEDIA_FULL for CUSTOM / GENERAL) stay as extra options.
          const layouts: HeroLayoutValue[] = draft.templateKey === "CUSTOM" || draft.templateKey === "GENERAL" ? ["FULL", "SPLIT", "POSTER", "STRIP", "MEDIA_FULL"] : ["FULL", "SPLIT", "POSTER", "STRIP"];
          const slides = config.hero.slides ?? [];
          const general = draft.templateKey === "GENERAL";
          return (
            <>
              <fieldset className="theme-block">
                <legend>{t("storeTheme.tc.heroLayout")}</legend>
                <ThemeSegmented
                  label={t("storeTheme.tc.heroLayout")}
                  hideLabel
                  value={config.hero.layout}
                  disabled={disabled}
                  error={errorFor("hero.layout")}
                  options={layouts.map((value) => ({ value, label: t(`storeTheme.variants.${value}`) }))}
                  onChange={(value) => setHeroLayout(value as HeroLayoutValue)}
                />
                {draft.templateKey === "CUSTOM" ? (
                  <ThemeSegmented
                    label={t("storeTheme.tc.textAlign")}
                    value={config.hero.textAlign ?? "LEFT"}
                    disabled={disabled}
                    error={errorFor("hero.textAlign")}
                    options={[{ value: "LEFT", label: t("storeTheme.tc.left") }, { value: "CENTER", label: t("storeTheme.tc.center") }]}
                    onChange={(value) => patchConfig((current) => ({ ...current, hero: { ...current.hero, textAlign: value as "LEFT" | "CENTER" } }), "config.hero.textAlign")}
                  />
                ) : (
                  <ThemeSegmented
              label={t("storeTheme.tc.textAlign")}
              value={config.brand?.textAlign ?? "LEFT"}
              disabled={disabled}
              error={errorFor("brand.textAlign")}
              options={[{ value: "LEFT", label: t("storeTheme.tc.left") }, { value: "CENTER", label: t("storeTheme.tc.center") }]}
              onChange={(value) => patchConfig((current) => ({ ...current, brand: { ...current.brand, textAlign: value as "LEFT" | "CENTER" } }), "config.brand.textAlign")}
            />
                )}
              </fieldset>

              <fieldset className="theme-block theme-hero-slides">
                <legend>{t("storeTheme.tc.media")}</legend>
                <div className="tc-upload tc-slides-box">
                  <FieldError text={errorFor("hero.slides")} />
                  {slides.length ? (
                    <ol className="tc-slides">
                      {slides.map((slide, index, all) => (
                        <li key={slide.slideMediaId} className="tc-slide">
                          <img src={mediaUrl(slide.imageUrl)} alt={t("storeTheme.heroSlides.slideAlt", { n: index + 1 })} />
                          <span>{t("storeTheme.heroSlides.slideAlt", { n: index + 1 })}</span>
                          <span className="theme-section-move">
                            <button type="button" disabled={disabled || index === 0} onClick={() => moveSlide(index, -1)} aria-label={t("storeTheme.heroSlides.moveUp")} title={t("storeTheme.heroSlides.moveUp")}><ChevronUp /></button>
                            <button type="button" disabled={disabled || index === all.length - 1} onClick={() => moveSlide(index, 1)} aria-label={t("storeTheme.heroSlides.moveDown")} title={t("storeTheme.heroSlides.moveDown")}><ChevronDown /></button>
                            <button type="button" disabled={disabled} onClick={() => removeSlide(index)} aria-label={t("storeTheme.heroSlides.remove")} title={t("storeTheme.heroSlides.remove")}><Trash2 /></button>
                          </span>
                        </li>
                      ))}
                    </ol>
                  ) : <span className="tc-upload-icon" aria-hidden="true"><ImagePlus /></span>}
                  <div className="tc-upload-side">
                    {slides.length < MAX_HERO_SLIDES ? (
                      <MediaField label={t("storeTheme.tc.mediaUpload")}>
                        <ThemeMediaPicker
                          kind="IMAGE"
                          value={undefined}
                          shopId={shopId}
                          shopSlug={shop.slug}
                          disabled={disabled}
                          onChange={(mediaId, item) => { if (mediaId && item?.url) addSlide({ slideMediaId: mediaId, imageUrl: item.url }); }}
                        />
                      </MediaField>
                    ) : <p className="theme-hint">{t("storeTheme.heroSlides.full", { max: MAX_HERO_SLIDES })}</p>}
                    <small>{t("storeTheme.tc.mediaHint", { max: MAX_HERO_SLIDES })}</small>
                    <small>{slides.length ? `${slides.length}/${MAX_HERO_SLIDES}` : t("storeTheme.heroSlides.empty", { max: MAX_HERO_SLIDES })}</small>
                  </div>
                </div>
              </fieldset>

              <fieldset className="theme-block">
                <legend>{t("storeTheme.tc.text")}</legend>
                {general ? (
                  <FormField label={t("storeTheme.tc.eyebrow")} error={errorFor("hero.eyebrow")}>
                    <input value={config.hero.eyebrow ?? ""} maxLength={60} disabled={disabled} placeholder={t("storeTheme.hero.eyebrowPlaceholder")} onChange={(event) => setHero("eyebrow", event.target.value)} />
                  </FormField>
                ) : null}
                <FormField label={t("storeTheme.tc.title")} error={errorFor("hero.title")}>
                  <input value={config.hero.title} maxLength={120} disabled={disabled} placeholder={shop.shopName} onChange={(event) => setHero("title", event.target.value)} />
                </FormField>
                <FormTextarea
                  label={t("storeTheme.tc.subtitle")}
                  value={config.hero.subtitle}
                  maxLength={240}
                  disabled={disabled}
                  error={errorFor("hero.subtitle")}
                  onChange={(event) => setHero("subtitle", event.target.value)}
                />
                <FormField label={t("storeTheme.tc.cta")} error={errorFor("hero.ctaLabel")}>
                  <input value={config.hero.ctaLabel} maxLength={120} disabled={disabled} onChange={(event) => setHero("ctaLabel", event.target.value)} />
                </FormField>
                {general ? (
                  <FormField label={t("storeTheme.tc.cta2")} error={errorFor("hero.ctaSecondaryLabel")}>
                    <input value={config.hero.ctaSecondaryLabel ?? ""} maxLength={120} disabled={disabled} placeholder={t("storeTheme.hero.ctaSecondaryPlaceholder")} onChange={(event) => setHero("ctaSecondaryLabel", event.target.value)} />
                  </FormField>
                ) : null}
              </fieldset>

              <fieldset className="theme-block">
                <legend>{t("storeTheme.tc.more")}</legend>
                <FormField label={t("storeTheme.tc.ctaLink")} error={errorFor("hero.ctaLink")}>
                  <input value={config.hero.ctaLink} disabled={disabled} placeholder="/shops  |  https://" onChange={(event) => setHero("ctaLink", event.target.value)} />
                </FormField>
                {general ? (
                  <FormField label={t("storeTheme.tc.cta2Link")} error={errorFor("hero.ctaSecondaryLink")}>
                    <input value={config.hero.ctaSecondaryLink ?? ""} disabled={disabled} placeholder="/shops  |  https://" onChange={(event) => setHero("ctaSecondaryLink", event.target.value)} />
                  </FormField>
                ) : null}
                {general ? [0, 1, 2].map((slot) => (
                  <FormField key={slot} label={`${t("storeTheme.hero.badges")} ${slot + 1}`} error={errorFor(`hero.badges[${slot}]`) ?? (slot === 0 ? errorFor("hero.badges") : undefined)}>
                    <input value={config.hero.badges?.[slot] ?? ""} maxLength={24} placeholder={t("storeTheme.hero.badgesHint")} disabled={disabled} onChange={(event) => setHeroBadge(slot, event.target.value)} />
                  </FormField>
                )) : null}
                <p className="theme-hint">{general ? t("storeTheme.hero.generalHint") : t("storeTheme.hero.ctaHint")}</p>
              </fieldset>
            </>
          );
        })()}

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
                {usesTsfTree(draft.templateKey) ? (
                  <FormField label={t("storeTheme.sections.trustSub")} error={errorFor(`trust.items[${index}].sub`)}>
                    <input
                      value={item.sub ?? ""}
                      maxLength={80}
                      placeholder={t("storeTheme.sections.trustSubPlaceholder")}
                      disabled={disabled || !config.trust.enabled}
                      onChange={(event) => setTrustItem(index, { sub: event.target.value })}
                    />
                  </FormField>
                ) : null}
              </li>
            ))}
          </ul>
        {usesTsfTree(draft.templateKey) ? (
            <div className="theme-grid">
              {[0, 1].map((slot) => (
                <FormField key={slot} label={`${t("storeTheme.announcement.label")} ${slot + 1}`} error={errorFor(`announcement.items[${slot}]`) ?? (slot === 0 ? errorFor("announcement.items") : undefined)}>
                  <input value={config.announcement?.items?.[slot] ?? ""} maxLength={120} placeholder={t("storeTheme.announcement.hint")} disabled={disabled} onChange={(event) => setAnnouncement(slot, event.target.value)} />
                </FormField>
              ))}
            </div>
          ) : null}
        </fieldset>
        </>) : null}
        {panelTab === "sections" ? (<>

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
        <>
        <fieldset className="theme-block">
          <legend>{t("storeTheme.tc.sectionsHead", { n: config.sections.filter((row) => row.enabled).length, total: config.sections.length })}</legend>
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
                  <span className="theme-section-main">
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
                        {variants.map((variant) => <option key={variant} value={variant ?? ""}>{variant}</option>)}
                      </ThemeSelect>
                    ) : <small className="theme-section-state">{section.enabled ? t("storeTheme.tc.shown") : t("storeTheme.tc.hidden")}</small>}
                  </span>
                  <span className="theme-section-move">
                    <button type="button" disabled={disabled || index === 0} aria-label={t("storeTheme.sections.moveUp")} title={t("storeTheme.sections.moveUp")} onClick={() => reorder(index, index - 1)}><ChevronUp /></button>
                    <button type="button" disabled={disabled || index === config.sections.length - 1} aria-label={t("storeTheme.sections.moveDown")} title={t("storeTheme.sections.moveDown")} onClick={() => reorder(index, index + 1)}><ChevronDown /></button>
                  </span>
                  <ToggleSwitch
                    checked={section.enabled}
                    disabled={disabled}
                    label={t(`storeTheme.sections.${section.key}`)}
                    onText={t("storeTheme.sections.on")}
                    offText={t("storeTheme.sections.off")}
                    onChange={(enabled) => setSection(index, { enabled })}
                  />
                  {rowError ? <FieldError text={rowError} /> : null}
                </li>
              );
            })}
          </ul>
          <p className="theme-hint tc-note">{t("storeTheme.tc.sectionsNote")}</p>
        </fieldset>
        {draft.templateKey === "GENERAL" ? (() => {
          const catalogIndex = config.sections.findIndex((row) => row.key === "CATALOG");
          const current = catalogIndex >= 0 ? (config.sections[catalogIndex].variant ?? "GRID") : "GRID";
          const options: { key: "GRID" | "LARGE" | "MENU"; Icon: LucideIcon }[] = [
            { key: "GRID", Icon: LayoutGrid }, { key: "LARGE", Icon: GalleryHorizontal }, { key: "MENU", Icon: List },
          ];
          return (
            <fieldset className="theme-block">
              <legend>{t("storeTheme.catalogLayout.title")}</legend>
              <p className="theme-hint">{t("storeTheme.catalogLayout.hint")}</p>
              <div className="tc-layout-opts" role="radiogroup" aria-label={t("storeTheme.catalogLayout.title")}
                onKeyDown={(event) => {
                  const step = event.key === "ArrowDown" || event.key === "ArrowRight" ? 1 : event.key === "ArrowUp" || event.key === "ArrowLeft" ? -1 : 0;
                  if (!step || disabled || catalogIndex < 0) return;
                  event.preventDefault();
                  const idx = options.findIndex((o) => o.key === current);
                  const next = options[(idx + step + options.length) % options.length].key;
                  setSection(catalogIndex, { variant: next });
                  const group = event.currentTarget;
                  requestAnimationFrame(() => group.querySelector<HTMLButtonElement>(`[data-key="${next}"]`)?.focus());
                }}>
                {options.map(({ key, Icon }) => {
                  const active = current === key;
                  return (
                    <button type="button" key={key} data-key={key} role="radio" aria-checked={active} tabIndex={active ? 0 : -1}
                      className={`tc-layout-opt${active ? " is-active" : ""}`}
                      disabled={disabled || catalogIndex < 0} onClick={() => setSection(catalogIndex, { variant: key })}>
                      <span className={`tc-layout-prev tc-layout-prev-${key.toLowerCase()}`} aria-hidden="true">
                        {key === "GRID" ? <><i /><i /><i /><i /><i /><i /></> : key === "LARGE" ? <><i /><i /></> : <><i /><i /><i /></>}
                      </span>
                      <span className="tc-layout-text">
                        <b><Icon aria-hidden="true" />{t(`storeTheme.catalogLayout.${key}.name`)}</b>
                        <small>{t(`storeTheme.catalogLayout.${key}.desc`)}</small>
                      </span>
                      <span className="tc-layout-radio" aria-hidden="true">{active ? <Check /> : null}</span>
                    </button>
                  );
                })}
              </div>
              {catalogIndex >= 0 ? <FieldError text={errorFor(`sections[${catalogIndex}].variant`)} /> : null}
            </fieldset>
          );
        })() : null}
        </>
        )}
        {usesTsfTree(draft.templateKey) ? (
          <fieldset className="theme-block theme-intro">
            <legend>{t("storeTheme.intro.title")}</legend>
            <p className="theme-hint">{t("storeTheme.intro.hint")}</p>
            <FieldError text={errorFor("intro")} />
            <div className="theme-grid">
              <FormField label={t("storeTheme.intro.eyebrow")} error={errorFor("intro.eyebrow")}>
                <input value={config.intro?.eyebrow ?? ""} maxLength={60} disabled={disabled} onChange={(event) => setIntro({ eyebrow: event.target.value }, "eyebrow")} />
              </FormField>
              <FormField label={t("storeTheme.intro.heading")} error={errorFor("intro.title")}>
                <input value={config.intro?.title ?? ""} maxLength={120} disabled={disabled} placeholder={shop.shopName} onChange={(event) => setIntro({ title: event.target.value }, "title")} />
              </FormField>
            </div>
            <FormTextarea
              label={t("storeTheme.intro.body")}
              value={config.intro?.body ?? ""}
              maxLength={1000}
              disabled={disabled}
              error={errorFor("intro.body")}
              onChange={(event) => setIntro({ body: event.target.value }, "body")}
            />
            <div className="theme-grid">
              <FormField label={t("storeTheme.intro.ctaLabel")} error={errorFor("intro.ctaLabel")}>
                <input value={config.intro?.ctaLabel ?? ""} maxLength={60} disabled={disabled} onChange={(event) => setIntro({ ctaLabel: event.target.value }, "ctaLabel")} />
              </FormField>
              <FormField label={t("storeTheme.intro.ctaLink")} error={errorFor("intro.ctaLink")}>
                <input value={config.intro?.ctaLink ?? ""} maxLength={500} disabled={disabled} placeholder="/products" onChange={(event) => setIntro({ ctaLink: event.target.value }, "ctaLink")} />
              </FormField>
            </div>
            <p className="theme-hint">{t("storeTheme.intro.linkHint")}</p>
            <MediaField label={t("storeTheme.intro.image")}>
              <ThemeMediaPicker
                kind="IMAGE"
                value={config.intro?.introMediaId}
                shopId={shopId}
                shopSlug={shop.slug}
                disabled={disabled}
                onChange={(mediaId, item) => setIntro({ introMediaId: mediaId || undefined, imageUrl: mediaId ? item?.url : undefined }, "introMediaId")}
              />
            </MediaField>
            <FieldError text={errorFor("intro.introMediaId")} />
            <p className="theme-hint">{t("storeTheme.intro.imageHint")}</p>
            <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <input type="checkbox" checked={!!config.intro?.reverse} disabled={disabled} onChange={(event) => setIntro({ reverse: event.target.checked }, "reverse")} />
              {t("storeTheme.intro.reverse")}
            </label>
          </fieldset>
        ) : null}
        </>) : null}
        </div>
      </section>

      <section className="tc-preview store-theme-preview">
        <div className="theme-preview-stage" ref={stageRef}>
          <div className="theme-preview-device tc-device" data-device={device} style={{ width: previewWidth, zoom }}>
            <HeroStickerDragContext.Provider value={stickerDrag}>
              <ShopStorefront shop={previewShop} products={previewProducts} catalog={previewCatalog} previewWidth={previewWidth}
                categoryNames={previewCategoryNames} allProductsLimit={PREVIEW_PRODUCT_LIMIT} />
            </HeroStickerDragContext.Provider>
          </div>
        </div>
        <p className="theme-hint">{t("storeTheme.preview.hint", { width: previewWidth })}</p>
      </section>
      </div>

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
