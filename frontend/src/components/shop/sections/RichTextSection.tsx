import { useMemo } from "react";
import ReactMarkdown from "react-markdown";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import { Link } from "react-router-dom";
import { MAX_RICH_TEXT, resolveRichTextContent, safeBlockLink } from "../../../theme/blockContent";
import { panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";
import type { ComponentProps } from "react";

/** The only elements / attributes a merchant's markdown can ever produce (h2–h3, bold, italic, lists, links, quote). */
const ALLOWED_ELEMENTS = ["h2", "h3", "p", "strong", "em", "ul", "ol", "li", "a", "blockquote", "br"] as const;

const SCHEMA = {
  ...defaultSchema,
  tagNames: [...ALLOWED_ELEMENTS],
  attributes: { a: ["href"] },
  protocols: { href: ["https"] },
  // Links are re-validated in the `a` renderer; ids / classes never come from merchant text.
  clobber: [],
  clobberPrefix: "",
  strip: ["script", "style", "iframe", "object", "embed"],
};

function RichLink({ href, children }: ComponentProps<"a">) {
  const target = safeBlockLink(href);
  if (!target) return <span>{children}</span>;
  if (target.external) return <a href={target.href} target="_blank" rel="nofollow noopener noreferrer">{children}</a>;
  return <Link to={target.href}>{children}</Link>;
}

/**
 * AG4 RICH_TEXT. Rendered from the `markdown` source with react-markdown (raw HTML is NOT enabled — no rehype-raw),
 * an element allowlist, rehype-sanitize and a link re-check; there is no dangerouslySetInnerHTML anywhere. The
 * backend additionally rejects scripts / javascript: / on*= and stores jsoup-sanitized HTML (unused here).
 */
export default function RichTextSection({ block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveRichTextContent(block.content), [block.content]);
  const l = useLocalized();
  const markdown = l(content.markdown).slice(0, MAX_RICH_TEXT);
  if (!markdown.trim()) return null;

  return (
    <section className={panelClasses(block, "shop-richtext-section")}>
      <div className="shop-richtext-body">
        <ReactMarkdown
          allowedElements={ALLOWED_ELEMENTS}
          unwrapDisallowed
          skipHtml
          rehypePlugins={[[rehypeSanitize, SCHEMA]]}
          components={{ a: RichLink }}
          urlTransform={(url) => (safeBlockLink(url) ? url : "")}
        >
          {markdown}
        </ReactMarkdown>
      </div>
    </section>
  );
}
