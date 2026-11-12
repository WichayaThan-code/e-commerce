import { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import Cropper, {
  getInitialCropFromCroppedAreaPercentages,
  type Area,
  type MediaSize,
  type Point,
  type Size,
} from "react-easy-crop";
import { Check, Maximize2, Minimize2, Move, RotateCcw, X } from "lucide-react";
import ToggleSwitch from "../form/ToggleSwitch";
import {
  DEFAULT_IMAGE_CROP,
  IMAGE_CROP_ZOOM_MAX,
  IMAGE_CROP_ZOOM_MIN,
  hasCustomCrop,
  type ImageCrop,
} from "../shop/imageCrop";
import "../../styles/image-focal-editor.css";

export type ImageFocalEditorProps = {
  open: boolean;
  imageUrl: string;
  aspect: number;
  mobileAspect?: number;
  value?: ImageCrop | null;
  mobileValue?: ImageCrop | null;
  disabled?: boolean;
  onCancel: () => void;
  onSave: (crop: ImageCrop, mobileCrop?: ImageCrop) => void;
};

type Mode = "desktop" | "mobile";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function round2(value: number) {
  return Math.round(value * 100) / 100;
}

function normalizeCrop(raw?: ImageCrop | null): ImageCrop {
  if (!raw) return { ...DEFAULT_IMAGE_CROP };
  return {
    x: round2(clamp(raw.x, 0, 100)),
    y: round2(clamp(raw.y, 0, 100)),
    zoom: round2(clamp(raw.zoom, IMAGE_CROP_ZOOM_MIN, IMAGE_CROP_ZOOM_MAX)),
  };
}

function areaToCrop(area: Area, zoom: number): ImageCrop {
  return {
    x: round2(clamp(area.x + area.width / 2, 0, 100)),
    y: round2(clamp(area.y + area.height / 2, 0, 100)),
    zoom: round2(clamp(zoom, IMAGE_CROP_ZOOM_MIN, IMAGE_CROP_ZOOM_MAX)),
  };
}

/** Inverse of the cropper zoom formula: visible media % for the current frame + zoom. */
function focalToArea(crop: ImageCrop, media: MediaSize, cropSize: Size): Area {
  const zoom = Math.max(IMAGE_CROP_ZOOM_MIN, crop.zoom || 1);
  const mediaW = Math.max(1, media.width);
  const mediaH = Math.max(1, media.height);
  let width = (100 * cropSize.width) / (mediaW * zoom);
  let height = (100 * cropSize.height) / (mediaH * zoom);
  width = clamp(width, 1, 100);
  height = clamp(height, 1, 100);
  const x = clamp(crop.x - width / 2, 0, Math.max(0, 100 - width));
  const y = clamp(crop.y - height / 2, 0, Math.max(0, 100 - height));
  return { x, y, width, height };
}

export default function ImageFocalEditor({
  open,
  imageUrl,
  aspect,
  mobileAspect,
  value,
  mobileValue,
  disabled,
  onCancel,
  onSave,
}: ImageFocalEditorProps) {
  const { t } = useTranslation("merchant");
  const titleId = useId();
  const hintId = useId();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const areaRef = useRef<Area | null>(null);
  const mediaRef = useRef<MediaSize | null>(null);
  const cropSizeRef = useRef<Size | null>(null);
  const appliedSeedRef = useRef<string>("");

  const [mode, setMode] = useState<Mode>("desktop");
  const [separateMobile, setSeparateMobile] = useState(Boolean(mobileValue));
  const [deskCrop, setDeskCrop] = useState(normalizeCrop(value));
  const [mobCrop, setMobCrop] = useState(normalizeCrop(mobileValue ?? value));
  const [cropPos, setCropPos] = useState<Point>({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(normalizeCrop(value).zoom);
  const [cropperKey, setCropperKey] = useState(0);
  const [initialArea, setInitialArea] = useState<Area | undefined>(undefined);

  const activeAspect = mode === "mobile" ? (mobileAspect ?? aspect) : aspect;
  const seedCrop = mode === "mobile" && separateMobile ? mobCrop : deskCrop;

  const flushArea = useCallback(() => {
    if (!areaRef.current) return;
    const next = areaToCrop(areaRef.current, zoom);
    if (mode === "mobile" && separateMobile) setMobCrop(next);
    else setDeskCrop(next);
  }, [mode, separateMobile, zoom]);

  const applySeedToCropper = useCallback((seed: ImageCrop) => {
    const media = mediaRef.current;
    const cropSize = cropSizeRef.current;
    if (!media || !cropSize || cropSize.width <= 0 || cropSize.height <= 0) return false;
    const area = focalToArea(seed, media, cropSize);
    const initial = getInitialCropFromCroppedAreaPercentages(
      area,
      media,
      0,
      cropSize,
      IMAGE_CROP_ZOOM_MIN,
      IMAGE_CROP_ZOOM_MAX,
    );
    areaRef.current = area;
    setInitialArea(area);
    setCropPos(initial.crop);
    setZoom(round2(clamp(initial.zoom, IMAGE_CROP_ZOOM_MIN, IMAGE_CROP_ZOOM_MAX)));
    return true;
  }, []);

  const remountFor = useCallback((seed: ImageCrop) => {
    mediaRef.current = null;
    cropSizeRef.current = null;
    areaRef.current = null;
    appliedSeedRef.current = "";
    setInitialArea(undefined);
    setZoom(seed.zoom);
    setCropPos({ x: 0, y: 0 });
    setCropperKey((key) => key + 1);
  }, []);

  useEffect(() => {
    if (!open) return;
    const desk = normalizeCrop(value);
    const mob = normalizeCrop(mobileValue ?? value);
    setDeskCrop(desk);
    setMobCrop(mob);
    setSeparateMobile(Boolean(mobileValue));
    setMode("desktop");
    remountFor(desk);
  }, [open, value, mobileValue, remountFor]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !disabled) onCancel();
    };
    window.addEventListener("keydown", onKey);
    cancelRef.current?.focus();
    return () => window.removeEventListener("keydown", onKey);
  }, [open, disabled, onCancel]);

  // After media + crop frame sizes are known, snap the cropper to the saved focal point.
  const onMediaLoaded = (mediaSize: MediaSize) => {
    mediaRef.current = mediaSize;
    const seedKey = `${cropperKey}:${mode}:${seedCrop.x}:${seedCrop.y}:${seedCrop.zoom}`;
    if (appliedSeedRef.current === seedKey) return;
    if (applySeedToCropper(seedCrop)) appliedSeedRef.current = seedKey;
  };

  const onCropSizeChange = (size: Size) => {
    cropSizeRef.current = size;
    const seedKey = `${cropperKey}:${mode}:${seedCrop.x}:${seedCrop.y}:${seedCrop.zoom}`;
    if (appliedSeedRef.current === seedKey) return;
    if (applySeedToCropper(seedCrop)) appliedSeedRef.current = seedKey;
  };

  const switchMode = (next: Mode) => {
    flushArea();
    setMode(next);
    remountFor(next === "mobile" && separateMobile ? mobCrop : deskCrop);
  };

  const resetCenter = () => {
    const next = { ...DEFAULT_IMAGE_CROP };
    if (mode === "mobile" && separateMobile) setMobCrop(next);
    else setDeskCrop(next);
    remountFor(next);
  };

  const save = () => {
    if (disabled) return;
    const fromArea = areaRef.current ? areaToCrop(areaRef.current, zoom) : null;
    const desk = mode === "desktop" && fromArea ? fromArea : deskCrop;
    const mob = mode === "mobile" && separateMobile && fromArea ? fromArea : mobCrop;
    onSave(desk, separateMobile ? mob : undefined);
  };

  if (!open || !imageUrl) return null;

  const dialog = (
    <div
      className="image-focal-dialog"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !disabled) onCancel();
      }}
    >
      <section role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={hintId}>
        <h2 id={titleId}>{t("storeTheme.crop.title")}</h2>
        <p id={hintId} className="image-focal-hint">{t("storeTheme.crop.hint")}</p>

        <div className="image-focal-toolbar">
          <div className="image-focal-mode" role="tablist" aria-label={t("storeTheme.crop.modeLabel")}>
            <button
              type="button"
              role="tab"
              className={mode === "desktop" ? "is-active" : ""}
              aria-selected={mode === "desktop"}
              onClick={() => switchMode("desktop")}
            >
              {t("storeTheme.crop.desktop")}
            </button>
            <button
              type="button"
              role="tab"
              className={mode === "mobile" ? "is-active" : ""}
              aria-selected={mode === "mobile"}
              disabled={!separateMobile}
              onClick={() => separateMobile && switchMode("mobile")}
            >
              {t("storeTheme.crop.mobile")}
            </button>
          </div>
          <ToggleSwitch
            checked={separateMobile}
            disabled={disabled}
            label={t("storeTheme.crop.separateMobile")}
            onText={t("storeTheme.crop.separateOn")}
            offText={t("storeTheme.crop.separateOff")}
            onChange={(checked) => {
              flushArea();
              setSeparateMobile(checked);
              if (!checked) {
                setMode("desktop");
                setMobCrop(deskCrop);
              } else if (!mobileValue) {
                setMobCrop(deskCrop);
              }
              remountFor(deskCrop);
            }}
          />
        </div>

        <div className="image-focal-stage">
          <Cropper
            key={cropperKey}
            image={imageUrl}
            crop={cropPos}
            zoom={zoom}
            aspect={activeAspect}
            minZoom={IMAGE_CROP_ZOOM_MIN}
            maxZoom={IMAGE_CROP_ZOOM_MAX}
            showGrid
            initialCroppedAreaPercentages={initialArea}
            onCropChange={setCropPos}
            onZoomChange={setZoom}
            onMediaLoaded={onMediaLoaded}
            onCropSizeChange={onCropSizeChange}
            onCropComplete={(croppedArea) => {
              areaRef.current = croppedArea;
            }}
            onCropAreaChange={(croppedArea) => {
              areaRef.current = croppedArea;
            }}
          />
        </div>

        <div className="image-focal-zoom">
          <Minimize2 aria-hidden="true" />
          <input
            type="range"
            min={IMAGE_CROP_ZOOM_MIN}
            max={IMAGE_CROP_ZOOM_MAX}
            step={0.01}
            value={zoom}
            disabled={disabled}
            aria-label={t("storeTheme.crop.zoom")}
            onChange={(event) => setZoom(Number(event.target.value))}
          />
          <Maximize2 aria-hidden="true" />
        </div>
        <p className="image-focal-zoom-value num" aria-live="polite">
          {t("storeTheme.crop.zoomValue", { value: zoom.toFixed(2) })}
        </p>

        <div className="image-focal-actions">
          <button type="button" className="secondary" disabled={disabled} onClick={resetCenter}>
            <RotateCcw aria-hidden="true" />
            {t("storeTheme.crop.reset")}
          </button>
          <div className="image-focal-actions-right">
            <button type="button" className="secondary" ref={cancelRef} disabled={disabled} onClick={onCancel}>
              <X aria-hidden="true" />
              {t("storeTheme.crop.cancel")}
            </button>
            <button type="button" className="primary" disabled={disabled} onClick={save}>
              <Check aria-hidden="true" />
              {t("storeTheme.crop.save")}
            </button>
          </div>
        </div>
      </section>
    </div>
  );

  return createPortal(dialog, document.body);
}

