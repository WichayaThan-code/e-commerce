import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronUp, Copy, GripVertical, Plus, Settings2, Sparkles, Trash2 } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import ToggleSwitch from "../form/ToggleSwitch";
import { isShopSectionAvailable } from "../shop/sections/availability";
import BlockContentEditor from "./BlockContentEditors";
import { isNewBlockType } from "../../theme/blockContent";
import ThemeMediaPicker from "./ThemeMediaPicker";
import { MediaField, ThemeSelect } from "./ThemeEditorParts";
import { GradientEditor, ThemeTokenSelect } from "./ThemeEditorParts";
import {
  createLayoutBlock, MAX_LAYOUT_BLOCKS, PRODUCT_BLOCK_TYPES,
  SHOP_BLOCK_BACKGROUNDS, SHOP_PADDING_Y, SHOP_SECTION_ALIGNS,
  isSectionKey, SHOP_BLOCK_TYPES, SINGLETON_BLOCK_TYPES, sectionSupportsVariant, uniqueBlockId, variantsForSection,
  type BlockSettings, type LayoutBlock, type SectionVariant, type ShopBlockBackground,
  type ShopPaddingY, type ShopBlockType, type ShopSectionAlign, type ThemeColors, type ThemeGradient,
} from "../../theme/shopTheme";

/** Cream → soft pink panel used by the "alternate pink/cream" preset (structured gradient, never a CSS string). */
const CREAM_GRADIENT: ThemeGradient = {
  angle: 180,
  stops: [{ color: "#fffaf0", at: 0 }, { color: "#fff0f3", at: 100 }],
};

type ShopBlocksEditorProps = {
  blocks: LayoutBlock[];
  colors: ThemeColors;
  disabled: boolean;
  shopId: number | null;
  shopSlug: string;
  errorFor: (path: string) => string | undefined;
  onChange: (blocks: LayoutBlock[]) => void;
};

