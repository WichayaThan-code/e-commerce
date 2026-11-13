import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import ToggleSwitch from "../form/ToggleSwitch";
import { LocalizedInput } from "./BlockContentEditors";
import ThemeMediaPicker from "./ThemeMediaPicker";
import { MediaField } from "./ThemeEditorParts";
import { ColorField, GradientEditor, ThemeTokenSelect } from "./ThemeEditorParts";
import type { LocalizedString } from "../../theme/blockContent";
import {
  HEADER_LAYOUTS, HERO_BACKGROUND_TYPES, HERO_HEADLINE_SIZES, HERO_RANGES, HERO_TEXT_ALIGNS, MAX_COLUMN_LINKS,
  MAX_FOOTER_COLUMNS, MAX_HERO_STICKERS, MAX_MENU_ITEMS, SHOP_BLOCK_BACKGROUNDS, SHOP_THEME_FONTS,
  type HeaderLayout, type HeroBackgroundType, type HeroHeadlineSize, type HeroTextAlign, type ShopBlockBackground,
  type ShopThemeFontKey, type ThemeChromeLink, type ThemeConfig, type ThemeFontCustom, type ThemeFooter,
  type ThemeFooterColumn, type ThemeGradient, type ThemeHeader, type ThemeHeroSticker,
} from "../../theme/shopTheme";

/** Derived (API-resolved) fields never travel back to the API; sticker rows without media are dropped. */
export function chromePayload(config: ThemeConfig, custom: boolean): ThemeConfig {
  const next: ThemeConfig = { ...config };
  if (!custom) {
    delete next.header;
    delete next.footer;
    delete next.fontCustom;
    return next;
  }
  const hero = { ...config.hero };
  if (hero.layout !== "MEDIA_FULL") {
    // Hero extras only make sense for MEDIA_FULL; drop them so a half-filled panel can't fail validation.
    delete hero.background;
    delete hero.overlay;
    delete hero.heightVh;
    delete hero.textAlign;
    delete hero.headlineSize;
    delete hero.stickers;
    delete hero.ctaSecondary;
  }
  if (hero.background) {
    const { url: _url, posterUrl: _posterUrl, ...background } = hero.background;
    void _url;
    void _posterUrl;
    hero.background = background;
  }
  if (hero.stickers) {
    hero.stickers = hero.stickers
      .filter((sticker) => sticker.mediaId)
      .map(({ url: _url, ...sticker }) => {
        void _url;
        return sticker;
      });
    if (hero.stickers.length === 0) delete hero.stickers;
  }
  next.hero = hero;
  for (const key of ["header", "footer"] as const) {
    const section = config[key];
    if (!section) continue;
    const { backgroundUrl: _url, ...rest } = section;
    void _url;
    (next as Record<string, unknown>)[key] = rest;
  }
  if (config.fontCustom) {
    const { headingUrl: _heading, bodyUrl: _body, ...font } = config.fontCustom;
    void _heading;
    void _body;
    if (font.headingMediaId || font.bodyMediaId) next.fontCustom = font;
    else delete next.fontCustom;
  }
  return next;
}

type Patch = (change: (config: ThemeConfig) => ThemeConfig, errorPrefix: string) => void;

type ChromeEditorProps = {
  config: ThemeConfig;
  disabled: boolean;
  shopId: number | null;
  shopSlug: string;
  errorFor: (path: string) => string | undefined;
  patch: Patch;
};

const clampNumber = (raw: string, min: number, max: number): number | null => {
  const next = Number.parseInt(raw, 10);
  return Number.isFinite(next) ? Math.max(min, Math.min(max, next)) : null;
};

function NumberField({
  label, value, min, max, step = 1, disabled, error, onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  disabled: boolean;
  error?: string;
  onChange: (value: number) => void;
}) {
  return (
    <FormField label={label} error={error}>
      <input
        type="number"
        className="num"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        onChange={(event) => {
          const next = clampNumber(event.target.value, min, max);
          if (next != null) onChange(next);
        }}
      />
    </FormField>
  );
}

