import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TextAlign from "@tiptap/extension-text-align";
import {
  AlignCenter,
  AlignLeft,
  Bold,
  Heading2,
  Heading3,
  ImagePlus,
  Italic,
  List,
  ListOrdered,
  LoaderCircle,
} from "lucide-react";
import { articleAdminApi, mediaUrl } from "../../services/api";
import { HelpFontSize, type HelpFontSizeValue } from "../help/HelpFontSize";
import { HelpResizableImage, IMAGE_WIDTH_PRESETS } from "../help/HelpResizableImage";
import { parseHelpImageWidth, toHelpEditorHtml } from "../help/helpHtml";

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
const ARTICLE_IMAGE_PATH = /\/api\/article-images\/[0-9a-fA-F-]{36}/;

function editorHtml(value: string | null | undefined): string {
  return toHelpEditorHtml(value).replace(
    /(<img\b[^>]*\bsrc=["'])(\/api\/article-images\/[0-9a-fA-F-]{36})(["'])/gi,
    (_m, pre: string, src: string, post: string) => `${pre}${mediaUrl(src)}${post}`,
  );
}

function persistHtml(html: string): string {
  return html.replace(
    /(<img\b[^>]*\bsrc=["'])([^"']+)(["'])/gi,
    (_m, pre: string, src: string, post: string) => {
      const match = src.match(ARTICLE_IMAGE_PATH);
      return match ? `${pre}${match[0]}${post}` : `${pre}${src}${post}`;
    },
  );
}

/** TipTap article body editor (same UX as Help Center: format text + images). */
export default function ArticleRichTextEditor({
  label,
  value,
  onChange,
  error,
  errorId,
  disabled,
  maxLength = 200_000,
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
        heading: { levels: [2, 3] },
        code: false,
        codeBlock: false,
        blockquote: false,
        horizontalRule: false,
        strike: false,
      }),
      TextAlign.configure({ types: ["paragraph", "heading"], alignments: ["left", "center"] }),
      HelpResizableImage,
      HelpFontSize,
    ],
    content: editorHtml(value),
    editable: !disabled,
    editorProps: {
      attributes: {
        class: "hc-rte-content ar-rte-content",
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
      setUploadError(t("articlesCms.errors.coverType"));
      return;
    }
    setUploading(true);
    setUploadError("");
    try {
      const uploaded = await articleAdminApi.uploadInlineImage(file);
      editor
        .chain()
        .focus()
        .setImage({
          src: mediaUrl(uploaded.url),
          alt: file.name.replace(/\.[^.]+$/, ""),
        })
        .run();
    } catch {
      setUploadError(t("articlesCms.editor.bodyImage.failed"));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const length = value.length;
  const te = (key: string) => t(`helpCenter.editor.${key}`);

  return (
    <div className={`hc-rte ar-rte ${error ? "is-invalid" : ""}`.trim()}>
      <div className="hc-rte-label">{label}</div>
      <div className="hc-rte-toolbar" role="toolbar" aria-label={te("toolbar")}>
        <button
          type="button"
          className={editor?.isActive("bold") ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("bold") || false}
          title={te("bold")}
          onClick={() => editor?.chain().focus().toggleBold().run()}
        >
          <Bold aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive("italic") ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("italic") || false}
          title={t("articlesCms.editor.rich.italic")}
          onClick={() => editor?.chain().focus().toggleItalic().run()}
        >
          <Italic aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive({ textAlign: "left" }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive({ textAlign: "left" }) || false}
          title={te("alignLeft")}
          onClick={() => editor?.chain().focus().setTextAlign("left").run()}
        >
          <AlignLeft aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive({ textAlign: "center" }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive({ textAlign: "center" }) || false}
          title={te("alignCenter")}
          onClick={() => editor?.chain().focus().setTextAlign("center").run()}
        >
          <AlignCenter aria-hidden="true" />
        </button>
        <span className="hc-rte-sep" aria-hidden="true" />
        <button
          type="button"
          className={editor?.isActive("heading", { level: 2 }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("heading", { level: 2 }) || false}
          title={t("articlesCms.editor.rich.h2")}
          onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}
        >
          <Heading2 aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive("heading", { level: 3 }) ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("heading", { level: 3 }) || false}
          title={t("articlesCms.editor.rich.h3")}
          onClick={() => editor?.chain().focus().toggleHeading({ level: 3 }).run()}
        >
          <Heading3 aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive("bulletList") ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("bulletList") || false}
          title={t("articlesCms.editor.rich.bulletList")}
          onClick={() => editor?.chain().focus().toggleBulletList().run()}
        >
          <List aria-hidden="true" />
        </button>
        <button
          type="button"
          className={editor?.isActive("orderedList") ? "active" : undefined}
          disabled={disabled || !editor}
          aria-pressed={editor?.isActive("orderedList") || false}
          title={t("articlesCms.editor.rich.orderedList")}
          onClick={() => editor?.chain().focus().toggleOrderedList().run()}
        >
          <ListOrdered aria-hidden="true" />
        </button>
        <span className="hc-rte-sep" aria-hidden="true" />
        {SIZES.map((size) => {
          const active =
            size === "md" ? !editor?.isActive("helpFontSize") : editor?.isActive("helpFontSize", { size });
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
          title={t("articlesCms.editor.bodyImage.insert")}
          onClick={() => fileRef.current?.click()}
        >
          {uploading ? <LoaderCircle className="pm-spin" aria-hidden="true" /> : <ImagePlus aria-hidden="true" />}
          <span>{t("articlesCms.editor.bodyImage.insert")}</span>
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
              title={t(`articlesCms.editor.bodyImage.size.${preset.key}`)}
              onClick={() => applyImageWidth(preset.pct)}
            >
              {t(`articlesCms.editor.bodyImage.size.${preset.key}`)}
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
