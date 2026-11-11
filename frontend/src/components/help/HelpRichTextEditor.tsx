import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TextAlign from "@tiptap/extension-text-align";
import { AlignCenter, AlignLeft, Bold, ImagePlus, LoaderCircle } from "lucide-react";
import { helpAdminApi, mediaUrl } from "../../services/api";
import { HelpFontSize, type HelpFontSizeValue } from "./HelpFontSize";
import { HelpResizableImage, IMAGE_WIDTH_PRESETS } from "./HelpResizableImage";
import { parseHelpImageWidth, toHelpEditorHtml } from "./helpHtml";

type Props = {
  label: React.ReactNode;
  value: string;
  onChange: (html: string) => void;
  error?: string;
  errorId?: string;
  disabled?: boolean;
  maxLength?: number;
};

const SIZES: HelpFontSizeValue[] = ["sm", "md", "lg", "heading"];
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const HELP_IMAGE_PATH = /\/api\/help\/images\/[0-9a-fA-F-]{36}/;

/** TipTap runs on the Vite host — resolve stored /api/... paths to the backend. */
function editorHtml(value: string | null | undefined): string {
  return toHelpEditorHtml(value).replace(
    /(<img\b[^>]*\bsrc=["'])(\/api\/help\/images\/[0-9a-fA-F-]{36})(["'])/gi,
    (_m, pre: string, src: string, post: string) => `${pre}${mediaUrl(src)}${post}`,
  );
}

/** Persist relative /api/help/images/{id} so backend sanitize accepts the HTML. */
function persistHtml(html: string): string {
  return html.replace(
    /(<img\b[^>]*\bsrc=["'])([^"']+)(["'])/gi,
    (_m, pre: string, src: string, post: string) => {
      const match = src.match(HELP_IMAGE_PATH);
      return match ? `${pre}${match[0]}${post}` : `${pre}${src}${post}`;
    },
  );
}

export default function HelpRichTextEditor({
  label,
  value,
  onChange,
  error,
  errorId,
  disabled,
  maxLength = 10_000,
}: Props) {
  const { t } = useTranslation("admin");
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [, setSelectionTick] = useState(0);
  const skipEmit = useRef(false);

  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({
        heading: false,
        code: false,
        codeBlock: false,
        blockquote: false,
        horizontalRule: false,
        strike: false,
      }),
      TextAlign.configure({ types: ["paragraph"], alignments: ["left", "center"] }),
      HelpResizableImage,
      HelpFontSize,
    ],
    content: editorHtml(value),
    editable: !disabled,
    editorProps: {
      attributes: {
        class: "hc-rte-content",
        ...(error ? { "aria-invalid": "true" } : {}),
        ...(error && errorId ? { "aria-describedby": errorId } : {}),
      },
    },
    onUpdate: ({ editor: current }) => {
      if (skipEmit.current) return;
      const html = current.isEmpty ? "" : persistHtml(current.getHTML());
      if (html.length <= maxLength) onChange(html);
    },
    onSelectionUpdate: () => setSelectionTick((tick) => tick + 1),
  });

  useEffect(() => {
    if (!editor) return;
    editor.setEditable(!disabled);
  }, [editor, disabled]);

  useEffect(() => {
    if (!editor) return;
    const next = editorHtml(value);
    const current = editor.isEmpty ? "" : editor.getHTML();
    if (next === current || (editor.isEmpty && !value.trim())) return;
    skipEmit.current = true;
    editor.commands.setContent(next || "", { emitUpdate: false });
    skipEmit.current = false;
  }, [editor, value]);

  const applySize = (size: HelpFontSizeValue) => {
    if (!editor || disabled) return;
    if (size === "md") editor.chain().focus().unsetHelpFontSize().run();
    else editor.chain().focus().setHelpFontSize(size).run();
  };

  const imageSelected = Boolean(editor?.isActive("image"));
  const currentImageWidth =
    parseHelpImageWidth(editor?.getAttributes("image")?.width != null ? String(editor.getAttributes("image").width) : null) ??
    (imageSelected ? 100 : null);

  const applyImageWidth = (pct: number) => {
    if (!editor || disabled || !imageSelected) return;
    editor.chain().focus().updateAttributes("image", { width: pct >= 100 ? null : pct }).run();
  };

  const insertImage = async (file: File) => {
    if (!editor || disabled) return;
    if (!IMAGE_TYPES.includes(file.type)) {
      setUploadError(t("helpCenter.editor.imageType"));
      return;
    }
    setUploading(true);
    setUploadError("");
    try {
      const uploaded = await helpAdminApi.uploadImage(file);
      editor.chain().focus().setImage({
        src: mediaUrl(uploaded.url),
        alt: file.name.replace(/\.[^.]+$/, ""),
      }).run();
    } catch {
      setUploadError(t("helpCenter.editor.imageFailed"));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const length = value.length;

  return (
    <div className={`hc-rte ${error ? "is-invalid" : ""}`.trim()}>
      <div className="hc-rte-label">{label}</div>
      <div className="hc-rte-toolbar" role="toolbar" aria-label={t("helpCenter.editor.toolbar")}>
        <button
          type="button"
          className={editor?.isActive("bold") ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("bold") || false}
          title={t("helpCenter.editor.bold")}
          onClick={() => editor?.chain().focus().toggleBold().run()}
        >
          <Bold aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive({ textAlign: "left" }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive({ textAlign: "left" }) || false}
          title={t("helpCenter.editor.alignLeft")}
          onClick={() => editor?.chain().focus().setTextAlign("left").run()}
        >
          <AlignLeft aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive({ textAlign: "center" }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive({ textAlign: "center" }) || false}
          title={t("helpCenter.editor.alignCenter")}
          onClick={() => editor?.chain().focus().setTextAlign("center").run()}
        >
          <AlignCenter aria-hidden="true" />
        </button>
        <span className="hc-rte-sep" aria-hidden="true" />
        {SIZES.map((size) => {
          const active =
            size === "md"
              ? !editor?.isActive("helpFontSize")
              : editor?.isActive("helpFontSize", { size });
          return (
            <button
              key={size}
              type="button"
              className={active ? "active" : undefined}
              disabled={disabled || !editor}
              aria-pressed={Boolean(active)}
              onClick={() => applySize(size)}
            >
              {t(`helpCenter.editor.size.${size}`)}
            </button>
          );
        })}
        <span className="hc-rte-sep" aria-hidden="true" />
        <button
          type="button"
          disabled={disabled || !editor || uploading}
          title={t("helpCenter.editor.insertImage")}
          onClick={() => fileRef.current?.click()}
        >
          {uploading ? <LoaderCircle className="pm-spin" aria-hidden="true" /> : <ImagePlus aria-hidden="true" />}
          <span>{t("helpCenter.editor.insertImage")}</span>
        </button>
        {IMAGE_WIDTH_PRESETS.map((preset) => {
          const active = imageSelected && currentImageWidth === preset.pct;
          return (
            <button
              key={preset.key}
              type="button"
              className={active ? "active" : undefined}
              disabled={disabled || !editor || !imageSelected}
              aria-pressed={Boolean(active)}
              title={t(`helpCenter.editor.imageSize.${preset.key}`)}
              onClick={() => applyImageWidth(preset.pct)}
            >
              {t(`helpCenter.editor.imageSize.${preset.key}`)}
            </button>
          );
        })}
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void insertImage(file);
          }}
        />
      </div>
      <EditorContent editor={editor} />
      <div className="hc-rte-meta">
        <small className={length > maxLength ? "is-over" : undefined}>
          {length}/{maxLength}
        </small>
      </div>
      {(error || uploadError) && (
        <em className="hc-rte-error" id={errorId} role="alert">
          {error || uploadError}
        </em>
      )}
    </div>
  );
}