function BackgroundFields({
  value, config, disabled, shopId, shopSlug, errorFor, prefix, onChange,
}: {
  value: Pick<ThemeHeader, "background" | "gradient" | "backgroundMediaId" | "backgroundUrl" | "overlay">;
  config: ThemeConfig;
  disabled: boolean;
  shopId: number | null;
  shopSlug: string;
  errorFor: (path: string) => string | undefined;
  prefix: string;
  onChange: (change: Partial<ThemeHeader & ThemeFooter>) => void;
}) {
  const { t } = useTranslation("merchant");
  return (
    <>
      <ThemeTokenSelect
        label={t("storeTheme.sectionSettings.background")}
        value={value.background}
        disabled={disabled}
        error={errorFor(`${prefix}.background`)}
        options={SHOP_BLOCK_BACKGROUNDS.map((entry) => ({ value: entry, label: t(`storeTheme.blocks.backgrounds.${entry}`) }))}
        onChange={(picked) => onChange({ background: picked as ShopBlockBackground })}
      />
      {value.background === "GRADIENT" ? (
        <GradientEditor
          title={t("storeTheme.blocks.gradient")}
          angleLabel={t("storeTheme.gradients.angle")}
          stopLabel={(stop) => t("storeTheme.gradients.stop", { index: stop + 1 })}
          addStopLabel={t("storeTheme.gradients.addStop")}
          removeStopLabel={t("storeTheme.gradients.removeStop")}
          gradient={value.gradient}
          fallbackColors={config.colors}
          disabled={disabled}
          hexError={t("storeTheme.errors.hex")}
          errorFor={errorFor}
          prefix={`${prefix}.gradient`}
          onChange={(gradient: ThemeGradient) => onChange({ gradient })}
        />
      ) : null}
      {value.background === "IMAGE" ? (
        <>
          <MediaField label={t("storeTheme.blocks.backgroundImage")} error={errorFor(`${prefix}.backgroundMediaId`)}>
            <ThemeMediaPicker
              kind="IMAGE"
              value={value.backgroundMediaId}
              shopId={shopId}
              shopSlug={shopSlug}
              disabled={disabled}
              onChange={(mediaId, item) => onChange(mediaId
                ? { backgroundMediaId: mediaId, backgroundUrl: item?.url ?? value.backgroundUrl }
                : { backgroundMediaId: undefined, backgroundUrl: undefined })}
            />
          </MediaField>
          <NumberField
            label={t("storeTheme.blocks.overlay")}
            value={value.overlay ?? 0}
            min={0}
            max={80}
            step={5}
            disabled={disabled}
            error={errorFor(`${prefix}.overlay`)}
            onChange={(overlay) => onChange({ overlay })}
          />
        </>
      ) : null}
    </>
  );
}

function LinkField({
  label, value, disabled, error, onChange,
}: {
  label: string;
  value: string;
  disabled: boolean;
  error?: string;
  onChange: (value: string) => void;
}) {
  return (
    <FormField label={label} error={error}>
      <input
        value={value}
        maxLength={500}
        spellCheck={false}
        placeholder="/shop/my-shop  ·  https://…"
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    </FormField>
  );
}

function ChromeLinkRows({
  rows, path, max, disabled, errorFor, addLabel, removeLabel, labelLabel, linkLabel, onChange,
}: {
  rows: ThemeChromeLink[];
  path: string;
  max: number;
  disabled: boolean;
  errorFor: (path: string) => string | undefined;
  addLabel: string;
  removeLabel: string;
  labelLabel: string;
  linkLabel: string;
  onChange: (rows: ThemeChromeLink[]) => void;
}) {
  const { t } = useTranslation("merchant");
  const patch = (index: number, change: Partial<ThemeChromeLink>) =>
    onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)));
  const move = (from: number, to: number) => {
    if (to < 0 || to >= rows.length) return;
    const next = [...rows];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  };
  return (
    <>
      <ul className="theme-banner-list">
        {rows.map((row, index) => (
          <li key={index} className="theme-banner-row">
            <div className="theme-grid">
              <LocalizedInput
                label={labelLabel}
                value={row.label}
                maxLength={40}
                disabled={disabled}
                error={errorFor(`${path}[${index}].label`)}
                onChange={(label: LocalizedString | undefined) => patch(index, { label: label ?? { th: "" } })}
              />
              <LinkField
                label={linkLabel}
                value={row.link}
                disabled={disabled}
                error={errorFor(`${path}[${index}].link`) || errorFor(`${path}[${index}]`)}
                onChange={(link) => patch(index, { link })}
              />
            </div>
            <span className="theme-section-move">
              <button type="button" disabled={disabled || index === 0} aria-label={t("storeTheme.sections.moveUp")} title={t("storeTheme.sections.moveUp")} onClick={() => move(index, index - 1)}><ChevronUp /></button>
              <button type="button" disabled={disabled || index === rows.length - 1} aria-label={t("storeTheme.sections.moveDown")} title={t("storeTheme.sections.moveDown")} onClick={() => move(index, index + 1)}><ChevronDown /></button>
            </span>
            <button type="button" className="theme-banner-remove" disabled={disabled} onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}>
              <Trash2 />{removeLabel}
            </button>
          </li>
        ))}
      </ul>
      <button
        type="button"
        className="theme-banner-add"
        disabled={disabled || rows.length >= max}
        onClick={() => onChange([...rows, { label: { th: "" }, link: "" }])}
      >
        <Plus />{addLabel}
      </button>
    </>
  );
}