function moveBlock(blocks: LayoutBlock[], from: number, to: number) {
  if (from === to || from < 0 || to < 0 || from >= blocks.length || to >= blocks.length) return blocks;
  const next = [...blocks];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

/** AG3 CUSTOM block list: add / duplicate / delete / enable / reorder plus a per-block settings panel. */
export default function ShopBlocksEditor({
  blocks, colors, disabled, shopId, shopSlug, errorFor, onChange,
}: ShopBlocksEditorProps) {
  const { t } = useTranslation("merchant");
  const [openId, setOpenId] = useState<string | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [addType, setAddType] = useState<ShopBlockType>("FEATURED");

  const atLimit = blocks.length >= MAX_LAYOUT_BLOCKS;
  const present = new Set(blocks.map((block) => block.type));
  const addable = SHOP_BLOCK_TYPES.filter((key) => !(SINGLETON_BLOCK_TYPES.includes(key) && present.has(key)));
  const pickedType = addable.includes(addType) ? addType : addable[0];

  const patchBlock = (index: number, change: Partial<LayoutBlock>) =>
    onChange(blocks.map((block, i) => (i === index ? { ...block, ...change } : block)));

  const patchSettings = (index: number, change: Partial<BlockSettings>) =>
    onChange(blocks.map((block, i) => (i === index ? { ...block, settings: { ...block.settings, ...change } } : block)));

  const addBlock = () => {
    if (atLimit || !pickedType) return;
    const block = createLayoutBlock(pickedType, blocks.map((row) => row.id));
    onChange([...blocks, block]);
    setOpenId(block.id);
  };

  const duplicate = (index: number) => {
    const source = blocks[index];
    if (atLimit || SINGLETON_BLOCK_TYPES.includes(source.type)) return;
    const copy: LayoutBlock = structuredClone(source);
    copy.id = uniqueBlockId(`${source.id.slice(0, 33)}-copy`, new Set(blocks.map((row) => row.id)));
    const next = [...blocks];
    next.splice(index + 1, 0, copy);
    onChange(next);
    setOpenId(copy.id);
  };

  const remove = (index: number) => {
    if (blocks.length <= 1) return;
    if (openId === blocks[index].id) setOpenId(null);
    onChange(blocks.filter((_, i) => i !== index));
  };

  /** One click: enabled blocks (except the shop header) alternate SURFACE / cream gradient. */
  const alternate = () => {
    let visibleIndex = 0;
    onChange(blocks.map((block) => {
      if (!block.enabled || block.type === "HERO") return block;
      const even = visibleIndex % 2 === 0;
      visibleIndex += 1;
      const rest: BlockSettings = { ...block.settings };
      delete rest.backgroundMediaId;
      delete rest.backgroundUrl;
      delete rest.gradient;
      const settings: BlockSettings = even
        ? { ...rest, background: "SURFACE" }
        : { ...rest, background: "GRADIENT", gradient: structuredClone(CREAM_GRADIENT) };
      return { ...block, settings };
    }));
  };

  const typeLabel = (type: ShopBlockType) => t(`storeTheme.sections.${type}`);

  return (
    <fieldset className="theme-block theme-blocks">
      <legend>{t("storeTheme.blocks.title")}</legend>
      <p className="theme-hint">{t("storeTheme.blocks.hint", { max: MAX_LAYOUT_BLOCKS })}</p>
      <FieldError text={errorFor("layout.blocks")} />

      <div className="theme-blocks-toolbar">
        <ThemeTokenSelect
          label={t("storeTheme.blocks.addType")}
          value={pickedType ?? ""}
          disabled={disabled || atLimit}
          options={addable.map((key) => ({ value: key, label: typeLabel(key) }))}
          onChange={(picked) => setAddType(picked as ShopBlockType)}
        />
        <button type="button" className="theme-banner-add" disabled={disabled || atLimit || !pickedType} onClick={addBlock}>
          <Plus />{t("storeTheme.blocks.add")}
        </button>
        <button type="button" className="theme-banner-add" disabled={disabled} onClick={alternate}>
          <Sparkles />{t("storeTheme.blocks.alternate")}
        </button>
      </div>
      <p className="theme-hint">
        {t("storeTheme.blocks.count", { count: blocks.length, max: MAX_LAYOUT_BLOCKS })}
        {atLimit ? ` · ${t("storeTheme.blocks.limit", { max: MAX_LAYOUT_BLOCKS })}` : ""}
      </p>

      <ul className="theme-sections theme-blocks-list">
        {blocks.map((block, index) => {
          const path = `layout.blocks[${index}]`;
          const variants = isSectionKey(block.type) && sectionSupportsVariant(block.type) ? variantsForSection(block.type, true) : [];
          const open = openId === block.id;
          const settings = block.settings;
          const rowError = errorFor(`${path}`) || errorFor(`${path}.id`) || errorFor(`${path}.type`)
            || errorFor(`${path}.enabled`) || errorFor(`${path}.variant`);
          const singleton = SINGLETON_BLOCK_TYPES.includes(block.type);
          return (
            <li
              key={block.id}
              className={`theme-section-row theme-blocks-row${block.enabled ? "" : " is-off"}${dragIndex === index ? " is-dragging" : ""}`}
              draggable={!disabled}
              onDragStart={() => setDragIndex(index)}
              onDragEnd={() => setDragIndex(null)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndex != null) onChange(moveBlock(blocks, dragIndex, index));
                setDragIndex(null);
              }}
            >
              <span className="theme-section-grip" aria-hidden="true"><GripVertical /></span>
              <b
                className="theme-section-name"
                title={isShopSectionAvailable(block.type) ? undefined : t("storeTheme.sections.comingSoonHint")}
              >
                {typeLabel(block.type)}
                {isShopSectionAvailable(block.type) ? null : ` · ${t("storeTheme.sections.comingSoon")}`}
                <small className="theme-blocks-id"> #{block.id}</small>
              </b>
              {variants.length ? (
                <ThemeSelect inline
                  label={`${typeLabel(block.type)} · ${t("storeTheme.sections.variant")}`}
                  value={block.variant ?? variants[0] ?? ""}
                  disabled={disabled}
                  onChange={(event) => patchBlock(index, { variant: event.target.value as SectionVariant })}
                >
                  {variants.map((variant) => <option key={variant} value={variant ?? ""}>{t(`storeTheme.variants.${variant}`)}</option>)}
                </ThemeSelect>
              ) : <span />}
              <ToggleSwitch
                checked={block.enabled}
                disabled={disabled}
                label={`${typeLabel(block.type)} #${index + 1}`}
                onText={t("storeTheme.sections.on")}
                offText={t("storeTheme.sections.off")}
                onChange={(enabled) => patchBlock(index, { enabled })}
              />
              <span className="theme-section-move">
                <button type="button" disabled={disabled || index === 0} aria-label={t("storeTheme.sections.moveUp")} title={t("storeTheme.sections.moveUp")} onClick={() => onChange(moveBlock(blocks, index, index - 1))}><ChevronUp /></button>
                <button type="button" disabled={disabled || index === blocks.length - 1} aria-label={t("storeTheme.sections.moveDown")} title={t("storeTheme.sections.moveDown")} onClick={() => onChange(moveBlock(blocks, index, index + 1))}><ChevronDown /></button>
                <button type="button" disabled={disabled || atLimit || singleton} aria-label={t("storeTheme.blocks.duplicate")} title={singleton ? t("storeTheme.blocks.singleton") : t("storeTheme.blocks.duplicate")} onClick={() => duplicate(index)}><Copy /></button>
                <button type="button" disabled={disabled || blocks.length <= 1} aria-label={t("storeTheme.blocks.delete")} title={t("storeTheme.blocks.delete")} onClick={() => remove(index)}><Trash2 /></button>
                <button type="button" aria-expanded={open} aria-label={t("storeTheme.blocks.settings")} title={t("storeTheme.blocks.settings")} className={open ? "is-active" : ""} onClick={() => setOpenId(open ? null : block.id)}><Settings2 /></button>
              </span>
              {rowError ? <FieldError text={rowError} /> : null}

              {open ? (
                <div className="theme-blocks-panel">
                  {isNewBlockType(block.type) ? (
                    <fieldset className="theme-content-fieldset">
                      <legend>{t("storeTheme.content.legend", { type: typeLabel(block.type) })}</legend>
                      <BlockContentEditor
                        type={block.type}
                        content={block.content ?? {}}
                        path={path}
                        disabled={disabled}
                        shopId={shopId}
                        shopSlug={shopSlug}
                        errorFor={errorFor}
                        onChange={(content) => patchBlock(index, { content })}
                      />
                    </fieldset>
                  ) : null}
                  <div className="theme-grid">
                    <ThemeTokenSelect
                      label={t("storeTheme.sectionSettings.background")}
                      value={settings.background}
                      disabled={disabled}
                      error={errorFor(`${path}.settings.background`)}
                      options={SHOP_BLOCK_BACKGROUNDS.map((value) => ({ value, label: t(`storeTheme.blocks.backgrounds.${value}`) }))}
                      onChange={(picked) => patchSettings(index, { background: picked as ShopBlockBackground })}
                    />
                    <ThemeTokenSelect
                      label={t("storeTheme.sectionSettings.align")}
                      value={settings.align}
                      disabled={disabled}
                      error={errorFor(`${path}.settings.align`)}
                      options={SHOP_SECTION_ALIGNS.map((value) => ({ value, label: t(`storeTheme.sectionSettings.aligns.${value}`) }))}
                      onChange={(picked) => patchSettings(index, { align: picked as ShopSectionAlign })}
                    />
                    <ThemeTokenSelect
                      label={t("storeTheme.sectionSettings.paddingY")}
                      value={settings.paddingY}
                      disabled={disabled}
                      error={errorFor(`${path}.settings.paddingY`)}
                      options={SHOP_PADDING_Y.map((value) => ({ value, label: t(`storeTheme.sectionSettings.paddings.${value}`) }))}
                      onChange={(picked) => patchSettings(index, { paddingY: picked as ShopPaddingY })}
                    />
                    {PRODUCT_BLOCK_TYPES.includes(block.type) ? (
                      <ThemeTokenSelect
                        label={t("storeTheme.sectionSettings.columns")}
                        value={String(settings.columns ?? 3)}
                        disabled={disabled}
                        error={errorFor(`${path}.settings.columns`)}
                        options={[2, 3, 4, 5].map((value) => ({ value: String(value), label: String(value) }))}
                        onChange={(picked) => patchSettings(index, { columns: Number.parseInt(picked, 10) as 2 | 3 | 4 | 5 })}
                      />
                    ) : null}
                    <div className="theme-trust-toggle">
                      <ToggleSwitch
                        checked={settings.fullWidth}
                        disabled={disabled}
                        label={t("storeTheme.blocks.fullWidth")}
                        onText={t("storeTheme.sections.on")}
                        offText={t("storeTheme.sections.off")}
                        onChange={(fullWidth) => patchSettings(index, { fullWidth })}
                      />
                    </div>
                  </div>

                  {settings.background === "GRADIENT" ? (
                    <GradientEditor
                      title={t("storeTheme.blocks.gradient")}
                      angleLabel={t("storeTheme.gradients.angle")}
                      stopLabel={(stop) => t("storeTheme.gradients.stop", { index: stop + 1 })}
                      addStopLabel={t("storeTheme.gradients.addStop")}
                      removeStopLabel={t("storeTheme.gradients.removeStop")}
                      gradient={settings.gradient}
                      fallbackColors={colors}
                      disabled={disabled}
                      hexError={t("storeTheme.errors.hex")}
                      errorFor={errorFor}
                      prefix={`${path}.settings.gradient`}
                      onChange={(gradient) => patchSettings(index, { gradient })}
                    />
                  ) : null}
                  <FieldError text={errorFor(`${path}.settings.gradient`)} />

                  {settings.background === "IMAGE" ? (
                    <>
                      <MediaField label={t("storeTheme.blocks.backgroundImage")} error={errorFor(`${path}.settings.backgroundMediaId`)}>
                        <ThemeMediaPicker
                          kind="IMAGE"
                          value={settings.backgroundMediaId}
                          shopId={shopId}
                          shopSlug={shopSlug}
                          disabled={disabled}
                          onChange={(mediaId, item) => patchSettings(index, mediaId
                            ? { backgroundMediaId: mediaId, backgroundUrl: item?.url ?? settings.backgroundUrl }
                            : { backgroundMediaId: undefined, backgroundUrl: undefined })}
                        />
                      </MediaField>
                      <FormField label={t("storeTheme.blocks.overlay")} error={errorFor(`${path}.settings.overlay`)}>
                        <input
                          type="number"
                          className="num"
                          min={0}
                          max={80}
                          step={5}
                          value={settings.overlay ?? 0}
                          disabled={disabled}
                          onChange={(event) => {
                            const next = Number.parseInt(event.target.value, 10);
                            if (!Number.isFinite(next)) return;
                            patchSettings(index, { overlay: Math.max(0, Math.min(80, next)) });
                          }}
                        />
                      </FormField>
                    </>
                  ) : null}
                  <FieldError text={errorFor(`${path}.settings.fullWidth`) || errorFor(`${path}.settings`)} />
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}