/** Compact text button that opens the focal editor without disturbing surrounding layout. */
export function ImageFocalTrigger({
  imageUrl,
  disabled,
  aspect,
  mobileAspect,
  value,
  mobileValue,
  onChange,
}: {
  imageUrl?: string | null;
  disabled?: boolean;
  aspect: number;
  mobileAspect?: number;
  value?: ImageCrop | null;
  mobileValue?: ImageCrop | null;
  onChange: (crop: ImageCrop, mobileCrop?: ImageCrop) => void;
}) {
  const { t } = useTranslation("merchant");
  const [open, setOpen] = useState(false);
  if (!imageUrl) return null;
  const adjusted = hasCustomCrop(value, mobileValue);
  return (
    <>
      <button
        type="button"
        className={`image-focal-trigger${adjusted ? " is-adjusted" : ""}`}
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        <Move aria-hidden="true" />
        <span>{adjusted ? t("storeTheme.crop.openAdjusted") : t("storeTheme.crop.open")}</span>
      </button>
      <ImageFocalEditor
        open={open}
        imageUrl={imageUrl}
        aspect={aspect}
        mobileAspect={mobileAspect}
        value={value}
        mobileValue={mobileValue}
        disabled={disabled}
        onCancel={() => setOpen(false)}
        onSave={(crop, mobileCrop) => {
          onChange(crop, mobileCrop);
          setOpen(false);
        }}
      />
    </>
  );
}
