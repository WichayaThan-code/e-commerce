import { useEffect, useId, useRef, useState } from "react";
import { ArrowUpDown, Check, ChevronDown } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { SHOP_SORT_KEYS, type ShopSortKey } from "./sections/types";
import "../../styles/shop-sort-select.css";

const LABEL_KEY: Record<ShopSortKey, string> = {
  popular: "catalog.sortPopular",
  newest: "catalog.sortNewest",
  best: "catalog.sortBest",
  price: "catalog.sortPrice",
  price_desc: "catalog.sortPriceHigh",
};

type Props = {
  value: ShopSortKey;
  onChange: (sort: ShopSortKey) => void;
  className?: string;
};

export default function ShopSortSelect({ value, onChange, className }: Props) {
  const { tp } = usePublicTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const label = tp(LABEL_KEY[value] ?? LABEL_KEY.popular);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className={`shop-sort-select${open ? " is-open" : ""}${className ? ` ${className}` : ""}`} ref={rootRef}>
      <button
        type="button"
        className="shop-sort-select__trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={tp("catalog.sortAria")}
        onClick={() => setOpen((prev) => !prev)}
      >
        <ArrowUpDown size={15} strokeWidth={2.25} aria-hidden="true" />
        <span className="shop-sort-select__value">{label}</span>
        <ChevronDown size={16} strokeWidth={2.25} className="shop-sort-select__chevron" aria-hidden="true" />
      </button>
      {open ? (
        <ul id={listId} className="shop-sort-select__menu" role="listbox" aria-label={tp("catalog.sortAria")}>
          {SHOP_SORT_KEYS.map((key) => {
            const selected = key === value;
            return (
              <li key={key} role="option" aria-selected={selected}>
                <button
                  type="button"
                  className={selected ? "is-selected" : undefined}
                  onClick={() => {
                    onChange(key);
                    setOpen(false);
                  }}
                >
                  <span>{tp(LABEL_KEY[key])}</span>
                  {selected ? <Check size={16} strokeWidth={2.5} aria-hidden="true" /> : null}
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}
