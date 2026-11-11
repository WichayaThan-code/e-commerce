import { useMemo } from "react";
import { mediaUrl } from "../../services/api";
import { helpPlainText, isHelpHtml, parseHelpImageWidth } from "./helpHtml";

const ALLOWED_TAGS = new Set(["P", "BR", "STRONG", "EM", "UL", "OL", "LI", "H3", "IMG", "SPAN", "B", "I"]);
const SIZE_CLASS = /^hc-size-(sm|md|lg|heading)$/;
const ALIGN_STYLE = /^\s*text-align\s*:\s*(left|center)\s*;?\s*$/i;
const HELP_IMAGE_SRC = /^\/api\/help\/images\/[0-9a-fA-F-]{36}$/;

function sanitizeNode(node: Node, doc: Document): Node | null {
  if (node.nodeType === Node.TEXT_NODE) return doc.createTextNode(node.textContent ?? "");
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const el = node as Element;
  let tag = el.tagName.toUpperCase();
  if (tag === "B") tag = "STRONG";
  if (tag === "I") tag = "EM";
  if (!ALLOWED_TAGS.has(tag)) {
    const frag = doc.createDocumentFragment();
    el.childNodes.forEach((child) => {
      const cleaned = sanitizeNode(child, doc);
      if (cleaned) frag.appendChild(cleaned);
    });
    return frag;
  }

  const out = doc.createElement(tag.toLowerCase());
  if (tag === "IMG") {
    const src = el.getAttribute("src")?.trim() ?? "";
    if (!HELP_IMAGE_SRC.test(src)) return null;
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

  const className = el.getAttribute("class") ?? "";
  const size = className.split(/\s+/).find((token) => SIZE_CLASS.test(token));
  if (size) out.setAttribute("class", size);

  const style = el.getAttribute("style") ?? "";
  const align = style.match(ALIGN_STYLE);
  if (align) out.setAttribute("style", `text-align: ${align[1].toLowerCase()}`);

  el.childNodes.forEach((child) => {
    const cleaned = sanitizeNode(child, doc);
    if (cleaned) out.appendChild(cleaned);
  });
  return out;
}

function sanitizeHtml(html: string): string {
  const parsed = new DOMParser().parseFromString(html, "text/html");
  const wrap = document.createElement("div");
  parsed.body.childNodes.forEach((child) => {
    const cleaned = sanitizeNode(child, document);
    if (cleaned) wrap.appendChild(cleaned);
  });
  return wrap.innerHTML;
}

type Props = {
  html: string;
  className?: string;
};

/** Renders help rich text (sanitized HTML) or legacy plain text. */
export default function HelpRichText({ html, className = "" }: Props) {
  const content = useMemo(() => {
    if (!html?.trim()) return { kind: "empty" as const };
    if (!isHelpHtml(html)) return { kind: "plain" as const, text: html };
    return { kind: "html" as const, html: sanitizeHtml(html) };
  }, [html]);

  if (content.kind === "empty") return null;
  if (content.kind === "plain") {
    return <div className={`help-richtext ${className}`.trim()} style={{ whiteSpace: "pre-line" }}>{content.text}</div>;
  }
  return (
    <div
      className={`help-richtext ${className}`.trim()}
      dangerouslySetInnerHTML={{ __html: content.html }}
    />
  );
}

export { helpPlainText, isHelpHtml };
