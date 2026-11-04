import { useEffect, useMemo, useRef, type ComponentProps } from "react";
import ReactMarkdown from "react-markdown";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import { Link } from "react-router-dom";

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

function ArticleImage({ src, alt, title }: ComponentProps<"img">) {
  if (!src) return null;
  return <img src={src} alt={alt ?? ""} title={title} loading="lazy" decoding="async" />;
}

function SectionHeading({ node: _node, ...props }: ComponentProps<"h2"> & { node?: unknown }) {
  return <h2 {...props} />;
}

const COMPONENTS = { a: ArticleLink, img: ArticleImage, h1: SectionHeading };

type Props = {
  markdown: string;
  className?: string;
  /** Called after render with the h2/h3 outline read back from the DOM (ids always match the rendered anchors). */
  onToc?: (items: TocItem[]) => void;
};

export default function ArticleMarkdown({ markdown, className = "", onToc }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const rehypePlugins = useMemo(() => [[rehypeSanitize, SCHEMA], rehypeHeadingIds] as never, []);

  useEffect(() => {
    if (!onToc || !ref.current) return;
    const items: TocItem[] = [];
    ref.current.querySelectorAll<HTMLHeadingElement>("h2[id], h3[id]").forEach((heading) => {
      items.push({ id: heading.id, text: heading.textContent?.trim() ?? "", level: heading.tagName === "H3" ? 3 : 2 });
    });
    onToc(items);
  }, [markdown, onToc]);

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
