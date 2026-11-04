import { useEffect } from "react";

type OgMeta = { title?: string | null; description?: string | null; image?: string | null; type?: string; url?: string | null };

function ensure(property: string): HTMLMetaElement {
  let node = document.head.querySelector<HTMLMetaElement>(`meta[property="${property}"]`);
  if (!node) {
    node = document.createElement("meta");
    node.setAttribute("property", property);
    node.dataset.tsfOg = "created";
    document.head.appendChild(node);
  }
  return node;
}

/** Sets og:title / og:description / og:image / og:type / og:url while mounted and restores the previous tags on unmount. */
export function useOgMeta({ title, description, image, type = "article", url }: OgMeta): void {
  useEffect(() => {
    const entries: [string, string | null | undefined][] = [
      ["og:title", title],
      ["og:description", description ? description.replace(/\s+/g, " ").trim().slice(0, 200) : description],
      ["og:image", image],
      ["og:type", title ? type : null],
      ["og:url", url],
    ];
    const restore: (() => void)[] = [];
    for (const [property, value] of entries) {
      if (!value) continue;
      const node = ensure(property);
      const created = node.dataset.tsfOg === "created";
      const previous = node.getAttribute("content");
      node.setAttribute("content", value);
      restore.push(() => {
        if (created) node.remove();
        else if (previous == null) node.removeAttribute("content");
        else node.setAttribute("content", previous);
      });
    }
    return () => restore.forEach((fn) => fn());
  }, [title, description, image, type, url]);
}
