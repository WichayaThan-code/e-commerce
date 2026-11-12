import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import ThemeMediaPicker from "./ThemeMediaPicker";
import { MediaField } from "./ThemeEditorParts";
import { ThemeTokenSelect } from "./ThemeEditorParts";
import { productApi } from "../../services/api";
import { collectionApi, type MerchantCollection } from "../../services/collectionsApi";
import {
  DEFAULT_COLLECTION_CARDS, FEATURE_LAYOUTS, GALLERY_LAYOUTS, MAX_BULLETS, MAX_COLLECTION_CARDS, MAX_GALLERY_ITEMS, MAX_RICH_TEXT, MAX_STEPS, MAX_VIDEOS, MIN_STEPS,
  STEP_ICONS, TIKTOK_ID_PATTERN,
  type BlockContent, type LocalizedString, type NewBlockType,
} from "../../theme/blockContent";

/**
 * AG4 content editors for the CUSTOM block types (STEPS / FEATURE / GALLERY / SOCIAL / RICH_TEXT).
 * They edit the raw `block.content` object, tolerate empty or partial content, and never write derived fields
 * (mediaUrl / url / productSlug) except as a draft-preview convenience, which the API whitelist drops on save.
 */

type Rec = Record<string, unknown>;

const rec = (value: unknown): Rec => (value && typeof value === "object" && !Array.isArray(value) ? (value as Rec) : {});
const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const str = (value: unknown) => (typeof value === "string" ? value : "");

/** Drops undefined keys so cleared fields disappear from the payload. */
function clean(value: Rec): Rec {
  const out: Rec = {};
  for (const [key, entry] of Object.entries(value)) if (entry !== undefined) out[key] = entry;
  return out;
}

function swap<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length) return items;
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

function toLocalized(th: string, en: string): LocalizedString | undefined {
  if (!th.trim() && !en.trim()) return undefined;
  return { th, ...(en.trim() ? { en } : {}) };
}

type EditorProps = {
  content: BlockContent;
  path: string;
  disabled: boolean;
  shopId: number | null;
  shopSlug: string;
  errorFor: (path: string) => string | undefined;
  onChange: (content: BlockContent) => void;
};

export function LocalizedInput({
  label, value, onChange, disabled, error, maxLength, multiline, rows = 4,
}: {
  label: string;
  value: unknown;
  onChange: (next: LocalizedString | undefined) => void;
  disabled: boolean;
  error?: string;
  maxLength: number;
  multiline?: boolean;
  rows?: number;
}) {
  const { t } = useTranslation("merchant");
  const current = rec(value);
  const th = str(current.th);
  const en = str(current.en);
  const set = (nextTh: string, nextEn: string) => onChange(toLocalized(nextTh, nextEn));
  const field = (lang: "th" | "en", text: string) => {
    const handle = (next: string) => (lang === "th" ? set(next, en) : set(th, next));
    return (
      <FormField
        label={`${label} · ${t(lang === "th" ? "storeTheme.content.langTh" : "storeTheme.content.langEn")}`}
        error={lang === "th" ? error : undefined}
        multiline={multiline}
      >
        {multiline ? (
          <textarea value={text} rows={rows} maxLength={maxLength} disabled={disabled} onChange={(event) => handle(event.target.value)} />
        ) : (
          <input value={text} maxLength={maxLength} disabled={disabled} onChange={(event) => handle(event.target.value)} />
        )}
      </FormField>
    );
  };
  return (
    <div className="theme-content-pair">
      {field("th", th)}
      {field("en", en)}
    </div>
  );
}

function CtaEditor({
  value, path, disabled, errorFor, onChange,
}: {
  value: unknown;
  path: string;
  disabled: boolean;
  errorFor: (path: string) => string | undefined;
  onChange: (next: Rec | undefined) => void;
}) {
  const { t } = useTranslation("merchant");
  const cta = rec(value);
  const patch = (change: Rec) => {
    const next = clean({ ...cta, ...change });
    onChange(Object.keys(next).length ? next : undefined);
  };
  return (
    <div className="theme-content-group">
      <b className="theme-content-subtitle">{t("storeTheme.content.cta")}</b>
      <LocalizedInput
        label={t("storeTheme.content.ctaLabel")}
        value={cta.label}
        maxLength={60}
        disabled={disabled}
        error={errorFor(`${path}.label`)}
        onChange={(label) => patch({ label })}
      />
      <FormField label={t("storeTheme.content.ctaLink")} error={errorFor(`${path}.link`)}>
        <input
          value={str(cta.link)}
          maxLength={500}
          spellCheck={false}
          placeholder="/shop/my-shop  ·  https://…"
          disabled={disabled}
          onChange={(event) => patch({ link: event.target.value.trim() ? event.target.value : undefined })}
        />
      </FormField>
      <p className="theme-hint">{t("storeTheme.content.linkHint")}</p>
    </div>
  );
}

