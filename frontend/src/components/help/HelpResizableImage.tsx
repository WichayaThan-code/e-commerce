import { useCallback, useRef, type PointerEvent as ReactPointerEvent } from "react";
import Image from "@tiptap/extension-image";
import { NodeViewWrapper, ReactNodeViewRenderer, type NodeViewProps } from "@tiptap/react";
import { parseHelpImageWidth } from "./helpHtml";

export const IMAGE_WIDTH_PRESETS = [
  { key: "s", pct: 33 },
  { key: "m", pct: 50 },
  { key: "l", pct: 75 },
  { key: "full", pct: 100 },
] as const;

export type ImageWidthPresetKey = (typeof IMAGE_WIDTH_PRESETS)[number]["key"];

function clampWidth(value: number): number {
  return Math.min(100, Math.max(10, Math.round(value)));
}

function HelpResizableImageView({ node, updateAttributes, selected, editor }: NodeViewProps) {
  const boxRef = useRef<HTMLDivElement>(null);
  const width = parseHelpImageWidth(node.attrs.width != null ? String(node.attrs.width) : null) ?? 100;

  const onResizeStart = useCallback(
    (event: ReactPointerEvent<HTMLSpanElement>) => {
      if (!editor.isEditable) return;
      event.preventDefault();
      event.stopPropagation();
      const startX = event.clientX;
      const startWidth = width;
      const parent = boxRef.current?.parentElement;
      const parentWidth = parent?.clientWidth || boxRef.current?.clientWidth || 1;

      const onMove = (moveEvent: PointerEvent) => {
        const deltaPct = ((moveEvent.clientX - startX) / parentWidth) * 100;
        updateAttributes({ width: clampWidth(startWidth + deltaPct) });
      };
      const onUp = () => {
        window.removeEventListener("pointermove", onMove);
        window.removeEventListener("pointerup", onUp);
      };
      window.addEventListener("pointermove", onMove);
      window.addEventListener("pointerup", onUp);
    },
    [editor.isEditable, updateAttributes, width],
  );

  return (
    <NodeViewWrapper as="div" className={`hc-rte-image${selected ? " is-selected" : ""}`} data-drag-handle>
      <div ref={boxRef} className="hc-rte-image-box" style={{ width: `${width}%` }}>
        <img
          src={node.attrs.src}
          alt={node.attrs.alt ?? ""}
          draggable={false}
        />
        {selected && editor.isEditable ? (
          <span className="hc-rte-image-handle" role="presentation" onPointerDown={onResizeStart} />
        ) : null}
      </div>
    </NodeViewWrapper>
  );
}

/** TipTap image that stores display width as a percent (10–100) in the HTML width attribute. */
export const HelpResizableImage = Image.extend({
  name: "image",
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null as number | null,
        parseHTML: (element) => parseHelpImageWidth(element.getAttribute("width")),
        renderHTML: (attributes) => {
          const pct =
            typeof attributes.width === "number"
              ? attributes.width
              : parseHelpImageWidth(attributes.width != null ? String(attributes.width) : null);
          if (pct == null || pct >= 100) return {};
          return { width: String(pct) };
        },
      },
    };
  },
  addNodeView() {
    return ReactNodeViewRenderer(HelpResizableImageView);
  },
}).configure({ inline: false, allowBase64: false });