/** AG5 hero MEDIA_FULL settings (background, overlay, height, alignment, secondary CTA, stickers). */
export function HeroMediaPanel({ config, disabled, shopId, shopSlug, errorFor, patch }: ChromeEditorProps) {
  const { t } = useTranslation("merchant");
  const { hero } = config;
  const background = hero.background ?? { type: "GRADIENT" as HeroBackgroundType };
  const stickers = hero.stickers ?? [];
  const setHero = (change: Partial<typeof hero>) =>
    patch((current) => ({ ...current, hero: { ...current.hero, ...change } }), "config.hero");
  const setBackground = (change: Partial<typeof background>) => setHero({ background: { ...background, ...change } });
  const setSticker = (index: number, change: Partial<ThemeHeroSticker>) =>
    setHero({ stickers: stickers.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)) });
  const secondary = hero.ctaSecondary;

  return (
    <fieldset className="theme-block theme-chrome">
      <legend>{t("storeTheme.chrome.hero.title")}</legend>
      <p className="theme-hint">{t("storeTheme.chrome.hero.hint")}</p>
      <FieldError text={errorFor("hero.background")} />
      <div className="theme-grid">
        <ThemeTokenSelect
          label={t("storeTheme.chrome.hero.bgType")}
          value={background.type}
          disabled={disabled}
          error={errorFor("hero.background.type")}
          options={HERO_BACKGROUND_TYPES.map((value) => ({ value, label: t(`storeTheme.chrome.hero.bgTypes.${value}`) }))}
          onChange={(picked) => setBackground({
            type: picked as HeroBackgroundType, mediaId: undefined, posterMediaId: undefined, url: undefined, posterUrl: undefined,
          })}
        />
        <ThemeTokenSelect
          label={t("storeTheme.chrome.hero.textAlign")}
          value={hero.textAlign ?? "LEFT"}
          disabled={disabled}
          error={errorFor("hero.textAlign")}
          options={HERO_TEXT_ALIGNS.map((value) => ({ value, label: t(`storeTheme.chrome.hero.aligns.${value}`) }))}
          onChange={(picked) => setHero({ textAlign: picked as HeroTextAlign })}
        />
        <ThemeTokenSelect
          label={t("storeTheme.chrome.hero.headlineSize")}
          value={hero.headlineSize ?? "L"}
          disabled={disabled}
          error={errorFor("hero.headlineSize")}
          options={HERO_HEADLINE_SIZES.map((value) => ({ value, label: t(`storeTheme.chrome.hero.sizes.${value}`) }))}
          onChange={(picked) => setHero({ headlineSize: picked as HeroHeadlineSize })}
        />
        <NumberField
          label={t("storeTheme.chrome.hero.overlay")}
          value={hero.overlay ?? 35}
          min={HERO_RANGES.overlay[0]}
          max={HERO_RANGES.overlay[1]}
          step={5}
          disabled={disabled}
          error={errorFor("hero.overlay")}
          onChange={(overlay) => setHero({ overlay })}
        />
        <NumberField
          label={t("storeTheme.chrome.hero.heightVh")}
          value={hero.heightVh ?? 70}
          min={HERO_RANGES.heightVh[0]}
          max={HERO_RANGES.heightVh[1]}
          step={5}
          disabled={disabled}
          error={errorFor("hero.heightVh")}
          onChange={(heightVh) => setHero({ heightVh })}
        />
      </div>

      {background.type === "IMAGE" ? (
        <MediaField label={t("storeTheme.chrome.hero.bgImage")} error={errorFor("hero.background.mediaId")}>
          <ThemeMediaPicker
            kind="IMAGE"
            value={background.mediaId}
            shopId={shopId}
            shopSlug={shopSlug}
            disabled={disabled}
            crop={background.crop}
            mobileCrop={background.mobileCrop}
            cropAspect={16 / 9}
            cropMobileAspect={9 / 16}
            onCropChange={(crop, mobileCrop) => setBackground({ crop, mobileCrop })}
            onChange={(mediaId, item) => setBackground(mediaId
              ? { mediaId, url: item?.url ?? background.url }
              : { mediaId: undefined, url: undefined, crop: undefined, mobileCrop: undefined })}
          />
        </MediaField>
      ) : null}
      {background.type === "VIDEO" ? (
        <>
          <MediaField label={t("storeTheme.chrome.hero.bgVideo")} error={errorFor("hero.background.mediaId")}>
            <ThemeMediaPicker
              kind="VIDEO"
              value={background.mediaId}
              shopId={shopId}
              shopSlug={shopSlug}
              disabled={disabled}
              onChange={(mediaId, item) => setBackground(mediaId
                ? { mediaId, url: item?.url ?? background.url }
                : { mediaId: undefined, url: undefined })}
            />
          </MediaField>
          <MediaField label={t("storeTheme.chrome.hero.poster")} error={errorFor("hero.background.posterMediaId")}>
            <ThemeMediaPicker
              kind="IMAGE"
              value={background.posterMediaId}
              shopId={shopId}
              shopSlug={shopSlug}
              disabled={disabled}
              crop={background.crop}
              mobileCrop={background.mobileCrop}
              cropAspect={16 / 9}
              cropMobileAspect={9 / 16}
              onCropChange={(crop, mobileCrop) => setBackground({ crop, mobileCrop })}
              onChange={(mediaId, item) => setBackground(mediaId
                ? { posterMediaId: mediaId, posterUrl: item?.url ?? background.posterUrl }
                : { posterMediaId: undefined, posterUrl: undefined, crop: undefined, mobileCrop: undefined })}
            />
          </MediaField>
          <p className="theme-hint">{t("storeTheme.chrome.hero.posterHint")}</p>
        </>
      ) : null}
      {background.type === "GRADIENT" ? <p className="theme-hint">{t("storeTheme.chrome.hero.gradientHint")}</p> : null}

      <div className="theme-content-group">
        <b className="theme-content-subtitle">{t("storeTheme.chrome.hero.secondaryCta")}</b>
        <div className="theme-grid">
          <FormField label={t("storeTheme.chrome.hero.secondaryLabel")} error={errorFor("hero.ctaSecondary.label")}>
            <input
              value={secondary?.label ?? ""}
              maxLength={60}
              disabled={disabled}
              onChange={(event) => {
                const label = event.target.value;
                setHero({ ctaSecondary: label || secondary?.link ? { label, link: secondary?.link ?? "" } : undefined });
              }}
            />
          </FormField>
          <LinkField
            label={t("storeTheme.chrome.hero.secondaryLink")}
            value={secondary?.link ?? ""}
            disabled={disabled}
            error={errorFor("hero.ctaSecondary.link")}
            onChange={(link) => setHero({ ctaSecondary: link || secondary?.label ? { label: secondary?.label ?? "", link } : undefined })}
          />
        </div>
        <p className="theme-hint">{t("storeTheme.content.linkHint")}</p>
      </div>

      <div className="theme-content-group">
        <b className="theme-content-subtitle">
          {t("storeTheme.chrome.hero.stickers", { count: stickers.length, max: MAX_HERO_STICKERS })}
        </b>
        <p className="theme-hint">{t("storeTheme.chrome.hero.stickersHint")}</p>
        <FieldError text={errorFor("hero.stickers")} />
        <ul className="theme-banner-list">
          {stickers.map((sticker, index) => {
            const path = `hero.stickers[${index}]`;
            return (
              <li key={index} className="theme-banner-row">
                <MediaField label={t("storeTheme.chrome.hero.stickerImage")} error={errorFor(`${path}.mediaId`) || errorFor(path)}>
                  <ThemeMediaPicker
                    kind="IMAGE"
                    value={sticker.mediaId || undefined}
                    shopId={shopId}
                    shopSlug={shopSlug}
                    disabled={disabled}
                    onChange={(mediaId, item) => setSticker(index, mediaId
                      ? { mediaId, url: item?.url ?? sticker.url }
                      : { mediaId: "", url: undefined })}
                  />
                </MediaField>
                <div className="theme-grid">
                  <NumberField label={t("storeTheme.chrome.hero.stickerX")} value={sticker.x} min={HERO_RANGES.x[0]} max={HERO_RANGES.x[1]} disabled={disabled} error={errorFor(`${path}.x`)} onChange={(x) => setSticker(index, { x })} />
                  <NumberField label={t("storeTheme.chrome.hero.stickerY")} value={sticker.y} min={HERO_RANGES.y[0]} max={HERO_RANGES.y[1]} disabled={disabled} error={errorFor(`${path}.y`)} onChange={(y) => setSticker(index, { y })} />
                  <NumberField label={t("storeTheme.chrome.hero.stickerSize")} value={sticker.size} min={HERO_RANGES.size[0]} max={HERO_RANGES.size[1]} step={10} disabled={disabled} error={errorFor(`${path}.size`)} onChange={(size) => setSticker(index, { size })} />
                  <NumberField label={t("storeTheme.chrome.hero.stickerRotate")} value={sticker.rotate} min={HERO_RANGES.rotate[0]} max={HERO_RANGES.rotate[1]} disabled={disabled} error={errorFor(`${path}.rotate`)} onChange={(rotate) => setSticker(index, { rotate })} />
                  <div className="theme-trust-toggle">
                    <ToggleSwitch
                      checked={sticker.hideOnMobile}
                      disabled={disabled}
                      label={t("storeTheme.chrome.hero.stickerHideMobile")}
                      onText={t("storeTheme.sections.on")}
                      offText={t("storeTheme.sections.off")}
                      onChange={(hideOnMobile) => setSticker(index, { hideOnMobile })}
                    />
                  </div>
                </div>
                <button type="button" className="theme-banner-remove" disabled={disabled} onClick={() => setHero({ stickers: stickers.filter((_, rowIndex) => rowIndex !== index) })}>
                  <Trash2 />{t("storeTheme.chrome.hero.stickerRemove")}
                </button>
              </li>
            );
          })}
        </ul>
        <button
          type="button"
          className="theme-banner-add"
          disabled={disabled || stickers.length >= MAX_HERO_STICKERS}
          onClick={() => setHero({ stickers: [...stickers, { mediaId: "", x: 50, y: 50, size: 120, rotate: 0, hideOnMobile: false }] })}
        >
          <Plus />{t("storeTheme.chrome.hero.stickerAdd")}
        </button>
      </div>
    </fieldset>
  );
}

