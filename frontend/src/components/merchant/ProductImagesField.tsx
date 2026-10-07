import { useRef, useState } from "react";
import { ImagePlus, Star, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { productApi, type Product, type ProductImage } from "../../services/api";
import AuthImage from "./AuthImage";

export type LocalImage = { key: string; file: File; preview: string; primary: boolean; progress?: number };

const MAX_IMAGES = 9;
const MAX_BYTES = 5 * 1024 * 1024;

function acceptFile(file: File) {
  const name = file.name.toLowerCase();
  const typeOk = file.type === "image/jpeg" || file.type === "image/png" || file.type === "image/webp"
    || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp");
  if (!typeOk) return "IMAGE_TYPE";
  if (file.size > MAX_BYTES) return "FILE_TOO_LARGE";
  return "";
}

export default function ProductImagesField({
  shopId, productId, remote, local, onLocal, onRemote, onError,
}: {
  shopId: number;
  productId?: string | null;
  remote: ProductImage[];
  local: LocalImage[];
  onLocal: (images: LocalImage[]) => void;
  onRemote: (product: Product) => void;
  onError: (code: string) => void;
}) {
  const { t } = useTranslation("merchant");
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [progress, setProgress] = useState<Record<string, number>>({});
  const total = remote.length + local.length;

  const addFiles = async (files: File[]) => {
    const room = MAX_IMAGES - total;
    if (room <= 0) {
      onError("IMAGE_LIMIT");
      return;
    }
    const accepted: File[] = [];
    for (const file of files.slice(0, room)) {
      const code = acceptFile(file);
      if (code) {
        onError(code);
        return;
      }
      accepted.push(file);
    }
    if (files.length > room) onError("IMAGE_LIMIT");
    if (!productId) {
      const next = accepted.map((file) => ({ key: crypto.randomUUID(), file, preview: URL.createObjectURL(file), primary: false }));
      const merged = [...local, ...next];
      if (!remote.some((image) => image.primary) && !merged.some((image) => image.primary) && merged[0]) merged[0].primary = true;
      onLocal(merged);
      return;
    }
    let latest: Product | null = null;
    for (const file of accepted) {
      const key = crypto.randomUUID();
      setProgress((current) => ({ ...current, [key]: 0 }));
      latest = await productApi.addImage(shopId, productId, file, (ratio) => setProgress((current) => ({ ...current, [key]: ratio })));
      setProgress((current) => {
        const next = { ...current };
        delete next[key];
        return next;
      });
    }
    if (latest) onRemote(latest);
  };

  const onDrop = (event: React.DragEvent) => {
    event.preventDefault();
    setDragOver(false);
    const files = [...event.dataTransfer.files];
    if (files.length) void addFiles(files).catch((error) => onError(error?.code || "REQUEST_FAILED"));
  };

  const removeRemote = async (imageId: string) => {
    if (!productId) return;
    onRemote(await productApi.deleteImage(shopId, productId, imageId));
  };

  const removeLocal = (key: string) => {
    const next = local.filter((image) => image.key !== key);
    const removed = local.find((image) => image.key === key);
    if (removed) URL.revokeObjectURL(removed.preview);
    if (removed?.primary && next[0]) next[0] = { ...next[0], primary: true };
    onLocal(next);
  };

  const setPrimaryRemote = async (imageId: string) => {
    if (!productId) return;
    onRemote(await productApi.orderImages(shopId, productId, remote.map((image) => image.id), imageId));
  };

  const moveRemote = async (from: number, to: number) => {
    if (!productId || from === to) return;
    const next = [...remote];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    const primary = next.find((image) => image.primary)?.id || next[0]?.id;
    if (primary) onRemote(await productApi.orderImages(shopId, productId, next.map((image) => image.id), primary));
  };

  const moveLocal = (from: number, to: number) => {
    if (from === to) return;
    const next = [...local];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onLocal(next);
  };

  return (
    <section className="pm-images">
      <header>
        <h2>{t("catalog.images.title")}</h2>
        <p>{t("catalog.images.hint")}</p>
      </header>
      <button
        type="button"
        className={dragOver ? "pm-drop over" : "pm-drop"}
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => { event.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
      >
        <ImagePlus />
        <span>{t("catalog.images.drop")}</span>
      </button>
      <input
        ref={inputRef}
        hidden
        type="file"
        accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
        multiple
        onChange={(event) => {
          const files = [...(event.target.files || [])];
          event.target.value = "";
          if (files.length) void addFiles(files).catch((error) => onError(error?.code || "REQUEST_FAILED"));
        }}
      />
      <div className="pm-image-grid">
        {remote.map((image, index) => (
          <figure
            key={image.id}
            draggable
            onDragStart={(event) => event.dataTransfer.setData("text/remote", String(index))}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => {
              event.preventDefault();
              const from = Number(event.dataTransfer.getData("text/remote"));
              if (!Number.isNaN(from)) void moveRemote(from, index).catch((error) => onError(error?.code || "REQUEST_FAILED"));
            }}
          >
            <AuthImage path={image.url} alt="" />
            {image.primary ? <em>{t("catalog.images.primary")}</em> : null}
            <div>
              <button type="button" aria-label={t("catalog.images.setPrimary")} onClick={() => void setPrimaryRemote(image.id).catch((error) => onError(error?.code || "REQUEST_FAILED"))}><Star /></button>
              <button type="button" aria-label={t("catalog.images.remove")} onClick={() => void removeRemote(image.id).catch((error) => onError(error?.code || "REQUEST_FAILED"))}><Trash2 /></button>
            </div>
          </figure>
        ))}
        {local.map((image, index) => (
          <figure
            key={image.key}
            draggable
            onDragStart={(event) => event.dataTransfer.setData("text/local", String(index))}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => {
              const from = Number(event.dataTransfer.getData("text/local"));
              if (!Number.isNaN(from)) moveLocal(from, index);
            }}
          >
            <img src={image.preview} alt="" />
            {image.primary ? <em>{t("catalog.images.primary")}</em> : null}
            {progress[image.key] != null ? <progress value={progress[image.key]} max={1} aria-label={t("catalog.images.progress")} /> : null}
            <div>
              <button type="button" aria-label={t("catalog.images.setPrimary")} onClick={() => onLocal(local.map((item) => ({ ...item, primary: item.key === image.key })))}><Star /></button>
              <button type="button" aria-label={t("catalog.images.remove")} onClick={() => removeLocal(image.key)}><Trash2 /></button>
            </div>
          </figure>
        ))}
      </div>
    </section>
  );
}
