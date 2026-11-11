import { Mark, mergeAttributes } from "@tiptap/core";

export type HelpFontSizeValue = "sm" | "md" | "lg" | "heading";

declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    helpFontSize: {
      setHelpFontSize: (size: HelpFontSizeValue) => ReturnType;
      unsetHelpFontSize: () => ReturnType;
    };
  }
}

const SIZES: HelpFontSizeValue[] = ["sm", "md", "lg", "heading"];

/** TipTap mark that stores font-size presets as `hc-size-*` classes. */
export const HelpFontSize = Mark.create({
  name: "helpFontSize",
  excludes: "helpFontSize",
  parseHTML() {
    return SIZES.map((size) => ({
      tag: `span.hc-size-${size}`,
      attrs: { size },
    }));
  },
  renderHTML({ HTMLAttributes }) {
    return ["span", mergeAttributes(HTMLAttributes), 0];
  },
  addAttributes() {
    return {
      size: {
        default: null as HelpFontSizeValue | null,
        parseHTML: (element) => {
          const className = element.getAttribute("class") ?? "";
          const match = className.match(/hc-size-(sm|md|lg|heading)/);
          return (match?.[1] as HelpFontSizeValue | undefined) ?? null;
        },
        renderHTML: (attributes) => {
          if (!attributes.size || !SIZES.includes(attributes.size as HelpFontSizeValue)) return {};
          return { class: `hc-size-${attributes.size}` };
        },
      },
    };
  },
  addCommands() {
    return {
      setHelpFontSize:
        (size) =>
        ({ commands }) =>
          commands.setMark(this.name, { size }),
      unsetHelpFontSize:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
    };
  },
});
