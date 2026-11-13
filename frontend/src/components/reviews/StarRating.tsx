import { Star } from "lucide-react";

type StarRatingProps = {
  value: number;
  onChange?: (value: number) => void;
  size?: number;
  readOnly?: boolean;
  ariaLabel?: string;
  className?: string;
};

/** 5-star control: read-only display (supports halves) or interactive 1–5 picker. */
export default function StarRating({
  value,
  onChange,
  size = 18,
  readOnly = false,
  ariaLabel,
  className,
}: StarRatingProps) {
  const interactive = Boolean(onChange) && !readOnly;
  const clamped = Math.max(0, Math.min(5, Number.isFinite(value) ? value : 0));

  return (
    <span
      className={["star-rating", interactive ? "is-interactive" : "", className].filter(Boolean).join(" ")}
      role={interactive ? "radiogroup" : "img"}
      aria-label={ariaLabel}
      style={{ ["--star-size" as string]: `${size}px` }}
    >
      {[1, 2, 3, 4, 5].map((star) => {
        const fill = interactive
          ? clamped >= star
            ? 1
            : 0
          : clamped >= star
            ? 1
            : clamped >= star - 0.5
              ? 0.5
              : 0;
        const body =
          fill === 0.5 ? (
            <span className="star-rating-unit is-half" aria-hidden="true">
              <Star />
              <span className="star-rating-half">
                <Star />
              </span>
            </span>
          ) : (
            <span className={`star-rating-unit is-${fill === 1 ? "full" : "empty"}`} aria-hidden="true">
              <Star />
            </span>
          );

        if (!interactive) return <span key={star}>{body}</span>;

        return (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={clamped === star}
            aria-label={`${star}`}
            className={clamped >= star ? "is-on" : ""}
            onClick={() => onChange?.(star)}
          >
            {body}
          </button>
        );
      })}
    </span>
  );
}