const DEFAULT_HEADER: ThemeHeader = {
  layout: "LOGO_LEFT", background: "NONE", sticky: false, menu: [], showSearch: false, showSocialIcons: false,
};

const DEFAULT_FOOTER: ThemeFooter = {
  columns: [], showContacts: true, showSocial: true, background: "SURFACE",
};

/** AG5 header panel (CUSTOM only). No header = the existing mall header / tabs stay as they are. */
export function HeaderPanel({ config, disabled, shopId, shopSlug, errorFor, patch }: ChromeEditorProps) {
  const { t } = useTranslation("merchant");
  const header = config.header;
  const setHeader = (change: Partial<ThemeHeader>) =>
    patch((current) => ({ ...current, header: { ...(current.header ?? DEFAULT_HEADER), ...change } }), "config.header");
  const bar = header?.announcementBar;
  const setBar = (change: Partial<NonNullable<ThemeHeader["announcementBar"]>> | null) => {
    if (change === null) {
      patch((current) => {
        if (!current.header) return current;
        const { announcementBar: _bar, ...rest } = current.header;
        void _bar;
        return { ...current, header: rest };
      }, "config.header.announcementBar");
      return;
    }
    setHeader({
      announcementBar: {
        text: { th: "" }, backgroundColor: config.colors.primary, textColor: "#ffffff", ...header?.announcementBar, ...change,
      },
    });
  };

  return (
    <fieldset className="theme-block theme-chrome">
      <legend>{t("storeTheme.chrome.header.title")}</legend>
      <p className="theme-hint">{t("storeTheme.chrome.header.hint")}</p>
      <div className="theme-trust-toggle">
        <ToggleSwitch
          checked={Boolean(header)}
          disabled={disabled}
          label={t("storeTheme.chrome.header.enable")}
          onText={t("storeTheme.sections.on")}
          offText={t("storeTheme.sections.off")}
          onChange={(enabled) => {
            if (enabled) setHeader({});
            else patch((current) => {
              const { header: _header, ...rest } = current;
              void _header;
              return rest;
            }, "config.header");
          }}
        />
      </div>
      <FieldError text={errorFor("header")} />
      {header ? (
        <>
          <div className="theme-grid">
            <ThemeTokenSelect
              label={t("storeTheme.chrome.header.layout")}
              value={header.layout}
              disabled={disabled}
              error={errorFor("header.layout")}
              options={HEADER_LAYOUTS.map((value) => ({ value, label: t(`storeTheme.chrome.header.layouts.${value}`) }))}
              onChange={(picked) => setHeader({ layout: picked as HeaderLayout })}
            />
            <div className="theme-trust-toggle">
              <ToggleSwitch checked={header.sticky} disabled={disabled} label={t("storeTheme.chrome.header.sticky")} onText={t("storeTheme.sections.on")} offText={t("storeTheme.sections.off")} onChange={(sticky) => setHeader({ sticky })} />
            </div>
            <div className="theme-trust-toggle">
              <ToggleSwitch checked={header.showSearch} disabled={disabled} label={t("storeTheme.chrome.header.showSearch")} onText={t("storeTheme.sections.on")} offText={t("storeTheme.sections.off")} onChange={(showSearch) => setHeader({ showSearch })} />
            </div>
            <div className="theme-trust-toggle">
              <ToggleSwitch checked={header.showSocialIcons} disabled={disabled} label={t("storeTheme.chrome.header.showSocial")} onText={t("storeTheme.sections.on")} offText={t("storeTheme.sections.off")} onChange={(showSocialIcons) => setHeader({ showSocialIcons })} />
            </div>
          </div>
          <BackgroundFields
            value={header}
            config={config}
            disabled={disabled}
            shopId={shopId}
            shopSlug={shopSlug}
            errorFor={errorFor}
            prefix="header"
            onChange={setHeader}
          />

          <div className="theme-content-group">
            <b className="theme-content-subtitle">{t("storeTheme.chrome.header.menu", { count: header.menu.length, max: MAX_MENU_ITEMS })}</b>
            <FieldError text={errorFor("header.menu")} />
            <ChromeLinkRows
              rows={header.menu}
              path="header.menu"
              max={MAX_MENU_ITEMS}
              disabled={disabled}
              errorFor={errorFor}
              addLabel={t("storeTheme.chrome.header.menuAdd")}
              removeLabel={t("storeTheme.chrome.header.menuRemove")}
              labelLabel={t("storeTheme.chrome.header.menuLabel")}
              linkLabel={t("storeTheme.chrome.header.menuLink")}
              onChange={(menu) => setHeader({ menu })}
            />
            <p className="theme-hint">{t("storeTheme.content.linkHint")}</p>
          </div>

          <div className="theme-content-group">
            <div className="theme-trust-toggle">
              <ToggleSwitch
                checked={Boolean(bar)}
                disabled={disabled}
                label={t("storeTheme.chrome.header.announce")}
                onText={t("storeTheme.sections.on")}
                offText={t("storeTheme.sections.off")}
                onChange={(enabled) => setBar(enabled ? {} : null)}
              />
            </div>
            <FieldError text={errorFor("header.announcementBar")} />
            {bar ? (
              <>
                <LocalizedInput
                  label={t("storeTheme.chrome.header.announceText")}
                  value={bar.text}
                  maxLength={160}
                  disabled={disabled}
                  error={errorFor("header.announcementBar.text")}
                  onChange={(text: LocalizedString | undefined) => setBar({ text: text ?? { th: "" } })}
                />
                <LinkField
                  label={t("storeTheme.chrome.header.announceLink")}
                  value={bar.link ?? ""}
                  disabled={disabled}
                  error={errorFor("header.announcementBar.link")}
                  onChange={(link) => setBar({ link: link.trim() ? link : undefined })}
                />
                <div className="theme-grid">
                  <ColorField
                    label={t("storeTheme.chrome.header.announceBg")}
                    value={bar.backgroundColor}
                    error={errorFor("header.announcementBar.backgroundColor")}
                    hexError={t("storeTheme.errors.hex")}
                    disabled={disabled}
                    onChange={(backgroundColor) => setBar({ backgroundColor })}
                  />
                  <ColorField
                    label={t("storeTheme.chrome.header.announceFg")}
                    value={bar.textColor}
                    error={errorFor("header.announcementBar.textColor")}
                    hexError={t("storeTheme.errors.hex")}
                    disabled={disabled}
                    onChange={(textColor) => setBar({ textColor })}
                  />
                </div>
              </>
            ) : null}
          </div>
        </>
      ) : null}
    </fieldset>
  );
}

