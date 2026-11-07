import type { ReactNode } from "react";
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslation } from "react-i18next";
import SearchableSelect from "../SearchableSelect";
import "../../styles/list-pagination.css";

const DEFAULT_SIZES = [10, 20, 50];

type Props = {
  /** 0-based page index */
  page: number;
  pageCount: number;
  size: number;
  total: number;
  sizeOptions?: number[];
  className?: string;
  /** Optional control between page-size and page nav (e.g. customers sort). */
  extra?: ReactNode;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
};

/** Shared merchant list pagination — matches /merchant/products. */
export default function MerchantPagination({
  page,
  pageCount,
  size,
  total,
  sizeOptions = DEFAULT_SIZES,
  className,
  extra,
  onPageChange,
  onSizeChange,
}: Props) {
  const { t } = useTranslation("merchant");
  const pages = Math.max(1, pageCount);
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  const windowSize = Math.min(5, pages);
  const start = Math.max(0, Math.min(page - 2, pages - windowSize));

  return (
    <div className={`pm-pagination${className ? ` ${className}` : ""}`}>
      <span>{t("catalog.showing", { from, to, total })}</span>
      <div className="pm-page-size">
        <span>{t("catalog.perPage")}</span>
        <SearchableSelect
          compact
          label={t("catalog.perPage")}
          options={sizeOptions.map(String)}
          value={String(size)}
          onChange={(picked) => {
            const next = Number(picked);
            if (sizeOptions.includes(next)) onSizeChange(next);
          }}
        />
      </div>
      {extra}
      <nav>
        <button type="button" aria-label={t("catalog.first")} disabled={page <= 0} onClick={() => onPageChange(0)}>
          <ChevronFirst />
        </button>
        <button type="button" aria-label={t("catalog.prev")} disabled={page <= 0} onClick={() => onPageChange(Math.max(0, page - 1))}>
          <ChevronLeft />
        </button>
        {Array.from({ length: windowSize }, (_, index) => {
          const number = start + index;
          return (
            <button
              key={number}
              type="button"
              className={number === page ? "active" : ""}
              aria-current={number === page ? "page" : undefined}
              onClick={() => onPageChange(number)}
            >
              {number + 1}
            </button>
          );
        })}
        <button type="button" aria-label={t("catalog.next")} disabled={page + 1 >= pages} onClick={() => onPageChange(Math.min(pages - 1, page + 1))}>
          <ChevronRight />
        </button>
        <button type="button" aria-label={t("catalog.last")} disabled={page + 1 >= pages} onClick={() => onPageChange(pages - 1)}>
          <ChevronLast />
        </button>
      </nav>
    </div>
  );
}
