const LOOKS_LIKE_HTML = /<[a-zA-Z!/?]/;

/** True when the stored body looks like HTML rather than legacy plain text. */
export function isHelpHtml(value: string | null | undefined): boolean {
  return Boolean(value && LOOKS_LIKE_HTML.test(value));
}

/** Strip tags for snippets, meta, and search previews. */
export function helpPlainText(value: string | null | undefined): string {
  if (!value) return "";
  if (!isHelpHtml(value)) return value.trim();
  const doc = new DOMParser().parseFromString(value, "text/html");
  return (doc.body.textContent ?? "").replace(/\s+/g, " ").trim();
}

export function escapeHelpText(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/** TipTap / display content: wrap legacy plain text as paragraphs. */
export function toHelpEditorHtml(value: string | null | undefined): string {
  const text = value ?? "";
  if (!text.trim()) return "";
  if (isHelpHtml(text)) return text;
  return text
    .split(/\n{2,}/)
    .map((block) => `<p>${escapeHelpText(block).replace(/\n/g, "<br>")}</p>`)
    .join("");
}

export const HELP_SIZE_CLASSES = ["hc-size-sm", "hc-size-md", "hc-size-lg", "hc-size-heading"] as const;
export type HelpSizeClass = (typeof HELP_SIZE_CLASSES)[number];

/** Image width attr is percent of content column (10–100). */
export function parseHelpImageWidth(raw: string | null | undefined): number | null {
  if (!raw?.trim()) return null;
  const trimmed = raw.trim().replace(/%$/, "");
  const value = Number.parseInt(trimmed, 10);
  if (!Number.isInteger(value) || value < 10 || value > 100) return null;
  return value;
}
