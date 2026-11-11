import { useEffect, useMemo, useRef, type ComponentProps } from "react";
import ReactMarkdown from "react-markdown";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import { Link } from "react-router-dom";
import { mediaUrl } from "../../services/api";
import { isHelpHtml, parseHelpImageWidth } from "../help/helpHtml";

export type TocItem = { id: string; text: string; level: 2 | 3 };

/** Only these elements can ever be produced from article markdown (no raw HTML, no tables, no iframes). */
const ALLOWED_ELEMENTS = [
  "h1", "h2", "h3", "h4", "p", "strong", "em", "del", "ul", "ol", "li", "a", "blockquote", "br", "hr", "code", "pre", "img",
] as const;

const SCHEMA = {
  ...defaultSchema,
  tagNames: [...ALLOWED_ELEMENTS],
  attributes: {
    a: ["href", "title"],
    img: ["src", "alt", "title"],
    code: [["className", /^language-[\w-]+$/]],
  },
  protocols: { href: ["http", "https", "mailto"], src: ["http", "https"] },
  strip: ["script", "style", "iframe", "object", "embed"],
};

type HNode = { type: string; tagName?: string; properties?: Record<string, unknown>; children?: HNode[]; value?: string };

function textOf(node: HNode): string {
  if (node.type === "text") return node.value ?? "";
  return (node.children ?? []).map(textOf).join("");
}

