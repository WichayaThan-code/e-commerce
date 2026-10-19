import { useEffect } from "react";

const TITLE_SUFFIX = " | ThaiShopFun";

function normalizeDescription(value: string) {
  return value.replace(/\s+/g, " ").trim().slice(0, 160);
}

function ensureDescriptionMeta() {
  let meta = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "description";
    document.head.appendChild(meta);
  }
  return meta;
}

/** Sets document title / description meta and restores previous values on unmount. */
export function useDocumentMeta(meta: { title?: string | null; description?: string | null }): void {
  const { title, description } = meta;

  useEffect(() => {
    const previousTitle = document.title;
    const descriptionNode = ensureDescriptionMeta();
    const previousDescription = descriptionNode.getAttribute("content");

    if (title) document.title = `${title}${TITLE_SUFFIX}`;
    if (description != null && description !== "") {
      descriptionNode.setAttribute("content", normalizeDescription(description));
    }

    return () => {
      document.title = previousTitle;
      if (previousDescription == null) descriptionNode.removeAttribute("content");
      else descriptionNode.setAttribute("content", previousDescription);
    };
  }, [title, description]);
}
