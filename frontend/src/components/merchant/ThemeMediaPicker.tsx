import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, Film, Trash2, Type, Upload } from "lucide-react";
import { ApiError, mediaUrl, themeMediaApi, type ThemeMediaItem, type ThemeMediaKind } from "../../services/api";
import { ImageFocalTrigger } from "./ImageFocalEditor";
import type { ImageCrop } from "../shop/imageCrop";
import "../../styles/theme-media-picker.css";
import "../../styles/image-focal-editor.css";

const ACCEPT: Record<ThemeMediaKind, string> = {
  IMAGE: "image/jpeg,image/png,image/webp",
  VIDEO: "video/mp4",
  FONT: "font/woff2,.woff2",
};

/** Client-side guard only; the backend re-checks size, magic bytes and quota. */
const MAX_BYTES: Record<ThemeMediaKind, number> = {
  IMAGE: 5 * 1024 * 1024,
  VIDEO: 20 * 1024 * 1024,
  FONT: 1024 * 1024,
};

const KNOWN_ERRORS = new Set([
  "THEME_MEDIA_CUSTOM_ONLY",
  "THEME_MEDIA_QUOTA_EXCEEDED",
  "THEME_MEDIA_IN_USE",
  "THEME_MEDIA_INVALID",
  "THEME_MEDIA_KIND_INVALID",
  "THEME_MEDIA_NOT_FOUND",
  "FILE_TOO_LARGE",
]);

export type ThemeMediaPickerProps = {
  kind: ThemeMediaKind;
  /** Selected media id (stored in the theme config as mediaId). */
  value?: string | null;
  onChange: (mediaId: string | undefined, item?: ThemeMediaItem) => void;
  shopId: number | null;
  shopSlug: string;
  disabled?: boolean;
  /** Optional focal-point editor (IMAGE only). */
  crop?: ImageCrop | null;
  mobileCrop?: ImageCrop | null;
  cropAspect?: number;
  cropMobileAspect?: number;
  onCropChange?: (crop: ImageCrop, mobileCrop?: ImageCrop) => void;
};

