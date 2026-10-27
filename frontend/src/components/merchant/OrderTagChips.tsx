import type { OrderTag } from "../../services/merchantOrderExtensions";

function contrastColor(hex: string | null | undefined) {
  const raw = (hex || "").replace("#", "");
  if (!/^[0-9a-fA-F]{6}$/.test(raw)) return "var(--shop-on-primary, #fff)";
  const r = Number.parseInt(raw.slice(0, 2), 16);
  const g = Number.parseInt(raw.slice(2, 4), 16);
  const b = Number.parseInt(raw.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? "#132b50" : "#fff";
}

type Props = {
  tags: OrderTag[] | null | undefined;
  className?: string;
};

export default function OrderTagChips({ tags, className = "" }: Props) {
  if (!tags?.length) return null;
  return (
    <ul className={`om-tag-chips ${className}`.trim()}>
      {tags.map((tag) => {
        const bg = tag.color || "var(--shop-accent, var(--shop-primary, #1268d7))";
        return (
          <li
            key={tag.id}
            className="om-tag-chip"
            style={{ background: bg, color: contrastColor(tag.color) }}
            title={tag.name}
          >
            {tag.name}
          </li>
        );
      })}
    </ul>
  );
}