function slugify(text: string): string {
  const slug = text
    .toLowerCase()
    .trim()
    .replace(/[^\p{L}\p{N}\p{M}\s-]/gu, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return slug || "section";
}

/** Runs after sanitize (which would clobber ids): gives every heading a stable, unique, unicode-safe id. */
function rehypeHeadingIds() {
  return (tree: unknown) => {
    const used = new Map<string, number>();
    const walk = (node: HNode) => {
      if (node.type === "element" && node.tagName && /^h[1-4]$/.test(node.tagName)) {
        const base = slugify(textOf(node));
        const count = used.get(base) ?? 0;
        used.set(base, count + 1);
        node.properties = { ...(node.properties ?? {}), id: count === 0 ? base : `${base}-${count + 1}` };
      }
      node.children?.forEach(walk);
    };
    walk(tree as HNode);
  };
}

function ArticleLink({ href, children }: ComponentProps<"a">) {
  if (!href) return <span>{children}</span>;
  if (href.startsWith("/") && !href.startsWith("//")) return <Link to={href}>{children}</Link>;
  if (href.startsWith("#")) return <a href={href}>{children}</a>;
  return (
    <a href={href} target="_blank" rel="nofollow noopener noreferrer">
      {children}
    </a>
  );
}

/** Relative API paths or absolute https URLs for article covers / inline body images. */
const SAFE_IMAGE_SRC =
  /^(https:\/\/[^\s]+|\/api\/article-(?:images|covers)\/[0-9a-fA-F-]{36})$/;
const ARTICLE_IMAGE_SRC = /^\/api\/article-images\/[0-9a-fA-F-]{36}$/;
const SIZE_CLASS = /^hc-size-(sm|md|lg|heading)$/;
const ALIGN_STYLE = /^\s*text-align\s*:\s*(left|center)\s*;?\s*$/i;
const ALLOWED_HTML_TAGS = new Set(["P", "BR", "STRONG", "EM", "UL", "OL", "LI", "H2", "H3", "IMG", "SPAN", "B", "I"]);

/** Markdown image title encodes width percent, e.g. ![alt](url "50%"). */
export function parseArticleImageWidth(title: string | null | undefined): number | null {
  if (!title?.trim()) return null;
  const match = title.trim().match(/^(\d{1,3})%$/);
  if (!match) return null;
  const value = Number.parseInt(match[1], 10);
  if (!Number.isInteger(value) || value < 10 || value > 100) return null;
  return value;
}

function ArticleImage({ src, alt, title }: ComponentProps<"img">) {
  if (!src || !SAFE_IMAGE_SRC.test(src)) return null;
  const widthPct = parseArticleImageWidth(title);
  const style = widthPct != null && widthPct < 100 ? { width: `${widthPct}%`, height: "auto" as const } : undefined;
  return (
    <img
      src={mediaUrl(src)}
      alt={alt ?? ""}
      title={widthPct != null ? undefined : title}
      style={style}
      loading="lazy"
      decoding="async"
    />
  );
}

function SectionHeading({ node: _node, ...props }: ComponentProps<"h2"> & { node?: unknown }) {
  return <h2 {...props} />;
}

const COMPONENTS = { a: ArticleLink, img: ArticleImage, h1: SectionHeading };

function sanitizeHtmlNode(node: Node, doc: Document): Node | null {
  if (node.nodeType === Node.TEXT_NODE) return doc.createTextNode(node.textContent ?? "");
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const el = node as Element;
  let tag = el.tagName.toUpperCase();
  if (tag === "B") tag = "STRONG";
  if (tag === "I") tag = "EM";
  if (!ALLOWED_HTML_TAGS.has(tag)) {
    const frag = doc.createDocumentFragment();
    el.childNodes.forEach((child) => {
      const cleaned = sanitizeHtmlNode(child, doc);
      if (cleaned) frag.appendChild(cleaned);
    });
    return frag;
  }

  const out = doc.createElement(tag.toLowerCase());
  if (tag === "IMG") {
    const src = el.getAttribute("src")?.trim() ?? "";
    if (!ARTICLE_IMAGE_SRC.test(src)) return null;
    out.setAttribute("src", mediaUrl(src));
    const alt = el.getAttribute("alt");
    if (alt) out.setAttribute("alt", alt.slice(0, 200));
    const widthPct = parseHelpImageWidth(el.getAttribute("width"));
    if (widthPct != null && widthPct < 100) {
      out.setAttribute("width", String(widthPct));
      out.setAttribute("style", `width: ${widthPct}%; height: auto`);
    }
    out.setAttribute("loading", "lazy");
    out.setAttribute("decoding", "async");
    return out;
  }

  if (tag === "H2" || tag === "H3") {
    const text = el.textContent?.trim() ?? "";
    if (text) out.setAttribute("id", slugify(text));
  }

  const className = el.getAttribute("class") ?? "";
  const size = className.split(/\s+/).find((token) => SIZE_CLASS.test(token));
  if (size) out.setAttribute("class", size);

  const style = el.getAttribute("style") ?? "";
  const align = style.match(ALIGN_STYLE);
  if (align) out.setAttribute("style", `text-align: ${align[1].toLowerCase()}`);

  el.childNodes.forEach((child) => {
    const cleaned = sanitizeHtmlNode(child, doc);
    if (cleaned) out.appendChild(cleaned);
  });
  return out;
}

function sanitizeArticleHtml(html: string): string {
  const parsed = new DOMParser().parseFromString(html, "text/html");
  const wrap = document.createElement("div");
  const used = new Map<string, number>();
  parsed.body.childNodes.forEach((child) => {
    const cleaned = sanitizeHtmlNode(child, document);
    if (cleaned) wrap.appendChild(cleaned);
  });
  wrap.querySelectorAll<HTMLHeadingElement>("h2, h3").forEach((heading) => {
    const base = slugify(heading.textContent?.trim() ?? "");
    const count = used.get(base) ?? 0;
    used.set(base, count + 1);
    heading.id = count === 0 ? base : `${base}-${count + 1}`;
  });
  return wrap.innerHTML;
}

type Props = {
  markdown: string;
  className?: string;
  /** Called after render with the h2/h3 outline read back from the DOM (ids always match the rendered anchors). */
  onToc?: (items: TocItem[]) => void;
};

/** Renders article body: TipTap HTML (new) or legacy Markdown. */
export default function ArticleMarkdown({ markdown, className = "", onToc }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const isHtml = isHelpHtml(markdown);
  const rehypePlugins = useMemo(() => [[rehypeSanitize, SCHEMA], rehypeHeadingIds] as never, []);
  const safeHtml = useMemo(() => (isHtml ? sanitizeArticleHtml(markdown) : ""), [isHtml, markdown]);

  useEffect(() => {
    if (!onToc || !ref.current) return;
    const items: TocItem[] = [];
    ref.current.querySelectorAll<HTMLHeadingElement>("h2[id], h3[id]").forEach((heading) => {
      items.push({ id: heading.id, text: heading.textContent?.trim() ?? "", level: heading.tagName === "H3" ? 3 : 2 });
    });
    onToc(items);
  }, [markdown, safeHtml, isHtml, onToc]);

  if (isHtml) {
    return (
      <div
        className={`article-markdown article-richtext help-richtext ${className}`.trim()}
        ref={ref}
        dangerouslySetInnerHTML={{ __html: safeHtml }}
      />
    );
  }

  return (
    <div className={`article-markdown ${className}`.trim()} ref={ref}>
      <ReactMarkdown
        allowedElements={ALLOWED_ELEMENTS}
        unwrapDisallowed
        skipHtml
        rehypePlugins={rehypePlugins}
        components={COMPONENTS}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  );
}