function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export default function ThemeMediaPicker({
  kind,
  value,
  onChange,
  shopId,
  shopSlug,
  disabled,
  crop,
  mobileCrop,
  cropAspect = 16 / 9,
  cropMobileAspect,
  onCropChange,
}: ThemeMediaPickerProps) {
  const { t } = useTranslation("merchant");
  const inputRef = useRef<HTMLInputElement>(null);
  const [items, setItems] = useState<ThemeMediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const errorText = useCallback((err: unknown, fallbackKey: string) => {
    if (err instanceof ApiError && KNOWN_ERRORS.has(err.code)) return t(`storeTheme.media.errors.${err.code}`);
    return t(fallbackKey);
  }, [t]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    themeMediaApi.list(kind, shopId)
      .then((rows) => { if (active) setItems(rows); })
      .catch((err) => { if (active) setError(errorText(err, "storeTheme.media.errors.load")); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [kind, shopId, errorText]);

  const urlFor = (item: Pick<ThemeMediaItem, "url">) => mediaUrl(item.url);
  const selected = value ? items.find((item) => item.id === value) : undefined;
  const selectedUrl = selected
    ? urlFor(selected)
    : value && shopSlug ? mediaUrl(`/api/shops/${shopSlug}/theme-media/${value}`) : "";

  const upload = async (file: File | undefined) => {
    if (!file || disabled) return;
    setError(null);
    if (file.size > MAX_BYTES[kind]) {
      setError(t("storeTheme.media.errors.FILE_TOO_LARGE", { size: formatSize(MAX_BYTES[kind]) }));
      return;
    }
    setProgress(0);
    try {
      const created = await themeMediaApi.upload(kind, file, shopId, setProgress);
      setItems((rows) => (rows.some((row) => row.id === created.id) ? rows : [created, ...rows]));
      onChange(created.id, created);
    } catch (err) {
      setError(errorText(err, "storeTheme.media.errors.upload"));
    } finally {
      setProgress(null);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  const remove = async (item: ThemeMediaItem) => {
    if (disabled) return;
    if (confirmDelete !== item.id) {
      setConfirmDelete(item.id);
      return;
    }
    setConfirmDelete(null);
    setError(null);
    try {
      await themeMediaApi.remove(item.id, shopId);
      setItems((rows) => rows.filter((row) => row.id !== item.id));
      if (value === item.id) onChange(undefined);
    } catch (err) {
      setError(errorText(err, "storeTheme.media.errors.delete"));
    }
  };

  const renderThumb = (item: ThemeMediaItem) => {
    if (kind === "IMAGE") return <img src={urlFor(item)} alt={item.originalFilename} loading="lazy" />;
    if (kind === "VIDEO") return <Film aria-hidden="true" />;
    return <Type aria-hidden="true" />;
  };

  return (
    <div className="theme-media-picker">
      <div className="theme-media-current">
        {value && kind === "IMAGE" && selectedUrl ? (
          <img className="theme-media-preview" src={selectedUrl} alt={selected?.originalFilename || t("storeTheme.media.selected")} />
        ) : value && kind === "VIDEO" && selectedUrl ? (
          <video className="theme-media-preview" src={selectedUrl} muted controls preload="metadata" />
        ) : null}
        <div className="theme-media-actions">
          <input
            ref={inputRef}
            type="file"
            hidden
            accept={ACCEPT[kind]}
            disabled={disabled || progress !== null}
            onChange={(event) => void upload(event.target.files?.[0])}
          />
          <button
            type="button"
            className="theme-banner-add"
            disabled={disabled || progress !== null}
            onClick={() => inputRef.current?.click()}
          >
            <Upload />{progress !== null ? t("storeTheme.media.uploading") : t(`storeTheme.media.upload.${kind}`)}
          </button>
          {!value ? (
            <span className="theme-media-filename is-empty">{t("storeTheme.media.none")}</span>
          ) : (
            <button type="button" className="theme-banner-remove" disabled={disabled} onClick={() => onChange(undefined)}>
              {t("storeTheme.media.clear")}
            </button>
          )}
        </div>
        {value ? (
          <p className="theme-media-filename is-selected" title={selected?.originalFilename || undefined}>
            {selected?.originalFilename || t("storeTheme.media.selected")}
          </p>
        ) : null}
        {kind === "IMAGE" && value && selectedUrl && onCropChange ? (
          <div className="theme-media-crop-row">
            <ImageFocalTrigger
              imageUrl={selectedUrl}
              disabled={disabled}
              aspect={cropAspect}
              mobileAspect={cropMobileAspect}
              value={crop}
              mobileValue={mobileCrop}
              onChange={onCropChange}
            />
          </div>
        ) : null}
        {progress !== null ? (
          <div
            className="theme-media-progress"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(progress * 100)}
          >
            <span style={{ width: `${Math.round(progress * 100)}%` }} />
          </div>
        ) : null}
        <p className="theme-hint theme-media-hint">{t(`storeTheme.media.hint.${kind}`)}</p>
        {error ? <p className="theme-media-error" role="alert">{error}</p> : null}
      </div>

      <div className="theme-media-library" aria-busy={loading}>
        <span className="theme-media-library-title">{t("storeTheme.media.library")}</span>
        {loading ? (
          <p className="theme-hint">{t("storeTheme.media.loading")}</p>
        ) : items.length === 0 ? (
          <p className="theme-hint">{t("storeTheme.media.empty")}</p>
        ) : (
          <ul className="theme-media-grid">
            {items.map((item) => (
              <li key={item.id} className={`theme-media-item${item.id === value ? " is-selected" : ""}`}>
                <button
                  type="button"
                  className="theme-media-thumb"
                  disabled={disabled}
                  title={item.originalFilename}
                  aria-pressed={item.id === value}
                  onClick={() => onChange(item.id, item)}
                >
                  {renderThumb(item)}
                  {item.id === value ? <Check className="theme-media-check" aria-hidden="true" /> : null}
                </button>
                <span className="theme-media-name" title={item.originalFilename}>{item.originalFilename}</span>
                <span className="theme-media-meta">
                  {formatSize(item.sizeBytes)}{item.width && item.height ? ` · ${item.width}×${item.height}` : ""}
                </span>
                <button
                  type="button"
                  className="theme-banner-remove"
                  disabled={disabled}
                  aria-label={t("storeTheme.media.delete")}
                  onClick={() => void remove(item)}
                  onBlur={() => setConfirmDelete((current) => (current === item.id ? null : current))}
                >
                  <Trash2 />{confirmDelete === item.id ? t("storeTheme.media.deleteConfirm") : t("storeTheme.media.delete")}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