/** AG5 footer panel (CUSTOM only). Contacts always come from the shop's public contact fields. */
export function FooterPanel({ config, disabled, shopId, shopSlug, errorFor, patch }: ChromeEditorProps) {
  const { t } = useTranslation("merchant");
  const footer = config.footer;
  const setFooter = (change: Partial<ThemeFooter>) =>
    patch((current) => ({ ...current, footer: { ...(current.footer ?? DEFAULT_FOOTER), ...change } }), "config.footer");
  const setColumn = (index: number, change: Partial<ThemeFooterColumn>) =>
    setFooter({ columns: (footer?.columns ?? []).map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)) });

  return (
    <fieldset className="theme-block theme-chrome">
      <legend>{t("storeTheme.chrome.footer.title")}</legend>
      <p className="theme-hint">{t("storeTheme.chrome.footer.hint")}</p>
      <div className="theme-trust-toggle">
        <ToggleSwitch
          checked={Boolean(footer)}
          disabled={disabled}
          label={t("storeTheme.chrome.footer.enable")}
          onText={t("storeTheme.sections.on")}
          offText={t("storeTheme.sections.off")}
          onChange={(enabled) => {
            if (enabled) setFooter({});
            else patch((current) => {
              const { footer: _footer, ...rest } = current;
              void _footer;
              return rest;
            }, "config.footer");
          }}
        />
      </div>
      <FieldError text={errorFor("footer")} />
      {footer ? (
        <>
          <LocalizedInput
            label={t("storeTheme.chrome.footer.about")}
            value={footer.about}
            maxLength={400}
            multiline
            rows={3}
            disabled={disabled}
            error={errorFor("footer.about")}
            onChange={(about: LocalizedString | undefined) => setFooter({ about })}
          />
          <div className="theme-grid">
            <div className="theme-trust-toggle">
              <ToggleSwitch checked={footer.showContacts} disabled={disabled} label={t("storeTheme.chrome.footer.showContacts")} onText={t("storeTheme.sections.on")} offText={t("storeTheme.sections.off")} onChange={(showContacts) => setFooter({ showContacts })} />
            </div>
            <div className="theme-trust-toggle">
              <ToggleSwitch checked={footer.showSocial} disabled={disabled} label={t("storeTheme.chrome.footer.showSocial")} onText={t("storeTheme.sections.on")} offText={t("storeTheme.sections.off")} onChange={(showSocial) => setFooter({ showSocial })} />
            </div>
            <FormField label={t("storeTheme.chrome.footer.copyright")} error={errorFor("footer.copyright")}>
              <input value={footer.copyright ?? ""} maxLength={160} disabled={disabled} onChange={(event) => setFooter({ copyright: event.target.value || undefined })} />
            </FormField>
          </div>
          <p className="theme-hint">{t("storeTheme.chrome.footer.contactsHint")}</p>
          <BackgroundFields
            value={footer}
            config={config}
            disabled={disabled}
            shopId={shopId}
            shopSlug={shopSlug}
            errorFor={errorFor}
            prefix="footer"
            onChange={setFooter}
          />

          <div className="theme-content-group">
            <b className="theme-content-subtitle">{t("storeTheme.chrome.footer.columns", { count: footer.columns.length, max: MAX_FOOTER_COLUMNS })}</b>
            <FieldError text={errorFor("footer.columns")} />
            <ul className="theme-banner-list">
              {footer.columns.map((column, index) => (
                <li key={index} className="theme-banner-row">
                  <LocalizedInput
                    label={t("storeTheme.chrome.footer.columnTitle")}
                    value={column.title}
                    maxLength={40}
                    disabled={disabled}
                    error={errorFor(`footer.columns[${index}].title`)}
                    onChange={(title: LocalizedString | undefined) => setColumn(index, { title })}
                  />
                  <FieldError text={errorFor(`footer.columns[${index}]`) || errorFor(`footer.columns[${index}].links`)} />
                  <ChromeLinkRows
                    rows={column.links}
                    path={`footer.columns[${index}].links`}
                    max={MAX_COLUMN_LINKS}
                    disabled={disabled}
                    errorFor={errorFor}
                    addLabel={t("storeTheme.chrome.footer.linkAdd")}
                    removeLabel={t("storeTheme.chrome.footer.linkRemove")}
                    labelLabel={t("storeTheme.chrome.header.menuLabel")}
                    linkLabel={t("storeTheme.chrome.header.menuLink")}
                    onChange={(links) => setColumn(index, { links })}
                  />
                  <button type="button" className="theme-banner-remove" disabled={disabled} onClick={() => setFooter({ columns: footer.columns.filter((_, rowIndex) => rowIndex !== index) })}>
                    <Trash2 />{t("storeTheme.chrome.footer.columnRemove")}
                  </button>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="theme-banner-add"
              disabled={disabled || footer.columns.length >= MAX_FOOTER_COLUMNS}
              onClick={() => setFooter({ columns: [...footer.columns, { links: [] }] })}
            >
              <Plus />{t("storeTheme.chrome.footer.columnAdd")}
            </button>
          </div>
        </>
      ) : null}
    </fieldset>
  );
}

/** AG5 custom fonts (CUSTOM only): WOFF2 from the shop's FONT media, with the legacy `font` as fallback. */
export function FontPanel({ config, disabled, shopId, shopSlug, errorFor, patch }: ChromeEditorProps) {
  const { t } = useTranslation("merchant");
  const custom = config.fontCustom;
  const setFont = (change: Partial<ThemeFontCustom>) =>
    patch((current) => ({
      ...current,
      fontCustom: { fallback: current.font, ...current.fontCustom, ...change },
    }), "config.fontCustom");

  return (
    <fieldset className="theme-block theme-chrome">
      <legend>{t("storeTheme.chrome.fonts.title")}</legend>
      <p className="theme-hint">{t("storeTheme.chrome.fonts.hint")}</p>
      <FieldError text={errorFor("fontCustom")} />
      <MediaField label={t("storeTheme.chrome.fonts.heading")} error={errorFor("fontCustom.headingMediaId")}>
        <ThemeMediaPicker
          kind="FONT"
          value={custom?.headingMediaId}
          shopId={shopId}
          shopSlug={shopSlug}
          disabled={disabled}
          onChange={(mediaId) => setFont({ headingMediaId: mediaId, headingUrl: undefined })}
        />
      </MediaField>
      <MediaField label={t("storeTheme.chrome.fonts.body")} error={errorFor("fontCustom.bodyMediaId")}>
        <ThemeMediaPicker
          kind="FONT"
          value={custom?.bodyMediaId}
          shopId={shopId}
          shopSlug={shopSlug}
          disabled={disabled}
          onChange={(mediaId) => setFont({ bodyMediaId: mediaId, bodyUrl: undefined })}
        />
      </MediaField>
      <ThemeTokenSelect
        label={t("storeTheme.chrome.fonts.fallback")}
        value={custom?.fallback ?? config.font}
        disabled={disabled}
        error={errorFor("fontCustom.fallback")}
        options={SHOP_THEME_FONTS.map((font) => ({ value: font, label: t(`storeTheme.font.${font}`) }))}
        onChange={(picked) => setFont({ fallback: picked as ShopThemeFontKey })}
      />
      <p className="theme-hint">{t("storeTheme.chrome.fonts.fallbackHint")}</p>
    </fieldset>
  );
}