function RowTools({
  index, count, disabled, onMove, onRemove, canRemove,
}: {
  index: number;
  count: number;
  disabled: boolean;
  onMove: (to: number) => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  const { t } = useTranslation("merchant");
  return (
    <span className="theme-section-move theme-content-tools">
      <button type="button" disabled={disabled || index === 0} aria-label={t("storeTheme.sections.moveUp")} title={t("storeTheme.sections.moveUp")} onClick={() => onMove(index - 1)}><ChevronUp /></button>
      <button type="button" disabled={disabled || index >= count - 1} aria-label={t("storeTheme.sections.moveDown")} title={t("storeTheme.sections.moveDown")} onClick={() => onMove(index + 1)}><ChevronDown /></button>
      <button type="button" disabled={disabled || !canRemove} aria-label={t("storeTheme.content.remove")} title={t("storeTheme.content.remove")} onClick={onRemove}><Trash2 /></button>
    </span>
  );
}

// ───────────────────────── STEPS ─────────────────────────

function StepsEditor({ content, path, disabled, shopId, shopSlug, errorFor, onChange }: EditorProps) {
  const { t } = useTranslation("merchant");
  const steps = list(content.steps).map(rec);
  const set = (change: Rec) => onChange(clean({ ...content, ...change }));
  const setSteps = (next: Rec[]) => set({ steps: next });
  const setStep = (index: number, change: Rec) => setSteps(steps.map((row, i) => (i === index ? clean({ ...row, ...change }) : row)));

  return (
    <div className="theme-content">
      <LocalizedInput label={t("storeTheme.content.title")} value={content.title} maxLength={120} disabled={disabled}
        error={errorFor(`${path}.title`)} onChange={(title) => set({ title })} />
      <LocalizedInput label={t("storeTheme.content.subtitle")} value={content.subtitle} maxLength={240} disabled={disabled}
        error={errorFor(`${path}.subtitle`)} onChange={(subtitle) => set({ subtitle })} />
      <FieldError text={errorFor(`${path}.steps`)} />
      <ul className="theme-content-list">
        {steps.map((step, index) => {
          const stepPath = `${path}.steps[${index}]`;
          const icon = STEP_ICONS.includes(str(step.icon) as (typeof STEP_ICONS)[number]) ? str(step.icon) : "NUMBER";
          return (
            <li key={index} className="theme-content-row">
              <div className="theme-content-row-head">
                <b>{t("storeTheme.content.stepN", { n: index + 1 })}</b>
                <RowTools index={index} count={steps.length} disabled={disabled} canRemove={steps.length > MIN_STEPS}
                  onMove={(to) => setSteps(swap(steps, index, to))} onRemove={() => setSteps(steps.filter((_, i) => i !== index))} />
              </div>
              <ThemeTokenSelect
                label={t("storeTheme.content.icon")}
                value={icon}
                disabled={disabled}
                error={errorFor(`${stepPath}.icon`)}
                options={STEP_ICONS.map((value) => ({ value, label: t(`storeTheme.content.icons.${value}`) }))}
                onChange={(picked) => setStep(index, picked === "CUSTOM"
                  ? { icon: picked }
                  : { icon: picked, mediaId: undefined, mediaUrl: undefined })}
              />
              {icon === "CUSTOM" ? (
                <MediaField label={t("storeTheme.content.image")} error={errorFor(`${stepPath}.mediaId`)}>
                  <ThemeMediaPicker
                    kind="IMAGE"
                    value={str(step.mediaId) || undefined}
                    shopId={shopId}
                    shopSlug={shopSlug}
                    disabled={disabled}
                    crop={step.crop as never}
                    mobileCrop={step.mobileCrop as never}
                    cropAspect={1}
                    cropMobileAspect={1}
                    onCropChange={(crop, mobileCrop) => setStep(index, { crop, mobileCrop })}
                    onChange={(mediaId, item) => setStep(index, mediaId
                      ? { mediaId, mediaUrl: item?.url ?? (str(step.mediaUrl) || undefined) }
                      : { mediaId: undefined, mediaUrl: undefined, crop: undefined, mobileCrop: undefined })}
                  />
                </MediaField>
              ) : null}
              <LocalizedInput label={t("storeTheme.content.title")} value={step.title} maxLength={80} disabled={disabled}
                error={errorFor(`${stepPath}.title`)} onChange={(title) => setStep(index, { title })} />
              <LocalizedInput label={t("storeTheme.content.body")} value={step.body} maxLength={240} multiline rows={3} disabled={disabled}
                error={errorFor(`${stepPath}.body`)} onChange={(body) => setStep(index, { body })} />
            </li>
          );
        })}
      </ul>
      <button type="button" className="theme-banner-add" disabled={disabled || steps.length >= MAX_STEPS}
        onClick={() => setSteps([...steps, { icon: "NUMBER" }])}>
        <Plus />{t("storeTheme.content.addStep", { count: steps.length, max: MAX_STEPS })}
      </button>
      <CtaEditor value={content.cta} path={`${path}.cta`} disabled={disabled} errorFor={errorFor} onChange={(cta) => set({ cta })} />
    </div>
  );
}

// ───────────────────────── FEATURE ─────────────────────────

type ProductOption = { id: string; slug: string; name: string };

function FeatureEditor({ content, path, disabled, shopId, shopSlug, errorFor, onChange }: EditorProps) {
  const { t, i18n } = useTranslation("merchant");
  const [products, setProducts] = useState<ProductOption[]>([]);
  const bullets = list(content.bullets);
  const set = (change: Rec) => onChange(clean({ ...content, ...change }));
  const layout = FEATURE_LAYOUTS.includes(str(content.layout) as (typeof FEATURE_LAYOUTS)[number]) ? str(content.layout) : "IMAGE_LEFT";

  useEffect(() => {
    if (shopId == null) return undefined;
    let active = true;
    productApi.list(shopId, { status: "PUBLISHED", size: 100 })
      .then((page) => {
        if (!active) return;
        const english = i18n.language.toLowerCase().startsWith("en");
        setProducts(page.items.map((row) => ({
          id: row.id,
          slug: row.slug,
          name: (english && row.nameEn?.trim()) || row.nameTh,
        })));
      })
      .catch(() => { if (active) setProducts([]); });
    return () => { active = false; };
  }, [shopId, i18n.language]);

  const productId = str(content.productId);

  return (
    <div className="theme-content">
      <MediaField label={t("storeTheme.content.image")} error={errorFor(`${path}.mediaId`)}>
        <ThemeMediaPicker
          kind="IMAGE"
          value={str(content.mediaId) || undefined}
          shopId={shopId}
          shopSlug={shopSlug}
          disabled={disabled}
          crop={content.crop as never}
          mobileCrop={content.mobileCrop as never}
          cropAspect={4 / 3}
          cropMobileAspect={4 / 3}
          onCropChange={(crop, mobileCrop) => set({ crop, mobileCrop })}
          onChange={(mediaId, item) => set(mediaId
            ? { mediaId, mediaUrl: item?.url ?? (str(content.mediaUrl) || undefined) }
            : { mediaId: undefined, mediaUrl: undefined, crop: undefined, mobileCrop: undefined })}
        />
      </MediaField>
      <ThemeTokenSelect
        label={t("storeTheme.content.layout")}
        value={layout}
        disabled={disabled}
        error={errorFor(`${path}.layout`)}
        options={FEATURE_LAYOUTS.map((value) => ({ value, label: t(`storeTheme.content.featureLayouts.${value}`) }))}
        onChange={(picked) => set({ layout: picked })}
      />
      <LocalizedInput label={t("storeTheme.content.title")} value={content.title} maxLength={120} disabled={disabled}
        error={errorFor(`${path}.title`)} onChange={(title) => set({ title })} />
      <LocalizedInput label={t("storeTheme.content.body")} value={content.body} maxLength={1000} multiline disabled={disabled}
        error={errorFor(`${path}.body`)} onChange={(body) => set({ body })} />
      <div className="theme-content-group">
        <b className="theme-content-subtitle">{t("storeTheme.content.bullets")}</b>
        <FieldError text={errorFor(`${path}.bullets`)} />
        <ul className="theme-content-list">
          {bullets.map((bullet, index) => (
            <li key={index} className="theme-content-row">
              <div className="theme-content-row-head">
                <b>{t("storeTheme.content.bulletN", { n: index + 1 })}</b>
                <RowTools index={index} count={bullets.length} disabled={disabled} canRemove
                  onMove={(to) => set({ bullets: swap(bullets, index, to) })}
                  onRemove={() => set({ bullets: bullets.filter((_, i) => i !== index) })} />
              </div>
              <LocalizedInput label={t("storeTheme.content.bullet")} value={bullet} maxLength={160} disabled={disabled}
                error={errorFor(`${path}.bullets[${index}]`)}
                onChange={(next) => set({ bullets: bullets.map((row, i) => (i === index ? (next ?? { th: "" }) : row)) })} />
            </li>
          ))}
        </ul>
        <button type="button" className="theme-banner-add" disabled={disabled || bullets.length >= MAX_BULLETS}
          onClick={() => set({ bullets: [...bullets, { th: "" }] })}>
          <Plus />{t("storeTheme.content.addBullet", { count: bullets.length, max: MAX_BULLETS })}
        </button>
      </div>
      <CtaEditor value={content.cta} path={`${path}.cta`} disabled={disabled} errorFor={errorFor} onChange={(cta) => set({ cta })} />
      <div className="theme-content-group">
        <ThemeTokenSelect
          label={t("storeTheme.content.product")}
          value={productId}
          disabled={disabled || shopId == null}
          error={errorFor(`${path}.productId`)}
          options={products.map((row) => ({ value: row.id, label: row.name }))}
          onChange={(picked) => set({
            productId: picked,
            productSlug: products.find((row) => row.id === picked)?.slug,
          })}
        />
        <p className="theme-hint">{products.length ? t("storeTheme.content.productHint") : t("storeTheme.content.productEmpty")}</p>
        {productId ? (
          <button type="button" className="theme-banner-add" disabled={disabled}
            onClick={() => set({ productId: undefined, productSlug: undefined })}>
            <Trash2 />{t("storeTheme.content.productClear")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

// ───────────────────────── GALLERY ─────────────────────────

function GalleryEditor({ content, path, disabled, shopId, shopSlug, errorFor, onChange }: EditorProps) {
  const { t } = useTranslation("merchant");
  const items = list(content.items).map(rec);
  const set = (change: Rec) => onChange(clean({ ...content, ...change }));
  const setItems = (next: Rec[]) => set({ items: next });
  const setItem = (index: number, change: Rec) => setItems(items.map((row, i) => (i === index ? clean({ ...row, ...change }) : row)));
  const layout = GALLERY_LAYOUTS.includes(str(content.layout) as (typeof GALLERY_LAYOUTS)[number]) ? str(content.layout) : "GRID";

  return (
    <div className="theme-content">
      <LocalizedInput label={t("storeTheme.content.title")} value={content.title} maxLength={120} disabled={disabled}
        error={errorFor(`${path}.title`)} onChange={(title) => set({ title })} />
      <ThemeTokenSelect
        label={t("storeTheme.content.layout")}
        value={layout}
        disabled={disabled}
        error={errorFor(`${path}.layout`)}
        options={GALLERY_LAYOUTS.map((value) => ({ value, label: t(`storeTheme.content.galleryLayouts.${value}`) }))}
        onChange={(picked) => set({ layout: picked })}
      />
      <FieldError text={errorFor(`${path}.items`)} />
      <ul className="theme-content-list">
        {items.map((item, index) => {
          const itemPath = `${path}.items[${index}]`;
          return (
            <li key={index} className="theme-content-row">
              <div className="theme-content-row-head">
                <b>{t("storeTheme.content.imageN", { n: index + 1 })}</b>
                <RowTools index={index} count={items.length} disabled={disabled} canRemove
                  onMove={(to) => setItems(swap(items, index, to))} onRemove={() => setItems(items.filter((_, i) => i !== index))} />
              </div>
              <MediaField label={t("storeTheme.content.image")} error={errorFor(`${itemPath}.mediaId`)}>
                <ThemeMediaPicker
                  kind="IMAGE"
                  value={str(item.mediaId) || undefined}
                  shopId={shopId}
                  shopSlug={shopSlug}
                  disabled={disabled}
                  crop={item.crop as never}
                  mobileCrop={item.mobileCrop as never}
                  cropAspect={1}
                  cropMobileAspect={1}
                  onCropChange={(crop, mobileCrop) => setItem(index, { crop, mobileCrop })}
                  onChange={(mediaId, picked) => setItem(index, mediaId
                    ? { mediaId, url: picked?.url ?? (str(item.url) || undefined) }
                    : { mediaId: "", url: undefined, crop: undefined, mobileCrop: undefined })}
                />
              </MediaField>
              <LocalizedInput label={t("storeTheme.content.caption")} value={item.caption} maxLength={160} disabled={disabled}
                error={errorFor(`${itemPath}.caption`)} onChange={(caption) => setItem(index, { caption })} />
              <FormField label={t("storeTheme.content.itemLink")} error={errorFor(`${itemPath}.link`)}>
                <input value={str(item.link)} maxLength={500} spellCheck={false} placeholder="/shop/my-shop  ·  https://…" disabled={disabled}
                  onChange={(event) => setItem(index, { link: event.target.value.trim() ? event.target.value : undefined })} />
              </FormField>
            </li>
          );
        })}
      </ul>
      <p className="theme-hint">{t("storeTheme.content.galleryHint")}</p>
      <button type="button" className="theme-banner-add" disabled={disabled || items.length >= MAX_GALLERY_ITEMS}
        onClick={() => setItems([...items, { mediaId: "" }])}>
        <Plus />{t("storeTheme.content.addImage", { count: items.length, max: MAX_GALLERY_ITEMS })}
      </button>
    </div>
  );
}

// ───────────────────────── SOCIAL ─────────────────────────

function SocialEditor({ content, path, disabled, errorFor, onChange }: EditorProps) {
  const { t } = useTranslation("merchant");
  const ids = list(content.videoIds).map(str);
  const set = (change: Rec) => onChange(clean({ ...content, provider: "TIKTOK", ...change }));
  const setIds = (next: string[]) => set({ videoIds: next });

  return (
    <div className="theme-content">
      <LocalizedInput label={t("storeTheme.content.title")} value={content.title} maxLength={120} disabled={disabled}
        error={errorFor(`${path}.title`)} onChange={(title) => set({ title })} />
      <p className="theme-hint">{t("storeTheme.content.socialHint")}</p>
      <FieldError text={errorFor(`${path}.videoIds`) || errorFor(`${path}.provider`)} />
      <ul className="theme-content-list">
        {ids.map((id, index) => {
          const bad = id.trim() !== "" && !TIKTOK_ID_PATTERN.test(id.trim());
          return (
            <li key={index} className="theme-content-row">
              <div className="theme-content-row-head">
                <b>{t("storeTheme.content.videoN", { n: index + 1 })}</b>
                <RowTools index={index} count={ids.length} disabled={disabled} canRemove
                  onMove={(to) => setIds(swap(ids, index, to))} onRemove={() => setIds(ids.filter((_, i) => i !== index))} />
              </div>
              <FormField
                label={t("storeTheme.content.videoId")}
                error={errorFor(`${path}.videoIds[${index}]`) || (bad ? t("storeTheme.content.videoIdInvalid") : undefined)}
              >
                <input
                  value={id}
                  inputMode="numeric"
                  maxLength={64}
                  spellCheck={false}
                  placeholder="7234567890123456789"
                  disabled={disabled}
                  aria-invalid={bad}
                  onChange={(event) => setIds(ids.map((row, i) => (i === index ? event.target.value : row)))}
                />
              </FormField>
            </li>
          );
        })}
      </ul>
      <button type="button" className="theme-banner-add" disabled={disabled || ids.length >= MAX_VIDEOS}
        onClick={() => setIds([...ids, ""])}>
        <Plus />{t("storeTheme.content.addVideo", { count: ids.length, max: MAX_VIDEOS })}
      </button>
    </div>
  );
}

// ───────────────────────── RICH_TEXT ─────────────────────────

function RichTextEditor({ content, path, disabled, errorFor, onChange }: EditorProps) {
  const { t } = useTranslation("merchant");
  const markdown = rec(content.markdown);
  const length = Math.max(str(markdown.th).length, str(markdown.en).length);
  return (
    <div className="theme-content">
      <p className="theme-hint">{t("storeTheme.content.richTextHint")}</p>
      <LocalizedInput
        label={t("storeTheme.content.markdown")}
        value={content.markdown}
        maxLength={MAX_RICH_TEXT}
        multiline
        rows={8}
        disabled={disabled}
        error={errorFor(`${path}.markdown`) || errorFor(`${path}.markdown.th`) || errorFor(`${path}.markdown.en`)}
        onChange={(next) => onChange(clean({ ...content, markdown: next, html: undefined }))}
      />
      <p className="theme-hint">{t("storeTheme.content.richTextCount", { count: length, max: MAX_RICH_TEXT })}</p>
    </div>
  );
}

// ───────────────────────── COLLECTIONS (V41) ─────────────────────────

function CollectionsEditor({ content, path, disabled, shopId, errorFor, onChange }: EditorProps) {
  const { t } = useTranslation("merchant");
  const [collections, setCollections] = useState<MerchantCollection[]>([]);
  const slugs = list(content.collectionSlugs).map(str).filter(Boolean);
  const limit = typeof content.limit === "number" ? content.limit : DEFAULT_COLLECTION_CARDS;
  const set = (change: Rec) => onChange(clean({ ...content, collectionSlugs: slugs, limit, ...change }));

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    collectionApi.list(shopId).then((rows) => { if (alive) setCollections(rows); }).catch(() => { if (alive) setCollections([]); });
    return () => { alive = false; };
  }, [shopId]);

  const nameOf = (slug: string) => {
    const row = collections.find((item) => item.slug === slug);
    return row ? `${row.nameTh}${row.published ? "" : ` (${t("collections.draft")})`}` : slug;
  };
  const addable = collections.filter((row) => !slugs.includes(row.slug));

  return (
    <div className="theme-content">
      <LocalizedInput label={t("storeTheme.content.title")} value={content.title} maxLength={120} disabled={disabled}
        error={errorFor(`${path}.title`)} onChange={(title) => set({ title })} />
      <LocalizedInput label={t("storeTheme.content.subtitle")} value={content.subtitle} maxLength={240} disabled={disabled}
        error={errorFor(`${path}.subtitle`)} onChange={(subtitle) => set({ subtitle })} />
      <ThemeTokenSelect
        label={t("collections.block.limit")}
        value={String(limit)}
        disabled={disabled}
        error={errorFor(`${path}.limit`)}
        options={Array.from({ length: MAX_COLLECTION_CARDS }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }))}
        onChange={(picked) => set({ limit: Number(picked) })}
      />
      <p className="theme-hint">{t("collections.block.hint")}</p>
      <FieldError text={errorFor(`${path}.collectionSlugs`)} />
      <ul className="theme-content-list">
        {slugs.map((slug, index) => (
          <li key={slug} className="theme-content-row">
            <div className="theme-content-row-head">
              <b>{nameOf(slug)}</b>
              <RowTools index={index} count={slugs.length} disabled={disabled} canRemove
                onMove={(to) => set({ collectionSlugs: swap(slugs, index, to) })}
                onRemove={() => set({ collectionSlugs: slugs.filter((_, i) => i !== index) })} />
            </div>
          </li>
        ))}
      </ul>
      {addable.length && slugs.length < MAX_COLLECTION_CARDS ? (
        <ThemeTokenSelect
          label={t("collections.block.add")}
          value=""
          disabled={disabled}
          options={addable.map((row) => ({ value: row.slug, label: nameOf(row.slug) }))}
          onChange={(picked) => set({ collectionSlugs: [...slugs, picked] })}
        />
      ) : null}
      {!collections.length ? <p className="theme-hint">{t("collections.block.none")}</p> : null}
    </div>
  );
}

/** Dispatches to the editor of one AG4 block type. `path` is the block path (`layout.blocks[i]`). */
export default function BlockContentEditor({ type, ...props }: EditorProps & { type: NewBlockType }) {
  const shared = { ...props, path: `${props.path}.content` };
  switch (type) {
    case "STEPS": return <StepsEditor {...shared} />;
    case "FEATURE": return <FeatureEditor {...shared} />;
    case "GALLERY": return <GalleryEditor {...shared} />;
    case "SOCIAL": return <SocialEditor {...shared} />;
    case "RICH_TEXT": return <RichTextEditor {...shared} />;
    case "COLLECTIONS": return <CollectionsEditor {...shared} />;
    default: return null;
  }
}
